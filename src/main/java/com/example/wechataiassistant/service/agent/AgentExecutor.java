package com.example.wechataiassistant.service.agent;

import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.rag.RagDocument;
import com.example.wechataiassistant.service.rag.RagService;
import com.example.wechataiassistant.service.skill.CalculatorSkill;
import com.example.wechataiassistant.service.tool.ToolContext;
import com.example.wechataiassistant.service.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 执行器：按依赖顺序执行子任务，按 capability 分发到
 * 工具 / 链式工具 / 工具循环 / 技能 / RAG / LLM 六类执行器。
 * 单步失败不中断整体，错误记录到 AgentContext。
 */
@Component
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    private final ToolRegistry tools;
    private final CalculatorSkill calculator;
    private final RagService ragService;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    public AgentExecutor(
        ToolRegistry tools, CalculatorSkill calculator, RagService ragService, LlmClient llm, ObjectMapper mapper) {
        this.tools = tools;
        this.calculator = calculator;
        this.ragService = ragService;
        this.llm = llm;
        this.mapper = mapper;
    }

    /** 按依赖拓扑序执行所有子任务；支持断点续跑（跳过已完成步骤并恢复结果）。 */
    public void execute(AgentPlan plan, AgentContext ctx, String runId, AgentRunStore store) {
        Map<Integer, AgentSubtask> byId = new LinkedHashMap<>();
        for (AgentSubtask s : plan.subtasks()) {
            byId.put(s.id(), s);
        }
        Set<Integer> done = new HashSet<>();
        if (runId != null && store != null) {
            AgentRunState st = store.get(runId);
            if (st != null && st.getDoneIds() != null && !st.getDoneIds().isEmpty()) {
                done.addAll(st.getDoneIds());
                // 恢复已完成步骤的结果与错误（Assembler 需要）
                st.getResults().forEach(ctx::putResult);
                st.getErrors().forEach(ctx::putError);
                log.info("♻️ 断点续跑：跳过已完成 {} 步，从中断处继续", done.size());
            }
        }
        while (done.size() < byId.size()) {
            boolean progressed = false;
            for (AgentSubtask s : byId.values()) {
                if (done.contains(s.id())) {
                    continue;
                }
                if (s.dependsOn() != null && s.dependsOn().stream().anyMatch(d -> !done.contains(d))) {
                    continue; // 依赖未完成
                }
                executeOne(s, plan, ctx);
                done.add(s.id());
                if (runId != null && store != null) {
                    persistCheckpoint(runId, done, ctx, store);
                }
                progressed = true;
            }
            if (!progressed) {
                log.warn("子任务依赖形成死锁或缺失，提前结束");
                break;
            }
        }
    }

    /** 每完成一步就落盘 checkpoint（断点续跑的关键）。 */
    private void persistCheckpoint(String runId, Set<Integer> done, AgentContext ctx, AgentRunStore store) {
        AgentRunState st = store.get(runId);
        if (st == null) {
            return;
        }
        st.setDoneIds(new ArrayList<>(done));
        st.setResults(ctx.exportResults());
        st.setErrors(ctx.errors());
        store.save(st);
    }

    private void executeOne(AgentSubtask s, AgentPlan plan, AgentContext ctx) {
        log.info("🤖 子任务[{}] {}（{}）", s.id(), s.title(), s.capability());
        try {
            // 按 action 归一化能力：LLM 可能标错 capability，以 action 为准更健壮
            String cap = normalizeCapability(s);
            Object value =
                switch (cap) {
                    case "TOOL" -> runTool(s.action(), argsFor(s, plan), ctx);
                    case "TOOL_CHAIN" -> runChain(s.action(), plan, ctx);
                    case "TOOL_LOOP" -> runLoop(s, plan, ctx);
                    case "SKILL" -> runSkill(s.action(), plan, ctx);
                    case "RAG" -> runRag(s.action(), ctx);
                    case "LLM" -> runLlm(s.action(), plan, ctx);
                    default -> "未知能力: " + cap;
                };
            ctx.putResult(s.outputKey(), value);
            log.info("✅ 子任务[{}] 完成: {}", s.id(), String.valueOf(value).length() > 80
                ? String.valueOf(value).substring(0, 80) + "..." : value);
        } catch (Exception e) {
            log.error("子任务[{}] 失败: {}", s.id(), e.getMessage());
            ctx.putError(s.outputKey(), e.getMessage());
        }
    }

    /** 按 action 归一化执行能力：只在标注可疑（TOOL 却要跑链式/循环等）时纠正，保留正确标注。 */
    private static String normalizeCapability(AgentSubtask s) {
        String cap = s.capability() == null ? "" : s.capability();
        String action = s.action() == null ? "" : s.action();
        // 正确的循环/链式标注优先保留
        if ("TOOL_LOOP".equals(cap) || "TOOL_CHAIN".equals(cap)
            || "SKILL".equals(cap) || "RAG".equals(cap) || "LLM".equals(cap)) {
            return cap;
        }
        // LLM 把特殊任务标成 TOOL 时，按 action 纠正
        if ("TOOL".equals(cap) || cap.isBlank()) {
            if ("sunrise".equals(action)) {
                return "TOOL_CHAIN";
            }
            if ("drive_safety".equals(action) || "safety".equals(action)) {
                return "RAG";
            }
            if ("budget".equals(action)) {
                return "SKILL";
            }
            if ("route_planning".equals(action)) {
                return "LLM";
            }
            if (action.startsWith("get_") || "generate_image".equals(action)) {
                return "TOOL";
            }
        }
        return cap.isBlank() ? "LLM" : cap;
    }

    // ------------------------------------------------------------------
    // TOOL：单次工具调用
    // ------------------------------------------------------------------

    private String runTool(String action, Map<String, Object> args, AgentContext ctx) {
        return tools.execute(new LlmClient.ToolCall("agent", action, json(args)), toToolContext(ctx));
    }

    private Map<String, Object> argsFor(AgentSubtask s, AgentPlan plan) {
        Map<String, Object> args = new LinkedHashMap<>();
        switch (s.action()) {
            case "generate_image" ->
                args.put("prompt", "为「" + plan.goal() + "」生成一张自驾路书封面插画，包含汽车与风景，风格清新明快");
            case "get_weather" -> args.put("city", plan.route().start());
            default -> { /* 无参数 */ }
        }
        return args;
    }

    // ------------------------------------------------------------------
    // TOOL_CHAIN：坐标 → 日出日落
    // ------------------------------------------------------------------

    private String runChain(String action, AgentPlan plan, AgentContext ctx) {
        if (!"sunrise".equals(action)) {
            return "未知链式任务: " + action;
        }
        String city = plan.route().start();
        Map<String, Object> coordArgs = new LinkedHashMap<>();
        coordArgs.put("city", city);
        String coord = tools.execute(
            new LlmClient.ToolCall("agent", "get_city_coordinates", json(coordArgs)), toToolContext(ctx));
        Matcher m = Pattern.compile("latitude=([-\\d.]+), longitude=([-\\d.]+)").matcher(coord);
        if (!m.find()) {
            return "无法解析坐标: " + coord;
        }
        Map<String, Object> sunArgs = new LinkedHashMap<>();
        sunArgs.put("latitude", Double.parseDouble(m.group(1)));
        sunArgs.put("longitude", Double.parseDouble(m.group(2)));
        return tools.execute(
            new LlmClient.ToolCall("agent", "get_sunrise_sunset", json(sunArgs)), toToolContext(ctx));
    }

    // ------------------------------------------------------------------
    // TOOL_LOOP：对结果列表循环调用同一工具
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object runLoop(AgentSubtask s, AgentPlan plan, AgentContext ctx) {
        Object listObj = ctx.getByPath(s.loopOver());
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return "（循环数据为空: " + s.loopOver() + "）";
        }
        Map<String, String> perCity = new LinkedHashMap<>();
        for (Object item : list) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("city", String.valueOf(item));
            perCity.put(String.valueOf(item), runTool(s.action(), args, ctx));
        }
        log.info("🔁 TOOL_LOOP 完成：{} 个城市", perCity.size());
        return perCity;
    }

    // ------------------------------------------------------------------
    // SKILL：预算（calculator）
    // ------------------------------------------------------------------

    private Object runSkill(String action, AgentPlan plan, AgentContext ctx) {
        if (!"budget".equals(action)) {
            return "未知技能任务: " + action;
        }
        int km = plan.route().totalKm();
        int days = Math.max(plan.route().days(), 1);
        long oil = Math.round(km / 100.0 * 8 * 8.0);        // 8L/100km × 8元/L
        long toll = Math.round(km * 0.5);                    // 0.5元/km
        long hotel = (days - 1) * 350L;
        long food = days * 150L;
        String expr = "计算 " + oil + "+" + toll + "+" + hotel + "+" + food;
        String result = calculator.execute(ctx.userId(), expr);   // 形如 "计算 2400+... = 3350"
        long total = extractTotal(result);
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("oil", oil);
        budget.put("toll", toll);
        budget.put("hotel", hotel);
        budget.put("food", food);
        budget.put("total", total);
        budget.put("kmKnown", km > 0);
        return budget;
    }

    private static long extractTotal(String s) {
        Matcher m = Pattern.compile("=\\s*(-?\\d+)").matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    // ------------------------------------------------------------------
    // RAG：自驾知识增强
    // ------------------------------------------------------------------

    private String runRag(String action, AgentContext ctx) {
        if (!"drive_safety".equals(action)) {
            return "未知 RAG 任务: " + action;
        }
        List<RagDocument> docs = ragService.retrieve("自驾 高原 安全 车辆 疲劳 山路 封路 注意事项");
        if (docs.isEmpty()) {
            return "（知识库未命中自驾内容）";
        }
        String enhanced = ragService.buildEnhancedPrompt("自驾游出行注意事项有哪些？请逐条给出建议", docs);
        return llm.chat(List.of(ChatMessage.user(enhanced)));
    }

    // ------------------------------------------------------------------
    // LLM：路线规划
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object runLlm(String action, AgentPlan plan, AgentContext ctx) {
        if (!"route_planning".equals(action)) {
            return "未知 LLM 任务: " + action;
        }
        String prompt =
            "根据目标「" + plan.goal() + "」输出路线 JSON（仅 JSON）："
                + "{\"start\":\"起点城市\",\"end\":\"终点城市\",\"cities\":[\"起点城市\",\"途经城市1\",\"...\",\"终点城市\"],\"totalKm\":数字,\"days\":数字}。"
                + "cities 必须以 start 开头、以 end 结尾；途经城市按真实地理顺序；总里程和天数做合理估算。";
        String reply = llm.chat(List.of(ChatMessage.user(prompt)));
        try {
            String json = reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1);
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("路线规划 JSON 解析失败，回退计划内路线: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("start", plan.route().start());
            fallback.put("end", plan.route().end());
            fallback.put("cities", plan.route().cities());
            fallback.put("totalKm", plan.route().totalKm());
            fallback.put("days", plan.route().days());
            return fallback;
        }
    }

    // ------------------------------------------------------------------

    private ToolContext toToolContext(AgentContext ctx) {
        return new ToolContext(ctx.userId(), ctx.sender());
    }

    private String json(Map<String, Object> args) {
        try {
            return mapper.writeValueAsString(args);
        } catch (Exception e) {
            return "{}";
        }
    }
}

package com.example.wechataiassistant.service.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.wechataiassistant.service.llm.LlmCache;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmProperties;
import com.example.wechataiassistant.service.rag.BotKnowledgeBase;
import com.example.wechataiassistant.service.rag.RagProperties;
import com.example.wechataiassistant.service.rag.RagService;
import com.example.wechataiassistant.service.skill.CalculatorSkill;
import com.example.wechataiassistant.service.tool.CurrentTimeTool;
import com.example.wechataiassistant.service.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 断点续跑：已完成步骤跳过、结果恢复、剩余步骤补齐。 */
class AgentResumeTest {

    private AgentRunStore store;
    private AgentExecutor executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String RUN_ID = "test-run";

    @BeforeEach
    void setUp() {
        store = new AgentRunStore(mapper);
        LlmProperties props = new LlmProperties();
        props.setApiKey(""); // 空 Key：若误执行 LLM 步骤会快速失败，便于断言
        LlmClient llm = new LlmClient(props, mapper, new LlmCache(props));
        RagService rag = new RagService(new RagProperties(), List.of(new BotKnowledgeBase()));
        ToolRegistry tools = new ToolRegistry(List.of(new CurrentTimeTool()), mapper);
        executor = new AgentExecutor(tools, new CalculatorSkill(), rag, llm, mapper);
    }

    private AgentPlan plan() {
        AgentPlan.RouteInfo route = new AgentPlan.RouteInfo("成都", "稻城", List.of("成都", "稻城"), 0, 3);
        List<AgentSubtask> subtasks = List.of(
            new AgentSubtask(1, "规划路线", "LLM", "route_planning", null, List.of(), "route"),
            new AgentSubtask(2, "估算预算", "SKILL", "budget", null, List.of(1), "budget"));
        return new AgentPlan("测试目标", route, subtasks);
    }

    @Test
    void resumeSkipsDoneSteps() {
        AgentPlan plan = plan();
        // 模拟中断：第 1 步已完成并落盘（结果 totalKm=900），第 2 步未执行
        AgentRunState st = store.create(RUN_ID, "测试目标", plan);
        st.setDoneIds(List.of(1));
        st.setResults(Map.of("route", Map.of("start", "成都", "end", "稻城",
            "cities", List.of("成都", "稻城"), "totalKm", 900, "days", 3)));
        store.save(st);

        AgentContext ctx = new AgentContext("u1", null);
        executor.execute(plan, ctx, RUN_ID, store);

        // 第 1 步未重跑（route 保持 checkpoint 值：totalKm=900）
        @SuppressWarnings("unchecked")
        Map<String, Object> route = (Map<String, Object>) ctx.getResult("route");
        assertEquals(900, ((Number) route.get("totalKm")).intValue(), "第1步应被跳过，route 保持 checkpoint 值");

        // 第 2 步被补齐（预算基于 900km 计算 > 0）
        @SuppressWarnings("unchecked")
        Map<String, Object> budget = (Map<String, Object>) ctx.getResult("budget");
        assertNotNull(budget, "第2步应继续执行");
        assertTrue(((Number) budget.get("total")).longValue() > 0, "预算应基于 900km 计算");

        // 完成后 checkpoint 由服务层清除（此处验证 store 可查）
        assertNotNull(store.get(RUN_ID));
    }

    @Test
    void storeRoundTrip() {
        AgentPlan plan = plan();
        AgentRunState st = store.create(RUN_ID, "目标", plan);
        st.setDoneIds(List.of(1, 2));
        st.setResults(Map.of("route", Map.of("totalKm", 100)));
        store.save(st);

        AgentRunState loaded = store.get(RUN_ID);
        assertNotNull(loaded);
        assertEquals(RUN_ID, loaded.getRunId());
        assertEquals(2, loaded.getDoneIds().size());
        assertTrue(loaded.getPlanJson().contains("route_planning"));
        store.complete(RUN_ID);
        assertNull(store.get(RUN_ID), "完成后应清除");
    }
}

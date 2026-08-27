package com.example.wechataiassistant.service.agent;

import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 规划器：把用户的一句话目标拆解为结构化子任务计划。
 *
 * <p>流程：先让 LLM 输出完整计划（含路线与子任务）；解析失败时回退到
 * 固定自驾模板（路线用正则从目标提取），保证任何输入都能产出 ≥4 个子任务。</p>
 */
@Component
public class AgentPlanner {

    private static final Logger log = LoggerFactory.getLogger(AgentPlanner.class);

    private final LlmClient llm;
    private final ObjectMapper mapper;

    public AgentPlanner(LlmClient llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public AgentPlan plan(String goal) {
        AgentPlan fromLlm = tryLlmPlan(goal);
        if (fromLlm != null) {
            return fromLlm;
        }
        log.warn("LLM 计划解析失败，回退到自驾模板。目标: {}", goal);
        return fallbackPlan(goal);
    }

    /** LLM 拆解：输出 {route, subtasks} JSON。 */
    private AgentPlan tryLlmPlan(String goal) {
        String prompt =
            """
            你是行程规划 Agent。根据用户目标，输出一份可执行的子任务计划，严格只输出 JSON。

            用户目标：%s

            可用能力(capability)：TOOL、TOOL_CHAIN、TOOL_LOOP、SKILL、RAG、LLM
            可用 action：route_planning、get_weather、sunrise、budget、drive_safety、generate_image、assemble
            loopOver 用于 TOOL_LOOP，值如 "route.cities"，表示循环遍历上一步输出的城市列表。

            输出格式（严格 JSON，无其他文字）：
            {"route":{"start":"起点城市","end":"终点城市","cities":["途经城市1","途经城市2","终点城市"],"totalKm":数字,"days":数字},
             "subtasks":[{"id":1,"title":"规划路线与途经城市","capability":"LLM","action":"route_planning","dependsOn":[],"outputKey":"route"},
             {"id":2,"title":"查询每个途经城市天气","capability":"TOOL_LOOP","action":"get_weather","loopOver":"route.cities","dependsOn":[1],"outputKey":"weather"},
             {"id":3,"title":"查询起点日出日落","capability":"TOOL_CHAIN","action":"sunrise","dependsOn":[1],"outputKey":"sun"},
             {"id":4,"title":"估算预算","capability":"SKILL","action":"budget","dependsOn":[1],"outputKey":"budget"},
             {"id":5,"title":"自驾安全知识","capability":"RAG","action":"drive_safety","dependsOn":[],"outputKey":"safety"},
             {"id":6,"title":"生成路书封面","capability":"TOOL","action":"generate_image","dependsOn":[1],"outputKey":"poster"}]}

            要求：必须输出参考模板中的全部 6 个子任务（不可删减，可微调标题/参数）；
            subtasks 至少 6 个；id 从 1 递增不重复；dependsOn 只能是已存在的更小 id；outputKey 唯一；
            路线 cities 必须以起点城市开头、以终点城市结尾，按真实地理顺序。
            """.formatted(goal);

        try {
            String reply = llm.chat(List.of(ChatMessage.user(prompt)));
            String json = extractJson(reply);
            JsonNode root = mapper.readTree(json);
            AgentPlan.RouteInfo route = parseRoute(root.path("route"), goal);
            List<AgentSubtask> subtasks = parseSubtasks(root.path("subtasks"));
            if (subtasks.size() >= 6) {
                log.info("🧠 LLM 计划成功：{} 个子任务", subtasks.size());
                return new AgentPlan(goal, route, subtasks);
            }
        } catch (Exception e) {
            log.warn("LLM 计划解析失败: {}", e.getMessage());
        }
        return null;
    }

    private AgentPlan fallbackPlan(String goal) {
        AgentPlan.RouteInfo route = parseRouteFromGoal(goal);
        return new AgentPlan(goal, route, templateSubtasks());
    }

    /** 自驾固定模板（LLM 计划失败时的兜底，同样 ≥6 个子任务）。 */
    private List<AgentSubtask> templateSubtasks() {
        List<AgentSubtask> list = new ArrayList<>();
        list.add(new AgentSubtask(1, "规划路线与途经城市", "LLM", "route_planning", null, List.of(), "route"));
        list.add(new AgentSubtask(2, "查询每个途经城市天气", "TOOL_LOOP", "get_weather", "route.cities", List.of(1), "weather"));
        list.add(new AgentSubtask(3, "查询起点日出日落", "TOOL_CHAIN", "sunrise", null, List.of(1), "sun"));
        list.add(new AgentSubtask(4, "估算预算", "SKILL", "budget", null, List.of(1), "budget"));
        list.add(new AgentSubtask(5, "自驾安全知识", "RAG", "drive_safety", null, List.of(), "safety"));
        list.add(new AgentSubtask(6, "生成路书封面", "TOOL", "generate_image", null, List.of(1), "poster"));
        return list;
    }

    private AgentPlan.RouteInfo parseRoute(JsonNode route, String goal) {
        if (route == null || route.isMissingNode()) {
            return parseRouteFromGoal(goal);
        }
        List<String> cities = new ArrayList<>();
        for (JsonNode c : route.path("cities")) {
            String s = c.asText("");
            if (!s.isBlank()) {
                cities.add(s);
            }
        }
        if (cities.isEmpty()) {
            return parseRouteFromGoal(goal);
        }
        return new AgentPlan.RouteInfo(
            route.path("start").asText(cities.get(0)),
            route.path("end").asText(cities.get(cities.size() - 1)),
            cities,
            route.path("totalKm").asInt(0),
            route.path("days").asInt(3));
    }

    private List<AgentSubtask> parseSubtasks(JsonNode arr) {
        List<AgentSubtask> list = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return list;
        }
        for (JsonNode n : arr) {
            List<Integer> deps = new ArrayList<>();
            for (JsonNode d : n.path("dependsOn")) {
                deps.add(d.asInt());
            }
            list.add(new AgentSubtask(
                n.path("id").asInt(0),
                n.path("title").asText(""),
                n.path("capability").asText(""),
                n.path("action").asText(""),
                n.path("loopOver").asText(null),
                deps,
                n.path("outputKey").asText("")));
        }
        return list;
    }

    /** 从目标里用正则提取 起点→终点（从X到Y / X到Y / X至Y）。 */
    private AgentPlan.RouteInfo parseRouteFromGoal(String goal) {
        Matcher m = Pattern.compile("(?:从|自)?([\\u4e00-\\u9fa5A-Za-z]{2,6})(?:到|至|去|前往)([\\u4e00-\\u9fa5A-Za-z]{2,6})").matcher(goal);
        if (m.find()) {
            String start = m.group(1);
            String end = m.group(2);
            return new AgentPlan.RouteInfo(start, end, List.of(start, end), 0, 3);
        }
        return AgentPlan.RouteInfo.empty();
    }

    /** 从 LLM 回复里提取第一个完整 JSON 对象（去掉代码块和多余文字）。 */
    private String extractJson(String reply) {
        if (reply == null) {
            throw new LlmException("空回复");
        }
        String s = reply.replace("```json", "").replace("```", "").trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("未找到 JSON");
        }
        return s.substring(start, end + 1);
    }
}

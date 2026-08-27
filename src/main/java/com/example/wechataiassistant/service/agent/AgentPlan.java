package com.example.wechataiassistant.service.agent;

import java.util.List;

/** Agent 计划：目标 + 路线信息 + 子任务列表。 */
public record AgentPlan(String goal, RouteInfo route, List<AgentSubtask> subtasks) {

    /** 路线信息（由 Planner 从目标中解析/生成）。 */
    public record RouteInfo(String start, String end, List<String> cities, int totalKm, int days) {
        public static RouteInfo empty() {
            return new RouteInfo("", "", List.of(), 0, 3);
        }
    }
}

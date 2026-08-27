package com.example.wechataiassistant.service.agent;

import java.util.List;

/** 子任务（Agent 计划的一步）。 */
public record AgentSubtask(
    int id,
    String title,
    /** TOOL / TOOL_CHAIN / TOOL_LOOP / SKILL / RAG / LLM */
    String capability,
    String action,
    /** TOOL_LOOP 时：结果路径，如 route.cities */
    String loopOver,
    List<Integer> dependsOn,
    /** 结果存入 AgentContext 的键名 */
    String outputKey) {}

package com.example.wechataiassistant.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 长任务 Agent 门面：一句话目标 → 完整路书成品。
 *
 * <p>闭环：Plan（LLM 拆解 ≥4 子任务）→ Execute（工具/Skill/RAG/LLM 依赖执行，
 * 含 TOOL_LOOP 多城市循环）→ Assemble（固定章节模板成文）。</p>
 */
@Service
public class RoadTripAgentService {

    private static final Logger log = LoggerFactory.getLogger(RoadTripAgentService.class);

    private final AgentPlanner planner;
    private final AgentExecutor executor;
    private final AgentAssembler assembler;

    public RoadTripAgentService(AgentPlanner planner, AgentExecutor executor, AgentAssembler assembler) {
        this.planner = planner;
        this.executor = executor;
        this.assembler = assembler;
    }

    /** 执行完整闭环，返回路书文档。 */
    public String execute(String goal, AgentContext ctx) {
        log.info("🤖 Agent 开始执行目标: {}", goal);
        AgentPlan plan = planner.plan(goal);
        log.info("📋 计划：{} 个子任务，路线 {} → {}", plan.subtasks().size(),
            plan.route().start(), plan.route().end());
        executor.execute(plan, ctx);
        String doc = assembler.assemble(plan, ctx);
        log.info("🤖 Agent 完成，成品 {} 字符", doc.length());
        return doc;
    }
}

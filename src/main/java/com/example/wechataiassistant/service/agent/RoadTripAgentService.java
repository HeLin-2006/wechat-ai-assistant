package com.example.wechataiassistant.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 长任务 Agent 门面：一句话目标 → 完整路书成品。
 *
 * <p>闭环：Plan（LLM 拆解 ≥6 子任务）→ Execute（工具/Skill/RAG/LLM 依赖执行，
 * 含 TOOL_LOOP 多城市循环、断点续跑）→ Assemble（固定章节模板成文）。</p>
 */
@Service
public class RoadTripAgentService {

    private static final Logger log = LoggerFactory.getLogger(RoadTripAgentService.class);

    private final AgentPlanner planner;
    private final AgentExecutor executor;
    private final AgentAssembler assembler;
    private final AgentRunStore store;
    private final tools.jackson.databind.ObjectMapper mapper;

    public RoadTripAgentService(
        AgentPlanner planner,
        AgentExecutor executor,
        AgentAssembler assembler,
        AgentRunStore store,
        tools.jackson.databind.ObjectMapper mapper) {
        this.planner = planner;
        this.executor = executor;
        this.assembler = assembler;
        this.store = store;
        this.mapper = mapper;
    }

    /** 执行结果：成品文档 + 是否断点续跑。 */
    public record AgentExecution(String document, boolean resumed) {}

    /**
     * 执行完整闭环。runId 相同的未完成任务会自动续跑（跳过已完成步骤）。
     */
    public AgentExecution execute(String goal, AgentContext ctx, String runId) {
        log.info("🤖 Agent 开始执行目标: {} runId={}", goal, runId);

        boolean resumed = false;
        AgentRunState st = runId == null ? null : store.get(runId);
        AgentPlan plan;
        if (st != null && st.getPlanJson() != null && !st.getPlanJson().isBlank()) {
            // 续跑：复用已规划的计划（保证计划一致），跳过已完成步骤
            plan = parsePlan(st.getPlanJson());
            if (plan == null) {
                plan = planner.plan(goal);
            }
            resumed = st.getDoneIds() != null && !st.getDoneIds().isEmpty();
            log.info("♻️ 续跑 runId={}，已完成 {} 步", runId, st.getDoneIds() == null ? 0 : st.getDoneIds().size());
        } else {
            plan = planner.plan(goal);
            if (runId != null) {
                store.create(runId, goal, plan);
            }
        }

        executor.execute(plan, ctx, runId, store);

        String doc = assembler.assemble(plan, ctx);
        if (runId != null) {
            store.complete(runId);
        }
        log.info("🤖 Agent 完成（{}），成品 {} 字符", resumed ? "续跑" : "全新", doc.length());
        return new AgentExecution(doc, resumed);
    }

    private AgentPlan parsePlan(String planJson) {
        try {
            return mapper.readValue(planJson, AgentPlan.class);
        } catch (Exception e) {
            log.error("恢复计划失败，将重新规划: {}", e.getMessage());
            return null;
        }
    }
}

package com.example.wechataiassistant.service.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 运行存储（断点续跑）：内存 + 文件双持久化。
 *
 * <p>每完成一个子任务就保存一次 checkpoint 到 agent-checkpoints/{runId}.json，
 * 中断/重启后可从中断处续跑；完成后清除。</p>
 */
@Component
public class AgentRunStore {

    private static final Logger log = LoggerFactory.getLogger(AgentRunStore.class);

    private final ObjectMapper mapper;
    private final Map<String, AgentRunState> runs = new ConcurrentHashMap<>();
    private final Path dir = Path.of("agent-checkpoints");

    public AgentRunStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AgentRunState get(String runId) {
        if (runId == null) {
            return null;
        }
        AgentRunState st = runs.get(runId);
        if (st != null) {
            return st;
        }
        return loadFromFile(runId);
    }

    /** 创建新运行并落盘。 */
    public AgentRunState create(String runId, String goal, AgentPlan plan) {
        AgentRunState st = new AgentRunState();
        st.setRunId(runId);
        st.setGoal(goal);
        st.setStatus("RUNNING");
        long now = System.currentTimeMillis();
        st.setCreatedAt(now);
        st.setUpdatedAt(now);
        try {
            st.setPlanJson(mapper.writeValueAsString(plan));
        } catch (Exception e) {
            log.warn("序列化计划失败: {}", e.getMessage());
        }
        runs.put(runId, st);
        save(st);
        log.info("🆕 Agent 运行创建 runId={}", runId);
        return st;
    }

    /** 保存（内存 + 文件）。 */
    public void save(AgentRunState st) {
        st.setUpdatedAt(System.currentTimeMillis());
        runs.put(st.getRunId(), st);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(st.getRunId() + ".json"), mapper.writeValueAsString(st));
        } catch (IOException e) {
            log.warn("Agent checkpoint 保存失败: {}", e.getMessage());
        }
    }

    /** 完成后清除。 */
    public void complete(String runId) {
        runs.remove(runId);
        try {
            Files.deleteIfExists(dir.resolve(runId + ".json"));
        } catch (IOException e) {
            log.warn("Agent checkpoint 删除失败: {}", e.getMessage());
        }
        log.info("🏁 Agent 运行完成并清除 checkpoint runId={}", runId);
    }

    /** 清理超过 maxAge 的未完成任务（定时任务调用）。 */
    public int cleanupExpired(Duration maxAge) {
        long cutoff = System.currentTimeMillis() - maxAge.toMillis();
        int n = 0;
        for (AgentRunState st : runs.values()) {
            if (st.getUpdatedAt() < cutoff) {
                complete(st.getRunId());
                n++;
            }
        }
        if (n > 0) {
            log.info("🧹 清理过期 Agent 运行 {} 个", n);
        }
        return n;
    }

    private AgentRunState loadFromFile(String runId) {
        try {
            Path p = dir.resolve(runId + ".json");
            if (!Files.exists(p)) {
                return null;
            }
            AgentRunState st = mapper.readValue(Files.readString(p), AgentRunState.class);
            runs.put(runId, st);
            return st;
        } catch (Exception e) {
            log.warn("Agent checkpoint 读取失败 {}: {}", runId, e.getMessage());
            return null;
        }
    }
}

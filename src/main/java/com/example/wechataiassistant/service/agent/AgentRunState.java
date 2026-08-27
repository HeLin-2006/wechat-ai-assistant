package com.example.wechataiassistant.service.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 运行状态（断点续跑）：计划 + 已完成的步骤与结果。 */
public class AgentRunState {

    private String runId;
    private String goal;
    /** 计划的 JSON（续跑时复用，避免重新规划导致计划漂移）。 */
    private String planJson;
    private Map<String, Object> results = new LinkedHashMap<>();
    private Map<String, String> errors = new LinkedHashMap<>();
    private List<Integer> doneIds = new ArrayList<>();
    private String status = "RUNNING"; // RUNNING / DONE
    private long createdAt;
    private long updatedAt;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public Map<String, Object> getResults() {
        return results;
    }

    public void setResults(Map<String, Object> results) {
        this.results = results == null ? new LinkedHashMap<>() : results;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors == null ? new LinkedHashMap<>() : errors;
    }

    public List<Integer> getDoneIds() {
        return doneIds;
    }

    public void setDoneIds(List<Integer> doneIds) {
        this.doneIds = doneIds == null ? new ArrayList<>() : doneIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.example.wechataiassistant.service.agent;

import com.example.wechataiassistant.service.tool.MessageSender;
import java.util.LinkedHashMap;
import java.util.Map;

/** Agent 执行上下文：用户、发送器、逐步结果与错误收集。 */
public class AgentContext {

    private final String userId;
    private final MessageSender sender;
    private final Map<String, Object> results = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();

    public AgentContext(String userId, MessageSender sender) {
        this.userId = userId;
        this.sender = sender;
    }

    public String userId() {
        return userId;
    }

    public boolean hasSender() {
        return sender != null;
    }

    public MessageSender sender() {
        return sender;
    }

    public void putResult(String key, Object value) {
        if (key != null && !key.isBlank()) {
            results.put(key, value);
        }
    }

    public Object getResult(String key) {
        return key == null ? null : results.get(key);
    }

    /** 按路径取结果，如 route.cities。 */
    @SuppressWarnings("unchecked")
    public Object getByPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Object cur = results;
        for (String part : path.split("\\.")) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(part);
            } else {
                return null;
            }
        }
        return cur;
    }

    public void putError(String key, String message) {
        errors.put(key, message);
    }

    public Map<String, String> errors() {
        return errors;
    }

    /** 导出结果副本（供 checkpoint 持久化）。 */
    public Map<String, Object> exportResults() {
        return new LinkedHashMap<>(results);
    }
}

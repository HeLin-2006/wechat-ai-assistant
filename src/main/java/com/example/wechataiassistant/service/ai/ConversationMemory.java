package com.example.wechataiassistant.service.ai;

import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按用户维护最近若干轮对话（仅文本），用于给 LLM 提供上下文。
 *
 * <p>双预算裁剪（省 token）：</p>
 * <ul>
 *   <li>条数预算：超过 llm.context-window 丢最旧</li>
 *   <li>字符预算：总字符超过 llm.context-max-chars 丢最旧（估算 token 数）</li>
 * </ul>
 */
@Component
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

    private final LlmProperties props;
    private final Map<String, Deque<ChatMessage>> store = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAccess = new ConcurrentHashMap<>();

    public ConversationMemory(LlmProperties props) {
        this.props = props;
    }

    public List<ChatMessage> history(String userId) {
        Deque<ChatMessage> deque = store.get(userId);
        if (deque == null) {
            return List.of();
        }
        lastAccess.put(userId, Instant.now());
        return new ArrayList<>(deque);
    }

    public void add(String userId, ChatMessage message) {
        lastAccess.put(userId, Instant.now());
        Deque<ChatMessage> deque = store.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(message);
            prune(deque);
        }
    }

    /** 双预算裁剪：条数 + 字符数。 */
    private void prune(Deque<ChatMessage> deque) {
        int window = Math.max(props.getContextWindow(), 2);
        while (deque.size() > window) {
            deque.removeFirst();
        }
        int maxChars = props.getContextMaxChars();
        if (maxChars > 0) {
            int total = 0;
            for (ChatMessage m : deque) {
                total += m.content() == null ? 0 : m.content().length();
            }
            while (total > maxChars && deque.size() > 1) {
                ChatMessage removed = deque.removeFirst();
                total -= removed.content() == null ? 0 : removed.content().length();
            }
        }
    }

    public void clear(String userId) {
        store.remove(userId);
        lastAccess.remove(userId);
    }

    /** 清理超过 idle 时长未活动的用户上下文（定时任务调用，防内存增长）。 */
    public int clearIdle(Duration idle) {
        Instant cutoff = Instant.now().minus(idle);
        List<String> stale =
            lastAccess.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
        for (String u : stale) {
            store.remove(u);
            lastAccess.remove(u);
        }
        if (!stale.isEmpty()) {
            log.info("🧹 清理空闲会话 {} 个", stale.size());
        }
        return stale.size();
    }
}

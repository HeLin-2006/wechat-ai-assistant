package com.example.wechataiassistant.service.ai;

import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmProperties;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 按用户维护最近若干轮对话（仅文本），用于给 LLM 提供上下文。 */
@Component
public class ConversationMemory {

    private final LlmProperties props;
    private final Map<String, Deque<ChatMessage>> store = new ConcurrentHashMap<>();

    public ConversationMemory(LlmProperties props) {
        this.props = props;
    }

    public List<ChatMessage> history(String userId) {
        Deque<ChatMessage> deque = store.get(userId);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    public void add(String userId, ChatMessage message) {
        Deque<ChatMessage> deque = store.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(message);
            int window = Math.max(props.getContextWindow(), 2);
            while (deque.size() > window) {
                deque.removeFirst();
            }
        }
    }

    public void clear(String userId) {
        store.remove(userId);
    }
}

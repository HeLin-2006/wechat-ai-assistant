package com.example.wechataiassistant.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmProperties;
import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    private ConversationMemory memory(int window, int maxChars) {
        LlmProperties props = new LlmProperties();
        props.setContextWindow(window);
        props.setContextMaxChars(maxChars);
        return new ConversationMemory(props);
    }

    @Test
    void windowBudgetTrimsOldest() {
        ConversationMemory m = memory(3, 0);
        for (int i = 1; i <= 5; i++) {
            m.add("u1", ChatMessage.user("消息" + i));
        }
        assertEquals(3, m.history("u1").size());
        assertEquals("消息3", m.history("u1").get(0).content());
    }

    @Test
    void charBudgetTrimsOldest() {
        // 每条约 10 字符，总预算 25 → 最多保留 2~3 条
        ConversationMemory m = memory(10, 25);
        for (int i = 1; i <= 4; i++) {
            m.add("u1", ChatMessage.user("这是一条测试消息" + i));
        }
        int total = m.history("u1").stream().mapToInt(x -> x.content().length()).sum();
        assertTrue(total <= 25, "字符预算应生效: total=" + total);
        assertEquals("这是一条测试消息4", m.history("u1").get(m.history("u1").size() - 1).content());
    }

    @Test
    void perUserIsolation() {
        ConversationMemory m = memory(3, 0);
        m.add("u1", ChatMessage.user("你好"));
        m.add("u2", ChatMessage.user("hi"));
        assertEquals(1, m.history("u1").size());
        assertEquals(1, m.history("u2").size());
        m.clear("u1");
        assertTrue(m.history("u1").isEmpty());
        assertEquals(1, m.history("u2").size());
    }
}

package com.example.wechataiassistant.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LlmCacheTest {

    private LlmCache cache(int maxEntries, int ttlMinutes, boolean enabled) {
        LlmProperties props = new LlmProperties();
        props.setCacheEnabled(enabled);
        props.setCacheMaxEntries(maxEntries);
        props.setCacheTtlMinutes(ttlMinutes);
        return new LlmCache(props);
    }

    @Test
    void hitAndMiss() {
        LlmCache c = cache(10, 10, true);
        assertNull(c.get("q1"), "未缓存应 miss");
        c.put("q1", "回答1");
        assertEquals("回答1", c.get("q1"));
        assertEquals(1, c.size());
    }

    @Test
    void disabledReturnsNull() {
        LlmCache c = cache(10, 10, false);
        c.put("q1", "回答1");
        assertNull(c.get("q1"));
    }

    @Test
    void overLimitClears() {
        LlmCache c = cache(2, 10, true);
        c.put("a", "1");
        c.put("b", "2");
        c.put("c", "3"); // 触发清空
        assertEquals(1, c.size());
        assertNull(c.get("a"));
    }
}

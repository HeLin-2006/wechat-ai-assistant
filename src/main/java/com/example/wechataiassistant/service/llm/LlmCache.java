package com.example.wechataiassistant.service.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 响应缓存（LRU 风格 + TTL）：
 * 相同问题在 TTL 内直接返回缓存结果，省 token 且毫秒级响应。
 */
@Component
public class LlmCache {

    private static final Logger log = LoggerFactory.getLogger(LlmCache.class);

    private final LlmProperties props;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    private record Entry(String reply, long expireAt) {}

    public LlmCache(LlmProperties props) {
        this.props = props;
    }

    public boolean enabled() {
        return props.isCacheEnabled();
    }

    public String get(String key) {
        if (!enabled()) {
            return null;
        }
        Entry e = cache.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expireAt()) {
            cache.remove(key);
            return null;
        }
        return e.reply();
    }

    public void put(String key, String reply) {
        if (!enabled() || key == null || reply == null) {
            return;
        }
        if (cache.size() >= props.getCacheMaxEntries()) {
            cache.clear(); // 极简 LRU：超限整体清空（缓存量级小，可接受）
            log.info("LLM 缓存达到上限 {}，已清空", props.getCacheMaxEntries());
        }
        cache.put(key, new Entry(reply, System.currentTimeMillis() + props.getCacheTtlMinutes() * 60_000L));
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}

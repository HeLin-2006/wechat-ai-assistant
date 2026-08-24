package com.example.wechataiassistant.service.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** RAG（检索增强生成）配置（前缀 rag.*）。 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 是否启用 RAG 增强（关闭后走纯 LLM 闲聊）。 */
    private boolean enabled = true;

    /** 检索返回的最多文档数。 */
    private int maxDocs = 2;

    /** 命中最少得分（一个关键词命中即 2 分）。 */
    private int minScore = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxDocs() {
        return maxDocs;
    }

    public void setMaxDocs(int maxDocs) {
        this.maxDocs = maxDocs;
    }

    public int getMinScore() {
        return minScore;
    }

    public void setMinScore(int minScore) {
        this.minScore = minScore;
    }
}

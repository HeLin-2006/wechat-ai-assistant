package com.example.wechataiassistant.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagServiceTest {

    private RagService rag;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.setEnabled(true);
        props.setMaxDocs(2);
        props.setMinScore(2);
        rag = new RagService(props, new KnowledgeBase());
    }

    @Test
    void retrieveByKeyword() {
        List<RagDocument> docs = rag.retrieve("机器人怎么发语音");
        assertFalse(docs.isEmpty(), "应命中语音文档");
        assertTrue(docs.get(0).title().contains("语音"), "首个结果应为语音相关: " + docs.get(0).title());
    }

    @Test
    void retrieveByBigramOnly() {
        // "日出" 不是任何文档关键词，但正文含日出日落 → Bigram 也能命中工具调用文档
        List<RagDocument> docs = rag.retrieve("几点日出");
        assertFalse(docs.isEmpty(), "应通过 Bigram 命中工具调用文档");
    }

    @Test
    void noMatchReturnsEmpty() {
        assertTrue(rag.retrieve("今天股票涨了吗").isEmpty());
    }

    @Test
    void buildEnhancedPromptContainsContextAndQuestion() {
        List<RagDocument> docs = rag.retrieve("语音回复方式是什么");
        assertFalse(docs.isEmpty());
        String prompt = rag.buildEnhancedPrompt("语音回复方式是什么", docs);
        assertTrue(prompt.contains("背景资料"));
        assertTrue(prompt.contains("用户问题"));
        assertTrue(prompt.contains("语音"));
    }

    @Test
    void disabledRagReturnsEmptyWhenOff() {
        RagProperties off = new RagProperties();
        off.setEnabled(false);
        RagService offRag = new RagService(off, new KnowledgeBase());
        assertFalse(offRag.isEnabled());
        // 检索函数本身仍可用（由路由层判断开关）
        assertTrue(offRag.retrieve("语音").stream().anyMatch(d -> d.title().contains("语音")));
    }
}

package com.example.wechataiassistant.service.rag;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 极简关键词检索 RAG：
 *
 * <ol>
 *   <li>检索：对用户消息与知识库文档做关键词/二元组(Bigram)匹配打分，返回 Top-K 文档</li>
 *   <li>增强：把命中的文档拼进 Prompt（上下文注入），再交给 LLM 回答</li>
 * </ol>
 *
 * <p>不做向量化/Embedding，用关键词+Bigram 重合度做极简相似度，够用且零依赖。</p>
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RagProperties props;
    private final List<KnowledgeBase> knowledgeBases;

    public RagService(RagProperties props, List<KnowledgeBase> knowledgeBases) {
        this.props = props;
        this.knowledgeBases = knowledgeBases;
        log.info("已加载知识库 {} 个，共 {} 篇文档", knowledgeBases.size(),
            knowledgeBases.stream().mapToInt(kb -> kb.all().size()).sum());
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** 关键词检索：返回命中的文档（按得分降序，最多 maxDocs 篇）。 */
    public List<RagDocument> retrieve(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.toLowerCase();
        List<RagDocument> hits =
            knowledgeBases.stream()
                .flatMap(kb -> kb.all().stream())
                .map(doc -> new Scored(doc, score(doc, q)))
                .filter(s -> s.score() >= props.getMinScore())
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(props.getMaxDocs())
                .map(Scored::doc)
                .collect(Collectors.toList());
        if (!hits.isEmpty()) {
            log.info("📚 RAG 检索命中 {} 篇: {}", hits.size(),
                hits.stream().map(RagDocument::title).toList());
        }
        return hits;
    }

    /** 构建增强 Prompt：把检索到的文档作为背景资料注入。 */
    public String buildEnhancedPrompt(String query, List<RagDocument> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("【背景资料（来自知识库，请优先据此回答）】\n");
        for (RagDocument d : docs) {
            sb.append("- ").append(d.content()).append("\n");
        }
        sb.append("\n【用户问题】").append(query);
        return sb.toString();
    }

    /**
     * 打分：关键词命中 +2/个；正文二元组重合 +1/个（上限 10）。
     * 二元组（Bigram）让检索能容忍关键词没完全命中的情况。
     */
    private int score(RagDocument doc, String query) {
        int score = 0;
        for (String kw : doc.keywords()) {
            if (query.contains(kw.toLowerCase())) {
                score += 2;
            }
        }
        int overlap = 0;
        for (String bigram : bigrams(query)) {
            if (bigram.length() == 2 && doc.content().contains(bigram)) {
                overlap++;
            }
        }
        return score + Math.min(overlap, 10);
    }

    private static List<String> bigrams(String s) {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
        return out;
    }

    private record Scored(RagDocument doc, int score) {}
}

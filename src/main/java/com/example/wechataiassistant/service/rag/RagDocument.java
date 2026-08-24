package com.example.wechataiassistant.service.rag;

import java.util.List;

/** 知识库文档：标题 + 关键词 + 正文。 */
public record RagDocument(String id, String title, List<String> keywords, String content) {}

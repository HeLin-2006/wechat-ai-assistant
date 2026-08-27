package com.example.wechataiassistant.service.rag;

import java.util.List;

/** 知识库接口：一个主题的文档集合。 */
public interface KnowledgeBase {

    List<RagDocument> all();
}

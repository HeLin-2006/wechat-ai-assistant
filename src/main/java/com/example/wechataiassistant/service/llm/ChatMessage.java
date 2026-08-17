package com.example.wechataiassistant.service.llm;

/** 一条对话消息（OpenAI 兼容格式）。 */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}

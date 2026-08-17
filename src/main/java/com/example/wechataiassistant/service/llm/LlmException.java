package com.example.wechataiassistant.service.llm;

/** LLM 调用失败时抛出。 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}

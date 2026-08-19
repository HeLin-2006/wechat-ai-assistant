package com.example.wechataiassistant.service.tool;

/** 工具执行上下文：告诉工具"是谁在调用、结果发给谁"。 */
public record ToolContext(String userId, MessageSender sender) {

    /** 是否带发送器（在微信机器人场景下为 true）。 */
    public boolean hasSender() {
        return sender != null;
    }
}

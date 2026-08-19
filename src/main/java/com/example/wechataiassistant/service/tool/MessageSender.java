package com.example.wechataiassistant.service.tool;

/**
 * 消息发送回调：让工具能主动给用户发消息（例如生图工具把图片直接发给用户）。
 * 由调用方（AiMessageHandler）实现，把发送动作桥接到微信。
 */
public interface MessageSender {

    void sendText(String text);

    void sendImage(byte[] imageBytes, String fileName, String caption);
}

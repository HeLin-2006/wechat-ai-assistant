package com.example.wechataiassistant.service.ai;

/** 意图识别结果。 */
public record IntentResult(
    Intent intent,
    /** 附加内容：图片生成提示词 / 语音朗读文本 / 语音模式命令原文 */
    String payload,
    /** 天气意图提取出的城市（可能为 null，使用默认城市） */
    String city,
    /** 天气意图提取出的时间限定（默认今天） */
    TimeQualifier time) {

    public static IntentResult simple(Intent intent) {
        return new IntentResult(intent, null, null, TimeQualifier.TODAY);
    }

    public static IntentResult withPayload(Intent intent, String payload) {
        return new IntentResult(intent, payload, null, TimeQualifier.TODAY);
    }
}

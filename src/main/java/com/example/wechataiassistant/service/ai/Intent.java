package com.example.wechataiassistant.service.ai;

/** 用户消息意图分类。 */
public enum Intent {
    /** 天气查询（携带城市/时间） */
    WEATHER,
    /** 长任务 Agent（一句话目标 → 自主规划执行，如「规划一次自驾游」） */
    AGENT,
    /** 图片生成（/img、画…） */
    IMAGE_GEN,
    /** 语音朗读/语音回复（/语音、/voice） */
    VOICE_SPEAK,
    /** 语音模式开关（/语音模式、/语音开、/语音关） */
    VOICE_MODE,
    /** 清空上下文（/clear） */
    CLEAR,
    /** 帮助（/help） */
    HELP,
    /** 普通对话（LLM 回复） */
    CHAT
}

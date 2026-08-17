package com.example.wechataiassistant.voice;

/** 语音编码失败（如缺少 ffmpeg / silk_encoder）。 */
public class VoiceEncodeException extends Exception {

    public VoiceEncodeException(String message) {
        super(message);
    }

    public VoiceEncodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

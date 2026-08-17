package com.example.wechataiassistant.service.llm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型（LLM）相关配置（前缀 llm.*）。
 *
 * <p>所有接口均采用 OpenAI 兼容协议（/chat/completions、/images/generations、/audio/speech），
 * 因此 base-url 可指向 OpenAI、DeepSeek、智谱 BigModel 等任何兼容服务。</p>
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** API Key（建议通过环境变量 LLM_API_KEY 注入，不要提交到仓库）。 */
    private String apiKey = "";

    /** OpenAI 兼容接口的 base url。 */
    private String baseUrl = "https://api.openai.com/v1";

    /** 文本/视觉对话模型。 */
    private String chatModel = "gpt-4o-mini";

    /** 图片生成模型。 */
    private String imageModel = "gpt-image-1";

    /** 语音合成（TTS）模型。 */
    private String ttsModel = "gpt-4o-mini-tts";

    /** TTS 音色。 */
    private String ttsVoice = "alloy";

    /** 对话采样温度。 */
    private double chatTemperature = 0.7;

    /** 单次回复最大 token 数。 */
    private int chatMaxTokens = 1024;

    /** 系统提示词。 */
    private String systemPrompt =
        "你是一个运行在微信里的智能 AI 助手。请始终用简体中文、简洁友好地回答问题。"
            + "用户可能会发送文字、图片或语音：收到图片时请描述图片内容；语音内容已经转为文字。";

    /** 每用户保留的上下文轮数（条消息数）。 */
    private int contextWindow = 10;

    /** 触发图片生成的文本前缀（逗号分隔），例如「/img 一只猫」「画一只猫」。 */
    private List<String> imagePrefixes = new ArrayList<>(List.of("/img", "/image", "画", "生成图片"));

    /** 触发语音回复的文本前缀（逗号分隔），例如「/语音 你好」。 */
    private List<String> voicePrefixes = new ArrayList<>(List.of("/语音", "/voice"));

    /** 是否每条回复都以语音消息（TTS）额外发送一份。 */
    private boolean voiceReplyEnabled = false;

    /** 语音编码（mp3 -> silk）相关配置。 */
    private Voice voice = new Voice();

    public static class Voice {

        /** ffmpeg 可执行文件路径（用于把 mp3 转为 PCM）。 */
        private String ffmpegPath = "ffmpeg";

        /** silk-v3-encoder 的 silk_encoder 可执行文件路径（用于把 PCM 编码为 SILK）。 */
        private String silkEncoderPath = "silk_encoder";

        /** 微信语音使用的采样率。 */
        private int sampleRate = 24000;

        public String getFfmpegPath() {
            return ffmpegPath;
        }

        public void setFfmpegPath(String ffmpegPath) {
            this.ffmpegPath = ffmpegPath;
        }

        public String getSilkEncoderPath() {
            return silkEncoderPath;
        }

        public void setSilkEncoderPath(String silkEncoderPath) {
            this.silkEncoderPath = silkEncoderPath;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public void setTtsModel(String ttsModel) {
        this.ttsModel = ttsModel;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public double getChatTemperature() {
        return chatTemperature;
    }

    public void setChatTemperature(double chatTemperature) {
        this.chatTemperature = chatTemperature;
    }

    public int getChatMaxTokens() {
        return chatMaxTokens;
    }

    public void setChatMaxTokens(int chatMaxTokens) {
        this.chatMaxTokens = chatMaxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public List<String> getImagePrefixes() {
        return imagePrefixes;
    }

    public void setImagePrefixes(List<String> imagePrefixes) {
        this.imagePrefixes = imagePrefixes;
    }

    public List<String> getVoicePrefixes() {
        return voicePrefixes;
    }

    public void setVoicePrefixes(List<String> voicePrefixes) {
        this.voicePrefixes = voicePrefixes;
    }

    public boolean isVoiceReplyEnabled() {
        return voiceReplyEnabled;
    }

    public void setVoiceReplyEnabled(boolean voiceReplyEnabled) {
        this.voiceReplyEnabled = voiceReplyEnabled;
    }

    public Voice getVoice() {
        return voice;
    }

    public void setVoice(Voice voice) {
        this.voice = voice;
    }
}

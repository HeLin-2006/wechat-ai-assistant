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

    // ---------- 图片理解（视觉）：默认复用 chat 配置，可单独指向智谱等视觉模型 ----------
    private String visionModel = "";
    private String visionBaseUrl = "";
    private String visionApiKey = "";

    // ---------- 图片生成：默认复用主配置，可单独指向智谱 cogview 等 ----------
    private String imageModel = "gpt-image-1";
    private String imageBaseUrl = "";
    private String imageApiKey = "";

    /** 生成图片尺寸，如 1024x1024（OpenAI）或 1024*1024（DashScope wanx），留空用服务商默认。 */
    private String imageSize = "";

    /** DashScope wanx 是否启用提示词扩展（prompt_extend）。 */
    private boolean imagePromptExtend = false;

    /** 图片生成最大等待时间（毫秒，DashScope wanx 为异步任务）。 */
    private long imageTimeoutMs = 120000;

    // ---------- 语音合成（TTS）：默认复用主配置，可单独指向智谱 glm-tts 等 ----------
    private String ttsModel = "gpt-4o-mini-tts";
    private String ttsBaseUrl = "";
    private String ttsApiKey = "";

    /** TTS 音色（OpenAI: alloy；DashScope qwen-tts: Cherry/Serena 等）。 */
    private String ttsVoice = "";

    /** 单条语音消息的最大字符数，超过则按句拆分多条发送（微信语音约 60s 上限）。 */
    private int ttsMaxCharsPerMessage = 100;

    /**
     * 语音回复方式：
     * 微信官方已调整协议，Bot 通过 sendVoice 发送的语音气泡不再渲染（见 SDK issue #13），
     * 因此默认把 TTS 音频作为文件（mp3）发送，用户可直接点开播放。
     * 设为 true 则尝试发送 SILK 语音气泡（当前微信端不显示）。
     */
    private boolean voiceBubbleEnabled = false;

    // ---------- 语音转文字（ASR）：收到语音且网关未提供转写文本时使用 ----------
    private String asrModel = "";
    private String asrBaseUrl = "";
    private String asrApiKey = "";

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

    /** 上下文总字符预算（约 0.6~1 token/字符），0 表示不限制（省 token）。 */
    private int contextMaxChars = 3000;

    /** 是否启用 LLM 响应缓存（重复问题直接命中，省 token + 加速）。 */
    private boolean cacheEnabled = true;

    /** 缓存最大条目数（超限清空）。 */
    private int cacheMaxEntries = 200;

    /** 缓存有效期（分钟）。 */
    private int cacheTtlMinutes = 10;

    /** 工具调用（Function Calling）最大轮数，防止死循环。 */
    private int toolMaxRounds = 4;

    /** 触发图片生成的文本前缀（逗号分隔），例如「/img 一只猫」「画一只猫」。 */
    private List<String> imagePrefixes = new ArrayList<>(List.of("/img", "/image", "画", "生成图片"));

    /** 触发语音回复的文本前缀（逗号分隔），例如「/语音 你好」。 */
    private List<String> voicePrefixes = new ArrayList<>(List.of("/语音", "/voice"));

    /** 是否每条回复都以语音消息（TTS）额外发送一份。 */
    private boolean voiceReplyEnabled = false;

    /** 语音编码（mp3 -> silk）相关配置。 */
    private Voice voice = new Voice();

    // ---------- 解析方法：未单独配置时回退到主配置 ----------

    public String resolveVisionModel() {
        return isBlank(visionModel) ? chatModel : visionModel;
    }

    public String resolveVisionBaseUrl() {
        return isBlank(visionBaseUrl) ? baseUrl : visionBaseUrl;
    }

    public String resolveVisionApiKey() {
        return isBlank(visionApiKey) ? apiKey : visionApiKey;
    }

    public String resolveImageBaseUrl() {
        return isBlank(imageBaseUrl) ? baseUrl : imageBaseUrl;
    }

    public String resolveImageApiKey() {
        return isBlank(imageApiKey) ? apiKey : imageApiKey;
    }

    public String resolveTtsBaseUrl() {
        return isBlank(ttsBaseUrl) ? baseUrl : ttsBaseUrl;
    }

    public String resolveTtsApiKey() {
        return isBlank(ttsApiKey) ? apiKey : ttsApiKey;
    }

    public String resolveAsrModel() {
        return isBlank(asrModel) ? "" : asrModel;
    }

    public String resolveAsrBaseUrl() {
        return isBlank(asrBaseUrl) ? baseUrl : asrBaseUrl;
    }

    public String resolveAsrApiKey() {
        return isBlank(asrApiKey) ? apiKey : asrApiKey;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static class Voice {

        /** ffmpeg 可执行文件路径（用于把 mp3 转为 PCM）。 */
        private String ffmpegPath = "ffmpeg";

        /** silk-v3-encoder 的 silk_encoder 可执行文件路径（用于把 PCM 编码为 SILK）。 */
        private String silkEncoderPath = "silk_encoder";

        /** silk 解码器可执行文件路径（用于把收到的微信语音解码，交给 ASR 转写）。 */
        private String silkDecoderPath = "silk_decoder";

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

        public String getSilkDecoderPath() {
            return silkDecoderPath;
        }

        public void setSilkDecoderPath(String silkDecoderPath) {
            this.silkDecoderPath = silkDecoderPath;
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

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getVisionBaseUrl() {
        return visionBaseUrl;
    }

    public void setVisionBaseUrl(String visionBaseUrl) {
        this.visionBaseUrl = visionBaseUrl;
    }

    public String getVisionApiKey() {
        return visionApiKey;
    }

    public void setVisionApiKey(String visionApiKey) {
        this.visionApiKey = visionApiKey;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getImageBaseUrl() {
        return imageBaseUrl;
    }

    public void setImageBaseUrl(String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

    public String getImageApiKey() {
        return imageApiKey;
    }

    public void setImageApiKey(String imageApiKey) {
        this.imageApiKey = imageApiKey;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public boolean isImagePromptExtend() {
        return imagePromptExtend;
    }

    public void setImagePromptExtend(boolean imagePromptExtend) {
        this.imagePromptExtend = imagePromptExtend;
    }

    public long getImageTimeoutMs() {
        return imageTimeoutMs;
    }

    public void setImageTimeoutMs(long imageTimeoutMs) {
        this.imageTimeoutMs = imageTimeoutMs;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public void setTtsModel(String ttsModel) {
        this.ttsModel = ttsModel;
    }

    public String getTtsBaseUrl() {
        return ttsBaseUrl;
    }

    public void setTtsBaseUrl(String ttsBaseUrl) {
        this.ttsBaseUrl = ttsBaseUrl;
    }

    public String getTtsApiKey() {
        return ttsApiKey;
    }

    public void setTtsApiKey(String ttsApiKey) {
        this.ttsApiKey = ttsApiKey;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public int getTtsMaxCharsPerMessage() {
        return ttsMaxCharsPerMessage;
    }

    public void setTtsMaxCharsPerMessage(int ttsMaxCharsPerMessage) {
        this.ttsMaxCharsPerMessage = ttsMaxCharsPerMessage;
    }

    public boolean isVoiceBubbleEnabled() {
        return voiceBubbleEnabled;
    }

    public void setVoiceBubbleEnabled(boolean voiceBubbleEnabled) {
        this.voiceBubbleEnabled = voiceBubbleEnabled;
    }

    public String getAsrModel() {
        return asrModel;
    }

    public void setAsrModel(String asrModel) {
        this.asrModel = asrModel;
    }

    public String getAsrBaseUrl() {
        return asrBaseUrl;
    }

    public void setAsrBaseUrl(String asrBaseUrl) {
        this.asrBaseUrl = asrBaseUrl;
    }

    public String getAsrApiKey() {
        return asrApiKey;
    }

    public void setAsrApiKey(String asrApiKey) {
        this.asrApiKey = asrApiKey;
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

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public int getToolMaxRounds() {
        return toolMaxRounds;
    }

    public void setToolMaxRounds(int toolMaxRounds) {
        this.toolMaxRounds = toolMaxRounds;
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

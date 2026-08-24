package com.example.wechataiassistant.service.ai;

import com.example.wechataiassistant.config.WechatProperties;
import com.example.wechataiassistant.service.WechatBotService;
import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmException;
import com.example.wechataiassistant.service.llm.LlmProperties;
import com.example.wechataiassistant.service.weather.WeatherService;
import com.example.wechataiassistant.service.rag.RagDocument;
import com.example.wechataiassistant.service.rag.RagService;
import com.example.wechataiassistant.service.skill.SkillService;
import com.example.wechataiassistant.service.tool.MessageSender;
import com.example.wechataiassistant.service.tool.ToolCallService;
import com.example.wechataiassistant.service.tool.ToolContext;
import com.example.wechataiassistant.voice.VoiceEncodeException;
import com.example.wechataiassistant.voice.VoiceEncoder;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AiMessageHandler implements OnMessageListener {

    private static final Logger log = LoggerFactory.getLogger(AiMessageHandler.class);

    private static final String HELP =
            """
            你好，我是 AI 助手 🤖
            直接发文字/图片/语音即可对话。
            指令：
            · /img <描述>  或 画<描述> —— 生成图片
            · /语音 <内容> —— 语音回复（内容留空则回复 AI 的回答）
            · /语音模式 —— 开启/关闭语音模式（之后每条回复都附带语音）
            · /clear —— 清空对话上下文
            · /help —— 显示本帮助
            """;

    private final WechatBotService bot;
    private final LlmClient llm;
    private final LlmProperties props;
    private final ConversationMemory memory;
    private final VoiceEncoder voiceEncoder;
    private final WechatProperties wechatProps;
    private final IntentRecognizer intentRecognizer;
    private final WeatherService weatherService;
    private final ToolCallService toolCallService;
    private final SkillService skillService;
    private final RagService ragService;

    /**
     * 已处理过的消息 id 集合，防止网关重复投递或回声导致重复回复。
     * 超过上限时整体清空（消息量级远小于上限，简单可靠）。
     */
    private final Set<Long> seenMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final int MAX_SEEN_IDS = 10_000;

    /** 开启「语音模式」的用户（每次回复都附带语音，等价于 llm.voice-reply-enabled 的运行时开关）。 */
    private final Set<String> voiceModeUsers = ConcurrentHashMap.newKeySet();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "ai-message-worker");
                        t.setDaemon(true);
                        return t;
                    });

    public AiMessageHandler(
            @Lazy WechatBotService bot,
            LlmClient llm,
            LlmProperties props,
            ConversationMemory memory,
            VoiceEncoder voiceEncoder,
            WechatProperties wechatProps,
            IntentRecognizer intentRecognizer,
            WeatherService weatherService,
            ToolCallService toolCallService,
            SkillService skillService,
            RagService ragService) {
        this.bot = bot;
        this.llm = llm;
        this.props = props;
        this.memory = memory;
        this.voiceEncoder = voiceEncoder;
        this.wechatProps = wechatProps;
        this.intentRecognizer = intentRecognizer;
        this.weatherService = weatherService;
        this.toolCallService = toolCallService;
        this.skillService = skillService;
        this.ragService = ragService;
    }

    @Override
    public void onMessages(List<WeixinMessage> messages) {
        log.info("✅✅✅ onMessages 被调用！消息数: {}", messages == null ? 0 : messages.size());

        if (messages == null) {
            return;
        }
        for (WeixinMessage msg : messages) {
            String from = msg.getFrom_user_id();
            Long msgId = msg.getMessage_id();
            String botId = bot.botUserId();
            boolean isSelf = from != null && from.equals(botId);
            log.info("📩 消息: id={}, from={}, 是否绑定账号自己: {}", msgId, from, isSelf);

            if (from == null || from.isBlank()) {
                log.info("⏭️ 跳过: from 为空");
                continue;
            }
            if (msgId != null && !seenMessageIds.add(msgId)) {
                log.info("⏭️ 跳过重复消息 id={}", msgId);
                continue;
            }
            if (seenMessageIds.size() > MAX_SEEN_IDS) {
                seenMessageIds.clear();
            }
            if (isSelf && wechatProps.isIgnoreSelf()) {
                log.info("⏭️ 已配置忽略机器人自己的消息（wechat.ignore-self=true）");
                continue;
            }
            executor.submit(() -> handleSafely(msg));
        }
    }

    private void handleSafely(WeixinMessage msg) {
        log.info("🔔 开始处理消息, from={}", msg.getFrom_user_id());
        try {
            handle(msg);
            log.info("✅ 消息处理完成");
        } catch (Exception e) {
            log.error("❌ 处理消息失败 from={}", msg.getFrom_user_id(), e);
            safeSendText(msg.getFrom_user_id(), "抱歉，处理消息时出了点问题：" + brief(e));
        }
    }

    private void handle(WeixinMessage msg) throws Exception {
        String from = msg.getFrom_user_id();
        List<MessageItem> items = msg.getItem_list() == null ? List.of() : msg.getItem_list();

        StringBuilder text = new StringBuilder();
        byte[] image = null;
        String voiceText = null;
        byte[] voiceBytes = null;

        for (MessageItem item : items) {
            if (item.getText_item() != null && item.getText_item().getText() != null) {
                String t = item.getText_item().getText().trim();
                if (!t.isEmpty()) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(t);
                }
            } else if (item.getImage_item() != null && image == null) {
                image = bot.downloadImage(item);
            } else if (item.getVoice_item() != null) {
                voiceText = item.getVoice_item().getText();
                if ((voiceText == null || voiceText.isBlank()) && voiceBytes == null) {
                    voiceBytes = bot.downloadVoice(item);
                }
            }
        }

        // 网关未提供语音转写文本时，尝试用 ASR 接口转写
        if ((voiceText == null || voiceText.isBlank()) && voiceBytes != null) {
            voiceText = transcribeVoice(voiceBytes);
        }

        String raw = text.toString().trim();
        String content = buildUserContent(raw, voiceText);

        log.info("📝 提取内容: raw='{}', content='{}', image={}, voiceText='{}'",
                raw, content, image != null, voiceText);

        if (content == null || content.isEmpty()) {
            if (image != null) {
                content = "（收到一张图片，请分析并描述图片内容）";
            } else if (voiceBytes != null && voiceText == null) {
                log.info("⚠️ 语音转写失败，回复提示");
                safeSendText(from, "收到语音消息，但我没听清内容，方便的话请用文字再说一次～");
                return;
            } else {
                log.info("⚠️ 消息内容为空，回复提示");
                safeSendText(from, "我暂时只能处理文字、图片和语音消息哦～");
                return;
            }
        }

        // 意图识别路由
        IntentResult intent = intentRecognizer.recognize(raw);
        log.info("🎯 意图: {} city={} time={} payload='{}'",
                intent.intent(), intent.city(), intent.time(), intent.payload());
        switch (intent.intent()) {
            case WEATHER -> {
                log.info("🌤️ 处理天气查询: city={} time={}", intent.city(), intent.time());
                try {
                    WeatherService.WeatherResult w = weatherService.getWeather(intent.city(), intent.time());
                    bot.sendTextWithTyping(from, w.summary(), 400);
                } catch (WeatherService.WeatherException e) {
                    log.warn("天气查询失败: {}", e.getMessage());
                    safeSendText(from, "天气查询失败：" + e.getMessage() + "（试试发「北京天气怎么样」）");
                }
                return;
            }
            case HELP -> {
                log.info("📖 处理帮助指令");
                safeSendText(from, HELP);
                return;
            }
            case CLEAR -> {
                log.info("🧹 清空上下文");
                memory.clear(from);
                bot.clearContext(from);
                safeSendText(from, "好的，已清空我们的对话上下文～");
                return;
            }
            case VOICE_MODE -> {
                String cmd = intent.payload();
                if (cmd.equals("/语音模式") || cmd.equalsIgnoreCase("/voice-mode")) {
                    if (voiceModeUsers.remove(from)) {
                        safeSendText(from, "语音模式已关闭，之后回复只发文字。");
                    } else {
                        voiceModeUsers.add(from);
                        safeSendText(from, "语音模式已开启：之后的回复都会同时发一条语音。发送「/语音模式」可关闭。");
                    }
                } else if (cmd.equals("/语音开") || cmd.equalsIgnoreCase("/voice-on")) {
                    voiceModeUsers.add(from);
                    safeSendText(from, "语音模式已开启 ✅");
                } else {
                    voiceModeUsers.remove(from);
                    safeSendText(from, "语音模式已关闭 ✅");
                }
                return;
            }
            case IMAGE_GEN -> {
                log.info("🖼️ 处理图片生成: {}", intent.payload());
                handleImageGeneration(from, intent.payload());
                return;
            }
            case VOICE_SPEAK -> {
                String speak;
                if (intent.payload() != null && !intent.payload().isBlank()) {
                    speak = intent.payload();
                } else {
                    speak = askLlm(from, content, image);
                }
                handleVoiceReply(from, speak);
                return;
            }
            case CHAT -> {
                // 下方统一走 LLM
            }
        }

        // 语音模式用户：文本回复 + 语音
        if (voiceModeUsers.contains(from) || props.isVoiceReplyEnabled()) {
            handleVoiceReply(from, askLlm(from, content, image));
            return;
        }

        // ============ 消息路由：Skill → RAG → LLM 兜底 ============
        if (image == null) {
            // ① Skill 优先：关键词命中 → 确定性执行，不经过 LLM
            java.util.Optional<String> skillReply = skillService.executeIfMatched(content, from);
            if (skillReply.isPresent()) {
                String r = skillReply.get();
                log.info("🧩 Skill 执行结果: {}", r);
                bot.sendTextWithTyping(from, r, 300);
                return;
            }
            // ② RAG 增强：关键词检索命中知识库 → 增强 Prompt → LLM
            if (ragService.isEnabled()) {
                List<RagDocument> docs = ragService.retrieve(content);
                if (!docs.isEmpty()) {
                    String enhanced = ragService.buildEnhancedPrompt(content, docs);
                    log.info("📚 RAG 增强 Prompt 已构建（{} 篇文档），调用 LLM", docs.size());
                    String r = llm.chat(List.of(ChatMessage.user(enhanced)));
                    log.info("📚 RAG 增强回复: {}", r);
                    memory.add(from, ChatMessage.user(content));
                    memory.add(from, ChatMessage.assistant(r));
                    bot.sendTextWithTyping(from, r, 500);
                    return;
                }
            }
            // ③ LLM 兜底：工具调用循环
        }

        log.info("💬 调用AI生成回复...");
        String reply = askLlm(from, content, image);
        log.info("💬 AI回复: {}", reply);

        log.info("📤 准备发送回复到 {}", from);
        bot.sendTextWithTyping(from, reply, 500);
        log.info("✅ 回复已发送");
    }

    private String askLlm(String from, String content, byte[] image) {
        log.info("🤖 进入 askLlm, from={}, content={}", from, content);

        if (!llm.isConfigured()) {
            log.warn("⚠️ LLM 未配置！");
            return "AI 尚未配置：请在 application.properties 中设置 llm.api-key"
                    + "（以及 llm.base-url / llm.chat-model），配置后重启服务即可。";
        }

        log.info("✅ LLM 已配置，准备调用");
        List<ChatMessage> messages = new ArrayList<>(memory.history(from));
        messages.add(ChatMessage.user(content));

        String reply;
        if (image != null) {
            // 图片理解：走视觉模型（不带工具）
            String imageDataUri =
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image);
            reply = llm.chatWithVision(messages, imageDataUri);
        } else {
            // 文本对话：走工具调用循环（LLM 可自主决定调用天气/时间/生图等工具）
            ToolContext ctx =
                    new ToolContext(
                            from,
                            new MessageSender() {
                                @Override
                                public void sendText(String text) {
                                    safeSendText(from, text);
                                }

                                @Override
                                public void sendImage(byte[] imageBytes, String fileName, String caption) {
                                    try {
                                        bot.sendImage(from, imageBytes, fileName, caption);
                                    } catch (Exception e) {
                                        log.error("工具发送图片失败", e);
                                    }
                                }
                            });
            reply = toolCallService.respond(from, content, ctx);
        }
        log.info("📥 LLM 返回: {}", reply);

        memory.add(from, ChatMessage.user(content));
        memory.add(from, ChatMessage.assistant(reply));
        return reply;
    }

    /** 语音转文字：silk -> wav -> ASR 接口。失败返回 null。 */
    private String transcribeVoice(byte[] voiceBytes) {
        try {
            byte[] wav = voiceEncoder.decodeToWav(voiceBytes);
            String text = llm.transcribe(wav, "voice.wav");
            log.info("🎙️ 语音转文字: {}", text);
            return text;
        } catch (Exception e) {
            log.warn("⚠️ 语音转写失败: {}", brief(e));
            return null;
        }
    }

    private void handleImageGeneration(String from, String prompt) throws Exception {
        if (!llm.isConfigured()) {
            safeSendText(from, "AI 尚未配置，无法生成图片：请在 application.properties 中设置 llm.api-key 与 llm.image-model。");
            return;
        }
        bot.sendTextWithTyping(from, "好的，正在为你生成图片：" + prompt, 400);
        byte[] image = llm.generateImage(prompt);
        bot.sendImage(from, image, "ai-image.png", "为你生成的图片：" + prompt);
    }

    private void handleVoiceReply(String from, String reply) throws Exception {
        if (!llm.isConfigured()) {
            safeSendText(from, "AI 尚未配置，无法合成语音。");
            return;
        }
        List<String> segments = splitForTts(reply);
        boolean first = true;
        for (String segment : segments) {
            byte[] audio;
            try {
                audio = llm.textToSpeech(segment);
            } catch (LlmException e) {
                log.warn("TTS 合成失败，降级为文本回复: {}", e.getMessage());
                safeSendText(from, "（语音合成失败，改为文字回复）" + reply);
                return;
            }
            if (props.isVoiceBubbleEnabled()) {
                // 尝试 SILK 语音气泡——注意：微信官方已调整协议，Bot 语音气泡不再渲染（SDK issue #13）
                try {
                    VoiceEncoder.SilkResult silk = voiceEncoder.toSilk(audio);
                    bot.sendVoice(
                            from,
                            silk.data(),
                            "reply.silk",
                            (int) silk.playTimeMs(),
                            props.getVoice().getSampleRate());
                } catch (VoiceEncodeException e) {
                    log.warn("语音编码失败，降级为发送音频文件: {}", e.getMessage());
                    bot.sendFile(from, audio, audioFileName(audio), first ? "语音回复（音频文件）" : null);
                }
            } else {
                // 默认方式：把 TTS 音频作为文件发送，微信端可直接点开播放
                bot.sendFile(from, audio, audioFileName(audio), first ? "语音回复" : null);
            }
            first = false;
        }
    }

    /** 根据音频内容判断扩展名（wav 以 RIFF 开头，mp3 以 ID3/0xFF 开头）。 */
    private static String audioFileName(byte[] audio) {
        if (audio == null || audio.length < 4) {
            return "reply.mp3";
        }
        if (audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F') {
            return "reply.wav";
        }
        return "reply.mp3";
    }

    /** 把长文本按句拆成多段，每段不超过 llm.tts-max-chars-per-message 字符（微信语音时长约 60s 上限）。 */
    private List<String> splitForTts(String text) {
        int hardLimit = Math.max(props.getTtsMaxCharsPerMessage(), 20);
        int softLimit = Math.max(hardLimit / 2, 10);
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            parts.add("嗯？");
            return parts;
        }
        if (text.length() <= hardLimit) {
            parts.add(text);
            return parts;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (isSentenceEnd(c) && sb.length() >= softLimit) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else if (sb.length() >= hardLimit) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            }
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString().trim());
        }
        log.info("🎙️ 语音回复拆分为 {} 段", parts.size());
        return parts;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?'
                || c == '；' || c == ';' || c == '\n';
    }

    private String buildUserContent(String raw, String voiceText) {
        StringBuilder sb = new StringBuilder();
        if (raw != null && !raw.isEmpty()) {
            sb.append(raw);
        }
        if (voiceText != null && !voiceText.isBlank()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("（语音内容）").append(voiceText.trim());
        }
        return sb.toString().trim();
    }

    private void safeSendText(String to, String text) {
        log.info("📤 safeSendText: to={}, text={}", to, text);
        try {
            bot.sendText(to, text);
            log.info("✅ safeSendText 发送成功");
        } catch (Exception e) {
            log.error("❌ 发送文本失败 to={}", to, e);
        }
    }

    private String brief(Throwable t) {
        if (t instanceof LlmException) {
            return t.getMessage();
        }
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }
}
package com.example.wechataiassistant.service.ai;

import com.example.wechataiassistant.service.WechatBotService;
import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmException;
import com.example.wechataiassistant.service.llm.LlmProperties;
import com.example.wechataiassistant.voice.VoiceEncodeException;
import com.example.wechataiassistant.voice.VoiceEncoder;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 微信消息监听器：收到文字/图片/语音后调用大模型，并回复文本、图片或语音。
 *
 * <p>处理规则：</p>
 * <ul>
 *   <li>文本 -> LLM 文本回复（带输入态）</li>
 *   <li>图片 -> 下载后走视觉模型理解并回复文本</li>
 *   <li>语音 -> 使用服务端语音转文字结果作为用户输入</li>
 *   <li>/img|画 开头 -> 生成图片并发送</li>
 *   <li>/语音|/voice 开头 -> TTS 语音回复（缺工具时降级为文本/音频文件）</li>
 *   <li>/clear、/help 等指令</li>
 * </ul>
 */
@Component
public class AiMessageHandler implements OnMessageListener {

    private static final Logger log = LoggerFactory.getLogger(AiMessageHandler.class);

    private static final String HELP =
        """
        你好，我是 AI 助手 🤖
        直接发文字/图片/语音即可对话。
        指令：
        · /img <描述>  或 画<描述> —— 生成图片
        · /语音 <内容> 或 /voice <内容> —— 语音回复（需要 ffmpeg + silk_encoder）
        · /clear —— 清空对话上下文
        · /help —— 显示本帮助
        """;

    private final WechatBotService bot;
    private final LlmClient llm;
    private final LlmProperties props;
    private final ConversationMemory memory;
    private final VoiceEncoder voiceEncoder;

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
        VoiceEncoder voiceEncoder) {
        this.bot = bot;
        this.llm = llm;
        this.props = props;
        this.memory = memory;
        this.voiceEncoder = voiceEncoder;
    }

    @Override
    public void onMessages(List<WeixinMessage> messages) {
        if (messages == null) {
            return;
        }
        for (WeixinMessage msg : messages) {
            String from = msg.getFrom_user_id();
            if (from == null || from.isBlank()) {
                continue;
            }
            if (from.equals(bot.botUserId())) {
                continue; // 忽略机器人自己发出的消息，避免死循环
            }
            executor.submit(() -> handleSafely(msg));
        }
    }

    private void handleSafely(WeixinMessage msg) {
        try {
            handle(msg);
        } catch (Exception e) {
            log.error("处理消息失败 from={}", msg.getFrom_user_id(), e);
            safeSendText(msg.getFrom_user_id(), "抱歉，处理消息时出了点问题：" + brief(e));
        }
    }

    private void handle(WeixinMessage msg) throws Exception {
        String from = msg.getFrom_user_id();
        List<MessageItem> items = msg.getItem_list() == null ? List.of() : msg.getItem_list();

        StringBuilder text = new StringBuilder();
        byte[] image = null;
        String voiceText = null;

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
            } else if (item.getVoice_item() != null && voiceText == null) {
                voiceText = item.getVoice_item().getText();
            }
        }

        String raw = text.toString().trim();
        String content = buildUserContent(raw, voiceText);

        if (content == null || content.isEmpty()) {
            if (image != null) {
                content = "（收到一张图片，请分析并描述图片内容）";
            } else {
                safeSendText(from, "我暂时只能处理文字、图片和语音消息哦～");
                return;
            }
        }

        // 指令处理
        if ("/help".equalsIgnoreCase(raw) || "帮助".equals(raw)) {
            safeSendText(from, HELP);
            return;
        }
        if ("/clear".equalsIgnoreCase(raw) || "清空上下文".equals(raw)) {
            memory.clear(from);
            bot.clearContext(from);
            safeSendText(from, "好的，已清空我们的对话上下文～");
            return;
        }

        String imagePrompt = matchPrefix(raw, props.getImagePrefixes());
        if (imagePrompt != null) {
            handleImageGeneration(from, imagePrompt);
            return;
        }

        String voiceSpeak = matchPrefix(raw, props.getVoicePrefixes());
        boolean wantVoice = voiceSpeak != null || props.isVoiceReplyEnabled();
        if (wantVoice) {
            String reply;
            if (voiceSpeak != null && !voiceSpeak.isBlank()) {
                reply = voiceSpeak; // 直接朗读用户指定的内容
            } else {
                reply = askLlm(from, content, image);
            }
            handleVoiceReply(from, reply);
            return;
        }

        String reply = askLlm(from, content, image);
        bot.sendTextWithTyping(from, reply, 500);
    }

    private String askLlm(String from, String content, byte[] image) {
        if (!llm.isConfigured()) {
            return "AI 尚未配置：请在 application.properties 中设置 llm.api-key"
                + "（以及 llm.base-url / llm.chat-model），配置后重启服务即可。";
        }
        List<ChatMessage> messages = new ArrayList<>(memory.history(from));
        messages.add(ChatMessage.user(content));

        String imageDataUri =
            image != null
                ? "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image)
                : null;

        String reply = llm.chat(messages, imageDataUri);

        memory.add(from, ChatMessage.user(content));
        memory.add(from, ChatMessage.assistant(reply));
        return reply;
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
        byte[] mp3 = llm.textToSpeech(reply);
        try {
            VoiceEncoder.SilkResult silk = voiceEncoder.toSilk(mp3);
            bot.sendVoice(
                from,
                silk.data(),
                "reply.silk",
                (int) silk.playTimeMs(),
                props.getVoice().getSampleRate());
        } catch (VoiceEncodeException e) {
            log.warn("语音编码失败，降级为发送音频文件: {}", e.getMessage());
            bot.sendFile(from, mp3, "reply.mp3", "语音回复（音频文件）");
        }
    }

    /** 把语音转文字内容合并进用户输入。 */
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

    /** 若文本以某个前缀开头，返回去掉前缀后的内容；否则返回 null。 */
    private String matchPrefix(String raw, List<String> prefixes) {
        if (raw == null || prefixes == null) {
            return null;
        }
        for (String p : prefixes) {
            String prefix = p.trim();
            if (prefix.isEmpty()) {
                continue;
            }
            if (raw.startsWith(prefix)) {
                String rest = raw.substring(prefix.length()).trim();
                return rest;
            }
        }
        return null;
    }

    private void safeSendText(String to, String text) {
        try {
            bot.sendText(to, text);
        } catch (Exception e) {
            log.error("发送文本失败 to={}", to, e);
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

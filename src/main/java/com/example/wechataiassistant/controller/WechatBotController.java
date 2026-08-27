package com.example.wechataiassistant.controller;

import com.example.wechataiassistant.service.WechatBotService;
import com.example.wechataiassistant.service.ai.TimeQualifier;
import com.example.wechataiassistant.service.llm.ChatMessage;
import com.example.wechataiassistant.service.llm.LlmClient;
import com.example.wechataiassistant.service.llm.LlmProperties;
import com.example.wechataiassistant.service.weather.WeatherService;
import com.example.wechataiassistant.service.agent.AgentContext;
import com.example.wechataiassistant.service.agent.RoadTripAgentService;
import com.example.wechataiassistant.service.rag.RagDocument;
import com.example.wechataiassistant.service.rag.RagService;
import com.example.wechataiassistant.service.skill.SkillService;
import com.example.wechataiassistant.service.tool.ToolCallService;
import com.example.wechataiassistant.service.tool.ToolContext;
import com.example.wechataiassistant.voice.VoiceEncoder;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信机器人 REST 接口：
 * <ul>
 *   <li>GET /wechat —— 扫码登录页面（浏览器打开即可扫码）</li>
 *   <li>GET /wechat/login —— 获取登录二维码</li>
 *   <li>GET /wechat/status —— 登录/连接状态</li>
 *   <li>GET /wechat/updates —— 手动拉取一次消息（调试用）</li>
 *   <li>POST /WeChat/send/text|image|voice —— 主动发消息</li>
 *   <li>POST /WeChat/clear-context —— 清空某用户上下文</li>
 *   <li>GET /wechat/llm-config —— 查看 LLM 配置（Key 打码）</li>
 *   <li>GET /wechat/test/chat|image|tts —— 单独验证文本/生图/语音合成</li>
 * </ul>
 */
@RestController
@RequestMapping("/wechat")
public class WechatBotController {

    private static final Logger log = LoggerFactory.getLogger(WechatBotController.class);

    private final WechatBotService bot;
    private final LlmClient llm;
    private final LlmProperties llmProps;
    private final VoiceEncoder voiceEncoder;
    private final WeatherService weatherService;
    private final ToolCallService toolCallService;
    private final SkillService skillService;
    private final RagService ragService;
    private final RoadTripAgentService roadTripAgentService;

    public WechatBotController(
        WechatBotService bot,
        LlmClient llm,
        LlmProperties llmProps,
        VoiceEncoder voiceEncoder,
        WeatherService weatherService,
        ToolCallService toolCallService,
        SkillService skillService,
        RagService ragService,
        RoadTripAgentService roadTripAgentService) {
        this.bot = bot;
        this.llm = llm;
        this.llmProps = llmProps;
        this.voiceEncoder = voiceEncoder;
        this.weatherService = weatherService;
        this.toolCallService = toolCallService;
        this.skillService = skillService;
        this.ragService = ragService;
        this.roadTripAgentService = roadTripAgentService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return QR_PAGE;
    }

    @GetMapping("/login")
    public Map<String, Object> login(@RequestParam(defaultValue = "false") boolean force) {
        try {
            return bot.startLogin(force);
        } catch (Exception e) {
            log.error("获取登录二维码失败", e);
            return Map.of("loggedIn", false, "error", "获取二维码失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return bot.status();
    }

    @GetMapping("/updates")
    public Map<String, Object> updates() {
        try {
            List<WeixinMessage> messages = bot.pollUpdates();
            return Map.of("ok", true, "count", messages.size(), "messages", messages);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/send/text")
    public Map<String, Object> sendText(@RequestBody SendTextRequest req) {
        try {
            bot.sendText(req.toUserId(), req.text());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("发送文本失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/send/image")
    public Map<String, Object> sendImage(@RequestBody SendImageRequest req) {
        try {
            byte[] bytes = Base64.getDecoder().decode(req.base64());
            bot.sendImage(req.toUserId(), bytes, req.fileName(), req.caption());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("发送图片失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/send/voice")
    public Map<String, Object> sendVoice(@RequestBody SendVoiceRequest req) {
        try {
            byte[] bytes = Base64.getDecoder().decode(req.base64());
            bot.sendVoice(req.toUserId(), bytes, req.fileName(), req.playTimeMs(), req.sampleRate());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("发送语音失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 发送文件（微信官方已不渲染 Bot 语音气泡，语音回复以文件方式发送）。 */
    @PostMapping("/send/file")
    public Map<String, Object> sendFile(@RequestBody SendFileRequest req) {
        try {
            byte[] bytes = Base64.getDecoder().decode(req.base64());
            bot.sendFile(req.toUserId(), bytes, req.fileName(), req.caption());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("发送文件失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/clear-context")
    public Map<String, Object> clearContext(@RequestBody ClearContextRequest req) {
        bot.clearContext(req.toUserId());
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------
    // 调试/自测接口
    // ------------------------------------------------------------------

    /** 查看当前 LLM 各能力配置（API Key 打码）。 */
    @GetMapping("/llm-config")
    public Map<String, Object> llmConfig() {
        return Map.of(
            "chat", configOf(llmProps.getBaseUrl(), llmProps.getChatModel(), llmProps.getApiKey()),
            "vision", configOf(llmProps.resolveVisionBaseUrl(), llmProps.resolveVisionModel(), llmProps.resolveVisionApiKey()),
            "image", configOf(llmProps.resolveImageBaseUrl(), llmProps.getImageModel(), llmProps.resolveImageApiKey()),
            "tts", configOf(llmProps.resolveTtsBaseUrl(), llmProps.getTtsModel(), llmProps.resolveTtsApiKey()),
            "asr", configOf(llmProps.resolveAsrBaseUrl(), llmProps.resolveAsrModel(), llmProps.resolveAsrApiKey()),
            "tools", Map.of(
                "ffmpeg", llmProps.getVoice().getFfmpegPath(),
                "silkEncoder", llmProps.getVoice().getSilkEncoderPath(),
                "silkDecoder", llmProps.getVoice().getSilkDecoderPath()));
    }

    private Map<String, String> configOf(String baseUrl, String model, String apiKey) {
        return Map.of(
            "baseUrl", baseUrl,
            "model", model == null || model.isBlank() ? "(未配置)" : model,
            "apiKey", maskKey(apiKey));
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "(未配置)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /** 单独验证文本对话。 */
    @GetMapping("/test/chat")
    public Map<String, Object> testChat(@RequestParam(defaultValue = "你好，请简单介绍下自己") String text) {
        try {
            String reply = llm.chat(List.of(ChatMessage.user(text)));
            return Map.of("ok", true, "reply", reply);
        } catch (Exception e) {
            log.error("测试对话失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 单独验证图片生成（返回 base64 预览与字节数）。 */
    @GetMapping("/test/image")
    public Map<String, Object> testImage(@RequestParam(defaultValue = "一只戴眼镜的橘猫") String prompt) {
        try {
            byte[] bytes = llm.generateImage(prompt);
            return Map.of(
                "ok", true,
                "bytes", bytes.length,
                "base64", Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            log.error("测试生图失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 单独验证语音合成（返回 base64 音频与字节数）。 */
    @GetMapping("/test/tts")
    public Map<String, Object> testTts(@RequestParam(defaultValue = "你好，我是你的微信 AI 助手") String text) {
        try {
            byte[] bytes = llm.textToSpeech(text);
            return Map.of(
                "ok", true,
                "bytes", bytes.length,
                "base64", Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            log.error("测试TTS失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 单独验证天气查询（城市 + 时间限定）。 */
    @GetMapping("/test/weather")
    public Map<String, Object> testWeather(
        @RequestParam(defaultValue = "北京") String city,
        @RequestParam(defaultValue = "today") String when) {
        try {
            TimeQualifier tq =
                switch (when.toLowerCase()) {
                    case "tomorrow", "明天" -> TimeQualifier.TOMORROW;
                    case "dayafter", "后天" -> TimeQualifier.DAY_AFTER;
                    case "week", "本周" -> TimeQualifier.WEEK;
                    default -> TimeQualifier.TODAY;
                };
            WeatherService.WeatherResult r = weatherService.getWeather(city, tq);
            return Map.of("ok", true, "city", r.city(), "summary", r.summary());
        } catch (Exception e) {
            log.error("测试天气失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /**
     * 长任务 Agent：一句话目标 → 完整路书成品（自主拆解/多工具/闭环/断点续跑）。
     * 传相同 runId 可续跑未完成任务。
     */
    @GetMapping("/test/agent")
    public Map<String, Object> testAgent(
        @RequestParam(defaultValue = "规划一次成都到稻城亚丁的自驾游") String goal,
        @RequestParam(required = false) String runId) {
        try {
            String rid = runId != null && !runId.isBlank() ? runId : String.format("%08x", goal.hashCode());
            AgentContext ctx = new AgentContext("test-user", null);
            var exec = roadTripAgentService.execute(goal, ctx, rid);
            return Map.of("ok", true, "runId", rid, "resumed", exec.resumed(), "document", exec.document());
        } catch (Exception e) {
            log.error("测试 Agent 失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 验证工具调用（Function Calling）：LLM 自主决定调用天气/时间等工具。 */
    @GetMapping("/test/tools")
    public Map<String, Object> testTools(@RequestParam(defaultValue = "现在几点了？") String text) {
        try {
            ToolContext ctx = new ToolContext("test-user", null);
            String reply = toolCallService.respond("test-user", text, ctx);
            return Map.of("ok", true, "reply", reply);
        } catch (Exception e) {
            log.error("测试工具调用失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 验证技能（Skill）：关键词命中即确定性执行，不经过 LLM。 */
    @GetMapping("/test/skill")
    public Map<String, Object> testSkill(@RequestParam(defaultValue = "今天是什么节日") String text) {
        try {
            var reply = skillService.executeIfMatched(text, "test-user");
            return Map.of("ok", true, "matched", reply.isPresent(), "reply", reply.orElse("（未命中任何技能）"));
        } catch (Exception e) {
            log.error("测试技能失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /**
     * RAG 开关对比测试：同一问题分别用「关闭 RAG」和「开启 RAG」回答，对比差异。
     */
    @GetMapping("/test/rag")
    public Map<String, Object> testRag(@RequestParam(defaultValue = "机器人支持哪些功能？") String text) {
        try {
            // RAG 关闭：直接 LLM 回答
            String ragOff = llm.chat(List.of(ChatMessage.user(text)));

            // RAG 开启：检索知识库 → 增强 Prompt → LLM 回答
            var docs = ragService.retrieve(text);
            String ragOn;
            if (docs.isEmpty()) {
                ragOn = "（知识库未检索到相关内容，与关闭 RAG 相同）";
            } else {
                String enhanced = ragService.buildEnhancedPrompt(text, docs);
                ragOn = llm.chat(List.of(ChatMessage.user(enhanced)));
            }
            return Map.of(
                "ok", true,
                "query", text,
                "retrievedDocs", docs.stream().map(RagDocument::title).toList(),
                "ragOff", ragOff,
                "ragOn", ragOn);
        } catch (Exception e) {
            log.error("测试 RAG 失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** 验证完整语音链路（TTS → PCM → SILK → 微信语音），返回 SILK 字节数与时长。 */
    @GetMapping("/test/voice")
    public Map<String, Object> testVoice(@RequestParam(defaultValue = "你好，我是你的微信 AI 助手") String text) {
        try {
            byte[] audio = llm.textToSpeech(text);
            var silk = voiceEncoder.toSilk(audio);
            return Map.of(
                "ok", true,
                "audioBytes", audio.length,
                "silkBytes", silk.data().length,
                "playTimeMs", silk.playTimeMs());
        } catch (Exception e) {
            log.error("测试语音链路失败", e);
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    public record SendTextRequest(String toUserId, String text) {}

    public record SendImageRequest(String toUserId, String base64, String fileName, String caption) {}

    public record SendVoiceRequest(
        String toUserId, String base64, String fileName, Integer playTimeMs, Integer sampleRate) {}

    public record SendFileRequest(String toUserId, String base64, String fileName, String caption) {}

    public record ClearContextRequest(String toUserId) {}

    private static final String QR_PAGE =
        """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <title>微信 AI 助手 - 扫码登录</title>
          <style>
            body { font-family: -apple-system, "PingFang SC", sans-serif; background:#f5f6f7;
                   display:flex; justify-content:center; padding-top:40px; }
            .card { background:#fff; border-radius:12px; box-shadow:0 2px 12px rgba(0,0,0,.08);
                    padding:32px 40px; text-align:center; min-width:320px; }
            h1 { font-size:20px; margin:0 0 8px; }
            .tip { color:#888; font-size:13px; margin-bottom:20px; }
            img.qr { width:220px; height:220px; border:1px solid #eee; border-radius:8px; }
            .status { margin-top:16px; font-size:14px; color:#333; min-height:20px; }
            .status.ok { color:#07c160; }
            .status.err { color:#e64340; }
            button { margin-top:16px; padding:8px 24px; border:0; border-radius:6px;
                     background:#07c160; color:#fff; font-size:14px; cursor:pointer; }
            .meta { margin-top:12px; color:#aaa; font-size:12px; word-break:break-all; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>微信 AI 助手</h1>
            <div class="tip">使用微信「扫一扫」扫码，并在手机上确认登录</div>
            <img id="qr" class="qr" alt="二维码" src="">
            <div id="status" class="status">加载中...</div>
            <button onclick="loadQr()">重新获取二维码</button>
            <div id="meta" class="meta"></div>
          </div>
          <script>
            let loggedIn = false;
            async function loadQr() {
              const st = document.getElementById('status');
              st.className = 'status';
              st.textContent = '正在获取二维码...';
              try {
                const r = await fetch('/wechat/login');
                const j = await r.json();
                if (j.loggedIn) { showLoggedIn(j); return; }
                if (j.error) { st.className='status err'; st.textContent = j.error; return; }
                document.getElementById('qr').src = j.qrcodeImg;
                document.getElementById('meta').textContent = 'qrcode: ' + j.qrcode;
                st.textContent = '等待扫码（二维码有效期约 3 分钟）';
              } catch (e) {
                st.className='status err'; st.textContent = '请求失败: ' + e;
              }
            }
            function showLoggedIn(j) {
              loggedIn = true;
              const st = document.getElementById('status');
              st.className = 'status ok';
              st.textContent = '✅ 已登录 botId=' + (j.botId || '');
              document.getElementById('qr').src = '';
            }
            async function poll() {
              try {
                const r = await fetch('/wechat/status');
                const j = await r.json();
                const st = document.getElementById('status');
                if (j.loggedIn) { showLoggedIn(j); return; }
                if (j.loginStatus === 'SCANNED') { st.textContent = '已扫码，请在手机上确认登录...'; }
                else if (j.loginStatus === 'EXPIRED') { st.textContent = '二维码已过期，请点击重新获取'; st.className='status err'; }
                else if (j.loginStatus === 'ERROR') { st.textContent = '登录出错: ' + j.errorMessage; st.className='status err'; }
              } catch (e) { /* ignore */ }
            }
            loadQr();
            setInterval(poll, 2000);
          </script>
        </body>
        </html>
        """;
}

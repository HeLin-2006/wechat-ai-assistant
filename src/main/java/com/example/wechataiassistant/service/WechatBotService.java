package com.example.wechataiassistant.service;

import com.example.wechataiassistant.config.WechatProperties;
import com.example.wechataiassistant.service.ai.AiMessageHandler;
import tools.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.login.LoginStatus;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 微信 iLink SDK 的门面服务：
 * <ul>
 *   <li>二维码登录 / 登录状态 / 会话持久化恢复</li>
 *   <li>文本、图片、语音、文件消息发送</li>
 *   <li>媒体下载、手动拉取消息</li>
 *   <li>心跳自动拉取消息并派发给 {@link AiMessageHandler}</li>
 * </ul>
 */
@Service
public class WechatBotService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WechatBotService.class);

    private final WechatProperties props;
    private final ObjectMapper mapper;
    private final ILinkClient client;

    public WechatBotService(WechatProperties props, ObjectMapper mapper, AiMessageHandler handler) {
        this.props = props;
        this.mapper = mapper;

        ILinkConfig config = ILinkConfig.builder()
                .heartbeatEnabled(true)
                .heartbeatIntervalMs(props.getHeartbeatIntervalMs())
                .loginTimeoutMs(props.getLoginTimeoutMs())
                .build();

        this.client = ILinkClient.builder()
                .config(config)
                .resumeContext(loadSession())
                .onLogin(
                    new OnLoginListener() {
                        @Override
                        public void onLoginSuccess(LoginContext context) {
                            log.info("微信登录成功 botId={}, userId={}", context.getBotId(), context.getUserId());
                            saveSession();
                        }

                        @Override
                        public void onLoginFailure(Throwable throwable) {
                            log.warn("微信登录失败: {}", throwable.getMessage());
                        }
                    })
                .onDisconnect(
                    new com.github.wechat.ilink.sdk.core.listener.OnDisconnectListener() {
                        @Override
                        public void onDisconnect(Throwable cause) {
                            log.warn("微信连接断开: {}", cause == null ? "unknown" : cause.getMessage());
                        }

                        @Override
                        public void onReconnectStart(int attempt) {
                            log.info("微信连接重试中: 第 {} 次", attempt);
                        }

                        @Override
                        public void onReconnectSuccess() {
                            log.info("微信连接重连成功");
                        }

                        @Override
                        public void onReconnectFailed(Throwable cause) {
                            log.error("微信重连失败", cause);
                        }
                    })
                .onMessage(handler)
                .build();

        if (client.isLoggedIn()) {
            log.info("已从本地会话恢复微信登录，botId={}", client.getLoginContext().getBotId());
        }
    }

    /** 开始二维码登录，返回二维码图片（data URI）与原始 qrcode。 */
    public Map<String, Object> startLogin(boolean force) {
        if (client.isLoggedIn() && !force) {
            return Map.of(
                "loggedIn", true,
                "message", "已登录，无需重复扫码",
                "botId", client.getLoginContext().getBotId());
        }
        String imgContent = client.executeLogin();
        return Map.of(
            "loggedIn", false,
            "qrcode", client.getQrcode() == null ? "" : client.getQrcode(),
            "qrcodeImg", normalizeQrImage(imgContent));
    }

    public Map<String, Object> status() {
        LoginStatus s = client.getLoginStatus();
        LoginContext ctx = client.getLoginContext();
        return Map.of(
            "loginStatus", s.getStatus().name(),
            "errorMessage", s.getErrorMessage() == null ? "" : s.getErrorMessage(),
            "connectionStatus", client.getConnectionStatus().name(),
            "loggedIn", client.isLoggedIn(),
            "botId", ctx == null ? "" : ctx.getBotId(),
            "botUserId", ctx == null ? "" : ctx.getUserId());
    }

    public boolean isLoggedIn() {
        return client.isLoggedIn();
    }

    /** 连接状态（LOGGED_IN / DISCONNECTED 等）。 */
    public String connectionStatus() {
        return client.getConnectionStatus().name();
    }

    /** 当前机器人自己的 userId（用于过滤自己发出的消息）。 */
    public String botUserId() {
        LoginContext ctx = client.getLoginContext();
        return ctx == null ? null : ctx.getUserId();
    }

    public void sendText(String toUserId, String text) throws IOException {
        client.sendText(toUserId, text);
    }

    public void sendTextWithTyping(String toUserId, String text, long typingMillis) throws IOException {
        client.sendTextWithTyping(toUserId, text, typingMillis);
    }

    public void sendImage(String toUserId, byte[] imageBytes, String fileName, String caption)
        throws IOException {
        client.sendImage(toUserId, imageBytes, fileName, caption);
    }

    public void sendVoice(
        String toUserId, byte[] voiceBytes, String fileName, Integer playTimeMs, Integer sampleRate)
        throws IOException {
        client.sendVoice(toUserId, voiceBytes, fileName, playTimeMs, sampleRate);
    }

    public void sendFile(String toUserId, byte[] fileBytes, String fileName, String caption)
        throws IOException {
        client.sendFile(toUserId, fileBytes, fileName, caption);
    }

    public byte[] downloadImage(MessageItem item) throws IOException {
        return client.downloadImageFromMessageItem(item);
    }

    public byte[] downloadVoice(MessageItem item) throws IOException {
        return client.downloadVoiceFromMessageItem(item);
    }

    public List<WeixinMessage> pollUpdates() throws IOException {
        return client.getUpdates();
    }

    public void clearContext(String userId) {
        client.clearContext(userId);
    }

    // ------------------------------------------------------------------
    // 会话持久化：服务重启后免扫码恢复登录
    // ------------------------------------------------------------------

    private void saveSession() {
        ResumeContext rc = client.exportResumeContext();
        LoginContext ctx = rc.getLoginContext();
        if (ctx == null) {
            return;
        }
        SessionFile sf =
            new SessionFile(
                ctx.getBotToken(), ctx.getUserId(), ctx.getBotId(), ctx.getBaseUrl(), rc.getUpdatesCursor());
        try {
            Files.writeString(Path.of(props.getSessionFile()), mapper.writeValueAsString(sf));
            log.info("微信会话已持久化到 {}", props.getSessionFile());
        } catch (IOException e) {
            log.warn("保存微信会话失败: {}", e.getMessage());
        }
    }

    private ResumeContext loadSession() {
        try {
            Path path = Path.of(props.getSessionFile());
            if (!Files.exists(path)) {
                return null;
            }
            SessionFile sf = mapper.readValue(Files.readString(path), SessionFile.class);
            if (sf.botToken() == null || sf.botToken().isBlank()) {
                return null;
            }
            LoginContext ctx = new LoginContext(sf.botToken(), sf.userId(), sf.botId(), sf.baseUrl());
            return ResumeContext.builder(ctx).updatesCursor(sf.updatesCursor()).build();
        } catch (Exception e) {
            log.warn("恢复微信会话失败（将重新扫码登录）: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeQrImage(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String c = content.trim();
        if (c.startsWith("data:")) {
            return c;
        }
        if (c.startsWith("http://") || c.startsWith("https://")) {
            // 返回的是图片 URL 时直接透传，由前端加载
            return c;
        }
        // 假定是 base64 图片内容，包装成 data URI
        try {
            byte[] decoded = Base64.getDecoder().decode(c);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(decoded);
        } catch (IllegalArgumentException e) {
            return "data:image/png;base64," + c;
        }
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            saveSession();
        } catch (Exception e) {
            log.warn("关闭时保存会话失败: {}", e.getMessage());
        }
        client.close();
        log.info("微信客户端已关闭");
    }



    /** 会话文件内容（LoginContext + 更新游标）。 */
    record SessionFile(String botToken, String userId, String botId, String baseUrl, String updatesCursor) {}
}

package com.example.wechataiassistant.controller;

import com.example.wechataiassistant.service.WechatBotService;
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
 *   <li>POST /wechat/send/text|image|voice —— 主动发消息</li>
 *   <li>POST /wechat/clear-context —— 清空某用户上下文</li>
 * </ul>
 */
@RestController
@RequestMapping("/wechat")
public class WechatBotController {

    private static final Logger log = LoggerFactory.getLogger(WechatBotController.class);

    private final WechatBotService bot;

    public WechatBotController(WechatBotService bot) {
        this.bot = bot;
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

    @PostMapping("/clear-context")
    public Map<String, Object> clearContext(@RequestBody ClearContextRequest req) {
        bot.clearContext(req.toUserId());
        return Map.of("ok", true);
    }

    public record SendTextRequest(String toUserId, String text) {}

    public record SendImageRequest(String toUserId, String base64, String fileName, String caption) {}

    public record SendVoiceRequest(
        String toUserId, String base64, String fileName, Integer playTimeMs, Integer sampleRate) {}

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

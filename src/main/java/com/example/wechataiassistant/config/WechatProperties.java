package com.example.wechataiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信 iLink SDK 相关配置（前缀 wechat.*）。
 */
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 心跳/拉取消息的间隔（毫秒），越小回复越及时，越大越省请求。 */
    private long heartbeatIntervalMs = 3000;

    /** 二维码登录超时时间（毫秒）。 */
    private long loginTimeoutMs = 180000;

    /** 登录会话持久化文件路径（服务重启后自动恢复登录）。 */
    private String sessionFile = "wechat-session.json";

    /**
     * 是否忽略机器人自己（扫码绑定账号）发出的消息。
     * iLink 的设计就是绑定账号自己跟机器人对话（from_user_id == 绑定账号），
     * 因此默认 false（不忽略）；仅当网关会把机器人发出的消息回传时建议设为 true 防死循环。
     */
    private boolean ignoreSelf = false;

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getLoginTimeoutMs() {
        return loginTimeoutMs;
    }

    public void setLoginTimeoutMs(long loginTimeoutMs) {
        this.loginTimeoutMs = loginTimeoutMs;
    }

    public String getSessionFile() {
        return sessionFile;
    }

    public void setSessionFile(String sessionFile) {
        this.sessionFile = sessionFile;
    }

    public boolean isIgnoreSelf() {
        return ignoreSelf;
    }

    public void setIgnoreSelf(boolean ignoreSelf) {
        this.ignoreSelf = ignoreSelf;
    }
}

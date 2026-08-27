package com.example.wechataiassistant.service.task;

import com.example.wechataiassistant.service.WechatBotService;
import com.example.wechataiassistant.service.ai.ConversationMemory;
import com.example.wechataiassistant.service.ai.TimeQualifier;
import com.example.wechataiassistant.service.weather.WeatherProperties;
import com.example.wechataiassistant.service.weather.WeatherService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：
 * <ul>
 *   <li>每日定时天气播报（默认每天 08:00 给绑定账号推送默认城市天气）</li>
 *   <li>定期清理空闲会话内存（防内存增长）</li>
 *   <li>定期健康检查（登录/连接状态日志）</li>
 * </ul>
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final WechatBotService bot;
    private final WeatherService weatherService;
    private final WeatherProperties weatherProps;
    private final ConversationMemory memory;
    private final com.example.wechataiassistant.service.agent.AgentRunStore agentRunStore;

    @Value("${scheduled.weather-report.enabled:true}")
    private boolean weatherReportEnabled;

    @Value("${scheduled.weather-report.city:}")
    private String weatherReportCity;

    public ScheduledTasks(
        WechatBotService bot,
        WeatherService weatherService,
        WeatherProperties weatherProps,
        ConversationMemory memory,
        com.example.wechataiassistant.service.agent.AgentRunStore agentRunStore) {
        this.bot = bot;
        this.weatherService = weatherService;
        this.weatherProps = weatherProps;
        this.memory = memory;
        this.agentRunStore = agentRunStore;
    }

    /** 每日定时天气播报（cron 可配，默认每天 08:00）。 */
    @Scheduled(cron = "${scheduled.weather-report.cron:0 0 8 * * ?}")
    public void dailyWeatherReport() {
        if (!weatherReportEnabled) {
            return;
        }
        String to = bot.botUserId();
        if (to == null || !bot.isLoggedIn()) {
            log.warn("定时天气播报跳过：未登录");
            return;
        }
        try {
            String city = weatherReportCity.isBlank() ? weatherProps.getDefaultCity() : weatherReportCity;
            WeatherService.WeatherResult w = weatherService.getWeather(city, TimeQualifier.TODAY);
            bot.sendText(to, "🌤️ 早安！今日天气播报：\n" + w.summary());
            log.info("定时天气播报已发送到 {}", to);
        } catch (Exception e) {
            log.error("定时天气播报失败: {}", e.getMessage());
        }
    }

    /** 每小时清理超过 6 小时未活动的会话内存 + 超过 1 小时的未完成 Agent 运行。 */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 600_000)
    public void cleanIdleMemory() {
        int n = memory.clearIdle(Duration.ofHours(6));
        int m = agentRunStore.cleanupExpired(Duration.ofHours(1));
        if (n > 0 || m > 0) {
            log.info("定时清理：空闲会话 {} 个，过期 Agent 运行 {} 个", n, m);
        }
    }

    /** 每 10 分钟健康检查：记录登录与连接状态。 */
    @Scheduled(fixedRate = 600_000, initialDelay = 60_000)
    public void healthCheck() {
        if (bot.isLoggedIn()) {
            log.info("健康检查：已登录，连接状态 {}", bot.connectionStatus());
        } else {
            log.warn("健康检查：未登录（等待扫码）");
        }
    }
}

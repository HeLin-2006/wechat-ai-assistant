package com.example.wechataiassistant;

import com.example.wechataiassistant.service.WechatBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class WechatAiAssistantApplication {

    private static final Logger log = LoggerFactory.getLogger(WechatAiAssistantApplication.class);

    public static void main(String[] args) {
        log.info("🚀 正在启动微信AI助手...");

        // 启动Spring容器
        ApplicationContext context = SpringApplication.run(WechatAiAssistantApplication.class, args);

        // 获取微信机器人服务
        try {
            WechatBotService botService = context.getBean(WechatBotService.class);
            log.info("✅ 微信机器人服务已加载");

            // 检查是否已登录
            if (botService.isLoggedIn()) {
                log.info("✅ 已从本地会话恢复登录，无需扫码");
            } else {
                // 调用登录方法，force=false 表示不强制重新登录
                var result = botService.startLogin(false);
                log.info("📱 请扫码登录，二维码信息：{}", result.get("qrcodeImg"));
                // 如果 qrcodeImg 是 base64 图片，可以保存为文件或显示
            }

        } catch (Exception e) {
            log.error("❌ 微信机器人启动失败: {}", e.getMessage(), e);
        }
    }
}
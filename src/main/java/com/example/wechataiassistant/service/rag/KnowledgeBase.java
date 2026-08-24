package com.example.wechataiassistant.service.rag;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 内置知识库：关于机器人自身功能/配置/使用方法的 FAQ 文档。
 *
 * <p>选择"机器人自身知识"作为知识库，是因为大模型并不知道本项目内部实现，
 * 检索增强后的回答与纯 LLM 回答差异明显，便于对比验证 RAG 效果。</p>
 */
@Component
public class KnowledgeBase {

    public List<RagDocument> all() {
        return List.of(
            doc("voice-reply", "语音回复方式",
                List.of("语音", "语音回复", "气泡", "音频文件", "听不到"),
                "微信官方已调整协议：机器人通过 sendVoice 发送的语音气泡不再渲染（用户看不到）。"
                    + "因此机器人现在把语音以 mp3/wav 音频文件的形式发送，点开即可播放。"
                    + "语音识别（听懂用户语音）不受影响。可在配置 llm.voice-bubble-enabled 切换气泡模式。"),

            doc("weather", "天气查询",
                List.of("天气", "气温", "温度", "预报", "下雨"),
                "直接说城市和时间即可，例如「北京明天天气」「上海这周天气」。"
                    + "意图识别会自动提取城市和时间（今天/明天/后天/本周），默认城市是北京。"
                    + "数据源为和风天气（需 Key）或 Open-Meteo（免 Key 兜底）。"),

            doc("image-gen", "图片生成",
                List.of("画", "图片", "生图", "生成", "配图"),
                "说「画一只猫」或「/img 一只猫」即可，机器人会用万相模型生成图片并直接发送。"
                    + "触发前缀：/img、/image、画、生成、帮我画等。"),

            doc("tool-call", "工具调用",
                List.of("工具", "函数", "function", "技能", "链式", "日出", "日落"),
                "机器人支持 Function Calling 工具调用：get_weather、get_current_time、"
                    + "get_city_coordinates、get_sunrise_sunset、generate_image 等，"
                    + "并可链式调用（先查城市坐标，再用坐标查日出日落）。"),

            doc("config-keys", "API Key 配置",
                List.of("配置", "key", "密钥", "api key", "环境变量"),
                "API Key 统一放在本地 application-secret.properties 文件中（已被 gitignore，不会提交）。"
                    + "文本对话用 llm.api-key，图片 llm.image-api-key，语音 llm.tts-api-key，天气 weather.api-key。"
                    + "推荐用环境变量注入。"),

            doc("restart", "重启与登录",
                List.of("重启", "登录", "扫码", "会话", "免扫码"),
                "登录状态保存在 wechat-session.json，服务重启后自动恢复，无需重新扫码。"
                    + "如需要重新登录，删除该文件或浏览器访问 /wechat/ 重新扫码。"),

            doc("features", "功能清单",
                List.of("功能", "能做什么", "能力", "支持什么", "有哪些"),
                "机器人支持：文本对话（多轮记忆）、图片理解、图片生成、语音识别与语音回复、"
                    + "天气查询、意图识别、工具调用（Function Calling）、"
                    + "技能（节日查询、计算器）、RAG 知识库增强。命令：/clear 清空上下文、/help 帮助。"));
    }

    private static RagDocument doc(String id, String title, List<String> keywords, String content) {
        return new RagDocument(id, title, keywords, content);
    }
}

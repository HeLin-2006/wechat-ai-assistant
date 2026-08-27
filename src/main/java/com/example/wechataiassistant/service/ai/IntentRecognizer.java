package com.example.wechataiassistant.service.ai;

import com.example.wechataiassistant.service.llm.LlmProperties;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 意图识别：把用户文本分类为天气 / 生图 / 语音 / 指令 / 闲聊。
 *
 * <p>天气意图会额外提取城市与时间限定词（今天/明天/后天/本周），
 * 未识别出城市时使用默认城市（weather.default-city）。</p>
 */
@Component
public class IntentRecognizer {

    private final LlmProperties llmProps;

    public IntentRecognizer(LlmProperties llmProps) {
        this.llmProps = llmProps;
    }

    /** 天气相关关键词。 */
    private static final Pattern WEATHER_KEYWORDS =
        Pattern.compile("天气|气温|温度|几度|多少度|下雨|下雪|阴天|晴天|台风|降雨|降水|雨量|风力|预报|weather", Pattern.CASE_INSENSITIVE);

    /** 长任务 Agent 触发关键词（一句话目标）。 */
    private static final Pattern AGENT_KEYWORDS =
        Pattern.compile("自驾|路书|旅游攻略|旅行方案|出行方案|行程规划|自驾游|旅行计划|出游计划|游玩攻略|攻略");

    /** 城市提取模式（按优先级，非贪婪匹配避免吞并前文）。 */
    private static final Pattern[] CITY_PATTERNS = {
        Pattern.compile("([\\u4e00-\\u9fa5]{2,6}?(?:省|市|县|区|镇))\\s*的?\\s*天气"),
        Pattern.compile("查(?:一下|下)?\\s*([\\u4e00-\\u9fa5]{2,4}?)\\s*的?\\s*天气"),
        Pattern.compile("(?:在|去|到)\\s*([\\u4e00-\\u9fa5]{2,4}?)\\s*的?\\s*天气"),
        Pattern.compile("([\\u4e00-\\u9fa5]{2,4}?)\\s*的?\\s*天气"),
        Pattern.compile("查(?:一下|下)?\\s*([\\u4e00-\\u9fa5]{2,4}?)\\s*(?:气温|温度|几度|多少度|降雨|降水|下雨|下雪)"),
        Pattern.compile("([\\u4e00-\\u9fa5]{2,4}?)\\s*(?:气温|温度|几度|多少度|降雨|降水|下雨|下雪)"),
    };

    /** 城市提取后需要剔除的残留词。 */
    private static final Pattern CITY_TRIM =
        Pattern.compile("怎么样|如何|什么|情况|怎样|预报|天气|帮我|请问|一下|看看|看下|现在|目前|这边|这里|我家|本地");

    public IntentResult recognize(String raw) {
        if (raw == null || raw.isBlank()) {
            return IntentResult.simple(Intent.CHAT);
        }
        String text = raw.trim();

        // 指令类（精确匹配）
        if ("/clear".equalsIgnoreCase(text) || "清空上下文".equals(text)) {
            return IntentResult.simple(Intent.CLEAR);
        }
        if ("/help".equalsIgnoreCase(text) || "帮助".equals(text)) {
            return IntentResult.simple(Intent.HELP);
        }
        if (text.equals("/语音模式") || text.equals("/voice-mode")
            || text.equals("/语音开") || text.equals("/voice-on")
            || text.equals("/语音关") || text.equals("/voice-off")) {
            return IntentResult.withPayload(Intent.VOICE_MODE, text);
        }

        // 长任务 Agent（优先于天气/生图等单步意图）
        if (AGENT_KEYWORDS.matcher(text).find()) {
            return IntentResult.withPayload(Intent.AGENT, text);
        }

        // 天气意图（优先级高于生图/语音前缀，避免「画明天的天气」误判为生图）
        if (WEATHER_KEYWORDS.matcher(text).find()) {
            String city = extractCity(text);
            TimeQualifier time = extractTime(text);
            return new IntentResult(Intent.WEATHER, null, city, time);
        }

        // 图片生成（/img、画…）
        String imagePrompt = matchPrefix(text, llmProps.getImagePrefixes());
        if (imagePrompt != null) {
            return IntentResult.withPayload(Intent.IMAGE_GEN, imagePrompt);
        }

        // 语音（/语音、/voice）
        String voiceSpeak = matchPrefix(text, llmProps.getVoicePrefixes());
        if (voiceSpeak != null) {
            return IntentResult.withPayload(Intent.VOICE_SPEAK, voiceSpeak);
        }

        return IntentResult.simple(Intent.CHAT);
    }

    /** 提取城市；未识别返回 null（调用方使用默认城市）。 */
    public String extractCity(String text) {
        String s = stripTimeWords(text);
        for (Pattern p : CITY_PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                String city = CITY_TRIM.matcher(m.group(1)).replaceAll("").trim();
                if (!city.isEmpty()) {
                    return city;
                }
            }
        }
        return null;
    }

    /** 提取时间限定词。 */
    public TimeQualifier extractTime(String text) {
        if (text.contains("大后天")) {
            return TimeQualifier.DAY_AFTER;
        }
        if (text.contains("后天")) {
            return TimeQualifier.DAY_AFTER;
        }
        if (text.contains("明天") || text.contains("明晚")) {
            return TimeQualifier.TOMORROW;
        }
        if (text.contains("周末") || text.contains("这周") || text.contains("本周")) {
            return TimeQualifier.WEEK;
        }
        return TimeQualifier.TODAY;
    }

    /** 去掉时间词，避免「今天天气」把「今天」当成城市。 */
    private static String stripTimeWords(String text) {
        return text.replaceAll("大后天|后天|明天|明晚|今晚|今天|昨天|周末|这周|本周|下周|早上|中午|晚上|下午|上午", "");
    }

    private static String matchPrefix(String raw, List<String> prefixes) {
        if (raw == null || prefixes == null) {
            return null;
        }
        for (String p : prefixes) {
            String prefix = p.trim();
            if (prefix.isEmpty()) {
                continue;
            }
            if (raw.startsWith(prefix)) {
                return raw.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}

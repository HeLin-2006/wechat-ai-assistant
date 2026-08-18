package com.example.wechataiassistant.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.wechataiassistant.service.llm.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntentRecognizerTest {

    private IntentRecognizer recognizer;

    @BeforeEach
    void setUp() {
        LlmProperties props = new LlmProperties();
        props.setImagePrefixes(List.of("/img", "/image", "画", "生成图片", "生成一张", "帮我画"));
        props.setVoicePrefixes(List.of("/语音", "/voice"));
        recognizer = new IntentRecognizer(props);
    }

    @Test
    void weatherIntent() {
        IntentResult r = recognizer.recognize("北京明天天气怎么样");
        assertEquals(Intent.WEATHER, r.intent());
        assertEquals("北京", r.city());
        assertEquals(TimeQualifier.TOMORROW, r.time());
    }

    @Test
    void weatherNoCity() {
        IntentResult r = recognizer.recognize("今天会下雨吗");
        assertEquals(Intent.WEATHER, r.intent());
        assertNull(r.city());
        assertEquals(TimeQualifier.TODAY, r.time());
    }

    @Test
    void weatherWeek() {
        IntentResult r = recognizer.recognize("上海这周的天气");
        assertEquals(Intent.WEATHER, r.intent());
        assertEquals("上海", r.city());
        assertEquals(TimeQualifier.WEEK, r.time());
    }

    @Test
    void weatherTemperature() {
        IntentResult r = recognizer.recognize("现在深圳几度");
        assertEquals(Intent.WEATHER, r.intent());
        assertEquals("深圳", r.city());
    }

    @Test
    void weatherAfterTomorrow() {
        IntentResult r = recognizer.recognize("广州大后天天气");
        assertEquals(Intent.WEATHER, r.intent());
        assertEquals("广州", r.city());
        assertEquals(TimeQualifier.DAY_AFTER, r.time());
    }

    @Test
    void imageGenIntent() {
        IntentResult r = recognizer.recognize("画一只戴眼镜的猫");
        assertEquals(Intent.IMAGE_GEN, r.intent());
        assertEquals("一只戴眼镜的猫", r.payload());
    }

    @Test
    void voiceSpeakIntent() {
        IntentResult r = recognizer.recognize("/语音 你好呀");
        assertEquals(Intent.VOICE_SPEAK, r.intent());
        assertEquals("你好呀", r.payload());
    }

    @Test
    void clearAndHelp() {
        assertEquals(Intent.CLEAR, recognizer.recognize("/clear").intent());
        assertEquals(Intent.HELP, recognizer.recognize("帮助").intent());
    }

    @Test
    void chatFallback() {
        assertEquals(Intent.CHAT, recognizer.recognize("讲个笑话").intent());
        assertEquals(Intent.CHAT, recognizer.recognize("你好").intent());
    }
}

package com.example.wechataiassistant.service.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillServiceTest {

    private final SkillService service = new SkillService(java.util.List.of(new HolidaySkill(), new CalculatorSkill()));

    @Test
    void holidaySkillHit() {
        Optional<String> r = service.executeIfMatched("今天是什么节日", "u1");
        assertTrue(r.isPresent(), "应命中节日技能");
        assertTrue(r.get().contains("年") && r.get().contains("月"), "应包含日期: " + r.get());
    }

    @Test
    void calculatorSkillHit() {
        Optional<String> r = service.executeIfMatched("帮我计算 3+5*2", "u1");
        assertTrue(r.isPresent());
        assertTrue(r.get().endsWith("= 13"), "3+5*2 应为 13: " + r.get());
    }

    @Test
    void calculatorParensAndPercent() {
        assertEquals("计算 (3+4)*5 = 35", service.executeIfMatched("计算 (3+4)*5", "u1").get());
        assertEquals("计算 7%3 = 1", service.executeIfMatched("计算 7%3", "u1").get());
    }

    @Test
    void calculatorDivisionByZero() {
        Optional<String> r = service.executeIfMatched("计算 5/0", "u1");
        assertTrue(r.get().contains("除数不能为 0"));
    }

    @Test
    void noSkillMatched() {
        assertEquals(Optional.empty(), service.executeIfMatched("你好，介绍一下自己", "u1"));
    }

    @Test
    void keywordPriorityBetweenSkills() {
        // 「计算」命中计算器，「节日」命中节日——互不干扰
        Optional<String> calc = service.executeIfMatched("计算 2+2", "u1");
        Optional<String> holi = service.executeIfMatched("明天有什么节日", "u1");
        assertTrue(calc.get().contains("= 4"));
        assertTrue(holi.get().contains("节日") || holi.get().contains("没有"));
    }
}

package com.example.wechataiassistant.service.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentAssemblerTest {

    @Test
    void assembleProducesCompleteDocument() {
        AgentPlan.RouteInfo route =
            new AgentPlan.RouteInfo("成都", "稻城亚丁", List.of("成都", "康定", "稻城亚丁"), 900, 6);
        AgentPlan plan = new AgentPlan("规划一次成都到稻城亚丁的自驾游", route, List.of());

        AgentContext ctx = new AgentContext("u1", null);
        ctx.putResult("route", Map.of("start", "成都", "end", "稻城亚丁",
            "cities", List.of("成都", "康定", "稻城亚丁"), "totalKm", 900, "days", 6));
        ctx.putResult("weather", Map.of("成都", "成都 晴 26℃", "康定", "康定 多云 14℃"));
        ctx.putResult("sun", "今日日出 06:37，日落 19:33");
        ctx.putResult("budget", Map.of("oil", 486L, "toll", 380L, "hotel", 1050L, "food", 600L, "total", 2516L, "kmKnown", true));
        ctx.putResult("safety", "注意休息与高原反应");

        String doc = new AgentAssembler().assemble(plan, ctx);
        assertTrue(doc.contains("【成都 → 稻城亚丁】"), doc);
        assertTrue(doc.contains("成都"), "应含路线城市");
        assertTrue(doc.contains("成都 晴 26℃"), "应含逐城天气");
        assertTrue(doc.contains("日出 06:37"), "应含日出日落");
        assertTrue(doc.contains("总计：2516 元"), "应含预算总计");
        assertTrue(doc.contains("注意休息与高原反应"), "应含安全知识");
    }

    @Test
    void missingSectionsShowPlaceholder() {
        AgentPlan.RouteInfo route = new AgentPlan.RouteInfo("北京", "青岛", List.of("北京", "青岛"), 0, 3);
        AgentPlan plan = new AgentPlan("做一份北京到青岛的自驾路书", route, List.of());
        AgentContext ctx = new AgentContext("u1", null);

        String doc = new AgentAssembler().assemble(plan, ctx);
        assertTrue(doc.contains("该环节暂缺"), "缺数据应显示占位");
        assertTrue(doc.contains("温馨提示"), "应含温馨提示章节");
    }
}

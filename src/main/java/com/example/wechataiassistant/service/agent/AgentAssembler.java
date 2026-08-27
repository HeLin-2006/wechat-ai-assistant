package com.example.wechataiassistant.service.agent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Agent 汇总器：把各子任务结果按固定章节模板拼装为完整路书成品。 */
@Component
public class AgentAssembler {

    public String assemble(AgentPlan plan, AgentContext ctx) {
        AgentPlan.RouteInfo route = plan.route();
        String start = str(ctx.getResult("route"), "start", route.start());
        String end = str(ctx.getResult("route"), "end", route.end());

        StringBuilder sb = new StringBuilder();
        sb.append("🚗【").append(start).append(" → ").append(end).append("】自驾路书\n");
        sb.append("生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))).append("\n\n");

        section(sb, "一、路线总览", overview(route, ctx));
        section(sb, "二、逐城天气预报", weatherSection(ctx));
        section(sb, "三、日出日落（建议出发时间）", val(ctx, "sun"));
        section(sb, "四、预算明细", budgetSection(ctx));
        section(sb, "五、自驾安全与注意事项", val(ctx, "safety"));
        section(sb, "六、路书封面", val(ctx, "poster"));
        section(sb, "七、温馨提示",
            "· 以上天气/日出数据来自公开接口，出行前 1 天请再次确认\n· 实时路况与封路信息以交管部门发布为准\n· 长途驾驶注意休息，安全第一！");

        if (!ctx.errors().isEmpty()) {
            sb.append("\n⚠️ 部分环节暂未完成：").append(String.join("；", ctx.errors().values())).append("\n");
        }
        return sb.toString();
    }

    private String overview(AgentPlan.RouteInfo route, AgentContext ctx) {
        Object routeRes = ctx.getResult("route");
        int km = routeRes instanceof Map<?, ?> m ? num(m.get("totalKm"), route.totalKm()) : route.totalKm();
        int days = routeRes instanceof Map<?, ?> m ? num(m.get("days"), route.days()) : route.days();
        @SuppressWarnings("unchecked")
        List<String> cities = routeRes instanceof Map<?, ?> m && m.get("cities") instanceof List<?> l
            ? (List<String>) l : route.cities();
        return "途经城市：" + String.join(" → ", cities)
            + "\n预计总里程：" + (km > 0 ? km + " km" : "（未提供）")
            + "\n预计天数：" + days + " 天";
    }

    private String weatherSection(AgentContext ctx) {
        Object w = ctx.getResult("weather");
        if (w instanceof Map<?, ?> m && !m.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append("· ").append(e.getKey()).append("：").append(e.getValue()).append("\n");
            }
            return sb.toString().trim();
        }
        return val(ctx, "weather");
    }

    private String budgetSection(AgentContext ctx) {
        Object b = ctx.getResult("budget");
        if (b instanceof Map<?, ?> m && !m.isEmpty()) {
            boolean kmKnown = Boolean.parseBoolean(String.valueOf(m.get("kmKnown")));
            StringBuilder sb = new StringBuilder();
            if (!kmKnown) {
                sb.append("（未提供总里程，按 3 天标准估算）\n");
            }
            sb.append("· 油费（8L/百公里×8元/L）：").append(m.get("oil")).append(" 元\n");
            sb.append("· 过路费（约0.5元/km）：").append(m.get("toll")).append(" 元\n");
            sb.append("· 住宿（350元/晚）：").append(m.get("hotel")).append(" 元\n");
            sb.append("· 餐饮（150元/天）：").append(m.get("food")).append(" 元\n");
            sb.append("· 💰 总计：").append(m.get("total")).append(" 元");
            return sb.toString();
        }
        return val(ctx, "budget");
    }

    private static void section(StringBuilder sb, String title, String content) {
        sb.append("## ").append(title).append("\n");
        sb.append(content == null || content.isBlank() ? "（该环节暂缺）" : content).append("\n\n");
    }

    private static String val(AgentContext ctx, String key) {
        Object v = ctx.getResult(key);
        if (v == null) {
            return "（该环节暂缺）";
        }
        if (v instanceof Map<?, ?> m && m.isEmpty()) {
            return "（该环节暂缺）";
        }
        return String.valueOf(v);
    }

    private static String str(Object obj, String key, String fallback) {
        if (obj instanceof Map<?, ?> m && m.get(key) != null) {
            return String.valueOf(m.get(key));
        }
        return fallback == null || fallback.isBlank() ? "（未指定）" : fallback;
    }

    private static int num(Object o, int fallback) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

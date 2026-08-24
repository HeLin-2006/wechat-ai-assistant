package com.example.wechataiassistant.service.skill;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 自定义技能：节日/纪念日查询（确定性输出，不调用 LLM）。
 *
 * <p>触发关键词：节日、放假、纪念日、什么日子。</p>
 * <p>内置公历节日表，输出今天的节日；没有则提示近期节日。</p>
 */
@Component
public class HolidaySkill implements Skill {

    /** 固定公历节日（月-日 → 名称）。 */
    private static final Map<String, String> HOLIDAYS =
        Map.ofEntries(
            Map.entry("01-01", "元旦"),
            Map.entry("02-14", "情人节"),
            Map.entry("03-08", "妇女节"),
            Map.entry("04-01", "愚人节"),
            Map.entry("05-01", "劳动节"),
            Map.entry("06-01", "儿童节"),
            Map.entry("08-01", "建军节"),
            Map.entry("09-10", "教师节"),
            Map.entry("10-01", "国庆节"),
            Map.entry("11-11", "双十一购物节"),
            Map.entry("12-25", "圣诞节"));

    /** 按日期排序的节日列表（月-日 → 名称），用于找"最近节日"。 */
    private static final List<Map.Entry<String, String>> SORTED =
        HOLIDAYS.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();

    private static final String[] WEEKS = {"一", "二", "三", "四", "五", "六", "日"};

    @Override
    public String name() {
        return "holiday_query";
    }

    @Override
    public String description() {
        return "查询今天的节日/纪念日，以及最近的节日";
    }

    @Override
    public List<String> keywords() {
        return List.of("节日", "放假", "纪念日", "什么日子", "今天是什么");
    }

    @Override
    public String execute(String userId, String content) {
        LocalDate today = LocalDate.now();
        String key = today.format(DateTimeFormatter.ofPattern("MM-dd"));
        String dateLine =
            today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                + " 星期" + WEEKS[today.getDayOfWeek().getValue() - 1];

        String todayHoliday = HOLIDAYS.get(key);
        if (todayHoliday != null) {
            return dateLine + "\n🎉 今天是「" + todayHoliday + "」！";
        }

        // 找最近的下一个节日
        int todayOrdinal = today.getDayOfYear();
        for (Map.Entry<String, String> e : SORTED) {
            LocalDate d = LocalDate.of(today.getYear(), Integer.parseInt(e.getKey().substring(0, 2)), Integer.parseInt(e.getKey().substring(3)));
            if (!d.isBefore(today)) {
                long days = d.toEpochDay() - today.toEpochDay();
                return dateLine + "\n今天没有特殊节日。\n📅 最近的节日是「"
                    + e.getValue() + "」（" + d.format(DateTimeFormatter.ofPattern("M月d日"))
                    + (days == 0 ? "" : "，还有 " + days + " 天") + "）";
            }
        }
        return dateLine + "\n今天没有特殊节日。";
    }
}

package com.example.wechataiassistant.service.tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具：获取当前日期和时间（无参数）。
 */
@Component
public class CurrentTimeTool implements Tool {

    private static final String SCHEMA =
        """
        {
          "type": "object",
          "properties": {},
          "required": []
        }
        """;

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "获取当前日期和时间（含星期几）。用户问「现在几点」「今天是几号」「今天是星期几」时调用。";
    }

    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Map<String, Object> args, ToolContext ctx) {
        LocalDateTime now = LocalDateTime.now();
        String[] weeks = {"一", "二", "三", "四", "五", "六", "日"};
        String time = now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"));
        return time + " 星期" + weeks[now.getDayOfWeek().getValue() - 1];
    }
}

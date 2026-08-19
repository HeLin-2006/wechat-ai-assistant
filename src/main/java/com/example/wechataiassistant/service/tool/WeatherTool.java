package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.ai.TimeQualifier;
import com.example.wechataiassistant.service.weather.WeatherService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具：查询天气。参数 city（可选，默认城市）、when（可选，枚举）。
 */
@Component
public class WeatherTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    private static final String SCHEMA =
        """
        {
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "城市名称，例如 北京、上海、深圳"
            },
            "when": {
              "type": "string",
              "enum": ["today", "tomorrow", "dayafter", "week"],
              "description": "查询时间：today=今天(默认)，tomorrow=明天，dayafter=后天，week=本周"
            }
          },
          "required": []
        }
        """;

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询指定城市的实时天气和近期预报，包含温度、天气现象、风速湿度。用户问天气、气温、温度、会不会下雨时调用。";
    }

    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Map<String, Object> args, ToolContext ctx) {
        String city = args.get("city") == null ? "" : String.valueOf(args.get("city"));
        TimeQualifier when = parseWhen(args.get("when"));
        try {
            WeatherService.WeatherResult r = weatherService.getWeather(city, when);
            return r.summary();
        } catch (Exception e) {
            log.warn("天气工具执行失败: {}", e.getMessage());
            return "天气查询失败：" + e.getMessage();
        }
    }

    private static TimeQualifier parseWhen(Object o) {
        if (o == null) {
            return TimeQualifier.TODAY;
        }
        return switch (String.valueOf(o)) {
            case "tomorrow", "明天" -> TimeQualifier.TOMORROW;
            case "dayafter", "后天" -> TimeQualifier.DAY_AFTER;
            case "week", "本周" -> TimeQualifier.WEEK;
            default -> TimeQualifier.TODAY;
        };
    }
}

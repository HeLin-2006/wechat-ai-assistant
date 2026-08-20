package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.weather.OpenMeteoClient;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 链式工具第二步：经纬度 → 今日日出/日落时间。
 * 输入坐标来自 get_city_coordinates 的输出（依赖上一步结果）。
 */
@Component
public class GetSunriseSunsetTool implements Tool {

    private final OpenMeteoClient openMeteo;

    public GetSunriseSunsetTool(OpenMeteoClient openMeteo) {
        this.openMeteo = openMeteo;
    }

    private static final String SCHEMA =
        """
        {
          "type": "object",
          "properties": {
            "latitude": {
              "type": "number",
              "description": "纬度，例如 31.2304（来自 get_city_coordinates 的返回值）"
            },
            "longitude": {
              "type": "number",
              "description": "经度，例如 121.4737（来自 get_city_coordinates 的返回值）"
            }
          },
          "required": ["latitude", "longitude"]
        }
        """;

    @Override
    public String name() {
        return "get_sunrise_sunset";
    }

    @Override
    public String description() {
        return "根据经纬度返回今天的日出和日落时间。"
            + "必须先调用 get_city_coordinates 获得城市坐标，再把它的 latitude、longitude 作为本工具参数。";
    }

    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Map<String, Object> args, ToolContext ctx) {
        double lat = parseDouble(args.get("latitude"));
        double lon = parseDouble(args.get("longitude"));
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            throw new IllegalArgumentException("latitude/longitude 必须是数字");
        }
        if (!OpenMeteoClient.isValidLatLon(lat, lon)) {
            throw new IllegalArgumentException("latitude/longitude 超出合法范围（纬度 -90~90，经度 -180~180）");
        }
        JsonNode d = openMeteo.forecast(lat, lon, "sunrise,sunset", 1);
        String sunrise = d.path("daily").path("sunrise").get(0).asText("");
        String sunset = d.path("daily").path("sunset").get(0).asText("");
        if (sunrise.isEmpty() || sunset.isEmpty()) {
            return "查询失败：该位置没有日出日落数据";
        }
        return String.format(
            "今日日出 %s，日落 %s（UTC+8）",
            sunrise.substring(11), sunset.substring(11));
    }

    private static double parseDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}

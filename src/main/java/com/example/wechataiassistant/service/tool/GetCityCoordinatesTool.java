package com.example.wechataiassistant.service.tool;

import com.example.wechataiassistant.service.weather.OpenMeteoClient;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 链式工具第一步：城市名 → 经纬度。
 * 输出格式固定为 "latitude=xx.x, longitude=yy.y"，供 get_sunrise_sunset 等工具使用。
 */
@Component
public class GetCityCoordinatesTool implements Tool {

    private final OpenMeteoClient openMeteo;

    public GetCityCoordinatesTool(OpenMeteoClient openMeteo) {
        this.openMeteo = openMeteo;
    }

    private static final String SCHEMA =
        """
        {
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "城市名称，例如 上海、北京、深圳"
            }
          },
          "required": ["city"]
        }
        """;

    @Override
    public String name() {
        return "get_city_coordinates";
    }

    @Override
    public String description() {
        return "把城市名转换为经纬度坐标。这是获取日出日落时间的第一步："
            + "用户问「某城市今天几点日出/日落」时，先调用本工具拿到坐标，"
            + "再把返回的 latitude 和 longitude 作为参数调用 get_sunrise_sunset。";
    }

    @Override
    public String parametersSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(Map<String, Object> args, ToolContext ctx) {
        String city = args.get("city") == null ? "" : String.valueOf(args.get("city")).trim();
        if (city.isEmpty()) {
            return "参数错误：缺少城市名 city";
        }
        OpenMeteoClient.GeocodeResult geo = openMeteo.geocode(city);
        // 固定格式，方便 LLM 提取坐标传给下一步
        return String.format(
            "城市 %s 的坐标：latitude=%.4f, longitude=%.4f",
            geo.city(), geo.latitude(), geo.longitude());
    }
}

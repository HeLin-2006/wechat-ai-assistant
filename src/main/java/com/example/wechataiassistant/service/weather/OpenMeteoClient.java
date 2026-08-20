package com.example.wechataiassistant.service.weather;

import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Open-Meteo 免 Key 天气客户端（共享组件）。
 *
 * <p>提供城市地理编码（城市名 → 经纬度）与天气/日出日落预报查询，
 * 供 {@link WeatherService} 与链式工具（坐标 → 日出日落）复用。</p>
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private static final String GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final ObjectMapper mapper;
    private final RestClient client;

    public OpenMeteoClient(ObjectMapper mapper) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** 地理编码结果。 */
    public record GeocodeResult(String city, double latitude, double longitude) {}

    /** 城市名 → 经纬度。 */
    public GeocodeResult geocode(String city) {
        JsonNode r =
            getJson(GEOCODE_URL + "?name=" + enc(city) + "&count=1&language=zh&format=json")
                .path("results")
                .path(0);
        if (r.isMissingNode()) {
            throw new WeatherService.WeatherException("未找到城市: " + city);
        }
        return new GeocodeResult(
            r.path("name").asText(city),
            r.path("latitude").asDouble(),
            r.path("longitude").asDouble());
    }

    /**
     * 按经纬度查预报。
     *
     * @param dailyFields 需要的日级字段（如 temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset）
     * @return 响应 JSON（含 current / daily）
     */
    public JsonNode forecast(double latitude, double longitude, String dailyFields, int forecastDays) {
        String url =
            FORECAST_URL
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m"
                + "&daily=" + dailyFields
                + "&timezone=Asia%2FShanghai"
                + "&forecast_days=" + forecastDays;
        return getJson(url);
    }

    /** 校验经纬度是否合法。 */
    public static boolean isValidLatLon(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /** WMO 天气代码 -> 中文描述。 */
    public static String wmoText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "晴间多云";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "雪粒";
            case 80, 81 -> "阵雨";
            case 82 -> "强阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷阵雨";
            case 96, 99 -> "雷阵雨伴冰雹";
            default -> "未知";
        };
    }

    private JsonNode getJson(String url) {
        try {
            // 用 URI.create 避免 Spring 的 UriBuilder 对已编码 URL 二次编码（会破坏查询参数）
            String body = client.get().uri(java.net.URI.create(url)).retrieve().body(String.class);
            return mapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw new WeatherService.WeatherException(
                "天气接口返回 " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WeatherService.WeatherException("天气查询失败: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}

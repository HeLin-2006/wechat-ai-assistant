package com.example.wechataiassistant.service.weather;

import java.time.Duration;
import java.util.Map;
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

    /** 地理编码结果（含时区，供预报使用）。 */
    public record GeocodeResult(String city, double latitude, double longitude, String timezone) {}

    /** 常见国际城市的中文名 → 英文名映射（Open-Meteo 对部分中文名支持差）。 */
    private static final Map<String, String> INTL_CITY_MAP =
        Map.ofEntries(
            Map.entry("纽约", "New York"),
            Map.entry("东京", "Tokyo"),
            Map.entry("巴黎", "Paris"),
            Map.entry("伦敦", "London"),
            Map.entry("悉尼", "Sydney"),
            Map.entry("柏林", "Berlin"),
            Map.entry("莫斯科", "Moscow"),
            Map.entry("首尔", "Seoul"),
            Map.entry("新加坡", "Singapore"),
            Map.entry("曼谷", "Bangkok"),
            Map.entry("迪拜", "Dubai"),
            Map.entry("多伦多", "Toronto"),
            Map.entry("旧金山", "San Francisco"),
            Map.entry("洛杉矶", "Los Angeles"),
            Map.entry("芝加哥", "Chicago"),
            Map.entry("罗马", "Rome"),
            Map.entry("马德里", "Madrid"),
            Map.entry("阿姆斯特丹", "Amsterdam"),
            Map.entry("维也纳", "Vienna"),
            Map.entry("开罗", "Cairo"),
            Map.entry("伊斯坦布尔", "Istanbul"),
            Map.entry("孟买", "Mumbai"),
            Map.entry("圣保罗", "Sao Paulo"),
            Map.entry("墨西哥城", "Mexico City"));

    /**
     * 城市名 → 经纬度（带回退链，提高命中率）：
     * <ol>
     *   <li>直接按原名查询</li>
     *   <li>国际城市映射表（纽约→New York，东京→Tokyo，避免解析到同名小城）</li>
     *   <li>去"市"前缀取区县名（上海浦东新区→浦东新区）</li>
     *   <li>取市级前缀兜底（北京朝阳区→北京，杭州市西湖区→杭州）</li>
     * </ol>
     */
    public GeocodeResult geocode(String city) {
        String original = city;
        String[] candidates = {original};

        if (city.length() > 2 && (city.endsWith("区") || city.endsWith("县") || city.endsWith("镇"))) {
            int cityIdx = city.indexOf("市");
            if (cityIdx > 0) {
                // 上海浦东新区 -> 浦东新区；杭州市西湖区 -> 西湖区 或 杭州
                String district = city.substring(cityIdx + 1);
                String cityPart = city.substring(0, cityIdx);
                candidates = new String[] {original, district, cityPart};
            } else if (city.length() >= 4) {
                // 北京朝阳区 -> 北京（取前两个字的市级兜底）
                candidates = new String[] {original, city.substring(0, 2)};
            }
        }

        String intl = INTL_CITY_MAP.get(original);
        if (intl != null) {
            GeocodeResult r = search(original, intl, "en");
            if (r != null) {
                return r;
            }
        }

        for (String c : candidates) {
            GeocodeResult r = search(c, null, "zh");
            if (r != null) {
                return r;
            }
        }

        throw new WeatherService.WeatherException(
            "未找到城市: " + original
                + "（试试「北京」「上海」等常见城市名；配置和风天气 Key 可支持更细的区县查询）");
    }

    /** 单次搜索：给定了英文名就直接查英文名（国际城市，避免中文名在英文模式下排到同名小城）；否则查中文名。 */
    private GeocodeResult search(String nameZh, String nameEn, String lang) {
        String query = (nameEn != null && !nameEn.isBlank()) ? nameEn : nameZh;
        JsonNode results =
            getJson(GEOCODE_URL + "?name=" + enc(query) + "&count=3&language=" + lang + "&format=json")
                .path("results");
        if (results.isMissingNode() || results.isEmpty()) {
            return null;
        }
        JsonNode first = results.get(0);
        return new GeocodeResult(
            first.path("name").asText(nameZh),
            first.path("latitude").asDouble(),
            first.path("longitude").asDouble(),
            first.path("timezone").asText("Asia/Shanghai"));
    }

    /**
     * 按经纬度查预报。
     *
     * @param dailyFields 需要的日级字段（如 temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset）
     * @return 响应 JSON（含 current / daily）
     */
    public JsonNode forecast(double latitude, double longitude, String dailyFields, int forecastDays) {
        return forecast(latitude, longitude, dailyFields, forecastDays, "Asia/Shanghai");
    }

    /** 按经纬度查预报，可指定时区（外国城市用当地时区，保证日出日落/日期正确）。 */
    public JsonNode forecast(
        double latitude, double longitude, String dailyFields, int forecastDays, String timezone) {
        String tz = (timezone == null || timezone.isBlank()) ? "Asia/Shanghai" : timezone;
        String url =
            FORECAST_URL
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m"
                + "&daily=" + dailyFields
                + "&timezone=" + enc(tz)
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

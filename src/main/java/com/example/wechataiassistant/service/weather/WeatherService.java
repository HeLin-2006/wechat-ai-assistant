package com.example.wechataiassistant.service.weather;

import com.example.wechataiassistant.service.ai.TimeQualifier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 天气服务：根据城市返回准确的实时天气与近期预报。
 *
 * <ul>
 *   <li>和风天气（QWeather）：国内准确，需 API Key（weather.provider=qweather）</li>
 *   <li>Open-Meteo：免 Key 兜底（weather.provider=open-meteo），qweather 未配 Key 时自动回退</li>
 * </ul>
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;
    private final OpenMeteoClient openMeteo;

    public WeatherService(WeatherProperties props, ObjectMapper mapper, OpenMeteoClient openMeteo) {
        this.props = props;
        this.mapper = mapper;
        this.openMeteo = openMeteo;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public record WeatherResult(String city, String summary) {}

    /** 查询天气。city 为空或为占位词（这里/我家等）时使用默认城市。 */
    public WeatherResult getWeather(String city, TimeQualifier when) {
        String c = normalizeCity(city);
        boolean useQWeather = "qweather".equalsIgnoreCase(props.getProvider())
            && props.getApiKey() != null
            && !props.getApiKey().isBlank();
        if (useQWeather) {
            try {
                return getQWeather(c, when);
            } catch (Exception e) {
                log.warn("和风天气查询失败，回退 Open-Meteo: {}", e.getMessage());
            }
        }
        return getOpenMeteo(c, when);
    }

    /** 把空值/占位词（这里、我家、本地等）归一为默认城市。 */
    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return props.getDefaultCity();
        }
        String t = city.trim();
        if (List.of("这里", "这边", "我家", "本地", "now", "here").contains(t)) {
            return props.getDefaultCity();
        }
        return t;
    }

    // ------------------------------------------------------------------
    // 和风天气（QWeather）
    // ------------------------------------------------------------------

    private WeatherResult getQWeather(String city, TimeQualifier when) {
        String key = props.getApiKey();
        String base = props.getQweatherBaseUrl();

        JsonNode lookup = getJson(base + "/v2/city/lookup?location=" + enc(city) + "&key=" + key);
        JsonNode loc = lookup.path("location").path(0);
        if (loc.isMissingNode()) {
            throw new WeatherException("未找到城市: " + city + "（" + lookup.path("message").asText("") + "）");
        }
        String locId = loc.path("id").asText();
        String name = loc.path("name").asText(city);

        StringBuilder sb = new StringBuilder();
        if (when == TimeQualifier.TODAY) {
            JsonNode now = getJson(base + "/v7/weather/now?location=" + locId + "&key=" + key).path("now");
            sb.append(name).append(" 现在：")
                .append(now.path("text").asText("未知")).append("，")
                .append(now.path("temp").asText("?")).append("℃")
                .append("（体感 ").append(now.path("feelsLike").asText("?")).append("℃）")
                .append("，").append(now.path("windDir").asText("")).append(now.path("windScale").asText("")).append("级")
                .append("，湿度 ").append(now.path("humidity").asText("?")).append("%");
        } else {
            JsonNode daily = getJson(base + "/v7/weather/3d?location=" + locId + "&key=" + key).path("daily");
            int[] idx = indexFor(when);
            sb.append(name).append(" 天气：");
            for (int i = 0; i < idx.length && i < daily.size(); i++) {
                JsonNode d = daily.get(idx[i]);
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(dayLabel(idx[i]))
                    .append(d.path("textDay").asText("未知")).append(" ")
                    .append(d.path("tempMin").asText("?")).append("~").append(d.path("tempMax").asText("?")).append("℃");
            }
        }
        return new WeatherResult(name, sb.toString());
    }

    // ------------------------------------------------------------------
    // Open-Meteo（免 Key 兜底）
    // ------------------------------------------------------------------

    private WeatherResult getOpenMeteo(String city, TimeQualifier when) {
        OpenMeteoClient.GeocodeResult geo = openMeteo.geocode(city);
        JsonNode d =
            openMeteo.forecast(
                geo.latitude(),
                geo.longitude(),
                "temperature_2m_max,temperature_2m_min,weather_code",
                3,
                geo.timezone());
        JsonNode current = d.path("current");
        JsonNode daily = d.path("daily");

        StringBuilder sb = new StringBuilder();
        if (when == TimeQualifier.TODAY) {
            sb.append(geo.city()).append(" 现在：")
                .append(OpenMeteoClient.wmoText(current.path("weather_code").asInt(-1))).append("，")
                .append(current.path("temperature_2m").asDouble()).append("℃")
                .append("，风速 ").append(current.path("wind_speed_10m").asDouble()).append(" km/h")
                .append("，湿度 ").append(current.path("relative_humidity_2m").asInt(-1)).append("%");
        } else {
            int[] idx = indexFor(when);
            sb.append(geo.city()).append(" 天气：");
            for (int i = 0; i < idx.length && i < daily.path("time").size(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(dayLabel(idx[i]))
                    .append(OpenMeteoClient.wmoText(daily.path("weather_code").get(idx[i]).asInt(-1))).append(" ")
                    .append(daily.path("temperature_2m_min").get(idx[i]).asDouble()).append("~")
                    .append(daily.path("temperature_2m_max").get(idx[i]).asDouble()).append("℃");
            }
        }
        return new WeatherResult(geo.city(), sb.toString());
    }

    /** 时间限定对应的预报下标。 */
    private static int[] indexFor(TimeQualifier when) {
        return switch (when) {
            case TODAY -> new int[] {0};
            case TOMORROW -> new int[] {1};
            case DAY_AFTER -> new int[] {2};
            case WEEK -> new int[] {0, 1, 2};
        };
    }

    private static String dayLabel(int i) {
        return switch (i) {
            case 0 -> "今天 ";
            case 1 -> "明天 ";
            case 2 -> "后天 ";
            default -> "";
        };
    }

    // ------------------------------------------------------------------

    private JsonNode getJson(String url) {
        try {
            // 用 URI.create 避免 Spring 的 UriBuilder 对已编码 URL 二次编码（会破坏查询参数）
            String body = client.get().uri(java.net.URI.create(url)).retrieve().body(String.class);
            return mapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw new WeatherException("天气接口返回 " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WeatherException("天气查询失败: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 天气查询异常。 */
    public static class WeatherException extends RuntimeException {
        public WeatherException(String message) {
            super(message);
        }

        public WeatherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

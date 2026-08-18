package com.example.wechataiassistant.service.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 天气服务配置（前缀 weather.*）。
 *
 * <p>provider 支持两种：</p>
 * <ul>
 *   <li>{@code qweather}：和风天气（国内准确度高，需注册获取 API Key）</li>
 *   <li>{@code open-meteo}：Open-Meteo（免费免 Key，全球城市，含中文城市名）</li>
 * </ul>
 * provider 配置为 qweather 但未配置 api-key 时，自动回退到 open-meteo。
 */
@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

    /** 服务商：qweather | open-meteo。 */
    private String provider = "qweather";

    /** 和风天气 API Key（https://dev.qweather.com 注册获取）。 */
    private String apiKey = "";

    /** 和风天气免费版 base url（付费版为 https://api.qweather.com）。 */
    private String qweatherBaseUrl = "https://devapi.qweather.com";

    /** 未识别出城市时的默认城市。 */
    private String defaultCity = "北京";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getQweatherBaseUrl() {
        return qweatherBaseUrl;
    }

    public void setQweatherBaseUrl(String qweatherBaseUrl) {
        this.qweatherBaseUrl = qweatherBaseUrl;
    }

    public String getDefaultCity() {
        return defaultCity;
    }

    public void setDefaultCity(String defaultCity) {
        this.defaultCity = defaultCity;
    }
}

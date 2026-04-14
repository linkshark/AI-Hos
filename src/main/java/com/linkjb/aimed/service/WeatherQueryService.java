package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WeatherQueryService {

    private static final Map<String, Location> COMMON_LOCATIONS = commonLocations();

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String geocodingBaseUrl;
    private final String forecastBaseUrl;
    private final String wttrBaseUrl;
    private final int timeoutMs;

    public WeatherQueryService(ObjectMapper objectMapper,
                               @Value("${app.weather.geocoding-base-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodingBaseUrl,
                               @Value("${app.weather.forecast-base-url:https://api.open-meteo.com/v1/forecast}") String forecastBaseUrl,
                               @Value("${app.weather.wttr-base-url:https://wttr.in}") String wttrBaseUrl,
                               @Value("${app.weather.timeout-ms:8000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.geocodingBaseUrl = geocodingBaseUrl;
        this.forecastBaseUrl = forecastBaseUrl;
        this.wttrBaseUrl = wttrBaseUrl;
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    public String queryWeather(String city) {
        String normalizedCity = normalizeCity(city);
        if (!StringUtils.hasText(normalizedCity)) {
            return "status=MISSING_CITY\nmessage=请提供要查询的城市，例如：杭州。";
        }
        try {
            Location location = resolveLocation(normalizedCity);
            JsonNode forecast = fetchForecast(location);
            return formatWeather(location, forecast);
        } catch (Exception exception) {
            try {
                return formatWttrWeather(normalizedCity, fetchWttrForecast(normalizedCity));
            } catch (Exception fallbackException) {
                return "status=ERROR\ncity=" + normalizedCity
                        + "\nmessage=天气查询失败：" + exception.getMessage()
                        + "；备用天气源也失败：" + fallbackException.getMessage();
            }
        }
    }

    private Location resolveLocation(String city) throws IOException, InterruptedException {
        Location common = COMMON_LOCATIONS.get(city);
        if (common != null) {
            return common;
        }
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        URI uri = URI.create(geocodingBaseUrl + "?name=" + encodedCity + "&count=1&language=zh&format=json");
        JsonNode root = getJson(uri);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            throw new IllegalArgumentException("未找到城市坐标");
        }
        JsonNode first = results.get(0);
        return new Location(
                text(first, "name", city),
                text(first, "country", ""),
                first.path("latitude").asDouble(),
                first.path("longitude").asDouble(),
                text(first, "timezone", "auto")
        );
    }

    private JsonNode fetchForecast(Location location) throws IOException, InterruptedException {
        String uri = forecastBaseUrl
                + "?latitude=" + location.latitude()
                + "&longitude=" + location.longitude()
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&forecast_days=3"
                + "&timezone=" + URLEncoder.encode(location.timezone(), StandardCharsets.UTF_8);
        return getJson(URI.create(uri));
    }

    private JsonNode fetchWttrForecast(String city) throws IOException, InterruptedException {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        return getJson(URI.create(wttrBaseUrl + "/" + encodedCity + "?format=j1"));
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("天气服务返回 HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String formatWeather(Location location, JsonNode forecast) {
        JsonNode current = forecast.get("current");
        JsonNode currentUnits = forecast.get("current_units");
        JsonNode daily = forecast.get("daily");
        if (current == null || daily == null) {
            throw new IllegalStateException("天气服务返回数据不完整");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("status=SUCCESS\n");
        builder.append("city=").append(location.name()).append("\n");
        builder.append("country=").append(location.country()).append("\n");
        builder.append("time=").append(text(current, "time", "")).append("\n");
        builder.append("weather=").append(weatherCodeLabel(current.path("weather_code").asInt(-1))).append("\n");
        builder.append("temperature=").append(number(current, "temperature_2m")).append(unit(currentUnits, "temperature_2m")).append("\n");
        builder.append("apparentTemperature=").append(number(current, "apparent_temperature")).append(unit(currentUnits, "apparent_temperature")).append("\n");
        builder.append("humidity=").append(number(current, "relative_humidity_2m")).append(unit(currentUnits, "relative_humidity_2m")).append("\n");
        builder.append("windSpeed=").append(number(current, "wind_speed_10m")).append(unit(currentUnits, "wind_speed_10m")).append("\n");
        builder.append("precipitation=").append(number(current, "precipitation")).append(unit(currentUnits, "precipitation")).append("\n");
        builder.append("dailyForecast=").append(formatDailyForecast(daily));
        return builder.toString();
    }

    private String formatWttrWeather(String requestedCity, JsonNode root) {
        JsonNode current = first(root.get("current_condition"));
        JsonNode nearestArea = first(root.get("nearest_area"));
        JsonNode weather = root.get("weather");
        if (current == null) {
            throw new IllegalStateException("备用天气源返回数据不完整");
        }
        String city = text(first(nearestArea == null ? null : nearestArea.get("areaName")), "value", requestedCity);
        String country = text(first(nearestArea == null ? null : nearestArea.get("country")), "value", "");
        StringBuilder builder = new StringBuilder();
        builder.append("status=SUCCESS\n");
        builder.append("source=wttr.in\n");
        builder.append("city=").append(city).append("\n");
        builder.append("country=").append(country).append("\n");
        builder.append("time=").append(text(current, "localObsDateTime", "")).append("\n");
        builder.append("weather=").append(text(first(current.get("weatherDesc")), "value", "未知")).append("\n");
        builder.append("temperature=").append(text(current, "temp_C", "-")).append("℃\n");
        builder.append("apparentTemperature=").append(text(current, "FeelsLikeC", "-")).append("℃\n");
        builder.append("humidity=").append(text(current, "humidity", "-")).append("%\n");
        builder.append("windSpeed=").append(text(current, "windspeedKmph", "-")).append("km/h\n");
        builder.append("precipitation=").append(text(current, "precipMM", "-")).append("mm\n");
        builder.append("dailyForecast=").append(formatWttrDailyForecast(weather));
        return builder.toString();
    }

    private String formatWttrDailyForecast(JsonNode weather) {
        if (weather == null || !weather.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int size = Math.min(weather.size(), 3);
        for (int index = 0; index < size; index++) {
            JsonNode day = weather.get(index);
            if (index > 0) {
                builder.append("；");
            }
            builder.append(text(day, "date", ""))
                    .append(" ")
                    .append(text(day, "mintempC", "-")).append("~").append(text(day, "maxtempC", "-")).append("℃")
                    .append(" 日照").append(text(day, "sunHour", "-")).append("h");
        }
        return builder.toString();
    }

    private String formatDailyForecast(JsonNode daily) {
        JsonNode times = daily.get("time");
        JsonNode weatherCodes = daily.get("weather_code");
        JsonNode maxTemps = daily.get("temperature_2m_max");
        JsonNode minTemps = daily.get("temperature_2m_min");
        JsonNode rainProbability = daily.get("precipitation_probability_max");
        if (times == null || !times.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < times.size(); index++) {
            if (index > 0) {
                builder.append("；");
            }
            builder.append(times.get(index).asText())
                    .append(" ")
                    .append(weatherCodeLabel(arrayInt(weatherCodes, index)))
                    .append(" ")
                    .append(arrayText(minTemps, index)).append("~").append(arrayText(maxTemps, index)).append("℃")
                    .append(" 降水概率").append(arrayText(rainProbability, index)).append("%");
        }
        return builder.toString();
    }

    private static String normalizeCity(String city) {
        if (!StringUtils.hasText(city)) {
            return "";
        }
        String normalized = city.trim();
        normalized = normalized.replaceAll("(市|天气|今天|明天|现在|当前)$", "");
        return normalized.trim();
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }

    private static JsonNode first(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.get(0) : null;
    }

    private static String number(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? String.format(Locale.ROOT, "%.1f", node.get(field).asDouble()) : "-";
    }

    private static String unit(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static int arrayInt(JsonNode node, int index) {
        return node != null && node.isArray() && index < node.size() ? node.get(index).asInt(-1) : -1;
    }

    private static String arrayText(JsonNode node, int index) {
        return node != null && node.isArray() && index < node.size() ? node.get(index).asText("-") : "-";
    }

    private static String weatherCodeLabel(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "少云";
            case 3 -> "多云";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67 -> "雨";
            case 71, 73, 75, 77 -> "雪";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95, 96, 99 -> "雷暴";
            default -> "未知";
        };
    }

    private static Map<String, Location> commonLocations() {
        Map<String, Location> locations = new LinkedHashMap<>();
        locations.put("杭州", new Location("杭州", "中国", 30.2936, 120.1614, "Asia/Shanghai"));
        locations.put("北京", new Location("北京", "中国", 39.9075, 116.3972, "Asia/Shanghai"));
        locations.put("上海", new Location("上海", "中国", 31.2222, 121.4581, "Asia/Shanghai"));
        locations.put("广州", new Location("广州", "中国", 23.1167, 113.25, "Asia/Shanghai"));
        locations.put("深圳", new Location("深圳", "中国", 22.5455, 114.0683, "Asia/Shanghai"));
        locations.put("南京", new Location("南京", "中国", 32.0617, 118.7778, "Asia/Shanghai"));
        locations.put("苏州", new Location("苏州", "中国", 31.3041, 120.5954, "Asia/Shanghai"));
        return locations;
    }

    private record Location(String name, String country, double latitude, double longitude, String timezone) {
    }
}

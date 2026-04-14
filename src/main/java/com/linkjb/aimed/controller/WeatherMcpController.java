package com.linkjb.aimed.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.service.WeatherQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/aimed/mcp/weather", "/api/aimed/mcp/weather"})
public class WeatherMcpController {

    private final WeatherQueryService weatherQueryService;
    private final ObjectMapper objectMapper;

    public WeatherMcpController(WeatherQueryService weatherQueryService, ObjectMapper objectMapper) {
        this.weatherQueryService = weatherQueryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(@RequestBody JsonNode request) {
        String method = text(request, "method");
        if (!StringUtils.hasText(method)) {
            return ResponseEntity.badRequest().body(error(id(request), -32600, "缺少 MCP method"));
        }
        if ("notifications/initialized".equals(method)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of());
        }
        return switch (method) {
            case "initialize" -> ResponseEntity.ok(result(id(request), initializeResult()));
            case "tools/list" -> ResponseEntity.ok(result(id(request), toolsListResult()));
            case "tools/call" -> ResponseEntity.ok(result(id(request), callTool(request)));
            default -> ResponseEntity.ok(error(id(request), -32601, "未知 MCP method: " + method));
        };
    }

    private Map<String, Object> initializeResult() {
        return Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "aihos-weather-mcp", "version", "1.0.0")
        );
    }

    private Map<String, Object> toolsListResult() {
        return Map.of("tools", List.of(Map.of(
                "name", "query_weather",
                "description", "查询指定城市当前天气和未来三天天气预报。",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "城市名称，例如：杭州、上海、北京")
                        ),
                        "required", List.of("city")
                )
        )));
    }

    private Map<String, Object> callTool(JsonNode request) {
        JsonNode params = request.get("params");
        String toolName = text(params, "name");
        if (!"query_weather".equals(toolName)) {
            return toolContent("不支持的天气 MCP 工具：" + toolName, true);
        }
        JsonNode arguments = params == null ? null : params.get("arguments");
        String city = text(arguments, "city");
        String text = weatherQueryService.queryWeather(city);
        return toolContent(text, text.startsWith("status=ERROR") || text.startsWith("status=MISSING_CITY"));
    }

    private Map<String, Object> toolContent(String text, boolean error) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "isError", error
        );
    }

    private Map<String, Object> result(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private Object id(JsonNode request) {
        JsonNode id = request == null ? null : request.get("id");
        return id == null || id.isNull() ? null : objectMapper.convertValue(id, Object.class);
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : "";
    }
}

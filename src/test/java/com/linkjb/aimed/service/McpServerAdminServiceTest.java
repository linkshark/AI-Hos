package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.mcp.McpServerToolItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpServerAdminServiceTest {

    private final McpServerAdminService service = new McpServerAdminService(null, new ObjectMapper(), null);

    @Test
    void shouldExtractToolNameFromAgentDescription() {
        String normalized = service.normalizeRequestedToolName("server=内置天气 MCP tool=query_weather description=查询天气");

        assertEquals("query_weather", normalized);
    }

    @Test
    void shouldMatchCamelCaseAndSnakeCaseToolName() {
        McpServerToolItem matched = service.resolveRequestedTool("queryWeather", List.of(
                new McpServerToolItem("query_weather", "查询天气", "{}"),
                new McpServerToolItem("list_forecast", "查询预报", "{}")
        ));

        assertEquals("query_weather", matched.name());
    }

    @Test
    void shouldMatchChineseWeatherIntentToWeatherTool() {
        McpServerToolItem matched = service.resolveRequestedTool("查询杭州天气", List.of(
                new McpServerToolItem("query_weather", "查询指定城市实时天气", "{}"),
                new McpServerToolItem("list_news", "查询新闻", "{}")
        ));

        assertEquals("query_weather", matched.name());
    }

    @Test
    void shouldFallbackToSingleTool() {
        McpServerToolItem matched = service.resolveRequestedTool("天气", List.of(
                new McpServerToolItem("weather", "天气查询", "{}")
        ));

        assertEquals("weather", matched.name());
    }

    @Test
    void shouldReturnNullWhenMultipleToolsDoNotMatch() {
        McpServerToolItem matched = service.resolveRequestedTool("预约挂号", List.of(
                new McpServerToolItem("query_weather", "查询天气", "{}"),
                new McpServerToolItem("list_news", "查询新闻", "{}")
        ));

        assertNull(matched);
    }
}

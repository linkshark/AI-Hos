package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.mcp.McpServerToolItem;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Agent 请求工具名与 MCP 实际工具名之间的容错匹配器。
 *
 * LLM 输出的工具名可能出现大小写、snake/camel、标点或中文意图描述差异，
 * 这层负责把这些松散表达收敛成一个稳定的工具选择结果。
 */
final class McpToolMatcher {

    String normalizeRequestedToolName(String toolName) {
        String normalized = toolName == null ? "" : toolName.trim();
        int toolMarkerIndex = normalized.indexOf("tool=");
        if (toolMarkerIndex >= 0) {
            String suffix = normalized.substring(toolMarkerIndex + "tool=".length()).trim();
            int endIndex = suffix.indexOf(' ');
            normalized = endIndex > 0 ? suffix.substring(0, endIndex) : suffix;
        }
        normalized = normalized.replace('。', '.').replace('．', '.');
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.trim();
    }

    McpServerToolItem resolveRequestedTool(String requestedTool, List<McpServerToolItem> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        String normalizedRequested = normalizeToolComparable(requestedTool);
        for (McpServerToolItem tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.name())) {
                continue;
            }
            if (requestedTool.equals(tool.name()) || requestedTool.equalsIgnoreCase(tool.name())) {
                return tool;
            }
            String normalizedToolName = normalizeToolComparable(tool.name());
            if (StringUtils.hasText(normalizedRequested)
                    && (normalizedRequested.equals(normalizedToolName)
                    || normalizedRequested.contains(normalizedToolName)
                    || normalizedToolName.contains(normalizedRequested))) {
                return tool;
            }
        }
        if (looksLikeWeatherToolRequest(requestedTool)) {
            for (McpServerToolItem tool : tools) {
                String combined = normalizeToolComparable(tool.name() + " " + nullToEmpty(tool.description()));
                if (combined.contains("weather") || combined.contains("天气")) {
                    return tool;
                }
            }
        }
        return tools.size() == 1 ? tools.get(0) : null;
    }

    private String normalizeToolComparable(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-./:：\"'`，,。()（）\\[\\]【】]+", "");
    }

    private boolean looksLikeWeatherToolRequest(String requestedTool) {
        String normalized = normalizeToolComparable(requestedTool);
        return normalized.contains("weather") || normalized.contains("天气") || normalized.contains("temperature");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

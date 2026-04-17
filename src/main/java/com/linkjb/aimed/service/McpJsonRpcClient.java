package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.vo.mcp.McpServerHeaderItem;
import com.linkjb.aimed.entity.vo.mcp.McpServerItem;
import com.linkjb.aimed.entity.dto.response.mcp.McpServerTestResponse;
import com.linkjb.aimed.entity.vo.mcp.McpServerToolItem;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP JSON-RPC 轻量客户端。
 *
 * 管理台测试和 Agent 运行时都复用这层协议细节，避免 `initialize / tools/list / tools/call`
 * 在多个入口各写一遍，导致一边修 transport 细节、另一边行为漂移。
 */
final class McpJsonRpcClient {

    private final ObjectMapper objectMapper;

    McpJsonRpcClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    McpServerTestResponse testServer(McpServerItem item, LocalDateTime checkedAt) throws Exception {
        if (!McpServerAdminService.TRANSPORT_STREAMABLE_HTTP.equals(item.transportType())) {
            throw new IllegalArgumentException("当前仅支持 Streamable HTTP 类型的 MCP 服务");
        }
        URI uri = URI.create(item.baseUrl());
        HttpClient client = buildClient(item);
        // 管理台测试和 Agent 运行时都遵循 MCP 的最小闭环：先 initialize，再发送 initialized，最后读取 tools/list。
        JsonNode initializeResult = sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(),
                "initialize",
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "aihos-admin", "version", "1.0.0")
                ));


        sendJsonRpcNotification(client, uri, item.headers(), item.connectTimeoutMs(),
                "notifications/initialized", Map.of());

        JsonNode toolsListResult = sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(),
                "tools/list", Map.of());

        JsonNode serverInfo = initializeResult == null ? null : initializeResult.get("serverInfo");
        String serverName = textValue(serverInfo, "name");
        String serverVersion = textValue(serverInfo, "version");
        List<McpServerToolItem> tools = extractTools(toolsListResult);
        String message = tools.isEmpty()
                ? "连接成功，但当前服务没有返回可用工具"
                : "连接成功，已读取到 " + tools.size() + " 个工具";
        return new McpServerTestResponse(true, item.transportType(), serverName, serverVersion, tools.size(), tools, message, checkedAt);
    }

    List<McpServerToolItem> fetchTools(McpServerItem item) throws Exception {
        HttpClient client = buildClient(item);
        URI uri = URI.create(item.baseUrl());
        initializeMcpSession(client, uri, item);
        JsonNode toolsListResult = sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(), "tools/list", Map.of());
        return extractTools(toolsListResult);
    }

    JsonNode callTool(McpServerItem item, String toolName, Map<String, Object> arguments) throws Exception {
        HttpClient client = buildClient(item);
        URI uri = URI.create(item.baseUrl());
        initializeMcpSession(client, uri, item);
        return sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(),
                "tools/call",
                Map.of(
                        "name", toolName,
                        "arguments", arguments == null ? Map.of() : arguments
                ));
    }

    Map<String, Object> parseToolArguments(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(argumentsJson);
            if (node == null || node.isNull()) {
                return Map.of();
            }
            if (!node.isObject()) {
                throw new IllegalArgumentException("MCP 工具参数必须是 JSON object");
            }
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("MCP 工具参数不是合法 JSON：" + exception.getMessage(), exception);
        }
    }

    String formatToolCallResult(McpServerItem server, String toolName, JsonNode result) {
        if (result == null) {
            return "status=SUCCESS\nserver=" + server.name() + "\ntool=" + toolName + "\nmessage=工具调用成功但无返回内容";
        }
        JsonNode content = result.get("content");
        if (content != null && content.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode item : content) {
                String text = textValue(item, "text");
                if (StringUtils.hasText(text)) {
                    texts.add(text);
                }
            }
            if (!texts.isEmpty()) {
                return String.join("\n", texts);
            }
        }
        return result.toString();
    }

    private HttpClient buildClient(McpServerItem item) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(item.connectTimeoutMs()))
                .build();
    }

    private void initializeMcpSession(HttpClient client, URI uri, McpServerItem item) throws Exception {
        sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(),
                "initialize",
                Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "aihos-agent-runtime", "version", "1.0.0")
                ));
        sendJsonRpcNotification(client, uri, item.headers(), item.connectTimeoutMs(),
                "notifications/initialized", Map.of());
    }

    private JsonNode sendJsonRpcRequest(HttpClient client,
                                        URI uri,
                                        List<McpServerHeaderItem> headers,
                                        int timeoutMs,
                                        String method,
                                        Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", UUID.randomUUID().toString());
        payload.put("method", method);
        payload.put("params", params);
        HttpRequest request = buildBaseRequest(uri, headers, timeoutMs)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MCP 服务返回 HTTP " + response.statusCode());
        }
        JsonNode root = parseJsonRpcResponse(response.body());
        if (root.hasNonNull("error")) {
            JsonNode errorNode = root.get("error");
            throw new IllegalStateException(textValue(errorNode, "message"));
        }
        return root.get("result");
    }

    private void sendJsonRpcNotification(HttpClient client,
                                         URI uri,
                                         List<McpServerHeaderItem> headers,
                                         int timeoutMs,
                                         String method,
                                         Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("params", params);
        HttpRequest request = buildBaseRequest(uri, headers, timeoutMs)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private HttpRequest.Builder buildBaseRequest(URI uri,
                                                 List<McpServerHeaderItem> headers,
                                                 int timeoutMs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        for (McpServerHeaderItem header : headers) {
            if (header == null || !StringUtils.hasText(header.key()) || header.value() == null) {
                continue;
            }
            builder.header(header.key().trim(), header.value());
        }
        return builder;
    }

    private List<McpServerToolItem> extractTools(JsonNode toolsListResult) {
        JsonNode toolsNode = toolsListResult == null ? null : toolsListResult.get("tools");
        if (toolsNode == null || !toolsNode.isArray()) {
            return List.of();
        }
        List<McpServerToolItem> tools = new ArrayList<>();
        for (JsonNode tool : toolsNode) {
            tools.add(new McpServerToolItem(
                    textValue(tool, "name"),
                    textValue(tool, "description"),
                    tool.hasNonNull("inputSchema") ? tool.get("inputSchema").toString() : null
            ));
        }
        return tools;
    }

    private JsonNode parseJsonRpcResponse(String body) throws Exception {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("MCP 服务返回空响应");
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return objectMapper.readTree(trimmed);
        }
        for (String line : trimmed.split("\\R")) {
            String normalized = line.trim();
            if (!normalized.startsWith("data:")) {
                continue;
            }
            String data = normalized.substring("data:".length()).trim();
            if (StringUtils.hasText(data) && !"[DONE]".equalsIgnoreCase(data)) {
                return objectMapper.readTree(data);
            }
        }
        throw new IllegalStateException("MCP 服务返回内容不是 JSON-RPC 响应");
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }
}

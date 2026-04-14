package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.mcp.McpServerHeaderItem;
import com.linkjb.aimed.bean.mcp.McpServerItem;
import com.linkjb.aimed.bean.mcp.McpServerRequest;
import com.linkjb.aimed.bean.mcp.McpServerTestResponse;
import com.linkjb.aimed.bean.mcp.McpServerToolItem;
import com.linkjb.aimed.entity.McpServerConfig;
import com.linkjb.aimed.mapper.McpServerConfigMapper;
import com.linkjb.aimed.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
 * MCP 服务管理。
 *
 * 这层先实现管理员真正需要的最小闭环：
 * - 保存远端 MCP 服务配置
 * - 主动测试 initialize / notifications/initialized / tools/list
 * - 把最近一次检查结果和工具摘要留在后台里
 *
 * 当前只支持最实用的 Streamable HTTP 远端服务，不把 stdio / SSE 一次性全做进来，
 * 这样可以先把系统复杂度控制住，等后面真要接更多 MCP 服务时再扩展。
 */
@Service
public class McpServerAdminService {

    public static final String TRANSPORT_STREAMABLE_HTTP = "STREAMABLE_HTTP";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";
    private static final TypeReference<List<McpServerHeaderItem>> HEADER_LIST_TYPE = new TypeReference<>() {
    };

    private final McpServerConfigMapper mcpServerConfigMapper;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public McpServerAdminService(McpServerConfigMapper mcpServerConfigMapper,
                                 ObjectMapper objectMapper,
                                 AuditLogService auditLogService) {
        this.mcpServerConfigMapper = mcpServerConfigMapper;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    public List<McpServerItem> listServers() {
        return mcpServerConfigMapper.selectList(new LambdaQueryWrapper<McpServerConfig>()
                        .orderByDesc(McpServerConfig::getEnabled)
                        .orderByDesc(McpServerConfig::getUpdatedAt)
                        .orderByDesc(McpServerConfig::getId))
                .stream()
                .map(this::toItem)
                .toList();
    }

    public McpServerItem createServer(McpServerRequest request, AuthenticatedUser currentUser) {
        ValidatedRequest validated = validateRequest(request);
        McpServerConfig entity = new McpServerConfig();
        entity.setName(validated.name());
        entity.setTransportType(validated.transportType());
        entity.setBaseUrl(validated.baseUrl());
        entity.setDescription(validated.description());
        entity.setEnabled(validated.enabled());
        entity.setConnectTimeoutMs(validated.connectTimeoutMs());
        entity.setRequestHeadersJson(validated.headersJson());
        mcpServerConfigMapper.insert(entity);
        Long id = entity.getId();
        if (id == null) {
            throw new IllegalStateException("创建 MCP 服务失败");
        }
        auditLogService.recordMcpAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_MCP_SERVER_CREATE, String.valueOf(id), "创建 MCP 服务 " + validated.name());
        return getRequiredServer(id);
    }

    public McpServerItem updateServer(Long id, McpServerRequest request, AuthenticatedUser currentUser) {
        getRequiredServer(id);
        ValidatedRequest validated = validateRequest(request);
        mcpServerConfigMapper.update(null, new LambdaUpdateWrapper<McpServerConfig>()
                .eq(McpServerConfig::getId, id)
                .set(McpServerConfig::getName, validated.name())
                .set(McpServerConfig::getTransportType, validated.transportType())
                .set(McpServerConfig::getBaseUrl, validated.baseUrl())
                .set(McpServerConfig::getDescription, validated.description())
                .set(McpServerConfig::getEnabled, validated.enabled())
                .set(McpServerConfig::getConnectTimeoutMs, validated.connectTimeoutMs())
                .set(McpServerConfig::getRequestHeadersJson, validated.headersJson()));
        auditLogService.recordMcpAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_MCP_SERVER_UPDATE, String.valueOf(id), "更新 MCP 服务 " + validated.name());
        return getRequiredServer(id);
    }

    public void deleteServer(Long id, AuthenticatedUser currentUser) {
        McpServerItem item = getRequiredServer(id);
        mcpServerConfigMapper.deleteById(id);
        auditLogService.recordMcpAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_MCP_SERVER_DELETE, String.valueOf(id), "删除 MCP 服务 " + item.name());
    }

    public McpServerTestResponse testServer(Long id, AuthenticatedUser currentUser) {
        McpServerItem item = getRequiredServer(id);
        LocalDateTime checkedAt = LocalDateTime.now();
        try {
            McpServerTestResponse response = doTest(item, checkedAt);
            persistTestStatus(id, STATUS_OK, null, response.serverName(), response.serverVersion(), response.toolsCount(), checkedAt);
            auditLogService.recordMcpAction(currentUser.userId(), currentUser.role(),
                    AuditLogService.ACTION_MCP_SERVER_TEST, String.valueOf(id), "测试 MCP 服务 " + item.name() + " 成功");
            return response;
        } catch (Exception exception) {
            String message = resolveErrorMessage(exception);
            persistTestStatus(id, STATUS_FAILED, message, null, null, null, checkedAt);
            auditLogService.recordMcpAction(currentUser.userId(), currentUser.role(),
                    AuditLogService.ACTION_MCP_SERVER_TEST, String.valueOf(id), "测试 MCP 服务 " + item.name() + " 失败：" + message);
            return new McpServerTestResponse(false, item.transportType(), null, null, 0, List.of(), message, checkedAt);
        }
    }

    public String describeEnabledToolsForAgent() {
        List<McpServerItem> servers = listServers().stream()
                .filter(McpServerItem::enabled)
                .filter(item -> TRANSPORT_STREAMABLE_HTTP.equals(item.transportType()))
                .toList();
        if (servers.isEmpty()) {
            return "当前没有启用的 MCP 服务。";
        }
        List<String> lines = new ArrayList<>();
        for (McpServerItem server : servers) {
            try {
                List<McpServerToolItem> tools = fetchTools(server);
                if (tools.isEmpty()) {
                    lines.add("server=" + server.name() + " tools=EMPTY");
                    continue;
                }
                for (McpServerToolItem tool : tools) {
                    lines.add("server=" + server.name()
                            + " tool=" + tool.name()
                            + " description=" + nullToEmpty(tool.description())
                            + " inputSchema=" + nullToEmpty(tool.inputSchema()));
                }
            } catch (Exception exception) {
                lines.add("server=" + server.name() + " status=FAILED message=" + resolveErrorMessage(exception));
            }
        }
        return String.join("\n", lines);
    }

    public String callEnabledToolForAgent(String toolName, String argumentsJson) {
        if (!StringUtils.hasText(toolName)) {
            return "status=MISSING_TOOL\nmessage=请提供 MCP 工具名称";
        }
        Map<String, Object> arguments = parseToolArguments(argumentsJson);
        String requestedTool = normalizeRequestedToolName(toolName);
        String requestedServer = null;
        int separatorIndex = requestedTool.indexOf('.');
        if (separatorIndex > 0 && separatorIndex < requestedTool.length() - 1) {
            requestedServer = requestedTool.substring(0, separatorIndex).trim();
            requestedTool = requestedTool.substring(separatorIndex + 1).trim();
        }
        String effectiveServer = requestedServer;
        String effectiveRequestedTool = requestedTool;
        List<McpServerItem> servers = listServers().stream()
                .filter(McpServerItem::enabled)
                .filter(item -> TRANSPORT_STREAMABLE_HTTP.equals(item.transportType()))
                .filter(item -> !StringUtils.hasText(effectiveServer) || effectiveServer.equalsIgnoreCase(item.name()))
                .toList();
        for (McpServerItem server : servers) {
            try {
                List<McpServerToolItem> tools = fetchTools(server);
                McpServerToolItem matchedTool = resolveRequestedTool(effectiveRequestedTool, tools);
                if (matchedTool == null) {
                    continue;
                }
                JsonNode result = callTool(server, matchedTool.name(), arguments);
                recordAgentMcpToolCall(server, matchedTool.name(), "SUCCESS", null);
                return formatToolCallResult(server, matchedTool.name(), result);
            } catch (Exception exception) {
                recordAgentMcpToolCall(server, effectiveRequestedTool, "ERROR", resolveErrorMessage(exception));
                return "status=ERROR\nserver=" + server.name() + "\ntool=" + effectiveRequestedTool + "\nmessage=" + resolveErrorMessage(exception);
            }
        }
        return "status=NOT_FOUND\ntool=" + effectiveRequestedTool
                + "\nmessage=没有找到已启用且匹配的 MCP 工具"
                + "\navailableTools=\n" + describeEnabledToolsForAgent();
    }

    private void recordAgentMcpToolCall(McpServerItem server, String toolName, String status, String error) {
        if (auditLogService == null || server == null) {
            return;
        }
        String summary = "Agent 调用 MCP 工具 " + server.name() + "." + toolName + " 状态 " + status;
        if (StringUtils.hasText(error)) {
            summary += "：" + error;
        }
        auditLogService.recordMcpAction(null, "AGENT", AuditLogService.ACTION_MCP_TOOL_CALL,
                String.valueOf(server.id()), summary);
    }

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
                .toLowerCase()
                .replaceAll("[\\s_\\-./:：\"'`，,。()（）\\[\\]【】]+", "");
    }

    private boolean looksLikeWeatherToolRequest(String requestedTool) {
        String normalized = normalizeToolComparable(requestedTool);
        return normalized.contains("weather") || normalized.contains("天气") || normalized.contains("temperature");
    }

    private McpServerTestResponse doTest(McpServerItem item, LocalDateTime checkedAt) throws Exception {
        if (!TRANSPORT_STREAMABLE_HTTP.equals(item.transportType())) {
            throw new IllegalArgumentException("当前仅支持 Streamable HTTP 类型的 MCP 服务");
        }
        URI uri = URI.create(item.baseUrl());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(item.connectTimeoutMs()))
                .build();

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

    private List<McpServerToolItem> fetchTools(McpServerItem item) throws Exception {
        HttpClient client = buildClient(item);
        URI uri = URI.create(item.baseUrl());
        initializeMcpSession(client, uri, item);
        JsonNode toolsListResult = sendJsonRpcRequest(client, uri, item.headers(), item.connectTimeoutMs(), "tools/list", Map.of());
        return extractTools(toolsListResult);
    }

    private JsonNode callTool(McpServerItem item, String toolName, Map<String, Object> arguments) throws Exception {
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

    private Map<String, Object> parseToolArguments(String argumentsJson) {
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

    private String formatToolCallResult(McpServerItem server, String toolName, JsonNode result) {
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

    private void persistTestStatus(Long id,
                                   String status,
                                   String error,
                                   String serverName,
                                   String serverVersion,
                                   Integer toolsCount,
                                   LocalDateTime checkedAt) {
        mcpServerConfigMapper.update(null, new LambdaUpdateWrapper<McpServerConfig>()
                .eq(McpServerConfig::getId, id)
                .set(McpServerConfig::getLastStatus, status)
                .set(McpServerConfig::getLastError, truncate(error, 500))
                .set(McpServerConfig::getServerName, serverName)
                .set(McpServerConfig::getServerVersion, serverVersion)
                .set(McpServerConfig::getToolsCount, toolsCount)
                .set(McpServerConfig::getLastCheckedAt, checkedAt));
    }

    private McpServerItem getRequiredServer(Long id) {
        McpServerConfig entity = mcpServerConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("MCP 服务不存在");
        }
        return toItem(entity);
    }

    private McpServerItem toItem(McpServerConfig entity) {
        return new McpServerItem(
                entity.getId(),
                entity.getName(),
                entity.getTransportType(),
                entity.getBaseUrl(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getConnectTimeoutMs() == null ? 5000 : entity.getConnectTimeoutMs(),
                parseHeaders(entity.getRequestHeadersJson()),
                entity.getLastStatus(),
                entity.getLastError(),
                entity.getServerName(),
                entity.getServerVersion(),
                entity.getToolsCount(),
                entity.getLastCheckedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<McpServerHeaderItem> parseHeaders(String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(headersJson, HEADER_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private ValidatedRequest validateRequest(McpServerRequest request) {
        String transportType = StringUtils.hasText(request.transportType())
                ? request.transportType().trim().toUpperCase()
                : TRANSPORT_STREAMABLE_HTTP;
        if (!TRANSPORT_STREAMABLE_HTTP.equals(transportType)) {
            throw new IllegalArgumentException("当前仅支持 Streamable HTTP 类型");
        }
        String baseUrl = request.baseUrl().trim();
        URI uri = URI.create(baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("MCP 服务地址必须是 http 或 https");
        }
        int timeoutMs = request.connectTimeoutMs() == null ? 5000 : request.connectTimeoutMs();
        String headersJson = serializeHeaders(request.headers());
        return new ValidatedRequest(
                request.name().trim(),
                transportType,
                baseUrl,
                StringUtils.hasText(request.description()) ? request.description().trim() : null,
                request.enabled() == null || request.enabled(),
                timeoutMs,
                headersJson
        );
    }

    private String serializeHeaders(List<McpServerHeaderItem> headers) {
        List<McpServerHeaderItem> normalized = headers == null ? List.of() : headers.stream()
                .filter(item -> item != null && StringUtils.hasText(item.key()) && item.value() != null)
                .map(item -> new McpServerHeaderItem(item.key().trim(), item.value()))
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new IllegalArgumentException("请求头格式无法序列化");
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof ResponseStatusException responseStatusException && StringUtils.hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason();
        }
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ValidatedRequest(
            String name,
            String transportType,
            String baseUrl,
            String description,
            boolean enabled,
            int connectTimeoutMs,
            String headersJson
    ) {
    }
}

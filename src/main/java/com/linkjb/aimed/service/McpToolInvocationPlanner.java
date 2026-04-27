package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 动态 MCP 工具调用规划器。
 *
 * 聊天模型直接 tool-call 时，尤其是本地模型，容易卡在“先列工具、再挑工具、再构造参数”这几步。
 * 这里把它收敛成一个小模型规划任务：只根据运行时工具清单输出结构化 toolName + argumentsJson，
 * 真正的工具匹配和调用仍走后端统一的 MCP matcher，不把任何具体工具硬编码进业务链路。
 */
@Service
public class McpToolInvocationPlanner {

    public record ToolInvocationPlan(String toolName,
                                     String argumentsJson,
                                     Double confidence,
                                     String reason) {
    }

    private static final Logger log = LoggerFactory.getLogger(McpToolInvocationPlanner.class);
    private static final String SYSTEM_PROMPT = """
            你是动态 MCP 工具调用规划器。
            任务：根据用户问题和可用 MCP 工具清单，选择最合适的一个工具，并生成 JSON 参数。
            约束：
            1. 只能选择工具清单里出现的 tool 名称，不能编造工具。
            2. argumentsJson 必须是 JSON object 字符串，字段必须尽量符合 inputSchema。
            3. 如果没有合适工具，toolName 返回空字符串，argumentsJson 返回 "{}"。
            4. 不要输出解释、Markdown 或代码块，只能输出一个 JSON 对象：
               {"toolName":"...","argumentsJson":"{...}","confidence":0.0,"reason":"..."}
            """;

    private final ObjectMapper objectMapper;
    private final ChatModel localChatModel;
    private final ChatModel onlineFastChatModel;
    private final ChatModel onlineDeepChatModel;
    private final boolean enabled;
    private final String provider;

    public McpToolInvocationPlanner(ObjectMapper objectMapper,
                                    @Qualifier("localChatModel") ChatModel localChatModel,
                                    @Qualifier("onlineFastChatModel") ChatModel onlineFastChatModel,
                                    @Qualifier("onlineDeepChatModel") ChatModel onlineDeepChatModel,
                                    @Value("${app.mcp.tool-planner.enabled:true}") boolean enabled,
                                    @Value("${app.mcp.tool-planner.provider:QWEN_ONLINE_FAST}") String provider) {
        this.objectMapper = objectMapper;
        this.localChatModel = localChatModel;
        this.onlineFastChatModel = onlineFastChatModel;
        this.onlineDeepChatModel = onlineDeepChatModel;
        this.enabled = enabled;
        this.provider = provider == null ? "QWEN_ONLINE_FAST" : provider.trim().toUpperCase(Locale.ROOT);
    }

    public ToolInvocationPlan plan(String userQuery, String toolContext) {
        if (!enabled || !StringUtils.hasText(userQuery) || !StringUtils.hasText(toolContext)
                || toolContext.contains("当前没有启用的 MCP 服务")) {
            return null;
        }
        try {
            // 规划失败只影响“提前确定工具调用”，不影响后续 direct agent 继续基于动态工具清单自行调用工具。
            // 所以这里吞掉异常并返回 null，让上层自然降级。
            ChatResponse response = selectModel().chat(buildMessages(userQuery, toolContext));
            String text = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
            ToolInvocationPlan plan = parsePlan(text);
            return validatePlan(plan) ? plan : null;
        } catch (Exception exception) {
            log.warn("mcp.tool.plan.failed provider={} query={}", provider, userQuery, exception);
            return null;
        }
    }

    ToolInvocationPlan parsePlan(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return null;
        }
        try {
            String json = stripCodeFence(rawResponse.trim());
            JsonNode root = objectMapper.readTree(json);
            String toolName = text(root, "toolName");
            String argumentsJson = text(root, "argumentsJson");
            Double confidence = root.hasNonNull("confidence") ? root.path("confidence").asDouble() : null;
            return new ToolInvocationPlan(
                    toolName == null ? "" : toolName.trim(),
                    StringUtils.hasText(argumentsJson) ? argumentsJson.trim() : "{}",
                    confidence,
                    text(root, "reason")
            );
        } catch (Exception exception) {
            log.debug("mcp.tool.plan.parse.failed response={}", rawResponse, exception);
            return null;
        }
    }

    private boolean validatePlan(ToolInvocationPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.toolName())) {
            return false;
        }
        if (plan.confidence() == null || plan.confidence() < 0.45d) {
            return false;
        }
        try {
            JsonNode arguments = objectMapper.readTree(plan.argumentsJson());
            return arguments != null && arguments.isObject();
        } catch (Exception exception) {
            return false;
        }
    }

    private List<ChatMessage> buildMessages(String userQuery, String toolContext) {
        String userPrompt = """
                用户问题：
                %s

                可用 MCP 工具清单：
                %s
                """.formatted(userQuery, toolContext);
        return List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt));
    }

    private ChatModel selectModel() {
        return switch (provider) {
            case "LOCAL_OMLX", "LOCAL_OLLAMA" -> localChatModel;
            case "QWEN_ONLINE_DEEP" -> onlineDeepChatModel;
            default -> onlineFastChatModel;
        };
    }

    private String stripCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        return value.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}

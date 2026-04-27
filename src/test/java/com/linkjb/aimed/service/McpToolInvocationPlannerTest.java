package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpToolInvocationPlannerTest {

    @Test
    void shouldParseStructuredPlan() {
        McpToolInvocationPlanner planner = planner();

        McpToolInvocationPlanner.ToolInvocationPlan plan = planner.parsePlan("""
                {"toolName":"query_weather","argumentsJson":"{\\"city\\":\\"杭州\\"}","confidence":0.91,"reason":"天气查询"}
                """);

        assertEquals("query_weather", plan.toolName());
        assertEquals("{\"city\":\"杭州\"}", plan.argumentsJson());
        assertEquals(0.91d, plan.confidence());
    }

    @Test
    void shouldRejectInvalidPlanResponse() {
        McpToolInvocationPlanner planner = planner();

        assertNull(planner.parsePlan("不是 JSON"));
    }

    private McpToolInvocationPlanner planner() {
        ChatModel noopModel = new ChatModel() {
        };
        return new McpToolInvocationPlanner(
                new ObjectMapper(),
                noopModel,
                noopModel,
                noopModel,
                true,
                "QWEN_ONLINE_FAST"
        );
    }
}

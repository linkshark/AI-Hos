package com.linkjb.aimed.entity.dto.request.mcp;

import com.linkjb.aimed.entity.vo.mcp.McpServerHeaderItem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record McpServerRequest(
        @NotBlank(message = "服务名称不能为空")
        String name,
        @NotBlank(message = "传输类型不能为空")
        String transportType,
        @NotBlank(message = "服务地址不能为空")
        String baseUrl,
        String description,
        Boolean enabled,
        @Min(value = 1000, message = "超时时间不能小于 1000ms")
        @Max(value = 60000, message = "超时时间不能大于 60000ms")
        Integer connectTimeoutMs,
        List<McpServerHeaderItem> headers
) {
}

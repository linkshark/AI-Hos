package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.mcp.McpServerItem;
import com.linkjb.aimed.bean.mcp.McpServerRequest;
import com.linkjb.aimed.bean.mcp.McpServerTestResponse;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.McpServerAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Mcp")
@RestController
@RequestMapping({"/aimed/admin", "/api/aimed/admin"})
public class McpController {

    private final McpServerAdminService mcpServerAdminService;

    public McpController(McpServerAdminService mcpServerAdminService) {
        this.mcpServerAdminService = mcpServerAdminService;
    }
    @Operation(summary = "管理员查看 MCP 服务配置")
    @GetMapping("/mcp/servers")
    public List<McpServerItem> mcpServers() {
        return mcpServerAdminService.listServers();
    }

    @Operation(summary = "管理员创建 MCP 服务配置")
    @PostMapping("/mcp/servers")
    public McpServerItem createMcpServer(@Valid @RequestBody McpServerRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return mcpServerAdminService.createServer(request, currentUser);
    }

    @Operation(summary = "管理员更新 MCP 服务配置")
    @PutMapping("/mcp/servers/{id}")
    public McpServerItem updateMcpServer(@PathVariable("id") Long id,
                                         @Valid @RequestBody McpServerRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return mcpServerAdminService.updateServer(id, request, currentUser);
    }

    @Operation(summary = "管理员删除 MCP 服务配置")
    @DeleteMapping("/mcp/servers/{id}")
    public void deleteMcpServer(@PathVariable("id") Long id,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        mcpServerAdminService.deleteServer(id, currentUser);
    }

    @Operation(summary = "管理员测试 MCP 服务连接")
    @PostMapping("/mcp/servers/{id}/test")
    public McpServerTestResponse testMcpServer(@PathVariable("id") Long id,
                                               @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return mcpServerAdminService.testServer(id, currentUser);
    }
}

package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.AdminCreateDoctorRequest;
import com.linkjb.aimed.bean.AdminUpdateUserRoleRequest;
import com.linkjb.aimed.bean.AdminUpdateUserStatusRequest;
import com.linkjb.aimed.bean.AdminUserItem;
import com.linkjb.aimed.bean.AuditLogItem;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.AdminService;
import com.linkjb.aimed.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Admin")
@RestController
@RequestMapping("/aimed/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;

    public AdminController(AdminService adminService,
                           AuditLogService auditLogService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "管理员查看用户列表")
    @GetMapping("/users")
    public PagedResponse<AdminUserItem> users(@RequestParam(value = "page", defaultValue = "1") int page,
                                              @RequestParam(value = "size", defaultValue = "10") int size,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "role", required = false) String role,
                                              @RequestParam(value = "status", required = false) String status) {
        return adminService.listUsers(page, size, keyword, role, status);
    }

    @Operation(summary = "管理员创建医生账号")
    @PostMapping("/doctors")
    public AdminUserItem createDoctor(@Valid @RequestBody AdminCreateDoctorRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return adminService.createDoctor(request, currentUser);
    }

    @Operation(summary = "管理员调整用户角色")
    @PutMapping("/users/{userId}/role")
    public AdminUserItem updateUserRole(@PathVariable("userId") Long userId,
                                        @Valid @RequestBody AdminUpdateUserRoleRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return adminService.updateUserRole(userId, request, currentUser);
    }

    @Operation(summary = "管理员调整用户状态")
    @PutMapping("/users/{userId}/status")
    public AdminUserItem updateUserStatus(@PathVariable("userId") Long userId,
                                          @Valid @RequestBody AdminUpdateUserStatusRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return adminService.updateUserStatus(userId, request, currentUser);
    }

    @Operation(summary = "管理员查看审计日志")
    @GetMapping("/audit-logs")
    public PagedResponse<AuditLogItem> auditLogs(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 @RequestParam(value = "actionType", required = false) String actionType,
                                                 @RequestParam(value = "targetType", required = false) String targetType,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "actorUserId", required = false) Long actorUserId,
                                                 @RequestParam(value = "createdFrom", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                                                 @RequestParam(value = "createdTo", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        return auditLogService.listLogs(page, size, actionType, targetType, keyword, actorUserId, createdFrom, createdTo);
    }

}

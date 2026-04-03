package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.AdminCreateDoctorRequest;
import com.linkjb.aimed.bean.AdminUpdateUserRoleRequest;
import com.linkjb.aimed.bean.AdminUpdateUserStatusRequest;
import com.linkjb.aimed.bean.AdminUserItem;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final AppUserService appUserService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AdminService(AppUserService appUserService,
                        PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService) {
        this.appUserService = appUserService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public PagedResponse<AdminUserItem> listUsers(int page, int size, String keyword, String role, String status) {
        return appUserService.listUsers(page, size, keyword, role, status);
    }

    public AdminUserItem createDoctor(AdminCreateDoctorRequest request, AuthenticatedUser currentUser) {
        String email = appUserService.normalizeEmail(request.email());
        if (appUserService.existsByEmail(email)) {
            throw new IllegalStateException("该邮箱已注册");
        }
        AppUser user = appUserService.createUser(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname(),
                AppUserService.ROLE_DOCTOR
        );
        auditLogService.recordAdminAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_ADMIN_CREATE_DOCTOR,
                String.valueOf(user.getId()),
                "创建医生账号 " + user.getEmail());
        return appUserService.toAdminUserItem(appUserService.findById(user.getId()));
    }

    public AdminUserItem updateUserRole(Long userId, AdminUpdateUserRoleRequest request, AuthenticatedUser currentUser) {
        String normalizedRole = AppUserService.normalizeRole(request.role());
        if (!AppUserService.isSupportedRole(normalizedRole)) {
            throw new IllegalArgumentException("不支持的角色类型");
        }
        AppUser target = appUserService.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!StringUtils.hasText(target.getRole()) || normalizedRole.equalsIgnoreCase(target.getRole())) {
            return appUserService.toAdminUserItem(target);
        }

        if (AppUserService.ROLE_ADMIN.equalsIgnoreCase(target.getRole())
                && AppUserService.STATUS_ACTIVE.equalsIgnoreCase(target.getStatus())
                && !AppUserService.ROLE_ADMIN.equalsIgnoreCase(normalizedRole)
                && appUserService.countByRoleAndStatus(AppUserService.ROLE_ADMIN, AppUserService.STATUS_ACTIVE) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能调整系统中最后一个启用管理员的角色");
        }

        appUserService.updateRole(target.getId(), normalizedRole);
        AppUser updated = appUserService.findById(target.getId());
        auditLogService.recordAdminAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_ADMIN_UPDATE_ROLE,
                String.valueOf(updated.getId()),
                "将 " + updated.getEmail() + " 的角色调整为 " + updated.getRole());
        return appUserService.toAdminUserItem(updated);
    }

    public AdminUserItem updateUserStatus(Long userId, AdminUpdateUserStatusRequest request, AuthenticatedUser currentUser) {
        String normalizedStatus = AppUserService.normalizeStatus(request.status());
        if (!AppUserService.isSupportedStatus(normalizedStatus)) {
            throw new IllegalArgumentException("不支持的用户状态");
        }
        AppUser target = appUserService.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!StringUtils.hasText(target.getStatus()) || normalizedStatus.equalsIgnoreCase(target.getStatus())) {
            return appUserService.toAdminUserItem(target);
        }

        if (AppUserService.ROLE_ADMIN.equalsIgnoreCase(target.getRole())
                && AppUserService.STATUS_ACTIVE.equalsIgnoreCase(target.getStatus())
                && AppUserService.STATUS_DISABLED.equalsIgnoreCase(normalizedStatus)
                && appUserService.countByRoleAndStatus(AppUserService.ROLE_ADMIN, AppUserService.STATUS_ACTIVE) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能禁用系统中最后一个启用管理员");
        }

        appUserService.updateStatus(target.getId(), normalizedStatus);
        AppUser updated = appUserService.findById(target.getId());
        auditLogService.recordAdminAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_ADMIN_UPDATE_STATUS,
                String.valueOf(updated.getId()),
                "将 " + updated.getEmail() + " 的状态调整为 " + updated.getStatus());
        return appUserService.toAdminUserItem(updated);
    }
}

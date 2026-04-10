package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linkjb.aimed.bean.AdminUserItem;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppUserService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_PATIENT = "PATIENT";
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String LEGACY_ROLE_USER = "USER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private final AppUserMapper appUserMapper;

    public AppUserService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    public AppUser findById(Long id) {
        return id == null ? null : appUserMapper.selectById(id);
    }

    public AppUser findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, normalizeEmail(email))
                .last("limit 1"));
    }

    public AppUser findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, normalizeUsername(username))
                .last("limit 1"));
    }

    public AppUser findByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String trimmed = account.trim();
        return trimmed.contains("@") ? findByEmail(trimmed) : findByUsername(trimmed);
    }

    public boolean existsByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, normalizeEmail(email))) > 0;
    }

    public boolean existsByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, normalizeUsername(username))) > 0;
    }

    public AppUser createUser(String email, String passwordHash, String nickname, String role) {
        AppUser user = new AppUser();
        user.setUsername(generateAvailableUsername(email));
        user.setEmail(normalizeEmail(email));
        user.setPasswordHash(passwordHash);
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : defaultNickname(email));
        user.setRole(StringUtils.hasText(role) ? normalizeRole(role) : ROLE_PATIENT);
        user.setStatus(STATUS_ACTIVE);
        appUserMapper.insert(user);
        return user;
    }

    public void updateRole(Long userId, String role) {
        if (userId == null || !StringUtils.hasText(role)) {
            return;
        }
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getRole, normalizeRole(role))
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
    }

    public void updateStatus(Long userId, String status) {
        if (userId == null || !StringUtils.hasText(status)) {
            return;
        }
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getStatus, normalizeStatus(status))
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
    }

    public void updateLastLogin(Long userId) {
        if (userId == null) {
            return;
        }
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getLastLoginAt, LocalDateTime.now()));
    }

    public void updatePassword(Long userId, String passwordHash) {
        if (userId == null || !StringUtils.hasText(passwordHash)) {
            return;
        }
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getPasswordHash, passwordHash)
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    public long countByRole(String role) {
        if (!StringUtils.hasText(role)) {
            return 0;
        }
        return appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getRole, normalizeRole(role)));
    }

    public long countByRoleAndStatus(String role, String status) {
        if (!StringUtils.hasText(role) || !StringUtils.hasText(status)) {
            return 0;
        }
        return appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getRole, normalizeRole(role))
                .eq(AppUser::getStatus, normalizeStatus(status)));
    }

    public Map<Long, AppUser> mapByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    public PagedResponse<AdminUserItem> listUsers(int page, int size, String keyword, String role, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(condition -> condition
                    .like(AppUser::getEmail, normalizedKeyword)
                    .or()
                    .like(AppUser::getUsername, normalizedKeyword)
                    .or()
                    .like(AppUser::getNickname, normalizedKeyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(AppUser::getRole, normalizeRole(role));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AppUser::getStatus, status.trim().toUpperCase());
        }

        Page<AppUser> resultPage = appUserMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper.orderByDesc(AppUser::getCreatedAt, AppUser::getId)
        );
        long total = resultPage.getTotal();
        List<AppUser> users = total == 0 ? Collections.emptyList() : resultPage.getRecords();
        return new PagedResponse<>(total, safePage, safeSize, users.stream().map(this::toAdminUserItem).toList());
    }

    public AdminUserItem toAdminUserItem(AppUser user) {
        if (user == null) {
            return null;
        }
        return new AdminUserItem(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                normalizeRole(user.getRole()),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }

    public static boolean isSupportedRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        String normalized = normalizeRole(role);
        return ROLE_ADMIN.equals(normalized) || ROLE_PATIENT.equals(normalized) || ROLE_DOCTOR.equals(normalized);
    }

    public static boolean isSupportedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = normalizeStatus(status);
        return STATUS_ACTIVE.equals(normalized) || STATUS_DISABLED.equals(normalized);
    }

    public static String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return ROLE_PATIENT;
        }
        String normalized = role.trim().toUpperCase();
        return LEGACY_ROLE_USER.equals(normalized) ? ROLE_PATIENT : normalized;
    }

    public static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ACTIVE;
        }
        return status.trim().toUpperCase();
    }

    private String defaultNickname(String email) {
        String normalized = normalizeEmail(email);
        int separatorIndex = normalized == null ? -1 : normalized.indexOf('@');
        return separatorIndex > 0 ? normalized.substring(0, separatorIndex) : "树兰用户";
    }

    private String generateAvailableUsername(String email) {
        String normalized = normalizeEmail(email);
        String seed = "user";
        if (normalized != null && normalized.contains("@")) {
            seed = normalized.substring(0, normalized.indexOf('@')).replaceAll("[^a-zA-Z0-9_\\-]", "");
        }
        if (!StringUtils.hasText(seed)) {
            seed = "user";
        }
        String candidate = normalizeUsername(seed);
        int suffix = 1;
        while (existsByUsername(candidate)) {
            candidate = normalizeUsername(seed + suffix);
            suffix++;
        }
        return candidate;
    }
}

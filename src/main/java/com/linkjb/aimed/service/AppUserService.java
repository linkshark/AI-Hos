package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AppUserService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
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
        user.setRole(StringUtils.hasText(role) ? role : ROLE_USER);
        user.setStatus(STATUS_ACTIVE);
        appUserMapper.insert(user);
        return user;
    }

    public void updateLastLogin(Long userId) {
        if (userId == null) {
            return;
        }
        AppUser user = new AppUser();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        appUserMapper.updateById(user);
    }

    public void updatePassword(Long userId, String passwordHash) {
        if (userId == null || !StringUtils.hasText(passwordHash)) {
            return;
        }
        AppUser user = new AppUser();
        user.setId(userId);
        user.setPasswordHash(passwordHash);
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(user);
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
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

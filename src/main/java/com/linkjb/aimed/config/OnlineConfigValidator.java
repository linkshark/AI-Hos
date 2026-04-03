package com.linkjb.aimed.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class OnlineConfigValidator {

    private static final String DEFAULT_JWT_SECRET = "change-me-for-production-change-me-for-production";
    private static final String DEFAULT_ADMIN_PASSWORD = "change-me-admin-password";
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@shulan.local";

    private final AppRuntimeProperties appRuntimeProperties;
    private final AuthProperties authProperties;
    private final AppSecurityProperties appSecurityProperties;

    public OnlineConfigValidator(AppRuntimeProperties appRuntimeProperties,
                                 AuthProperties authProperties,
                                 AppSecurityProperties appSecurityProperties) {
        this.appRuntimeProperties = appRuntimeProperties;
        this.authProperties = authProperties;
        this.appSecurityProperties = appSecurityProperties;
    }

    @PostConstruct
    public void validate() {
        if (!appRuntimeProperties.isOnlineConfig()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        if (!StringUtils.hasText(authProperties.getJwtSecret()) || DEFAULT_JWT_SECRET.equals(authProperties.getJwtSecret())) {
            violations.add("app.auth.jwt-secret 仍为默认值");
        }
        if (!StringUtils.hasText(authProperties.getAdminPassword()) || DEFAULT_ADMIN_PASSWORD.equals(authProperties.getAdminPassword())) {
            violations.add("app.auth.admin-password 仍为默认值");
        }
        if (authProperties.isMailMockEnabled()) {
            violations.add("app.auth.mail-mock-enabled 在线上必须为 false");
        }
        if (CollectionUtils.isEmpty(appSecurityProperties.getAllowedOriginPatterns())) {
            violations.add("app.security.allowed-origin-patterns 未配置");
        }
        if (authProperties.isAdminBootstrapEnabled()) {
            if (!StringUtils.hasText(authProperties.getAdminUsername()) || DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(authProperties.getAdminUsername())) {
                violations.add("线上启用 admin bootstrap 时，app.auth.admin-username 不能使用默认值");
            }
            if (!StringUtils.hasText(authProperties.getAdminEmail()) || DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(authProperties.getAdminEmail())) {
                violations.add("线上启用 admin bootstrap 时，app.auth.admin-email 不能使用默认值");
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("线上配置校验失败: " + String.join("；", violations));
        }
    }
}

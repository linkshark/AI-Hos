package com.linkjb.aimed.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.mapper.AppUserMapper;
import com.linkjb.aimed.service.AppUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

@Component
public class AuthSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final AppUserMapper appUserMapper;

    public AuthSchemaInitializer(JdbcTemplate jdbcTemplate,
                                 AuthProperties authProperties,
                                 PasswordEncoder passwordEncoder,
                                 AppUserMapper appUserMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.appUserMapper = appUserMapper;
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  username VARCHAR(64) NOT NULL,
                  email VARCHAR(255) NOT NULL,
                  password_hash VARCHAR(255) NOT NULL,
                  nickname VARCHAR(128) DEFAULT NULL,
                  role VARCHAR(32) NOT NULL DEFAULT 'USER',
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  last_login_at DATETIME DEFAULT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_app_user_username (username),
                  UNIQUE KEY uk_app_user_email (email),
                  KEY idx_app_user_role (role),
                  KEY idx_app_user_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing("username", "ALTER TABLE app_user ADD COLUMN username VARCHAR(64) NOT NULL DEFAULT 'user' AFTER id");
        addUniqueIndexIfMissing("uk_app_user_username", "ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_username (username)");

        bootstrapAdmin();
    }

    private void bootstrapAdmin() {
        if (!authProperties.isAdminBootstrapEnabled()
                || !StringUtils.hasText(authProperties.getAdminUsername())
                || !StringUtils.hasText(authProperties.getAdminEmail())
                || !StringUtils.hasText(authProperties.getAdminPassword())) {
            return;
        }
        String username = authProperties.getAdminUsername().trim().toLowerCase();
        String email = authProperties.getAdminEmail().trim().toLowerCase();
        Long count = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .and(wrapper -> wrapper.eq(AppUser::getEmail, email).or().eq(AppUser::getUsername, username)));
        if (count != null && count > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(authProperties.getAdminPassword()));
        admin.setNickname(authProperties.getAdminNickname());
        admin.setRole(AppUserService.ROLE_ADMIN);
        admin.setStatus(AppUserService.STATUS_ACTIVE);
        appUserMapper.insert(admin);
    }

    private void addColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'app_user'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addUniqueIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'app_user'
                          AND index_name = ?
                        """,
                Integer.class,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}

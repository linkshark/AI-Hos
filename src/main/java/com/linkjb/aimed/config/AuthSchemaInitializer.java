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
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                  username VARCHAR(64) NOT NULL COMMENT '用户名',
                  email VARCHAR(255) NOT NULL COMMENT '登录邮箱',
                  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
                  nickname VARCHAR(128) DEFAULT NULL COMMENT '昵称',
                  role VARCHAR(32) NOT NULL DEFAULT 'PATIENT' COMMENT '角色',
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
                  last_login_at DATETIME DEFAULT NULL COMMENT '最近登录时间',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_app_user_username (username),
                  UNIQUE KEY uk_app_user_email (email),
                  KEY idx_app_user_role (role),
                  KEY idx_app_user_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing("username", "ALTER TABLE app_user ADD COLUMN username VARCHAR(64) NOT NULL DEFAULT 'user' AFTER id");
        addUniqueIndexIfMissing("uk_app_user_username", "ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_username (username)");
        jdbcTemplate.update("UPDATE app_user SET role = ? WHERE role = ?", AppUserService.ROLE_PATIENT, AppUserService.LEGACY_ROLE_USER);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_session_owner (
                  memory_id BIGINT NOT NULL COMMENT '会话 memoryId',
                  user_id BIGINT NOT NULL COMMENT '会话所属用户 ID',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (memory_id),
                  KEY idx_chat_session_owner_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                  actor_user_id BIGINT DEFAULT NULL COMMENT '操作人用户 ID',
                  actor_role VARCHAR(32) DEFAULT NULL COMMENT '操作人角色',
                  action_type VARCHAR(64) NOT NULL COMMENT '动作类型',
                  target_type VARCHAR(64) DEFAULT NULL COMMENT '目标范围',
                  target_id VARCHAR(128) DEFAULT NULL COMMENT '目标对象 ID',
                  summary VARCHAR(512) DEFAULT NULL COMMENT '摘要说明',
                  trace_id VARCHAR(64) DEFAULT NULL COMMENT 'SkyWalking TraceId',
                  memory_id BIGINT DEFAULT NULL COMMENT '关联会话 ID',
                  provider VARCHAR(64) DEFAULT NULL COMMENT '模型提供方',
                  query_type VARCHAR(64) DEFAULT NULL COMMENT '问答查询类型',
                  retrieved_count_keyword INT DEFAULT NULL COMMENT '关键词召回数',
                  retrieved_count_vector INT DEFAULT NULL COMMENT '向量召回数',
                  merged_count INT DEFAULT NULL COMMENT '合并后候选数',
                  final_citation_count INT DEFAULT NULL COMMENT '最终引用数',
                  empty_recall TINYINT(1) DEFAULT NULL COMMENT '是否空召回',
                  top_doc_hashes VARCHAR(512) DEFAULT NULL COMMENT '命中文档 hash 摘要',
                  duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
                  has_attachments TINYINT(1) DEFAULT NULL COMMENT '是否包含附件',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_audit_log_action_type (action_type),
                  KEY idx_audit_log_actor_user_id (actor_user_id),
                  KEY idx_audit_log_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addAuditLogColumnIfMissing("query_type", "ALTER TABLE audit_log ADD COLUMN query_type VARCHAR(64) DEFAULT NULL");
        addAuditLogColumnIfMissing("retrieved_count_keyword", "ALTER TABLE audit_log ADD COLUMN retrieved_count_keyword INT DEFAULT NULL");
        addAuditLogColumnIfMissing("retrieved_count_vector", "ALTER TABLE audit_log ADD COLUMN retrieved_count_vector INT DEFAULT NULL");
        addAuditLogColumnIfMissing("merged_count", "ALTER TABLE audit_log ADD COLUMN merged_count INT DEFAULT NULL");
        addAuditLogColumnIfMissing("final_citation_count", "ALTER TABLE audit_log ADD COLUMN final_citation_count INT DEFAULT NULL");
        addAuditLogColumnIfMissing("empty_recall", "ALTER TABLE audit_log ADD COLUMN empty_recall TINYINT(1) DEFAULT NULL");
        addAuditLogColumnIfMissing("top_doc_hashes", "ALTER TABLE audit_log ADD COLUMN top_doc_hashes VARCHAR(512) DEFAULT NULL");
        ensureColumnComments();

        bootstrapAdmin();
    }

    private void ensureColumnComments() {
        updateAppUserColumnComment("id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID'");
        updateAppUserColumnComment("username VARCHAR(64) NOT NULL COMMENT '用户名'");
        updateAppUserColumnComment("email VARCHAR(255) NOT NULL COMMENT '登录邮箱'");
        updateAppUserColumnComment("password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希'");
        updateAppUserColumnComment("nickname VARCHAR(128) DEFAULT NULL COMMENT '昵称'");
        updateAppUserColumnComment("role VARCHAR(32) NOT NULL DEFAULT 'PATIENT' COMMENT '角色'");
        updateAppUserColumnComment("status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态'");
        updateAppUserColumnComment("last_login_at DATETIME DEFAULT NULL COMMENT '最近登录时间'");
        updateAppUserColumnComment("created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        updateAppUserColumnComment("updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");

        updateChatSessionOwnerColumnComment("memory_id BIGINT NOT NULL COMMENT '会话 memoryId'");
        updateChatSessionOwnerColumnComment("user_id BIGINT NOT NULL COMMENT '会话所属用户 ID'");
        updateChatSessionOwnerColumnComment("created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        updateChatSessionOwnerColumnComment("updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");

        updateAuditLogColumnComment("id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID'");
        updateAuditLogColumnComment("actor_user_id BIGINT DEFAULT NULL COMMENT '操作人用户 ID'");
        updateAuditLogColumnComment("actor_role VARCHAR(32) DEFAULT NULL COMMENT '操作人角色'");
        updateAuditLogColumnComment("action_type VARCHAR(64) NOT NULL COMMENT '动作类型'");
        updateAuditLogColumnComment("target_type VARCHAR(64) DEFAULT NULL COMMENT '目标范围'");
        updateAuditLogColumnComment("target_id VARCHAR(128) DEFAULT NULL COMMENT '目标对象 ID'");
        updateAuditLogColumnComment("summary VARCHAR(512) DEFAULT NULL COMMENT '摘要说明'");
        updateAuditLogColumnComment("trace_id VARCHAR(64) DEFAULT NULL COMMENT 'SkyWalking TraceId'");
        updateAuditLogColumnComment("memory_id BIGINT DEFAULT NULL COMMENT '关联会话 ID'");
        updateAuditLogColumnComment("provider VARCHAR(64) DEFAULT NULL COMMENT '模型提供方'");
        updateAuditLogColumnComment("query_type VARCHAR(64) DEFAULT NULL COMMENT '问答查询类型'");
        updateAuditLogColumnComment("retrieved_count_keyword INT DEFAULT NULL COMMENT '关键词召回数'");
        updateAuditLogColumnComment("retrieved_count_vector INT DEFAULT NULL COMMENT '向量召回数'");
        updateAuditLogColumnComment("merged_count INT DEFAULT NULL COMMENT '合并后候选数'");
        updateAuditLogColumnComment("final_citation_count INT DEFAULT NULL COMMENT '最终引用数'");
        updateAuditLogColumnComment("empty_recall TINYINT(1) DEFAULT NULL COMMENT '是否空召回'");
        updateAuditLogColumnComment("top_doc_hashes VARCHAR(512) DEFAULT NULL COMMENT '命中文档 hash 摘要'");
        updateAuditLogColumnComment("duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒'");
        updateAuditLogColumnComment("has_attachments TINYINT(1) DEFAULT NULL COMMENT '是否包含附件'");
        updateAuditLogColumnComment("created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
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

    private void addAuditLogColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'audit_log'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void updateAppUserColumnComment(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN " + columnDefinition);
    }

    private void updateChatSessionOwnerColumnComment(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE chat_session_owner MODIFY COLUMN " + columnDefinition);
    }

    private void updateAuditLogColumnComment(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE audit_log MODIFY COLUMN " + columnDefinition);
    }
}

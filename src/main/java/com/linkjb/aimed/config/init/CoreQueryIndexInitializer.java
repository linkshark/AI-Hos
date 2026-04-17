package com.linkjb.aimed.config.init;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * 核心查询索引的幂等初始化器。
 *
 * 当前项目没有统一的数据库迁移框架，这里只补“确定被高频查询吃到”的低风险索引：
 * - 列表页的用户、审计、历史排序
 * - 知识文件与 chunk 的详情/批量同步
 * - 科室树和疾病标准页的常用检索
 */
@Component
@DependsOn("medicalStandardSchemaInitializer")
public class CoreQueryIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    public CoreQueryIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        initializeAppUserIndexes();
        initializeAuditLogIndexes();
        initializeChatSessionOwnerIndexes();
        initializeDeptInfoIndexes();
        initializeKnowledgeIndexes();
        initializeMedicalIndexes();
    }

    private void initializeAppUserIndexes() {
        addIndexIfMissing("app_user", "idx_app_user_email",
                new String[]{"email"},
                "ALTER TABLE app_user ADD INDEX idx_app_user_email (email)");
        addIndexIfMissing("app_user", "idx_app_user_username",
                new String[]{"username"},
                "ALTER TABLE app_user ADD INDEX idx_app_user_username (username)");
        addIndexIfMissing("app_user", "idx_app_user_role_status_created",
                new String[]{"role", "status", "created_at", "id"},
                "ALTER TABLE app_user ADD INDEX idx_app_user_role_status_created (role, status, created_at, id)");
    }

    private void initializeAuditLogIndexes() {
        addIndexIfMissing("audit_log", "idx_audit_log_created_id",
                new String[]{"created_at", "id"},
                "ALTER TABLE audit_log ADD INDEX idx_audit_log_created_id (created_at, id)");
        addIndexIfMissing("audit_log", "idx_audit_log_action_created",
                new String[]{"action_type", "created_at", "id"},
                "ALTER TABLE audit_log ADD INDEX idx_audit_log_action_created (action_type, created_at, id)");
        addIndexIfMissing("audit_log", "idx_audit_log_target_created",
                new String[]{"target_type", "created_at", "id"},
                "ALTER TABLE audit_log ADD INDEX idx_audit_log_target_created (target_type, created_at, id)");
        addIndexIfMissing("audit_log", "idx_audit_log_actor_created",
                new String[]{"actor_user_id", "created_at", "id"},
                "ALTER TABLE audit_log ADD INDEX idx_audit_log_actor_created (actor_user_id, created_at, id)");
    }

    private void initializeChatSessionOwnerIndexes() {
        addIndexIfMissing("chat_session_owner", "idx_chat_owner_user_visible_sort",
                new String[]{"user_id", "hidden", "pinned", "pinned_at", "updated_at", "created_at"},
                "ALTER TABLE chat_session_owner ADD INDEX idx_chat_owner_user_visible_sort (user_id, hidden, pinned, pinned_at, updated_at, created_at)");
    }

    private void initializeDeptInfoIndexes() {
        addIndexIfMissing("dept_info", "idx_dept_info_code_status",
                new String[]{"dept_code", "status"},
                "ALTER TABLE dept_info ADD INDEX idx_dept_info_code_status (dept_code, status)");
        addIndexIfMissing("dept_info", "idx_dept_info_status_sort_create",
                new String[]{"status", "sort", "create_time"},
                "ALTER TABLE dept_info ADD INDEX idx_dept_info_status_sort_create (status, sort, create_time)");
        addIndexIfMissing("dept_info", "idx_dept_info_parent_sort_create",
                new String[]{"parent_id", "sort", "create_time"},
                "ALTER TABLE dept_info ADD INDEX idx_dept_info_parent_sort_create (parent_id, sort, create_time)");
        addIndexIfMissing("dept_info", "idx_dept_info_pinyin",
                new String[]{"pinyin_code"},
                "ALTER TABLE dept_info ADD INDEX idx_dept_info_pinyin (pinyin_code)");
        addIndexIfMissing("dept_info", "idx_dept_info_name",
                new String[]{"dept_name"},
                "ALTER TABLE dept_info ADD INDEX idx_dept_info_name (dept_name)");
    }

    private void initializeKnowledgeIndexes() {
        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_hash",
                new String[]{"hash"},
                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_hash (hash)");
        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_source_name",
                new String[]{"source", "original_filename"},
                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_source_name (source, original_filename)");
        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_status_updated",
                new String[]{"processing_status", "updated_at"},
                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_status_updated (processing_status, updated_at)");

        addIndexIfMissing("knowledge_chunk_index", "idx_knowledge_chunk_file_segment",
                new String[]{"file_hash", "segment_index"},
                "ALTER TABLE knowledge_chunk_index ADD INDEX idx_knowledge_chunk_file_segment (file_hash, segment_index)");
        addIndexIfMissing("knowledge_chunk_index", "idx_knowledge_chunk_publish_filter",
                new String[]{"status", "audience", "source_priority", "file_hash"},
                "ALTER TABLE knowledge_chunk_index ADD INDEX idx_knowledge_chunk_publish_filter (status, audience, source_priority, file_hash)");
    }

    private void initializeMedicalIndexes() {
        addIndexIfMissing("medical_concept", "idx_medical_concept_status_category_code",
                new String[]{"status", "category_code", "standard_code"},
                "ALTER TABLE medical_concept ADD INDEX idx_medical_concept_status_category_code (status, category_code, standard_code)");
        addIndexIfMissing("medical_concept", "idx_medical_concept_status_updated",
                new String[]{"status", "updated_at"},
                "ALTER TABLE medical_concept ADD INDEX idx_medical_concept_status_updated (status, updated_at)");
        addIndexIfMissing("medical_concept_alias", "idx_medical_alias_status_alias",
                new String[]{"status", "alias"},
                "ALTER TABLE medical_concept_alias ADD INDEX idx_medical_alias_status_alias (status, alias)");
        addIndexIfMissing("medical_symptom_mapping", "idx_medical_symptom_concept_updated",
                new String[]{"concept_code", "updated_at", "id"},
                "ALTER TABLE medical_symptom_mapping ADD INDEX idx_medical_symptom_concept_updated (concept_code, updated_at, id)");
        addIndexIfMissing("medical_symptom_mapping", "idx_medical_symptom_enabled_updated",
                new String[]{"enabled", "updated_at", "id"},
                "ALTER TABLE medical_symptom_mapping ADD INDEX idx_medical_symptom_enabled_updated (enabled, updated_at, id)");
        addIndexIfMissing("medical_doc_mapping", "idx_medical_doc_hash_sort_updated",
                new String[]{"knowledge_hash", "sort_order", "updated_at"},
                "ALTER TABLE medical_doc_mapping ADD INDEX idx_medical_doc_hash_sort_updated (knowledge_hash, sort_order, updated_at)");
    }

    private void addIndexIfMissing(String tableName, String indexName, String[] requiredColumns, String ddl) {
        if (!tableExists(tableName) || indexExists(tableName, indexName) || !columnsExist(tableName, requiredColumns)) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return !Objects.equals(count, 0);
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        return !Objects.equals(count, 0);
    }

    private boolean columnsExist(String tableName, String[] columnNames) {
        if (columnNames == null || columnNames.length == 0) {
            return true;
        }
        String placeholders = String.join(",", Arrays.stream(columnNames).map(item -> "?").toList());
        Object[] args = new Object[columnNames.length + 1];
        args[0] = tableName;
        System.arraycopy(columnNames, 0, args, 1, columnNames.length);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME IN (""" + placeholders + ")",
                Integer.class,
                args
        );
        return Objects.equals(count, columnNames.length);
    }
}

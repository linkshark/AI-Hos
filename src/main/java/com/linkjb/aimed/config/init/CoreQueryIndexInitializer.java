//package com.linkjb.aimed.config.init;
//
//import jakarta.annotation.PostConstruct;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.DependsOn;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.Arrays;
//import java.util.Objects;
//
///**
// * 核心查询索引的幂等初始化器。
// *
// * 当前项目没有统一的数据库迁移框架，这里只补“确定被高频查询吃到”的低风险索引：
// * - 列表页的用户、审计、历史排序
// * - 知识文件与 chunk 的详情/批量同步
// * - 科室树和疾病标准页的常用检索
// */
//@Component
//@DependsOn("medicalStandardSchemaInitializer")
//public class CoreQueryIndexInitializer {
//
//    private static final Logger log = LoggerFactory.getLogger(CoreQueryIndexInitializer.class);
//    private final JdbcTemplate jdbcTemplate;
//
//    public CoreQueryIndexInitializer(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    @PostConstruct
//    public void initialize() {
//        initializeAppUserIndexes();
//        initializeAuditLogIndexes();
//        initializeChatSessionOwnerIndexes();
//        initializeDeptInfoIndexes();
//        initializeKnowledgeIndexes();
//        initializeMedicalIndexes();
//    }
//
//    private void initializeAppUserIndexes() {
//        addIndexIfMissing("app_user", "idx_app_user_email",
//                new String[]{"email"},
//                "ALTER TABLE app_user ADD INDEX idx_app_user_email (email)");
//        addIndexIfMissing("app_user", "idx_app_user_username",
//                new String[]{"username"},
//                "ALTER TABLE app_user ADD INDEX idx_app_user_username (username)");
//        addIndexIfMissing("app_user", "idx_app_user_role_status_created",
//                new String[]{"role", "status", "created_at", "id"},
//                "ALTER TABLE app_user ADD INDEX idx_app_user_role_status_created (role, status, created_at, id)");
//    }
//
//    private void initializeAuditLogIndexes() {
//        addIndexIfMissing("audit_log", "idx_audit_log_created_id",
//                new String[]{"created_at", "id"},
//                "ALTER TABLE audit_log ADD INDEX idx_audit_log_created_id (created_at, id)");
//        addIndexIfMissing("audit_log", "idx_audit_log_action_created",
//                new String[]{"action_type", "created_at", "id"},
//                "ALTER TABLE audit_log ADD INDEX idx_audit_log_action_created (action_type, created_at, id)");
//        addIndexIfMissing("audit_log", "idx_audit_log_target_created",
//                new String[]{"target_type", "created_at", "id"},
//                "ALTER TABLE audit_log ADD INDEX idx_audit_log_target_created (target_type, created_at, id)");
//        addIndexIfMissing("audit_log", "idx_audit_log_actor_created",
//                new String[]{"actor_user_id", "created_at", "id"},
//                "ALTER TABLE audit_log ADD INDEX idx_audit_log_actor_created (actor_user_id, created_at, id)");
//    }
//
//    private void initializeChatSessionOwnerIndexes() {
//        addIndexIfMissing("chat_session_owner", "idx_chat_owner_user_visible_sort",
//                new String[]{"user_id", "hidden", "pinned", "pinned_at", "updated_at", "created_at"},
//                "ALTER TABLE chat_session_owner ADD INDEX idx_chat_owner_user_visible_sort (user_id, hidden, pinned, pinned_at, updated_at, created_at)");
//    }
//
//    private void initializeDeptInfoIndexes() {
//        addIndexIfMissing("dept_info", "idx_dept_info_code_status",
//                new String[]{"dept_code", "status"},
//                "ALTER TABLE dept_info ADD INDEX idx_dept_info_code_status (dept_code, status)");
//        addIndexIfMissing("dept_info", "idx_dept_info_status_sort_create",
//                new String[]{"status", "sort", "create_time"},
//                "ALTER TABLE dept_info ADD INDEX idx_dept_info_status_sort_create (status, sort, create_time)");
//        addIndexIfMissing("dept_info", "idx_dept_info_parent_sort_create",
//                new String[]{"parent_id", "sort", "create_time"},
//                "ALTER TABLE dept_info ADD INDEX idx_dept_info_parent_sort_create (parent_id, sort, create_time)");
//        addIndexIfMissing("dept_info", "idx_dept_info_pinyin",
//                new String[]{"pinyin_code"},
//                "ALTER TABLE dept_info ADD INDEX idx_dept_info_pinyin (pinyin_code)");
//        addIndexIfMissing("dept_info", "idx_dept_info_name",
//                new String[]{"dept_name"},
//                "ALTER TABLE dept_info ADD INDEX idx_dept_info_name (dept_name)");
//    }
//
//    private void initializeKnowledgeIndexes() {
//        addColumnIfMissing("knowledge_file_status", "index_build_version",
//                "ALTER TABLE knowledge_file_status ADD COLUMN index_build_version INT NOT NULL DEFAULT 0 COMMENT '知识索引构建版本，低于当前版本时会触发自动升级' AFTER embedding_model_name");
//        addColumnIfMissing("knowledge_file_status", "current_generation",
//                "ALTER TABLE knowledge_file_status ADD COLUMN current_generation INT NOT NULL DEFAULT 1 COMMENT '当前对外生效的索引代际' AFTER index_build_version");
//        addColumnIfMissing("knowledge_file_status", "index_upgrade_state",
//                "ALTER TABLE knowledge_file_status ADD COLUMN index_upgrade_state VARCHAR(32) NOT NULL DEFAULT 'IDLE' COMMENT '索引升级状态：IDLE / UPGRADING / FAILED' AFTER current_generation");
//        addColumnIfMissing("knowledge_file_status", "index_upgrade_message",
//                "ALTER TABLE knowledge_file_status ADD COLUMN index_upgrade_message VARCHAR(255) NULL COMMENT '索引升级说明' AFTER index_upgrade_state");
//
//        addColumnIfMissing("knowledge_chunk_index", "generation",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN generation INT NOT NULL DEFAULT 1 COMMENT 'chunk 所属索引代际，仅 current_generation 对外生效' AFTER segment_index");
//        addColumnIfMissing("knowledge_chunk_index", "section_title",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN section_title VARCHAR(255) NULL COMMENT 'chunk 所属章节标题' AFTER character_count");
//        addColumnIfMissing("knowledge_chunk_index", "segment_kind",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN segment_kind VARCHAR(32) NULL COMMENT 'chunk 结构类型：SECTION / PARAGRAPH / LIST / STEP / FALLBACK' AFTER section_title");
//        addColumnIfMissing("knowledge_chunk_index", "segmentation_mode",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN segmentation_mode VARCHAR(32) NOT NULL DEFAULT 'RULE_RECURSIVE' COMMENT '切分模式：RULE_RECURSIVE / STRUCTURED' AFTER segment_kind");
//        addColumnIfMissing("knowledge_chunk_index", "semantic_summary",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN semantic_summary VARCHAR(255) NULL COMMENT 'chunk 语义摘要' AFTER segmentation_mode");
//        addColumnIfMissing("knowledge_chunk_index", "semantic_keywords",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN semantic_keywords VARCHAR(512) NULL COMMENT 'chunk 语义关键词，空格分隔' AFTER semantic_summary");
//        addColumnIfMissing("knowledge_chunk_index", "section_role",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN section_role VARCHAR(64) NULL COMMENT 'chunk 语义角色：诊断、治疗、检查、流程等' AFTER semantic_keywords");
//        addColumnIfMissing("knowledge_chunk_index", "medical_entities_json",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN medical_entities_json JSON NULL COMMENT 'chunk 命中的疾病/症状/检查等术语 JSON 列表' AFTER section_role");
//        addColumnIfMissing("knowledge_chunk_index", "target_questions_json",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN target_questions_json JSON NULL COMMENT 'chunk 适合回答的问题 JSON 列表' AFTER medical_entities_json");
//        addColumnIfMissing("knowledge_chunk_index", "semantic_enriched_at",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN semantic_enriched_at DATETIME NULL COMMENT 'chunk 语义增强时间' AFTER target_questions_json");
//        addColumnIfMissing("knowledge_chunk_index", "semantic_enrichment_model",
//                "ALTER TABLE knowledge_chunk_index ADD COLUMN semantic_enrichment_model VARCHAR(128) NULL COMMENT 'chunk 语义增强使用的模型名或策略名' AFTER semantic_enriched_at");
//
//        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_hash",
//                new String[]{"hash"},
//                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_hash (hash)");
//        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_source_name",
//                new String[]{"source", "original_filename"},
//                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_source_name (source, original_filename)");
//        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_status_updated",
//                new String[]{"processing_status", "updated_at"},
//                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_status_updated (processing_status, updated_at)");
//        addIndexIfMissing("knowledge_file_status", "idx_knowledge_file_generation_version",
//                new String[]{"hash", "current_generation", "index_build_version"},
//                "ALTER TABLE knowledge_file_status ADD INDEX idx_knowledge_file_generation_version (hash, current_generation, index_build_version)");
//
//        addIndexIfMissing("knowledge_chunk_index", "idx_knowledge_chunk_file_segment",
//                new String[]{"file_hash", "segment_index"},
//                "ALTER TABLE knowledge_chunk_index ADD INDEX idx_knowledge_chunk_file_segment (file_hash, segment_index)");
//        addIndexIfMissing("knowledge_chunk_index", "idx_knowledge_chunk_file_generation_segment",
//                new String[]{"file_hash", "generation", "segment_index"},
//                "ALTER TABLE knowledge_chunk_index ADD INDEX idx_knowledge_chunk_file_generation_segment (file_hash, generation, segment_index)");
//        addIndexIfMissing("knowledge_chunk_index", "idx_knowledge_chunk_publish_filter",
//                new String[]{"status", "audience", "source_priority", "file_hash", "generation"},
//                "ALTER TABLE knowledge_chunk_index ADD INDEX idx_knowledge_chunk_publish_filter (status, audience, source_priority, file_hash, generation)");
//    }
//
//    private void initializeMedicalIndexes() {
//        addIndexIfMissing("medical_concept", "idx_medical_concept_status_category_code",
//                new String[]{"status", "category_code", "standard_code"},
//                "ALTER TABLE medical_concept ADD INDEX idx_medical_concept_status_category_code (status, category_code, standard_code)");
//        addIndexIfMissing("medical_concept", "idx_medical_concept_status_updated",
//                new String[]{"status", "updated_at"},
//                "ALTER TABLE medical_concept ADD INDEX idx_medical_concept_status_updated (status, updated_at)");
//        addIndexIfMissing("medical_concept_alias", "idx_medical_alias_status_alias",
//                new String[]{"status", "alias"},
//                "ALTER TABLE medical_concept_alias ADD INDEX idx_medical_alias_status_alias (status, alias)");
//        addIndexIfMissing("medical_symptom_mapping", "idx_medical_symptom_concept_updated",
//                new String[]{"concept_code", "updated_at", "id"},
//                "ALTER TABLE medical_symptom_mapping ADD INDEX idx_medical_symptom_concept_updated (concept_code, updated_at, id)");
//        addIndexIfMissing("medical_symptom_mapping", "idx_medical_symptom_enabled_updated",
//                new String[]{"enabled", "updated_at", "id"},
//                "ALTER TABLE medical_symptom_mapping ADD INDEX idx_medical_symptom_enabled_updated (enabled, updated_at, id)");
//        addIndexIfMissing("medical_doc_mapping", "idx_medical_doc_hash_sort_updated",
//                new String[]{"knowledge_hash", "sort_order", "updated_at"},
//                "ALTER TABLE medical_doc_mapping ADD INDEX idx_medical_doc_hash_sort_updated (knowledge_hash, sort_order, updated_at)");
//    }
//
//    private void addIndexIfMissing(String tableName, String indexName, String[] requiredColumns, String ddl) {
//        if (!tableExists(tableName) || indexExists(tableName, indexName) || !columnsExist(tableName, requiredColumns)) {
//            return;
//        }
//        try {
//            jdbcTemplate.execute(ddl);
//        } catch (Exception exception) {
//            // 索引是启动期性能增强项，不应该因为不同 MySQL 版本或线上历史表结构差异阻断主服务启动。
//            log.warn("core.index.create.failed table={} index={} ddl={}", tableName, indexName, ddl, exception);
//        }
//    }
//
//    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
//        if (!tableExists(tableName) || columnExists(tableName, columnName)) {
//            return;
//        }
//        try {
//            jdbcTemplate.execute(ddl);
//        } catch (Exception exception) {
//            log.warn("core.column.create.failed table={} column={} ddl={}", tableName, columnName, ddl, exception);
//        }
//    }
//
//    private boolean tableExists(String tableName) {
//        Integer count = jdbcTemplate.queryForObject("""
//                SELECT COUNT(*)
//                FROM INFORMATION_SCHEMA.TABLES
//                WHERE TABLE_SCHEMA = DATABASE()
//                  AND TABLE_NAME = ?
//                """, Integer.class, tableName);
//        return !Objects.equals(count, 0);
//    }
//
//    private boolean indexExists(String tableName, String indexName) {
//        Integer count = jdbcTemplate.queryForObject("""
//                SELECT COUNT(*)
//                FROM INFORMATION_SCHEMA.STATISTICS
//                WHERE TABLE_SCHEMA = DATABASE()
//                  AND TABLE_NAME = ?
//                  AND INDEX_NAME = ?
//                """, Integer.class, tableName, indexName);
//        return !Objects.equals(count, 0);
//    }
//
//    private boolean columnsExist(String tableName, String[] columnNames) {
//        if (columnNames == null || columnNames.length == 0) {
//            return true;
//        }
//        String placeholders = String.join(",", Arrays.stream(columnNames).map(item -> "?").toList());
//        Object[] args = new Object[columnNames.length + 1];
//        args[0] = tableName;
//        System.arraycopy(columnNames, 0, args, 1, columnNames.length);
//        Integer count = jdbcTemplate.queryForObject("""
//                SELECT COUNT(*)
//                FROM INFORMATION_SCHEMA.COLUMNS
//                WHERE TABLE_SCHEMA = DATABASE()
//                  AND TABLE_NAME = ?
//                  AND COLUMN_NAME IN (""" + placeholders + ")",
//                Integer.class,
//                args
//        );
//        return Objects.equals(count, columnNames.length);
//    }
//
//    private boolean columnExists(String tableName, String columnName) {
//        Integer count = jdbcTemplate.queryForObject("""
//                SELECT COUNT(*)
//                FROM INFORMATION_SCHEMA.COLUMNS
//                WHERE TABLE_SCHEMA = DATABASE()
//                  AND TABLE_NAME = ?
//                  AND COLUMN_NAME = ?
//                """, Integer.class, tableName, columnName);
//        return !Objects.equals(count, 0);
//    }
//}

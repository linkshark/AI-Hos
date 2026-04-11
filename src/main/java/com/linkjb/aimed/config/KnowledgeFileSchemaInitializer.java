package com.linkjb.aimed.config;

import com.linkjb.aimed.service.DeptInfoService;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeFileSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeFileSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_file_status (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                  hash VARCHAR(64) NOT NULL COMMENT '知识文件内容哈希',
                  file_name VARCHAR(512) NOT NULL COMMENT '存储目录中的文件名',
                  original_filename VARCHAR(512) NOT NULL COMMENT '用户原始文件名',
                  source VARCHAR(32) NOT NULL COMMENT '文件来源 bundled/uploaded',
                  parser VARCHAR(128) DEFAULT NULL COMMENT '解析器名称',
                  extension VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名',
                  size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
                  editable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持在线编辑',
                  deletable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许删除',
                  processing_status VARCHAR(32) NOT NULL COMMENT '知识生命周期状态',
                  status_message VARCHAR(512) DEFAULT NULL COMMENT '状态说明',
                  progress_percent INT NOT NULL DEFAULT 0 COMMENT '处理进度百分比',
                  current_batch INT NOT NULL DEFAULT 0 COMMENT '当前 embedding 批次',
                  total_batches INT NOT NULL DEFAULT 0 COMMENT '总 embedding 批次',
                  embedding_model_name VARCHAR(128) DEFAULT NULL COMMENT '当前 embedding 模型名',
                  doc_type VARCHAR(64) DEFAULT NULL COMMENT '文档类型',
                  department VARCHAR(128) DEFAULT NULL COMMENT '归属科室',
                  audience VARCHAR(32) DEFAULT NULL COMMENT '适用对象 PATIENT/DOCTOR/BOTH',
                  version VARCHAR(64) DEFAULT NULL COMMENT '文档版本',
                  effective_at TIMESTAMP NULL DEFAULT NULL COMMENT '文档生效时间',
                  title VARCHAR(512) DEFAULT NULL COMMENT '检索与展示标题',
                  doctor_name VARCHAR(128) DEFAULT NULL COMMENT '关联医生或专家名称',
                  source_priority INT NOT NULL DEFAULT 50 COMMENT '来源优先级',
                  keywords TEXT DEFAULT NULL COMMENT '关键词扩展',
                  extracted_text LONGTEXT DEFAULT NULL COMMENT '解析得到的全文文本',
                  extracted_characters INT NOT NULL DEFAULT 0 COMMENT '全文字符数',
                  chunk_count INT NOT NULL DEFAULT 0 COMMENT 'chunk 数量',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_knowledge_file_hash (hash),
                  KEY idx_knowledge_file_source (source),
                  KEY idx_knowledge_file_status (processing_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_chunk_index (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                  file_hash VARCHAR(64) NOT NULL COMMENT '所属知识文件哈希',
                  segment_id VARCHAR(96) NOT NULL COMMENT 'chunk 唯一标识',
                  segment_index INT NOT NULL COMMENT 'chunk 顺序号',
                  content MEDIUMTEXT NOT NULL COMMENT 'chunk 原始内容',
                  preview VARCHAR(255) DEFAULT NULL COMMENT 'chunk 简略摘要',
                  character_count INT NOT NULL DEFAULT 0 COMMENT 'chunk 字符数',
                  embedding MEDIUMTEXT DEFAULT NULL COMMENT 'embedding 向量序列化结果',
                  title VARCHAR(512) DEFAULT NULL COMMENT '继承自文件的标题',
                  doc_type VARCHAR(64) DEFAULT NULL COMMENT '文档类型',
                  department VARCHAR(128) DEFAULT NULL COMMENT '归属科室',
                  audience VARCHAR(32) DEFAULT NULL COMMENT '适用对象 PATIENT/DOCTOR/BOTH',
                  version VARCHAR(64) DEFAULT NULL COMMENT '文档版本',
                  effective_at TIMESTAMP NULL DEFAULT NULL COMMENT '文档生效时间',
                  status VARCHAR(32) DEFAULT NULL COMMENT '发布状态',
                  doctor_name VARCHAR(128) DEFAULT NULL COMMENT '关联医生或专家名称',
                  source_priority INT NOT NULL DEFAULT 50 COMMENT '来源优先级',
                  keywords TEXT DEFAULT NULL COMMENT '关键词扩展',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_knowledge_chunk_segment_id (segment_id),
                  KEY idx_knowledge_chunk_file_hash (file_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing("progress_percent", "ALTER TABLE knowledge_file_status ADD COLUMN progress_percent INT NOT NULL DEFAULT 0");
        addColumnIfMissing("current_batch", "ALTER TABLE knowledge_file_status ADD COLUMN current_batch INT NOT NULL DEFAULT 0");
        addColumnIfMissing("total_batches", "ALTER TABLE knowledge_file_status ADD COLUMN total_batches INT NOT NULL DEFAULT 0");
        addColumnIfMissing("embedding_model_name", "ALTER TABLE knowledge_file_status ADD COLUMN embedding_model_name VARCHAR(128) DEFAULT NULL");
        addColumnIfMissing("doc_type", "ALTER TABLE knowledge_file_status ADD COLUMN doc_type VARCHAR(64) DEFAULT NULL");
        addColumnIfMissing("department", "ALTER TABLE knowledge_file_status ADD COLUMN department VARCHAR(128) DEFAULT NULL");
        addColumnIfMissing("audience", "ALTER TABLE knowledge_file_status ADD COLUMN audience VARCHAR(32) DEFAULT NULL");
        addColumnIfMissing("version", "ALTER TABLE knowledge_file_status ADD COLUMN version VARCHAR(64) DEFAULT NULL");
        addColumnIfMissing("effective_at", "ALTER TABLE knowledge_file_status ADD COLUMN effective_at TIMESTAMP NULL DEFAULT NULL");
        addColumnIfMissing("title", "ALTER TABLE knowledge_file_status ADD COLUMN title VARCHAR(512) DEFAULT NULL");
        addColumnIfMissing("doctor_name", "ALTER TABLE knowledge_file_status ADD COLUMN doctor_name VARCHAR(128) DEFAULT NULL");
        addColumnIfMissing("source_priority", "ALTER TABLE knowledge_file_status ADD COLUMN source_priority INT NOT NULL DEFAULT 50");
        addColumnIfMissing("keywords", "ALTER TABLE knowledge_file_status ADD COLUMN keywords TEXT DEFAULT NULL");
        addColumnIfMissing("extracted_text", "ALTER TABLE knowledge_file_status ADD COLUMN extracted_text LONGTEXT DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("title", "ALTER TABLE knowledge_chunk_index ADD COLUMN title VARCHAR(512) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("doc_type", "ALTER TABLE knowledge_chunk_index ADD COLUMN doc_type VARCHAR(64) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("department", "ALTER TABLE knowledge_chunk_index ADD COLUMN department VARCHAR(128) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("audience", "ALTER TABLE knowledge_chunk_index ADD COLUMN audience VARCHAR(32) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("version", "ALTER TABLE knowledge_chunk_index ADD COLUMN version VARCHAR(64) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("effective_at", "ALTER TABLE knowledge_chunk_index ADD COLUMN effective_at TIMESTAMP NULL DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("status", "ALTER TABLE knowledge_chunk_index ADD COLUMN status VARCHAR(32) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("doctor_name", "ALTER TABLE knowledge_chunk_index ADD COLUMN doctor_name VARCHAR(128) DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("source_priority", "ALTER TABLE knowledge_chunk_index ADD COLUMN source_priority INT NOT NULL DEFAULT 50");
        addKnowledgeChunkColumnIfMissing("keywords", "ALTER TABLE knowledge_chunk_index ADD COLUMN keywords TEXT DEFAULT NULL");
        addKnowledgeChunkColumnIfMissing("embedding", "ALTER TABLE knowledge_chunk_index ADD COLUMN embedding MEDIUMTEXT DEFAULT NULL");
        createIndexIfMissing("idx_knowledge_chunk_status", "CREATE INDEX idx_knowledge_chunk_status ON knowledge_chunk_index(status)");
        createIndexIfMissing("idx_knowledge_chunk_doc_type", "CREATE INDEX idx_knowledge_chunk_doc_type ON knowledge_chunk_index(doc_type)");
        createIndexIfMissing("idx_knowledge_chunk_department", "CREATE INDEX idx_knowledge_chunk_department ON knowledge_chunk_index(department)");
        createIndexIfMissing("idx_knowledge_chunk_audience", "CREATE INDEX idx_knowledge_chunk_audience ON knowledge_chunk_index(audience)");
        createFulltextIndexIfMissing();
        dropKnowledgeChunkColumnIfExists("local_embedding");
        dropKnowledgeChunkColumnIfExists("online_embedding");
        normalizeGeneralDepartment();
        ensureColumnComments();
    }

    private void normalizeGeneralDepartment() {
        jdbcTemplate.update("""
                UPDATE knowledge_file_status
                SET department = ?
                WHERE department IS NULL OR TRIM(department) = '' OR department = ?
                """, DeptInfoService.GENERAL_DEPT_CODE, DeptInfoService.GENERAL_DEPT_NAME);
        jdbcTemplate.update("""
                UPDATE knowledge_chunk_index
                SET department = ?
                WHERE department IS NULL OR TRIM(department) = '' OR department = ?
                """, DeptInfoService.GENERAL_DEPT_CODE, DeptInfoService.GENERAL_DEPT_NAME);
    }

    private void ensureColumnComments() {
        updateKnowledgeFileColumnComment("id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID'");
        updateKnowledgeFileColumnComment("hash VARCHAR(64) NOT NULL COMMENT '知识文件内容哈希'");
        updateKnowledgeFileColumnComment("file_name VARCHAR(512) NOT NULL COMMENT '存储目录中的文件名'");
        updateKnowledgeFileColumnComment("original_filename VARCHAR(512) NOT NULL COMMENT '用户原始文件名'");
        updateKnowledgeFileColumnComment("source VARCHAR(32) NOT NULL COMMENT '文件来源 bundled/uploaded'");
        updateKnowledgeFileColumnComment("parser VARCHAR(128) DEFAULT NULL COMMENT '解析器名称'");
        updateKnowledgeFileColumnComment("extension VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名'");
        updateKnowledgeFileColumnComment("size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）'");
        updateKnowledgeFileColumnComment("editable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持在线编辑'");
        updateKnowledgeFileColumnComment("deletable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许删除'");
        updateKnowledgeFileColumnComment("processing_status VARCHAR(32) NOT NULL COMMENT '知识生命周期状态'");
        updateKnowledgeFileColumnComment("status_message VARCHAR(512) DEFAULT NULL COMMENT '状态说明'");
        updateKnowledgeFileColumnComment("progress_percent INT NOT NULL DEFAULT 0 COMMENT '处理进度百分比'");
        updateKnowledgeFileColumnComment("current_batch INT NOT NULL DEFAULT 0 COMMENT '当前 embedding 批次'");
        updateKnowledgeFileColumnComment("total_batches INT NOT NULL DEFAULT 0 COMMENT '总 embedding 批次'");
        updateKnowledgeFileColumnComment("embedding_model_name VARCHAR(128) DEFAULT NULL COMMENT '当前 embedding 模型名'");
        updateKnowledgeFileColumnComment("doc_type VARCHAR(64) DEFAULT NULL COMMENT '文档类型'");
        updateKnowledgeFileColumnComment("department VARCHAR(128) DEFAULT NULL COMMENT '归属科室'");
        updateKnowledgeFileColumnComment("audience VARCHAR(32) DEFAULT NULL COMMENT '适用对象 PATIENT/DOCTOR/BOTH'");
        updateKnowledgeFileColumnComment("version VARCHAR(64) DEFAULT NULL COMMENT '文档版本'");
        updateKnowledgeFileColumnComment("effective_at TIMESTAMP NULL DEFAULT NULL COMMENT '文档生效时间'");
        updateKnowledgeFileColumnComment("title VARCHAR(512) DEFAULT NULL COMMENT '检索与展示标题'");
        updateKnowledgeFileColumnComment("doctor_name VARCHAR(128) DEFAULT NULL COMMENT '关联医生或专家名称'");
        updateKnowledgeFileColumnComment("source_priority INT NOT NULL DEFAULT 50 COMMENT '来源优先级'");
        updateKnowledgeFileColumnComment("keywords TEXT DEFAULT NULL COMMENT '关键词扩展'");
        updateKnowledgeFileColumnComment("extracted_text LONGTEXT DEFAULT NULL COMMENT '解析得到的全文文本'");
        updateKnowledgeFileColumnComment("extracted_characters INT NOT NULL DEFAULT 0 COMMENT '全文字符数'");
        updateKnowledgeFileColumnComment("chunk_count INT NOT NULL DEFAULT 0 COMMENT 'chunk 数量'");
        updateKnowledgeFileColumnComment("created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        updateKnowledgeFileColumnComment("updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");

        updateKnowledgeChunkColumnComment("id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID'");
        updateKnowledgeChunkColumnComment("file_hash VARCHAR(64) NOT NULL COMMENT '所属知识文件哈希'");
        updateKnowledgeChunkColumnComment("segment_id VARCHAR(96) NOT NULL COMMENT 'chunk 唯一标识'");
        updateKnowledgeChunkColumnComment("segment_index INT NOT NULL COMMENT 'chunk 顺序号'");
        updateKnowledgeChunkColumnComment("content MEDIUMTEXT NOT NULL COMMENT 'chunk 原始内容'");
        updateKnowledgeChunkColumnComment("preview VARCHAR(255) DEFAULT NULL COMMENT 'chunk 简略摘要'");
        updateKnowledgeChunkColumnComment("character_count INT NOT NULL DEFAULT 0 COMMENT 'chunk 字符数'");
        updateKnowledgeChunkColumnComment("embedding MEDIUMTEXT DEFAULT NULL COMMENT 'embedding 向量序列化结果'");
        updateKnowledgeChunkColumnComment("title VARCHAR(512) DEFAULT NULL COMMENT '继承自文件的标题'");
        updateKnowledgeChunkColumnComment("doc_type VARCHAR(64) DEFAULT NULL COMMENT '文档类型'");
        updateKnowledgeChunkColumnComment("department VARCHAR(128) DEFAULT NULL COMMENT '归属科室'");
        updateKnowledgeChunkColumnComment("audience VARCHAR(32) DEFAULT NULL COMMENT '适用对象 PATIENT/DOCTOR/BOTH'");
        updateKnowledgeChunkColumnComment("version VARCHAR(64) DEFAULT NULL COMMENT '文档版本'");
        updateKnowledgeChunkColumnComment("effective_at TIMESTAMP NULL DEFAULT NULL COMMENT '文档生效时间'");
        updateKnowledgeChunkColumnComment("status VARCHAR(32) DEFAULT NULL COMMENT '发布状态'");
        updateKnowledgeChunkColumnComment("doctor_name VARCHAR(128) DEFAULT NULL COMMENT '关联医生或专家名称'");
        updateKnowledgeChunkColumnComment("source_priority INT NOT NULL DEFAULT 50 COMMENT '来源优先级'");
        updateKnowledgeChunkColumnComment("keywords TEXT DEFAULT NULL COMMENT '关键词扩展'");
        updateKnowledgeChunkColumnComment("created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        updateKnowledgeChunkColumnComment("updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
    }

    private void createIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'knowledge_chunk_index'
                          AND index_name = ?
                        """,
                Integer.class,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void createFulltextIndexIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'knowledge_chunk_index'
                          AND index_name = 'ft_knowledge_chunk_search'
                        """,
                Integer.class
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE knowledge_chunk_index
                    ADD FULLTEXT INDEX ft_knowledge_chunk_search (title, content, keywords)
                    """);
        }
    }

    private void addColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'knowledge_file_status'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addKnowledgeChunkColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'knowledge_chunk_index'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void dropKnowledgeChunkColumnIfExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'knowledge_chunk_index'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE knowledge_chunk_index DROP COLUMN " + columnName);
        }
    }

    private void updateKnowledgeFileColumnComment(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE knowledge_file_status MODIFY COLUMN " + columnDefinition);
    }

    private void updateKnowledgeChunkColumnComment(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE knowledge_chunk_index MODIFY COLUMN " + columnDefinition);
    }
}

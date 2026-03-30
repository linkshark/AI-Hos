package com.linkjb.aimed.config;

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
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  hash VARCHAR(64) NOT NULL,
                  file_name VARCHAR(512) NOT NULL,
                  original_filename VARCHAR(512) NOT NULL,
                  source VARCHAR(32) NOT NULL,
                  parser VARCHAR(128) DEFAULT NULL,
                  extension VARCHAR(32) DEFAULT NULL,
                  size BIGINT NOT NULL DEFAULT 0,
                  editable TINYINT(1) NOT NULL DEFAULT 0,
                  deletable TINYINT(1) NOT NULL DEFAULT 0,
                  processing_status VARCHAR(32) NOT NULL,
                  status_message VARCHAR(512) DEFAULT NULL,
                  progress_percent INT NOT NULL DEFAULT 0,
                  current_batch INT NOT NULL DEFAULT 0,
                  total_batches INT NOT NULL DEFAULT 0,
                  extracted_characters INT NOT NULL DEFAULT 0,
                  chunk_count INT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_knowledge_file_hash (hash),
                  KEY idx_knowledge_file_source (source),
                  KEY idx_knowledge_file_status (processing_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        addColumnIfMissing("progress_percent", "ALTER TABLE knowledge_file_status ADD COLUMN progress_percent INT NOT NULL DEFAULT 0");
        addColumnIfMissing("current_batch", "ALTER TABLE knowledge_file_status ADD COLUMN current_batch INT NOT NULL DEFAULT 0");
        addColumnIfMissing("total_batches", "ALTER TABLE knowledge_file_status ADD COLUMN total_batches INT NOT NULL DEFAULT 0");
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
}

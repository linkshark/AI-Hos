package com.linkjb.aimed.config.init;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Objects;

/**
 * 疾病标准表的幂等初始化器。
 *
 * 当前项目没有引入 Flyway/Liquibase，这里沿用现有启动补表策略；实际主数据由一次性导入脚本写入。
 */
@Component
public class MedicalStandardSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MedicalStandardSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        createMedicalConceptTable();
        createMedicalConceptAliasTable();
        createMedicalSymptomMappingTable();
        createMedicalDocMappingTable();
    }

    private void createMedicalConceptTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS medical_concept (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                    concept_code VARCHAR(128) NOT NULL COMMENT '项目内统一疾病概念编码，全局唯一',
                    standard_system VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '标准来源：NHC_CLINICAL / NHSA / ICD10_GB / HOSPITAL / SEEDED',
                    standard_code VARCHAR(64) NULL COMMENT '原始标准编码，例如国家临床版、医保版或 ICD-10 编码',
                    icd10_code VARCHAR(64) NULL COMMENT 'ICD-10 对照编码',
                    nhsa_code VARCHAR(64) NULL COMMENT '医保疾病诊断分类与代码',
                    icd11_uri VARCHAR(255) NULL COMMENT 'ICD-11 对照 URI，当前仅作扩展映射',
                    disease_name VARCHAR(255) NOT NULL COMMENT '中文疾病名称',
                    english_name VARCHAR(255) NULL COMMENT '英文名称或国际标准名称',
                    category_code VARCHAR(64) NULL COMMENT '疾病分类编码',
                    category_name VARCHAR(128) NULL COMMENT '疾病分类名称',
                    parent_concept_code VARCHAR(128) NULL COMMENT '父级疾病概念编码',
                    dept_code VARCHAR(64) NULL COMMENT '默认归属科室 deptCode',
                    is_leaf TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否叶子概念',
                    source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '数据来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
                    version_tag VARCHAR(64) NOT NULL DEFAULT 'CN-SEED-2026-04' COMMENT '数据版本标识',
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
                    raw_metadata_json LONGTEXT NULL COMMENT '原始导入数据或维护备注 JSON',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_medical_concept_code (concept_code),
                    KEY idx_medical_concept_standard_code (standard_system, standard_code),
                    KEY idx_medical_concept_icd10_code (icd10_code),
                    KEY idx_medical_concept_name (disease_name),
                    KEY idx_medical_concept_category (category_code),
                    KEY idx_medical_concept_parent (parent_concept_code),
                    KEY idx_medical_concept_dept (dept_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病标准概念表'
                """);
    }

    private void createMedicalConceptAliasTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS medical_concept_alias (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                    concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
                    alias VARCHAR(255) NOT NULL COMMENT '疾病别名、俗称、缩写或检索扩展词',
                    alias_type VARCHAR(32) NOT NULL DEFAULT 'SEARCH' COMMENT '别名类型：OFFICIAL / SYNONYM / LAYMAN / ABBR / SEARCH',
                    lang VARCHAR(16) NOT NULL DEFAULT 'zh' COMMENT '语言标记，默认 zh',
                    source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_medical_alias_concept_alias (concept_code, alias),
                    KEY idx_medical_alias_alias (alias),
                    KEY idx_medical_alias_concept (concept_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病标准别名表'
                """);
    }

    private void createMedicalSymptomMappingTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS medical_symptom_mapping (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                    concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
                    symptom_term VARCHAR(255) NULL COMMENT '症状词',
                    chief_complaint_term VARCHAR(255) NULL COMMENT '主诉词',
                    mapping_type VARCHAR(32) NOT NULL DEFAULT 'BOTH' COMMENT '映射类型：SYMPTOM / CHIEF_COMPLAINT / BOTH',
                    weight DECIMAL(6,2) NOT NULL DEFAULT 1.00 COMMENT '命中权重',
                    source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
                    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_medical_symptom_mapping (concept_code, symptom_term, chief_complaint_term),
                    KEY idx_medical_symptom_concept (concept_code),
                    KEY idx_medical_symptom_term (symptom_term),
                    KEY idx_medical_complaint_term (chief_complaint_term)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病症状主诉映射表'
                """);
    }

    private void createMedicalDocMappingTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS medical_doc_mapping (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                    knowledge_hash VARCHAR(128) NOT NULL COMMENT '知识文档 hash，对应 knowledge_file_status.hash',
                    concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
                    match_source VARCHAR(32) NOT NULL DEFAULT 'BACKFILL' COMMENT '映射来源：TITLE / KEYWORDS / ADMIN / BACKFILL',
                    confidence DECIMAL(6,4) NOT NULL DEFAULT 0.5000 COMMENT '映射置信度，范围 0~1',
                    sort_order INT NOT NULL DEFAULT 0 COMMENT '手动维护时的排序优先级，值越小越靠前',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_medical_doc_mapping (knowledge_hash, concept_code),
                    KEY idx_medical_doc_hash (knowledge_hash),
                    KEY idx_medical_doc_concept (concept_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档与疾病概念映射表'
                """);
        addColumnIfMissing("medical_doc_mapping", "sort_order",
                "ALTER TABLE medical_doc_mapping ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '手动维护时的排序优先级，值越小越靠前' AFTER confidence");
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (!Objects.equals(count, 0)) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }
}

CREATE DATABASE IF NOT EXISTS `aimed`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `aimed`;

CREATE TABLE IF NOT EXISTS `appointment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL COMMENT '预约人姓名',
  `id_card` VARCHAR(32) NOT NULL COMMENT '身份证号',
  `department` VARCHAR(64) NOT NULL COMMENT '科室名称',
  `date` VARCHAR(20) NOT NULL COMMENT '预约日期',
  `time` VARCHAR(20) NOT NULL COMMENT '预约时段',
  `doctor_name` VARCHAR(64) DEFAULT NULL COMMENT '医生姓名',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_unique_slot` (`username`, `id_card`, `department`, `date`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约挂号表';

CREATE TABLE IF NOT EXISTS `knowledge_file_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `hash` VARCHAR(64) NOT NULL COMMENT '文件内容哈希',
  `file_name` VARCHAR(512) NOT NULL COMMENT '本地存储文件名',
  `original_filename` VARCHAR(512) NOT NULL COMMENT '原始文件名',
  `source` VARCHAR(32) NOT NULL COMMENT '文件来源，bundled/uploaded',
  `parser` VARCHAR(128) DEFAULT NULL COMMENT '实际解析器',
  `extension` VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名',
  `size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
  `editable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许在线编辑',
  `deletable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许删除',
  `processing_status` VARCHAR(32) NOT NULL COMMENT 'PROCESSING/READY/FAILED',
  `status_message` VARCHAR(512) DEFAULT NULL COMMENT '状态说明',
  `progress_percent` INT NOT NULL DEFAULT 0 COMMENT '处理进度百分比',
  `current_batch` INT NOT NULL DEFAULT 0 COMMENT '当前批次',
  `total_batches` INT NOT NULL DEFAULT 0 COMMENT '总批次',
  `extracted_text` LONGTEXT DEFAULT NULL COMMENT '提取后的全文文本',
  `extracted_characters` INT NOT NULL DEFAULT 0 COMMENT '提取文本长度',
  `chunk_count` INT NOT NULL DEFAULT 0 COMMENT 'RAG 切分数量',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_file_hash` (`hash`),
  KEY `idx_knowledge_file_source` (`source`),
  KEY `idx_knowledge_file_status` (`processing_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文件状态表';

CREATE TABLE IF NOT EXISTS `knowledge_chunk_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_hash` VARCHAR(64) NOT NULL COMMENT '所属文件哈希',
  `segment_id` VARCHAR(96) NOT NULL COMMENT '切片唯一ID',
  `segment_index` INT NOT NULL COMMENT '切片序号',
  `content` MEDIUMTEXT NOT NULL COMMENT '切片全文',
  `preview` VARCHAR(255) DEFAULT NULL COMMENT '切片预览',
  `character_count` INT NOT NULL DEFAULT 0 COMMENT '切片字符数',
  `local_embedding` MEDIUMTEXT DEFAULT NULL COMMENT '本地 embedding 向量(JSON)',
  `online_embedding` MEDIUMTEXT DEFAULT NULL COMMENT '在线 embedding 向量(JSON)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_chunk_segment_id` (`segment_id`),
  KEY `idx_knowledge_chunk_file_hash` (`file_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库切片及向量表';

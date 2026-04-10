package com.linkjb.aimed.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class McpServerSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public McpServerSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mcp_server_config (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                  name VARCHAR(128) NOT NULL COMMENT 'MCP 服务名称',
                  transport_type VARCHAR(32) NOT NULL DEFAULT 'STREAMABLE_HTTP' COMMENT '传输类型',
                  base_url VARCHAR(512) NOT NULL COMMENT '远端服务地址',
                  description VARCHAR(255) DEFAULT NULL COMMENT '用途说明',
                  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
                  connect_timeout_ms INT NOT NULL DEFAULT 5000 COMMENT '连接超时毫秒',
                  request_headers_json TEXT DEFAULT NULL COMMENT '自定义请求头 JSON',
                  last_status VARCHAR(32) DEFAULT NULL COMMENT '最近一次测试状态',
                  last_error VARCHAR(512) DEFAULT NULL COMMENT '最近一次错误信息',
                  server_name VARCHAR(128) DEFAULT NULL COMMENT '远端声明的服务名',
                  server_version VARCHAR(64) DEFAULT NULL COMMENT '远端声明的服务版本',
                  tools_count INT DEFAULT NULL COMMENT '最近一次探测到的工具数',
                  last_checked_at DATETIME DEFAULT NULL COMMENT '最近一次测试时间',
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  KEY idx_mcp_server_enabled (enabled),
                  KEY idx_mcp_server_transport (transport_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}

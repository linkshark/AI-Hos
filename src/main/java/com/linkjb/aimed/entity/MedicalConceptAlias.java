package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 疾病概念别名实体。
 * 对应数据库表：medical_concept_alias，用于维护中文俗称、错别字、指南常用名和检索同义词。
 */
@TableName("medical_concept_alias")
public class MedicalConceptAlias {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 对应 medical_concept.concept_code。
     */
    private String conceptCode;
    /**
     * 别名文本，例如“感冒”“伤风”“血压高”。
     */
    private String alias;
    /**
     * 别名类型：OFFICIAL / SYNONYM / LAYMAN / ABBR / SEARCH。
     */
    private String aliasType;
    /**
     * 语言标记，默认 zh。
     */
    private String lang;
    /**
     * 来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL。
     */
    private String source;
    /**
     * 状态：ACTIVE / INACTIVE。
     */
    private String status;
    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConceptCode() {
        return conceptCode;
    }

    public void setConceptCode(String conceptCode) {
        this.conceptCode = conceptCode;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getAliasType() {
        return aliasType;
    }

    public void setAliasType(String aliasType) {
        this.aliasType = aliasType;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

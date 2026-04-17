package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 知识文档与疾病概念映射实体。
 * 对应数据库表：medical_doc_mapping，用于让 RAG 重排知道某份文档主要服务哪些疾病概念。
 */
@TableName("medical_doc_mapping")
public class MedicalDocMapping {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 知识文档 hash，对应 knowledge_file_status.hash。
     */
    private String knowledgeHash;
    /**
     * 对应 medical_concept.concept_code。
     */
    private String conceptCode;
    /**
     * 映射来源：TITLE / KEYWORDS / ADMIN / BACKFILL。
     */
    private String matchSource;
    /**
     * 映射置信度，范围 0~1。
     */
    private Double confidence;
    /**
     * 手动维护时的排序优先级，值越小越靠前。
     */
    private Integer sortOrder;
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

    public String getKnowledgeHash() {
        return knowledgeHash;
    }

    public void setKnowledgeHash(String knowledgeHash) {
        this.knowledgeHash = knowledgeHash;
    }

    public String getConceptCode() {
        return conceptCode;
    }

    public void setConceptCode(String conceptCode) {
        this.conceptCode = conceptCode;
    }

    public String getMatchSource() {
        return matchSource;
    }

    public void setMatchSource(String matchSource) {
        this.matchSource = matchSource;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

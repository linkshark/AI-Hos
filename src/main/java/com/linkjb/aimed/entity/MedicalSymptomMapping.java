package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 疾病症状/主诉映射实体。
 * 对应数据库表：medical_symptom_mapping，用于把中文症状、主诉与疾病概念关联起来。
 */
@TableName("medical_symptom_mapping")
public class MedicalSymptomMapping {

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
     * 症状词，例如“鼻塞”“咳嗽”“腹泻”。
     */
    private String symptomTerm;
    /**
     * 主诉词，例如“我感冒了”“拉肚子”“血压高”。
     */
    private String chiefComplaintTerm;
    /**
     * 映射类型：SYMPTOM / CHIEF_COMPLAINT / BOTH。
     */
    private String mappingType;
    /**
     * 命中权重，值越高越适合用于疾病标准化和检索重排。
     */
    private Double weight;
    /**
     * 来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL。
     */
    private String source;
    /**
     * 是否启用：1=启用，0=停用。
     */
    private Boolean enabled;
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

    public String getSymptomTerm() {
        return symptomTerm;
    }

    public void setSymptomTerm(String symptomTerm) {
        this.symptomTerm = symptomTerm;
    }

    public String getChiefComplaintTerm() {
        return chiefComplaintTerm;
    }

    public void setChiefComplaintTerm(String chiefComplaintTerm) {
        this.chiefComplaintTerm = chiefComplaintTerm;
    }

    public String getMappingType() {
        return mappingType;
    }

    public void setMappingType(String mappingType) {
        this.mappingType = mappingType;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 疾病概念实体。
 * 对应数据库表：medical_concept，承载国家临床版、医保版、ICD-10 或院内维护的中文疾病标准概念。
 */
@TableName("medical_concept")
public class MedicalConcept {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 项目内统一疾病概念编码，全局唯一，作为别名、症状映射和文档映射的关联键。
     */
    private String conceptCode;
    /**
     * 标准来源：NHC_CLINICAL / NHSA / ICD10_GB / HOSPITAL / SEEDED。
     */
    private String standardSystem;
    /**
     * 原始标准编码，例如国家临床版疾病编码、医保疾病编码或 ICD-10 编码。
     */
    private String standardCode;
    /**
     * ICD-10 对照编码；若当前标准就是 ICD-10，可与 standardCode 相同。
     */
    private String icd10Code;
    /**
     * 医保疾病诊断分类与代码；无医保对照时为空。
     */
    private String nhsaCode;
    /**
     * ICD-11 MMS 对照 URI；当前国内主链路不依赖该字段，仅作为扩展映射。
     */
    private String icd11Uri;
    /**
     * 中文疾病名称，是 query rewrite、后台检索和诊断展示的主名称。
     */
    private String diseaseName;
    /**
     * 英文名称或国际标准名称，主要用于后续跨标准对照。
     */
    private String englishName;
    /**
     * 疾病分类编码，例如章节、病种分类或本地高频分组。
     */
    private String categoryCode;
    /**
     * 疾病分类名称，例如呼吸系统疾病、消化系统疾病、肿瘤等。
     */
    private String categoryName;
    /**
     * 父级疾病概念编码，用于表达疾病分类层级；第一版只保留，不做完整树浏览。
     */
    private String parentConceptCode;
    /**
     * 默认归属科室 deptCode；可为空，后续可与科室树联动。
     */
    private String deptCode;
    /**
     * 是否叶子概念：1=叶子概念，0=分类节点。
     */
    private Boolean isLeaf;
    /**
     * 数据来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL。
     */
    private String source;
    /**
     * 版本标识，例如 CN-SEED-2026-04 或外部标准版本号。
     */
    private String versionTag;
    /**
     * 状态：ACTIVE / INACTIVE。
     */
    private String status;
    /**
     * 原始导入数据或维护备注 JSON，便于排障和后续补字段。
     */
    private String rawMetadataJson;
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

    public String getStandardSystem() {
        return standardSystem;
    }

    public void setStandardSystem(String standardSystem) {
        this.standardSystem = standardSystem;
    }

    public String getStandardCode() {
        return standardCode;
    }

    public void setStandardCode(String standardCode) {
        this.standardCode = standardCode;
    }

    public String getIcd10Code() {
        return icd10Code;
    }

    public void setIcd10Code(String icd10Code) {
        this.icd10Code = icd10Code;
    }

    public String getNhsaCode() {
        return nhsaCode;
    }

    public void setNhsaCode(String nhsaCode) {
        this.nhsaCode = nhsaCode;
    }

    public String getIcd11Uri() {
        return icd11Uri;
    }

    public void setIcd11Uri(String icd11Uri) {
        this.icd11Uri = icd11Uri;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getParentConceptCode() {
        return parentConceptCode;
    }

    public void setParentConceptCode(String parentConceptCode) {
        this.parentConceptCode = parentConceptCode;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public Boolean getLeaf() {
        return isLeaf;
    }

    public void setLeaf(Boolean leaf) {
        isLeaf = leaf;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawMetadataJson() {
        return rawMetadataJson;
    }

    public void setRawMetadataJson(String rawMetadataJson) {
        this.rawMetadataJson = rawMetadataJson;
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

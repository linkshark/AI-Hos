package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_chunk_index")
public class KnowledgeChunkIndex {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属知识文件 hash。 */
    private String fileHash;
    /** chunk 唯一标识，通常为 hash + segment 序号。 */
    private String segmentId;
    /** chunk 顺序号，从 1 开始。 */
    private Integer segmentIndex;
    /** chunk 原始内容。 */
    private String content;
    /** chunk 简略摘要，用于管理台和引用预览。 */
    private String preview;
    /** chunk 字符数。 */
    private Integer characterCount;
    /** embedding 向量序列化结果。 */
    private String embedding;
    /** 继承自文件级 metadata 的标题。 */
    private String title;
    /** 文件/片段文档类型。 */
    private String docType;
    /** 文件/片段归属科室。 */
    private String department;
    /** 适用对象：PATIENT / DOCTOR / BOTH。 */
    private String audience;
    /** 文档版本。 */
    private String version;
    /** 文档生效时间。 */
    private LocalDateTime effectiveAt;
    /** 发布状态，仅 PUBLISHED 参与检索。 */
    private String status;
    /** 关联医生/专家名称。 */
    private String doctorName;
    /** 来源优先级。 */
    private Integer sourcePriority;
    /** 用于关键词召回的扩展词。 */
    private String keywords;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public Integer getCharacterCount() {
        return characterCount;
    }

    public void setCharacterCount(Integer characterCount) {
        this.characterCount = characterCount;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public Integer getSourcePriority() {
        return sourcePriority;
    }

    public void setSourcePriority(Integer sourcePriority) {
        this.sourcePriority = sourcePriority;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
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

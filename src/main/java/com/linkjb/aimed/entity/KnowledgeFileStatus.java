package com.linkjb.aimed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_file_status")
public class KnowledgeFileStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 文件内容哈希，作为知识文件主标识。 */
    private String hash;
    /** 存储目录中的文件名，上传文件通常带 hash 前缀。 */
    private String fileName;
    /** 原始展示名，用于管理台和引用展示。 */
    private String originalFilename;
    /** 文件来源：bundled / uploaded。 */
    private String source;
    /** 解析器名称，便于排查抽取质量。 */
    private String parser;
    /** 文件扩展名。 */
    private String extension;
    /** 原始文件大小，单位字节。 */
    private Long size;
    /** 是否支持在线编辑原文。 */
    private Boolean editable;
    /** 是否允许在管理台删除。 */
    private Boolean deletable;
    /** 知识生命周期状态：DRAFT / PROCESSING / READY / PUBLISHED / FAILED / ARCHIVED / PENDING_SYNC。 */
    private String processingStatus;
    /** 当前处理状态的人类可读说明。 */
    private String statusMessage;
    /** 后台处理进度百分比。 */
    private Integer progressPercent;
    /** 当前处理到第几批 embedding。 */
    private Integer currentBatch;
    /** 总 batch 数，用于显示处理进度。 */
    private Integer totalBatches;
    /** 当前文件索引使用的 embedding 模型名。 */
    private String embeddingModelName;
    /** 文档类型，用于过滤和重排。 */
    private String docType;
    /** 归属科室或学科。 */
    private String department;
    /** 适用对象：PATIENT / DOCTOR / BOTH。 */
    private String audience;
    /** 业务版本，例如 v1 / 2024版。 */
    private String version;
    /** 文档生效时间。 */
    private LocalDateTime effectiveAt;
    /** 检索和展示使用的标题。 */
    private String title;
    /** 关联医生/专家名称。 */
    private String doctorName;
    /** 来源优先级，值越高排序越靠前。 */
    private Integer sourcePriority;
    /** 关键词词典种子，供关键词召回使用。 */
    private String keywords;
    /** 解析得到的全文文本。 */
    private String extractedText;
    /** 解析得到的总字符数。 */
    private Integer extractedCharacters;
    /** 当前文件切出的 chunk 数。 */
    private Integer chunkCount;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getParser() {
        return parser;
    }

    public void setParser(String parser) {
        this.parser = parser;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public Boolean getDeletable() {
        return deletable;
    }

    public void setDeletable(Boolean deletable) {
        this.deletable = deletable;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getCurrentBatch() {
        return currentBatch;
    }

    public void setCurrentBatch(Integer currentBatch) {
        this.currentBatch = currentBatch;
    }

    public Integer getTotalBatches() {
        return totalBatches;
    }

    public void setTotalBatches(Integer totalBatches) {
        this.totalBatches = totalBatches;
    }

    public String getEmbeddingModelName() {
        return embeddingModelName;
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Integer getExtractedCharacters() {
        return extractedCharacters;
    }

    public void setExtractedCharacters(Integer extractedCharacters) {
        this.extractedCharacters = extractedCharacters;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
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

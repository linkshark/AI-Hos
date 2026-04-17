package com.linkjb.aimed.entity.dto.request;

public class KnowledgeUpdateRequest {
    /** 编辑后的正文内容；只改 metadata 时可为空。 */
    private String content;
    /** 文档类型。 */
    private String docType;
    /** 科室归属。 */
    private String department;
    /** 适用对象。 */
    private String audience;
    /** 文档版本。 */
    private String version;
    /** 文档生效时间，前端以 datetime-local 字符串传入。 */
    private String effectiveAt;
    /** 检索/展示标题。 */
    private String title;
    /** 关联医生或专家名称。 */
    private String doctorName;
    /** 来源优先级，越大排序越靠前。 */
    private Integer sourcePriority;
    /** 关键词扩展。 */
    private String keywords;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(String effectiveAt) {
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
}

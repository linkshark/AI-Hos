package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.KnowledgeMetadataBackfillFieldChange;
import com.linkjb.aimed.entity.KnowledgeFileStatus;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeMetadataService {

    static final String AUDIENCE_BOTH = "BOTH";
    static final String DOC_TYPE_HOSPITAL_OVERVIEW = "HOSPITAL_OVERVIEW";
    static final String DOC_TYPE_DEPARTMENT = "DEPARTMENT";
    static final String DOC_TYPE_DOCTOR = "DOCTOR";
    static final String DOC_TYPE_GUIDE = "GUIDE";
    static final String DOC_TYPE_PROCESS = "PROCESS";
    static final String DOC_TYPE_NOTICE = "NOTICE";
    static final String DOC_TYPE_DEVICE = "DEVICE";

    public KnowledgeFileMetadata buildDefaultMetadata(String originalFilename, String source, String extension) {
        String normalizedName = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        String docType = DOC_TYPE_HOSPITAL_OVERVIEW;
        if (normalizedName.contains("科室") || normalizedName.contains("门诊") || normalizedName.contains("中心")) {
            docType = DOC_TYPE_DEPARTMENT;
        } else if (normalizedName.contains("医生") || normalizedName.contains("专家") || normalizedName.contains("院士")) {
            docType = DOC_TYPE_DOCTOR;
        } else if (normalizedName.contains("指南") || normalizedName.contains("共识") || normalizedName.contains("规范")) {
            docType = DOC_TYPE_GUIDE;
        } else if (normalizedName.contains("流程") || normalizedName.contains("预约") || normalizedName.contains("就诊") || normalizedName.contains("挂号")) {
            docType = DOC_TYPE_PROCESS;
        } else if (normalizedName.contains("公告") || normalizedName.contains("通知")) {
            docType = DOC_TYPE_NOTICE;
        } else if (normalizedName.contains("设备")) {
            docType = DOC_TYPE_DEVICE;
        }
        String title = StringUtils.hasText(originalFilename) ? originalFilename : "未命名知识文件";
        int sourcePriority = "bundled".equals(source) ? 100 : 60;
        String department = KnowledgeSearchLexicon.inferDepartment(title);
        String doctorName = KnowledgeSearchLexicon.inferDoctorName(title);
        String keywordSeed = KnowledgeSearchLexicon.buildKeywordSeed(title, extension, docType, department, doctorName);
        return new KnowledgeFileMetadata(
                docType,
                department,
                AUDIENCE_BOTH,
                "v1",
                null,
                title,
                doctorName,
                sourcePriority,
                keywordSeed
        );
    }

    public KnowledgeFileMetadata mergeMetadata(KnowledgeFileMetadata base, KnowledgeFileMetadata override) {
        if (override == null) {
            return base;
        }
        return new KnowledgeFileMetadata(
                pickText(override.docType(), base.docType()),
                pickText(override.department(), base.department()),
                pickText(override.audience(), base.audience()),
                pickText(override.version(), base.version()),
                override.effectiveAt() == null ? base.effectiveAt() : override.effectiveAt(),
                pickText(override.title(), base.title()),
                pickText(override.doctorName(), base.doctorName()),
                override.sourcePriority() == null ? base.sourcePriority() : override.sourcePriority(),
                pickText(override.keywords(), base.keywords())
        );
    }

    public LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    public Metadata buildSegmentMetadata(String hash,
                                         String source,
                                         String extension,
                                         String status,
                                         KnowledgeFileMetadata fileMetadata) {
        Metadata metadata = new Metadata();
        putMetadataIfPresent(metadata, "knowledge_hash", hash);
        putMetadataIfPresent(metadata, "knowledge_source", source);
        putMetadataIfPresent(metadata, "knowledge_extension", extension);
        putMetadataIfPresent(metadata, "status", status);
        putMetadataIfPresent(metadata, "doc_type", fileMetadata.docType());
        putMetadataIfPresent(metadata, "department", fileMetadata.department());
        putMetadataIfPresent(metadata, "audience", fileMetadata.audience());
        putMetadataIfPresent(metadata, "version", fileMetadata.version());
        putMetadataIfPresent(metadata, "title", fileMetadata.title());
        putMetadataIfPresent(metadata, "document_name", fileMetadata.title());
        putMetadataIfPresent(metadata, "doctor_name", fileMetadata.doctorName());
        if (fileMetadata.sourcePriority() != null) {
            metadata.put("source_priority", fileMetadata.sourcePriority());
        }
        putMetadataIfPresent(metadata, "keywords", fileMetadata.keywords());
        if (fileMetadata.effectiveAt() != null) {
            metadata.put("effective_at", fileMetadata.effectiveAt().toString());
        }
        return metadata;
    }

    public List<TextSegment> enrichSegments(List<TextSegment> segments,
                                            String hash,
                                            String source,
                                            String extension,
                                            String status,
                                            KnowledgeFileMetadata fileMetadata) {
        List<TextSegment> enriched = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            enriched.add(TextSegment.from(segment.text(), buildSegmentMetadata(hash, source, extension, status, fileMetadata)));
        }
        return enriched;
    }

    public KnowledgeFileMetadata toMetadata(KnowledgeFileStatus status) {
        if (status == null) {
            return null;
        }
        return new KnowledgeFileMetadata(
                status.getDocType(),
                status.getDepartment(),
                status.getAudience(),
                status.getVersion(),
                status.getEffectiveAt(),
                status.getTitle(),
                status.getDoctorName(),
                status.getSourcePriority(),
                status.getKeywords()
        );
    }

    public MetadataBackfillPlan buildBackfillPlan(KnowledgeFileStatus status) {
        KnowledgeFileMetadata current = toMetadata(status);
        KnowledgeFileMetadata inferred = buildDefaultMetadata(status.getOriginalFilename(), status.getSource(), status.getExtension());
        List<KnowledgeMetadataBackfillFieldChange> changes = new ArrayList<>();

        String title = resolveBackfillValue("title", current.title(), inferred.title(), this::shouldBackfillTitle, changes);
        String department = resolveBackfillValue("department", current.department(), inferred.department(), this::shouldBackfillDepartment, changes);
        String doctorName = resolveBackfillValue("doctorName", current.doctorName(), inferred.doctorName(), this::shouldBackfillDoctorName, changes);
        String keywords = resolveBackfillValue("keywords", current.keywords(), inferred.keywords(), this::shouldBackfillKeywords, changes);

        List<String> skippedReasons = changes.isEmpty()
                ? List.of("没有可回填的 metadata 字段")
                : List.of();

        return new MetadataBackfillPlan(
                new KnowledgeFileMetadata(
                        current.docType(),
                        department,
                        current.audience(),
                        current.version(),
                        current.effectiveAt(),
                        title,
                        doctorName,
                        current.sourcePriority(),
                        keywords
                ),
                changes,
                skippedReasons
        );
    }

    private String resolveBackfillValue(String field,
                                        String existingValue,
                                        String inferredValue,
                                        java.util.function.Predicate<String> shouldBackfill,
                                        List<KnowledgeMetadataBackfillFieldChange> changes) {
        if (!shouldBackfill.test(existingValue) || !StringUtils.hasText(inferredValue)) {
            return existingValue;
        }
        String normalizedBefore = normalize(existingValue);
        String normalizedAfter = normalize(inferredValue);
        if (normalizedAfter.equals(normalizedBefore)) {
            return existingValue;
        }
        changes.add(new KnowledgeMetadataBackfillFieldChange(field, normalizedBefore, normalizedAfter));
        return inferredValue;
    }

    private boolean shouldBackfillTitle(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() || "未命名知识文件".equals(normalized);
    }

    private boolean shouldBackfillDepartment(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() || "通用".equals(normalized);
    }

    private boolean shouldBackfillDoctorName(String value) {
        return !StringUtils.hasText(value);
    }

    private boolean shouldBackfillKeywords(String value) {
        return !StringUtils.hasText(value);
    }

    private void putMetadataIfPresent(Metadata metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String pickText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record MetadataBackfillPlan(KnowledgeFileMetadata metadata,
                                       List<KnowledgeMetadataBackfillFieldChange> changes,
                                       List<String> skippedReasons) {
    }
}

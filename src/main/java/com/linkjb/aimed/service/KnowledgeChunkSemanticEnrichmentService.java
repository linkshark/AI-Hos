package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class KnowledgeChunkSemanticEnrichmentService {

    public static final String METADATA_SEMANTIC_SUMMARY = "semantic_summary";
    public static final String METADATA_SEMANTIC_KEYWORDS = "semantic_keywords";
    public static final String METADATA_SECTION_ROLE = "section_role";
    public static final String METADATA_MEDICAL_ENTITIES_JSON = "medical_entities_json";
    public static final String METADATA_TARGET_QUESTIONS_JSON = "target_questions_json";
    public static final String METADATA_SEMANTIC_ENRICHED_AT = "semantic_enriched_at";
    public static final String METADATA_SEMANTIC_ENRICHMENT_MODEL = "semantic_enrichment_model";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChunkSemanticEnrichmentService.class);
    private static final String SYSTEM_PROMPT = """
            你是医疗知识库 chunk 语义增强器。
            任务：针对单个知识段落生成稳定、简洁、可检索的结构化特征。
            约束：
            1. 只基于输入内容总结，不能凭空添加未出现的病名、药名、结论。
            2. 只返回一个 JSON 对象，不要输出解释、markdown、代码块。
            3. 字段固定：
               semanticSummary(string)
               semanticKeywords(array of string)
               sectionRole(string)
               medicalEntities(array of string)
               targetQuestions(array of string)
            4. semanticSummary 控制在 40 个字内。
            5. semanticKeywords 4 到 8 个，优先疾病、症状、检查、治疗、流程词。
            6. sectionRole 只允许这些值之一：
               诊断, 临床表现, 检查, 治疗, 用药, 手术, 随访, 注意事项, 预防, 挂号流程, 就诊流程, 科室介绍, 医生介绍, 设备说明, 通用
            7. targetQuestions 2 到 4 条，必须是用户可能会问的短问法。
            """;

    private final ObjectMapper objectMapper;
    private final ChatModel localChatModel;
    private final boolean enabled;
    private final int maxInputChars;
    private final int maxKeywords;
    private final String modelName;

    public KnowledgeChunkSemanticEnrichmentService(ObjectMapper objectMapper,
                                                   @Qualifier("localChatModel") ChatModel localChatModel,
                                                   @Value("${app.knowledge-base.semantic-enrichment.enabled:true}") boolean enabled,
                                                   @Value("${app.knowledge-base.semantic-enrichment.max-input-chars:1400}") int maxInputChars,
                                                   @Value("${app.knowledge-base.semantic-enrichment.max-keywords:8}") int maxKeywords,
                                                   @Value("${app.knowledge-base.semantic-enrichment.model-name:${app.local-chat.model-name:local-chat}}") String modelName) {
        this.objectMapper = objectMapper;
        this.localChatModel = localChatModel;
        this.enabled = enabled;
        this.maxInputChars = Math.max(400, maxInputChars);
        this.maxKeywords = Math.max(4, maxKeywords);
        this.modelName = modelName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<TextSegment> enrichSegments(List<TextSegment> segments, String documentTitle, String docType) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<TextSegment> enriched = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            enriched.add(enrichSegment(segment, documentTitle, docType));
        }
        return enriched;
    }

    public boolean needsSemanticUpgrade(List<KnowledgeChunkIndex> chunkIndexes) {
        if (chunkIndexes == null || chunkIndexes.isEmpty()) {
            return false;
        }
        return chunkIndexes.stream().anyMatch(index ->
                !StringUtils.hasText(index.getSemanticSummary())
                        || !StringUtils.hasText(index.getSemanticKeywords())
                        || !StringUtils.hasText(index.getSectionRole()));
    }

    private TextSegment enrichSegment(TextSegment segment, String documentTitle, String docType) {
        if (segment == null || !StringUtils.hasText(segment.text())) {
            return segment;
        }
        SegmentEnrichment enrichment = resolveEnrichment(segment, documentTitle, docType);
        var metadata = segment.metadata() == null ? new dev.langchain4j.data.document.Metadata() : segment.metadata().copy();
        putIfPresent(metadata, METADATA_SEMANTIC_SUMMARY, enrichment.semanticSummary());
        putIfPresent(metadata, METADATA_SEMANTIC_KEYWORDS, joinKeywords(enrichment.semanticKeywords()));
        putIfPresent(metadata, METADATA_SECTION_ROLE, enrichment.sectionRole());
        putIfPresent(metadata, METADATA_MEDICAL_ENTITIES_JSON, writeList(enrichment.medicalEntities()));
        putIfPresent(metadata, METADATA_TARGET_QUESTIONS_JSON, writeList(enrichment.targetQuestions()));
        metadata.put(METADATA_SEMANTIC_ENRICHED_AT, LocalDateTime.now().toString());
        putIfPresent(metadata, METADATA_SEMANTIC_ENRICHMENT_MODEL, enabled ? modelName : "HEURISTIC");
        return TextSegment.from(segment.text(), metadata);
    }

    private SegmentEnrichment resolveEnrichment(TextSegment segment, String documentTitle, String docType) {
        SegmentEnrichment heuristic = heuristicEnrichment(segment, documentTitle, docType);
        if (!enabled || segment.text().length() > maxInputChars) {
            return heuristic;
        }
        try {
            ChatResponse response = localChatModel.chat(buildMessages(segment, documentTitle, docType));
            String payload = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
            SegmentEnrichment candidate = parseModelResult(payload, heuristic);
            return candidate == null ? heuristic : candidate;
        } catch (Exception exception) {
            log.debug("knowledge.semantic.enrichment.failed title={} docType={}", documentTitle, docType, exception);
            return heuristic;
        }
    }

    private List<ChatMessage> buildMessages(TextSegment segment, String documentTitle, String docType) {
        String sectionTitle = segment.metadata() == null ? null : segment.metadata().getString(KnowledgeIndexingService.METADATA_SECTION_TITLE);
        String segmentKind = segment.metadata() == null ? null : segment.metadata().getString(KnowledgeIndexingService.METADATA_SEGMENT_KIND);
        String userPrompt = """
                documentTitle: %s
                docType: %s
                sectionTitle: %s
                segmentKind: %s
                chunkText: %s
                """.formatted(
                safeValue(documentTitle),
                safeValue(docType),
                safeValue(sectionTitle),
                safeValue(segmentKind),
                trimToLength(segment.text(), maxInputChars)
        );
        return List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt));
    }

    private SegmentEnrichment parseModelResult(String payload, SegmentEnrichment fallback) {
        if (!StringUtils.hasText(payload)) {
            return fallback;
        }
        try {
            EnrichmentPayload parsed = objectMapper.readValue(payload.trim(), EnrichmentPayload.class);
            String summary = trimToLength(parsed.semanticSummary(), 40);
            List<String> keywords = normalizeList(parsed.semanticKeywords(), maxKeywords);
            String role = normalizeSectionRole(parsed.sectionRole(), fallback.sectionRole());
            List<String> medicalEntities = normalizeList(parsed.medicalEntities(), maxKeywords);
            List<String> targetQuestions = normalizeList(parsed.targetQuestions(), 4);
            if (!StringUtils.hasText(summary) || keywords.isEmpty()) {
                return fallback;
            }
            return new SegmentEnrichment(summary, keywords, role, medicalEntities, targetQuestions);
        } catch (Exception exception) {
            log.debug("knowledge.semantic.enrichment.parse.failed payload={}", payload, exception);
            return fallback;
        }
    }

    private SegmentEnrichment heuristicEnrichment(TextSegment segment, String documentTitle, String docType) {
        String text = segment.text();
        String sectionTitle = segment.metadata() == null ? null : segment.metadata().getString(KnowledgeIndexingService.METADATA_SECTION_TITLE);
        Set<String> keywords = new LinkedHashSet<>();
        if (StringUtils.hasText(sectionTitle)) {
            keywords.add(sectionTitle.trim());
        }
        KnowledgeSearchLexicon.extractCoreQueryTokens(text).forEach(keywords::add);
        KnowledgeSearchLexicon.detectMedicalAnchors(text).forEach(keywords::add);
        KnowledgeSearchLexicon.extractMedicalRewriteHints(text).stream()
                .filter(StringUtils::hasText)
                .filter(item -> item.length() <= 16)
                .limit(maxKeywords)
                .forEach(keywords::add);
        addDocTypeKeywords(keywords, docType);

        List<String> normalizedKeywords = keywords.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(maxKeywords)
                .toList();
        List<String> medicalEntities = KnowledgeSearchLexicon.detectMedicalAnchors(text).stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(maxKeywords)
                .toList();
        String sectionRole = normalizeSectionRole(detectSectionRole(sectionTitle, text, docType), "通用");
        String semanticSummary = buildSummaryLine(sectionTitle, text, normalizedKeywords, documentTitle);
        List<String> targetQuestions = buildTargetQuestions(sectionRole, normalizedKeywords, medicalEntities, documentTitle);
        return new SegmentEnrichment(
                semanticSummary,
                normalizedKeywords,
                sectionRole,
                medicalEntities,
                targetQuestions
        );
    }

    private void addDocTypeKeywords(Set<String> keywords, String docType) {
        if (!StringUtils.hasText(docType)) {
            return;
        }
        switch (docType.trim().toUpperCase(Locale.ROOT)) {
            case KnowledgeMetadataService.DOC_TYPE_GUIDE -> keywords.add("指南");
            case KnowledgeMetadataService.DOC_TYPE_PROCESS -> keywords.add("流程");
            case KnowledgeMetadataService.DOC_TYPE_NOTICE -> keywords.add("通知");
            case KnowledgeMetadataService.DOC_TYPE_DOCTOR -> keywords.add("医生");
            case KnowledgeMetadataService.DOC_TYPE_DEPARTMENT -> keywords.add("科室");
            case KnowledgeMetadataService.DOC_TYPE_DEVICE -> keywords.add("设备");
            default -> {
            }
        }
    }

    private String detectSectionRole(String sectionTitle, String text, String docType) {
        String lower = safeValue(sectionTitle) + "\n" + safeValue(text);
        if (containsAny(lower, "挂号流程", "预约方式", "预约流程")) {
            return "挂号流程";
        }
        if (containsAny(lower, "就诊流程", "就医流程", "流程步骤", "所需材料")) {
            return "就诊流程";
        }
        if (containsAny(lower, "临床表现", "症状", "主诉", "表现")) {
            return "临床表现";
        }
        if (containsAny(lower, "诊断", "鉴别诊断")) {
            return "诊断";
        }
        if (containsAny(lower, "检查", "检验", "化验", "影像")) {
            return "检查";
        }
        if (containsAny(lower, "治疗", "方案", "处置")) {
            return "治疗";
        }
        if (containsAny(lower, "用药", "药物", "服药")) {
            return "用药";
        }
        if (containsAny(lower, "手术", "切除", "术后")) {
            return "手术";
        }
        if (containsAny(lower, "随访", "复查")) {
            return "随访";
        }
        if (containsAny(lower, "注意事项", "警示", "尽快就医", "禁忌")) {
            return "注意事项";
        }
        if (containsAny(lower, "预防", "防治")) {
            return "预防";
        }
        if (KnowledgeMetadataService.DOC_TYPE_DOCTOR.equals(docType)) {
            return "医生介绍";
        }
        if (KnowledgeMetadataService.DOC_TYPE_DEPARTMENT.equals(docType)) {
            return "科室介绍";
        }
        if (KnowledgeMetadataService.DOC_TYPE_DEVICE.equals(docType)) {
            return "设备说明";
        }
        return "通用";
    }

    private List<String> buildTargetQuestions(String sectionRole,
                                              List<String> keywords,
                                              List<String> medicalEntities,
                                              String documentTitle) {
        String anchor = !medicalEntities.isEmpty()
                ? medicalEntities.get(0)
                : (!keywords.isEmpty() ? keywords.get(0) : trimToLength(documentTitle, 12));
        if (!StringUtils.hasText(anchor)) {
            anchor = "这个问题";
        }
        List<String> questions = switch (sectionRole) {
            case "诊断" -> List.of(anchor + "怎么诊断", anchor + "需要做哪些检查");
            case "临床表现" -> List.of(anchor + "有哪些症状", anchor + "典型表现是什么");
            case "检查" -> List.of(anchor + "要做什么检查", anchor + "检查前要准备什么");
            case "治疗" -> List.of(anchor + "怎么治疗", anchor + "治疗方案有哪些");
            case "用药" -> List.of(anchor + "用什么药", anchor + "药物怎么用");
            case "手术" -> List.of(anchor + "需要手术吗", anchor + "手术怎么做");
            case "随访" -> List.of(anchor + "多久复查", anchor + "随访要注意什么");
            case "注意事项" -> List.of(anchor + "要注意什么", anchor + "什么时候尽快就医");
            case "预防" -> List.of(anchor + "怎么预防", anchor + "怎么降低风险");
            case "挂号流程" -> List.of(anchor + "怎么挂号", anchor + "挂号流程是什么");
            case "就诊流程" -> List.of(anchor + "就诊流程是什么", anchor + "需要准备什么材料");
            case "科室介绍" -> List.of(anchor + "看什么病", anchor + "这个科室主要做什么");
            case "医生介绍" -> List.of(anchor + "擅长看什么", anchor + "适合什么问题");
            case "设备说明" -> List.of(anchor + "适用于什么情况", anchor + "检查前要注意什么");
            default -> List.of(anchor + "是什么", anchor + "主要讲什么");
        };
        return normalizeList(questions, 4);
    }

    private String buildSummaryLine(String sectionTitle, String text, List<String> keywords, String documentTitle) {
        if (StringUtils.hasText(sectionTitle)) {
            return trimToLength(sectionTitle.trim(), 40);
        }
        String firstSentence = text.split("[。；;\\n]")[0].trim();
        if (StringUtils.hasText(firstSentence)) {
            return trimToLength(firstSentence, 40);
        }
        if (!keywords.isEmpty()) {
            return trimToLength(String.join("、", keywords), 40);
        }
        return trimToLength(documentTitle, 40);
    }

    private List<String> normalizeList(List<String> items, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(this::safeValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(Math.max(1, limit))
                .toList();
    }

    private String normalizeSectionRole(String rawRole, String fallback) {
        String role = safeValue(rawRole);
        if (!StringUtils.hasText(role)) {
            return fallback;
        }
        return switch (role) {
            case "诊断", "临床表现", "检查", "治疗", "用药", "手术", "随访", "注意事项",
                    "预防", "挂号流程", "就诊流程", "科室介绍", "医生介绍", "设备说明", "通用" -> role;
            default -> fallback;
        };
    }

    private String joinKeywords(List<String> keywords) {
        return keywords == null || keywords.isEmpty() ? null : String.join(" ", keywords);
    }

    private String writeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            return null;
        }
    }

    private void putIfPresent(dev.langchain4j.data.document.Metadata metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToLength(String value, int maxLength) {
        String normalized = safeValue(value).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)).trim();
    }

    private boolean containsAny(String value, String... tokens) {
        String lower = safeValue(value).toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record EnrichmentPayload(String semanticSummary,
                                     List<String> semanticKeywords,
                                     String sectionRole,
                                     List<String> medicalEntities,
                                     List<String> targetQuestions) {
    }

    private record SegmentEnrichment(String semanticSummary,
                                     List<String> semanticKeywords,
                                     String sectionRole,
                                     List<String> medicalEntities,
                                     List<String> targetQuestions) {
    }
}

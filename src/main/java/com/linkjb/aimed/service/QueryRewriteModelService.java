package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalModelRewriteCandidate;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QueryRewriteModelService {

    enum ModelRewriteMode {
        OFF,
        DIAGNOSTIC_ONLY,
        SHADOW_COMPARE,
        ACTIVE
    }

    record ModelRewriteResult(KnowledgeRetrievalModelRewriteCandidate candidate,
                              boolean accepted) {
    }

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteModelService.class);
    private static final String SYSTEM_PROMPT = """
            你是医疗知识库检索 query 规范化器。
            任务：把用户当前问题和最近几轮主诉摘要，改写成适合知识检索的短 query。
            约束：
            1. 只能基于输入内容重写，不能新增输入里未出现的强诊断结论。
            2. 不要输出解释，不要输出 markdown。
            3. 只能返回一个 JSON 对象，字段固定：
               effectiveQuery(string)
               medicalAnchors(array of string)
               intentType(string)
               confidence(number)
               droppedNoise(array of string)
            4. effectiveQuery 控制在 40 个字内，优先保留疾病/症状、年龄、体温、时长、用药意图。
            """;

    private final ObjectMapper objectMapper;
    private final ChatModel queryRewriteChatModel;
    private final boolean enabled;
    private final String platform;
    private final ModelRewriteMode mode;

    public QueryRewriteModelService(ObjectMapper objectMapper,
                                    @Qualifier("queryRewriteChatModel") ChatModel queryRewriteChatModel,
                                    @Value("${app.rag.query-rewrite.model.enabled:false}") boolean enabled,
                                    @Value("${app.rag.query-rewrite.model.platform:ONLINE_FAST}") String platform,
                                    @Value("${app.rag.query-rewrite.model.mode:DIAGNOSTIC_ONLY}") String mode) {
        this.objectMapper = objectMapper;
        this.queryRewriteChatModel = queryRewriteChatModel;
        this.enabled = enabled;
        this.platform = platform == null ? "ONLINE_FAST" : platform.trim().toUpperCase(Locale.ROOT);
        this.mode = parseMode(mode);
    }

    ModelRewriteMode mode() {
        return enabled ? mode : ModelRewriteMode.OFF;
    }

    ModelRewriteResult rewrite(String currentQuery,
                               List<String> recentUserSummaries,
                               List<String> detectedMedicalAnchors,
                               String ruleEffectiveQuery,
                               String intentType) {
        if (!enabled || mode == ModelRewriteMode.OFF || !StringUtils.hasText(currentQuery)) {
            return new ModelRewriteResult(null, false);
        }
        try {
            ChatResponse response = queryRewriteChatModel.chat(buildMessages(currentQuery, recentUserSummaries, detectedMedicalAnchors, ruleEffectiveQuery, intentType));
            String text = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
            KnowledgeRetrievalModelRewriteCandidate candidate = parseCandidate(text);
            boolean accepted = candidate != null && validateCandidate(candidate, currentQuery, recentUserSummaries, detectedMedicalAnchors, intentType);
            return new ModelRewriteResult(candidate, accepted);
        } catch (Exception exception) {
            log.warn("query.rewrite.model.failed platform={} intentType={}", platform, intentType, exception);
            return new ModelRewriteResult(null, false);
        }
    }

    KnowledgeRetrievalModelRewriteCandidate parseCandidate(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return null;
        }
        try {
            ModelRewritePayload payload = objectMapper.readValue(rawResponse.trim(), ModelRewritePayload.class);
            return new KnowledgeRetrievalModelRewriteCandidate(
                    normalizeText(payload.effectiveQuery()),
                    payload.medicalAnchors() == null ? List.of() : payload.medicalAnchors().stream()
                            .map(this::normalizeText)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .toList(),
                    normalizeText(payload.intentType()),
                    payload.confidence(),
                    payload.droppedNoise() == null ? List.of() : payload.droppedNoise().stream()
                            .map(this::normalizeText)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .toList()
            );
        } catch (Exception exception) {
            log.debug("query.rewrite.model.parse.failed response={}", rawResponse, exception);
            return null;
        }
    }

    boolean validateCandidate(KnowledgeRetrievalModelRewriteCandidate candidate,
                              String currentQuery,
                              List<String> recentUserSummaries,
                              List<String> detectedMedicalAnchors,
                              String intentType) {
        if (candidate == null || !StringUtils.hasText(candidate.effectiveQuery())) {
            return false;
        }
        String effectiveQuery = candidate.effectiveQuery().trim();
        if (effectiveQuery.length() > 40) {
            return false;
        }
        if (candidate.confidence() == null || candidate.confidence() < 0.6d) {
            return false;
        }
        Set<String> allowedAnchors = new LinkedHashSet<>(detectedMedicalAnchors == null ? List.of() : detectedMedicalAnchors);
        KnowledgeSearchLexicon.detectMedicalAnchors(currentQuery).forEach(allowedAnchors::add);
        if (recentUserSummaries != null) {
            recentUserSummaries.forEach(text -> KnowledgeSearchLexicon.detectMedicalAnchors(text).forEach(allowedAnchors::add));
        }
        if (!allowedAnchors.isEmpty() && candidate.medicalAnchors().stream().anyMatch(anchor -> !allowedAnchors.contains(anchor))) {
            return false;
        }
        if (("PROCESS".equals(intentType) || "DOCTOR".equals(intentType) || "DEPARTMENT".equals(intentType) || "GUIDE".equals(intentType))
                && !candidate.medicalAnchors().isEmpty()) {
            return false;
        }
        return true;
    }

    private List<ChatMessage> buildMessages(String currentQuery,
                                            List<String> recentUserSummaries,
                                            List<String> detectedMedicalAnchors,
                                            String ruleEffectiveQuery,
                                            String intentType) {
        String userPrompt = """
                currentQuery: %s
                recentUserSummaries: %s
                detectedMedicalAnchors: %s
                ruleEffectiveQuery: %s
                intentType: %s
                """.formatted(
                currentQuery,
                recentUserSummaries == null ? List.of() : recentUserSummaries,
                detectedMedicalAnchors == null ? List.of() : detectedMedicalAnchors,
                ruleEffectiveQuery,
                intentType
        );
        return List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt));
    }

    private ModelRewriteMode parseMode(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return ModelRewriteMode.DIAGNOSTIC_ONLY;
        }
        try {
            return ModelRewriteMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ModelRewriteMode.DIAGNOSTIC_ONLY;
        }
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private record ModelRewritePayload(String effectiveQuery,
                                       List<String> medicalAnchors,
                                       String intentType,
                                       Double confidence,
                                       List<String> droppedNoise) {
    }
}

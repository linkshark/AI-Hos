package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalQueryRewriteInfo;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatRetrievalQueryRewriteService {
    private static final int MODEL_REWRITE_MAX_QUERY_LENGTH = 32;
    private static final int MODEL_REWRITE_MAX_RECENT_SUMMARIES = 2;
    private static final int MODEL_REWRITE_MAX_RECENT_SUMMARY_CHARS = 48;

    private final QueryRewriteModelService modelService;
    private final MedicalStandardLookupService medicalStandardLookupService;
    private final boolean enabled;
    private final int historyUserWindow;
    private final String mode;

    public ChatRetrievalQueryRewriteService(QueryRewriteModelService modelService,
                                            MedicalStandardLookupService medicalStandardLookupService,
                                            @Value("${app.rag.query-rewrite.enabled:true}") boolean enabled,
                                            @Value("${app.rag.query-rewrite.history-user-window:4}") int historyUserWindow,
                                            @Value("${app.rag.query-rewrite.mode:RULE_ONLY}") String mode) {
        this.modelService = modelService;
        this.medicalStandardLookupService = medicalStandardLookupService;
        this.enabled = enabled;
        this.historyUserWindow = Math.max(1, historyUserWindow);
        this.mode = StringUtils.hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "RULE_ONLY";
    }

    public KnowledgeRetrievalQueryRewriteInfo rewrite(Query query) {
        if (query == null) {
            return passthrough(null, List.of(), 0L);
        }
        Metadata metadata = query.metadata();
        List<String> recentUserMessages = extractRecentUserMessages(metadata == null ? null : metadata.chatMemory(), query.text());
        return rewriteInternal(query.text(), recentUserMessages);
    }

    public KnowledgeRetrievalQueryRewriteInfo rewrite(String rawQuery) {
        return rewriteInternal(rawQuery, List.of());
    }

    KnowledgeRetrievalQueryRewriteInfo rewriteInternalForTest(String rawQuery, List<String> recentUserMessages) {
        return rewriteInternal(rawQuery, recentUserMessages == null ? List.of() : recentUserMessages);
    }

    private KnowledgeRetrievalQueryRewriteInfo rewriteInternal(String rawQuery, List<String> recentUserMessages) {
        long startedAt = System.nanoTime();
        String normalizedQuery = KnowledgeSearchLexicon.normalizeSearchQuery(rawQuery);
        if (!enabled || !StringUtils.hasText(normalizedQuery)) {
            return passthrough(normalizedQuery, List.of(), durationMs(startedAt));
        }

        String intentType = classifyIntent(normalizedQuery);
        List<String> currentAnchors = KnowledgeSearchLexicon.detectMedicalAnchors(normalizedQuery);
        boolean excludedIntent = isExcludedIntent(intentType);
        List<KnowledgeRetrievalMatchedDiseaseEntity> currentDiseaseEntities = shouldResolveDiseaseEntities(normalizedQuery, currentAnchors, excludedIntent)
                ? medicalStandardLookupService.matchDiseaseEntities(normalizedQuery, 4)
                : List.of();
        List<String> contextMessagesUsed = new ArrayList<>();

        String effectiveQuery = buildRuleEffectiveQuery(normalizedQuery, recentUserMessages, currentAnchors, currentDiseaseEntities, contextMessagesUsed, excludedIntent);
        List<String> effectiveAnchors = KnowledgeSearchLexicon.detectMedicalAnchors(effectiveQuery);
        boolean rewriteApplied = !normalizedQuery.equals(effectiveQuery);

        QueryRewriteModelService.ModelRewriteResult modelResult = maybeRewriteWithModel(
                normalizedQuery,
                trimRecentSummaries(recentUserMessages),
                effectiveAnchors,
                effectiveQuery,
                intentType
        );
        String rewriteMode = rewriteApplied ? "RULE" : "NONE";
        if (mode.startsWith("RULE_PLUS_MODEL") && modelResult.candidate() != null) {
            rewriteMode = modelResult.accepted() && "RULE_PLUS_MODEL_ACTIVE".equals(mode) ? "MODEL" : "RULE+MODEL_DIAGNOSTIC";
        }
        String finalEffectiveQuery = effectiveQuery;
        if ("RULE_PLUS_MODEL_ACTIVE".equals(mode) && modelResult.accepted() && StringUtils.hasText(modelResult.candidate().effectiveQuery())) {
            finalEffectiveQuery = modelResult.candidate().effectiveQuery().trim();
            effectiveAnchors = modelResult.candidate().medicalAnchors().isEmpty()
                    ? effectiveAnchors
                    : modelResult.candidate().medicalAnchors();
            rewriteApplied = !normalizedQuery.equals(finalEffectiveQuery);
        }

        return new KnowledgeRetrievalQueryRewriteInfo(
                normalizedQuery,
                finalEffectiveQuery,
                rewriteMode,
                rewriteApplied,
                contextMessagesUsed,
                effectiveAnchors,
                modelResult.candidate(),
                modelResult.accepted(),
                durationMs(startedAt)
        );
    }

    private QueryRewriteModelService.ModelRewriteResult maybeRewriteWithModel(String normalizedQuery,
                                                                             List<String> recentUserMessages,
                                                                             List<String> medicalAnchors,
                                                                             String ruleEffectiveQuery,
                                                                             String intentType) {
        if ("RULE_ONLY".equals(mode) || !enabled || !shouldAttemptModelRewrite(normalizedQuery, recentUserMessages, medicalAnchors, ruleEffectiveQuery, intentType)) {
            return new QueryRewriteModelService.ModelRewriteResult(null, false);
        }
        return modelService.rewrite(normalizedQuery, recentUserMessages, medicalAnchors, ruleEffectiveQuery, intentType);
    }

    private String buildRuleEffectiveQuery(String normalizedQuery,
                                           List<String> recentUserMessages,
                                           List<String> currentAnchors,
                                           List<KnowledgeRetrievalMatchedDiseaseEntity> currentDiseaseEntities,
                                           List<String> contextMessagesUsed,
                                           boolean excludedIntent) {
        List<String> currentHints = KnowledgeSearchLexicon.extractMedicalRewriteHints(normalizedQuery);
        if (excludedIntent) {
            return currentHints.isEmpty() ? normalizedQuery : String.join(" ", currentHints);
        }

        ComplaintContext complaintContext = findRecentComplaintContext(recentUserMessages);
        Set<String> mergedHints = new LinkedHashSet<>();
        if (!currentDiseaseEntities.isEmpty()) {
            mergedHints.addAll(resolveDiseaseKeywords(currentDiseaseEntities, currentAnchors));
        } else if (!currentAnchors.isEmpty()) {
            mergedHints.addAll(currentAnchors);
        } else if (complaintContext != null && (!complaintContext.anchors().isEmpty() || !complaintContext.diseaseEntities().isEmpty())) {
            mergedHints.addAll(resolveDiseaseKeywords(complaintContext.diseaseEntities(), complaintContext.anchors()));
            mergedHints.addAll(complaintContext.anchors());
            contextMessagesUsed.add(complaintContext.rawMessage());
        }
        mergedHints.addAll(KnowledgeSearchLexicon.extractStructuredRewriteHints(normalizedQuery));
        if (complaintContext != null && !contextMessagesUsed.isEmpty()) {
            mergedHints.addAll(complaintContext.intentHints());
        } else {
            mergedHints.addAll(KnowledgeSearchLexicon.extractIntentRewriteHints(normalizedQuery));
        }
        if (mergedHints.isEmpty()) {
            mergedHints.addAll(currentHints);
        }
        String effectiveQuery = String.join(" ", mergedHints).trim();
        return StringUtils.hasText(effectiveQuery) ? effectiveQuery : normalizedQuery;
    }

    private List<String> resolveDiseaseKeywords(List<KnowledgeRetrievalMatchedDiseaseEntity> diseaseEntities,
                                                List<String> fallbackAnchors) {
        Set<String> keywords = new LinkedHashSet<>();
        if (fallbackAnchors != null) {
            keywords.addAll(fallbackAnchors);
        }
        if (diseaseEntities != null && !diseaseEntities.isEmpty()) {
            Set<String> normalizedAnchors = (fallbackAnchors == null ? List.<String>of() : fallbackAnchors).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (KnowledgeRetrievalMatchedDiseaseEntity diseaseEntity : diseaseEntities) {
                String candidate = StringUtils.hasText(diseaseEntity.diseaseName())
                        ? diseaseEntity.diseaseName().trim()
                        : StringUtils.hasText(diseaseEntity.englishName()) ? diseaseEntity.englishName().trim() : null;
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }
                if (normalizedAnchors.isEmpty() || isReasonableDiseaseExpansion(candidate, normalizedAnchors)) {
                    keywords.add(candidate);
                }
            }
        }
        return keywords.stream().filter(StringUtils::hasText).toList();
    }

    private boolean isReasonableDiseaseExpansion(String candidate, Set<String> anchors) {
        for (String anchor : anchors) {
            if (!StringUtils.hasText(anchor)) {
                continue;
            }
            String trimmedAnchor = anchor.trim();
            if (candidate.equals(trimmedAnchor)) {
                return true;
            }
            if (candidate.contains(trimmedAnchor)) {
                int extraChars = candidate.length() - trimmedAnchor.length();
                if (extraChars <= 4 && !containsScenarioQualifier(candidate, trimmedAnchor)) {
                    return true;
                }
            }
            if (trimmedAnchor.contains(candidate) && trimmedAnchor.length() - candidate.length() <= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean containsScenarioQualifier(String candidate, String anchor) {
        String remainder = candidate.replace(anchor, "");
        return remainder.contains("妊娠")
                || remainder.contains("新生儿")
                || remainder.contains("婴儿")
                || remainder.contains("儿童")
                || remainder.contains("老年")
                || remainder.contains("成人");
    }

    private ComplaintContext findRecentComplaintContext(List<String> recentUserMessages) {
        for (String recentUserMessage : recentUserMessages) {
            List<String> anchors = KnowledgeSearchLexicon.detectMedicalAnchors(recentUserMessage);
            if (anchors.isEmpty()) {
                continue;
            }
            return new ComplaintContext(
                    recentUserMessage,
                    anchors,
                    KnowledgeSearchLexicon.extractIntentRewriteHints(recentUserMessage),
                    shouldResolveDiseaseEntities(recentUserMessage, anchors, false)
                            ? medicalStandardLookupService.matchDiseaseEntities(recentUserMessage, 4)
                            : List.of()
            );
        }
        return null;
    }

    private List<String> extractRecentUserMessages(List<ChatMessage> chatMemory, String currentQuery) {
        if (chatMemory == null || chatMemory.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (int i = chatMemory.size() - 1; i >= 0 && items.size() < historyUserWindow; i--) {
            ChatMessage message = chatMemory.get(i);
            if (!(message instanceof UserMessage userMessage)) {
                continue;
            }
            String text = KnowledgeSearchLexicon.normalizeSearchQuery(userMessage.singleText());
            if (!StringUtils.hasText(text) || text.equals(KnowledgeSearchLexicon.normalizeSearchQuery(currentQuery))) {
                continue;
            }
            items.add(text);
        }
        return items;
    }

    private String classifyIntent(String query) {
        if (!StringUtils.hasText(query)) {
            return "GENERAL";
        }
        if (query.contains("医生") || query.contains("院士") || query.contains("主任")) {
            return "DOCTOR";
        }
        if (query.contains("科室") || query.contains("门诊")) {
            return "DEPARTMENT";
        }
        if (query.contains("指南") || query.contains("规范") || query.contains("共识") || query.matches(".*20\\d{2}.*版.*")) {
            return "GUIDE";
        }
        if (query.contains("预约") || query.contains("挂号") || query.contains("流程") || query.matches(".*挂\\S{0,4}号.*")) {
            return "PROCESS";
        }
        if (!KnowledgeSearchLexicon.detectMedicalAnchors(query).isEmpty()) {
            return "MEDICAL";
        }
        return "GENERAL";
    }

    private boolean isExcludedIntent(String intentType) {
        return "PROCESS".equals(intentType)
                || "DOCTOR".equals(intentType)
                || "DEPARTMENT".equals(intentType)
                || "GUIDE".equals(intentType);
    }

    private boolean shouldResolveDiseaseEntities(String normalizedQuery,
                                                 List<String> medicalAnchors,
                                                 boolean excludedIntent) {
        if (excludedIntent || !StringUtils.hasText(normalizedQuery)) {
            return false;
        }
        if (!medicalAnchors.isEmpty()) {
            return true;
        }
        return normalizedQuery.length() <= 18 && containsDiseaseSuffix(normalizedQuery);
    }

    private boolean shouldAttemptModelRewrite(String normalizedQuery,
                                             List<String> recentUserMessages,
                                             List<String> medicalAnchors,
                                             String ruleEffectiveQuery,
                                             String intentType) {
        if (!StringUtils.hasText(normalizedQuery) || isExcludedIntent(intentType)) {
            return false;
        }
        if (normalizedQuery.length() > MODEL_REWRITE_MAX_QUERY_LENGTH) {
            return false;
        }
        if (medicalAnchors.isEmpty()) {
            return false;
        }
        if (containsExactLookupHint(normalizedQuery)) {
            return false;
        }
        boolean weakFollowUp = containsWeakFollowUpHint(normalizedQuery) && !recentUserMessages.isEmpty();
        boolean weakRuleResult = !StringUtils.hasText(ruleEffectiveQuery)
                || ruleEffectiveQuery.equals(normalizedQuery)
                || ruleEffectiveQuery.length() <= normalizedQuery.length() + 4;
        return weakFollowUp || weakRuleResult;
    }

    private List<String> trimRecentSummaries(List<String> recentUserMessages) {
        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            return List.of();
        }
        return recentUserMessages.stream()
                .filter(StringUtils::hasText)
                .limit(MODEL_REWRITE_MAX_RECENT_SUMMARIES)
                .map(text -> text.length() > MODEL_REWRITE_MAX_RECENT_SUMMARY_CHARS
                        ? text.substring(0, MODEL_REWRITE_MAX_RECENT_SUMMARY_CHARS)
                        : text)
                .toList();
    }

    private boolean containsWeakFollowUpHint(String query) {
        return query.contains("没有别的症状")
                || query.contains("无其他症状")
                || query.contains("我现在")
                || query.contains("就这些")
                || query.contains("先这样")
                || query.contains("目前");
    }

    private boolean containsExactLookupHint(String query) {
        return query.matches(".*20\\d{2}.*版.*")
                || query.contains("指南")
                || query.contains("规范")
                || query.contains("共识")
                || query.contains("主任")
                || query.contains("院士");
    }

    private boolean containsDiseaseSuffix(String query) {
        return query.contains("癌")
                || query.contains("瘤")
                || query.contains("炎")
                || query.contains("病")
                || query.contains("症")
                || query.contains("感染");
    }

    private KnowledgeRetrievalQueryRewriteInfo passthrough(String rawQuery, List<String> medicalAnchors, long durationMs) {
        String normalizedQuery = KnowledgeSearchLexicon.normalizeSearchQuery(rawQuery);
        return new KnowledgeRetrievalQueryRewriteInfo(
                normalizedQuery,
                normalizedQuery,
                "NONE",
                false,
                List.of(),
                medicalAnchors == null ? List.of() : medicalAnchors,
                null,
                false,
                durationMs
        );
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ComplaintContext(String rawMessage,
                                    List<String> anchors,
                                    List<String> intentHints,
                                    List<KnowledgeRetrievalMatchedDiseaseEntity> diseaseEntities) {
    }
}

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
        List<KnowledgeRetrievalMatchedDiseaseEntity> currentDiseaseEntities = medicalStandardLookupService.matchDiseaseEntities(normalizedQuery, 4);
        boolean excludedIntent = isExcludedIntent(intentType);
        List<String> contextMessagesUsed = new ArrayList<>();

        String effectiveQuery = buildRuleEffectiveQuery(normalizedQuery, recentUserMessages, currentAnchors, currentDiseaseEntities, contextMessagesUsed, excludedIntent);
        List<String> effectiveAnchors = KnowledgeSearchLexicon.detectMedicalAnchors(effectiveQuery);
        boolean rewriteApplied = !normalizedQuery.equals(effectiveQuery);

        QueryRewriteModelService.ModelRewriteResult modelResult = maybeRewriteWithModel(
                normalizedQuery,
                recentUserMessages,
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
        if ("RULE_ONLY".equals(mode) || !enabled) {
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
        if (diseaseEntities != null) {
            diseaseEntities.stream()
                    .map(item -> StringUtils.hasText(item.diseaseName()) ? item.diseaseName() : item.englishName())
                    .filter(StringUtils::hasText)
                    .forEach(keywords::add);
        }
        if (keywords.isEmpty() && fallbackAnchors != null) {
            keywords.addAll(fallbackAnchors);
        }
        return keywords.stream().filter(StringUtils::hasText).toList();
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
                    medicalStandardLookupService.matchDiseaseEntities(recentUserMessage, 4)
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

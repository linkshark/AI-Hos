package com.linkjb.aimed.service.retrieval;

import com.linkjb.aimed.service.HybridKnowledgeRetrieverService;
import com.linkjb.aimed.service.KnowledgeSearchLexicon;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 检索摘要暂存器。
 *
 * ContentRetriever 和聊天流完成阶段不在同一个调用栈里，因此这里显式缓存最近几条检索摘要，
 * 由聊天编排层按 memoryId + rawQuery 取回，替代之前的 ThreadLocal 旁路传值。
 */
@Service
public class RetrievalSummaryStore {

    private static final String GLOBAL_KEY = "__global__";
    private static final int MAX_BUFFERED_SUMMARY_PER_KEY = 8;

    private final ConcurrentMap<String, Deque<StoredSummary>> summariesByKey = new ConcurrentHashMap<>();

    public void remember(Object chatMemoryId,
                         String rawQuery,
                         HybridKnowledgeRetrieverService.RetrievalSummary summary) {
        if (summary == null) {
            return;
        }
        rememberByKey(resolveKey(chatMemoryId), rawQuery, summary);
        if (chatMemoryId == null) {
            rememberByKey(GLOBAL_KEY, rawQuery, summary);
        }
    }

    public HybridKnowledgeRetrieverService.RetrievalSummary consume(Object chatMemoryId, String rawQuery) {
        String normalizedQuery = normalizeQuery(rawQuery);
        HybridKnowledgeRetrieverService.RetrievalSummary matched = consumeByKey(resolveKey(chatMemoryId), normalizedQuery);
        if (matched != null) {
            return matched;
        }
        return consumeByKey(GLOBAL_KEY, normalizedQuery);
    }

    private void rememberByKey(String key,
                               String rawQuery,
                               HybridKnowledgeRetrieverService.RetrievalSummary summary) {
        summariesByKey.compute(key, (ignored, existing) -> {
            Deque<StoredSummary> summaries = existing == null ? new ArrayDeque<>() : existing;
            summaries.addLast(new StoredSummary(normalizeQuery(rawQuery), summary));
            while (summaries.size() > MAX_BUFFERED_SUMMARY_PER_KEY) {
                summaries.removeFirst();
            }
            return summaries;
        });
    }

    private HybridKnowledgeRetrieverService.RetrievalSummary consumeByKey(String key, String normalizedQuery) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(normalizedQuery)) {
            return null;
        }
        AtomicReference<HybridKnowledgeRetrieverService.RetrievalSummary> matched = new AtomicReference<>();
        summariesByKey.computeIfPresent(key, (ignored, summaries) -> {
            var iterator = summaries.descendingIterator();
            while (iterator.hasNext()) {
                StoredSummary candidate = iterator.next();
                if (!normalizedQuery.equals(candidate.normalizedRawQuery())) {
                    continue;
                }
                matched.set(candidate.summary());
                iterator.remove();
                break;
            }
            return summaries.isEmpty() ? null : summaries;
        });
        return matched.get();
    }

    private String resolveKey(Object chatMemoryId) {
        return chatMemoryId == null ? GLOBAL_KEY : String.valueOf(chatMemoryId);
    }

    private String normalizeQuery(String rawQuery) {
        return KnowledgeSearchLexicon.normalizeSearchQuery(rawQuery);
    }

    private record StoredSummary(String normalizedRawQuery,
                                 HybridKnowledgeRetrieverService.RetrievalSummary summary) {
    }
}

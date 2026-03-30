package com.linkjb.aimed.model;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class RetryingEmbeddingModel implements EmbeddingModel {
    private static final Logger log = LoggerFactory.getLogger(RetryingEmbeddingModel.class);

    private final EmbeddingModel delegate;
    private final int maxAttempts;
    private final Duration retryDelay;

    public RetryingEmbeddingModel(EmbeddingModel delegate, int maxAttempts, Duration retryDelay) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay == null ? Duration.ZERO : retryDelay;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.embedAll(segments);
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt >= maxAttempts) {
                    break;
                }
                log.warn("Embedding 调用失败，第 {} 次重试后继续尝试，共 {} 次: {}", attempt, maxAttempts, e.getMessage());
                sleepQuietly();
            }
        }
        throw lastException == null ? new IllegalStateException("Embedding 调用失败") : lastException;
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    private void sleepQuietly() {
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}

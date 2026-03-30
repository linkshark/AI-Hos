package com.linkjb.aimed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class KnowledgeAsyncConfig {

    @Bean(name = "knowledgeIngestionExecutor")
    public ThreadPoolTaskExecutor knowledgeIngestionExecutor(
            @Value("${app.knowledge-base.async.core-pool-size:1}") int corePoolSize,
            @Value("${app.knowledge-base.async.max-pool-size:1}") int maxPoolSize,
            @Value("${app.knowledge-base.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("knowledge-ingestion-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean(name = "knowledgeBootstrapExecutor")
    public ThreadPoolTaskExecutor knowledgeBootstrapExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("knowledge-bootstrap-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}

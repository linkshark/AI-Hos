package com.linkjb.aimed.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Map;

@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    private final ThreadPoolTaskExecutor mvcAsyncTaskExecutor;
    private final Duration asyncTimeout;

    public WebMvcAsyncConfig(@Value("${app.web.async.core-pool-size:4}") int corePoolSize,
                             @Value("${app.web.async.max-pool-size:16}") int maxPoolSize,
                             @Value("${app.web.async.queue-capacity:200}") int queueCapacity,
                             @Value("${app.web.async.timeout:PT60S}") Duration asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
        this.mvcAsyncTaskExecutor = createExecutor(corePoolSize, maxPoolSize, queueCapacity);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Streaming chat endpoints should use a bounded thread pool instead of
        // Spring MVC's fallback SimpleAsyncTaskExecutor.
        configurer.setTaskExecutor(mvcAsyncTaskExecutor);
        configurer.setDefaultTimeout(asyncTimeout.toMillis());
    }

    private ThreadPoolTaskExecutor createExecutor(int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mvc-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    } else {
                        MDC.clear();
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}

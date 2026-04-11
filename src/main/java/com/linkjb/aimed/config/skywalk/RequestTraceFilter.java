package com.linkjb.aimed.config.skywalk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    public static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private final TraceIdProvider traceIdProvider;

    public RequestTraceFilter(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 请求刚进入时 SkyWalking trace 可能还没完全建立，先放一个占位值；
        // 请求结束前会再次解析，确保响应头和日志里最终写回真实 traceId。
        String traceId = resolveTraceId(request, null);
        long startedAt = System.nanoTime();
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        log.info("http.request.start method={} path={} query={}", request.getMethod(), request.getRequestURI(), safeQuery(request.getQueryString()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            String resolvedTraceId = resolveTraceId(request, traceId);
            if (!traceId.equals(resolvedTraceId)) {
                traceId = resolvedTraceId;
                MDC.put(TRACE_ID_KEY, traceId);
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("http.request.end method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request, String fallback) {
        String currentTraceId = traceIdProvider.currentTraceId();
        if (StringUtils.hasText(currentTraceId)) {
            return currentTraceId;
        }
        String incoming = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            return incoming.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        return "pending-trace";
    }

    private String safeQuery(String query) {
        return StringUtils.hasText(query) ? query : "-";
    }
}

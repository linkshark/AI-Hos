package com.linkjb.aimed.config.skywalk;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TraceIdProvider {

    public String currentTraceId() {
        String traceId = TraceContext.traceId();
        if (!StringUtils.hasText(traceId)) {
            return null;
        }
        String normalized = traceId.trim();
        if ("Ignored_Trace".equalsIgnoreCase(normalized) || "N/A".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}

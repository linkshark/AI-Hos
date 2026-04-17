package com.linkjb.aimed.entity.dto.response;

import java.util.List;

public record ChatProviderConfigResponse(
        String defaultProvider,
        List<String> enabledProviders
) {
}

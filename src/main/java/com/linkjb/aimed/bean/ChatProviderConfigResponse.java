package com.linkjb.aimed.bean;

import java.util.List;

public record ChatProviderConfigResponse(
        String defaultProvider,
        List<String> enabledProviders
) {
}

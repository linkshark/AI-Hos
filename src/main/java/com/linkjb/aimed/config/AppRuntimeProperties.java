package com.linkjb.aimed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.runtime")
public class AppRuntimeProperties {

    private boolean onlineConfig;

    public boolean isOnlineConfig() {
        return onlineConfig;
    }

    public void setOnlineConfig(boolean onlineConfig) {
        this.onlineConfig = onlineConfig;
    }
}

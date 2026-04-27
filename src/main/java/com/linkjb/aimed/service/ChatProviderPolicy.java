package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.ChatProviderConfigResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 聊天 provider 的归一化和对外展示策略。
 *
 * 这里不触碰具体聊天流程，只负责把前端/历史兼容值映射成系统内部真正使用的 provider，
 * 让编排层无需反复关心默认值、启用开关和旧值兼容。
 */
final class ChatProviderPolicy {

    private final String defaultProvider;
    private final boolean localProviderEnabled;
    private final boolean onlineProviderEnabled;

    ChatProviderPolicy(String defaultProvider,
                       boolean localProviderEnabled,
                       boolean onlineProviderEnabled) {
        this.defaultProvider = defaultProvider;
        this.localProviderEnabled = localProviderEnabled;
        this.onlineProviderEnabled = onlineProviderEnabled;
    }

    String normalizeProvider(String provider) {
        String requested = provider == null ? defaultProvider : provider;
        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (ChatApplicationService.QWEN_ONLINE.equals(normalized)
                || ChatApplicationService.QWEN_ONLINE_FAST.equals(normalized)) {
            return onlineProviderEnabled ? ChatApplicationService.QWEN_ONLINE_FAST : fallbackProvider();
        }
        if (ChatApplicationService.QWEN_ONLINE_DEEP.equals(normalized)) {
            return onlineProviderEnabled ? ChatApplicationService.QWEN_ONLINE_DEEP : fallbackProvider();
        }
        if (ChatApplicationService.LOCAL_OMLX.equals(normalized)
                || ChatApplicationService.LOCAL_OLLAMA.equals(normalized)) {
            return localProviderEnabled ? ChatApplicationService.LOCAL_OMLX : fallbackProvider();
        }
        return localProviderEnabled ? ChatApplicationService.LOCAL_OMLX : fallbackProvider();
    }

    ChatProviderConfigResponse providerConfig() {
        List<String> enabledProviders = new ArrayList<>(3);
        if (localProviderEnabled) {
            enabledProviders.add(ChatApplicationService.LOCAL_OMLX);
        }
        if (onlineProviderEnabled) {
            enabledProviders.add(ChatApplicationService.QWEN_ONLINE_FAST);
            enabledProviders.add(ChatApplicationService.QWEN_ONLINE_DEEP);
        }
        return new ChatProviderConfigResponse(normalizeProvider(defaultProvider), enabledProviders);
    }

    boolean isOnlineProvider(String provider) {
        return ChatApplicationService.QWEN_ONLINE_FAST.equals(provider)
                || ChatApplicationService.QWEN_ONLINE_DEEP.equals(provider)
                || ChatApplicationService.QWEN_ONLINE.equals(provider);
    }

    String errorMessageForProvider(String provider) {
        if (isOnlineProvider(provider)) {
            return "抱歉，当前千问在线服务暂时不可用，请稍后重试。若持续失败，请检查百炼网络连接。";
        }
        return "抱歉，当前本地 OMLX 模型暂时不可用，请检查 OMLX 服务和模型是否已启动。";
    }

    private String fallbackProvider() {
        if (localProviderEnabled) {
            return ChatApplicationService.LOCAL_OMLX;
        }
        if (onlineProviderEnabled) {
            return ChatApplicationService.QWEN_ONLINE_FAST;
        }
        throw new IllegalStateException("未启用任何可用的大模型提供方，请检查 app.provider.* 配置");
    }
}

package com.linkjb.aimed.service.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 视觉链路 provider 路由服务。
 *
 * 视觉问答目前只区分“本地视觉模型”和“在线视觉模型”两种运行形态，
 * 这里统一处理 provider 兼容值、启停开关和错误提示，避免同样的判断散落在视觉主服务里。
 */
@Service
public class VisionChatRoutingService {

    public static final String LOCAL_OMLX = "LOCAL_OMLX";
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE_FAST = "QWEN_ONLINE_FAST";

    private final boolean localProviderEnabled;
    private final boolean onlineProviderEnabled;
    private final String defaultProvider;

    public VisionChatRoutingService(@Value("${app.provider.local-enabled:true}") boolean localProviderEnabled,
                                    @Value("${app.provider.online-enabled:true}") boolean onlineProviderEnabled,
                                    @Value("${app.provider.default:LOCAL_OMLX}") String defaultProvider) {
        this.localProviderEnabled = localProviderEnabled;
        this.onlineProviderEnabled = onlineProviderEnabled;
        this.defaultProvider = defaultProvider;
    }

    public String resolveProvider(String provider) {
        String requested = StringUtils.hasText(provider)
                ? provider.trim().toUpperCase(Locale.ROOT)
                : defaultProvider.trim().toUpperCase(Locale.ROOT);
        if (isLocalProvider(requested)) {
            if (localProviderEnabled) {
                return LOCAL_OMLX;
            }
            if (onlineProviderEnabled) {
                return QWEN_ONLINE_FAST;
            }
        }
        if (onlineProviderEnabled) {
            return QWEN_ONLINE_FAST;
        }
        if (localProviderEnabled) {
            return LOCAL_OMLX;
        }
        throw new IllegalStateException("未启用任何可用的图片分析模型提供方，请检查 app.provider.* 配置");
    }

    public boolean isLocalProvider(String provider) {
        return LOCAL_OMLX.equals(provider) || LOCAL_OLLAMA.equals(provider);
    }

    public String errorMessage(String provider) {
        if (isLocalProvider(provider)) {
            return "抱歉，本地 OMLX 视觉模型暂时无法完成图片分析，请检查 OMLX 服务和 `Qwen3.6-35B-A3B-4bit` 是否正常运行。";
        }
        return "抱歉，我暂时无法完成图片分析，请稍后重试。";
    }
}

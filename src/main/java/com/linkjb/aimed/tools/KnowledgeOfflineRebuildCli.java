package com.linkjb.aimed.tools;

import com.linkjb.aimed.MainApp;
import com.linkjb.aimed.service.KnowledgeBaseService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * 本地离线知识索引重建入口。
 *
 * 这个 CLI 只做结构化切分和 embedding 构建，不在这一步跑 chunk 语义增强。
 * 这样可以把“切分/向量重建”和“在线 qwen-turbo 语义增强”拆成两支可独立恢复的脚本。
 */
public final class KnowledgeOfflineRebuildCli {

    private KnowledgeOfflineRebuildCli() {
    }

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(MainApp.class)
                .properties(Map.of(
                        "server.port", "0",
                        "spring.main.banner-mode", "off",
                        "app.knowledge-base.bootstrap-enabled", "false",
                        "app.knowledge-base.semantic-enrichment.enabled", "false"
                ))
                .run(args);

        int exitCode = 0;
        try {
            context.getBean(KnowledgeBaseService.class).reloadKnowledgeBase();
        } catch (Exception exception) {
            exitCode = 1;
            throw exception;
        } finally {
            context.close();
        }
        System.exit(exitCode);
    }
}

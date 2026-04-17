package com.linkjb.aimed.service;

import com.linkjb.aimed.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class VisionChatService {
    private static final Logger log = LoggerFactory.getLogger(VisionChatService.class);
    public static final String LOCAL_OLLAMA = "LOCAL_OLLAMA";
    public static final String QWEN_ONLINE = "QWEN_ONLINE";
    public static final String QWEN_ONLINE_FAST = "QWEN_ONLINE_FAST";
    public static final String QWEN_ONLINE_DEEP = "QWEN_ONLINE_DEEP";

    private final ChatModel onlineVisionChatModel;
    private final ChatModel localVisionChatModel;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final Resource promptTemplateResource;
    private final boolean localProviderEnabled;
    private final boolean onlineProviderEnabled;
    private final String defaultProvider;

    public VisionChatService(@Qualifier("qwenVisionChatModel") ChatModel onlineVisionChatModel,
                             @Qualifier("localVisionChatModel") ChatModel localVisionChatModel,
                             KnowledgeBaseService knowledgeBaseService,
                             MongoChatMemoryStore mongoChatMemoryStore,
                             @Value("${app.provider.local-enabled:true}") boolean localProviderEnabled,
                             @Value("${app.provider.online-enabled:true}") boolean onlineProviderEnabled,
                             @Value("${app.provider.default:LOCAL_OLLAMA}") String defaultProvider,
                             @org.springframework.beans.factory.annotation.Value("classpath:prompt-templates/aimed-prompt-template.txt") Resource promptTemplateResource) {
        this.onlineVisionChatModel = onlineVisionChatModel;
        this.localVisionChatModel = localVisionChatModel;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.localProviderEnabled = localProviderEnabled;
        this.onlineProviderEnabled = onlineProviderEnabled;
        this.defaultProvider = defaultProvider;
        this.promptTemplateResource = promptTemplateResource;
    }

    public String analyze(Long memoryId, String message, MultipartFile[] files, String provider) throws IOException {
        long startedAt = System.nanoTime();
        int imageCount = countImages(files);
        int attachmentCount = files == null ? 0 : files.length;
        log.info("vision.chat.start provider={} memoryId={} attachments={} images={} chars={}",
                provider, memoryId, attachmentCount, imageCount, message == null ? 0 : message.length());
        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
        String textPrompt = buildTextPrompt(message, files);
        contents.add(TextContent.from(textPrompt));

        for (MultipartFile file : files) {
            if (!knowledgeBaseService.isImageAttachment(file)) {
                continue;
            }

            String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = knowledgeBaseService.resolveMimeType(file);
            Image image = Image.builder()
                    .base64Data(base64Data)
                    .mimeType(mimeType)
                    .build();
            contents.add(ImageContent.from(image));
        }

        List<ChatMessage> messages = List.of(
                SystemMessage.from(loadPrompt(memoryId)),
                UserMessage.from(contents)
        );

        ChatResponse response = selectVisionChatModel(provider).chat(messages);
        if (response == null || response.aiMessage() == null || !StringUtils.hasText(response.aiMessage().text())) {
            log.warn("vision.chat.empty provider={} memoryId={} durationMs={}", provider, memoryId, durationMs(startedAt));
            return errorMessage(provider);
        }
        persistConversation(memoryId, effectiveUserSummary(message, files), response.aiMessage().text(), messages.get(0));
        log.info("vision.chat.complete provider={} memoryId={} answerChars={} durationMs={}",
                provider, memoryId, response.aiMessage().text().length(), durationMs(startedAt));
        return response.aiMessage().text();
    }

    private ChatModel selectVisionChatModel(String provider) {
        String resolvedProvider = resolveProvider(provider);
        if (LOCAL_OLLAMA.equals(resolvedProvider)) {
            return localVisionChatModel;
        }
        return onlineVisionChatModel;
    }

    private String resolveProvider(String provider) {
        String requested = StringUtils.hasText(provider)
                ? provider.trim().toUpperCase(Locale.ROOT)
                : defaultProvider.trim().toUpperCase(Locale.ROOT);
        if (LOCAL_OLLAMA.equals(requested)) {
            if (localProviderEnabled) {
                return LOCAL_OLLAMA;
            }
            if (onlineProviderEnabled) {
                return QWEN_ONLINE_FAST;
            }
        }
        if (onlineProviderEnabled) {
            return QWEN_ONLINE_FAST;
        }
        if (localProviderEnabled) {
            return LOCAL_OLLAMA;
        }
        throw new IllegalStateException("未启用任何可用的图片分析模型提供方，请检查 app.provider.* 配置");
    }

    private String errorMessage(String provider) {
        if (LOCAL_OLLAMA.equals(provider)) {
            return "抱歉，本地 Ollama 视觉模型暂时无法完成图片分析，请检查 `qwen2.5vl:7b` 是否已拉取并正常运行。";
        }
        return "抱歉，我暂时无法完成图片分析，请稍后重试。";
    }

    private String buildTextPrompt(String message, MultipartFile[] files) throws IOException {
        String effectiveMessage = StringUtils.hasText(message) ? message : "请结合我上传的图片或材料进行分析并回答。";
        // 文档类附件先转成文本上下文，再与图片一起送入多模态模型。
        String attachmentContext = knowledgeBaseService.buildChatAttachmentTextContext(files);

        StringBuilder builder = new StringBuilder();
        builder.append("用户上传了图片或材料，请优先基于附件内容回答。\n")
                .append("如果图片或材料信息不足，请明确说明不确定性；不要编造诊断结果。\n")
                .append("如涉及病情判断，请区分：观察建议、就诊建议、急诊风险提示。\n");

        if (StringUtils.hasText(attachmentContext)) {
            builder.append('\n').append(attachmentContext).append('\n');
        }

        builder.append("\n[用户问题]\n").append(effectiveMessage);
        return builder.toString();
    }

    private String loadPrompt(Long memoryId) throws IOException {
        String prompt = StreamUtils.copyToString(promptTemplateResource.getInputStream(), StandardCharsets.UTF_8);
        return prompt.replace("{{current_date}}", LocalDate.now().toString())
                + "\n7、如果用户上传了图片、病历、检验单或检查报告，请优先结合附件内容进行分析，但必须明确说明你的回答不能替代医生面诊。"
                + "\n8、如果图片中存在可能提示急症、危险信号或需要线下复诊的情况，请优先给出风险提示。"
                + "\n9、当前会话ID为 " + memoryId + "。";
    }

    private String effectiveUserSummary(String message, MultipartFile[] files) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(message) ? message : "请结合我上传的图片或材料进行分析并回答。");

        List<String> attachmentNames = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty() && StringUtils.hasText(file.getOriginalFilename())) {
                    attachmentNames.add(file.getOriginalFilename());
                }
            }
        }
        if (!attachmentNames.isEmpty()) {
            builder.append("\n附件：").append(String.join("，", attachmentNames));
        }
        return builder.toString();
    }

    private void persistConversation(Long memoryId, String userSummary, String answer, ChatMessage systemMessage) {
        List<ChatMessage> history = new LinkedList<>(mongoChatMemoryStore.getMessages(memoryId));
        if (history.isEmpty()) {
            history.add(systemMessage);
        }
        history.add(UserMessage.from(userSummary));
        history.add(AiMessage.from(answer));

        // 视觉会话只保留最近窗口，避免 Mongo 记忆无限增长并拖慢后续问答。
        if (history.size() > 20) {
            ChatMessage first = history.get(0);
            List<ChatMessage> trimmed = new LinkedList<>();
            if (first instanceof SystemMessage) {
                trimmed.add(first);
                trimmed.addAll(history.subList(Math.max(1, history.size() - 19), history.size()));
            } else {
                trimmed.addAll(history.subList(Math.max(0, history.size() - 20), history.size()));
            }
            history = trimmed;
        }
        mongoChatMemoryStore.updateMessages(memoryId, history);
    }

    private int countImages(MultipartFile[] files) {
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (MultipartFile file : files) {
            if (knowledgeBaseService.isImageAttachment(file)) {
                count++;
            }
        }
        return count;
    }

    private long durationMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    public static void main(String[] args) {
       String s ="select \n" +
               "      CAST(@row_number := @row_number + 1 AS CHAR) AS hc,\n" +
               "    xmmc,\n" +
               "    je,\n" +
               "    dw,\n" +
               "    sl,\n" +
               "    bz\n" +
               "FROM (\n" +
               "    SELECT\n" +
               "    CONCAT('(',d.insure_grade,')',d.drug_name) AS xmmc,\n" +
               "    ( round( round( price, 4 ) * round( sum( qty ), 4 ), 2 ) ) AS je,\n" +
               "    d.exe_unit AS dw,\n" +
               "    CONVERT ( sum( qty ), DECIMAL ( 12, 2 ) ) AS sl,\n" +
               "    CONCAT((case when insure_scale=0 then '甲' when insure_scale>0 and insure_scale<1 then '乙'  else '丙' end),'/',CONCAT(ifnull( CONVERT ( d.insure_scale * 100, DECIMAL ( 12, 2 ) ), 1 ),'%')) as bz,\n" +
               "    income_id as hc\n" +
               "    FROM\n" +
               "    emr.cd_prescription_detail d\n" +
               "    INNER JOIN emr.cd_prescription p ON p.prescription_id = d.prescription_id\n" +
               "    WHERE  p.del = 1 \n" +
               "    AND p.cash_id =  #{cashId}\n" +
               "    GROUP BY\n" +
               "    d.drug_factory_id,\n" +
               "    store_stock_id,\n" +
               "    d.prescription_id \n" +
               "    having je>0\n" +
               "\n" +
               "    UNION ALL\n" +
               "\n" +
               "    SELECT\n" +
               "    CONCAT('(',c.insure_grade,')',c.charge_item_name) AS xmmc,\n" +
               "    ( round( round( price, 4 ) * round( sum( qty ), 4 ), 2 ) ) AS je,\n" +
               "    c.charge_item_unit AS dw,\n" +
               "    CONVERT ( sum( qty ), DECIMAL ( 12, 2 ) ) AS sl,\n" +
               "    CONCAT((case when insure_scale=0 then '甲' when insure_scale>0 and insure_scale<1 then '乙'  else '丙' end),'/',CONCAT(ifnull( CONVERT ( c.insure_scale * 100, DECIMAL ( 12, 2 ) ), 1 ),'%') ) as bz,\n" +
               "    income_id as hc\n" +
               "    FROM\n" +
               "    emr.cd_charge c\n" +
               "    LEFT JOIN (select insure_order_code,charge_item_id from sis.si_order_dict2his b where b.dict_book_id='402' and  b.mapp_end_time is null) s on c.charge_item_id=s.charge_item_id \n" +
               "    WHERE\n" +
               "    IFNULL(free_flag,0) = 0 \n" +
               "    AND cash_id = #{cashId}\n" +
               "    GROUP BY\n" +
               "    c.charge_item_id,\n" +
               "    request_id\n" +
               "    having je>0\n" +
               "    ) AS combined_result\n" +
               "CROSS JOIN (SELECT @row_number := 0) AS init\n" +
               "ORDER BY hc;\n";
       System.out.println(s);
    }
}

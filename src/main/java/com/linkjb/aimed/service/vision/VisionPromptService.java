package com.linkjb.aimed.service.vision;

import com.linkjb.aimed.service.knowledge.KnowledgeAttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉聊天 prompt 组装服务。
 *
 * 视觉链路的 prompt 由三部分构成：系统模板、附件上下文和用户问题。
 * 单独抽出来以后，视觉主服务只负责调用模型和保存会话，不再混着处理文案拼装。
 */
@Service
public class VisionPromptService {

    private final KnowledgeAttachmentService knowledgeAttachmentService;
    private final Resource promptTemplateResource;

    public VisionPromptService(KnowledgeAttachmentService knowledgeAttachmentService,
                               @Value("classpath:prompt-templates/aimed-prompt-template.txt") Resource promptTemplateResource) {
        this.knowledgeAttachmentService = knowledgeAttachmentService;
        this.promptTemplateResource = promptTemplateResource;
    }

    public String buildTextPrompt(String message, MultipartFile[] files) throws IOException {
        String effectiveMessage = StringUtils.hasText(message) ? message : "请结合我上传的图片或材料进行分析并回答。";
        String attachmentContext = knowledgeAttachmentService.buildChatAttachmentTextContext(files);

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

    public String loadPrompt(Long memoryId) throws IOException {
        String prompt = StreamUtils.copyToString(promptTemplateResource.getInputStream(), StandardCharsets.UTF_8);
        return prompt.replace("{{current_date}}", LocalDate.now().toString())
                + "\n7、如果用户上传了图片、病历、检验单或检查报告，请优先结合附件内容进行分析，但必须明确说明你的回答不能替代医生面诊。"
                + "\n8、如果图片中存在可能提示急症、危险信号或需要线下复诊的情况，请优先给出风险提示。"
                + "\n9、当前会话ID为 " + memoryId + "。";
    }

    public String effectiveUserSummary(String message, MultipartFile[] files) {
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
}

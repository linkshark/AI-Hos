package com.linkjb.aimed.service.knowledge;

import dev.langchain4j.data.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 当前轮聊天附件预处理服务。
 *
 * 这类附件只服务于“当前这次问答”，不会进入长期知识库，因此它的职责应和知识文件上传/发布拆开。
 * 这里统一处理校验、文本抽取、图片识别和 prompt 上下文拼装，供聊天主链和视觉链复用。
 */
@Service
public class KnowledgeAttachmentService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif", "bmp");
    private static final Set<String> SUPPORTED_EXTENSIONS = new LinkedHashSet<>(List.of(
            "pdf", "txt", "md", "markdown", "csv", "doc", "docx", "rtf",
            "html", "htm", "xml", "odt", "ods", "odp", "xls", "xlsx", "ppt", "pptx"
    ));
    private static final Set<String> CHAT_ATTACHMENT_EXTENSIONS = new LinkedHashSet<>();
    private static final int MAX_CHAT_ATTACHMENTS = 3;
    private static final long MAX_SINGLE_CHAT_ATTACHMENT_BYTES = 1024L * 1024L;
    private static final long MAX_TOTAL_CHAT_ATTACHMENT_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_SINGLE_CHAT_ATTACHMENT_CHARACTERS = 8000;
    private static final int MAX_TOTAL_CHAT_ATTACHMENT_CHARACTERS = 18000;

    static {
        CHAT_ATTACHMENT_EXTENSIONS.addAll(SUPPORTED_EXTENSIONS);
        CHAT_ATTACHMENT_EXTENSIONS.addAll(IMAGE_EXTENSIONS);
    }

    private final KnowledgeParseService knowledgeParseService;

    public KnowledgeAttachmentService(KnowledgeParseService knowledgeParseService) {
        this.knowledgeParseService = knowledgeParseService;
    }

    public String buildChatMessageWithAttachments(String userMessage, MultipartFile[] files) throws IOException {
        String attachmentContext = buildChatAttachmentTextContext(files);
        if (!StringUtils.hasText(attachmentContext)) {
            return userMessage;
        }
        return attachmentContext + "\n\n[用户问题]\n" + userMessage;
    }

    public String buildChatAttachmentTextContext(MultipartFile[] files) throws IOException {
        validateChatAttachments(files);
        if (files == null || files.length == 0) {
            return "";
        }

        List<ChatAttachmentContent> attachmentContents = collectChatAttachmentContents(files);
        if (attachmentContents.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("以下是用户在本轮对话中上传的材料内容，仅用于当前问题，不写入长期知识库。\n")
                .append("回答时请优先结合这些材料；如果材料信息不足，请明确说明，不要编造。\n");

        for (int index = 0; index < attachmentContents.size(); index++) {
            ChatAttachmentContent attachment = attachmentContents.get(index);
            builder.append("\n[附件")
                    .append(index + 1)
                    .append("：")
                    .append(attachment.fileName())
                    .append("]\n")
                    .append(attachment.content());
            if (attachment.truncated()) {
                builder.append("\n（以上内容因篇幅限制已截断）");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public boolean hasImageAttachments(MultipartFile[] files) throws IOException {
        validateChatAttachments(files);
        if (files == null) {
            return false;
        }
        for (MultipartFile file : files) {
            if (isImageAttachment(file)) {
                return true;
            }
        }
        return false;
    }

    public boolean isImageAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
        return IMAGE_EXTENSIONS.contains(getExtension(originalFilename));
    }

    public String resolveMimeType(MultipartFile file) {
        if (file == null) {
            return "application/octet-stream";
        }
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        String extension = getExtension(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "");
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private void validateChatAttachments(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            return;
        }
        int nonEmptyCount = 0;
        long totalBytes = 0L;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            nonEmptyCount++;
            String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
            if (nonEmptyCount > MAX_CHAT_ATTACHMENTS) {
                throw new IOException("单次对话最多上传 " + MAX_CHAT_ATTACHMENTS + " 个附件");
            }
            if (file.getSize() > MAX_SINGLE_CHAT_ATTACHMENT_BYTES) {
                throw new IOException("单个附件大小不能超过 1MB: " + originalFilename);
            }
            totalBytes += Math.max(0L, file.getSize());
            if (totalBytes > MAX_TOTAL_CHAT_ATTACHMENT_BYTES) {
                throw new IOException("聊天附件总大小不能超过 2MB");
            }
            getChatAttachmentExtension(originalFilename);
        }
    }

    private List<ChatAttachmentContent> collectChatAttachmentContents(MultipartFile[] files) throws IOException {
        List<ChatAttachmentContent> attachmentContents = new ArrayList<>();
        int remainingCharacters = MAX_TOTAL_CHAT_ATTACHMENT_CHARACTERS;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalFilename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed";
            String extension = getChatAttachmentExtension(originalFilename);
            if (IMAGE_EXTENSIONS.contains(extension)) {
                continue;
            }

            String text = parseMultipartFile(file, originalFilename, extension);
            int currentLimit = Math.min(MAX_SINGLE_CHAT_ATTACHMENT_CHARACTERS, remainingCharacters);
            if (currentLimit <= 0) {
                break;
            }

            boolean truncated = text.length() > currentLimit;
            String boundedText = truncated ? text.substring(0, currentLimit) : text;
            attachmentContents.add(new ChatAttachmentContent(originalFilename, boundedText, truncated));
            remainingCharacters -= boundedText.length();
        }
        return attachmentContents;
    }

    private String parseMultipartFile(MultipartFile file, String originalFilename, String extension) throws IOException {
        try (var inputStream = file.getInputStream()) {
            KnowledgeParsedDocument parsedDocument = knowledgeParseService.parse(
                    inputStream,
                    originalFilename,
                    "transient-chat",
                    extension,
                    "chat-upload"
            );
            String normalizedText = parsedDocument.document().text();
            if (!StringUtils.hasText(normalizedText)) {
                throw new IOException("未从文件中解析出有效文本: " + originalFilename);
            }
            return normalizedText;
        }
    }

    private String getChatAttachmentExtension(String originalFilename) throws IOException {
        String extension = getExtension(originalFilename);
        if (!CHAT_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new IOException("聊天附件暂不支持该格式: " + originalFilename);
        }
        return extension;
    }

    private String getExtension(String filename) {
        return knowledgeParseService.getExtension(filename);
    }

    private record ChatAttachmentContent(String fileName, String content, boolean truncated) {
    }
}

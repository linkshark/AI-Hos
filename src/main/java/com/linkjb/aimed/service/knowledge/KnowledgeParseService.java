package com.linkjb.aimed.service.knowledge;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 知识文件解析服务。
 *
 * 这里统一负责“文件流 -> 标准化 Document”的过程，明确处理三类差异：
 * - 纯文本类直接走轻量 parser
 * - PDF 保留 PDFBox -> Tika fallback
 * - 其他 Office/HTML 类统一走 Tika
 *
 * 这样知识库入库和聊天附件预处理不会再各自维护一套解析细节。
 */
@Service
public class KnowledgeParseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeParseService.class);
    private static final Set<String> DIRECT_TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "csv");

    private final DocumentParser textParser = new TextDocumentParser();
    private final DocumentParser pdfParser = new ApachePdfBoxDocumentParser();
    private final DocumentParser tikaParser = new ApacheTikaDocumentParser();

    public KnowledgeParsedDocument parseFile(Path file,
                                             String originalFilename,
                                             String hash,
                                             String extension,
                                             String source) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return parse(inputStream, originalFilename, hash, extension, source);
        }
    }

    public KnowledgeParsedDocument parse(InputStream inputStream,
                                         String originalFilename,
                                         String hash,
                                         String extension,
                                         String source) throws IOException {
        DocumentParser parser = selectParser(extension);
        Document parsedDocument;
        String parserName;

        if ("pdf".equals(extension)) {
            byte[] fileBytes = inputStream.readAllBytes();
            try {
                parsedDocument = parser.parse(new ByteArrayInputStream(fileBytes));
                parserName = parser.getClass().getSimpleName();
            } catch (Exception primaryException) {
                log.warn("PDFBox 解析失败，回退到 Tika: {}", originalFilename, primaryException);
                parsedDocument = tikaParser.parse(new ByteArrayInputStream(fileBytes));
                parserName = tikaParser.getClass().getSimpleName();
            }
        } else {
            parsedDocument = parser.parse(inputStream);
            parserName = parser.getClass().getSimpleName();
        }

        String normalizedText = normalizeText(parsedDocument.text());
        if (!StringUtils.hasText(normalizedText)) {
            throw new IOException("未从文件中解析出有效文本");
        }

        Metadata metadata = parsedDocument.metadata() == null ? new Metadata() : parsedDocument.metadata().copy();
        metadata.put(Document.FILE_NAME, originalFilename);
        metadata.put("knowledge_hash", hash);
        metadata.put("knowledge_source", source);
        metadata.put("knowledge_extension", extension);
        return new KnowledgeParsedDocument(Document.from(normalizedText, metadata), parserName);
    }

    public String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private DocumentParser selectParser(String extension) {
        if ("pdf".equals(extension)) {
            return pdfParser;
        }
        if (DIRECT_TEXT_EXTENSIONS.contains(extension)) {
            return textParser;
        }
        return tikaParser;
    }
}

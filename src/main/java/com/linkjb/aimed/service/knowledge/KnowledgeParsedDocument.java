package com.linkjb.aimed.service.knowledge;

import dev.langchain4j.data.document.Document;

/**
 * 文件解析后的统一结果。
 *
 * 这里保留最终可消费的 `Document` 和实际命中的 parser 名称，
 * 让知识库入库、附件预处理和诊断工具都能复用同一套解析结果结构。
 */
public record KnowledgeParsedDocument(Document document, String parserName) {
}

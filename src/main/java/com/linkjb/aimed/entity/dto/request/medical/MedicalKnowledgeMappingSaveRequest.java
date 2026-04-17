package com.linkjb.aimed.entity.dto.request.medical;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 手动保存知识文档关联疾病的请求体。
 *
 * @param hash         知识文档哈希
 * @param conceptCodes 用户手动勾选的疾病概念编码列表
 */
public record MedicalKnowledgeMappingSaveRequest(@NotBlank String hash,
                                                 List<String> conceptCodes) {
}

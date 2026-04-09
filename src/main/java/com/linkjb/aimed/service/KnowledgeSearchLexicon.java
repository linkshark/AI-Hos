package com.linkjb.aimed.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class KnowledgeSearchLexicon {

    private KnowledgeSearchLexicon() {
    }

    static String inferDepartment(String title) {
        if (!StringUtils.hasText(title)) {
            return "通用";
        }
        String normalized = title.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "神经", "脑")) {
            return "神经系统";
        }
        if (containsAny(normalized, "肝", "胆", "胰")) {
            return "肝胆胰";
        }
        if (containsAny(normalized, "肿瘤", "癌")) {
            return "肿瘤";
        }
        if (containsAny(normalized, "门诊", "挂号", "预约")) {
            return "门诊";
        }
        if (containsAny(normalized, "心", "血管", "冠脉")) {
            return "心血管";
        }
        return "通用";
    }

    static String inferDoctorName(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String normalized = title.trim();
        if (normalized.contains("院士")) {
            return normalized.replaceAll("[._-].*$", "").replace("院士资源", "").trim();
        }
        if (normalized.contains("主任")) {
            return normalized.replaceAll("[._-].*$", "").trim();
        }
        return null;
    }

    static String buildKeywordSeed(String title, String extension, String docType, String department, String doctorName) {
        Set<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, title);
        addKeyword(keywords, extension);
        addKeyword(keywords, docType);
        addKeyword(keywords, department);
        addKeyword(keywords, doctorName);

        String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (containsAny(normalizedTitle, "院士", "专家", "医生", "主任")) {
            keywords.add("专家");
            keywords.add("医生");
        }
        if (containsAny(normalizedTitle, "挂号", "预约", "门诊", "就诊")) {
            keywords.add("挂号");
            keywords.add("预约");
            keywords.add("就诊");
            keywords.add("门诊");
        }
        if (containsAny(normalizedTitle, "指南", "共识", "规范")) {
            keywords.add("指南");
            keywords.add("规范");
            keywords.add("共识");
        }
        if (containsAny(normalizedTitle, "肝胆胰")) {
            keywords.add("肝胆科");
            keywords.add("肝胆胰外科");
        }
        return String.join(" ", keywords);
    }

    static List<String> expandQueryTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        addKeyword(tokens, normalized);
        for (String part : normalized.split("\\s+")) {
            addKeyword(tokens, part);
        }
        if (normalized.contains("院士")) {
            addKeyword(tokens, "院士");
            addKeyword(tokens, "专家");
            addKeyword(tokens, "医生");
        }
        if (containsAny(normalized, "挂号", "预约")) {
            addKeyword(tokens, "挂号");
            addKeyword(tokens, "预约");
            addKeyword(tokens, "门诊");
            addKeyword(tokens, "就诊");
        }
        if (normalized.contains("肝胆科")) {
            addKeyword(tokens, "肝胆胰");
            addKeyword(tokens, "肝胆胰外科");
        }
        if (normalized.contains("神经科")) {
            addKeyword(tokens, "神经内科");
            addKeyword(tokens, "神经外科");
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            addKeyword(tokens, normalized.substring(i, i + 2).trim());
        }
        return tokens.stream().filter(token -> token.length() >= 2).limit(16).toList();
    }

    private static void addKeyword(Set<String> keywords, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            keywords.add(trimmed);
        }
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}

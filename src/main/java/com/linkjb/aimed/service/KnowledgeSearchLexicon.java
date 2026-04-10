package com.linkjb.aimed.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class KnowledgeSearchLexicon {

    private static final List<String> QUERY_NOISE_SUFFIXES = List.of(
            "讲了什么", "讲什么", "是什么", "主要内容", "内容是什么", "内容讲什么",
            "介绍一下", "介绍下", "说一下", "说说", "帮我总结", "总结一下", "解读一下",
            "在哪个科室方向", "在哪个方向", "是哪个方向", "是什么方向"
    );

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
        String normalized = title.trim().replaceAll("\\.(md|txt|pdf|docx?)$", "");
        String personLike = normalized.replaceAll(".*?([\\u4e00-\\u9fa5]{2,4})(院士|主任|教授|医生).*", "$1");
        if (personLike.matches("[\\u4e00-\\u9fa5]{2,4}")) {
            return personLike;
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
        String normalized = normalizeSearchQuery(query);
        Set<String> tokens = new LinkedHashSet<>();
        addKeyword(tokens, normalized);
        addKeyword(tokens, normalized.replaceAll("[（()）《》“”\"'、，。？！：:;；\\-_/]", ""));
        for (String part : normalized.split("\\s+")) {
            addKeyword(tokens, part);
        }
        for (String part : normalized.split("[（()）《》“”\"'、，。？！：:;；\\-_/\\s]+")) {
            addKeyword(tokens, part);
        }
        String doctorName = extractDoctorName(normalized);
        addKeyword(tokens, doctorName);
        if (normalized.contains("院士")) {
            addKeyword(tokens, "院士");
            addKeyword(tokens, "专家");
            addKeyword(tokens, "医生");
        }
        if (containsAny(normalized, "主任", "教授", "医生")) {
            addKeyword(tokens, "专家");
            addKeyword(tokens, "医生");
        }
        if (containsAny(normalized, "挂号", "预约")) {
            addKeyword(tokens, "挂号");
            addKeyword(tokens, "预约");
            addKeyword(tokens, "门诊");
            addKeyword(tokens, "就诊");
        }
        if (containsAny(normalized, "肝胆科", "肝胆外科", "肝胆胰外科")) {
            addKeyword(tokens, "肝胆胰");
            addKeyword(tokens, "肝胆胰外科");
        }
        if (normalized.contains("神经科")) {
            addKeyword(tokens, "神经内科");
            addKeyword(tokens, "神经外科");
        }
        if (normalized.matches(".*20\\d{2}.*")) {
            String year = normalized.replaceAll(".*?(20\\d{2}).*", "$1");
            addKeyword(tokens, year);
            addKeyword(tokens, year + "年");
            addKeyword(tokens, year + "年版");
        }
        String version = extractVersion(normalized);
        addKeyword(tokens, version);
        if (containsAny(normalized, "指南", "共识", "规范")) {
            addKeyword(tokens, normalized.replaceAll("[^\\p{IsHan}0-9]", ""));
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            addKeyword(tokens, normalized.substring(i, i + 2).trim());
        }
        return tokens.stream().filter(token -> token.length() >= 2).limit(16).toList();
    }

    static String normalizeSearchQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        String normalized = query
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", " ")
                .trim();
        for (String suffix : QUERY_NOISE_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
                break;
            }
        }
        return normalized;
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

    private static String extractDoctorName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String extracted = value.replaceAll(".*?([\\u4e00-\\u9fa5]{2,4})(院士|主任|教授|医生).*", "$1");
        return extracted.matches("[\\u4e00-\\u9fa5]{2,4}") ? extracted : null;
    }

    private static String extractVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.matches(".*v\\d+(\\.\\d+)?[a-zA-Z0-9]*.*")) {
            return value.replaceAll(".*?(v\\d+(?:\\.\\d+)?[a-zA-Z0-9]*).*", "$1");
        }
        if (value.matches(".*\\d{4}年?版.*")) {
            return value.replaceAll(".*?(\\d{4}年?版).*", "$1");
        }
        return null;
    }
}

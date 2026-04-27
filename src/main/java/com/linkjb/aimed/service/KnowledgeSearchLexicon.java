package com.linkjb.aimed.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KnowledgeSearchLexicon {

    private static final List<String> QUERY_NOISE_SUFFIXES = List.of(
            "讲了什么", "讲什么", "是什么", "主要内容", "内容是什么", "内容讲什么",
            "介绍一下", "介绍下", "说一下", "说说", "帮我总结", "总结一下", "解读一下",
            "在哪个科室方向", "在哪个方向", "是哪个方向", "是什么方向"
    );
    private static final Set<String> QUERY_NOISE_TOKENS = Set.of(
            "应该", "应当", "如何", "怎么", "怎么办", "怎样", "处理", "可以", "一下", "请问",
            "帮我", "需要", "建议", "什么", "怎么样", "是否", "能否", "治疗"
    );
    private static final List<String> COMMON_MEDICAL_TERMS = List.of(
            "肺炎支原体感染", "肺炎支原体肺炎", "支原体肺炎", "支原体感染",
            "慢性阻塞性肺疾病", "慢性肾脏病", "高尿酸血症",
            "普通感冒", "感冒", "上呼吸道感染", "急性上呼吸道感染",
            "肝癌", "肺癌", "胃癌", "肠癌", "乳腺癌", "糖尿病", "高血压",
            "痛风", "肥胖症", "流感", "诺如病毒", "新型冠状病毒感染",
            "发热", "发烧", "鼻塞", "咳嗽", "干咳", "咳痰", "咽痛", "喉咙痛",
            "流涕", "流鼻涕", "打喷嚏", "腹泻", "拉肚子", "呕吐", "恶心",
            "头痛", "头疼", "胸闷", "胸痛", "腹痛", "皮疹", "乏力"
    );
    private static final Set<String> REWRITE_NOISE_TOKENS = Set.of(
            "普通人", "没有别的症状", "就这些", "先这样", "我现在", "我叫", "好的", "你好", "您好"
    );
    private static final List<String> STRUCTURED_HINT_PATTERNS = List.of(
            "\\d{1,3}岁", "男", "女", "\\d+(?:\\.\\d+)?℃", "\\d+天", "\\d+周"
    );
    private static final List<String> MEDICAL_INTENT_TERMS = List.of(
            "吃什么药", "吃点什么药", "用什么药", "怎么用药", "挂什么科", "看什么科",
            "需要住院", "怎么治疗", "如何治疗", "怎么处理", "怎么办"
    );
    private static final Pattern CHINESE_NAME_PATTERN = Pattern.compile("我叫[\\u4e00-\\u9fa5A-Za-z]{2,16}");

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
        if (!containsQueryNoise(normalized)) {
            addKeyword(tokens, normalized);
        }
        extractCoreQueryTokens(normalized).forEach(token -> addKeyword(tokens, token));
        String noiseRemoved = removeQueryNoise(normalized);
        addKeyword(tokens, noiseRemoved);
        addKeyword(tokens, noiseRemoved.replaceAll("[（()）《》“”\"'、，。？！：:;；\\-_/]", ""));
        for (String part : noiseRemoved.split("\\s+")) {
            addUsefulQueryToken(tokens, part);
        }
        for (String part : noiseRemoved.split("[（()）《》“”\"'、，。？！：:;；\\-_/\\s]+")) {
            addUsefulQueryToken(tokens, part);
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
            addKeyword(tokens, noiseRemoved.replaceAll("[^\\p{IsHan}0-9]", ""));
        }
        return tokens.stream()
                .filter(KnowledgeSearchLexicon::isUsefulQueryToken)
                .limit(16)
                .toList();
    }

    public static String normalizeSearchQuery(String query) {
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

    static List<String> extractCoreQueryTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = normalizeSearchQuery(query);
        Set<String> tokens = new LinkedHashSet<>();
        for (String term : COMMON_MEDICAL_TERMS) {
            if (!normalized.contains(term)) {
                continue;
            }
            addKeyword(tokens, term);
            if (term.endsWith("癌") && normalized.contains("原发性")) {
                addKeyword(tokens, "原发性" + term);
            }
            if (term.endsWith("癌") && normalized.contains("早期")) {
                addKeyword(tokens, "早期" + term);
            }
            if (containsAny(normalized, "指南", "规范", "共识")) {
                addKeyword(tokens, term + "指南");
            }
        }
        addAliasTokens(normalized, tokens);
        return tokens.stream().filter(KnowledgeSearchLexicon::isUsefulQueryToken).toList();
    }

    static List<String> detectMedicalAnchors(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = normalizeSearchQuery(query);
        Set<String> anchors = new LinkedHashSet<>();
        extractCoreQueryTokens(normalized).forEach(anchors::add);
        for (String term : COMMON_MEDICAL_TERMS) {
            if (normalized.contains(term)) {
                anchors.add(term);
            }
        }
        addAliasTokens(normalized, anchors);
        return anchors.stream().filter(StringUtils::hasText).toList();
    }

    static List<String> extractMedicalRewriteHints(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = normalizeSearchQuery(query);
        String stripped = stripRewriteNoise(normalized);
        Set<String> hints = new LinkedHashSet<>();
        detectMedicalAnchors(stripped).forEach(hints::add);
        extractStructuredRewriteHints(normalized).forEach(hints::add);
        extractIntentRewriteHints(normalized).forEach(hints::add);
        if (hints.isEmpty()) {
            for (String token : stripped.split("[\\s,，。；;]+")) {
                if (isUsefulQueryToken(token)) {
                    hints.add(token.trim());
                }
            }
        }
        return hints.stream().filter(StringUtils::hasText).toList();
    }

    static List<String> extractStructuredRewriteHints(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = normalizeSearchQuery(query);
        Set<String> hints = new LinkedHashSet<>();
        for (String regex : STRUCTURED_HINT_PATTERNS) {
            Matcher matcher = Pattern.compile(regex).matcher(normalized);
            while (matcher.find()) {
                hints.add(matcher.group());
            }
        }
        if (normalized.contains("没有别的症状")) {
            hints.add("无其他症状");
        }
        return hints.stream().filter(StringUtils::hasText).toList();
    }

    static List<String> extractIntentRewriteHints(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = normalizeSearchQuery(query);
        Set<String> hints = new LinkedHashSet<>();
        for (String term : MEDICAL_INTENT_TERMS) {
            if (normalized.contains(term)) {
                hints.add(term);
            }
        }
        if (normalized.contains("挂号") || normalized.matches(".*挂\\S{0,4}号.*")) {
            hints.add("挂号");
        }
        return hints.stream().filter(StringUtils::hasText).toList();
    }

    private static String removeQueryNoise(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = value;
        for (String noise : QUERY_NOISE_TOKENS) {
            cleaned = cleaned.replace(noise, " ");
        }
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String stripRewriteNoise(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = removeQueryNoise(value);
        for (String noise : REWRITE_NOISE_TOKENS) {
            cleaned = cleaned.replace(noise, " ");
        }
        cleaned = CHINESE_NAME_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsQueryNoise(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return QUERY_NOISE_TOKENS.stream().anyMatch(value::contains);
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

    private static void addUsefulQueryToken(Set<String> keywords, String value) {
        if (isUsefulQueryToken(value)) {
            addKeyword(keywords, value);
        }
    }

    private static boolean isUsefulQueryToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.length() >= 2 && !QUERY_NOISE_TOKENS.contains(trimmed) && !REWRITE_NOISE_TOKENS.contains(trimmed);
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

    private static void addAliasTokens(String normalized, Set<String> tokens) {
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        if (containsAny(normalized, "肺炎支原体感染", "肺炎支原体肺炎", "支原体肺炎", "支原体感染", "肺炎支原体")) {
            addKeyword(tokens, "肺炎支原体感染");
            addKeyword(tokens, "肺炎支原体肺炎");
            addKeyword(tokens, "支原体肺炎");
            addKeyword(tokens, "支原体感染");
            if (normalized.contains("儿童")) {
                addKeyword(tokens, "儿童肺炎支原体肺炎");
            }
            if (containsAny(normalized, "指南", "规范", "共识")) {
                addKeyword(tokens, "肺炎支原体肺炎指南");
                addKeyword(tokens, "儿童肺炎支原体肺炎诊疗指南");
            }
        }
        if (containsAny(normalized, "肝细胞癌", "原发性肝癌")) {
            addKeyword(tokens, "原发性肝癌");
            addKeyword(tokens, "肝细胞癌");
            addKeyword(tokens, "肝癌");
        }
    }
}

package com.linkjb.aimed.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 聊天意图分析服务。
 *
 * 第一版只做可解释的规则分流，用来决定“这一轮是否需要知识库 RAG”。
 * 工具类问题如果仍走 RAG，容易引入无关引用并拖慢首字；所以这里把工具意图优先级放在医疗知识问答之前。
 */
@Service
public class ChatIntentAnalysisService {
    private static final List<String> MEDICAL_NEED_TERMS = List.of(
            "怎么办", "怎么处理", "怎么治疗", "如何治疗", "吃什么药", "用什么药", "要不要去医院",
            "需要检查", "挂什么科", "看什么科", "是什么原因", "会不会", "能不能", "有没有必要",
            "报告怎么看", "指标异常", "术后", "复查", "复诊", "注意什么"
    );
    private static final List<String> MEDICAL_FOLLOW_UP_TERMS = List.of(
            "没有别的症状", "无其他症状", "症状", "病史", "过敏史", "体温", "服药", "用药", "疼痛程度"
    );
    private static final List<String> HOSPITAL_KNOWLEDGE_TERMS = List.of(
            "医生", "院士", "主任", "科室", "门诊", "指南", "规范", "共识", "树兰", "医院", "就诊流程", "医保", "报销"
    );
    private static final List<String> EXAMINATION_TERMS = List.of(
            "报告", "检查", "检验", "血常规", "尿常规", "肝功能", "肾功能", "血糖", "血脂", "心电图",
            "CT", "MRI", "B超", "彩超", "胃镜", "肠镜", "病理", "活检", "阳性", "阴性", "偏高", "偏低"
    );
    private static final Pattern MEDICAL_SYMPTOM_PATTERN = Pattern.compile(
            "(头痛|头晕|发热|发烧|咳嗽|鼻塞|流涕|咽痛|胸闷|胸痛|腹痛|腹泻|呕吐|恶心|便秘|皮疹|瘙痒|水肿|腰痛|关节痛|尿频|尿急|尿痛|失眠|乏力|麻木|抽搐)"
    );
    private static final Pattern MEDICAL_DISEASE_SUFFIX_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{1,18})(癌|瘤|炎|病|症|感染|结石|梗死|出血|衰竭|综合征)"
    );

    public ChatIntentResult analyze(String rawMessage) {
        String normalized = KnowledgeSearchLexicon.normalizeSearchQuery(rawMessage);
        if (!StringUtils.hasText(normalized)) {
            return skip("EMPTY", "SMALL_TALK", "空问题无需检索", 0.9d);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (isAppointmentIntent(normalized)) {
            return skip("APPOINTMENT", "APPOINTMENT", "挂号/预约类问题走工具链，不需要知识库检索", 0.95d);
        }
        if (isWeatherIntent(normalized)) {
            return skip("MCP_WEATHER", "MCP", "天气类问题走动态 MCP 工具，不需要知识库检索", 0.95d);
        }
        if (isExplicitMcpIntent(normalized, lower)) {
            return skip("MCP_TOOL", "MCP", "明确工具类问题走动态 MCP，不需要知识库检索", 0.85d);
        }
        if (isSmallTalk(normalized)) {
            return skip("SMALL_TALK", "SMALL_TALK", "寒暄类问题无需知识库检索", 0.9d);
        }
        if (isMedicalKnowledgeIntent(normalized)) {
            return required("MEDICAL_QA", "MEDICAL_QA", "医疗/院内知识问答需要知识库检索", 0.85d);
        }
        if (isHospitalKnowledgeIntent(normalized)) {
            return required("HOSPITAL_QA", "MEDICAL_QA", "院内医生、科室、指南或流程知识需要知识库检索", 0.8d);
        }
        return skip("GENERAL_CHAT", "SMALL_TALK", "未命中医疗或院内知识锚点，跳过知识库检索", 0.65d);
    }

    private boolean isAppointmentIntent(String query) {
        return query.contains("挂号")
                || query.contains("预约")
                || query.contains("取消预约")
                || query.contains("改约")
                || query.contains("号源")
                || query.matches(".*挂\\S{0,4}号.*");
    }

    private boolean isWeatherIntent(String query) {
        return containsAny(query, "天气", "温度", "气温", "降雨", "下雨", "下雪", "穿衣", "台风", "空气质量");
    }

    private boolean isExplicitMcpIntent(String query, String lower) {
        return lower.contains("mcp")
                || query.contains("工具")
                || query.contains("调用");
    }

    private boolean isSmallTalk(String query) {
        String compact = query.replaceAll("\\s+", "");
        return List.of("你好", "您好", "谢谢", "感谢", "好的", "嗯", "哦", "再见").contains(compact);
    }

    private boolean isMedicalKnowledgeIntent(String query) {
        // 用加权分数判断医疗知识需求，普通轻症也会因症状锚点进入 RAG，避免绕过院内知识库。
        return medicalIntentScore(query) >= 2;
    }

    private boolean isHospitalKnowledgeIntent(String query) {
        return containsAny(query, HOSPITAL_KNOWLEDGE_TERMS);
    }

    private boolean containsAny(String text, String... terms) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private ChatIntentResult required(String intentType, String routeTarget, String reason, double confidence) {
        return new ChatIntentResult(intentType, routeTarget, true, "", reason, confidence);
    }

    private ChatIntentResult skip(String intentType, String routeTarget, String reason, double confidence) {
        return new ChatIntentResult(intentType, routeTarget, false, reason, reason, confidence);
    }

    public record ChatIntentResult(String intentType,
                                   String routeTarget,
                                   boolean ragRequired,
                                   String ragSkipReason,
                                   String reason,
                                   double confidence) {
    }
    private int medicalIntentScore(String query) {
        int score = 0;
        if (!KnowledgeSearchLexicon.detectMedicalAnchors(query).isEmpty()) {
            score += 2;
        }
        if (containsAny(query, MEDICAL_NEED_TERMS)) {
            score += 1;
        }
        if (containsAny(query, MEDICAL_FOLLOW_UP_TERMS)) {
            score += 2;
        }
        if (containsAny(query, EXAMINATION_TERMS)) {
            score += 2;
        }
        if (MEDICAL_SYMPTOM_PATTERN.matcher(query).find()) {
            score += 2;
        }
        if (MEDICAL_DISEASE_SUFFIX_PATTERN.matcher(query).find()) {
            score += 2;
        }
        return score;
    }

    private boolean containsAny(String text, List<String> terms) {
        return containsAny(text, terms.toArray(String[]::new));
    }
}

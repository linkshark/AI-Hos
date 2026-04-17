package com.linkjb.aimed.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 聊天意图分析服务。
 *
 * 第一版只做可解释的规则分流，用来决定“这一轮是否需要知识库 RAG”。
 * 工具类问题如果仍走 RAG，容易引入无关引用并拖慢首字；所以这里把工具意图优先级放在医疗知识问答之前。
 */
@Service
public class ChatIntentAnalysisService {

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
        return !KnowledgeSearchLexicon.detectMedicalAnchors(query).isEmpty()
                || containsAny(query, "疾病", "症状", "治疗", "用药", "药", "检查", "报告", "指标", "手术", "发烧", "发热", "咳嗽", "鼻塞", "腹泻","病");
    }

    private boolean isHospitalKnowledgeIntent(String query) {
        return containsAny(query, "医生", "院士", "主任", "科室", "门诊", "指南", "规范", "共识", "树兰", "医院", "就诊流程");
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
}

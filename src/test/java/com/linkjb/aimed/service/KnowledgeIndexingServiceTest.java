package com.linkjb.aimed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIndexingServiceTest {

    private final KnowledgeIndexingService service = new KnowledgeIndexingService(
            new ObjectMapper(),
            new KnowledgeMetadataService(),
            "STRUCTURED",
            1000,
            150,
            4000,
            1200,
            900,
            1400,
            900,
            1400,
            180,
            240,
            80
    );

    @Test
    void shouldSplitGuideBySectionHeading() {
        Document document = Document.from("""
                一、诊断

                原发性肝癌的诊断应结合影像学、实验室指标与病史综合判断。

                二、治疗

                早期肝癌可结合肿瘤数量、大小和肝功能选择手术切除、消融或肝移植。
                """);

        List<TextSegment> segments = service.splitDocument(document, KnowledgeMetadataService.DOC_TYPE_GUIDE);

        assertEquals(2, segments.size());
        assertEquals("诊断", segments.get(0).metadata().getString(KnowledgeIndexingService.METADATA_SECTION_TITLE));
        assertEquals("治疗", segments.get(1).metadata().getString(KnowledgeIndexingService.METADATA_SECTION_TITLE));
        assertEquals(KnowledgeIndexingService.SEGMENTATION_MODE_STRUCTURED,
                segments.get(0).metadata().getString(KnowledgeIndexingService.METADATA_SEGMENTATION_MODE));
    }

    @Test
    void shouldSplitProcessDocumentIntoSteps() {
        Document document = Document.from("""
                就诊流程

                1. 到院后先在自助机签到
                2. 前往分诊台确认候诊区域
                3. 完成就诊后按指引缴费取药
                """);

        List<TextSegment> segments = service.splitDocument(document, KnowledgeMetadataService.DOC_TYPE_PROCESS);

        assertEquals(3, segments.size());
        assertTrue(segments.stream().allMatch(segment ->
                KnowledgeIndexingService.SEGMENT_KIND_STEP.equals(
                        segment.metadata().getString(KnowledgeIndexingService.METADATA_SEGMENT_KIND))));
    }

    @Test
    void shouldKeepDoctorProfileAsNaturalParagraphs() {
        Document document = Document.from("""
                李兰娟院士长期从事感染病学研究与临床工作，擅长复杂肝胆胰疾病的综合诊疗。

                出诊信息以医院官方安排为准，建议提前关注门诊排班并准备既往检查资料。
                """);

        List<TextSegment> segments = service.splitDocument(document, KnowledgeMetadataService.DOC_TYPE_DOCTOR);

        assertFalse(segments.isEmpty());
        assertTrue(segments.stream().allMatch(segment ->
                KnowledgeIndexingService.SEGMENTATION_MODE_STRUCTURED.equals(
                        segment.metadata().getString(KnowledgeIndexingService.METADATA_SEGMENTATION_MODE))));
        assertTrue(segments.stream().allMatch(segment -> {
            String segmentKind = segment.metadata().getString(KnowledgeIndexingService.METADATA_SEGMENT_KIND);
            return KnowledgeIndexingService.SEGMENT_KIND_PARAGRAPH.equals(segmentKind)
                    || KnowledgeIndexingService.SEGMENT_KIND_SECTION.equals(segmentKind);
        }));
    }

    @Test
    void shouldFallbackToRecursiveWhenSingleBlockIsTooLong() {
        String longParagraph = "肝癌治疗方案需要结合肿瘤大小肝功能患者基础情况综合评估".repeat(120);
        Document document = Document.from(longParagraph);

        List<TextSegment> segments = service.splitDocument(document, KnowledgeMetadataService.DOC_TYPE_GUIDE);

        assertTrue(segments.size() > 1);
        assertTrue(segments.stream().anyMatch(segment ->
                KnowledgeIndexingService.SEGMENT_KIND_FALLBACK.equals(
                        segment.metadata().getString(KnowledgeIndexingService.METADATA_SEGMENT_KIND))));
    }

    @Test
    void shouldKeepGuideListWithinSameSubsection() {
        Document document = Document.from("""
                二、治疗

                适用于早期肝癌且肝功能保留良好的患者。
                - 首选手术切除
                - 无法手术时可考虑消融
                - 需结合肿瘤大小与肝功能综合评估
                """);

        List<TextSegment> segments = service.splitDocument(document, KnowledgeMetadataService.DOC_TYPE_GUIDE);

        assertEquals(1, segments.size());
        assertTrue(segments.get(0).text().contains("首选手术切除"));
        assertTrue(segments.get(0).text().contains("综合评估"));
    }
}

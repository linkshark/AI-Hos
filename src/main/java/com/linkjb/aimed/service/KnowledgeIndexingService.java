package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.entity.vo.KnowledgeChunkInfo;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class KnowledgeIndexingService {

    private static final int CHUNK_PREVIEW_LENGTH = 180;
    public static final String SEGMENTATION_MODE_RULE_RECURSIVE = "RULE_RECURSIVE";
    public static final String SEGMENTATION_MODE_STRUCTURED = "STRUCTURED";
    public static final String SEGMENT_KIND_SECTION = "SECTION";
    public static final String SEGMENT_KIND_PARAGRAPH = "PARAGRAPH";
    public static final String SEGMENT_KIND_LIST = "LIST";
    public static final String SEGMENT_KIND_STEP = "STEP";
    public static final String SEGMENT_KIND_FALLBACK = "FALLBACK";
    public static final String METADATA_SECTION_TITLE = "section_title";
    public static final String METADATA_SEGMENT_KIND = "segment_kind";
    public static final String METADATA_SEGMENTATION_MODE = "segmentation_mode";
    public static final String METADATA_GENERATION = "generation";
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern HEADING_MARKDOWN_PATTERN = Pattern.compile("^#{1,6}\\s+.+$");
    private static final Pattern HEADING_CN_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百千万0-9]+[章节部分篇]|[一二三四五六七八九十]+[、.．]|（[一二三四五六七八九十]+）|\\d+[、.．])\\s*.+$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(?:[-*•]|\\d+[、.．)]|（[一二三四五六七八九十]+）)\\s*.+$");
    private static final Pattern REPEATED_SEPARATOR_PATTERN = Pattern.compile("(?m)^[\\-=*_·•]{4,}\\s*$");
    private static final List<String> MEDICAL_SECTION_KEYWORDS = List.of(
            "适应证", "适应症", "诊断", "临床表现", "病因", "检查", "治疗", "用药", "药物", "手术",
            "随访", "并发症", "禁忌", "预后", "预防", "注意事项", "就医指征", "回答建议",
            "导诊与安全边界", "文档说明", "适用问题", "关键知识点", "预约方式", "挂号流程",
            "就诊流程", "所需材料", "检查前准备"
    );

    private final ObjectMapper objectMapper;
    private final KnowledgeMetadataService knowledgeMetadataService;
    private final String segmentationMode;
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;
    private final int maxChunkSize;
    private final int maxSegmentsPerDocument;
    private final int guideTargetChars;
    private final int guideMaxChars;
    private final int generalTargetChars;
    private final int generalMaxChars;
    private final int minMergeChars;
    private final int maxStructureSections;
    private final int sentenceFallbackOverlapChars;

    public KnowledgeIndexingService(ObjectMapper objectMapper,
                                    KnowledgeMetadataService knowledgeMetadataService,
                                    @Value("${app.knowledge-base.segmentation-mode:STRUCTURED}") String segmentationMode,
                                    @Value("${app.knowledge-base.chunk-size:1000}") int defaultChunkSize,
                                    @Value("${app.knowledge-base.chunk-overlap:150}") int defaultChunkOverlap,
                                    @Value("${app.knowledge-base.max-chunk-size:4000}") int maxChunkSize,
                                    @Value("${app.knowledge-base.max-segments-per-document:1200}") int maxSegmentsPerDocument,
                                    @Value("${app.knowledge-base.guide-target-chars:700}") int guideTargetChars,
                                    @Value("${app.knowledge-base.guide-max-chars:1100}") int guideMaxChars,
                                    @Value("${app.knowledge-base.general-target-chars:900}") int generalTargetChars,
                                    @Value("${app.knowledge-base.general-max-chars:1400}") int generalMaxChars,
                                    @Value("${app.knowledge-base.min-merge-chars:120}") int minMergeChars,
                                    @Value("${app.knowledge-base.max-structure-sections:240}") int maxStructureSections,
                                    @Value("${app.knowledge-base.sentence-fallback-overlap-chars:80}") int sentenceFallbackOverlapChars) {
        this.objectMapper = objectMapper;
        this.knowledgeMetadataService = knowledgeMetadataService;
        this.segmentationMode = normalizeSegmentationMode(segmentationMode);
        this.defaultChunkSize = Math.max(300, defaultChunkSize);
        this.defaultChunkOverlap = Math.max(0, defaultChunkOverlap);
        this.maxChunkSize = Math.max(this.defaultChunkSize, maxChunkSize);
        this.maxSegmentsPerDocument = Math.max(50, maxSegmentsPerDocument);
        this.guideTargetChars = Math.max(300, guideTargetChars);
        this.guideMaxChars = Math.max(this.guideTargetChars, guideMaxChars);
        this.generalTargetChars = Math.max(300, generalTargetChars);
        this.generalMaxChars = Math.max(this.generalTargetChars, generalMaxChars);
        this.minMergeChars = Math.max(40, minMergeChars);
        this.maxStructureSections = Math.max(20, maxStructureSections);
        this.sentenceFallbackOverlapChars = Math.max(0, sentenceFallbackOverlapChars);
    }

    public List<TextSegment> splitDocument(Document document) {
        return splitDocument(document, null);
    }

    public List<TextSegment> splitDocument(Document document, String docType) {
        String normalizedText = preprocessText(document == null ? null : document.text());
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }
        if (!SEGMENTATION_MODE_STRUCTURED.equals(segmentationMode)) {
            return splitWithRecursiveFallback(normalizedText, defaultChunkSize, defaultChunkOverlap, null, SEGMENT_KIND_FALLBACK, SEGMENTATION_MODE_RULE_RECURSIVE);
        }

        DocTypeChunkProfile profile = resolveChunkProfile(docType);
        List<StructuredBlock> blocks = extractStructuredBlocks(normalizedText, profile);
        List<TextSegment> segments = toSegments(blocks, profile);
        if (segments.isEmpty()) {
            segments = splitWithRecursiveFallback(normalizedText, defaultChunkSize, defaultChunkOverlap, null, SEGMENT_KIND_FALLBACK, SEGMENTATION_MODE_RULE_RECURSIVE);
        }

        int chunkSize = Math.max(defaultChunkSize, profile.maxChars());
        int chunkOverlap = Math.min(defaultChunkOverlap, Math.max(0, chunkSize / 3));
        while (segments.size() > maxSegmentsPerDocument && chunkSize < maxChunkSize) {
            int nextChunkSize = Math.min(maxChunkSize, chunkSize * 2);
            if (nextChunkSize == chunkSize) {
                break;
            }
            chunkSize = nextChunkSize;
            chunkOverlap = Math.min(Math.max(50, chunkOverlap * 2), Math.max(0, chunkSize / 4));
            segments = splitWithRecursiveFallback(normalizedText, chunkSize, chunkOverlap, null, SEGMENT_KIND_FALLBACK, SEGMENTATION_MODE_RULE_RECURSIVE);
        }
        return segments;
    }

    public boolean hasMissingEmbedding(List<KnowledgeChunkIndex> chunkIndexes) {
        for (KnowledgeChunkIndex chunkIndex : chunkIndexes) {
            if (!StringUtils.hasText(chunkIndex.getEmbedding())) {
                return true;
            }
        }
        return false;
    }

    public void applyEmbeddings(List<KnowledgeChunkIndex> chunkIndexes, List<Embedding> embeddings) {
        for (int i = 0; i < chunkIndexes.size() && i < embeddings.size(); i++) {
            chunkIndexes.get(i).setEmbedding(serializeEmbedding(embeddings.get(i)));
        }
    }

    public List<KnowledgeChunkInfo> buildChunkInfos(List<TextSegment> segments) {
        List<KnowledgeChunkInfo> chunks = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeChunkInfo chunk = new KnowledgeChunkInfo();
            chunk.setIndex(i + 1);
            chunk.setContent(segment.text());
            chunk.setCharacterCount(segment.text().length());
            chunk.setPreview(buildPreview(segment.text()));
            chunk.setSectionTitle(readSegmentMetadata(segment.metadata(), METADATA_SECTION_TITLE));
            chunk.setSegmentKind(readSegmentMetadata(segment.metadata(), METADATA_SEGMENT_KIND));
            chunk.setSegmentationMode(readSegmentMetadata(segment.metadata(), METADATA_SEGMENTATION_MODE));
            chunk.setSemanticSummary(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_SUMMARY));
            chunk.setSemanticKeywords(parseKeywordList(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_KEYWORDS)));
            chunk.setSectionRole(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SECTION_ROLE));
            chunk.setMedicalEntities(parseJsonList(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_MEDICAL_ENTITIES_JSON)));
            chunk.setTargetQuestions(parseJsonList(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_TARGET_QUESTIONS_JSON)));
            chunks.add(chunk);
        }
        return chunks;
    }

    public List<KnowledgeChunkInfo> toChunkInfos(List<KnowledgeChunkIndex> indexes) {
        List<KnowledgeChunkInfo> chunks = new ArrayList<>(indexes.size());
        for (KnowledgeChunkIndex index : indexes) {
            KnowledgeChunkInfo chunk = new KnowledgeChunkInfo();
            chunk.setIndex(index.getSegmentIndex() == null ? 0 : index.getSegmentIndex());
            chunk.setContent(index.getContent());
            chunk.setCharacterCount(index.getCharacterCount() == null ? 0 : index.getCharacterCount());
            chunk.setPreview(index.getPreview());
            chunk.setSectionTitle(index.getSectionTitle());
            chunk.setSegmentKind(index.getSegmentKind());
            chunk.setSegmentationMode(index.getSegmentationMode());
            chunk.setSemanticSummary(index.getSemanticSummary());
            chunk.setSemanticKeywords(parseKeywordList(index.getSemanticKeywords()));
            chunk.setSectionRole(index.getSectionRole());
            chunk.setMedicalEntities(parseJsonList(index.getMedicalEntitiesJson()));
            chunk.setTargetQuestions(parseJsonList(index.getTargetQuestionsJson()));
            chunks.add(chunk);
        }
        return chunks;
    }

    public List<KnowledgeChunkIndex> buildChunkIndexes(String hash,
                                                       int generation,
                                                       List<TextSegment> segments,
                                                       List<Embedding> embeddings,
                                                       String status,
                                                       KnowledgeFileMetadata fileMetadata) {
        List<KnowledgeChunkIndex> indexes = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeChunkIndex index = new KnowledgeChunkIndex();
            index.setFileHash(hash);
            index.setSegmentId(hash + "-g" + generation + "-segment-" + (i + 1));
            index.setSegmentIndex(i + 1);
            index.setGeneration(generation);
            index.setContent(segment.text());
            String semanticSummary = readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_SUMMARY);
            index.setPreview(buildPreview(StringUtils.hasText(semanticSummary) ? semanticSummary : segment.text()));
            index.setCharacterCount(segment.text().length());
            index.setSectionTitle(readSegmentMetadata(segment.metadata(), METADATA_SECTION_TITLE));
            index.setSegmentKind(readSegmentMetadata(segment.metadata(), METADATA_SEGMENT_KIND));
            index.setSegmentationMode(resolveSegmentationMode(segment.metadata()));
            index.setSemanticSummary(semanticSummary);
            index.setSemanticKeywords(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_KEYWORDS));
            index.setSectionRole(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SECTION_ROLE));
            index.setMedicalEntitiesJson(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_MEDICAL_ENTITIES_JSON));
            index.setTargetQuestionsJson(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_TARGET_QUESTIONS_JSON));
            index.setSemanticEnrichedAt(parseDateTime(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_ENRICHED_AT)));
            index.setSemanticEnrichmentModel(readSegmentMetadata(segment.metadata(), KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_ENRICHMENT_MODEL));
            if (i < embeddings.size()) {
                index.setEmbedding(serializeEmbedding(embeddings.get(i)));
            }
            index.setTitle(fileMetadata.title());
            index.setDocType(fileMetadata.docType());
            index.setDepartment(fileMetadata.department());
            index.setAudience(fileMetadata.audience());
            index.setVersion(fileMetadata.version());
            index.setEffectiveAt(fileMetadata.effectiveAt());
            index.setStatus(status);
            index.setDoctorName(fileMetadata.doctorName());
            index.setSourcePriority(fileMetadata.sourcePriority());
            index.setKeywords(fileMetadata.keywords());
            indexes.add(index);
        }
        return indexes;
    }

    public List<TextSegment> toSegments(List<KnowledgeChunkIndex> chunkIndexes) {
        List<TextSegment> segments = new ArrayList<>(chunkIndexes.size());
        for (KnowledgeChunkIndex index : chunkIndexes) {
            Metadata metadata = knowledgeMetadataService.buildSegmentMetadata(
                    index.getFileHash(),
                    "uploaded",
                    null,
                    index.getStatus(),
                    new KnowledgeFileMetadata(
                            index.getDocType(),
                            index.getDepartment(),
                            index.getAudience(),
                            index.getVersion(),
                            index.getEffectiveAt(),
                            index.getTitle(),
                            index.getDoctorName(),
                            index.getSourcePriority(),
                            index.getKeywords()
                    )
            );
            if (index.getUpdatedAt() != null) {
                metadata.put("updated_at", index.getUpdatedAt().toString());
            }
            metadata.put("segment_index", index.getSegmentIndex() == null ? 0 : index.getSegmentIndex());
            metadata.put(METADATA_GENERATION, index.getGeneration() == null ? 1 : index.getGeneration());
            if (StringUtils.hasText(index.getSectionTitle())) {
                metadata.put(METADATA_SECTION_TITLE, index.getSectionTitle());
            }
            if (StringUtils.hasText(index.getSegmentKind())) {
                metadata.put(METADATA_SEGMENT_KIND, index.getSegmentKind());
            }
            metadata.put(METADATA_SEGMENTATION_MODE,
                    StringUtils.hasText(index.getSegmentationMode()) ? index.getSegmentationMode() : SEGMENTATION_MODE_RULE_RECURSIVE);
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_SUMMARY, index.getSemanticSummary());
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_KEYWORDS, index.getSemanticKeywords());
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_SECTION_ROLE, index.getSectionRole());
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_MEDICAL_ENTITIES_JSON, index.getMedicalEntitiesJson());
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_TARGET_QUESTIONS_JSON, index.getTargetQuestionsJson());
            if (index.getSemanticEnrichedAt() != null) {
                metadata.put(KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_ENRICHED_AT, index.getSemanticEnrichedAt().toString());
            }
            putMetadataIfPresent(metadata, KnowledgeChunkSemanticEnrichmentService.METADATA_SEMANTIC_ENRICHMENT_MODEL, index.getSemanticEnrichmentModel());
            segments.add(TextSegment.from(index.getContent(), metadata));
        }
        return segments;
    }

    public void restoreEmbeddingsToStore(EmbeddingStore<TextSegment> embeddingStore,
                                         List<String> segmentIds,
                                         List<TextSegment> segments,
                                         List<KnowledgeChunkIndex> chunkIndexes) {
        List<Embedding> embeddings = new ArrayList<>(chunkIndexes.size());
        List<TextSegment> availableSegments = new ArrayList<>(chunkIndexes.size());
        List<String> availableSegmentIds = new ArrayList<>(chunkIndexes.size());
        for (int i = 0; i < chunkIndexes.size(); i++) {
            String serialized = chunkIndexes.get(i).getEmbedding();
            if (!StringUtils.hasText(serialized)) {
                continue;
            }
            embeddings.add(deserializeEmbedding(serialized));
            availableSegments.add(segments.get(i));
            availableSegmentIds.add(segmentIds.get(i));
        }
        if (embeddings.isEmpty()) {
            return;
        }
        embeddingStore.addAll(availableSegmentIds, embeddings, availableSegments);
    }

    private DocumentSplitter createSplitter(int chunkSize, int chunkOverlap) {
        return DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }

    private List<StructuredBlock> extractStructuredBlocks(String normalizedText, DocTypeChunkProfile profile) {
        List<SectionBlock> sections = splitBySections(normalizedText);
        if (sections.isEmpty() || sections.size() > maxStructureSections) {
            sections = List.of(new SectionBlock(null, normalizedText));
        }
        List<StructuredBlock> blocks = new ArrayList<>();
        for (SectionBlock section : sections) {
            List<StructuredBlock> sectionBlocks = splitSection(section, profile);
            if (sectionBlocks.isEmpty()) {
                continue;
            }
            blocks.addAll(mergeSmallBlocks(sectionBlocks, profile.targetChars()));
        }
        return blocks;
    }

    private List<SectionBlock> splitBySections(String normalizedText) {
        String[] paragraphs = BLANK_LINE_PATTERN.split(normalizedText);
        List<SectionBlock> sections = new ArrayList<>();
        String currentTitle = null;
        StringBuilder buffer = new StringBuilder();
        int explicitSections = 0;
        for (String paragraph : paragraphs) {
            String trimmed = normalizeBlockText(paragraph);
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (isHeading(trimmed)) {
                explicitSections++;
                if (buffer.length() > 0) {
                    sections.add(new SectionBlock(currentTitle, buffer.toString().trim()));
                    buffer.setLength(0);
                }
                currentTitle = normalizeHeading(trimmed);
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(trimmed);
        }
        if (buffer.length() > 0) {
            sections.add(new SectionBlock(currentTitle, buffer.toString().trim()));
        }
        if (explicitSections == 0 && sections.isEmpty() && StringUtils.hasText(normalizedText)) {
            sections.add(new SectionBlock(null, normalizedText));
        }
        return sections;
    }

    private List<StructuredBlock> splitSection(SectionBlock section, DocTypeChunkProfile profile) {
        List<StructuredBlock> blocks = new ArrayList<>();
        String[] paragraphs = BLANK_LINE_PATTERN.split(section.content());
        for (String paragraph : paragraphs) {
            String trimmed = normalizeBlockText(paragraph);
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            List<String> logicalItems = splitLogicalItems(trimmed, profile.docType());
            if (logicalItems.isEmpty()) {
                logicalItems = List.of(trimmed);
            }
            for (String item : logicalItems) {
                String normalizedItem = normalizeBlockText(item);
                if (!StringUtils.hasText(normalizedItem)) {
                    continue;
                }
                String segmentKind = classifySegmentKind(normalizedItem, profile.docType(), section.title());
                if (normalizedItem.length() > profile.maxChars()) {
                    blocks.addAll(splitOversizedBlock(normalizedItem, section.title(), profile));
                    continue;
                }
                blocks.add(new StructuredBlock(section.title(), segmentKind, normalizedItem));
            }
        }
        return blocks;
    }

    private List<String> splitLogicalItems(String paragraph, String docType) {
        String[] lines = paragraph.split("\\n");
        long listLineCount = java.util.Arrays.stream(lines)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(this::isListItem)
                .count();
        if (listLineCount >= 2) {
            if (KnowledgeMetadataService.DOC_TYPE_GUIDE.equals(docType)) {
                return List.of(paragraph);
            }
            return java.util.Arrays.stream(lines)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (KnowledgeMetadataService.DOC_TYPE_PROCESS.equals(docType) && paragraph.contains("\n")) {
            return java.util.Arrays.stream(lines)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of(paragraph);
    }

    private List<StructuredBlock> mergeSmallBlocks(List<StructuredBlock> blocks, int targetChars) {
        List<StructuredBlock> merged = new ArrayList<>();
        StructuredBlock current = null;
        for (StructuredBlock block : blocks) {
            if (current == null) {
                current = block;
                continue;
            }
            boolean sameSection = normalizeValue(current.sectionTitle()).equals(normalizeValue(block.sectionTitle()));
            boolean preservesStepStructure = !SEGMENT_KIND_STEP.equals(current.segmentKind())
                    && !SEGMENT_KIND_STEP.equals(block.segmentKind())
                    && !SEGMENT_KIND_LIST.equals(current.segmentKind())
                    && !SEGMENT_KIND_LIST.equals(block.segmentKind());
            boolean mergeableKind = current.segmentKind().equals(block.segmentKind())
                    || (SEGMENT_KIND_PARAGRAPH.equals(current.segmentKind()) && SEGMENT_KIND_PARAGRAPH.equals(block.segmentKind()));
            int mergedLength = current.content().length() + block.content().length() + 2;
            if (sameSection && preservesStepStructure && mergeableKind
                    && current.content().length() < minMergeChars && mergedLength <= targetChars) {
                current = new StructuredBlock(current.sectionTitle(), current.segmentKind(),
                        current.content() + "\n\n" + block.content());
                continue;
            }
            merged.add(current);
            current = block;
        }
        if (current != null) {
            merged.add(current);
        }
        return merged;
    }

    private List<StructuredBlock> splitOversizedBlock(String content, String sectionTitle, DocTypeChunkProfile profile) {
        List<String> sentenceGroups = splitBySentenceBoundary(content, profile.maxChars(), profile.targetChars());
        if (sentenceGroups.size() <= 1) {
            List<TextSegment> fallbackSegments = splitWithRecursiveFallback(content,
                    Math.max(defaultChunkSize, profile.targetChars()),
                    Math.min(sentenceFallbackOverlapChars, Math.max(0, profile.targetChars() / 5)),
                    sectionTitle,
                    SEGMENT_KIND_FALLBACK,
                    SEGMENTATION_MODE_STRUCTURED);
            return fallbackSegments.stream()
                    .map(segment -> new StructuredBlock(
                            readSegmentMetadata(segment.metadata(), METADATA_SECTION_TITLE),
                            readSegmentMetadata(segment.metadata(), METADATA_SEGMENT_KIND),
                            segment.text()))
                    .toList();
        }
        return sentenceGroups.stream()
                .map(group -> new StructuredBlock(sectionTitle, SEGMENT_KIND_SECTION, group))
                .toList();
    }

    private List<String> splitBySentenceBoundary(String content, int maxChars, int targetChars) {
        List<String> sentences = new ArrayList<>();
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            sentence.append(current);
            if ("。！？；;".indexOf(current) >= 0 || i == content.length() - 1) {
                String trimmed = sentence.toString().trim();
                if (StringUtils.hasText(trimmed)) {
                    sentences.add(trimmed);
                }
                sentence.setLength(0);
            }
        }
        if (sentences.isEmpty()) {
            return List.of(content);
        }
        List<String> groups = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String current : sentences) {
            int projectedLength = buffer.length() + current.length() + 1;
            if (buffer.length() > 0 && projectedLength > maxChars) {
                groups.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            if (buffer.length() > 0 && buffer.length() >= targetChars) {
                groups.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append(' ');
            }
            buffer.append(current);
        }
        if (buffer.length() > 0) {
            groups.add(buffer.toString().trim());
        }
        return groups;
    }

    private List<TextSegment> toSegments(List<StructuredBlock> blocks, DocTypeChunkProfile profile) {
        List<TextSegment> segments = new ArrayList<>();
        for (StructuredBlock block : blocks) {
            if (!StringUtils.hasText(block.content())) {
                continue;
            }
            if (block.content().length() > profile.maxChars()) {
                segments.addAll(splitWithRecursiveFallback(block.content(),
                        Math.max(defaultChunkSize, profile.targetChars()),
                        Math.min(sentenceFallbackOverlapChars, Math.max(0, profile.targetChars() / 5)),
                        block.sectionTitle(),
                        SEGMENT_KIND_FALLBACK,
                        SEGMENTATION_MODE_STRUCTURED));
                continue;
            }
            segments.add(TextSegment.from(block.content(), buildSegmentMetadata(block.sectionTitle(), block.segmentKind(), SEGMENTATION_MODE_STRUCTURED)));
        }
        return segments;
    }

    private List<TextSegment> splitWithRecursiveFallback(String content,
                                                         int chunkSize,
                                                         int chunkOverlap,
                                                         String sectionTitle,
                                                         String segmentKind,
                                                         String segmentationMode) {
        Document document = Document.from(content);
        return createSplitter(chunkSize, chunkOverlap).split(document).stream()
                .map(segment -> TextSegment.from(segment.text(), buildSegmentMetadata(sectionTitle, segmentKind, segmentationMode)))
                .toList();
    }

    private Metadata buildSegmentMetadata(String sectionTitle, String segmentKind, String segmentationMode) {
        Metadata metadata = new Metadata();
        if (StringUtils.hasText(sectionTitle)) {
            metadata.put(METADATA_SECTION_TITLE, sectionTitle);
        }
        metadata.put(METADATA_SEGMENT_KIND, StringUtils.hasText(segmentKind) ? segmentKind : SEGMENT_KIND_PARAGRAPH);
        metadata.put(METADATA_SEGMENTATION_MODE, StringUtils.hasText(segmentationMode) ? segmentationMode : SEGMENTATION_MODE_RULE_RECURSIVE);
        return metadata;
    }

    private boolean isHeading(String paragraph) {
        if (!StringUtils.hasText(paragraph)) {
            return false;
        }
        String normalized = paragraph.trim();
        if (normalized.contains("\n")) {
            return false;
        }
        if (HEADING_MARKDOWN_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        if (normalized.length() <= 40 && HEADING_CN_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        return normalized.length() <= 24
                && MEDICAL_SECTION_KEYWORDS.stream().anyMatch(normalized::contains)
                && !normalized.endsWith("。");
    }

    private String normalizeHeading(String heading) {
        String normalized = heading == null ? "" : heading.trim();
        normalized = normalized.replaceFirst("^#{1,6}\\s*", "");
        normalized = normalized.replaceFirst("^(第[一二三四五六七八九十百千万0-9]+[章节部分篇]|[一二三四五六七八九十]+[、.．]|（[一二三四五六七八九十]+）|\\d+[、.．])\\s*", "");
        return normalized.trim();
    }

    private boolean isListItem(String line) {
        return StringUtils.hasText(line) && LIST_ITEM_PATTERN.matcher(line.trim()).matches();
    }

    private String classifySegmentKind(String content, String docType, String sectionTitle) {
        String normalizedTitle = normalizeValue(sectionTitle);
        String normalizedContent = normalizeValue(content);
        if (isListItem(normalizedContent) || normalizedContent.contains("\n-") || normalizedContent.contains("\n1.")) {
            if (KnowledgeMetadataService.DOC_TYPE_PROCESS.equals(docType)
                    || normalizedTitle.contains("流程")
                    || normalizedTitle.contains("步骤")
                    || normalizedTitle.contains("预约")) {
                return SEGMENT_KIND_STEP;
            }
            return SEGMENT_KIND_LIST;
        }
        if (StringUtils.hasText(sectionTitle) && normalizedContent.length() >= minMergeChars) {
            return SEGMENT_KIND_SECTION;
        }
        return SEGMENT_KIND_PARAGRAPH;
    }

    private String preprocessText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ');
        normalized = REPEATED_SEPARATOR_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("(?m)^[ \\u3000]+$", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    private String normalizeBlockText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("[ \\u3000]+", " ");
    }

    private String resolveSegmentationMode(Metadata metadata) {
        String mode = readSegmentMetadata(metadata, METADATA_SEGMENTATION_MODE);
        return StringUtils.hasText(mode) ? mode : SEGMENTATION_MODE_RULE_RECURSIVE;
    }

    private String readSegmentMetadata(Metadata metadata, String key) {
        return metadata == null ? null : metadata.getString(key);
    }

    private List<String> parseKeywordList(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        return List.of(rawValue.trim().split("\\s+")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> parseJsonList(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawValue, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private LocalDateTime parseDateTime(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return LocalDateTime.parse(rawValue.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private void putMetadataIfPresent(Metadata metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String normalizeSegmentationMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return SEGMENTATION_MODE_STRUCTURED.equals(normalized) ? SEGMENTATION_MODE_STRUCTURED : SEGMENTATION_MODE_RULE_RECURSIVE;
    }

    private DocTypeChunkProfile resolveChunkProfile(String docType) {
        String normalized = docType == null ? "" : docType.trim().toUpperCase(Locale.ROOT);
        if (KnowledgeMetadataService.DOC_TYPE_GUIDE.equals(normalized)) {
            return new DocTypeChunkProfile(normalized, guideTargetChars, guideMaxChars);
        }
        if (KnowledgeMetadataService.DOC_TYPE_PROCESS.equals(normalized)
                || KnowledgeMetadataService.DOC_TYPE_NOTICE.equals(normalized)) {
            return new DocTypeChunkProfile(normalized, Math.min(generalTargetChars, 760), Math.min(generalMaxChars, 1080));
        }
        if (KnowledgeMetadataService.DOC_TYPE_DOCTOR.equals(normalized)
                || KnowledgeMetadataService.DOC_TYPE_DEPARTMENT.equals(normalized)
                || KnowledgeMetadataService.DOC_TYPE_HOSPITAL_OVERVIEW.equals(normalized)
                || KnowledgeMetadataService.DOC_TYPE_DEVICE.equals(normalized)) {
            return new DocTypeChunkProfile(normalized, generalTargetChars, generalMaxChars);
        }
        return new DocTypeChunkProfile(normalized, generalTargetChars, generalMaxChars);
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String serializeEmbedding(Embedding embedding) {
        if (embedding == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(embedding.vectorAsList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 embedding 失败", e);
        }
    }

    private Embedding deserializeEmbedding(String serialized) {
        try {
            return Embedding.from(objectMapper.readValue(serialized, float[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化 embedding 失败", e);
        }
    }

    private String buildPreview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= CHUNK_PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
    }

    private record SectionBlock(String title, String content) {
    }

    private record StructuredBlock(String sectionTitle, String segmentKind, String content) {
    }

    private record DocTypeChunkProfile(String docType, int targetChars, int maxChars) {
    }
}

package com.linkjb.aimed.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkjb.aimed.bean.KnowledgeChunkInfo;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIndexingService {

    private static final int CHUNK_PREVIEW_LENGTH = 180;

    private final ObjectMapper objectMapper;
    private final KnowledgeMetadataService knowledgeMetadataService;
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;
    private final int maxChunkSize;
    private final int maxSegmentsPerDocument;

    public KnowledgeIndexingService(ObjectMapper objectMapper,
                                    KnowledgeMetadataService knowledgeMetadataService,
                                    @Value("${app.knowledge-base.chunk-size:1000}") int defaultChunkSize,
                                    @Value("${app.knowledge-base.chunk-overlap:150}") int defaultChunkOverlap,
                                    @Value("${app.knowledge-base.max-chunk-size:4000}") int maxChunkSize,
                                    @Value("${app.knowledge-base.max-segments-per-document:1200}") int maxSegmentsPerDocument) {
        this.objectMapper = objectMapper;
        this.knowledgeMetadataService = knowledgeMetadataService;
        this.defaultChunkSize = Math.max(300, defaultChunkSize);
        this.defaultChunkOverlap = Math.max(0, defaultChunkOverlap);
        this.maxChunkSize = Math.max(this.defaultChunkSize, maxChunkSize);
        this.maxSegmentsPerDocument = Math.max(50, maxSegmentsPerDocument);
    }

    public List<TextSegment> splitDocument(Document document) {
        int chunkSize = defaultChunkSize;
        int chunkOverlap = Math.min(defaultChunkOverlap, Math.max(0, chunkSize / 3));
        List<TextSegment> segments = createSplitter(chunkSize, chunkOverlap).split(document);
        while (segments.size() > maxSegmentsPerDocument && chunkSize < maxChunkSize) {
            int nextChunkSize = Math.min(maxChunkSize, chunkSize * 2);
            if (nextChunkSize == chunkSize) {
                break;
            }
            chunkSize = nextChunkSize;
            chunkOverlap = Math.min(Math.max(50, chunkOverlap * 2), Math.max(0, chunkSize / 4));
            segments = createSplitter(chunkSize, chunkOverlap).split(document);
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
            chunks.add(chunk);
        }
        return chunks;
    }

    public List<KnowledgeChunkIndex> buildChunkIndexes(String hash,
                                                       List<TextSegment> segments,
                                                       List<Embedding> embeddings,
                                                       String status,
                                                       KnowledgeFileMetadata fileMetadata) {
        List<KnowledgeChunkIndex> indexes = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            KnowledgeChunkIndex index = new KnowledgeChunkIndex();
            index.setFileHash(hash);
            index.setSegmentId(hash + "-segment-" + (i + 1));
            index.setSegmentIndex(i + 1);
            index.setContent(segment.text());
            index.setPreview(buildPreview(segment.text()));
            index.setCharacterCount(segment.text().length());
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
            segments.add(TextSegment.from(index.getContent(), knowledgeMetadataService.buildSegmentMetadata(
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
            )));
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
}

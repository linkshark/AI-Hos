package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import com.linkjb.aimed.mapper.KnowledgeChunkIndexMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.List;

@Service
public class KnowledgeChunkIndexService {

    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;

    public KnowledgeChunkIndexService(KnowledgeChunkIndexMapper knowledgeChunkIndexMapper) {
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
    }

    public List<KnowledgeChunkIndex> listByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return List.of();
        }
        return knowledgeChunkIndexMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .orderByAsc(KnowledgeChunkIndex::getGeneration)
                .orderByAsc(KnowledgeChunkIndex::getSegmentIndex));
    }

    public List<KnowledgeChunkIndex> listByHashAndGeneration(String hash, Integer generation) {
        if (!StringUtils.hasText(hash) || generation == null || generation <= 0) {
            return List.of();
        }
        return knowledgeChunkIndexMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .eq(KnowledgeChunkIndex::getGeneration, generation)
                .orderByAsc(KnowledgeChunkIndex::getSegmentIndex));
    }

    public boolean existsByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return false;
        }
        return knowledgeChunkIndexMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)) > 0;
    }

    public boolean existsByHashAndGeneration(String hash, Integer generation) {
        if (!StringUtils.hasText(hash) || generation == null || generation <= 0) {
            return false;
        }
        return knowledgeChunkIndexMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .eq(KnowledgeChunkIndex::getGeneration, generation)) > 0;
    }

    public void replaceAll(String hash, List<KnowledgeChunkIndex> chunks) {
        deleteByHash(hash);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        batchInsert(chunks);
    }

    public void replaceGeneration(String hash, Integer generation, List<KnowledgeChunkIndex> chunks) {
        deleteByHashAndGeneration(hash, generation);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        batchInsert(chunks);
    }

    public void insertAll(List<KnowledgeChunkIndex> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        batchInsert(chunks);
    }

    public void deleteByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return;
        }
        knowledgeChunkIndexMapper.delete(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash));
    }

    public void deleteByHashAndGeneration(String hash, Integer generation) {
        if (!StringUtils.hasText(hash) || generation == null || generation <= 0) {
            return;
        }
        knowledgeChunkIndexMapper.delete(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .eq(KnowledgeChunkIndex::getGeneration, generation));
    }

    public List<String> listSegmentIdsByHash(String hash) {
        return listByHash(hash).stream()
                .map(KnowledgeChunkIndex::getSegmentId)
                .filter(StringUtils::hasText)
                .toList();
    }

    public List<String> listSegmentIdsByHashAndGeneration(String hash, Integer generation) {
        return listByHashAndGeneration(hash, generation).stream()
                .map(KnowledgeChunkIndex::getSegmentId)
                .filter(StringUtils::hasText)
                .toList();
    }

    public void updateStatusByHash(String hash, String status) {
        if (!StringUtils.hasText(hash)) {
            return;
        }
        knowledgeChunkIndexMapper.update(null, new LambdaUpdateWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .set(KnowledgeChunkIndex::getStatus, status));
    }

    public void updateMetadataByHash(String hash,
                                     String status,
                                     String title,
                                     String docType,
                                     String department,
                                     String audience,
                                     String version,
                                     Timestamp effectiveAt,
                                     String doctorName,
                                     Integer sourcePriority,
                                     String keywords) {
        if (!StringUtils.hasText(hash)) {
            return;
        }
        knowledgeChunkIndexMapper.update(null, new LambdaUpdateWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)
                .set(KnowledgeChunkIndex::getStatus, status)
                .set(KnowledgeChunkIndex::getTitle, title)
                .set(KnowledgeChunkIndex::getDocType, docType)
                .set(KnowledgeChunkIndex::getDepartment, department)
                .set(KnowledgeChunkIndex::getAudience, audience)
                .set(KnowledgeChunkIndex::getVersion, version)
                .set(KnowledgeChunkIndex::getEffectiveAt, effectiveAt == null ? null : effectiveAt.toLocalDateTime())
                .set(KnowledgeChunkIndex::getDoctorName, doctorName)
                .set(KnowledgeChunkIndex::getSourcePriority, sourcePriority == null ? 50 : sourcePriority)
                .set(KnowledgeChunkIndex::getKeywords, keywords));
    }

    private void batchInsert(List<KnowledgeChunkIndex> chunks) {
        final int batchSize = 200;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<KnowledgeChunkIndex> batch = chunks.subList(start, end).stream()
                    .map(this::normalizeChunk)
                    .toList();
            knowledgeChunkIndexMapper.batchInsert(batch);
        }
    }

    private KnowledgeChunkIndex normalizeChunk(KnowledgeChunkIndex chunk) {
        if (chunk == null) {
            return null;
        }
        if (chunk.getSegmentIndex() == null) {
            chunk.setSegmentIndex(0);
        }
        if (chunk.getGeneration() == null || chunk.getGeneration() <= 0) {
            chunk.setGeneration(1);
        }
        if (chunk.getCharacterCount() == null) {
            chunk.setCharacterCount(0);
        }
        if (chunk.getSourcePriority() == null) {
            chunk.setSourcePriority(50);
        }
        if (!StringUtils.hasText(chunk.getSegmentKind())) {
            chunk.setSegmentKind(KnowledgeIndexingService.SEGMENT_KIND_PARAGRAPH);
        }
        if (!StringUtils.hasText(chunk.getSegmentationMode())) {
            chunk.setSegmentationMode(KnowledgeIndexingService.SEGMENTATION_MODE_RULE_RECURSIVE);
        }
        if (StringUtils.hasText(chunk.getSemanticSummary())) {
            chunk.setSemanticSummary(chunk.getSemanticSummary().trim());
        }
        if (StringUtils.hasText(chunk.getSemanticKeywords())) {
            chunk.setSemanticKeywords(chunk.getSemanticKeywords().trim());
        }
        if (StringUtils.hasText(chunk.getSectionRole())) {
            chunk.setSectionRole(chunk.getSectionRole().trim());
        }
        return chunk;
    }
}

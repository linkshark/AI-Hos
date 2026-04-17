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
                .orderByAsc(KnowledgeChunkIndex::getSegmentIndex));
    }

    public boolean existsByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return false;
        }
        return knowledgeChunkIndexMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash)) > 0;
    }

    public void replaceAll(String hash, List<KnowledgeChunkIndex> chunks) {
        deleteByHash(hash);
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
        if (chunk.getCharacterCount() == null) {
            chunk.setCharacterCount(0);
        }
        if (chunk.getSourcePriority() == null) {
            chunk.setSourcePriority(50);
        }
        return chunk;
    }
}

package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import com.linkjb.aimed.mapper.KnowledgeChunkIndexMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Service
public class KnowledgeChunkIndexService {

    private final KnowledgeChunkIndexMapper knowledgeChunkIndexMapper;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeChunkIndexService(KnowledgeChunkIndexMapper knowledgeChunkIndexMapper,
                                      JdbcTemplate jdbcTemplate) {
        this.knowledgeChunkIndexMapper = knowledgeChunkIndexMapper;
        this.jdbcTemplate = jdbcTemplate;
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
            List<KnowledgeChunkIndex> batch = chunks.subList(start, end);
            jdbcTemplate.batchUpdate("""
                            INSERT INTO knowledge_chunk_index
                            (file_hash, segment_id, segment_index, content, preview, character_count, embedding,
                             title, doc_type, department, audience, version, effective_at, status, doctor_name,
                             source_priority, keywords)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            KnowledgeChunkIndex chunk = batch.get(i);
                            ps.setString(1, chunk.getFileHash());
                            ps.setString(2, chunk.getSegmentId());
                            ps.setInt(3, chunk.getSegmentIndex() == null ? 0 : chunk.getSegmentIndex());
                            ps.setString(4, chunk.getContent());
                            ps.setString(5, chunk.getPreview());
                            ps.setInt(6, chunk.getCharacterCount() == null ? 0 : chunk.getCharacterCount());
                            ps.setString(7, chunk.getEmbedding());
                            ps.setString(8, chunk.getTitle());
                            ps.setString(9, chunk.getDocType());
                            ps.setString(10, chunk.getDepartment());
                            ps.setString(11, chunk.getAudience());
                            ps.setString(12, chunk.getVersion());
                            ps.setTimestamp(13, chunk.getEffectiveAt() == null ? null : java.sql.Timestamp.valueOf(chunk.getEffectiveAt()));
                            ps.setString(14, chunk.getStatus());
                            ps.setString(15, chunk.getDoctorName());
                            ps.setInt(16, chunk.getSourcePriority() == null ? 50 : chunk.getSourcePriority());
                            ps.setString(17, chunk.getKeywords());
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    });
        }
    }
}

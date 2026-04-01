package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import com.linkjb.aimed.mapper.KnowledgeChunkIndexMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    private void batchInsert(List<KnowledgeChunkIndex> chunks) {
        final int batchSize = 200;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<KnowledgeChunkIndex> batch = chunks.subList(start, end);
            jdbcTemplate.batchUpdate("""
                            INSERT INTO knowledge_chunk_index
                            (file_hash, segment_id, segment_index, content, preview, character_count, local_embedding, online_embedding)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                            ps.setString(7, chunk.getLocalEmbedding());
                            ps.setString(8, chunk.getOnlineEmbedding());
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    });
        }
    }
}

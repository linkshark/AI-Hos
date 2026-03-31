package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import com.linkjb.aimed.mapper.KnowledgeChunkIndexMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        for (KnowledgeChunkIndex chunk : chunks) {
            knowledgeChunkIndexMapper.insert(chunk);
        }
    }

    public void deleteByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return;
        }
        knowledgeChunkIndexMapper.delete(new LambdaQueryWrapper<KnowledgeChunkIndex>()
                .eq(KnowledgeChunkIndex::getFileHash, hash));
    }
}

package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.KnowledgeFileStatus;
import com.linkjb.aimed.mapper.KnowledgeFileStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgeFileStatusService {

    private final KnowledgeFileStatusMapper knowledgeFileStatusMapper;

    public KnowledgeFileStatusService(KnowledgeFileStatusMapper knowledgeFileStatusMapper) {
        this.knowledgeFileStatusMapper = knowledgeFileStatusMapper;
    }

    public List<KnowledgeFileStatus> listAll() {
        return knowledgeFileStatusMapper.selectList(new LambdaQueryWrapper<KnowledgeFileStatus>()
                .orderByAsc(KnowledgeFileStatus::getSource)
                .orderByAsc(KnowledgeFileStatus::getOriginalFilename));
    }

    public KnowledgeFileStatus findByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return null;
        }
        return knowledgeFileStatusMapper.selectOne(new LambdaQueryWrapper<KnowledgeFileStatus>()
                .eq(KnowledgeFileStatus::getHash, hash)
                .last("limit 1"));
    }

    public boolean existsByHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return false;
        }
        return knowledgeFileStatusMapper.selectCount(new LambdaQueryWrapper<KnowledgeFileStatus>()
                .eq(KnowledgeFileStatus::getHash, hash)) > 0;
    }

    public void saveOrUpdate(KnowledgeFileStatus status) {
        KnowledgeFileStatus existing = findByHash(status.getHash());
        if (existing == null) {
            knowledgeFileStatusMapper.insert(status);
            return;
        }
        status.setId(existing.getId());
        status.setCreatedAt(existing.getCreatedAt());
        knowledgeFileStatusMapper.updateById(status);
    }

    public void deleteByHash(String hash) {
        knowledgeFileStatusMapper.delete(new LambdaQueryWrapper<KnowledgeFileStatus>()
                .eq(KnowledgeFileStatus::getHash, hash));
    }

    public void deleteAll() {
        knowledgeFileStatusMapper.delete(null);
    }
}

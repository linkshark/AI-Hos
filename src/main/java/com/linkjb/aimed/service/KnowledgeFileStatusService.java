package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
        return knowledgeFileStatusMapper.selectList(new LambdaQueryWrapper<KnowledgeFileStatus>()
                        .eq(KnowledgeFileStatus::getHash, hash)
                        .orderByDesc(KnowledgeFileStatus::getId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
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
        status.setId(null);
        status.setCreatedAt(existing.getCreatedAt());
        knowledgeFileStatusMapper.update(status, new LambdaUpdateWrapper<KnowledgeFileStatus>()
                .eq(KnowledgeFileStatus::getHash, status.getHash())
                .eq(KnowledgeFileStatus::getId, existing.getId()));
    }

    public void deleteByHash(String hash) {
        knowledgeFileStatusMapper.delete(new LambdaQueryWrapper<KnowledgeFileStatus>()
                .eq(KnowledgeFileStatus::getHash, hash));
    }

    public void deleteAll() {
        knowledgeFileStatusMapper.delete(null);
    }
}

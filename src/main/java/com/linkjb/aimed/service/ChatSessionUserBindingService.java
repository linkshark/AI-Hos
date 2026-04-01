package com.linkjb.aimed.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ChatSessionUserBindingService {

    private final ConcurrentMap<Long, Long> sessionUserMap = new ConcurrentHashMap<>();

    public void bind(Long memoryId, Long userId) {
        if (memoryId == null || userId == null) {
            return;
        }
        sessionUserMap.put(memoryId, userId);
    }

    public Long resolveUserId(Long memoryId) {
        return memoryId == null ? null : sessionUserMap.get(memoryId);
    }
}

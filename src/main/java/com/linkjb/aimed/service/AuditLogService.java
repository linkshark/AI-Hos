package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.bean.AuditLogItem;
import com.linkjb.aimed.bean.PagedResponse;
import com.linkjb.aimed.config.TraceIdProvider;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.entity.AuditLog;
import com.linkjb.aimed.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    public static final String TARGET_USER = "USER";
    public static final String TARGET_AUTH = "AUTH";
    public static final String TARGET_KNOWLEDGE = "KNOWLEDGE";
    public static final String TARGET_CHAT = "CHAT";

    public static final String ACTION_AUTH_REGISTER = "AUTH_REGISTER";
    public static final String ACTION_AUTH_LOGIN = "AUTH_LOGIN";
    public static final String ACTION_AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String ACTION_AUTH_REFRESH = "AUTH_REFRESH";
    public static final String ACTION_AUTH_PASSWORD_RESET = "AUTH_PASSWORD_RESET";
    public static final String ACTION_ADMIN_CREATE_DOCTOR = "ADMIN_CREATE_DOCTOR";
    public static final String ACTION_ADMIN_UPDATE_ROLE = "ADMIN_UPDATE_ROLE";
    public static final String ACTION_ADMIN_UPDATE_STATUS = "ADMIN_UPDATE_STATUS";
    public static final String ACTION_KNOWLEDGE_UPLOAD = "KNOWLEDGE_UPLOAD";
    public static final String ACTION_KNOWLEDGE_UPDATE = "KNOWLEDGE_UPDATE";
    public static final String ACTION_KNOWLEDGE_DELETE = "KNOWLEDGE_DELETE";
    public static final String ACTION_CHAT_SUMMARY = "CHAT_SUMMARY";

    private final AuditLogMapper auditLogMapper;
    private final AppUserService appUserService;
    private final TraceIdProvider traceIdProvider;

    public AuditLogService(AuditLogMapper auditLogMapper,
                           AppUserService appUserService,
                           TraceIdProvider traceIdProvider) {
        this.auditLogMapper = auditLogMapper;
        this.appUserService = appUserService;
        this.traceIdProvider = traceIdProvider;
    }

    public void recordAuthAction(Long actorUserId,
                                 String actorRole,
                                 String actionType,
                                 String targetId,
                                 String summary) {
        record(actorUserId, actorRole, actionType, TARGET_AUTH, targetId, summary, null, null, null, null, null);
    }

    public void recordAdminAction(Long actorUserId,
                                  String actorRole,
                                  String actionType,
                                  String targetId,
                                  String summary) {
        record(actorUserId, actorRole, actionType, TARGET_USER, targetId, summary, null, null, null, null, null);
    }

    public void recordKnowledgeAction(Long actorUserId,
                                      String actorRole,
                                      String actionType,
                                      String targetId,
                                      String summary) {
        record(actorUserId, actorRole, actionType, TARGET_KNOWLEDGE, targetId, summary, null, null, null, null, null);
    }

    public void recordChatSummary(Long actorUserId,
                                  String actorRole,
                                  Long memoryId,
                                  String provider,
                                  long durationMs,
                                  boolean hasAttachments,
                                  String traceId) {
        String summary = "会话 #" + memoryId + " 使用 " + provider + (hasAttachments ? " 发起附件问答" : " 发起文本问答");
        record(actorUserId, actorRole, ACTION_CHAT_SUMMARY, TARGET_CHAT, String.valueOf(memoryId), summary,
                traceId, memoryId, provider, durationMs, hasAttachments);
    }

    public PagedResponse<AuditLogItem> listLogs(int page,
                                                int size,
                                                String actionType,
                                                String targetType,
                                                String keyword,
                                                Long actorUserId,
                                                LocalDateTime createdFrom,
                                                LocalDateTime createdTo) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(actionType)) {
            wrapper.eq(AuditLog::getActionType, actionType.trim().toUpperCase());
        }
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(AuditLog::getTargetType, targetType.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(condition -> condition
                    .like(AuditLog::getSummary, normalizedKeyword)
                    .or()
                    .like(AuditLog::getTraceId, normalizedKeyword)
                    .or()
                    .like(AuditLog::getTargetId, normalizedKeyword)
                    .or()
                    .like(AuditLog::getProvider, normalizedKeyword));
        }
        if (actorUserId != null) {
            wrapper.eq(AuditLog::getActorUserId, actorUserId);
        }
        if (createdFrom != null) {
            wrapper.ge(AuditLog::getCreatedAt, createdFrom);
        }
        if (createdTo != null) {
            wrapper.le(AuditLog::getCreatedAt, createdTo);
        }

        long total = auditLogMapper.selectCount(wrapper);
        List<AuditLog> records = total == 0
                ? Collections.emptyList()
                : auditLogMapper.selectList(wrapper.orderByDesc(AuditLog::getCreatedAt, AuditLog::getId)
                .last("LIMIT " + ((safePage - 1) * safeSize) + ", " + safeSize));

        Set<Long> actorIds = records.stream()
                .map(AuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AppUser> userMap = actorIds.isEmpty() ? Collections.emptyMap() : appUserService.mapByIds(actorIds);

        List<AuditLogItem> items = records.stream()
                .map(log -> new AuditLogItem(
                        log.getId(),
                        log.getActorUserId(),
                        toActorLabel(userMap.get(log.getActorUserId())),
                        log.getActorRole(),
                        log.getActionType(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getSummary(),
                        log.getTraceId(),
                        log.getMemoryId(),
                        log.getProvider(),
                        log.getDurationMs(),
                        log.getHasAttachments(),
                        log.getCreatedAt()
                ))
                .toList();

        return new PagedResponse<>(total, safePage, safeSize, items);
    }

    private void record(Long actorUserId,
                        String actorRole,
                        String actionType,
                        String targetType,
                        String targetId,
                        String summary,
                        String traceId,
                        Long memoryId,
                        String provider,
                        Long durationMs,
                        Boolean hasAttachments) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setActorRole(actorRole);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSummary(summary);
        log.setTraceId(StringUtils.hasText(traceId) ? traceId : traceIdProvider.currentTraceId());
        log.setMemoryId(memoryId);
        log.setProvider(provider);
        log.setDurationMs(durationMs);
        log.setHasAttachments(hasAttachments);
        auditLogMapper.insert(log);
    }

    private String toActorLabel(AppUser user) {
        if (user == null) {
            return "未知用户";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname() + " (" + user.getEmail() + ")";
        }
        return user.getEmail();
    }
}

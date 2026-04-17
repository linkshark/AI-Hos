package com.linkjb.aimed.controller;

import com.linkjb.aimed.entity.vo.KnowledgeDocumentDetail;
import com.linkjb.aimed.entity.dto.request.KnowledgeBatchActionRequest;
import com.linkjb.aimed.entity.vo.KnowledgeFileInfo;
import com.linkjb.aimed.entity.dto.request.KnowledgeUpdateRequest;
import com.linkjb.aimed.entity.dto.response.KnowledgeUploadResponse;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.AuditLogService;
import com.linkjb.aimed.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Knowledge")
@RestController
@RequestMapping("/aimed/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService,
                               AuditLogService auditLogService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "上传本地知识库文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeUploadResponse uploadKnowledge(@RequestParam("files") MultipartFile[] files,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        KnowledgeUploadResponse response = knowledgeBaseService.upload(files);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_UPLOAD,
                files == null ? "0" : String.valueOf(files.length),
                "上传知识文件 " + (files == null ? 0 : files.length) + " 个");
        return response;
    }

    @Operation(summary = "查看已上传的本地知识库文件")
    @GetMapping("/files")
    public List<KnowledgeFileInfo> knowledgeFiles() throws IOException {
        return knowledgeBaseService.listUploadedFiles();
    }

    @Operation(summary = "查看单个知识文件详情及切分结果")
    @GetMapping("/files/{hash}")
    public KnowledgeDocumentDetail knowledgeFileDetail(@PathVariable("hash") String hash) throws IOException {
        return knowledgeBaseService.getKnowledgeDetail(hash);
    }

    @Operation(summary = "更新单个知识文件内容")
    @PutMapping("/files/{hash}")
    public KnowledgeDocumentDetail updateKnowledgeFile(@PathVariable("hash") String hash,
                                                       @RequestBody KnowledgeUpdateRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        KnowledgeDocumentDetail detail = knowledgeBaseService.updateKnowledgeContent(hash, request == null ? null : request.getContent(), request);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_UPDATE,
                hash,
                "更新知识文件 " + hash);
        return detail;
    }

    @Operation(summary = "删除单个知识文件")
    @DeleteMapping("/files/{hash}")
    public void deleteKnowledgeFile(@PathVariable("hash") String hash,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        knowledgeBaseService.deleteKnowledge(hash);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_DELETE,
                hash,
                "删除知识文件 " + hash);
    }

    @Operation(summary = "发布单个知识文件")
    @PostMapping("/files/{hash}/publish")
    public KnowledgeDocumentDetail publishKnowledgeFile(@PathVariable("hash") String hash,
                                                        @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        KnowledgeDocumentDetail detail = knowledgeBaseService.publishKnowledge(hash);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_PUBLISH,
                hash,
                "发布知识文件 " + hash);
        return detail;
    }

    @Operation(summary = "归档单个知识文件")
    @PostMapping("/files/{hash}/archive")
    public KnowledgeDocumentDetail archiveKnowledgeFile(@PathVariable("hash") String hash,
                                                        @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        KnowledgeDocumentDetail detail = knowledgeBaseService.archiveKnowledge(hash);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_ARCHIVE,
                hash,
                "归档知识文件 " + hash);
        return detail;
    }

    @Operation(summary = "重新处理单个知识文件")
    @PostMapping("/files/{hash}/reprocess")
    public KnowledgeDocumentDetail reprocessKnowledgeFile(@PathVariable("hash") String hash,
                                                          @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        KnowledgeDocumentDetail detail = knowledgeBaseService.reprocessKnowledge(hash);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                AuditLogService.ACTION_KNOWLEDGE_REPROCESS,
                hash,
                "重新处理知识文件 " + hash);
        return detail;
    }

    @Operation(summary = "批量发布知识文件")
    @PostMapping("/files/batch/publish")
    public List<KnowledgeDocumentDetail> publishKnowledgeFiles(@RequestBody KnowledgeBatchActionRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        List<KnowledgeDocumentDetail> details = new ArrayList<>();
        for (String hash : request == null ? List.<String>of() : request.getHashes()) {
            KnowledgeDocumentDetail detail = knowledgeBaseService.publishKnowledge(hash);
            details.add(detail);
            auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                    AuditLogService.ACTION_KNOWLEDGE_PUBLISH, hash, "批量发布知识文件 " + hash);
        }
        return details;
    }

    @Operation(summary = "批量归档知识文件")
    @PostMapping("/files/batch/archive")
    public List<KnowledgeDocumentDetail> archiveKnowledgeFiles(@RequestBody KnowledgeBatchActionRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser currentUser) throws IOException {
        List<KnowledgeDocumentDetail> details = new ArrayList<>();
        for (String hash : request == null ? List.<String>of() : request.getHashes()) {
            KnowledgeDocumentDetail detail = knowledgeBaseService.archiveKnowledge(hash);
            details.add(detail);
            auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                    AuditLogService.ACTION_KNOWLEDGE_ARCHIVE, hash, "批量归档知识文件 " + hash);
        }
        return details;
    }
}

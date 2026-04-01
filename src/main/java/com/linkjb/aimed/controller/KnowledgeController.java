package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.KnowledgeDocumentDetail;
import com.linkjb.aimed.bean.KnowledgeFileInfo;
import com.linkjb.aimed.bean.KnowledgeUpdateRequest;
import com.linkjb.aimed.bean.KnowledgeUploadResponse;
import com.linkjb.aimed.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
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
import java.util.List;

@Tag(name = "Knowledge")
@RestController
@RequestMapping("/aimed/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "上传本地知识库文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeUploadResponse uploadKnowledge(@RequestParam("files") MultipartFile[] files) {
        return knowledgeBaseService.upload(files);
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
                                                       @RequestBody KnowledgeUpdateRequest request) throws IOException {
        return knowledgeBaseService.updateKnowledgeContent(hash, request == null ? null : request.getContent());
    }

    @Operation(summary = "删除单个知识文件")
    @DeleteMapping("/files/{hash}")
    public void deleteKnowledgeFile(@PathVariable("hash") String hash) throws IOException {
        knowledgeBaseService.deleteKnowledge(hash);
    }
}

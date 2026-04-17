package com.linkjb.aimed.controller;

import com.linkjb.aimed.entity.dto.response.medical.MedicalStandardSummaryResponse;
import com.linkjb.aimed.entity.vo.medical.MedicalKnowledgeMappingItem;
import com.linkjb.aimed.entity.dto.request.medical.MedicalKnowledgeMappingSaveRequest;
import com.linkjb.aimed.entity.vo.medical.MedicalSymptomMappingItem;
import com.linkjb.aimed.entity.dto.request.medical.MedicalSymptomMappingRequest;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.AuditLogService;
import com.linkjb.aimed.service.MedicalKnowledgeMappingService;
import com.linkjb.aimed.service.MedicalStandardLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "MedicalStandardAdmin")
@RestController
@RequestMapping("/aimed/admin/medical-standards")
public class MedicalStandardAdminController {

    private final MedicalStandardLookupService medicalStandardLookupService;
    private final MedicalKnowledgeMappingService medicalStandardKnowledgeMappingService;
    private final AuditLogService auditLogService;

    public MedicalStandardAdminController(MedicalStandardLookupService medicalStandardLookupService,
                                MedicalKnowledgeMappingService medicalStandardKnowledgeMappingService,
                                AuditLogService auditLogService) {
        this.medicalStandardLookupService = medicalStandardLookupService;
        this.medicalStandardKnowledgeMappingService = medicalStandardKnowledgeMappingService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "查询疾病标准目录摘要")
    @GetMapping("/summary")
    public MedicalStandardSummaryResponse summary() {
        return medicalStandardLookupService.summary();
    }

    @Operation(summary = "查看疾病标准症状主诉映射")
    @GetMapping("/symptoms")
    public List<MedicalSymptomMappingItem> symptomMappings(@RequestParam(value = "keyword", required = false) String keyword,
                                                           @RequestParam(value = "conceptCode", required = false) String conceptCode) {
        return medicalStandardLookupService.listSymptomMappings(keyword, conceptCode);
    }

    @Operation(summary = "保存疾病标准症状主诉映射")
    @PostMapping("/symptoms")
    public MedicalSymptomMappingItem saveSymptomMapping(@Valid @RequestBody MedicalSymptomMappingRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        MedicalSymptomMappingItem item = medicalStandardLookupService.saveSymptomMapping(request);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                "MEDICAL_STANDARD_SYMPTOM_MAPPING_SAVE", item.conceptCode(), "保存疾病标准症状主诉映射");
        return item;
    }

    @Operation(summary = "删除疾病标准症状主诉映射")
    @DeleteMapping("/symptoms/{id}")
    public void deleteSymptomMapping(@PathVariable("id") Long id,
                                     @AuthenticationPrincipal AuthenticatedUser currentUser) {
        medicalStandardLookupService.deleteSymptomMapping(id);
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                "MEDICAL_STANDARD_SYMPTOM_MAPPING_DELETE", String.valueOf(id), "删除疾病标准症状主诉映射");
    }

    @Operation(summary = "查看知识文档疾病映射")
    @GetMapping("/knowledge/mappings")
    public List<MedicalKnowledgeMappingItem> knowledgeMappings(@RequestParam("hash") String hash) {
        return medicalStandardKnowledgeMappingService.listMappings(hash);
    }

    @Operation(summary = "手动保存知识文档疾病映射")
    @PutMapping("/knowledge/mappings")
    public List<MedicalKnowledgeMappingItem> saveKnowledgeMappings(@Valid @RequestBody MedicalKnowledgeMappingSaveRequest request,
                                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<MedicalKnowledgeMappingItem> response = medicalStandardKnowledgeMappingService.replaceMappings(request.hash(), request.conceptCodes());
        auditLogService.recordKnowledgeAction(currentUser.userId(), currentUser.role(),
                "MEDICAL_STANDARD_DOC_MAPPING_SAVE", request.hash(), "手动保存知识文档疾病映射");
        return response;
    }
}

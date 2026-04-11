package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.KnowledgeFileStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMetadataServiceTest {

    private final KnowledgeMetadataService service = new KnowledgeMetadataService();

    @Test
    void shouldBackfillOnlyPlaceholderMetadata() {
        KnowledgeFileStatus status = new KnowledgeFileStatus();
        status.setHash("hash-1");
        status.setOriginalFilename("李兰娟院士肝胆胰门诊指南.md");
        status.setSource("uploaded");
        status.setExtension("md");
        status.setDocType("GUIDE");
        status.setDepartment("通用");
        status.setAudience("BOTH");
        status.setVersion("v1");
        status.setTitle("未命名知识文件");
        status.setDoctorName(null);
        status.setSourcePriority(60);
        status.setKeywords("");

        KnowledgeMetadataService.MetadataBackfillPlan plan = service.buildBackfillPlan(status);

        assertEquals("李兰娟院士肝胆胰门诊指南.md", plan.metadata().title());
        assertEquals(DeptInfoService.GENERAL_DEPT_CODE, plan.metadata().department());
        assertEquals("李兰娟", plan.metadata().doctorName());
        assertTrue(plan.metadata().keywords().contains("指南"));
        assertEquals(4, plan.changes().size());
        assertTrue(plan.skippedReasons().isEmpty());
    }

    @Test
    void shouldKeepManualMetadataUntouched() {
        KnowledgeFileStatus status = new KnowledgeFileStatus();
        status.setHash("hash-2");
        status.setOriginalFilename("专家门诊指南.md");
        status.setSource("uploaded");
        status.setExtension("md");
        status.setDocType("GUIDE");
        status.setDepartment("NEUROLOGY");
        status.setAudience("BOTH");
        status.setVersion("v2");
        status.setTitle("神经内科专家门诊");
        status.setDoctorName("张三");
        status.setSourcePriority(70);
        status.setKeywords("神经内科 专家");

        KnowledgeMetadataService.MetadataBackfillPlan plan = service.buildBackfillPlan(status);

        assertTrue(plan.changes().isEmpty());
        assertFalse(plan.skippedReasons().isEmpty());
        assertEquals("神经内科专家门诊", plan.metadata().title());
        assertEquals("NEUROLOGY", plan.metadata().department());
        assertEquals("张三", plan.metadata().doctorName());
    }
}

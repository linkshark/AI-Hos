package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.DeptInfo;
import com.linkjb.aimed.mapper.DeptInfoMapper;
import com.linkjb.aimed.service.impl.DeptInfoServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeptInfoServiceImplTest {

    @Test
    void shouldNormalizeGeneralKnowledgeDepartment() {
        DeptInfoService service = new DeptInfoServiceImpl(mock(DeptInfoMapper.class));

        assertEquals(DeptInfoService.GENERAL_DEPT_CODE, service.normalizeKnowledgeDeptCode(null));
        assertEquals(DeptInfoService.GENERAL_DEPT_CODE, service.normalizeKnowledgeDeptCode("通用"));
        assertEquals(DeptInfoService.GENERAL_DEPT_CODE, service.normalizeKnowledgeDeptCode(" GENERAL "));
    }

    @Test
    void shouldRejectUnknownKnowledgeDepartment() {
        DeptInfoMapper mapper = mock(DeptInfoMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        DeptInfoService service = new DeptInfoServiceImpl(mapper);

        assertThrows(IllegalArgumentException.class, () -> service.normalizeKnowledgeDeptCode("UNKNOWN"));
    }

    @Test
    void shouldLetParentDepartmentAccessChildDocument() {
        DeptInfo parent = dept(1L, "PARENT", 0L);
        DeptInfo child = dept(2L, "CHILD", 1L);
        DeptInfoMapper mapper = mock(DeptInfoMapper.class);
        when(mapper.selectOne(any())).thenReturn(parent, child);
        when(mapper.selectById(1L)).thenReturn(parent);
        DeptInfoService service = new DeptInfoServiceImpl(mapper);

        assertTrue(service.canAccessKnowledgeDept("PARENT", "CHILD"));
    }

    private DeptInfo dept(Long id, String deptCode, Long parentId) {
        DeptInfo deptInfo = new DeptInfo();
        deptInfo.setId(id);
        deptInfo.setDeptCode(deptCode);
        deptInfo.setDeptName(deptCode);
        deptInfo.setParentId(parentId);
        deptInfo.setStatus(1);
        return deptInfo;
    }
}

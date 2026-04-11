package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linkjb.aimed.entity.DeptInfo;
import com.linkjb.aimed.entity.vo.DeptTreeVO;

import java.util.List;

/**
 * 科室信息Service接口
 * 定义基础CRUD+分页+树形查询核心方法
 */
public interface DeptInfoService {

    String GENERAL_DEPT_CODE = "GENERAL";
    String GENERAL_DEPT_NAME = "通用";

    /**
     * 新增科室
     * @param deptInfo 科室信息
     * @return 新增结果
     */
    boolean addDept(DeptInfo deptInfo);

    /**
     * 修改科室信息
     * @param deptInfo 科室信息
     * @return 修改结果
     */
    boolean updateDept(DeptInfo deptInfo);

    /**
     * 根据ID删除科室
     * @param id 科室ID
     * @return 删除结果
     */
    boolean deleteDept(Long id);

    /**
     * 根据ID查询科室详情
     * @param id 科室ID
     * @return 科室详情
     */
    DeptInfo getDeptById(Long id);

    /**
     * 分页查询科室（支持拼音模糊匹配）
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<DeptInfo> getDeptPage(Integer current, Integer size, String keyword);

    /**
     * 获取科室树形结构
     * @return 树形层级科室列表
     */
    List<DeptTreeVO> getDeptTree();

    /**
     * 归一化知识文档科室字段。通用文档统一落库为 GENERAL，其余值必须是启用科室的 deptCode。
     *
     * @param deptCode 前端传入的科室编码
     * @return GENERAL 或启用科室 deptCode
     */
    String normalizeKnowledgeDeptCode(String deptCode);

    /**
     * 获取启用科室名称，用于展示或补充检索信息。
     *
     * @param deptCode 科室编码
     * @return 科室名称；通用返回“通用”；未知编码返回空字符串
     */
    String getEnabledDeptName(String deptCode);

    /**
     * 判断授权科室是否能覆盖文档科室。通用文档对全部可见，父科室授权覆盖子科室。
     *
     * @param authorizedDeptCode 授权科室编码
     * @param documentDeptCode 文档科室编码
     * @return 是否可访问
     */
    boolean canAccessKnowledgeDept(String authorizedDeptCode, String documentDeptCode);
}

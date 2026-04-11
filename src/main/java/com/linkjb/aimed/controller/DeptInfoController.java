package com.linkjb.aimed.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linkjb.aimed.entity.DeptInfo;
import com.linkjb.aimed.entity.vo.DeptTreeVO;
import com.linkjb.aimed.service.DeptInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 医院科室管理Controller
 * 提供完整的增删改查、分页搜索、树形结构查询接口
 */
@RestController
@RequestMapping("/aimed/dept")
public class DeptInfoController {

    @Autowired
    private DeptInfoService deptInfoService;

    /**
     * 1. 新增科室
     * 请求方式：POST
     * 请求地址：/dept/add
     * 请求体：科室信息JSON
     */
    @PostMapping("/add")
    public boolean addDept(@RequestBody DeptInfo deptInfo) {
        return deptInfoService.addDept(deptInfo);
    }

    /**
     * 2. 修改科室信息
     * 请求方式：PUT
     * 请求地址：/dept/update
     * 请求体：科室信息JSON（必须带id）
     */
    @PutMapping("/update")
    public boolean updateDept(@RequestBody DeptInfo deptInfo) {
        return deptInfoService.updateDept(deptInfo);
    }

    /**
     * 3. 根据ID删除科室
     * 请求方式：DELETE
     * 请求地址：/dept/delete/{id}
     * 路径参数：id=科室ID
     */
    @DeleteMapping("/delete/{id}")
    public boolean deleteDept(@PathVariable Long id) {
        return deptInfoService.deleteDept(id);
    }

    /**
     * 4. 根据ID查询科室详情
     * 请求方式：GET
     * 请求地址：/dept/get/{id}
     * 路径参数：id=科室ID
     */
    @GetMapping("/get/{id}")
    public DeptInfo getDeptById(@PathVariable Long id) {
        return deptInfoService.getDeptById(id);
    }

    /**
     * 5. 分页查询科室（支持拼音部分模糊匹配）
     * 请求方式：GET
     * 请求地址：/dept/page
     * 请求参数：
     *  current：当前页码（默认1）
     *  size：每页条数（默认10）
     *  keyword：搜索关键词（可选，支持拼音/中文，如fk匹配妇科/妇产科）
     * 示例：/dept/page?current=1&size=10&keyword=fk
     */
    @GetMapping("/page")
    public IPage<DeptInfo> getDeptPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return deptInfoService.getDeptPage(current, size, keyword);
    }

    /**
     * 6. 获取科室树形结构
     * 请求方式：GET
     * 请求地址：/dept/tree
     * 返回：完整的层级树形科室列表，仅返回启用的科室
     */
    @GetMapping("/tree")
    public List<DeptTreeVO> getDeptTree() {
        return deptInfoService.getDeptTree();
    }
}

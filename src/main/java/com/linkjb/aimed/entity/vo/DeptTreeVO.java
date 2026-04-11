package com.linkjb.aimed.entity.vo;


import lombok.Data;
import java.util.List;

/**
 * 科室树形结构返回VO
 * 用于前端树形菜单/层级选择器展示
 */
@Data
public class DeptTreeVO {

    /**
     * 科室ID
     */
    private Long id;

    /**
     * 科室编码
     */
    private String deptCode;

    /**
     * 科室名称
     */
    private String deptName;

    /**
     * 上级科室ID
     */
    private Long parentId;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 科室类型
     */
    private String deptType;

    /**
     * 子科室列表
     */
    private List<DeptTreeVO> children;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getDeptType() {
        return deptType;
    }

    public void setDeptType(String deptType) {
        this.deptType = deptType;
    }

    public List<DeptTreeVO> getChildren() {
        return children;
    }

    public void setChildren(List<DeptTreeVO> children) {
        this.children = children;
    }
}

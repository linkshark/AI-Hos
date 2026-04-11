package com.linkjb.aimed.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 医院科室信息实体类
 * 对应数据库表：dept_info
 */
@Data
@TableName("dept_info")
public class DeptInfo {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 科室编码（全局唯一，系统内标识）
     */
    private String deptCode;

    /**
     * 科室标准名称
     */
    private String deptName;

    /**
     * 科室名称拼音首字母编码（用于模糊检索，如妇产科=fck）
     */
    private String pinyinCode;

    /**
     * 科室分类：临床科室/医技科室/行政科室/后勤科室
     */
    private String deptType;

    /**
     * 上级科室ID（0=顶级科室，无上级）
     */
    private Long parentId;

    /**
     * 排序号（用于前端列表/树形展示顺序）
     */
    private Integer sort;

    /**
     * 科室状态：0-停用 1-正常启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 科室备注（诊疗范围、专科特色等说明）
     */
    private String remark;

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

    public String getPinyinCode() {
        return pinyinCode;
    }

    public void setPinyinCode(String pinyinCode) {
        this.pinyinCode = pinyinCode;
    }

    public String getDeptType() {
        return deptType;
    }

    public void setDeptType(String deptType) {
        this.deptType = deptType;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

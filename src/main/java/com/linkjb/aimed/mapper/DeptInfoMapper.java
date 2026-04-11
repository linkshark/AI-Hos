package com.linkjb.aimed.mapper;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linkjb.aimed.entity.DeptInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 科室信息Mapper接口
 * 继承BaseMapper获得基础CRUD能力，自定义扩展分页/树形查询
 */
@Mapper
public interface DeptInfoMapper extends BaseMapper<DeptInfo> {

    /**
     * 自定义分页查询：支持拼音首字母部分匹配+科室名称模糊匹配
     * @param page 分页对象
     * @param keyword 搜索关键词（支持拼音/中文，如fk匹配妇科/妇产科）
     * @return 分页结果
     */
    IPage<DeptInfo> selectDeptPage(Page<DeptInfo> page, @Param("keyword") String keyword);

    /**
     * 查询所有启用的科室（用于树形结构组装）
     * @return 科室列表
     */
    List<DeptInfo> selectAllEnableDept();
}
package com.linkjb.aimed.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linkjb.aimed.entity.DeptInfo;
import com.linkjb.aimed.entity.vo.DeptTreeVO;
import com.linkjb.aimed.mapper.DeptInfoMapper;
import com.linkjb.aimed.service.DeptInfoService;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 科室信息Service实现类
 * 实现基础CRUD、拼音自动生成、分页查询、树形结构组装
 */
@Service
public class DeptInfoServiceImpl implements DeptInfoService {

    private final DeptInfoMapper deptInfoMapper;

    public DeptInfoServiceImpl(DeptInfoMapper deptInfoMapper) {
        this.deptInfoMapper = deptInfoMapper;
    }

    // 拼音格式化配置（全局复用）
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT;
    static {
        PINYIN_FORMAT = new HanyuPinyinOutputFormat();
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE); // 小写输出
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE); // 无声调
    }

    @Override
    public boolean addDept(DeptInfo deptInfo) {
        // 新增时自动生成拼音首字母编码
        if (StringUtils.hasText(deptInfo.getDeptName())) {
            deptInfo.setPinyinCode(convertToPinyinCode(deptInfo.getDeptName()));
        }
        // 状态默认启用
        if (deptInfo.getStatus() == null) {
            deptInfo.setStatus(1);
        }
        // 排序默认0
        if (deptInfo.getSort() == null) {
            deptInfo.setSort(0);
        }
        // 上级科室默认0（顶级）
        if (deptInfo.getParentId() == null) {
            deptInfo.setParentId(0L);
        }
        return deptInfoMapper.insert(deptInfo) > 0;
    }

    @Override
    public boolean updateDept(DeptInfo deptInfo) {
        // 科室名称变更时，同步更新拼音首字母编码
        if (StringUtils.hasText(deptInfo.getDeptName())) {
            deptInfo.setPinyinCode(convertToPinyinCode(deptInfo.getDeptName()));
        }
        return deptInfoMapper.updateById(deptInfo) > 0;
    }

    @Override
    public boolean deleteDept(Long id) {
        return deptInfoMapper.deleteById(id) > 0;
    }

    @Override
    public DeptInfo getDeptById(Long id) {
        return deptInfoMapper.selectById(id);
    }

    @Override
    public IPage<DeptInfo> getDeptPage(Integer current, Integer size, String keyword) {
        Page<DeptInfo> page = new Page<>(current, size);
        return deptInfoMapper.selectDeptPage(page, keyword);
    }

    @Override
    public List<DeptTreeVO> getDeptTree() {
        // 1. 查询所有启用的科室
        List<DeptInfo> allDeptList = deptInfoMapper.selectAllEnableDept();
        // 2. 先查出所有顶级科室（parent_id=0）
        List<DeptTreeVO> topDeptList = allDeptList.stream()
                .filter(dept -> dept.getParentId()==0)
                .map(this::convertToTreeVO)
                .collect(Collectors.toList());
        // 3. 递归为每个顶级科室组装子科室
        for (DeptTreeVO topDept : topDeptList) {
            topDept.setChildren(getChildrenDept(topDept.getId(), allDeptList));
        }
        return topDeptList;
    }

    @Override
    public String normalizeKnowledgeDeptCode(String deptCode) {
        String normalized = normalizeDeptCode(deptCode);
        if (isGeneralDeptCode(normalized)) {
            return GENERAL_DEPT_CODE;
        }
        if (findEnabledDeptByCode(normalized) == null) {
            throw new IllegalArgumentException("无效科室编码：" + normalized);
        }
        return normalized;
    }

    @Override
    public String getEnabledDeptName(String deptCode) {
        String normalized = normalizeDeptCode(deptCode);
        if (isGeneralDeptCode(normalized)) {
            return GENERAL_DEPT_NAME;
        }
        DeptInfo deptInfo = findEnabledDeptByCode(normalized);
        return deptInfo == null ? "" : deptInfo.getDeptName();
    }

    @Override
    public boolean canAccessKnowledgeDept(String authorizedDeptCode, String documentDeptCode) {
        String normalizedDocumentDeptCode = normalizeDeptCode(documentDeptCode);
        if (isGeneralDeptCode(normalizedDocumentDeptCode)) {
            return true;
        }
        String normalizedAuthorizedDeptCode = normalizeDeptCode(authorizedDeptCode);
        if (isGeneralDeptCode(normalizedAuthorizedDeptCode)) {
            return true;
        }
        DeptInfo authorizedDept = findEnabledDeptByCode(normalizedAuthorizedDeptCode);
        DeptInfo documentDept = findEnabledDeptByCode(normalizedDocumentDeptCode);
        if (authorizedDept == null || documentDept == null) {
            return false;
        }
        if (Objects.equals(authorizedDept.getId(), documentDept.getId())) {
            return true;
        }
        Long parentId = documentDept.getParentId();
        while (parentId != null && parentId > 0) {
            DeptInfo parent = deptInfoMapper.selectById(parentId);
            if (parent == null || !Integer.valueOf(1).equals(parent.getStatus())) {
                return false;
            }
            if (Objects.equals(authorizedDept.getId(), parent.getId())) {
                return true;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    /**
     * 递归组装子科室
     * @param parentId 父科室ID
     * @param allDeptList 所有科室列表
     * @return 子科室树形列表
     */
    private List<DeptTreeVO> getChildrenDept(Long parentId, List<DeptInfo> allDeptList) {
        // 筛选当前父科室的子科室
        List<DeptTreeVO> childrenList = allDeptList.stream()
                .filter(dept -> parentId.equals(dept.getParentId()))
                .map(this::convertToTreeVO)
                .collect(Collectors.toList());
        // 递归为每个子科室组装下一级子科室
        for (DeptTreeVO childDept : childrenList) {
            childDept.setChildren(getChildrenDept(childDept.getId(), allDeptList));
        }
        return childrenList;
    }

    /**
     * 实体类转树形VO
     * @param deptInfo 科室实体
     * @return 树形VO
     */
    private DeptTreeVO convertToTreeVO(DeptInfo deptInfo) {
        DeptTreeVO vo = new DeptTreeVO();
        BeanUtils.copyProperties(deptInfo, vo);
        return vo;
    }

    private DeptInfo findEnabledDeptByCode(String deptCode) {
        if (!StringUtils.hasText(deptCode)) {
            return null;
        }
        return deptInfoMapper.selectOne(new LambdaQueryWrapper<DeptInfo>()
                .eq(DeptInfo::getDeptCode, deptCode.trim())
                .eq(DeptInfo::getStatus, 1)
                .last("LIMIT 1"));
    }

    private boolean isGeneralDeptCode(String deptCode) {
        return !StringUtils.hasText(deptCode)
                || GENERAL_DEPT_CODE.equalsIgnoreCase(deptCode.trim())
                || GENERAL_DEPT_NAME.equals(deptCode.trim());
    }

    private String normalizeDeptCode(String deptCode) {
        return StringUtils.hasText(deptCode) ? deptCode.trim() : GENERAL_DEPT_CODE;
    }

    /**
     * 工具方法：中文名称转拼音首字母编码
     * 示例：妇产科 → fck，妇科 → fk
     * @param chineseName 中文科室名称
     * @return 拼音首字母编码
     */
    private String convertToPinyinCode(String chineseName) {
        if (!StringUtils.hasText(chineseName)) {
            return "";
        }
        StringBuilder code = new StringBuilder();
        char[] chars = chineseName.toCharArray();
        for (char c : chars) {
            // 匹配中文字符
            if (Character.toString(c).matches("[\\u4E00-\\u9FA5]+")) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        code.append(pinyinArray[0].charAt(0));
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            } else {
                // 非中文字符直接追加（如数字、字母）
                code.append(c);
            }
        }
        return code.toString();
    }
}

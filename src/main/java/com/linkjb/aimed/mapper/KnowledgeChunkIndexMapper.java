package com.linkjb.aimed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linkjb.aimed.entity.KnowledgeChunkIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkIndexMapper extends BaseMapper<KnowledgeChunkIndex> {

    void batchInsert(@Param("chunks") List<KnowledgeChunkIndex> chunks);
}

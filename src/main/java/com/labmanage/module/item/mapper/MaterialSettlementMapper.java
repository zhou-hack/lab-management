package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialSettlementMapper extends BaseMapper<MaterialSettlement> {

    @Select("""
            SELECT * FROM material_settlement
            WHERE material_document_id = #{documentId} AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MaterialSettlement> selectByDocumentIdForUpdate(@Param("documentId") Long documentId);
}

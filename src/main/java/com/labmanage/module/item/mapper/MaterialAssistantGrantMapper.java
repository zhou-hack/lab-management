package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialAssistantGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialAssistantGrantMapper extends BaseMapper<MaterialAssistantGrant> {

    @Select("SELECT * FROM material_assistant_grant WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MaterialAssistantGrant selectByIdForUpdate(@Param("id") Long id);
}

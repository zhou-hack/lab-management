package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.Building;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 楼栋 Mapper
 */
@Mapper
public interface BuildingMapper extends BaseMapper<Building> {

    /**
     * 楼栋内未删除实验室数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM lab WHERE building_id = #{buildingId} AND deleted = 0")
    long countLabs(@Param("buildingId") Long buildingId);

    /**
     * 引用该楼栋的有效数据范围记录数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM sys_user_data_scope WHERE building_id = #{buildingId} AND deleted = 0")
    long countDataScopes(@Param("buildingId") Long buildingId);
}

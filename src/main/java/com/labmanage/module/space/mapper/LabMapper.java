package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.Lab;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 实验室 Mapper
 */
@Mapper
public interface LabMapper extends BaseMapper<Lab> {

    /**
     * 实验室内未删除设备数（删除校验；equipment_asset 由设备模块维护，
     * 表已由 sql/equipment_module.sql 建立）
     */
    @Select("SELECT COUNT(*) FROM equipment_asset WHERE lab_id = #{labId} AND deleted = 0")
    long countAssets(@Param("labId") Long labId);

    /**
     * 引用该实验室的有效数据范围记录数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM sys_user_data_scope WHERE lab_id = #{labId} AND deleted = 0")
    long countDataScopes(@Param("labId") Long labId);
}

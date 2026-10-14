package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备分类 Mapper
 */
@Mapper
public interface EquipmentCategoryMapper extends BaseMapper<EquipmentCategory> {

    /**
     * 分类下未删除子分类数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM equipment_category WHERE parent_id = #{categoryId} AND deleted = 0")
    long countChildren(@Param("categoryId") Long categoryId);

    /**
     * 分类下未删除型号数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM equipment_model WHERE category_id = #{categoryId} AND deleted = 0")
    long countModels(@Param("categoryId") Long categoryId);
}

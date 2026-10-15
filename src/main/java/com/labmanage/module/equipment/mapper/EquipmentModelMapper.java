package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备型号 Mapper
 */
@Mapper
public interface EquipmentModelMapper extends BaseMapper<EquipmentModel> {

    /**
     * 型号下未删除设备数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM equipment_asset WHERE model_id = #{modelId} AND deleted = 0")
    long countAssets(@Param("modelId") Long modelId);
}

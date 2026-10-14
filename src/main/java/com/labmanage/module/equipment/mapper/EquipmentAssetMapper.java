package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备台账 Mapper
 */
@Mapper
public interface EquipmentAssetMapper extends BaseMapper<EquipmentAsset> {

    /**
     * 行锁读取设备（迁移等占用变更前，必须在事务内先锁资产行再复查）
     */
    @Select("SELECT * FROM equipment_asset WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EquipmentAsset selectByIdForUpdate(@Param("id") Long id);
}

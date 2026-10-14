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
     * 行锁读取设备（借用/审批/借还等占用变更前，必须在事务内先锁资产行再复查）
     */
    @Select("SELECT * FROM equipment_asset WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EquipmentAsset selectByIdForUpdate(@Param("id") Long id);

    /**
     * 设备是否存在未完结借用单（删除校验：待审批/已批准/借出中）
     */
    @Select("""
            SELECT COUNT(*) FROM equipment_booking
            WHERE asset_id = #{assetId} AND deleted = 0
              AND status IN ('PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED', 'CHECKED_OUT')
            """)
    long countActiveBookings(@Param("assetId") Long assetId);
}

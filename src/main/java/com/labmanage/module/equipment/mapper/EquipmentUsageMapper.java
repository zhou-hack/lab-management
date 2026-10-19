package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 设备借出归还流水 Mapper
 */
@Mapper
public interface EquipmentUsageMapper extends BaseMapper<EquipmentUsage> {

    /** 按借用单查流水（booking_id 唯一，最多一行） */
    @Select("SELECT * FROM equipment_usage WHERE booking_id = #{bookingId} AND deleted = 0 LIMIT 1")
    EquipmentUsage selectByBookingId(Long bookingId);
}

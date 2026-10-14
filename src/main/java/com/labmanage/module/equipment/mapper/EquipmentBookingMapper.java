package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.equipment.entity.EquipmentBooking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 器材借用单 Mapper
 */
@Mapper
public interface EquipmentBookingMapper extends BaseMapper<EquipmentBooking> {

    /**
     * 半开区间 [start_at, end_at) 冲突计数：与指定时段相交的 APPROVED/CHECKED_OUT 单
     * <p>
     * 时间参数为 UTC LocalDateTime；excludeBookingId 用于复查自身单据时排除自己
     */
    @Select("""
            SELECT COUNT(*) FROM equipment_booking
            WHERE asset_id = #{assetId}
              AND deleted = 0
              AND status IN ('APPROVED', 'CHECKED_OUT')
              AND start_at < #{endAt}
              AND end_at > #{startAt}
              AND (#{excludeBookingId} IS NULL OR id <> #{excludeBookingId})
            """)
    long countConflicts(@Param("assetId") Long assetId,
                        @Param("startAt") LocalDateTime startAt,
                        @Param("endAt") LocalDateTime endAt,
                        @Param("excludeBookingId") Long excludeBookingId);

    /**
     * 设备当前是否存在未归还/未完结的借用单（归还后资产状态重算依据）
     */
    @Select("""
            SELECT COUNT(*) FROM equipment_booking
            WHERE asset_id = #{assetId}
              AND deleted = 0
              AND status IN ('APPROVED', 'CHECKED_OUT')
              AND (#{excludeBookingId} IS NULL OR id <> #{excludeBookingId})
            """)
    long countActiveExcluding(@Param("assetId") Long assetId,
                              @Param("excludeBookingId") Long excludeBookingId);
}

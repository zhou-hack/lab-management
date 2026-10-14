package com.labmanage.module.equipment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 预约限制查询（user_booking_restriction 表建表于 sys_rbac.sql，
 * 本期仅由设备借用链在提交时校验使用）
 */
@Mapper
public interface BookingRestrictionMapper {

    /**
     * 用户在指定时段内是否有生效的 EQUIPMENT/ALL 限制
     * <p>
     * 已撤销（revoked_at 非空）或已结束（end_at 早于时段起点）的限制不拦截；
     * 时间参数为 UTC LocalDateTime，半开区间相交判定。
     */
    @Select("""
            SELECT COUNT(*) FROM user_booking_restriction
            WHERE user_id = #{userId}
              AND deleted = 0
              AND restriction_type IN ('EQUIPMENT', 'ALL')
              AND revoked_at IS NULL
              AND start_at < #{endAt}
              AND (end_at IS NULL OR end_at > #{startAt})
            """)
    long countActiveRestrictions(@Param("userId") Long userId,
                                 @Param("startAt") LocalDateTime startAt,
                                 @Param("endAt") LocalDateTime endAt);
}

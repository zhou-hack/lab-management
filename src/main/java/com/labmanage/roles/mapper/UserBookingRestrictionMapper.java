package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.UserBookingRestriction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * 预约限制 Mapper。
 *
 * 管理端页面与接口已于 2026-10-21 下线；表与本 Mapper 保留，
 * 供预约模块落地后通过 countActive 做下单拦截、逾期自动冻结写入。
 */
@Mapper
public interface UserBookingRestrictionMapper extends BaseMapper<UserBookingRestriction> {

    /**
     * 指定时间点生效中的限制条数（ALL 覆盖 EQUIPMENT/LAB；预约模块落地后用于下单拦截）。
     */
    @Select("""
            SELECT COUNT(*) FROM user_booking_restriction
            WHERE deleted = 0 AND user_id = #{userId}
              AND revoked_at IS NULL
              AND start_at &lt;= #{at}
              AND (end_at IS NULL OR end_at &gt; #{at})
              AND (restriction_type = 'ALL' OR restriction_type = #{restrictionType})
            """)
    long countActive(@Param("userId") Long userId, @Param("restrictionType") String restrictionType,
                     @Param("at") Instant at);
}

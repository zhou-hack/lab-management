package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.RestrictionPageQueryDTO;
import com.labmanage.roles.entity.UserBookingRestriction;
import com.labmanage.roles.vo.RestrictionPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * 预约限制 Mapper
 */
@Mapper
public interface UserBookingRestrictionMapper extends BaseMapper<UserBookingRestriction> {

    /**
     * 限制分页列表（join 被限制用户信息；状态筛选用 UTC 当前时间比较；ID 列 CAST 为字符）。
     */
    @Select("""
            <script>
            SELECT CAST(r.id AS CHAR) AS id,
                   CAST(r.user_id AS CHAR) AS userId,
                   u.real_name AS userName,
                   u.account_name AS accountName,
                   u.person_no AS personNo,
                   u.user_type AS userType,
                   r.restriction_type AS restrictionType,
                   r.reason AS reason,
                   r.start_at AS startAt,
                   r.end_at AS endAt,
                   r.revoked_at AS revokedAt,
                   r.create_time AS createTime
            FROM user_booking_restriction r
            JOIN sys_user u ON u.id = r.user_id AND u.deleted = 0
            WHERE r.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.account_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.person_no LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.restrictionType != null and query.restrictionType != ''">
                AND r.restriction_type = #{query.restrictionType}
            </if>
            <if test="query.status == 'REVOKED'">
                AND r.revoked_at IS NOT NULL
            </if>
            <if test="query.status == 'ACTIVE'">
                AND r.revoked_at IS NULL AND r.start_at &lt;= UTC_TIMESTAMP(3)
                AND (r.end_at IS NULL OR r.end_at &gt; UTC_TIMESTAMP(3))
            </if>
            <if test="query.status == 'PENDING'">
                AND r.revoked_at IS NULL AND r.start_at &gt; UTC_TIMESTAMP(3)
            </if>
            <if test="query.status == 'ENDED'">
                AND r.revoked_at IS NULL AND r.end_at IS NOT NULL AND r.end_at &lt;= UTC_TIMESTAMP(3)
            </if>
            ORDER BY r.id DESC
            </script>
            """)
    Page<RestrictionPageItemVO> selectRestrictionPage(Page<RestrictionPageItemVO> page,
                                                      @Param("query") RestrictionPageQueryDTO query);

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

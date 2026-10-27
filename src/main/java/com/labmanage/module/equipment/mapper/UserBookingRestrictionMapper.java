package com.labmanage.module.equipment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * 用户申请权限限制 Mapper（user_booking_restriction 表，RBAC 11 表之一；
 * 本模块仅用于「逾期未还冻结 7 天」，其余限制场景后续模块按需复用）。
 * <p>
 * 幂等：uk(source_document_id, restriction_type) 唯一键 + INSERT...WHERE NOT EXISTS
 * 双重保障，扫描/惰性推进重跑不会重复冻结或延长 end_at。
 */
@Mapper
public interface UserBookingRestrictionMapper {

    /**
     * 逾期冻结写入（幂等）：借出到期未还进入 OVERDUE 时调用。
     * end_at 固定 = 写入时刻 + 7 天，重跑不延长。
     */
    @Insert("""
            INSERT INTO user_booking_restriction
                (id, user_id, source_document_id, restriction_type, reason, start_at, end_at, deleted)
            SELECT #{id}, #{userId}, #{documentId}, 'ALL',
                   '借出到期未归还，冻结设备申请权限 7 天',
                   UTC_TIMESTAMP(3), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), 0
            FROM DUAL
            WHERE NOT EXISTS (
                SELECT 1 FROM user_booking_restriction
                WHERE source_document_id = #{documentId} AND restriction_type = 'ALL' AND deleted = 0
            )
            """)
    int insertOverdueFreeze(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("documentId") Long documentId);

    /**
     * 查询当前用户有效限制的结束时间（未过期或长期限制），用于申请拦截。
     */
    @Select("""
            SELECT end_at FROM user_booking_restriction
            WHERE user_id = #{userId} AND deleted = 0
              AND restriction_type IN ('ALL', 'EQUIPMENT')
              AND (end_at IS NULL OR end_at > UTC_TIMESTAMP(3))
            ORDER BY end_at ASC
            LIMIT 1
            """)
    Instant selectActiveRestrictionEndAt(@Param("userId") Long userId);
}

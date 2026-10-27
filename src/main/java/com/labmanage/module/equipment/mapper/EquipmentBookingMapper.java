package com.labmanage.module.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.entity.EquipmentBooking;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * 设备使用申请单 Mapper
 */
@Mapper
public interface EquipmentBookingMapper extends BaseMapper<EquipmentBooking> {

    /**
     * 申请单分页联查（单据号/设备/型号/申请人/审批教师/驳回信息）。
     * 数据范围由 service 层填充：applicantId=本人申请、teacherId=教师待办、
     * 两者皆空=管理员全量。
     * 驳回原因/时间取 document_event 最新 REJECT 事件（REJECTED 为终态，至多一条）。
     */
    @Select("""
            <script>
            SELECT b.id, d.document_no, b.asset_id, a.asset_code,
                   m.equipment_name, m.model_code, m.brand,
                   b.applicant_id, u.real_name AS applicant_name,
                   b.approval_teacher_id, t.real_name AS approval_teacher_name,
                   b.purpose, b.start_at, b.end_at, b.status,
                   b.cancel_reason, b.cancelled_at,
                   re.reason AS reject_reason, re.occurred_at AS rejected_at,
                   b.create_time
            FROM equipment_booking b
            JOIN biz_document d ON d.id = b.document_id AND d.deleted = 0
            JOIN equipment_asset a ON a.id = b.asset_id
            JOIN equipment_model m ON m.id = a.model_id
            JOIN sys_user u ON u.id = b.applicant_id
            LEFT JOIN sys_user t ON t.id = b.approval_teacher_id
            LEFT JOIN (
                SELECT document_id, reason, occurred_at
                FROM document_event
                WHERE event_type = 'REJECT' AND deleted = 0
            ) re ON re.document_id = d.id
            WHERE b.deleted = 0
            <if test="q.applicantId != null">AND b.applicant_id = #{q.applicantId}</if>
            <if test="q.teacherId != null">AND b.approval_teacher_id = #{q.teacherId}</if>
            <if test="q.status != null">AND b.status = #{q.status}</if>
            ORDER BY b.create_time DESC
            </script>
            """)
    Page<BookingVO> selectBookingPage(Page<BookingVO> page, @Param("q") BookingPageQuery query);

    /**
     * 时段冲突计数：同设备已存在 APPROVED/CHECKED_OUT 且与 [startAt, endAt)
     * 相交的其他申请单。excludeId 传 -1 表示不排除任何单据。
     */
    @Select("""
            SELECT COUNT(*) FROM equipment_booking
            WHERE asset_id = #{assetId}
              AND deleted = 0
              AND id != #{excludeId}
              AND status IN ('APPROVED', 'CHECKED_OUT')
              AND start_at < #{endAt}
              AND end_at > #{startAt}
            """)
    long countOverlap(@Param("assetId") Long assetId,
                      @Param("startAt") Instant startAt,
                      @Param("endAt") Instant endAt,
                      @Param("excludeId") Long excludeId);

    /** 申请单行锁（借出/归还与审批重验使用） */
    @Select("SELECT * FROM equipment_booking WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EquipmentBooking selectByIdForUpdate(Long id);

    /** 审批教师下拉（TEACHER 角色启用用户） */
    @Select("""
            SELECT u.id, u.real_name
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_code = 'TEACHER' AND u.enabled = 1 AND u.deleted = 0
            ORDER BY u.id
            """)
    List<TeacherOptionVO> selectTeacherOptions();

    /**
     * 逾期未还候选单：到期仍未归还的 CHECKED_OUT 单（惰性推进置 OVERDUE 用）。
     */
    @Select("""
            SELECT id, document_id, applicant_id
            FROM equipment_booking
            WHERE status = 'CHECKED_OUT'
              AND end_at < UTC_TIMESTAMP(3)
              AND deleted = 0
            """)
    List<EquipmentBooking> selectOverdueCandidates();

    /**
     * 批量置 OVERDUE（仅作用于仍为 CHECKED_OUT 的单，单向一次，避免重复冻结）。
     */
    @Update("""
            <script>
            UPDATE equipment_booking SET status = 'OVERDUE'
            WHERE status = 'CHECKED_OUT' AND deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int advanceOverdueByIds(@Param("ids") List<Long> ids);
}

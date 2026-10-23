package com.labmanage.module.item.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 跨模块只读引用：lab_booking 场地预约单（场地预约模块表，物料侧仅做只读
 * 展示，不修改其数据；场地预约后端合入前本地库可能无数据，候选为空属预期）
 * <p>
 * 用于申领单关联场地预约的候选搜索与课程信息解析。
 */
@Mapper
public interface LabBookingRefMapper {

    /**
     * 预约单候选搜索：场地/课程/班级/用途前缀匹配可关联状态
     * （PENDING/APPROVED）的预约单（只读，最多 20 条，按预约日期倒序）
     */
    @Select("""
            <script>
            SELECT b.id, l.lab_name AS labName, b.course_name AS courseName,
                   b.class_name AS className, b.purpose, b.booking_date AS bookingDate,
                   b.start_at AS startAt, b.end_at AS endAt, b.status
            FROM lab_booking b
            JOIN lab l ON b.lab_id = l.id
            WHERE b.deleted = 0 AND b.status IN ('PENDING', 'APPROVED')
            <if test="keyword != null and keyword != ''">
              AND (l.lab_name LIKE CONCAT(#{keyword}, '%')
                   OR b.course_name LIKE CONCAT(#{keyword}, '%')
                   OR b.class_name LIKE CONCAT(#{keyword}, '%')
                   OR b.purpose LIKE CONCAT(#{keyword}, '%'))
            </if>
            ORDER BY b.booking_date DESC, b.start_at DESC, b.id DESC
            LIMIT 20
            </script>
            """)
    List<BookingOption> searchOptionsByKeyword(@Param("keyword") String keyword);

    /**
     * 预约单候选选项（新建申领单选择关联预约）
     */
    record BookingOption(Long id, String labName, String courseName, String className,
                         String purpose, java.time.LocalDate bookingDate,
                         java.time.Instant startAt, java.time.Instant endAt, String status) {
    }

    /**
     * 按预约单 ID 批量解析课程信息（申领单列表/详情展示课程名称用）
     */
    @Select("""
            <script>
            SELECT id, course_name AS courseName, class_name AS className,
                   booking_date AS bookingDate
            FROM lab_booking
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='i' open='(' separator=',' close=')'>
                #{i}
            </foreach>
            </script>
            """)
    List<BookingCourse> selectCourseByIds(@Param("ids") List<Long> ids);

    /**
     * 按课程/班级名称关键字查预约单 ID（申领单课程名称筛选用，包含匹配，最多 500 条）
     */
    @Select("""
            SELECT id FROM lab_booking
            WHERE deleted = 0
              AND (course_name LIKE CONCAT('%', #{keyword}, '%')
                   OR class_name LIKE CONCAT('%', #{keyword}, '%'))
            LIMIT 500
            """)
    List<Long> searchIdsByCourseKeyword(@Param("keyword") String keyword);

    /**
     * 预约单课程信息（申领单展示用）
     */
    record BookingCourse(Long id, String courseName, String className,
                         java.time.LocalDate bookingDate) {
    }
}

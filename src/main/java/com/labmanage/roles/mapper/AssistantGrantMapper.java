package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.GrantPageQueryDTO;
import com.labmanage.roles.entity.AssistantGrant;
import com.labmanage.roles.vo.GrantPageItemVO;
import com.labmanage.roles.vo.StudentOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 助理授权 Mapper
 */
@Mapper
public interface AssistantGrantMapper extends BaseMapper<AssistantGrant> {

    /**
     * 是否存在有效授权：ACTIVE 且 start_at <= now < end_at（半开区间）。
     */
    @Select("""
            SELECT COUNT(1) FROM assistant_grant
            WHERE deleted = 0 AND status = 'ACTIVE' AND student_id = #{studentId}
              AND start_at <= #{now} AND end_at > #{now}
            """)
    int countValidGrant(@Param("studentId") Long studentId, @Param("now") Instant now);

    /**
     * 授权分页列表（join 老师/学生姓名；展示状态按 UTC 当前时间窗口计算；
     * teacherId 为 NULL 时表示审计全量查询）。
     */
    @Select("""
            <script>
            SELECT CAST(g.id AS CHAR) AS id,
                   CAST(g.teacher_id AS CHAR) AS teacherId,
                   t.real_name AS teacherName,
                   CAST(g.student_id AS CHAR) AS studentId,
                   s.real_name AS studentName,
                   s.person_no AS studentNo,
                   g.start_at AS startAt,
                   g.end_at AS endAt,
                   CASE WHEN g.status = 'REVOKED' THEN 'REVOKED'
                        WHEN g.end_at &lt;= UTC_TIMESTAMP(3) THEN 'EXPIRED'
                        ELSE 'ACTIVE' END AS status,
                   g.revoked_at AS revokedAt,
                   r.real_name AS revokedByName,
                   g.reason AS reason,
                   g.create_time AS createTime
            FROM assistant_grant g
            JOIN sys_user t ON t.id = g.teacher_id AND t.deleted = 0
            JOIN sys_user s ON s.id = g.student_id AND s.deleted = 0
            LEFT JOIN sys_user r ON r.id = g.revoked_by
            WHERE g.deleted = 0
            <if test="teacherId != null">
                AND g.teacher_id = #{teacherId}
            </if>
            <if test="query.keyword != null and query.keyword != ''">
                AND (s.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR s.person_no LIKE CONCAT('%', #{query.keyword}, '%')
                     OR t.real_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.status == 'ACTIVE'">
                AND g.status = 'ACTIVE' AND g.end_at > UTC_TIMESTAMP(3)
            </if>
            <if test="query.status == 'REVOKED'">
                AND g.status = 'REVOKED'
            </if>
            <if test="query.status == 'EXPIRED'">
                AND (g.status = 'EXPIRED' OR (g.status = 'ACTIVE' AND g.end_at &lt;= UTC_TIMESTAMP(3)))
            </if>
            ORDER BY g.create_time DESC, g.id DESC
            </script>
            """)
    Page<GrantPageItemVO> selectGrantPage(Page<GrantPageItemVO> page, @Param("teacherId") Long teacherId,
                                          @Param("query") GrantPageQueryDTO query);

    /**
     * 被授权学生搜索：有效学生账号，按姓名/学工号模糊匹配，最多 20 条。
     */
    @Select("""
            <script>
            SELECT CAST(u.id AS CHAR) AS id,
                   u.real_name AS realName,
                   u.person_no AS personNo
            FROM sys_user u
            WHERE u.deleted = 0 AND u.user_type = 'STUDENT' AND u.enabled = 1
            <if test="keyword != null and keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                     OR u.person_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY u.person_no ASC
            LIMIT 20
            </script>
            """)
    List<StudentOptionVO> selectStudentOptions(@Param("keyword") String keyword);
}

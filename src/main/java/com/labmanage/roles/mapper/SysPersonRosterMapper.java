package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.vo.PersonPageItemVO;
import com.labmanage.roles.vo.RosterExportRowVO;
import com.labmanage.roles.vo.RosterPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预录名单 Mapper
 */
@Mapper
public interface SysPersonRosterMapper extends BaseMapper<SysPersonRoster> {

    /**
     * 名单分页列表（join 部门名；matched 由是否存在引用该名单的有效用户得出；
     * ID 列 CAST 为字符，避免 JavaScript 精度损失）。
     */
    @Select("""
            <script>
            SELECT CAST(r.id AS CHAR) AS id,
                   r.person_no AS personNo,
                   r.person_type AS personType,
                   r.real_name AS realName,
                   r.phone AS phone,
                   CAST(r.department_id AS CHAR) AS departmentId,
                   d.department_name AS departmentName,
                   r.enabled AS enabled,
                   (u.id IS NOT NULL) AS matched,
                   r.create_time AS createTime
            FROM sys_person_roster r
            LEFT JOIN sys_department d ON d.id = r.department_id AND d.deleted = 0
            LEFT JOIN sys_user u ON u.roster_id = r.id AND u.deleted = 0
            WHERE r.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (r.person_no LIKE CONCAT('%', #{query.keyword}, '%')
                     OR r.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR r.phone LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.personType != null and query.personType != ''">
                AND r.person_type = #{query.personType}
            </if>
            <if test="query.matched != null and query.matched == 1">
                AND u.id IS NOT NULL
            </if>
            <if test="query.matched != null and query.matched == 0">
                AND u.id IS NULL
            </if>
            ORDER BY r.create_time DESC, r.id DESC
            </script>
            """)
    Page<RosterPageItemVO> selectRosterPage(Page<RosterPageItemVO> page, @Param("query") RosterPageQueryDTO query);

    /**
     * 统一人员视图分页（名单 LEFT JOIN 账号 UNION 纯后台账号，一行一人；
     * ID 列 CAST 为字符，避免 JavaScript 精度损失；
     * roles 仅含持久化角色，ASSISTANT 由有效助理授权动态计算不在此列）。
     */
    @Select("""
            <script>
            SELECT t.userId, t.rosterId, t.personNo, t.accountName, t.realName, t.phone,
                   t.departmentId, t.departmentName, t.personType, t.status, t.roles, t.createTime
            FROM (
                SELECT CAST(u.id AS CHAR) AS userId,
                       CAST(r.id AS CHAR) AS rosterId,
                       r.person_no AS personNo,
                       u.account_name AS accountName,
                       COALESCE(u.real_name, r.real_name) AS realName,
                       COALESCE(u.phone, r.phone) AS phone,
                       CAST(COALESCE(u.department_id, r.department_id) AS CHAR) AS departmentId,
                       d.department_name AS departmentName,
                       r.person_type AS personType,
                       CASE
                           WHEN u.id IS NULL AND r.enabled = 1 THEN 'UNFILED'
                           WHEN u.id IS NULL AND r.enabled = 0 THEN 'ROSTER_DISABLED'
                           WHEN u.enabled = 1 THEN 'NORMAL'
                           ELSE 'USER_DISABLED'
                       END AS status,
                       (SELECT GROUP_CONCAT(rl.role_code ORDER BY rl.id SEPARATOR ',')
                          FROM sys_user_role ur
                          JOIN sys_role rl ON rl.id = ur.role_id AND rl.deleted = 0
                         WHERE ur.user_id = u.id AND ur.deleted = 0) AS roles,
                       r.create_time AS createTime
                FROM sys_person_roster r
                LEFT JOIN sys_user u ON u.roster_id = r.id AND u.deleted = 0
                LEFT JOIN sys_department d ON d.id = COALESCE(u.department_id, r.department_id) AND d.deleted = 0
                WHERE r.deleted = 0
                UNION ALL
                SELECT CAST(u.id AS CHAR) AS userId,
                       NULL AS rosterId,
                       u.person_no AS personNo,
                       u.account_name AS accountName,
                       u.real_name AS realName,
                       u.phone AS phone,
                       CAST(u.department_id AS CHAR) AS departmentId,
                       d.department_name AS departmentName,
                       u.user_type AS personType,
                       CASE WHEN u.enabled = 1 THEN 'NORMAL' ELSE 'USER_DISABLED' END AS status,
                       (SELECT GROUP_CONCAT(rl.role_code ORDER BY rl.id SEPARATOR ',')
                          FROM sys_user_role ur
                          JOIN sys_role rl ON rl.id = ur.role_id AND rl.deleted = 0
                         WHERE ur.user_id = u.id AND ur.deleted = 0) AS roles,
                       u.create_time AS createTime
                FROM sys_user u
                LEFT JOIN sys_department d ON d.id = u.department_id AND d.deleted = 0
                WHERE u.deleted = 0 AND u.roster_id IS NULL
            ) t
            <where>
                <if test="query.keyword != null and query.keyword != ''">
                    AND (t.personNo LIKE CONCAT('%', #{query.keyword}, '%')
                         OR t.realName LIKE CONCAT('%', #{query.keyword}, '%')
                         OR t.phone LIKE CONCAT('%', #{query.keyword}, '%')
                         OR t.accountName LIKE CONCAT('%', #{query.keyword}, '%'))
                </if>
                <if test="query.personType != null and query.personType != ''">
                    AND t.personType = #{query.personType}
                </if>
                <if test="query.departmentId != null">
                    AND t.departmentId = #{query.departmentId}
                </if>
                <if test="query.status != null and query.status == 'UNFILED'">
                    AND t.status = 'UNFILED'
                </if>
                <if test="query.status != null and query.status == 'NORMAL'">
                    AND t.status = 'NORMAL'
                </if>
                <if test="query.status != null and query.status == 'DISABLED'">
                    AND t.status IN ('ROSTER_DISABLED', 'USER_DISABLED')
                </if>
            </where>
            ORDER BY t.createTime DESC, t.userId DESC
            </script>
            """)
    Page<PersonPageItemVO> selectPersonPage(Page<PersonPageItemVO> page, @Param("query") PersonPageQueryDTO query);

    /**
     * 名单导出（不分页，筛选条件与分页列表一致，附部门编码）。
     */
    @Select("""
            <script>
            SELECT r.person_no AS personNo,
                   r.person_type AS personType,
                   r.real_name AS realName,
                   r.phone AS phone,
                   d.department_code AS departmentCode,
                   d.department_name AS departmentName,
                   r.enabled AS enabled,
                   r.create_time AS createTime
            FROM sys_person_roster r
            LEFT JOIN sys_department d ON d.id = r.department_id AND d.deleted = 0
            LEFT JOIN sys_user u ON u.roster_id = r.id AND u.deleted = 0
            WHERE r.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (r.person_no LIKE CONCAT('%', #{query.keyword}, '%')
                     OR r.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR r.phone LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.personType != null and query.personType != ''">
                AND r.person_type = #{query.personType}
            </if>
            <if test="query.matched != null and query.matched == 1">
                AND u.id IS NOT NULL
            </if>
            <if test="query.matched != null and query.matched == 0">
                AND u.id IS NULL
            </if>
            ORDER BY r.create_time DESC, r.id DESC
            </script>
            """)
    List<RosterExportRowVO> selectRosterExport(@Param("query") RosterPageQueryDTO query);
}

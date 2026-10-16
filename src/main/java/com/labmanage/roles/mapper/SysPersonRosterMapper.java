package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.vo.RosterPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}

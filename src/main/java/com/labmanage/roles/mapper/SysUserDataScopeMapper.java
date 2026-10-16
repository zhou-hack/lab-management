package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.ScopePageQueryDTO;
import com.labmanage.roles.entity.SysUserDataScope;
import com.labmanage.roles.vo.ScopePageItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据范围 Mapper
 */
@Mapper
public interface SysUserDataScopeMapper extends BaseMapper<SysUserDataScope> {

    /**
     * 范围分页列表（join 管理员姓名/账号与目标部门名；ID 列 CAST 为字符）。
     */
    @Select("""
            <script>
            SELECT CAST(s.id AS CHAR) AS id,
                   CAST(s.user_id AS CHAR) AS userId,
                   u.real_name AS userName,
                   u.account_name AS accountName,
                   s.scope_type AS scopeType,
                   CAST(s.department_id AS CHAR) AS departmentId,
                   d.department_name AS departmentName,
                   s.access_mode AS accessMode,
                   s.create_time AS createTime
            FROM sys_user_data_scope s
            JOIN sys_user u ON u.id = s.user_id AND u.deleted = 0
            LEFT JOIN sys_department d ON d.id = s.department_id AND d.deleted = 0
            WHERE s.deleted = 0 AND s.enabled = 1
            <if test="query.keyword != null and query.keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.account_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.scopeType != null and query.scopeType != ''">
                AND s.scope_type = #{query.scopeType}
            </if>
            <if test="query.accessMode != null and query.accessMode != ''">
                AND s.access_mode = #{query.accessMode}
            </if>
            ORDER BY s.id DESC
            </script>
            """)
    Page<ScopePageItemVO> selectScopePage(Page<ScopePageItemVO> page, @Param("query") ScopePageQueryDTO query);

    /** 物理删除：关系表保持一行唯一，逻辑删除会占住去重键导致无法重新授权（审计由操作日志承担） */
    @Delete("DELETE FROM sys_user_data_scope WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}

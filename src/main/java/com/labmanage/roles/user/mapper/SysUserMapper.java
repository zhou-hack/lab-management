package com.labmanage.roles.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper（只读支撑）
 * <p>
 * 角色/权限为跨表聚合查询，直接以 SQL 表达；
 * ASSISTANT 附加角色由 assistant_grant 动态计算，该模块落地后在此补充。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户的有效角色码（启用且未删除的绑定与角色）
     */
    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1 AND r.deleted = 0
            WHERE ur.user_id = #{userId} AND ur.enabled = 1 AND ur.deleted = 0
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /**
     * 查询用户的有效权限标识（经角色聚合，去重）
     */
    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_user_role ur
            JOIN sys_role_permission rp ON rp.role_id = ur.role_id AND rp.enabled = 1 AND rp.deleted = 0
            JOIN sys_permission p ON p.id = rp.permission_id AND p.enabled = 1 AND p.deleted = 0
            WHERE ur.user_id = #{userId} AND ur.enabled = 1 AND ur.deleted = 0
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);
}

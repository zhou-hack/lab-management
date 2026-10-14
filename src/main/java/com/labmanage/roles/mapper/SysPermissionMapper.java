package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询用户经持久化角色绑定的权限标识（ASSISTANT 动态角色权限由业务另行增量）。
     */
    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_user_role ur
            JOIN sys_role_permission rp ON rp.role_id = ur.role_id AND rp.deleted = 0 AND rp.enabled = 1
            JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.enabled = 1
            WHERE ur.deleted = 0 AND ur.enabled = 1 AND ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}

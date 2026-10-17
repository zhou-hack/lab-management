package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色权限关联 Mapper
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 物理删除绑定：uk_sys_role_permission(role_id, permission_id) 不含 deleted，
     * 逻辑删除后再绑定同一对会撞唯一键，故整集替换时移除项走物理删除。
     */
    @Delete("""
            <script>
            DELETE FROM sys_role_permission
            WHERE role_id = #{roleId} AND permission_id IN
            <foreach collection='permissionIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>
            </script>
            """)
    int physicalDeleteByRoleAndPermissions(@Param("roleId") Long roleId,
                                           @Param("permissionIds") List<Long> permissionIds);
}

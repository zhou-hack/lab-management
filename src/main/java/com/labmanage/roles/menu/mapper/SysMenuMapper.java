package com.labmanage.roles.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.menu.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper（只读支撑：菜单维护属后续基础数据模块）
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可见菜单（经角色绑定聚合，含绑定权限标识，按同级排序升序）
     */
    @Select("""
            SELECT DISTINCT m.id, m.parent_id, m.menu_code, m.menu_name, m.route_path,
                   p.permission_code AS permission_code, m.menu_type, m.sort_order
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.enabled = 1 AND rm.deleted = 0
            JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.enabled = 1 AND ur.deleted = 0
            LEFT JOIN sys_permission p ON p.id = m.permission_id AND p.deleted = 0
            WHERE m.enabled = 1
              AND m.deleted = 0
              AND ur.user_id = #{userId}
            ORDER BY m.sort_order, m.id
            """)
    List<SysMenu> selectUserMenus(@Param("userId") Long userId);
}

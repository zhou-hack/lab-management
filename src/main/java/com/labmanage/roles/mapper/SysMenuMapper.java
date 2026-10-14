package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户经角色绑定的可见菜单（按 sort_order 排序）。
     */
    @Select("""
            SELECT DISTINCT m.*
            FROM sys_user_role ur
            JOIN sys_role_menu rm ON rm.role_id = ur.role_id AND rm.deleted = 0 AND rm.enabled = 1
            JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.enabled = 1
            WHERE ur.deleted = 0 AND ur.enabled = 1 AND ur.user_id = #{userId}
            ORDER BY m.sort_order
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}

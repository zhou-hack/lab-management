package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户角色关联 Mapper
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /** 含逻辑删除行查询绑定记录（uk(user_id, role_id) 下重复绑定须走恢复路径） */
    @Select("SELECT * FROM sys_user_role WHERE user_id = #{userId} AND role_id = #{roleId} LIMIT 1")
    SysUserRole selectAnyByUserAndRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /** 恢复逻辑删除的绑定行（@TableLogic 的 updateById 带 deleted=0 条件无法命中，须显式 SQL） */
    @Update("UPDATE sys_user_role SET deleted = 0, enabled = 1, update_time = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}

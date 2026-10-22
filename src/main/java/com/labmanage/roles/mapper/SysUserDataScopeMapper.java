package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysUserDataScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据范围 Mapper（数据范围模块页面与接口已于 2026-10-22 下线；表与本 Mapper 保留）
 */
@Mapper
public interface SysUserDataScopeMapper extends BaseMapper<SysUserDataScope> {

    /** 物理删除：关系表保持一行唯一，逻辑删除会占住去重键导致无法重新授权（审计由操作日志承担） */
    @Delete("DELETE FROM sys_user_data_scope WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}

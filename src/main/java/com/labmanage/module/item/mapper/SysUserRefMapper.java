package com.labmanage.module.item.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 跨模块只读引用：sys_user 姓名（roles 模块表，物料侧仅做只读解析展示，不修改其数据）
 * <p>
 * 用于申领单列表/详情展示「责任老师 / 代领人」姓名；登录模块接入后由会话写入的用户 ID
 * 在此统一解析，缺失（历史数据或用户已删除）时返回空，由前端回退显示 ID。
 */
@Mapper
public interface SysUserRefMapper {

    /**
     * 按用户 ID 批量解析姓名
     */
    @Select("""
            <script>
            SELECT id AS userId, real_name AS realName FROM sys_user
            WHERE id IN
            <foreach collection='ids' item='i' open='(' separator=',' close=')'>
                #{i}
            </foreach>
            </script>
            """)
    List<UserRef> selectNamesByIds(@Param("ids") List<Long> ids);

    /**
     * 用户名称解析结果
     */
    record UserRef(Long userId, String realName) {
    }
}

package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.LogPageQueryDTO;
import com.labmanage.roles.entity.SysOperationLog;
import com.labmanage.roles.vo.LogPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作日志 Mapper（只追加 + 管理端分页查询）
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    /**
     * 日志分页列表（left join 操作者姓名；追加表 deleted 固定 0）。
     */
    @Select("""
            <script>
            SELECT CAST(l.id AS CHAR) AS id,
                   CAST(l.actor_id AS CHAR) AS actorId,
                   u.real_name AS actorName,
                   l.actor_type AS actorType,
                   l.module_code AS moduleCode,
                   l.action_code AS actionCode,
                   l.target_table AS targetTable,
                   CAST(l.target_id AS CHAR) AS targetId,
                   l.request_id AS requestId,
                   l.client_ip AS clientIp,
                   l.success AS success,
                   l.occurred_at AS occurredAt
            FROM sys_operation_log l
            LEFT JOIN sys_user u ON u.id = l.actor_id AND u.deleted = 0
            WHERE l.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR l.module_code LIKE CONCAT('%', #{query.keyword}, '%')
                     OR l.action_code LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.moduleCode != null and query.moduleCode != ''">
                AND l.module_code = #{query.moduleCode}
            </if>
            <if test="query.success != null">
                AND l.success = #{query.success}
            </if>
            <if test="query.startAt != null">
                AND l.occurred_at &gt;= #{query.startAt}
            </if>
            <if test="query.endAt != null">
                AND l.occurred_at &lt; #{query.endAt}
            </if>
            ORDER BY l.occurred_at DESC, l.id DESC
            </script>
            """)
    Page<LogPageItemVO> selectLogPage(Page<LogPageItemVO> page, @Param("query") LogPageQueryDTO query);
}

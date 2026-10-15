package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.BizSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 场地 Mapper
 */
@Mapper
public interface BizSpaceMapper extends BaseMapper<BizSpace> {

    /**
     * 按名称查询行 ID（含逻辑删除记录）。
     * 逻辑删除后名称不复用，BaseMapper 的查询会自动过滤 deleted=1，
     * 故用原生 SQL 兜底校验（创建查重 + 编辑排除自身），避免唯一索引冲突落入 500。
     */
    @Select("SELECT id FROM lab WHERE lab_name = #{labName} LIMIT 1")
    Long selectIdByNameIncludingDeleted(@Param("labName") String labName);

    /**
     * 统计引用该场地的有效 LAB 级数据范围授权条数（删除前引用检查）。
     * 单表模式下 sys_user_data_scope.lab_id 的目标为 lab.id。
     */
    @Select("SELECT COUNT(*) FROM sys_user_data_scope "
            + "WHERE lab_id = #{spaceId} AND enabled = 1 AND deleted = 0")
    long countLabScopeBySpaceId(@Param("spaceId") Long spaceId);

    /**
     * 按状态分组统计未删除场地块数（列表页统计卡片）。
     * 显式过滤 deleted=0（原生 SQL 不走 @TableLogic 自动过滤）。
     */
    @Select("SELECT status AS status, COUNT(*) AS cnt FROM lab "
            + "WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 按类型分组统计未删除场地块数（统计卡片类型分布）。
     * 别名用全小写下划线（H2 DATABASE_TO_LOWER 会将别名转小写，
     * MySQL 保留原样，两者对 lab_type 均可命中）。
     */
    @Select("SELECT lab_type AS lab_type, COUNT(*) AS cnt FROM lab "
            + "WHERE deleted = 0 GROUP BY lab_type")
    List<Map<String, Object>> countGroupByType();

    /**
     * 按主键行锁读取场地（预约审批串行化依据，数据库设计 8.1）：
     * 审批事务先锁定 lab 行再复核冲突，防同场地并发双写 lab_slot。
     */
    @Select("SELECT * FROM lab WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    BizSpace selectByIdForUpdate(@Param("id") Long id);
}

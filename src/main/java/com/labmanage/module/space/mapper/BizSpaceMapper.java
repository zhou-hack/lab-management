package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.BizSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT id FROM biz_space WHERE space_name = #{spaceName} LIMIT 1")
    Long selectIdByNameIncludingDeleted(@Param("spaceName") String spaceName);

    /**
     * 统计引用该场地的有效 LAB 级数据范围授权条数（删除前引用检查）。
     * 单表模式下 sys_user_data_scope.lab_id 的目标为 biz_space.id。
     */
    @Select("SELECT COUNT(*) FROM sys_user_data_scope "
            + "WHERE lab_id = #{spaceId} AND enabled = 1 AND deleted = 0")
    long countLabScopeBySpaceId(@Param("spaceId") Long spaceId);
}

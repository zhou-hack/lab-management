package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 物料结算 Mapper
 */
@Mapper
public interface MaterialSettlementMapper extends BaseMapper<MaterialSettlement> {

    /**
     * 原出库行累计结算量（归还/核销前校验不超过原出库量）
     */
    @Select("SELECT COALESCE(SUM(qty), 0) FROM material_settlement "
            + "WHERE issue_line_id = #{issueLineId} AND deleted = 0")
    BigDecimal sumSettledQty(@Param("issueLineId") Long issueLineId);

    /**
     * 已有结算记录的原出库行 ID 集合（冲销依赖检查批量加载：已归还/核销的出库行禁止冲销）
     */
    @Select("""
            <script>
            SELECT DISTINCT issue_line_id FROM material_settlement WHERE deleted = 0
              AND issue_line_id IN
            <foreach collection='issueLineIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<Long> selectSettledIssueLineIds(@Param("issueLineIds") Collection<Long> issueLineIds);
}

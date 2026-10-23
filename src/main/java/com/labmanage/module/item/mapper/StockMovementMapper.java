package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockMovement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 库存流水 Mapper（只插入，不更新不改写）
 */
@Mapper
public interface StockMovementMapper extends BaseMapper<StockMovement> {

    /**
     * 指定流水行之后（同物料、更晚发生）的全部增量合计（变更后总数量 = 当前总量 − 此值）
     */
    record AfterSumRow(Long movementId, BigDecimal afterSum) {
    }

    /**
     * 批量统计每行流水之后同物料的流水增量合计（流水页「总数量」列推算用）
     */
    @Select("""
            <script>
            SELECT m1.id AS movement_id, SUM(m2.qty_delta) AS after_sum
              FROM stock_movement m1
              JOIN stock_movement m2
                ON m2.sku_id = m1.sku_id AND m2.deleted = 0
               AND (m2.occurred_at &gt; m1.occurred_at
                    OR (m2.occurred_at = m1.occurred_at AND m2.id &gt; m1.id))
             WHERE m1.deleted = 0 AND m1.id IN
            <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>
             GROUP BY m1.id
            </script>
            """)
    List<AfterSumRow> sumDeltaAfterByIds(@Param("ids") Collection<Long> ids);
}

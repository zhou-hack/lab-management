package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StocktakeLine;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 盘点行 Mapper
 */
@Mapper
public interface StocktakeLineMapper extends BaseMapper<StocktakeLine> {

    /**
     * 按盘点单查询行（ID 升序）
     */
    @Select("SELECT * FROM stocktake_line WHERE stocktake_id = #{stocktakeId} AND deleted = 0 ORDER BY id")
    List<StocktakeLine> selectByStocktakeId(@Param("stocktakeId") Long stocktakeId);

    /**
     * 批量插入盘点行（多行 VALUES 单语句；ID 须由调用方 IdWorker.getId() 预赋，
     * 审计字段 create_time/update_time/deleted 走列默认值）
     */
    @Insert("""
            <script>
            INSERT INTO stocktake_line
                (id, stocktake_id, sku_id, book_qty_snapshot, counted_qty, balance_version_snapshot,
                 difference_reason, counted_by, counted_at)
            VALUES
            <foreach collection='lines' item='l' separator=','>
                (#{l.id}, #{l.stocktakeId}, #{l.skuId}, #{l.bookQtySnapshot}, #{l.countedQty},
                 #{l.balanceVersionSnapshot}, #{l.differenceReason}, #{l.countedBy}, #{l.countedAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<StocktakeLine> lines);

    /**
     * 录入实盘量（重复录入覆盖，未提交前允许修正；差异原因随实盘一并保存）
     */
    @Update("UPDATE stocktake_line SET counted_qty = #{countedQty}, difference_reason = #{differenceReason}, "
            + "counted_at = #{countedAt}, update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND stocktake_id = #{stocktakeId} AND deleted = 0")
    int updateCountedQty(@Param("id") Long id,
                         @Param("stocktakeId") Long stocktakeId,
                         @Param("countedQty") BigDecimal countedQty,
                         @Param("differenceReason") String differenceReason,
                         @Param("countedAt") Instant countedAt);
}

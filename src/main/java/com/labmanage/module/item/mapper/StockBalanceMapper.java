package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 库存余额 Mapper
 */
@Mapper
public interface StockBalanceMapper extends BaseMapper<StockBalance> {

    /**
     * 行锁读取库位+SKU 余额（过账在事务内按 location_id、sku_id 排序加锁）
     */
    @Select("SELECT * FROM stock_balance WHERE location_id = #{locationId} AND sku_id = #{skuId} AND deleted = 0 FOR UPDATE")
    StockBalance lockByLocationAndSku(@Param("locationId") Long locationId, @Param("skuId") Long skuId);

    /**
     * 过账更新余额（行锁保护下更新，version 条件递增；行数为 0 表示版本冲突）
     */
    @Update("UPDATE stock_balance SET stock_qty = #{stockQty}, "
            + "version = version + 1, update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND deleted = 0")
    int updatePostBalance(@Param("id") Long id,
                          @Param("version") Integer version,
                          @Param("stockQty") BigDecimal stockQty);

    /**
     * 更新预警配置（安全库存与补货目标；version 条件更新，0 行表示并发冲突）
     */
    @Update("UPDATE stock_balance SET safe_qty = #{safeQty}, target_qty = #{targetQty}, "
            + "version = version + 1, update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND deleted = 0")
    int updateWarningConfig(@Param("id") Long id,
                            @Param("version") Integer version,
                            @Param("safeQty") BigDecimal safeQty,
                            @Param("targetQty") BigDecimal targetQty);

    /**
     * 按 SKU 批量汇总可用库存（全库位合计，仅正常行；申领可选物料校验与选项展示用）
     */
    @Select("""
            <script>
            SELECT sku_id AS skuId, COALESCE(SUM(stock_qty), 0) AS totalQty
            FROM stock_balance
            WHERE deleted = 0 AND sku_id IN
            <foreach collection='skuIds' item='i' open='(' separator=',' close=')'>
                #{i}
            </foreach>
            GROUP BY sku_id
            </script>
            """)
    List<SkuStockTotal> sumStockBySkuIds(@Param("skuIds") Collection<Long> skuIds);

    /**
     * SKU 可用库存合计
     */
    record SkuStockTotal(Long skuId, BigDecimal totalQty) {
    }
}

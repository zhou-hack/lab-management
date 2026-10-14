package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

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
    @Update("UPDATE stock_balance SET stock_qty = #{stockQty}, moving_average_price = #{avgPrice}, "
            + "version = version + 1, update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND deleted = 0")
    int updatePostBalance(@Param("id") Long id,
                          @Param("version") Integer version,
                          @Param("stockQty") BigDecimal stockQty,
                          @Param("avgPrice") BigDecimal avgPrice);

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
}

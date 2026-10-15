package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialRequestLine;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料申领行 Mapper
 */
@Mapper
public interface MaterialRequestLineMapper extends BaseMapper<MaterialRequestLine> {

    /**
     * 行锁读取申领行（出库过账回写实发量时锁行，防止并发超批准量）
     */
    @Select("SELECT * FROM material_request_line WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MaterialRequestLine lockById(@Param("id") Long id);

    /**
     * 按申领单查询行（ID 升序）
     */
    @Select("SELECT * FROM material_request_line WHERE request_id = #{requestId} AND deleted = 0 ORDER BY id")
    List<MaterialRequestLine> selectByRequestId(@Param("requestId") Long requestId);

    /**
     * 批量插入申领行（多行 VALUES 单语句；ID 须由调用方 IdWorker.getId() 预赋，
     * 审计字段 create_time/update_time/deleted 走列默认值）
     */
    @Insert("""
            <script>
            INSERT INTO material_request_line
                (id, request_id, sku_id, requested_qty, quota_qty_snapshot, requires_extra_approval,
                 requires_dual_sign, approved_qty, fulfilled_qty)
            VALUES
            <foreach collection='lines' item='l' separator=','>
                (#{l.id}, #{l.requestId}, #{l.skuId}, #{l.requestedQty}, #{l.quotaQtySnapshot},
                 #{l.requiresExtraApproval}, #{l.requiresDualSign}, #{l.approvedQty}, #{l.fulfilledQty})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<MaterialRequestLine> lines);

    /**
     * 过账回写实发量（条件更新：累加后不超过批准量，0 行表示超过批准量）
     */
    @Update("UPDATE material_request_line SET fulfilled_qty = fulfilled_qty + #{quantity}, "
            + "update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND deleted = 0 "
            + "AND approved_qty IS NOT NULL AND approved_qty >= fulfilled_qty + #{quantity}")
    int increaseFulfilledQty(@Param("id") Long id, @Param("quantity") BigDecimal quantity);
}

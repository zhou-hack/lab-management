package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.Stocktake;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 盘点单 Mapper（状态迁移均为条件更新 CAS；活跃唯一由 uk_item_stocktake_active 兜底）
 */
@Mapper
public interface StocktakeMapper extends BaseMapper<Stocktake> {

    /**
     * 行锁读取库位当前活跃盘点单（COUNTING/PENDING_REVIEW；无则返回 null）
     */
    @Select("SELECT * FROM stocktake WHERE location_id = #{locationId} "
            + "AND status IN ('COUNTING', 'PENDING_REVIEW') AND deleted = 0 FOR UPDATE")
    Stocktake lockActiveByLocationId(@Param("locationId") Long locationId);

    /**
     * 草稿 → 盘点中（开始盘点即冻结库位）
     */
    @Update("UPDATE stocktake SET status = 'COUNTING', started_at = #{startedAt}, "
            + "update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'DRAFT' AND deleted = 0")
    int casStart(@Param("id") Long id, @Param("startedAt") Instant startedAt);

    /**
     * 盘点中 → 待复核（实盘完成提交）
     */
    @Update("UPDATE stocktake SET status = 'PENDING_REVIEW', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'COUNTING' AND deleted = 0")
    int casSubmitReview(@Param("id") Long id);

    /**
     * 待复核 → 已完成（调账单过账后解冻）
     */
    @Update("UPDATE stocktake SET status = 'COMPLETED', finished_at = #{finishedAt}, "
            + "update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'PENDING_REVIEW' AND deleted = 0")
    int casComplete(@Param("id") Long id, @Param("finishedAt") Instant finishedAt);

    /**
     * 草稿 → 已取消（未开始盘点前可取消）
     */
    @Update("UPDATE stocktake SET status = 'CANCELLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'DRAFT' AND deleted = 0")
    int casCancel(@Param("id") Long id);

    /**
     * 挂接生成的调账单 ID（提交复核时写入）
     */
    @Update("UPDATE stocktake SET adjust_document_id = #{adjustDocumentId}, "
            + "update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND deleted = 0")
    int updateAdjustDocumentId(@Param("id") Long id, @Param("adjustDocumentId") Long adjustDocumentId);
}

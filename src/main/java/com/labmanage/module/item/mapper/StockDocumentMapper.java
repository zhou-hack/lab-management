package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 库存单据 Mapper（状态迁移均为条件更新 CAS，保证只能执行一次）
 */
@Mapper
public interface StockDocumentMapper extends BaseMapper<StockDocument> {

    /**
     * 草稿 → 已批准（条件更新，0 行表示状态不允许）
     */
    @Update("UPDATE stock_document SET status = 'APPROVED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'DRAFT' AND deleted = 0")
    int casApprove(@Param("id") Long id);

    /**
     * 草稿/已批准 → 已取消
     */
    @Update("UPDATE stock_document SET status = 'CANCELLED', update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status IN ('DRAFT', 'APPROVED') AND deleted = 0")
    int casCancel(@Param("id") Long id);

    /**
     * 已批准 → 已过账（与过账流水同一事务，只能成功一次）
     */
    @Update("UPDATE stock_document SET status = 'POSTED', posted_at = #{postedAt}, update_time = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'APPROVED' AND deleted = 0")
    int casPost(@Param("id") Long id, @Param("postedAt") Instant postedAt);
}

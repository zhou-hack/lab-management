package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockMovement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存流水 Mapper（只插入，不更新不改写）
 */
@Mapper
public interface StockMovementMapper extends BaseMapper<StockMovement> {
}

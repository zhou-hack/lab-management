package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.dto.StockMovementQuery;
import com.labmanage.module.item.dto.StockWarningQuery;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StockMovementVO;
import com.labmanage.module.item.vo.StockWarningVO;

/**
 * 库存查询服务（余额、台账、预警）与预警配置
 */
public interface StockQueryService {

    /** 库存余额分页查询（含预警状态与建议补货量） */
    PageResult<StockBalanceVO> pageBalance(StockBalanceQuery query);

    /** 库存台账（流水）分页查询 */
    PageResult<StockMovementVO> pageMovement(StockMovementQuery query);

    /** 库存预警分页查询（stock_qty ≤ safe_qty；0 单独标缺货） */
    PageResult<StockWarningVO> pageWarning(StockWarningQuery query);

    /** 设置预警配置（安全库存与补货目标库存；targetQty ≥ safeQty，version 乐观锁） */
    void updateWarningConfig(String id, StockBalanceWarningUpdateDTO dto);
}

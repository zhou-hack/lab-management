package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StockBalanceAdjustDTO;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StockDocumentQuery;
import com.labmanage.module.item.dto.StockMovementCreateDTO;
import com.labmanage.module.item.dto.StockReversalCreateDTO;
import com.labmanage.module.item.dto.StockReturnCreateDTO;
import com.labmanage.module.item.dto.StockScrapCreateDTO;
import com.labmanage.module.item.dto.StockTransferCreateDTO;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.module.item.vo.StockDocumentVO;

/**
 * 库存单据服务（登记 → 审批 → 过账；过账产生流水与余额变动）
 */
public interface StockDocumentService {

    /** 登记单据（DRAFT），返回单据 ID */
    String create(StockDocumentCreateDTO dto);

    /** 分页查询 */
    PageResult<StockDocumentVO> page(StockDocumentQuery query);

    /** 详情（含明细行） */
    StockDocumentDetailVO get(String id);

    /** 审批（DRAFT → APPROVED） */
    void approve(String id);

    /** 取消（DRAFT/APPROVED → CANCELLED） */
    void cancel(String id);

    /**
     * 过账（APPROVED → POSTED，与流水写入同一事务，只能成功一次）：
     * 排序锁定余额行 → 校验数量与精度 → 更新余额与移动加权均价 → 写不可变流水
     */
    void post(String id);

    /** 登记调拨单（TRANSFER，双边库位；同一行过账产生两条流水），返回单据 ID */
    String createTransfer(StockTransferCreateDTO dto);

    /** 登记归还单（RETURN_IN，行绑定原出库行；过账回增库存并同事务生成 RETURNED 结算），返回单据 ID */
    String createReturn(StockReturnCreateDTO dto);

    /** 登记报废单（SCRAP 出库；核销 LOST/DAMAGED 不再扣库存，走物料核销接口），返回单据 ID */
    String createScrap(StockScrapCreateDTO dto);

    /** 登记冲销单（REVERSAL 整单一次；行由服务端从原单复制，过账写相反流水），返回单据 ID */
    String createReversal(StockReversalCreateDTO dto);

    /**
     * 物料存量调整：目标量与当前量的差值生成 ADJUST 调账单（调增入库行 / 调减出库行），
     * 同一事务内自动审批并过账，余额与流水立即更新（保留完整审计），返回调账单据 ID
     */
    String adjustBalance(String id, StockBalanceAdjustDTO dto);

    /**
     * 新建库存流水：按库位+物料+变更后数量登记（可指定出入库时间），
     * 差值生成 ADJUST 调账单并在同一事务内自动审批过账，余额与流水立即更新，
     * 方向须与差值符号一致（库存台账面板「新建流水」入口），返回调账单据 ID
     */
    String createMovement(StockMovementCreateDTO dto);
}

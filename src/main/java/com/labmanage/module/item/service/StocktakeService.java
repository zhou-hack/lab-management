package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeCreateDTO;
import com.labmanage.module.item.dto.StocktakeQuery;
import com.labmanage.module.item.vo.StocktakeDetailVO;
import com.labmanage.module.item.vo.StocktakeVO;

/**
 * 盘点服务（建单快照 → 开始冻结 → 实盘录入 → 提交复核生成调账 → 审批过账解冻）
 */
public interface StocktakeService {

    /** 建盘点单（DRAFT，快照库位全部账面量与余额版本），返回盘点单 ID */
    String create(StocktakeCreateDTO dto);

    /** 分页查询 */
    PageResult<StocktakeVO> page(StocktakeQuery query);

    /** 详情（含盘点行） */
    StocktakeDetailVO get(String id);

    /** 开始盘点（DRAFT → COUNTING，库位冻结；同一库位仅一张活跃盘点单） */
    void start(String id);

    /** 实盘录入（COUNTING 期间可重复修正；差异原因随实盘保存） */
    void count(String id, StocktakeCountDTO dto);

    /**
     * 提交复核（COUNTING → PENDING_REVIEW）：校验全部行已录入、差异行必填原因、
     * 余额版本与建单快照一致；差异非零时生成 ADJUST 调账单草稿。
     * 返回调账单 ID（无差异返回 null，随后调 complete 确认完成）。
     */
    String submitReview(String id);

    /** 无差异确认完成（PENDING_REVIEW → COMPLETED，解冻库位） */
    void complete(String id);

    /** 差异调账审批过账（一步完成调账单审批与过账；过账后自动完成盘点并解冻） */
    void approveAdjust(String id);

    /** 取消（DRAFT → CANCELLED，未开始盘点前可取消） */
    void cancel(String id);
}

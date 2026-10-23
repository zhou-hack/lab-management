package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialSettlementCreateDTO;
import com.labmanage.module.item.dto.MaterialSettlementQuery;
import com.labmanage.module.item.dto.SettlementIssueOptionQuery;
import com.labmanage.module.item.vo.MaterialSettlementVO;
import com.labmanage.module.item.vo.SettlementIssueOptionVO;

/**
 * 物料结算服务（核销登记；归还 RETURNED 由归还单过账自动生成，不走此接口）
 */
public interface MaterialSettlementService {

    /**
     * 核销登记（CONSUMED/LOST/DAMAGED）：锁原出库行校验累计结算量不超过原出库量；
     * 核销不扣库存（出库时已扣减）。返回结算 ID。
     */
    String settle(MaterialSettlementCreateDTO dto);

    /** 分页查询 */
    PageResult<MaterialSettlementVO> page(MaterialSettlementQuery query);

    /** 可核销出库行选项分页（核销登记下拉：已过账领用出库单的行，含剩余可核销量） */
    PageResult<SettlementIssueOptionVO> issueOptions(SettlementIssueOptionQuery query);
}

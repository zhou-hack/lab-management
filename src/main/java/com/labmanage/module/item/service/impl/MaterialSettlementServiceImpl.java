package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialSettlementCreateDTO;
import com.labmanage.module.item.dto.MaterialSettlementQuery;
import com.labmanage.module.item.dto.SettlementIssueOptionQuery;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.SettlementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.service.MaterialSettlementService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialSettlementVO;
import com.labmanage.module.item.vo.SettlementIssueOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 物料结算服务实现（核销与流水同事务；累计结算量不超过原出库量）
 */
@Service
public class MaterialSettlementServiceImpl implements MaterialSettlementService {

    private final MaterialSettlementMapper settlementMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final StockDocumentMapper documentMapper;

    public MaterialSettlementServiceImpl(MaterialSettlementMapper settlementMapper,
                                         StockDocumentLineMapper documentLineMapper,
                                         StockDocumentMapper documentMapper) {
        this.settlementMapper = settlementMapper;
        this.documentLineMapper = documentLineMapper;
        this.documentMapper = documentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String settle(MaterialSettlementCreateDTO dto) {
        if (dto.settlementType() == SettlementType.RETURNED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "归还结算由归还单过账自动生成，不能手工登记");
        }
        Long issueLineId = IdUtils.requireId(dto.issueLineId(), "原出库行 ID");
        // 行锁原出库行，串行化同一原行的并发核销/归还校验
        StockDocumentLine issueLine = documentLineMapper.lockById(issueLineId);
        if (issueLine == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "原出库行不存在");
        }
        StockDocument issueDoc = documentMapper.selectById(issueLine.getStockDocumentId());
        if (issueDoc == null || issueDoc.getMovementType() != MovementType.ISSUE
                || issueDoc.getStatus() != StockDocumentStatus.POSTED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "原出库行不属于已过账的领用出库单，无法核销");
        }
        BigDecimal settled = settlementMapper.sumSettledQty(issueLineId);
        BigDecimal already = settled == null ? BigDecimal.ZERO : settled;
        if (already.add(dto.quantity()).compareTo(issueLine.getQuantity()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "核销累计结算量超过原出库量：原出库 "
                            + issueLine.getQuantity().stripTrailingZeros().toPlainString()
                            + "，已结算 " + already.stripTrailingZeros().toPlainString()
                            + "，本次 " + dto.quantity().stripTrailingZeros().toPlainString());
        }

        MaterialSettlement settlement = new MaterialSettlement();
        settlement.setIssueLineId(issueLineId);
        settlement.setSettlementType(dto.settlementType());
        settlement.setQuantity(dto.quantity());
        settlement.setSettledAt(Instant.now());
        settlement.setDocumentId(IdWorker.getId());
        settlement.setReason(dto.reason());
        settlementMapper.insert(settlement);
        return String.valueOf(settlement.getId());
    }

    @Override
    public PageResult<MaterialSettlementVO> page(MaterialSettlementQuery query) {
        Long issueLineId = query.getIssueLineId() == null || query.getIssueLineId().isBlank()
                ? null : IdUtils.requireId(query.getIssueLineId(), "原出库行 ID");
        IPage<MaterialSettlementMapper.SettlementRow> page =
                settlementMapper.selectSettlementPage(PageUtils.toPage(query), issueLineId);
        List<MaterialSettlementVO> records = page.getRecords().stream()
                .map(MaterialSettlementVO::from).toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<SettlementIssueOptionVO> issueOptions(SettlementIssueOptionQuery query) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        IPage<MaterialSettlementMapper.IssueOptionRow> page =
                settlementMapper.selectIssueOptionPage(PageUtils.toPage(query), keyword);
        List<SettlementIssueOptionVO> records = page.getRecords().stream()
                .map(SettlementIssueOptionVO::from).toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }
}

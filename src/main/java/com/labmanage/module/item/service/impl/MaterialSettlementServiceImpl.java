package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.SettlementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.MaterialSettlementService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialSettlementVO;
import com.labmanage.module.item.vo.SettlementIssueOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料结算服务实现（核销与流水同事务；累计结算量不超过原出库量）
 */
@Service
public class MaterialSettlementServiceImpl implements MaterialSettlementService {

    private final MaterialSettlementMapper settlementMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final StockDocumentMapper documentMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;

    public MaterialSettlementServiceImpl(MaterialSettlementMapper settlementMapper,
                                         StockDocumentLineMapper documentLineMapper,
                                         StockDocumentMapper documentMapper,
                                         MaterialSkuMapper skuMapper,
                                         StockLocationMapper locationMapper) {
        this.settlementMapper = settlementMapper;
        this.documentLineMapper = documentLineMapper;
        this.documentMapper = documentMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
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
        LambdaQueryWrapper<MaterialSettlement> wrapper = new LambdaQueryWrapper<>();
        if (query.getIssueLineId() != null && !query.getIssueLineId().isBlank()) {
            wrapper.eq(MaterialSettlement::getIssueLineId,
                    IdUtils.requireId(query.getIssueLineId(), "原出库行 ID"));
        }
        wrapper.orderByDesc(MaterialSettlement::getId);
        IPage<MaterialSettlement> page = settlementMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialSettlementVO> records = page.getRecords().stream()
                .map(s -> MaterialSettlementVO.from(s, null, null, null))
                .collect(Collectors.toCollection(ArrayList::new));
        enrichIssueInfo(records, page.getRecords());
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 批量解析结算记录的物料与库位：结算 → 原出库行（skuId）→ 物料名称；
     * 库位口径按类型分流——RETURNED 按归还行（returnLineId → toLocationId 归还入库目标库位）解析，
     * 其余类型按原出库行 fromLocationId（出库来源库位）解析
     */
    private void enrichIssueInfo(List<MaterialSettlementVO> records, List<MaterialSettlement> entities) {
        if (entities.isEmpty()) {
            return;
        }
        // 原出库行与归还行同表，合并一次批量加载
        Set<Long> lineIds = entities.stream()
                .map(MaterialSettlement::getIssueLineId).collect(Collectors.toSet());
        Set<Long> returnLineIds = entities.stream()
                .map(MaterialSettlement::getReturnLineId).filter(Objects::nonNull).collect(Collectors.toSet());
        lineIds.addAll(returnLineIds);
        Map<Long, StockDocumentLine> lineMap = documentLineMapper.selectByIds(lineIds).stream()
                .collect(Collectors.toMap(StockDocumentLine::getId, Function.identity()));
        Set<Long> skuIds = entities.stream()
                .map(MaterialSettlement::getIssueLineId).map(lineMap::get).filter(Objects::nonNull)
                .map(StockDocumentLine::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, Function.identity()));
        Set<Long> locationIds = new HashSet<>();
        for (MaterialSettlement entity : entities) {
            StockDocumentLine issueLine = lineMap.get(entity.getIssueLineId());
            if (issueLine == null) {
                continue;
            }
            Long locationId = resolveLocationId(entity, issueLine, lineMap);
            if (locationId != null) {
                locationIds.add(locationId);
            }
        }
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
        for (int i = 0; i < entities.size(); i++) {
            StockDocumentLine line = lineMap.get(entities.get(i).getIssueLineId());
            if (line == null) {
                continue;
            }
            MaterialSku sku = skuMap.get(line.getSkuId());
            StockLocation location = locationMap.get(resolveLocationId(entities.get(i), line, lineMap));
            records.set(i, MaterialSettlementVO.from(entities.get(i),
                    sku == null ? null : sku.getSkuCode(),
                    sku == null ? null : sku.getMaterialName(),
                    location == null ? null : location.getLocationName()));
        }
    }

    /**
     * 库位解析：RETURNED（有归还行）取归还行 toLocationId（归还入库目标库位），其余取原出库行 fromLocationId
     */
    private Long resolveLocationId(MaterialSettlement entity, StockDocumentLine issueLine,
            Map<Long, StockDocumentLine> lineMap) {
        if (entity.getReturnLineId() != null) {
            StockDocumentLine returnLine = lineMap.get(entity.getReturnLineId());
            return returnLine == null ? null : returnLine.getToLocationId();
        }
        return issueLine.getFromLocationId();
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

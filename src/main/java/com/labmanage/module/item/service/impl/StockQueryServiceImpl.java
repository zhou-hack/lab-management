package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.dto.StockMovementQuery;
import com.labmanage.module.item.dto.StockWarningQuery;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StockMovementVO;
import com.labmanage.module.item.vo.StockWarningVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存查询服务实现（余额、台账、预警；均为只读查询）
 */
@Service
public class StockQueryServiceImpl implements StockQueryService {

    private final StockBalanceMapper balanceMapper;
    private final StockMovementMapper movementMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentLineMapper lineMapper;

    public StockQueryServiceImpl(StockBalanceMapper balanceMapper,
                                   StockMovementMapper movementMapper,
                                   MaterialSkuMapper skuMapper,
                                   StockLocationMapper locationMapper,
                                   StockDocumentLineMapper lineMapper) {
        this.balanceMapper = balanceMapper;
        this.movementMapper = movementMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.lineMapper = lineMapper;
    }

    @Override
    public PageResult<StockBalanceVO> pageBalance(StockBalanceQuery query) {
        LambdaQueryWrapper<StockBalance> wrapper = new LambdaQueryWrapper<>();
        if (query.getLocationId() != null && !query.getLocationId().isBlank()) {
            wrapper.eq(StockBalance::getLocationId, IdUtils.requireId(query.getLocationId(), "库位 ID"));
        }
        if (query.getSkuId() != null && !query.getSkuId().isBlank()) {
            wrapper.eq(StockBalance::getSkuId, IdUtils.requireId(query.getSkuId(), "物料 ID"));
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            List<Long> locationIds = locationIdsOfLab(IdUtils.requireId(query.getLabId(), "实验室 ID"));
            if (locationIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockBalance::getLocationId, locationIds);
        }
        wrapper.orderByDesc(StockBalance::getUpdateTime).orderByDesc(StockBalance::getId);
        IPage<StockBalance> page = balanceMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<StockBalance> balances = page.getRecords();
        Map<Long, MaterialSku> skuMap = skuMapOf(balances.stream().map(StockBalance::getSkuId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locationMap = locationMapOf(
                balances.stream().map(StockBalance::getLocationId).collect(Collectors.toSet()));
        List<StockBalanceVO> records = balances.stream()
                .map(balance -> toBalanceVO(balance, skuMap.get(balance.getSkuId()), locationMap.get(balance.getLocationId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<StockMovementVO> pageMovement(StockMovementQuery query) {
        LambdaQueryWrapper<StockMovement> wrapper = new LambdaQueryWrapper<>();
        if (query.getLocationId() != null && !query.getLocationId().isBlank()) {
            wrapper.eq(StockMovement::getLocationId, IdUtils.requireId(query.getLocationId(), "库位 ID"));
        }
        if (query.getSkuId() != null && !query.getSkuId().isBlank()) {
            wrapper.eq(StockMovement::getSkuId, IdUtils.requireId(query.getSkuId(), "物料 ID"));
        }
        if (query.getDirection() != null) {
            wrapper.eq(StockMovement::getDirection, query.getDirection());
        }
        if (query.getOccurredFrom() != null) {
            wrapper.ge(StockMovement::getOccurredAt, query.getOccurredFrom().toInstant());
        }
        if (query.getOccurredTo() != null) {
            wrapper.le(StockMovement::getOccurredAt, query.getOccurredTo().toInstant());
        }
        if (query.getStockDocumentId() != null && !query.getStockDocumentId().isBlank()) {
            Long documentPk = IdUtils.requireId(query.getStockDocumentId(), "单据 ID");
            List<StockDocumentLine> lines = lineMapper.selectByDocumentId(documentPk);
            if (lines.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockMovement::getDocumentLineId, lines.stream().map(StockDocumentLine::getId).toList());
        }
        wrapper.orderByDesc(StockMovement::getOccurredAt).orderByDesc(StockMovement::getId);
        IPage<StockMovement> page = movementMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<StockMovement> movements = page.getRecords();
        Set<Long> lineIds = movements.stream().map(StockMovement::getDocumentLineId).collect(Collectors.toSet());
        Map<Long, StockDocumentLine> lineMap = lineIds.isEmpty() ? Map.of()
                : lineMapper.selectByIds(lineIds).stream()
                        .collect(Collectors.toMap(StockDocumentLine::getId, line -> line));
        Map<Long, MaterialSku> skuMap = skuMapOf(movements.stream().map(StockMovement::getSkuId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locationMap = locationMapOf(
                movements.stream().map(StockMovement::getLocationId).collect(Collectors.toSet()));
        List<StockMovementVO> records = movements.stream()
                .map(movement -> toMovementVO(movement, lineMap.get(movement.getDocumentLineId()),
                        skuMap.get(movement.getSkuId()), locationMap.get(movement.getLocationId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<StockWarningVO> pageWarning(StockWarningQuery query) {
        LambdaQueryWrapper<StockBalance> wrapper = new LambdaQueryWrapper<>();
        // 预警规则（定稿口径）：stock_qty ≤ safe_qty 触发；0 单独标缺货
        wrapper.apply("stock_qty <= safe_qty");
        if (Boolean.TRUE.equals(query.getShortageOnly())) {
            wrapper.apply("stock_qty = 0");
        }
        if (query.getLocationId() != null && !query.getLocationId().isBlank()) {
            wrapper.eq(StockBalance::getLocationId, IdUtils.requireId(query.getLocationId(), "库位 ID"));
        }
        if (query.getSkuId() != null && !query.getSkuId().isBlank()) {
            wrapper.eq(StockBalance::getSkuId, IdUtils.requireId(query.getSkuId(), "物料 ID"));
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            List<Long> locationIds = locationIdsOfLab(IdUtils.requireId(query.getLabId(), "实验室 ID"));
            if (locationIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockBalance::getLocationId, locationIds);
        }
        wrapper.orderByAsc(StockBalance::getStockQty).orderByAsc(StockBalance::getId);
        IPage<StockBalance> page = balanceMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<StockBalance> balances = page.getRecords();
        Map<Long, MaterialSku> skuMap = skuMapOf(balances.stream().map(StockBalance::getSkuId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locationMap = locationMapOf(
                balances.stream().map(StockBalance::getLocationId).collect(Collectors.toSet()));
        List<StockWarningVO> records = balances.stream()
                .map(balance -> toWarningVO(balance, skuMap.get(balance.getSkuId()), locationMap.get(balance.getLocationId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void updateWarningConfig(String id, StockBalanceWarningUpdateDTO dto) {
        Long balanceId = IdUtils.requireId(id, "余额 ID");
        StockBalance balance = balanceMapper.selectById(balanceId);
        if (balance == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库存余额不存在");
        }
        BigDecimal safeQty = dto.safeQty();
        BigDecimal targetQty = dto.targetQty();
        // 定稿约束：target_qty ≥ safe_qty，DB CHECK 兜底，应用层先校验给出明确错误
        if (targetQty.compareTo(safeQty) < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "补货目标库存不能低于安全库存");
        }
        MaterialSku sku = skuMapper.selectById(balance.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在，无法配置预警");
        }
        validateScale(safeQty, sku);
        validateScale(targetQty, sku);
        // version 乐观锁：0 行表示并发修改（如过账推进了 version），要求刷新重试
        int updated = balanceMapper.updateWarningConfig(balanceId, balance.getVersion(), safeQty, targetQty);
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预警配置已被他人修改，请刷新后重试");
        }
    }

    /**
     * 数量精度校验：小数位不能超过物料 quantity_scale
     */
    private void validateScale(BigDecimal qty, MaterialSku sku) {
        if (qty.stripTrailingZeros().scale() > sku.getQuantityScale()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "数量精度超过物料限制（最多 " + sku.getQuantityScale() + " 位小数）");
        }
    }

    private StockBalanceVO toBalanceVO(StockBalance balance, MaterialSku sku, StockLocation location) {
        BigDecimal stockQty = balance.getStockQty();
        BigDecimal safeQty = balance.getSafeQty();
        BigDecimal targetQty = balance.getTargetQty();
        String level = warningLevel(stockQty, safeQty);
        BigDecimal suggested = suggestedQty(targetQty, stockQty);
        return new StockBalanceVO(
                String.valueOf(balance.getId()),
                String.valueOf(balance.getLocationId()),
                location == null ? null : location.getLocationName(),
                String.valueOf(balance.getSkuId()),
                sku == null ? null : sku.getSkuCode(),
                sku == null ? null : sku.getMaterialName(),
                sku == null ? null : sku.getBaseUnit(),
                stockQty,
                safeQty,
                targetQty,
                balance.getMovingAveragePrice(),
                suggested,
                level,
                balance.getVersion(),
                balance.getUpdateTime()
        );
    }

    private StockWarningVO toWarningVO(StockBalance balance, MaterialSku sku, StockLocation location) {
        BigDecimal stockQty = balance.getStockQty();
        return new StockWarningVO(
                String.valueOf(balance.getId()),
                String.valueOf(balance.getLocationId()),
                location == null ? null : location.getLocationName(),
                String.valueOf(balance.getSkuId()),
                sku == null ? null : sku.getSkuCode(),
                sku == null ? null : sku.getMaterialName(),
                sku == null ? null : sku.getBaseUnit(),
                stockQty,
                balance.getSafeQty(),
                balance.getTargetQty(),
                suggestedQty(balance.getTargetQty(), stockQty),
                stockQty.signum() == 0 ? "OUT_OF_STOCK" : "WARNING",
                balance.getUpdateTime()
        );
    }

    private StockMovementVO toMovementVO(StockMovement movement, StockDocumentLine line,
                                          MaterialSku sku, StockLocation location) {
        return new StockMovementVO(
                String.valueOf(movement.getId()),
                movement.getMovementKey(),
                line == null ? null : String.valueOf(line.getStockDocumentId()),
                String.valueOf(movement.getDocumentLineId()),
                String.valueOf(movement.getLocationId()),
                location == null ? null : location.getLocationName(),
                String.valueOf(movement.getSkuId()),
                sku == null ? null : sku.getSkuCode(),
                sku == null ? null : sku.getMaterialName(),
                movement.getDirection() == null ? null : movement.getDirection().name(),
                movement.getQtyDelta(),
                movement.getBeforeQty(),
                movement.getAfterQty(),
                movement.getValueDelta(),
                movement.getBeforeAvgPrice(),
                movement.getAfterAvgPrice(),
                movement.getOccurredAt()
        );
    }

    private String warningLevel(BigDecimal stockQty, BigDecimal safeQty) {
        if (stockQty.signum() == 0) {
            return "OUT_OF_STOCK";
        }
        return stockQty.compareTo(safeQty) <= 0 ? "WARNING" : "NORMAL";
    }

    /**
     * 建议补货量 = max(0, 目标库存 - 可用库存)（目标量与建议量分开，建议量为派生值）
     */
    private BigDecimal suggestedQty(BigDecimal targetQty, BigDecimal stockQty) {
        BigDecimal suggested = targetQty.subtract(stockQty);
        return suggested.signum() > 0 ? suggested : BigDecimal.ZERO;
    }

    private List<Long> locationIdsOfLab(Long labId) {
        return locationMapper.selectList(new LambdaQueryWrapper<StockLocation>()
                        .select(StockLocation::getId)
                        .eq(StockLocation::getLabId, labId))
                .stream().map(StockLocation::getId).toList();
    }

    private Map<Long, MaterialSku> skuMapOf(Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
    }

    private Map<Long, StockLocation> locationMapOf(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        return locationMapper.selectByIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, location -> location));
    }
}

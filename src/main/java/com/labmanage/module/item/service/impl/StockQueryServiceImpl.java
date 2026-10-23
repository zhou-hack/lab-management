package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.dto.StockMovementQuery;
import com.labmanage.module.item.dto.StockWarningQuery;
import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StockMovementVO;
import com.labmanage.module.item.vo.StockWarningVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存查询服务实现（余额、台账、预警；均为只读查询）
 */
@Service
public class StockQueryServiceImpl implements StockQueryService {

    private static final Logger log = LoggerFactory.getLogger(StockQueryServiceImpl.class);

    private final StockBalanceMapper balanceMapper;
    private final StockMovementMapper movementMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentLineMapper lineMapper;
    private final StockDocumentMapper documentMapper;
    private final MaterialRequestMapper requestMapper;
    private final SysUserRefMapper sysUserRefMapper;

    public StockQueryServiceImpl(StockBalanceMapper balanceMapper,
                                   StockMovementMapper movementMapper,
                                   MaterialSkuMapper skuMapper,
                                   StockLocationMapper locationMapper,
                                   StockDocumentLineMapper lineMapper,
                                   StockDocumentMapper documentMapper,
                                   MaterialRequestMapper requestMapper,
                                   SysUserRefMapper sysUserRefMapper) {
        this.balanceMapper = balanceMapper;
        this.movementMapper = movementMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.lineMapper = lineMapper;
        this.documentMapper = documentMapper;
        this.requestMapper = requestMapper;
        this.sysUserRefMapper = sysUserRefMapper;
    }

    @Override
    public PageResult<StockBalanceVO> pageBalance(StockBalanceQuery query) {
        LambdaQueryWrapper<StockBalance> wrapper = balanceWrapperOf(
                query.getLabId(), query.getLocationId(), query.getSkuId(), query.getCategoryId());
        if (wrapper == null) {
            return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
        }
        IPage<StockBalance> page = balanceMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(balanceVOsOf(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<StockBalanceVO> listBalanceForExport(StockBalanceExportQuery query) {
        LambdaQueryWrapper<StockBalance> wrapper = balanceWrapperOf(
                query.getLabId(), query.getLocationId(), query.getSkuId(), query.getCategoryId());
        if (wrapper == null) {
            return List.of();
        }
        return balanceVOsOf(balanceMapper.selectList(wrapper));
    }

    /**
     * 余额查询条件（分页与导出共用）；实验室名下无库位、分类下无物料时返回 null 表示结果集为空
     */
    private LambdaQueryWrapper<StockBalance> balanceWrapperOf(String labId, String locationId, String skuId,
                                                              String categoryId) {
        LambdaQueryWrapper<StockBalance> wrapper = new LambdaQueryWrapper<>();
        if (locationId != null && !locationId.isBlank()) {
            wrapper.eq(StockBalance::getLocationId, IdUtils.requireId(locationId, "库位 ID"));
        }
        if (skuId != null && !skuId.isBlank()) {
            wrapper.eq(StockBalance::getSkuId, IdUtils.requireId(skuId, "物料 ID"));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            List<Long> skuIds = skuIdsOfCategory(IdUtils.requireId(categoryId, "分类 ID"));
            if (skuIds.isEmpty()) {
                return null;
            }
            wrapper.in(StockBalance::getSkuId, skuIds);
        }
        if (labId != null && !labId.isBlank()) {
            List<Long> locationIds = locationIdsOfLab(IdUtils.requireId(labId, "实验室 ID"));
            if (locationIds.isEmpty()) {
                return null;
            }
            wrapper.in(StockBalance::getLocationId, locationIds);
        }
        wrapper.orderByDesc(StockBalance::getUpdateTime).orderByDesc(StockBalance::getId);
        return wrapper;
    }

    private List<StockBalanceVO> balanceVOsOf(List<StockBalance> balances) {
        Map<Long, MaterialSku> skuMap = skuMapOf(balances.stream().map(StockBalance::getSkuId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locationMap = locationMapOf(
                balances.stream().map(StockBalance::getLocationId).collect(Collectors.toSet()));
        return balances.stream()
                .map(balance -> toBalanceVO(balance, skuMap.get(balance.getSkuId()), locationMap.get(balance.getLocationId())))
                .toList();
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
        if (query.getCategoryId() != null && !query.getCategoryId().isBlank()) {
            List<Long> skuIds = skuIdsOfCategory(IdUtils.requireId(query.getCategoryId(), "分类 ID"));
            if (skuIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockMovement::getSkuId, skuIds);
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
        if (query.getMaterialRequestId() != null && !query.getMaterialRequestId().isBlank()) {
            // 按申领单查其发放产生的全部流水：申领单 → 出库单（material_request_id）→ 单据行 → 流水
            Long requestPk = IdUtils.requireId(query.getMaterialRequestId(), "申领单 ID");
            List<Long> documentIds = documentMapper.selectList(new LambdaQueryWrapper<StockDocument>()
                            .select(StockDocument::getId)
                            .eq(StockDocument::getMaterialRequestId, requestPk))
                    .stream().map(StockDocument::getId).toList();
            if (documentIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            List<Long> lineIds = lineMapper.selectList(new LambdaQueryWrapper<StockDocumentLine>()
                            .select(StockDocumentLine::getId)
                            .in(StockDocumentLine::getStockDocumentId, documentIds))
                    .stream().map(StockDocumentLine::getId).toList();
            if (lineIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockMovement::getDocumentLineId, lineIds);
        }
        wrapper.orderByDesc(StockMovement::getOccurredAt).orderByDesc(StockMovement::getId);
        IPage<StockMovement> page = movementMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<StockMovement> movements = page.getRecords();
        Set<Long> lineIds = movements.stream().map(StockMovement::getDocumentLineId).collect(Collectors.toSet());
        Map<Long, StockDocumentLine> lineMap = lineIds.isEmpty() ? Map.of()
                : lineMapper.selectByIds(lineIds).stream()
                        .collect(Collectors.toMap(StockDocumentLine::getId, line -> line));
        // 单据映射：取 operator_id（入库人/出库人，实际经办人）
        Set<Long> documentIds = lineMap.values().stream()
                .map(StockDocumentLine::getStockDocumentId).collect(Collectors.toSet());
        Map<Long, StockDocument> documentMap = documentIds.isEmpty() ? Map.of()
                : documentMapper.selectByIds(documentIds).stream()
                        .collect(Collectors.toMap(StockDocument::getId, doc -> doc));
        // 申领单映射：申领发放产生的出库单经 material_request_id 关联（用途 purpose 在申领单上）
        Set<Long> requestIds = documentMap.values().stream()
                .map(StockDocument::getMaterialRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MaterialRequest> requestMap = requestIds.isEmpty() ? Map.of()
                : requestMapper.selectByIds(requestIds).stream()
                        .collect(Collectors.toMap(MaterialRequest::getId, req -> req));
        Map<Long, MaterialSku> skuMap = skuMapOf(movements.stream().map(StockMovement::getSkuId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locationMap = locationMapOf(
                movements.stream().map(StockMovement::getLocationId).collect(Collectors.toSet()));
        Map<Long, String> userNames = movementUserNamesOf(movements, lineMap, documentMap, requestMap, locationMap);
        // 总数量（物料全部库位合计）：变更后总数量 = 当前总量 − 该行之后同物料全部流水增量
        Map<Long, BigDecimal> totalNowMap = totalQtyBySkuOf(movements);
        Map<Long, BigDecimal> afterSumMap = deltaAfterOf(movements);
        List<StockMovementVO> records = new java.util.ArrayList<>();
        for (StockMovement movement : movements) {
            StockDocumentLine line = lineMap.get(movement.getDocumentLineId());
            StockDocument doc = line == null ? null : documentMap.get(line.getStockDocumentId());
            MaterialRequest request = doc == null || doc.getMaterialRequestId() == null
                    ? null : requestMap.get(doc.getMaterialRequestId());
            BigDecimal totalQty = totalNowMap.getOrDefault(movement.getSkuId(), BigDecimal.ZERO)
                    .subtract(afterSumMap.getOrDefault(movement.getId(), BigDecimal.ZERO));
            StockMovementVO vo = toMovementVO(movement, line, doc, request, skuMap.get(movement.getSkuId()),
                    locationMap.get(movement.getLocationId()), userNames, totalQty);
            // 入库人/出库人/保管人 字段排查日志：定位姓名缺失或 ID 不匹配问题
            if (log.isDebugEnabled()) {
                log.debug(
                        "MovementVO fields: movementId={}, direction={}, operatorId={}, operatorName={}, "
                                + "custodianId={}, custodianName={}, docNull={}, lineNull={}, locationNull={}",
                        vo.id(), vo.direction(), vo.operatorId(), vo.operatorName(),
                        vo.custodianId(), vo.custodianName(),
                        doc == null, line == null, locationMap.get(movement.getLocationId()) == null);
            }
            records.add(vo);
        }
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 流水行涉及的用户姓名：单据经办人（入库人/出库人）+ 申领老师 + 库位保管人，批量只读解析
     */
    private Map<Long, String> movementUserNamesOf(List<StockMovement> movements,
                                                  Map<Long, StockDocumentLine> lineMap,
                                                  Map<Long, StockDocument> documentMap,
                                                  Map<Long, MaterialRequest> requestMap,
                                                  Map<Long, StockLocation> locationMap) {
        Set<Long> userIds = new HashSet<>();
        for (StockMovement movement : movements) {
            StockDocumentLine line = lineMap.get(movement.getDocumentLineId());
            StockDocument doc = line == null ? null : documentMap.get(line.getStockDocumentId());
            if (doc != null && doc.getOperatorId() != null) {
                userIds.add(doc.getOperatorId());
            }
            Long teacherId = requestTeacherIdOf(doc,
                    doc == null || doc.getMaterialRequestId() == null
                            ? null : requestMap.get(doc.getMaterialRequestId()));
            if (teacherId != null) {
                userIds.add(teacherId);
            }
            StockLocation location = locationMap.get(movement.getLocationId());
            if (location != null && location.getCustodianId() != null) {
                userIds.add(location.getCustodianId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUserRefMapper.UserRef ref : sysUserRefMapper.selectNamesByIds(List.copyOf(userIds))) {
            if (ref.realName() != null) {
                names.put(ref.userId(), ref.realName());
            }
        }
        // 排查日志：打印待解析用户 ID 与已解析姓名映射，便于定位姓名缺失（如用户被删除/realName 为空）
        if (log.isDebugEnabled()) {
            log.debug("movementUserNamesOf: requestedUserIds={}, resolvedNames={}", userIds, names);
        }
        return names;
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
        if (query.getCategoryId() != null && !query.getCategoryId().isBlank()) {
            List<Long> skuIds = skuIdsOfCategory(IdUtils.requireId(query.getCategoryId(), "分类 ID"));
            if (skuIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(StockBalance::getSkuId, skuIds);
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

    private StockMovementVO toMovementVO(StockMovement movement, StockDocumentLine line, StockDocument doc,
                                          MaterialRequest request, MaterialSku sku, StockLocation location,
                                          Map<Long, String> userNames, BigDecimal totalQty) {
        Long operatorId = doc == null ? null : doc.getOperatorId();
        Long custodianId = location == null ? null : location.getCustodianId();
        Long teacherId = requestTeacherIdOf(doc, request);
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
                totalQty,
                movement.getValueDelta(),
                movement.getBeforeAvgPrice(),
                movement.getAfterAvgPrice(),
                movement.getOccurredAt(),
                operatorId == null ? null : String.valueOf(operatorId),
                // 经办人姓名：台账直接登记的流水优先展示登记姓名，其余按单据 operator_id 解析
                movement.getOperatorName() != null ? movement.getOperatorName()
                        : operatorId == null ? null : userNames.get(operatorId),
                custodianId == null ? null : String.valueOf(custodianId),
                custodianId == null ? null : userNames.get(custodianId),
                doc == null || doc.getMaterialRequestId() == null ? null : String.valueOf(doc.getMaterialRequestId()),
                teacherId == null ? null : String.valueOf(teacherId),
                teacherId == null ? null : userNames.get(teacherId),
                request == null ? null : request.getPurpose()
        );
    }

    /**
     * 物料当前总量（全部库位 stock_qty 合计；流水页「总数量」列推算基准）
     */
    private Map<Long, BigDecimal> totalQtyBySkuOf(List<StockMovement> movements) {
        Set<Long> skuIds = movements.stream().map(StockMovement::getSkuId).collect(Collectors.toSet());
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return balanceMapper.sumQtyBySkuIds(skuIds).stream()
                .collect(Collectors.toMap(StockBalanceMapper.SkuQtyRow::skuId,
                        row -> row.totalQty() == null ? BigDecimal.ZERO : row.totalQty()));
    }

    /**
     * 每行流水之后（同物料、更晚发生）的全部增量合计（变更后总数量 = 当前总量 − 此值）
     */
    private Map<Long, BigDecimal> deltaAfterOf(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return Map.of();
        }
        return movementMapper.sumDeltaAfterByIds(
                        movements.stream().map(StockMovement::getId).toList()).stream()
                .collect(Collectors.toMap(StockMovementMapper.AfterSumRow::movementId,
                        row -> row.afterSum() == null ? BigDecimal.ZERO : row.afterSum()));
    }

    /**
     * 申领老师：优先取单据归属老师（issue 时写入），为空兜底申领单归属老师
     */
    private Long requestTeacherIdOf(StockDocument doc, MaterialRequest request) {
        if (doc != null && doc.getOwnerTeacherId() != null) {
            return doc.getOwnerTeacherId();
        }
        return request == null ? null : request.getOwnerTeacherId();
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

    /**
     * 分类下全部物料 ID（精确匹配 category_id，与物料档案列表筛选口径一致；分类下无物料返回空集合）
     */
    private List<Long> skuIdsOfCategory(Long categoryId) {
        return skuMapper.selectList(new LambdaQueryWrapper<MaterialSku>()
                        .select(MaterialSku::getId)
                        .eq(MaterialSku::getCategoryId, categoryId))
                .stream().map(MaterialSku::getId).toList();
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

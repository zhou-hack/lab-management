package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StockDocumentLineCreateDTO;
import com.labmanage.module.item.dto.StockDocumentQuery;
import com.labmanage.module.item.dto.StockReversalCreateDTO;
import com.labmanage.module.item.dto.StockReturnCreateDTO;
import com.labmanage.module.item.dto.StockReturnLineDTO;
import com.labmanage.module.item.dto.StockScrapCreateDTO;
import com.labmanage.module.item.dto.StockScrapLineDTO;
import com.labmanage.module.item.dto.StockTransferCreateDTO;
import com.labmanage.module.item.dto.StockTransferLineDTO;
import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.enums.MoveDirection;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.SettlementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.module.item.vo.StockDocumentLineVO;
import com.labmanage.module.item.vo.StockDocumentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 库存单据服务实现（登记 → 审批 → 过账）
 * <p>
 * 过账与流水写入同一事务：状态条件更新（CAS）→ 按 location_id、sku_id 排序锁定余额行
 * → 校验数量与精度 → 更新余额与移动加权均价 → 写不可变流水；任一失败整笔回滚。
 */
@Service
public class StockDocumentServiceImpl implements StockDocumentService {

    private static final Logger log = LoggerFactory.getLogger(StockDocumentServiceImpl.class);

    private static final Set<MovementType> SUPPORTED_TYPES = Set.of(
            MovementType.OPENING, MovementType.PURCHASE_IN, MovementType.ISSUE);

    private final StockDocumentMapper documentMapper;
    private final StockDocumentLineMapper lineMapper;
    private final StockBalanceMapper balanceMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockMovementMapper movementMapper;
    // ---- 第二阶段追加依赖：申领实发回写、归还结算 ----
    private final MaterialRequestMapper requestMapper;
    private final MaterialRequestLineMapper requestLineMapper;
    private final MaterialSettlementMapper settlementMapper;
    // ---- 经办人姓名解析（跨模块只读 sys_user）----
    private final SysUserRefMapper sysUserRefMapper;

    public StockDocumentServiceImpl(StockDocumentMapper documentMapper,
                                     StockDocumentLineMapper lineMapper,
                                     StockBalanceMapper balanceMapper,
                                     MaterialSkuMapper skuMapper,
                                     StockLocationMapper locationMapper,
                                     StockMovementMapper movementMapper,
                                     MaterialRequestMapper requestMapper,
                                     MaterialRequestLineMapper requestLineMapper,
                                     MaterialSettlementMapper settlementMapper,
                                     SysUserRefMapper sysUserRefMapper) {
        this.documentMapper = documentMapper;
        this.lineMapper = lineMapper;
        this.balanceMapper = balanceMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.movementMapper = movementMapper;
        this.requestMapper = requestMapper;
        this.requestLineMapper = requestLineMapper;
        this.settlementMapper = settlementMapper;
        this.sysUserRefMapper = sysUserRefMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(StockDocumentCreateDTO dto) {
        MovementType type = dto.movementType();
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "本阶段仅支持期初入库、采购入库与领用出库");
        }
        MoveDirection direction = directionOf(type);
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);

        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(type);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOwnerTeacherId(IdUtils.optionalId(dto.ownerTeacherId(), "归属老师 ID"));
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        document.setVendorName(dto.vendorName());
        documentMapper.insert(document);

        List<StockDocumentLine> lines = buildLines(document.getId(), direction, dto.lines());
        if (!lines.isEmpty()) {
            lineMapper.insertBatch(lines);
        }
        return String.valueOf(document.getId());
    }

    @Override
    public PageResult<StockDocumentVO> page(StockDocumentQuery query) {
        LambdaQueryWrapper<StockDocument> wrapper = new LambdaQueryWrapper<>();
        if (query.getMovementType() != null) {
            wrapper.eq(StockDocument::getMovementType, query.getMovementType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(StockDocument::getStatus, query.getStatus());
        }
        if (query.getOccurredFrom() != null) {
            wrapper.ge(StockDocument::getOccurredAt, query.getOccurredFrom().toInstant());
        }
        if (query.getOccurredTo() != null) {
            wrapper.le(StockDocument::getOccurredAt, query.getOccurredTo().toInstant());
        }
        wrapper.orderByDesc(StockDocument::getOccurredAt).orderByDesc(StockDocument::getId);
        IPage<StockDocument> page = documentMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<StockDocument> documents = page.getRecords();
        Map<Long, String> operatorNames = userNamesOf(documents);
        List<StockDocumentVO> records = documents.stream()
                .map(doc -> toDocumentVO(doc, operatorNames))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public StockDocumentDetailVO get(String id) {
        Long documentPk = IdUtils.requireId(id, "单据 ID");
        StockDocument document = documentMapper.selectById(documentPk);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库存单据不存在");
        }
        List<StockDocumentLine> lines = lineMapper.selectByDocumentId(documentPk);

        Set<Long> skuIds = lines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locationIds = new HashSet<>();
        lines.forEach(line -> {
            if (line.getFromLocationId() != null) {
                locationIds.add(line.getFromLocationId());
            }
            if (line.getToLocationId() != null) {
                locationIds.add(line.getToLocationId());
            }
        });
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<StockDocumentLineVO> lineVos = new ArrayList<>(lines.size());
        for (StockDocumentLine line : lines) {
            MaterialSku sku = skuMap.get(line.getSkuId());
            if (line.getAmountSnapshot() != null) {
                totalAmount = totalAmount.add(line.getAmountSnapshot());
            }
            lineVos.add(StockDocumentLineVO.from(line,
                    sku == null ? null : sku.getSkuCode(),
                    sku == null ? null : sku.getMaterialName(),
                    sku == null ? null : sku.getBaseUnit(),
                    nameOf(locationMap, line.getFromLocationId()),
                    nameOf(locationMap, line.getToLocationId())));
        }
        Map<Long, String> operatorNames = document.getOperatorId() == null
                ? Map.of() : userNamesOf(List.of(document));
        StockDocumentDetailVO vo = StockDocumentDetailVO.from(document, lineVos,
                totalAmount.setScale(2, RoundingMode.HALF_UP), operatorNames);
        // 经办人字段排查日志：定位姓名缺失或 ID 不匹配问题
        if (log.isDebugEnabled()) {
            log.debug(
                    "DocumentDetailVO fields: documentId={}, movementType={}, status={}, operatorId={}, "
                            + "operatorName={}, operatorNamesMap={}",
                    vo.id(), vo.movementType(), vo.status(), vo.operatorId(), vo.operatorName(), operatorNames);
        }
        return vo;
    }

    /**
     * 批量解析单据经办人姓名：收集 operator_id 一次性查询，避免 N+1
     */
    private Map<Long, String> userNamesOf(List<StockDocument> documents) {
        Set<Long> userIds = new HashSet<>();
        for (StockDocument doc : documents) {
            if (doc.getOperatorId() != null) {
                userIds.add(doc.getOperatorId());
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
        // 排查日志：打印待解析用户 ID 与已解析姓名映射，便于定位姓名缺失
        if (log.isDebugEnabled()) {
            log.debug("documentUserNamesOf: requestedUserIds={}, resolvedNames={}", userIds, names);
        }
        return names;
    }

    private StockDocumentVO toDocumentVO(StockDocument doc, Map<Long, String> operatorNames) {
        StockDocumentVO vo = StockDocumentVO.from(doc, operatorNames);
        // 经办人字段排查日志：定位姓名缺失或 ID 不匹配问题
        if (log.isDebugEnabled()) {
            log.debug(
                    "DocumentVO fields: documentId={}, movementType={}, status={}, operatorId={}, "
                            + "operatorName={}",
                    vo.id(), vo.movementType(), vo.status(), vo.operatorId(), vo.operatorName());
        }
        return vo;
    }

    @Override
    public void approve(String id) {
        Long documentPk = IdUtils.requireId(id, "单据 ID");
        if (documentMapper.casApprove(documentPk) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "单据不存在或不处于草稿状态，无法审批");
        }
    }

    @Override
    public void cancel(String id) {
        Long documentPk = IdUtils.requireId(id, "单据 ID");
        if (documentMapper.casCancel(documentPk) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "单据不存在或已过账，无法取消");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void post(String id) {
        Long documentPk = IdUtils.requireId(id, "单据 ID");
        // 状态 CAS 与流水同事务：失败抛出后整体回滚，POSTED 只能成功一次
        if (documentMapper.casPost(documentPk, Instant.now()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "单据不存在或不是已批准状态");
        }
        StockDocument document = documentMapper.selectById(documentPk);
        if (document == null) {
            // CAS 成功即说明行存在，读不到属于数据异常；防御性回滚避免 NPE
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "单据数据异常，无法过账");
        }
        List<StockDocumentLine> lines = lineMapper.selectByDocumentId(documentPk);
        if (lines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "单据没有明细行，无法过账");
        }
        // ---- 第二阶段追加：调拨/冲销走专用过账路径（双边锁/相反流水）----
        switch (document.getMovementType()) {
            case TRANSFER -> { postTransfer(document, lines); return; }
            case REVERSAL -> { postReversal(document, lines); return; }
            default -> { }
        }
        MoveDirection direction = directionOf(document.getMovementType());
        // ---- 第二阶段追加：归还行锁原出库行校验累计结算量；申领实发行锁申领行校验批准量 ----
        Map<Long, StockDocumentLine> issueLineMap = checkReturnQuota(lines);
        Map<Long, MaterialRequestLine> requestLineMap = checkIssueQuota(lines);
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(
                        lines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MaterialSku::getId, sku -> sku));

        // 多行按 库位 ID、SKU ID 排序加锁，避免并发交叉死锁；调拨双边锁由调拨阶段引入
        List<StockDocumentLine> sorted = lines.stream()
                .sorted(Comparator.comparing((StockDocumentLine line) -> targetLocationId(line, direction))
                        .thenComparing(StockDocumentLine::getSkuId))
                .toList();

        for (StockDocumentLine line : sorted) {
            MaterialSku sku = skuMap.get(line.getSkuId());
            if (sku == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在，无法过账");
            }
            validateQuantityScale(line.getQuantity(), sku);

            Long locationId = targetLocationId(line, direction);
            StockBalance balance = lockBalance(locationId, line.getSkuId());
            BigDecimal beforeQty = balance.getStockQty();
            BigDecimal beforeAvg = balance.getMovingAveragePrice();
            BigDecimal quantity = line.getQuantity();

            BigDecimal afterQty;
            BigDecimal afterAvg;
            BigDecimal valueDelta;
            if (direction == MoveDirection.IN) {
                BigDecimal price = line.getUnitPriceSnapshot() == null ? BigDecimal.ZERO : line.getUnitPriceSnapshot();
                afterQty = beforeQty.add(quantity);
                // 移动加权平均：新均价 = (原量×原均价 + 入量×入价) / 新量（4 位小数）
                afterAvg = afterQty.signum() > 0
                        ? beforeQty.multiply(beforeAvg).add(quantity.multiply(price))
                                .divide(afterQty, 4, RoundingMode.HALF_UP)
                        : beforeAvg;
                valueDelta = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            } else {
                if (beforeQty.compareTo(quantity) < 0) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "库存不足：" + sku.getMaterialName() + "，可用 " + beforeQty.stripTrailingZeros().toPlainString()
                                    + " " + sku.getBaseUnit() + "，需要 "
                                    + quantity.stripTrailingZeros().toPlainString());
                }
                afterQty = beforeQty.subtract(quantity);
                afterAvg = beforeAvg;
                valueDelta = quantity.multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP).negate();
                // 出库行回填过账时成本快照
                line.setUnitPriceSnapshot(beforeAvg);
                line.setAmountSnapshot(valueDelta.negate());
                lineMapper.updateById(line);
            }

            // 行锁保护下 version 条件更新必然成功，0 行表示数据异常，整笔回滚
            int updated = balanceMapper.updatePostBalance(balance.getId(), balance.getVersion(), afterQty, afterAvg);
            if (updated == 0) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "库存余额更新异常，请重试");
            }

            StockMovement movement = new StockMovement();
            movement.setDocumentLineId(line.getId());
            movement.setLocationId(locationId);
            movement.setSkuId(line.getSkuId());
            movement.setDirection(direction);
            movement.setQtyDelta(direction == MoveDirection.IN ? quantity : quantity.negate());
            movement.setBeforeQty(beforeQty);
            movement.setAfterQty(afterQty);
            movement.setValueDelta(valueDelta);
            movement.setBeforeAvgPrice(beforeAvg);
            movement.setAfterAvgPrice(afterAvg);
            movement.setOccurredAt(document.getOccurredAt());
            movement.setMovementKey("STK:" + document.getDocumentId() + ":" + line.getLineNo() + ":" + direction);
            // 幂等键唯一冲突表示重复过账，整笔回滚
            try {
                movementMapper.insert(movement);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "流水已存在，禁止重复过账");
            }
        }
        // ---- 第二阶段追加：过账落账后处理（RETURN_IN 同事务生成结算；ISSUE 回写实发量并推进申领状态）----
        afterPosted(document, lines, issueLineMap, requestLineMap);
    }

    /**
     * 行锁读取余额行；不存在则插入（唯一键兜底并发首次创建，冲突后改为行锁读取）
     */
    private StockBalance lockBalance(Long locationId, Long skuId) {
        StockBalance balance = balanceMapper.lockByLocationAndSku(locationId, skuId);
        if (balance != null) {
            return balance;
        }
        StockBalance created = new StockBalance();
        created.setLocationId(locationId);
        created.setSkuId(skuId);
        created.setStockQty(BigDecimal.ZERO);
        created.setSafeQty(BigDecimal.ZERO);
        created.setTargetQty(BigDecimal.ZERO);
        created.setMovingAveragePrice(BigDecimal.ZERO);
        created.setVersion(0);
        try {
            balanceMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            StockBalance existing = balanceMapper.lockByLocationAndSku(locationId, skuId);
            if (existing == null) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存余额行初始化冲突");
            }
            return existing;
        }
    }

    private List<StockDocumentLine> buildLines(Long documentId, MoveDirection direction,
                                               List<StockDocumentLineCreateDTO> dtos) {
        Set<Long> skuIds = dtos.stream()
                .map(dto -> IdUtils.requireId(dto.skuId(), "物料 ID"))
                .collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locationIds = new HashSet<>();
        for (StockDocumentLineCreateDTO dto : dtos) {
            Long from = IdUtils.optionalId(dto.fromLocationId(), "出库位 ID");
            Long to = IdUtils.optionalId(dto.toLocationId(), "入库位 ID");
            if (from != null) {
                locationIds.add(from);
            }
            if (to != null) {
                locationIds.add(to);
            }
        }
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));

        List<StockDocumentLine> lines = new ArrayList<>(dtos.size());
        int lineNo = 1;
        for (StockDocumentLineCreateDTO dto : dtos) {
            Long skuId = IdUtils.requireId(dto.skuId(), "物料 ID");
            MaterialSku sku = skuMap.get(skuId);
            if (sku == null || sku.getEnabled() == null || sku.getEnabled() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料不存在或已停用：" + dto.skuId());
            }
            validateQuantityScale(dto.quantity(), sku);

            Long fromLocationId = IdUtils.optionalId(dto.fromLocationId(), "出库位 ID");
            Long toLocationId = IdUtils.optionalId(dto.toLocationId(), "入库位 ID");
            if (direction == MoveDirection.IN) {
                if (toLocationId == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "入库单据必须填写入库位");
                }
                if (fromLocationId != null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "入库单据不能填写出库位");
                }
            } else {
                if (fromLocationId == null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "领用出库单据必须填写出库位");
                }
                if (toLocationId != null) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "领用出库单据不能填写入库位");
                }
            }
            Long locationId = direction == MoveDirection.IN ? toLocationId : fromLocationId;
            StockLocation location = locationMap.get(locationId);
            if (location == null || location.getEnabled() == null || location.getEnabled() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位不存在或已停用：" + locationId);
            }
            if (direction == MoveDirection.OUT && dto.unitPrice() != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "出库单据不接受单价，出库成本由过账时移动加权均价快照生成");
            }

            StockDocumentLine line = new StockDocumentLine();
            line.setId(IdWorker.getId());
            line.setStockDocumentId(documentId);
            line.setLineNo(lineNo++);
            line.setSkuId(skuId);
            line.setFromLocationId(fromLocationId);
            line.setToLocationId(toLocationId);
            line.setQuantity(dto.quantity());
            line.setUnitPriceSnapshot(dto.unitPrice());
            line.setAmountSnapshot(dto.unitPrice() == null ? null
                    : dto.quantity().multiply(dto.unitPrice()).setScale(2, RoundingMode.HALF_UP));
            lines.add(line);
        }
        return lines;
    }

    private void validateQuantityScale(BigDecimal quantity, MaterialSku sku) {
        int allowed = sku.getQuantityScale() == null ? 3 : sku.getQuantityScale();
        if (quantity.stripTrailingZeros().scale() > allowed) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "数量精度超过物料限制：" + sku.getMaterialName() + " 最多 " + allowed + " 位小数");
        }
    }

    private MoveDirection directionOf(MovementType type) {
        return switch (type) {
            case OPENING, PURCHASE_IN, RETURN_IN -> MoveDirection.IN;
            case ISSUE, SCRAP, REVERSAL -> MoveDirection.OUT;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "业务类型需要双边库位，本阶段未开放：" + type);
        };
    }

    private Long targetLocationId(StockDocumentLine line, MoveDirection direction) {
        return direction == MoveDirection.IN ? line.getToLocationId() : line.getFromLocationId();
    }

    private String nameOf(Map<Long, StockLocation> locationMap, Long locationId) {
        if (locationId == null) {
            return null;
        }
        StockLocation location = locationMap.get(locationId);
        return location == null ? null : location.getLocationName();
    }

    // ====================================================================
    // 以下为第二阶段追加：调拨 / 归还 / 报废 / 冲销登记与过账扩展。
    // 既有 create/page/get/approve/cancel/post 及单边过账循环保持原实现不动。
    // ====================================================================

    /** 可冲销的原单业务类型（TRANSFER/REVERSAL/RETURN_IN 不开放冲销，见设计文档 7.1 差异） */
    private static final Set<MovementType> REVERSABLE_TYPES = Set.of(
            MovementType.OPENING, MovementType.PURCHASE_IN, MovementType.ISSUE, MovementType.SCRAP);

    /** 库位 + SKU 锁序键（调拨/冲销按库位、SKU 排序锁定的最小单位） */
    private record LocSku(Long locationId, Long skuId) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTransfer(StockTransferCreateDTO dto) {
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);
        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.TRANSFER);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOwnerTeacherId(IdUtils.optionalId(dto.ownerTeacherId(), "归属老师 ID"));
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        documentMapper.insert(document);

        Set<Long> skuIds = dto.lines().stream()
                .map(line -> IdUtils.requireId(line.skuId(), "物料 ID"))
                .collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locationIds = new HashSet<>();
        for (StockTransferLineDTO line : dto.lines()) {
            locationIds.add(IdUtils.requireId(line.fromLocationId(), "调出库位 ID"));
            locationIds.add(IdUtils.requireId(line.toLocationId(), "调入库位 ID"));
        }
        Map<Long, StockLocation> locationMap = locationMapper.selectByIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, location -> location));

        List<StockDocumentLine> lines = new ArrayList<>(dto.lines().size());
        int lineNo = 1;
        for (StockTransferLineDTO lineDto : dto.lines()) {
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.quantity(), sku);
            Long from = IdUtils.requireId(lineDto.fromLocationId(), "调出库位 ID");
            Long to = IdUtils.requireId(lineDto.toLocationId(), "调入库位 ID");
            if (from.equals(to)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "调拨双边库位不能相同");
            }
            requireEnabledLocation(locationMap, from, "调出库位");
            requireEnabledLocation(locationMap, to, "调入库位");

            StockDocumentLine line = new StockDocumentLine();
            line.setId(IdWorker.getId());
            line.setStockDocumentId(document.getId());
            line.setLineNo(lineNo++);
            line.setSkuId(skuId);
            line.setFromLocationId(from);
            line.setToLocationId(to);
            line.setQuantity(lineDto.quantity());
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            lineMapper.insertBatch(lines);
        }
        return String.valueOf(document.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createReturn(StockReturnCreateDTO dto) {
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);
        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.RETURN_IN);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOwnerTeacherId(IdUtils.optionalId(dto.ownerTeacherId(), "归属老师 ID"));
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        documentMapper.insert(document);

        // 原出库行必须属于已过账的领用出库单
        Set<Long> issueLineIds = dto.lines().stream()
                .map(line -> IdUtils.requireId(line.originalIssueLineId(), "原出库行 ID"))
                .collect(Collectors.toSet());
        // 批量加载原出库行（替代逐行 selectById）
        Map<Long, StockDocumentLine> issueLineMap = lineMapper.selectByIds(issueLineIds).stream()
                .collect(Collectors.toMap(StockDocumentLine::getId, line -> line));
        for (Long issueLineId : issueLineIds) {
            if (!issueLineMap.containsKey(issueLineId)) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "原出库行不存在：" + issueLineId);
            }
        }
        Set<Long> issueDocIds = issueLineMap.values().stream()
                .map(StockDocumentLine::getStockDocumentId).collect(Collectors.toSet());
        Map<Long, StockDocument> issueDocMap = documentMapper.selectByIds(issueDocIds).stream()
                .collect(Collectors.toMap(StockDocument::getId, doc -> doc));
        for (StockDocumentLine issueLine : issueLineMap.values()) {
            StockDocument issueDoc = issueDocMap.get(issueLine.getStockDocumentId());
            if (issueDoc == null || issueDoc.getMovementType() != MovementType.ISSUE
                    || issueDoc.getStatus() != StockDocumentStatus.POSTED) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "原出库行不属于已过账的领用出库单，无法归还：" + issueLine.getId());
            }
        }

        Set<Long> skuIds = issueLineMap.values().stream()
                .map(StockDocumentLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locationIds = new HashSet<>();
        for (StockReturnLineDTO line : dto.lines()) {
            Long to = IdUtils.optionalId(line.toLocationId(), "归还入库位 ID");
            if (to != null) {
                locationIds.add(to);
            } else {
                // 未指定入库位时默认回原出库位，原出库位需一并加载参与校验
                StockDocumentLine issueLine = issueLineMap.get(
                        IdUtils.requireId(line.originalIssueLineId(), "原出库行 ID"));
                if (issueLine != null && issueLine.getFromLocationId() != null) {
                    locationIds.add(issueLine.getFromLocationId());
                }
            }
        }
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));

        List<StockDocumentLine> lines = new ArrayList<>(dto.lines().size());
        int lineNo = 1;
        for (StockReturnLineDTO lineDto : dto.lines()) {
            Long issueLineId = IdUtils.requireId(lineDto.originalIssueLineId(), "原出库行 ID");
            StockDocumentLine issueLine = issueLineMap.get(issueLineId);
            MaterialSku sku = requireEnabledSku(skuMap, issueLine.getSkuId(),
                    String.valueOf(issueLine.getSkuId()));
            validateQuantityScale(lineDto.quantity(), sku);
            Long to = IdUtils.optionalId(lineDto.toLocationId(), "归还入库位 ID");
            if (to == null) {
                // 未指定入库位时默认回原出库位
                to = issueLine.getFromLocationId();
            }
            if (to == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "原出库行缺少出库位，请指定归还入库位");
            }
            requireEnabledLocation(locationMap, to, "归还入库位");
            // 创建时友好校验累计结算量（权威校验在过账行锁内）
            BigDecimal settled = settlementMapper.sumSettledQty(issueLineId);
            BigDecimal already = settled == null ? BigDecimal.ZERO : settled;
            if (already.add(lineDto.quantity()).compareTo(issueLine.getQuantity()) > 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "归还累计结算量超过原出库量：原出库 "
                                + issueLine.getQuantity().stripTrailingZeros().toPlainString()
                                + "，已结算 " + already.stripTrailingZeros().toPlainString()
                                + "，本次 " + lineDto.quantity().stripTrailingZeros().toPlainString());
            }

            StockDocumentLine line = new StockDocumentLine();
            line.setId(IdWorker.getId());
            line.setStockDocumentId(document.getId());
            line.setLineNo(lineNo++);
            line.setSkuId(issueLine.getSkuId());
            line.setToLocationId(to);
            line.setQuantity(lineDto.quantity());
            line.setOriginalIssueLineId(issueLineId);
            // 归还按原出库成本快照入库（过账参与移动加权）
            BigDecimal price = issueLine.getUnitPriceSnapshot();
            line.setUnitPriceSnapshot(price);
            line.setAmountSnapshot(price == null ? null
                    : lineDto.quantity().multiply(price).setScale(2, RoundingMode.HALF_UP));
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            lineMapper.insertBatch(lines);
        }
        return String.valueOf(document.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createScrap(StockScrapCreateDTO dto) {
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);
        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.SCRAP);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOwnerTeacherId(IdUtils.optionalId(dto.ownerTeacherId(), "归属老师 ID"));
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        documentMapper.insert(document);

        Set<Long> skuIds = dto.lines().stream()
                .map(line -> IdUtils.requireId(line.skuId(), "物料 ID"))
                .collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locationIds = dto.lines().stream()
                .map(line -> IdUtils.requireId(line.fromLocationId(), "报废出库位 ID"))
                .collect(Collectors.toSet());
        Map<Long, StockLocation> locationMap = locationMapper.selectByIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, location -> location));

        List<StockDocumentLine> lines = new ArrayList<>(dto.lines().size());
        int lineNo = 1;
        for (StockScrapLineDTO lineDto : dto.lines()) {
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.quantity(), sku);
            Long from = IdUtils.requireId(lineDto.fromLocationId(), "报废出库位 ID");
            requireEnabledLocation(locationMap, from, "报废出库位");

            StockDocumentLine line = new StockDocumentLine();
            line.setId(IdWorker.getId());
            line.setStockDocumentId(document.getId());
            line.setLineNo(lineNo++);
            line.setSkuId(skuId);
            line.setFromLocationId(from);
            line.setQuantity(lineDto.quantity());
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            lineMapper.insertBatch(lines);
        }
        return String.valueOf(document.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createReversal(StockReversalCreateDTO dto) {
        Long originalId = IdUtils.requireId(dto.reversalOfId(), "冲销原单 ID");
        StockDocument original = documentMapper.selectById(originalId);
        if (original == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "冲销原单不存在");
        }
        if (original.getStatus() != StockDocumentStatus.POSTED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅允许冲销已过账单据");
        }
        if (!REVERSABLE_TYPES.contains(original.getMovementType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "该业务类型不允许冲销：" + original.getMovementType()
                            + "（调拨/冲销/归还不开放冲销，见设计文档 7.1）");
        }
        if (original.getMaterialRequestId() != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领实发单据禁止冲销，请通过申领调整流程处理");
        }
        // 整单一次：已存在任何冲销单即拒绝
        Long reversed = documentMapper.selectCount(new LambdaQueryWrapper<StockDocument>()
                .eq(StockDocument::getReversalOfId, originalId));
        if (reversed != null && reversed > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "原单已被冲销，整单只允许冲销一次");
        }
        List<StockDocumentLine> originalLines = lineMapper.selectByDocumentId(originalId);
        if (originalLines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "原单没有明细行，无法冲销");
        }
        // 冲销前检查归还/核销依赖：原出库行已有结算记录则禁止冲销（批量查询替代逐行 count）
        if (original.getMovementType() == MovementType.ISSUE) {
            Set<Long> originalLineIds = originalLines.stream()
                    .map(StockDocumentLine::getId).collect(Collectors.toSet());
            Set<Long> settledIds = new HashSet<>(
                    settlementMapper.selectSettledIssueLineIds(originalLineIds));
            for (StockDocumentLine originalLine : originalLines) {
                if (settledIds.contains(originalLine.getId())) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "原出库行已有归还或核销记录，禁止冲销：" + originalLine.getId());
                }
            }
        }

        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.REVERSAL);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setReversalOfId(originalId);
        document.setOccurredAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        document.setReason(dto.reason());
        documentMapper.insert(document);

        // 复制原单行：原入库行 → 冲销出库行；原出库行 → 冲销入库行
        MoveDirection originalDirection = switch (original.getMovementType()) {
            case OPENING, PURCHASE_IN, RETURN_IN -> MoveDirection.IN;
            default -> MoveDirection.OUT;
        };
        List<StockDocumentLine> lines = new ArrayList<>(originalLines.size());
        int lineNo = 1;
        for (StockDocumentLine originalLine : originalLines) {
            StockDocumentLine line = new StockDocumentLine();
            line.setId(IdWorker.getId());
            line.setStockDocumentId(document.getId());
            line.setLineNo(lineNo++);
            line.setSkuId(originalLine.getSkuId());
            line.setQuantity(originalLine.getQuantity());
            if (originalDirection == MoveDirection.IN) {
                line.setFromLocationId(originalLine.getToLocationId());
                line.setUnitPriceSnapshot(null);
                line.setAmountSnapshot(null);
            } else {
                line.setToLocationId(originalLine.getFromLocationId());
                BigDecimal price = originalLine.getUnitPriceSnapshot();
                line.setUnitPriceSnapshot(price);
                line.setAmountSnapshot(price == null ? null
                        : originalLine.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP));
            }
            lines.add(line);
        }
        lineMapper.insertBatch(lines);
        return String.valueOf(document.getId());
    }

    private MaterialSku requireEnabledSku(Map<Long, MaterialSku> skuMap, Long skuId, String raw) {
        MaterialSku sku = skuMap.get(skuId);
        if (sku == null || sku.getEnabled() == null || sku.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料不存在或已停用：" + raw);
        }
        return sku;
    }

    private void requireEnabledLocation(Map<Long, StockLocation> locationMap, Long locationId, String label) {
        StockLocation location = locationMap.get(locationId);
        if (location == null || location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), label + "不存在或已停用：" + locationId);
        }
    }

    /**
     * 调拨过账：双边库位一起按 库位、SKU 排序锁定；同一行产生两条流水（:OUT / :IN），
     * 以来源库位成本转入目标库位；调出库位缺货整笔回滚。
     */
    private void postTransfer(StockDocument document, List<StockDocumentLine> lines) {
        Map<Long, MaterialSku> skuMap = loadSkuMap(lines);
        for (StockDocumentLine line : lines) {
            MaterialSku sku = requireSku(skuMap, line);
            validateQuantityScale(line.getQuantity(), sku);
            if (line.getFromLocationId() == null || line.getToLocationId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "调拨行必须同时填写调出与调入库位");
            }
            if (line.getFromLocationId().equals(line.getToLocationId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "调拨双边库位不能相同");
            }
        }

        SortedSet<LocSku> pairs = new TreeSet<>(
                Comparator.comparing(LocSku::locationId).thenComparing(LocSku::skuId));
        for (StockDocumentLine line : lines) {
            pairs.add(new LocSku(line.getFromLocationId(), line.getSkuId()));
            pairs.add(new LocSku(line.getToLocationId(), line.getSkuId()));
        }
        Map<LocSku, StockBalance> balanceMap = lockBalanceMap(pairs);

        for (StockDocumentLine line : lines) {
            MaterialSku sku = requireSku(skuMap, line);
            StockBalance src = balanceMap.get(new LocSku(line.getFromLocationId(), line.getSkuId()));
            StockBalance dst = balanceMap.get(new LocSku(line.getToLocationId(), line.getSkuId()));
            BigDecimal quantity = line.getQuantity();
            if (src.getStockQty().compareTo(quantity) < 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "调出库位库存不足：" + sku.getMaterialName() + "，可用 "
                                + src.getStockQty().stripTrailingZeros().toPlainString()
                                + " " + sku.getBaseUnit() + "，需要 "
                                + quantity.stripTrailingZeros().toPlainString());
            }
            BigDecimal srcAvg = src.getMovingAveragePrice();
            applyOutLeg(document, line, line.getFromLocationId(), src, quantity);
            applyInLeg(document, line, line.getToLocationId(), dst, quantity, srcAvg);
        }
    }

    /**
     * 冲销过账：原单为入库 → 冲销按当前账面均价出库；原单为出库 → 冲销按原出库成本快照回库；
     * 写相反流水并保留原单；原单已被冲销/存在结算依赖时整笔回滚（防御性复核）。
     */
    private void postReversal(StockDocument document, List<StockDocumentLine> lines) {
        if (document.getReversalOfId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "冲销单缺少原单绑定");
        }
        StockDocument original = documentMapper.selectById(document.getReversalOfId());
        if (original == null || original.getStatus() != StockDocumentStatus.POSTED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "冲销原单不存在或未过账");
        }
        // 防御：原单未被其他冲销单占用（createReversal 已校验，并发兜底）
        Long others = documentMapper.selectCount(new LambdaQueryWrapper<StockDocument>()
                .eq(StockDocument::getReversalOfId, original.getId())
                .ne(StockDocument::getId, document.getId()));
        if (others != null && others > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "原单已被冲销，整单只允许冲销一次");
        }
        List<StockDocumentLine> originalLines = lineMapper.selectByDocumentId(original.getId());
        Map<Integer, StockDocumentLine> originalByLineNo = originalLines.stream()
                .collect(Collectors.toMap(StockDocumentLine::getLineNo, l -> l, (a, b) -> a));

        Map<Long, MaterialSku> skuMap = loadSkuMap(lines);
        // 原单方向反转：原入库 → 冲销出库；原出库 → 冲销入库
        MoveDirection reversalDirection = switch (original.getMovementType()) {
            case OPENING, PURCHASE_IN, RETURN_IN -> MoveDirection.OUT;
            default -> MoveDirection.IN;
        };

        List<StockDocumentLine> sorted = lines.stream()
                .sorted(Comparator.comparing((StockDocumentLine line) -> targetLocationId(line, reversalDirection))
                        .thenComparing(StockDocumentLine::getSkuId))
                .toList();
        SortedSet<LocSku> pairs = new TreeSet<>(
                Comparator.comparing(LocSku::locationId).thenComparing(LocSku::skuId));
        for (StockDocumentLine line : sorted) {
            pairs.add(new LocSku(targetLocationId(line, reversalDirection), line.getSkuId()));
        }
        Map<LocSku, StockBalance> balanceMap = lockBalanceMap(pairs);

        for (StockDocumentLine line : sorted) {
            MaterialSku sku = requireSku(skuMap, line);
            validateQuantityScale(line.getQuantity(), sku);
            StockDocumentLine originalLine = originalByLineNo.get(line.getLineNo());
            if (originalLine == null || !originalLine.getSkuId().equals(line.getSkuId())
                    || originalLine.getQuantity().compareTo(line.getQuantity()) != 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "冲销行与原单行不匹配");
            }
            StockBalance balance = balanceMap.get(
                    new LocSku(targetLocationId(line, reversalDirection), line.getSkuId()));
            if (reversalDirection == MoveDirection.OUT) {
                // 撤销入库：按当前账面均价出库，回填成本快照
                BigDecimal beforeAvg = balance.getMovingAveragePrice();
                applyOutLeg(document, line, line.getFromLocationId(), balance, line.getQuantity());
                line.setUnitPriceSnapshot(beforeAvg);
                line.setAmountSnapshot(line.getQuantity().multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP));
                lineMapper.updateById(line);
            } else {
                // 撤销出库：按原出库成本快照回库
                BigDecimal inPrice = originalLine.getUnitPriceSnapshot() == null
                        ? BigDecimal.ZERO : originalLine.getUnitPriceSnapshot();
                applyInLeg(document, line, line.getToLocationId(), balance, line.getQuantity(), inPrice);
            }
        }
    }

    /**
     * 过账落账后处理：RETURN_IN 同事务生成 RETURNED 结算（同归还行唯一键兜底）；
     * ISSUE 申领实发回写 fulfilled_qty（条件更新防超批准量）并推进申领状态。
     */
    private void afterPosted(StockDocument document, List<StockDocumentLine> lines,
                              Map<Long, StockDocumentLine> issueLineMap,
                              Map<Long, MaterialRequestLine> requestLineMap) {
        if (document.getMovementType() == MovementType.RETURN_IN) {
            Instant now = Instant.now();
            for (StockDocumentLine line : lines) {
                StockDocumentLine issueLine = issueLineMap.get(line.getId());
                if (issueLine == null) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "归还行缺少原出库行绑定");
                }
                MaterialSettlement settlement = new MaterialSettlement();
                settlement.setIssueLineId(issueLine.getId());
                settlement.setSettlementType(SettlementType.RETURNED);
                settlement.setQuantity(line.getQuantity());
                settlement.setSettledAt(now);
                settlement.setReturnLineId(line.getId());
                settlement.setDocumentId(document.getDocumentId());
                settlement.setReason(document.getReason());
                try {
                    settlementMapper.insert(settlement);
                } catch (DuplicateKeyException e) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "该归还行已生成结算记录，禁止重复过账");
                }
            }
            return;
        }
        if (document.getMovementType() == MovementType.ISSUE && !requestLineMap.isEmpty()) {
            Set<Long> requestIds = new HashSet<>();
            for (StockDocumentLine line : lines) {
                if (line.getRequestLineId() == null) {
                    continue;
                }
                // 条件更新：累加后不超过批准量，0 行表示超批准量，整笔回滚
                if (requestLineMapper.increaseFulfilledQty(line.getRequestLineId(), line.getQuantity()) == 0) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "实发量超过申领批准量，整笔回滚：" + line.getRequestLineId());
                }
                MaterialRequestLine requestLine = requestLineMap.get(line.getId());
                if (requestLine != null) {
                    requestIds.add(requestLine.getRequestId());
                }
            }
            // 推进申领状态：全部行实发达批准量 → FULFILLED，否则 PARTIALLY_FULFILLED
            for (Long requestId : requestIds) {
                List<MaterialRequestLine> latest = requestLineMapper.selectByRequestId(requestId);
                boolean allFulfilled = latest.stream().allMatch(line ->
                        line.getApprovedQty() != null
                                && line.getFulfilledQty().compareTo(line.getApprovedQty()) >= 0);
                int moved = allFulfilled
                        ? requestMapper.casMarkFulfilled(requestId)
                        : requestMapper.casMarkPartiallyFulfilled(requestId);
                if (allFulfilled && moved == 0) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单状态已变更，实发回写失败");
                }
            }
        }
    }

    /** 归还行锁原出库行并校验累计结算量不超过原出库量（行锁串行化并发归还） */
    private Map<Long, StockDocumentLine> checkReturnQuota(List<StockDocumentLine> lines) {
        Map<Long, StockDocumentLine> issueLineMap = new HashMap<>();
        for (StockDocumentLine line : lines) {
            if (line.getOriginalIssueLineId() == null) {
                continue;
            }
            StockDocumentLine issueLine = lineMapper.lockById(line.getOriginalIssueLineId());
            if (issueLine == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "原出库行不存在：" + line.getOriginalIssueLineId());
            }
            BigDecimal settled = settlementMapper.sumSettledQty(issueLine.getId());
            BigDecimal already = settled == null ? BigDecimal.ZERO : settled;
            if (already.add(line.getQuantity()).compareTo(issueLine.getQuantity()) > 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "归还累计结算量超过原出库量：原出库 "
                                + issueLine.getQuantity().stripTrailingZeros().toPlainString()
                                + "，已结算 " + already.stripTrailingZeros().toPlainString()
                                + "，本次 " + line.getQuantity().stripTrailingZeros().toPlainString());
            }
            issueLineMap.put(line.getId(), issueLine);
        }
        return issueLineMap;
    }

    /** 申领实发行锁申领行并校验批准量（权威校验为条件更新，此处提供精确错误信息） */
    private Map<Long, MaterialRequestLine> checkIssueQuota(List<StockDocumentLine> lines) {
        Map<Long, MaterialRequestLine> requestLineMap = new HashMap<>();
        for (StockDocumentLine line : lines) {
            if (line.getRequestLineId() == null) {
                continue;
            }
            MaterialRequestLine requestLine = requestLineMapper.lockById(line.getRequestLineId());
            if (requestLine == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "申领行不存在：" + line.getRequestLineId());
            }
            BigDecimal fulfilled = requestLine.getFulfilledQty() == null
                    ? BigDecimal.ZERO : requestLine.getFulfilledQty();
            if (requestLine.getApprovedQty() == null
                    || fulfilled.add(line.getQuantity()).compareTo(requestLine.getApprovedQty()) > 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "实发量超过申领批准量：批准 "
                                + (requestLine.getApprovedQty() == null ? "未批准"
                                        : requestLine.getApprovedQty().stripTrailingZeros().toPlainString())
                                + "，已实发 " + fulfilled.stripTrailingZeros().toPlainString()
                                + "，本次 " + line.getQuantity().stripTrailingZeros().toPlainString());
            }
            requestLineMap.put(line.getId(), requestLine);
        }
        return requestLineMap;
    }

    /** 入库腿：移动加权均价更新 + 写 IN 流水（inPrice 为入账单价） */
    private void applyInLeg(StockDocument document, StockDocumentLine line, Long locationId,
                            StockBalance balance, BigDecimal quantity, BigDecimal inPrice) {
        BigDecimal beforeQty = balance.getStockQty();
        BigDecimal beforeAvg = balance.getMovingAveragePrice();
        BigDecimal afterQty = beforeQty.add(quantity);
        BigDecimal afterAvg = afterQty.signum() > 0
                ? beforeQty.multiply(beforeAvg).add(quantity.multiply(inPrice))
                        .divide(afterQty, 4, RoundingMode.HALF_UP)
                : beforeAvg;
        BigDecimal valueDelta = quantity.multiply(inPrice).setScale(2, RoundingMode.HALF_UP);
        if (balanceMapper.updatePostBalance(balance.getId(), balance.getVersion(), afterQty, afterAvg) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "库存余额更新异常，请重试");
        }
        insertMovement(document, line, locationId, MoveDirection.IN, quantity,
                beforeQty, afterQty, valueDelta, beforeAvg, afterAvg);
    }

    /** 出库腿：缺货校验 + 账面均价计成本 + 写 OUT 流水 */
    private void applyOutLeg(StockDocument document, StockDocumentLine line, Long locationId,
                             StockBalance balance, BigDecimal quantity) {
        BigDecimal beforeQty = balance.getStockQty();
        BigDecimal beforeAvg = balance.getMovingAveragePrice();
        if (beforeQty.compareTo(quantity) < 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存不足，无法完成过账");
        }
        BigDecimal afterQty = beforeQty.subtract(quantity);
        BigDecimal valueDelta = quantity.multiply(beforeAvg).setScale(2, RoundingMode.HALF_UP).negate();
        if (balanceMapper.updatePostBalance(balance.getId(), balance.getVersion(), afterQty, beforeAvg) == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "库存余额更新异常，请重试");
        }
        insertMovement(document, line, locationId, MoveDirection.OUT, quantity,
                beforeQty, afterQty, valueDelta, beforeAvg, beforeAvg);
    }

    private void insertMovement(StockDocument document, StockDocumentLine line, Long locationId,
                                MoveDirection direction, BigDecimal quantity,
                                BigDecimal beforeQty, BigDecimal afterQty, BigDecimal valueDelta,
                                BigDecimal beforeAvg, BigDecimal afterAvg) {
        StockMovement movement = new StockMovement();
        movement.setDocumentLineId(line.getId());
        movement.setLocationId(locationId);
        movement.setSkuId(line.getSkuId());
        movement.setDirection(direction);
        movement.setQtyDelta(direction == MoveDirection.IN ? quantity : quantity.negate());
        movement.setBeforeQty(beforeQty);
        movement.setAfterQty(afterQty);
        movement.setValueDelta(valueDelta);
        movement.setBeforeAvgPrice(beforeAvg);
        movement.setAfterAvgPrice(afterAvg);
        movement.setOccurredAt(document.getOccurredAt());
        movement.setMovementKey("STK:" + document.getDocumentId() + ":" + line.getLineNo()
                + ":" + direction);
        // 幂等键唯一冲突表示重复过账，整笔回滚
        try {
            movementMapper.insert(movement);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "流水已存在，禁止重复过账");
        }
    }

    private MaterialSku requireSku(Map<Long, MaterialSku> skuMap, StockDocumentLine line) {
        MaterialSku sku = skuMap.get(line.getSkuId());
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在，无法过账");
        }
        return sku;
    }

    private Map<Long, MaterialSku> loadSkuMap(List<StockDocumentLine> lines) {
        return skuMapper.selectByIds(lines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
    }

    private Map<LocSku, StockBalance> lockBalanceMap(SortedSet<LocSku> pairs) {
        Map<LocSku, StockBalance> balances = new HashMap<>();
        for (LocSku pair : pairs) {
            balances.put(pair, lockBalance(pair.locationId(), pair.skuId()));
        }
        return balances;
    }
}

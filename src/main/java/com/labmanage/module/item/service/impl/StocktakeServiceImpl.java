package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeCreateDTO;
import com.labmanage.module.item.dto.StocktakeQuery;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.Stocktake;
import com.labmanage.module.item.entity.StocktakeLine;
import com.labmanage.module.item.enums.AdjustDirection;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.enums.StocktakeStatus;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StocktakeLineMapper;
import com.labmanage.module.item.mapper.StocktakeMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StocktakeService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.StocktakeDetailVO;
import com.labmanage.module.item.vo.StocktakeLineVO;
import com.labmanage.module.item.vo.StocktakeVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 盘点服务实现
 * <p>
 * 冻结是持久业务状态：COUNTING/PENDING_REVIEW 期间库存引擎拒绝该库位一切写入；
 * 提交复核时以余额版本快照校验建单后无变动，差异非零生成 ADJUST 调账单，
 * 调账过账完成后自动关闭盘点单并解冻。
 */
@Service
public class StocktakeServiceImpl implements StocktakeService {

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeLineMapper stocktakeLineMapper;
    private final StockBalanceMapper balanceMapper;
    private final StockLocationMapper locationMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockDocumentMapper documentMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final StockDocumentService documentService;

    public StocktakeServiceImpl(StocktakeMapper stocktakeMapper,
                                 StocktakeLineMapper stocktakeLineMapper,
                                 StockBalanceMapper balanceMapper,
                                 StockLocationMapper locationMapper,
                                 MaterialSkuMapper skuMapper,
                                 StockDocumentMapper documentMapper,
                                 StockDocumentLineMapper documentLineMapper,
                                 StockDocumentService documentService) {
        this.stocktakeMapper = stocktakeMapper;
        this.stocktakeLineMapper = stocktakeLineMapper;
        this.balanceMapper = balanceMapper;
        this.locationMapper = locationMapper;
        this.skuMapper = skuMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.documentService = documentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(StocktakeCreateDTO dto) {
        Long locationId = IdUtils.requireId(dto.locationId(), "库位 ID");
        StockLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        if (location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位已停用，无法盘点");
        }

        Stocktake stocktake = new Stocktake();
        stocktake.setDocumentId(IdWorker.getId());
        stocktake.setLocationId(locationId);
        stocktake.setSemesterId(IdUtils.optionalId(dto.semesterId(), "学期 ID"));
        stocktake.setCountDate(dto.countDate());
        stocktake.setStatus(StocktakeStatus.DRAFT);
        stocktakeMapper.insert(stocktake);

        // 建单即快照：复制该库位全部余额行的账面量与余额版本（批量插入替代逐行 insert）
        List<StockBalance> balances = balanceMapper.selectList(
                new LambdaQueryWrapper<StockBalance>().eq(StockBalance::getLocationId, locationId));
        List<StocktakeLine> snapshotLines = new java.util.ArrayList<>(balances.size());
        for (StockBalance balance : balances) {
            StocktakeLine line = new StocktakeLine();
            line.setId(IdWorker.getId());
            line.setStocktakeId(stocktake.getId());
            line.setSkuId(balance.getSkuId());
            line.setBookQtySnapshot(balance.getStockQty());
            line.setBalanceVersionSnapshot(balance.getVersion());
            snapshotLines.add(line);
        }
        if (!snapshotLines.isEmpty()) {
            stocktakeLineMapper.insertBatch(snapshotLines);
        }
        return String.valueOf(stocktake.getId());
    }

    @Override
    public PageResult<StocktakeVO> page(StocktakeQuery query) {
        LambdaQueryWrapper<Stocktake> wrapper = new LambdaQueryWrapper<>();
        if (query.getLocationId() != null && !query.getLocationId().isBlank()) {
            wrapper.eq(Stocktake::getLocationId, IdUtils.requireId(query.getLocationId(), "库位 ID"));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Stocktake::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Stocktake::getId);
        IPage<Stocktake> page = stocktakeMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<Stocktake> records = page.getRecords();
        Set<Long> locationIds = records.stream().map(Stocktake::getLocationId).collect(Collectors.toSet());
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));
        List<StocktakeVO> vos = records.stream()
                .map(e -> StocktakeVO.from(e,
                        locationMap.containsKey(e.getLocationId())
                                ? locationMap.get(e.getLocationId()).getLocationName() : null))
                .toList();
        return PageResult.of(vos, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public StocktakeDetailVO get(String id) {
        Stocktake stocktake = requireStocktake(id);
        List<StocktakeLine> lines = stocktakeLineMapper.selectByStocktakeId(stocktake.getId());
        StockLocation location = locationMapper.selectById(stocktake.getLocationId());

        Set<Long> skuIds = lines.stream().map(StocktakeLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        List<StocktakeLineVO> lineVos = lines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    return StocktakeLineVO.from(line,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit());
                })
                .toList();
        return StocktakeDetailVO.from(stocktake,
                location == null ? null : location.getLocationName(), lineVos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(String id) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktake.getStatus() != StocktakeStatus.DRAFT) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不处于草稿状态，无法开始盘点");
        }
        // 行锁检查同库位活跃盘点单（uk_item_stocktake_active 生成列唯一键兜底并发）
        Stocktake active = stocktakeMapper.lockActiveByLocationId(stocktake.getLocationId());
        if (active != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "该库位已有进行中的盘点单，请先完成或等待其结束");
        }
        try {
            if (stocktakeMapper.casStart(stocktake.getId(), Instant.now()) == 0) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "盘点单状态已变更，无法开始盘点");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "该库位已有进行中的盘点单（并发冲突），请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void count(String id, StocktakeCountDTO dto) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不处于盘点中状态，无法录入实盘");
        }
        List<StocktakeLine> lines = stocktakeLineMapper.selectByStocktakeId(stocktake.getId());
        Set<Long> lineIds = lines.stream().map(StocktakeLine::getId).collect(Collectors.toSet());
        Instant now = Instant.now();
        for (StocktakeCountDTO.CountLineDTO lineDto : dto.lines()) {
            Long lineId = IdUtils.requireId(lineDto.lineId(), "盘点行 ID");
            if (!lineIds.contains(lineId)) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "盘点行不存在或不属于该盘点单：" + lineDto.lineId());
            }
            if (stocktakeLineMapper.updateCountedQty(lineId, stocktake.getId(),
                    lineDto.countedQty(), lineDto.differenceReason(), now) == 0) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(),
                        "实盘录入失败，请重试");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitReview(String id) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不处于盘点中状态，无法提交复核");
        }
        List<StocktakeLine> lines = stocktakeLineMapper.selectByStocktakeId(stocktake.getId());

        // 全部行已录入 + 差异行必填原因
        List<StocktakeLine> differenceLines = new java.util.ArrayList<>();
        for (StocktakeLine line : lines) {
            if (line.getCountedQty() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "存在未录入实盘量的盘点行，无法提交复核");
            }
            if (line.getCountedQty().compareTo(line.getBookQtySnapshot()) != 0) {
                if (line.getDifferenceReason() == null || line.getDifferenceReason().isBlank()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "差异非零的盘点行必须填写差异原因");
                }
                differenceLines.add(line);
            }
        }

        // 余额版本校验：建单后发生过库存变动则要求重新盘点
        if (!lines.isEmpty()) {
            List<StockBalance> balances = balanceMapper.selectList(
                    new LambdaQueryWrapper<StockBalance>()
                            .eq(StockBalance::getLocationId, stocktake.getLocationId()));
            Map<Long, StockBalance> balanceMap = balances.stream()
                    .collect(Collectors.toMap(StockBalance::getSkuId, Function.identity()));
            for (StocktakeLine line : lines) {
                StockBalance balance = balanceMap.get(line.getSkuId());
                Integer snapshotVersion = line.getBalanceVersionSnapshot();
                if (balance == null ? snapshotVersion != null
                        : !balance.getVersion().equals(snapshotVersion)) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "盘点期间库存已发生变动（物料：" + line.getSkuId() + "），请重新盘点");
                }
            }
        }

        // 差异非零：生成 ADJUST 调账单草稿（审批过账由调账审批人执行）
        Long adjustDocumentId = null;
        if (!differenceLines.isEmpty()) {
            StockDocument adjust = new StockDocument();
            adjust.setDocumentId(IdWorker.getId());
            adjust.setMovementType(MovementType.ADJUST);
            adjust.setStatus(StockDocumentStatus.DRAFT);
            adjust.setOccurredAt(Instant.now());
            adjust.setReason("盘点调账：差异行 " + differenceLines.size() + " 条");
            documentMapper.insert(adjust);

            int lineNo = 1;
            List<StockDocumentLine> adjustLines = new java.util.ArrayList<>(differenceLines.size());
            for (StocktakeLine line : differenceLines) {
                BigDecimal difference = line.getCountedQty().subtract(line.getBookQtySnapshot());
                StockDocumentLine adjustLine = new StockDocumentLine();
                adjustLine.setId(IdWorker.getId());
                adjustLine.setStockDocumentId(adjust.getId());
                adjustLine.setLineNo(lineNo++);
                adjustLine.setSkuId(line.getSkuId());
                if (difference.signum() > 0) {
                    adjustLine.setAdjustDirection(AdjustDirection.SURPLUS.name());
                    adjustLine.setToLocationId(stocktake.getLocationId());
                    adjustLine.setQuantity(difference.setScale(3, RoundingMode.HALF_UP));
                } else {
                    adjustLine.setAdjustDirection(AdjustDirection.LOSS.name());
                    adjustLine.setFromLocationId(stocktake.getLocationId());
                    adjustLine.setQuantity(difference.negate().setScale(3, RoundingMode.HALF_UP));
                }
                adjustLines.add(adjustLine);
            }
            documentLineMapper.insertBatch(adjustLines);
            adjustDocumentId = adjust.getId();
            stocktakeMapper.updateAdjustDocumentId(stocktake.getId(), adjustDocumentId);
        }

        if (stocktakeMapper.casSubmitReview(stocktake.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单状态已变更，提交复核失败");
        }
        return adjustDocumentId == null ? null : String.valueOf(adjustDocumentId);
    }

    @Override
    public void complete(String id) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktake.getStatus() != StocktakeStatus.PENDING_REVIEW) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不处于待复核状态，无法确认完成");
        }
        if (stocktake.getAdjustDocumentId() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "存在差异调账单，请先审批调账，过账后自动完成盘点");
        }
        if (stocktakeMapper.casComplete(stocktake.getId(), Instant.now()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单状态已变更，确认完成失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAdjust(String id) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktake.getStatus() != StocktakeStatus.PENDING_REVIEW) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不处于待复核状态，无需审批调账");
        }
        Long adjustDocumentId = stocktake.getAdjustDocumentId();
        if (adjustDocumentId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "该盘点单没有差异调账单，无差异请直接确认完成");
        }
        // 一步审批并过账调账单；过账引擎按行方向调账并自动关闭盘点单（解冻库位）
        String adjustId = String.valueOf(adjustDocumentId);
        documentService.approve(adjustId);
        documentService.post(adjustId);
    }

    @Override
    public void cancel(String id) {
        Stocktake stocktake = requireStocktake(id);
        if (stocktakeMapper.casCancel(stocktake.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "盘点单不存在或已开始盘点，无法取消");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Stocktake requireStocktake(String id) {
        Stocktake stocktake = stocktakeMapper.selectById(IdUtils.requireId(id, "盘点单 ID"));
        if (stocktake == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "盘点单不存在");
        }
        return stocktake;
    }
}

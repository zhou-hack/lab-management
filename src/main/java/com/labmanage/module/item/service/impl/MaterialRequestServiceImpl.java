package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestLineDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestLineVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料申领服务实现
 * <p>
 * 申领批准不扣库存；实发时生成关联申领行的 ISSUE 草稿单，过账阶段由库存引擎
 * 行锁申领行 + 条件更新 fulfilled_qty（防超批准量）并推进申领状态。
 */
@Service
public class MaterialRequestServiceImpl implements MaterialRequestService {

    private final MaterialRequestMapper requestMapper;
    private final MaterialRequestLineMapper requestLineMapper;
    private final MaterialQuotaMapper quotaMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentMapper documentMapper;
    private final StockDocumentLineMapper documentLineMapper;

    public MaterialRequestServiceImpl(MaterialRequestMapper requestMapper,
                                      MaterialRequestLineMapper requestLineMapper,
                                      MaterialQuotaMapper quotaMapper,
                                      MaterialSkuMapper skuMapper,
                                      StockLocationMapper locationMapper,
                                      StockDocumentMapper documentMapper,
                                      StockDocumentLineMapper documentLineMapper) {
        this.requestMapper = requestMapper;
        this.requestLineMapper = requestLineMapper;
        this.quotaMapper = quotaMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MaterialRequestCreateDTO dto) {
        Long labBookingId = IdUtils.optionalId(dto.labBookingId(), "场地预约 ID");
        Long scheduleOccurrenceId = IdUtils.optionalId(dto.scheduleOccurrenceId(), "课次 ID");
        if (labBookingId != null && scheduleOccurrenceId != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "场地预约与课次不能同时填写，只能取其一");
        }
        Long offeringId = IdUtils.optionalId(dto.offeringId(), "开课实例 ID");

        // 申领单 + SKU 唯一：同一单内物料去重
        Set<Long> skuIds = new HashSet<>();
        for (MaterialRequestLineDTO line : dto.lines()) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            if (!skuIds.add(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "申领行物料重复：" + line.skuId());
            }
        }
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        // 启用定额一次加载（批量查询替代逐行 selectByOfferingAndSku）
        Map<Long, MaterialQuota> quotaMap = offeringId == null ? Map.of()
                : quotaMapper.selectByOfferingAndSkuIds(offeringId, skuIds).stream()
                        .collect(Collectors.toMap(MaterialQuota::getSkuId, quota -> quota));

        MaterialRequest request = new MaterialRequest();
        request.setDocumentId(IdWorker.getId());
        request.setLabId(IdUtils.optionalId(dto.labId(), "实验室 ID"));
        request.setOwnerTeacherId(IdUtils.optionalId(dto.ownerTeacherId(), "归属老师 ID"));
        request.setOperatorId(IdUtils.optionalId(dto.operatorId(), "经办人 ID"));
        request.setGrantId(IdUtils.optionalId(dto.grantId(), "助理授权 ID"));
        request.setOfferingId(offeringId);
        request.setLabBookingId(labBookingId);
        request.setScheduleOccurrenceId(scheduleOccurrenceId);
        request.setParticipantCount(dto.participantCount());
        request.setPeriodCount(dto.periodCount());
        request.setPurpose(dto.purpose());
        request.setStatus(MaterialRequestStatus.DRAFT);
        requestMapper.insert(request);

        BigDecimal participant = dto.participantCount() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(dto.participantCount());
        BigDecimal period = dto.periodCount() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(dto.periodCount());

        List<MaterialRequestLine> lines = new ArrayList<>(dto.lines().size());
        for (MaterialRequestLineDTO lineDto : dto.lines()) {
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.requestedQty(), sku);

            // 定额快照：开课实例有启用定额时按 参与人数 × 课时 × 每人每课时定额；缺定额记 0
            BigDecimal quotaSnapshot = BigDecimal.ZERO;
            if (offeringId != null) {
                MaterialQuota quota = quotaMap.get(skuId);
                if (quota != null && quota.getEnabled() != null && quota.getEnabled() == 1) {
                    quotaSnapshot = participant.multiply(period)
                            .multiply(quota.getQtyPerStudentPeriod())
                            .setScale(3, RoundingMode.HALF_UP);
                }
            }

            MaterialRequestLine line = new MaterialRequestLine();
            line.setId(IdWorker.getId());
            line.setRequestId(request.getId());
            line.setSkuId(skuId);
            line.setRequestedQty(lineDto.requestedQty());
            line.setQuotaQtySnapshot(quotaSnapshot);
            // 超定额（含缺定额按 0 记）须老师签批确认
            line.setRequiresExtraApproval(
                    lineDto.requestedQty().compareTo(quotaSnapshot) > 0 ? 1 : 0);
            // 危险/高价值物料要求双人签字（标志按 SKU 主档预填，签字依赖后续接入）
            line.setRequiresDualSign(isOne(sku.getIsHazardous()) || isOne(sku.getIsValuable()) ? 1 : 0);
            line.setFulfilledQty(BigDecimal.ZERO);
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            requestLineMapper.insertBatch(lines);
        }
        return String.valueOf(request.getId());
    }

    @Override
    public PageResult<MaterialRequestVO> page(MaterialRequestQuery query) {
        LambdaQueryWrapper<MaterialRequest> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(MaterialRequest::getStatus, query.getStatus());
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            wrapper.eq(MaterialRequest::getLabId, IdUtils.requireId(query.getLabId(), "实验室 ID"));
        }
        if (query.getOwnerTeacherId() != null && !query.getOwnerTeacherId().isBlank()) {
            wrapper.eq(MaterialRequest::getOwnerTeacherId,
                    IdUtils.requireId(query.getOwnerTeacherId(), "归属老师 ID"));
        }
        if (query.getOfferingId() != null && !query.getOfferingId().isBlank()) {
            wrapper.eq(MaterialRequest::getOfferingId,
                    IdUtils.requireId(query.getOfferingId(), "开课实例 ID"));
        }
        wrapper.orderByDesc(MaterialRequest::getId);
        IPage<MaterialRequest> page = requestMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialRequestVO> records = page.getRecords().stream()
                .map(MaterialRequestVO::from).toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public MaterialRequestDetailVO get(String id) {
        MaterialRequest request = requireRequest(id);
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Set<Long> skuIds = lines.stream().map(MaterialRequestLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        List<MaterialRequestLineVO> lineVos = lines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    return MaterialRequestLineVO.from(line,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit());
                })
                .toList();
        return MaterialRequestDetailVO.from(request, lineVos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String id, MaterialRequestApproveDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (request.getStatus() != MaterialRequestStatus.DRAFT) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单不处于草稿状态，无法审批");
        }
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Map<Long, MaterialRequestLine> lineMap = lines.stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        Map<Long, BigDecimal> approvedMap = new HashMap<>();
        if (dto == null || dto.lines() == null || dto.lines().isEmpty()) {
            // 未指定行批准量：全部行按申请量批准
            lines.forEach(line -> approvedMap.put(line.getId(), line.getRequestedQty()));
        } else {
            for (MaterialRequestApproveDTO.ApproveLineDTO lineDto : dto.lines()) {
                Long lineId = IdUtils.requireId(lineDto.lineId(), "申领行 ID");
                MaterialRequestLine line = lineMap.get(lineId);
                if (line == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                            "申领行不存在：" + lineDto.lineId());
                }
                if (lineDto.approvedQty().compareTo(line.getRequestedQty()) > 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "批准量不能超过申请量：申请 "
                                    + line.getRequestedQty().stripTrailingZeros().toPlainString()
                                    + "，批准 " + lineDto.approvedQty().stripTrailingZeros().toPlainString());
                }
                approvedMap.put(lineId, lineDto.approvedQty());
            }
            // 未指定的行按申请量批准
            lines.forEach(line -> approvedMap.putIfAbsent(line.getId(), line.getRequestedQty()));
        }
        for (Map.Entry<Long, BigDecimal> entry : approvedMap.entrySet()) {
            MaterialRequestLine line = lineMap.get(entry.getKey());
            line.setApprovedQty(entry.getValue());
            requestLineMapper.updateById(line);
        }
        if (requestMapper.casApprove(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单状态已变更，审批失败");
        }
    }

    @Override
    public void reject(String id) {
        MaterialRequest request = requireRequest(id);
        if (requestMapper.casReject(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不存在或不处于草稿状态，无法拒绝");
        }
    }

    @Override
    public void cancel(String id) {
        MaterialRequest request = requireRequest(id);
        if (request.hasFulfillment()) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单已有实发量，不能取消，请走冲销流程");
        }
        if (requestMapper.casCancel(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单当前状态不允许取消");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issue(String id, MaterialRequestIssueDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (!request.issuable()) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单未批准，不能实发出库");
        }
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Map<Long, MaterialRequestLine> lineMap = lines.stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        Long defaultLocationId = IdUtils.requireId(dto.fromLocationId(), "出库位 ID");

        // 组装本次实发行：指定行用指定量与库位，未指定行按剩余批准量 + 默认库位
        List<StockDocumentLine> issueLines = new ArrayList<>();
        Map<Long, Long> locationIds = new HashMap<>();
        if (dto.lines() == null || dto.lines().isEmpty()) {
            for (MaterialRequestLine line : lines) {
                BigDecimal remaining = remainingOf(line);
                if (remaining.signum() <= 0) {
                    continue;
                }
                locationIds.put(line.getId(), defaultLocationId);
                issueLines.add(buildIssueLine(line, remaining, defaultLocationId));
            }
        } else {
            for (MaterialRequestIssueDTO.IssueLineDTO lineDto : dto.lines()) {
                Long lineId = IdUtils.requireId(lineDto.lineId(), "申领行 ID");
                MaterialRequestLine line = lineMap.get(lineId);
                if (line == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                            "申领行不存在：" + lineDto.lineId());
                }
                Long from = lineDto.fromLocationId() == null || lineDto.fromLocationId().isBlank()
                        ? defaultLocationId
                        : IdUtils.requireId(lineDto.fromLocationId(), "出库位 ID");
                if (lineDto.quantity().compareTo(remainingOf(line)) > 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "实发量超过剩余批准量：批准 "
                                    + line.getApprovedQty().stripTrailingZeros().toPlainString()
                                    + "，已实发 " + line.getFulfilledQty().stripTrailingZeros().toPlainString()
                                    + "，本次 " + lineDto.quantity().stripTrailingZeros().toPlainString());
                }
                locationIds.put(line.getId(), from);
                issueLines.add(buildIssueLine(line, lineDto.quantity(), from));
            }
        }
        if (issueLines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "没有可实发的申领行（批准量已发完或未指定有效行）");
        }

        // 校验物料与库位
        Set<Long> skuIds = issueLines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locIds = new HashSet<>(locationIds.values());
        Map<Long, StockLocation> locationMap = locIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));
        for (StockDocumentLine issueLine : issueLines) {
            MaterialSku sku = requireEnabledSku(skuMap, issueLine.getSkuId(),
                    String.valueOf(issueLine.getSkuId()));
            validateQuantityScale(issueLine.getQuantity(), sku);
            requireEnabledLocation(locationMap, issueLine.getFromLocationId());
        }

        // 生成关联申领单的 ISSUE 草稿单（审批与过账走库存单据既有流程）
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);
        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.ISSUE);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOperatorId(request.getOperatorId());
        document.setOwnerTeacherId(request.getOwnerTeacherId());
        document.setGrantId(request.getGrantId());
        document.setMaterialRequestId(request.getId());
        document.setLabBookingId(request.getLabBookingId());
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        documentMapper.insert(document);

        int lineNo = 1;
        for (StockDocumentLine issueLine : issueLines) {
            issueLine.setId(IdWorker.getId());
            issueLine.setStockDocumentId(document.getId());
            issueLine.setLineNo(lineNo++);
        }
        documentLineMapper.insertBatch(issueLines);
        return String.valueOf(document.getId());
    }

    // ---------------------------------------------------------------- helpers

    private MaterialRequest requireRequest(String id) {
        Long requestPk = IdUtils.requireId(id, "申领单 ID");
        MaterialRequest request = requestMapper.selectById(requestPk);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return request;
    }

    private BigDecimal remainingOf(MaterialRequestLine line) {
        if (line.getApprovedQty() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fulfilled = line.getFulfilledQty() == null
                ? BigDecimal.ZERO : line.getFulfilledQty();
        return line.getApprovedQty().subtract(fulfilled);
    }

    private StockDocumentLine buildIssueLine(MaterialRequestLine line, BigDecimal quantity, Long fromLocationId) {
        StockDocumentLine issueLine = new StockDocumentLine();
        issueLine.setSkuId(line.getSkuId());
        issueLine.setFromLocationId(fromLocationId);
        issueLine.setQuantity(quantity);
        issueLine.setRequestLineId(line.getId());
        return issueLine;
    }

    private MaterialSku requireEnabledSku(Map<Long, MaterialSku> skuMap, Long skuId, String raw) {
        MaterialSku sku = skuMap.get(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在：" + raw);
        }
        if (sku.getEnabled() == null || sku.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料已停用：" + raw);
        }
        return sku;
    }

    private void requireEnabledLocation(Map<Long, StockLocation> locationMap, Long locationId) {
        StockLocation location = locationMap.get(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在：" + locationId);
        }
        if (location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位已停用：" + locationId);
        }
    }

    private void validateQuantityScale(BigDecimal quantity, MaterialSku sku) {
        int scale = sku.getQuantityScale() == null ? 3 : sku.getQuantityScale();
        if (quantity.stripTrailingZeros().scale() > scale) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "数量精度超过物料数量精度（最多 " + scale + " 位小数）：" + sku.getSkuCode());
        }
    }

    private boolean isOne(Integer flag) {
        return flag != null && flag == 1;
    }
}

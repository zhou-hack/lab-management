package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestLineDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.dto.MaterialRequestReturnDTO;
import com.labmanage.module.item.dto.StockReturnCreateDTO;
import com.labmanage.module.item.dto.StockReturnLineDTO;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.PickupMode;
import com.labmanage.module.item.enums.RequestType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestLineVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
import com.labmanage.module.item.vo.ReturnableLineVO;
import com.labmanage.module.item.vo.TeacherOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料申领服务实现
 * <p>
 * 申领批准不扣库存；实发时生成关联申领行的 ISSUE 草稿单，过账阶段由库存引擎
 * 行锁申领行 + 条件更新 fulfilled_qty（防超批准量）并推进申领状态。
 * <p>
 * 两级审核（2026-10-19）：SELF 本人申领经老师审核（DRAFT → TEACHER_APPROVED）再经
 * 管理员终审（TEACHER_APPROVED → APPROVED）；PROXY 学生代理代领只能引用老师已审核
 * 通过的源申领单并按源行剩余可代领量领取，创建即 TEACHER_APPROVED（无需老师再审核），
 * 责任老师 = 源单归属老师，代领人 = operator_id，直接交由管理员终审。
 */
@Service
public class MaterialRequestServiceImpl implements MaterialRequestService {

    /** 申领单终审权限（持有者为管理员视角：全量数据范围、可审任意单） */
    private static final String PERM_REQUEST_APPROVE = "item:request:approve";

    private final MaterialRequestMapper requestMapper;
    private final MaterialRequestLineMapper requestLineMapper;
    private final MaterialQuotaMapper quotaMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentMapper documentMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final StockBalanceMapper balanceMapper;
    private final SysUserRefMapper sysUserRefMapper;
    private final StockDocumentService stockDocumentService;

    public MaterialRequestServiceImpl(MaterialRequestMapper requestMapper,
                                      MaterialRequestLineMapper requestLineMapper,
                                      MaterialQuotaMapper quotaMapper,
                                      MaterialSkuMapper skuMapper,
                                      StockLocationMapper locationMapper,
                                      StockDocumentMapper documentMapper,
                                      StockDocumentLineMapper documentLineMapper,
                                      StockBalanceMapper balanceMapper,
                                      SysUserRefMapper sysUserRefMapper,
                                      StockDocumentService stockDocumentService) {
        this.requestMapper = requestMapper;
        this.requestLineMapper = requestLineMapper;
        this.quotaMapper = quotaMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.balanceMapper = balanceMapper;
        this.sysUserRefMapper = sysUserRefMapper;
        this.stockDocumentService = stockDocumentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MaterialRequestCreateDTO dto) {
        RequestType requestType = dto.requestType() == null || dto.requestType().isBlank()
                ? RequestType.SELF : RequestType.valueOf(dto.requestType());
        return requestType == RequestType.PROXY
                ? createProxy(dto)
                : createSelf(dto);
    }

    /**
     * 本人申领：学生单登记草稿待老师初审（必填审批教师）；老师单创建即老师已审核，
     * 直接交管理员终审（与设备申请口径一致，2026-10-24 起）。
     * 经办人强制为当前用户；归属老师学生取审批教师、老师取本人，入参不再直接信任。
     */
    private String createSelf(MaterialRequestCreateDTO dto) {
        Long currentUserId = SecurityUtils.currentUserId();
        SysUserRefMapper.UserTypeRef current = sysUserRefMapper.selectUserTypeById(currentUserId);
        if (current == null || current.enabled() == null || current.enabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "当前账号不存在或已停用");
        }
        boolean isStudent = "STUDENT".equals(current.userType());
        boolean isTeacher = "TEACHER".equals(current.userType());
        if (!isStudent && !isTeacher) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅学生/教师可提交物料申领");
        }
        Long ownerTeacherId;
        if (isStudent) {
            ownerTeacherId = IdUtils.optionalId(dto.ownerTeacherId(), "审批教师 ID");
            if (ownerTeacherId == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "学生申领必须指定审批教师");
            }
            SysUserRefMapper.UserTypeRef teacher = sysUserRefMapper.selectUserTypeById(ownerTeacherId);
            if (teacher == null || teacher.enabled() == null || teacher.enabled() != 1
                    || !"TEACHER".equals(teacher.userType())) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "审批教师不存在、已停用或不是教师");
            }
        } else {
            ownerTeacherId = currentUserId;
        }

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
        // 能申领的都是库存中有的：申领行 SKU 全库位可用合计须 > 0（不预占库存，实发时仍重新校验缺货）
        Map<Long, BigDecimal> stockMap = balanceMapper.sumStockBySkuIds(skuIds).stream()
                .collect(Collectors.toMap(StockBalanceMapper.SkuStockTotal::skuId,
                        StockBalanceMapper.SkuStockTotal::totalQty));
        for (MaterialRequestLineDTO line : dto.lines()) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            MaterialSku sku = skuMap.get(skuId);
            BigDecimal available = stockMap.getOrDefault(skuId, BigDecimal.ZERO);
            if (available.signum() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "物料当前无库存，不可申领：" + (sku == null ? line.skuId()
                                : sku.getMaterialName() + "（" + sku.getSkuCode() + "）"));
            }
        }
        // 启用定额一次加载（批量查询替代逐行 selectByOfferingAndSku）
        Map<Long, MaterialQuota> quotaMap = offeringId == null ? Map.of()
                : quotaMapper.selectByOfferingAndSkuIds(offeringId, skuIds).stream()
                        .collect(Collectors.toMap(MaterialQuota::getSkuId, quota -> quota));

        MaterialRequest request = new MaterialRequest();
        request.setDocumentId(IdWorker.getId());
        request.setLabId(IdUtils.optionalId(dto.labId(), "实验室 ID"));
        request.setOwnerTeacherId(ownerTeacherId);
        request.setOperatorId(currentUserId);
        request.setGrantId(null);
        request.setOfferingId(offeringId);
        request.setLabBookingId(labBookingId);
        request.setScheduleOccurrenceId(scheduleOccurrenceId);
        request.setParticipantCount(dto.participantCount());
        request.setPeriodCount(dto.periodCount());
        request.setPurpose(dto.purpose());
        // 领取方式：仅老师本人申领单可选「学生代理领取」；学生单与代领单强制本人领取
        request.setPickupMode(!isStudent && "PROXY_PICKUP".equals(dto.pickupMode())
                ? PickupMode.PROXY_PICKUP : PickupMode.SELF_PICKUP);
        request.setRequestType(RequestType.SELF);
        // 学生单落草稿待老师初审；老师单跳过同行初审直接交管理员终审
        request.setStatus(isStudent ? MaterialRequestStatus.DRAFT : MaterialRequestStatus.TEACHER_APPROVED);
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
            // 老师单跳过同行初审直落 TEACHER_APPROVED：批准量视同老师已审，按申请量写入；
            // 学生草稿单批准量留空，待老师初审写入
            if (!isStudent) {
                line.setApprovedQty(lineDto.requestedQty());
            }
            line.setFulfilledQty(BigDecimal.ZERO);
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            requestLineMapper.insertBatch(lines);
        }
        return String.valueOf(request.getId());
    }

    /**
     * 学生代理代领：不能新建空白申领，必须引用老师已审核通过的源申领单
     * （request_type=SELF、status=TEACHER_APPROVED），按源行剩余可代领量领取；
     * 创建即 TEACHER_APPROVED（源单已过老师审核，无需老师再审核），
     * 责任老师强制绑定源单归属老师，代领人 = operator_id，直接交由管理员终审。
     */
    private String createProxy(MaterialRequestCreateDTO dto) {
        if (dto.sourceRequestId() == null || dto.sourceRequestId().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "学生代理代领必须指定老师已审核通过的源申领单，不能新建空白申领");
        }
        MaterialRequest source = requireRequest(dto.sourceRequestId());
        if (source.getRequestType() != RequestType.SELF
                || source.getStatus() != MaterialRequestStatus.TEACHER_APPROVED
                || source.getPickupMode() != PickupMode.PROXY_PICKUP) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "源申领单不是老师已审核通过且标记「学生代理领取」的申领单，不可代领");
        }
        // 代领人强制为当前登录学生；须持有源单归属老师的有效助理授权（2026-10-24 收紧：
        // 原信任入参 operatorId/grantId 可冒填他人，现服务端强制并命中授权留档 grant_id）
        Long proxyOperatorId = SecurityUtils.currentUserId();
        SysUserRefMapper.UserTypeRef operator = sysUserRefMapper.selectUserTypeById(proxyOperatorId);
        if (operator == null || operator.enabled() == null || operator.enabled() != 1
                || !"STUDENT".equals(operator.userType())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅学生可提交代理代领");
        }
        Long grantId = sysUserRefMapper.findValidGrantId(proxyOperatorId, source.getOwnerTeacherId());
        if (grantId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),
                    "未持有该老师（" + source.getOwnerTeacherId() + "）的有效助理授权，不能代领");
        }
        Long sourceRequestId = source.getId();

        Map<Long, MaterialRequestLine> sourceLineMap = requestLineMapper
                .selectByRequestId(sourceRequestId).stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        // 源行剩余可代领量 = 批准量 − 实发量 − 已被有效代领单占用申请量
        Map<Long, BigDecimal> occupiedMap = occupiedQtyOf(sourceLineMap.keySet());

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

        MaterialRequest request = new MaterialRequest();
        request.setDocumentId(IdWorker.getId());
        request.setLabId(source.getLabId());
        // 责任老师 = 源单归属老师（第一责任人留档，入参忽略）
        request.setOwnerTeacherId(source.getOwnerTeacherId());
        request.setOperatorId(proxyOperatorId);
        request.setGrantId(grantId);
        request.setOfferingId(source.getOfferingId());
        request.setLabBookingId(source.getLabBookingId());
        request.setScheduleOccurrenceId(source.getScheduleOccurrenceId());
        request.setParticipantCount(source.getParticipantCount());
        request.setPeriodCount(source.getPeriodCount());
        request.setPurpose(dto.purpose());
        request.setRequestType(RequestType.PROXY);
        request.setSourceRequestId(sourceRequestId);
        request.setStatus(MaterialRequestStatus.TEACHER_APPROVED);
        requestMapper.insert(request);

        List<MaterialRequestLine> lines = new ArrayList<>(dto.lines().size());
        for (MaterialRequestLineDTO lineDto : dto.lines()) {
            Long sourceLineId = IdUtils.requireId(lineDto.sourceLineId(), "源申领行 ID");
            MaterialRequestLine sourceLine = sourceLineMap.get(sourceLineId);
            if (sourceLine == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "源申领行不存在或不属于源申领单：" + lineDto.sourceLineId());
            }
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            if (!sourceLine.getSkuId().equals(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "代领物料与源申领行物料不一致：" + lineDto.skuId());
            }
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.requestedQty(), sku);

            BigDecimal available = sourceLine.getApprovedQty().subtract(sourceLine.getFulfilledQty())
                    .subtract(occupiedMap.getOrDefault(sourceLineId, BigDecimal.ZERO))
                    .max(BigDecimal.ZERO);
            if (lineDto.requestedQty().compareTo(available) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "代领量超过源申领行剩余可代领量：剩余 "
                                + available.stripTrailingZeros().toPlainString()
                                + "，本次 " + lineDto.requestedQty().stripTrailingZeros().toPlainString());
            }

            MaterialRequestLine line = new MaterialRequestLine();
            line.setId(IdWorker.getId());
            line.setRequestId(request.getId());
            line.setSkuId(skuId);
            line.setRequestedQty(lineDto.requestedQty());
            // 定额快照与签批/双签标志继承源行（源行已经老师审核）
            line.setQuotaQtySnapshot(sourceLine.getQuotaQtySnapshot());
            line.setRequiresExtraApproval(sourceLine.getRequiresExtraApproval());
            line.setRequiresDualSign(sourceLine.getRequiresDualSign());
            line.setSourceLineId(sourceLineId);
            // 批准量由管理员终审写入；占用口径按申请量保守计算
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
        if (query.getRequestType() != null) {
            wrapper.eq(MaterialRequest::getRequestType, query.getRequestType());
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
        if (query.getOperatorId() != null && !query.getOperatorId().isBlank()) {
            wrapper.eq(MaterialRequest::getOperatorId,
                    IdUtils.requireId(query.getOperatorId(), "经办人 ID"));
        }
        // 数据范围：终审权持有者（管理员）见全量；其余仅见本人经办或归属本人的单
        if (!SecurityUtils.hasPermission(PERM_REQUEST_APPROVE)) {
            Long me = SecurityUtils.currentUserId();
            wrapper.and(w -> w.eq(MaterialRequest::getOperatorId, me)
                    .or().eq(MaterialRequest::getOwnerTeacherId, me));
        }
        wrapper.orderByDesc(MaterialRequest::getId);
        IPage<MaterialRequest> page = requestMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> names = resolveNames(page.getRecords());
        List<MaterialRequestVO> records = page.getRecords().stream()
                .map(e -> MaterialRequestVO.from(e,
                        names.get(e.getOwnerTeacherId()),
                        names.get(e.getOperatorId())))
                .toList();
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
        Map<Long, String> names = resolveNames(List.of(request));
        return MaterialRequestDetailVO.from(request, lineVos,
                names.get(request.getOwnerTeacherId()),
                names.get(request.getOperatorId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void teacherApprove(String id, MaterialRequestApproveDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (request.getStatus() != MaterialRequestStatus.DRAFT) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不处于草稿状态，无法老师审核（学生代理代领单无需老师审核）");
        }
        requireOwnerOrAdmin(request, "只能审核归属本人的申领单");
        writeApprovedQuantities(request, dto, true);
        if (requestMapper.casTeacherApprove(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单状态已变更，老师审核失败");
        }
    }

    @Override
    public void teacherReject(String id) {
        MaterialRequest request = requireRequest(id);
        requireOwnerOrAdmin(request, "只能审核归属本人的申领单");
        if (requestMapper.casTeacherReject(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不存在或不处于草稿状态，无法老师审核拒绝");
        }
    }

    /** 老师初审归属校验：终审权持有者（管理员）可审任意单，老师只能审归属本人的单 */
    private void requireOwnerOrAdmin(MaterialRequest request, String message) {
        if (!SecurityUtils.hasPermission(PERM_REQUEST_APPROVE)
                && !Objects.equals(request.getOwnerTeacherId(), SecurityUtils.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), message);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String id, MaterialRequestApproveDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (request.getStatus() != MaterialRequestStatus.TEACHER_APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单未通过老师审核或已完成终审，无法管理员终审");
        }
        writeApprovedQuantities(request, dto, false);
        if (requestMapper.casApprove(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单状态已变更，终审失败");
        }
    }

    @Override
    public void reject(String id) {
        MaterialRequest request = requireRequest(id);
        if (requestMapper.casReject(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不存在或未通过老师审核，无法管理员终审拒绝");
        }
    }

    @Override
    public PageResult<ProxySourceVO> proxySources(MaterialRequestQuery query) {
        LambdaQueryWrapper<MaterialRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialRequest::getRequestType, RequestType.SELF);
        wrapper.eq(MaterialRequest::getStatus, MaterialRequestStatus.TEACHER_APPROVED);
        // 仅老师标记「学生代理领取」的申领单可被代领（2026-10-24 起）
        wrapper.eq(MaterialRequest::getPickupMode, PickupMode.PROXY_PICKUP);
        if (query.getOwnerTeacherId() != null && !query.getOwnerTeacherId().isBlank()) {
            wrapper.eq(MaterialRequest::getOwnerTeacherId,
                    IdUtils.requireId(query.getOwnerTeacherId(), "归属老师 ID"));
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            wrapper.eq(MaterialRequest::getLabId, IdUtils.requireId(query.getLabId(), "实验室 ID"));
        }
        wrapper.orderByDesc(MaterialRequest::getId);
        IPage<MaterialRequest> page = requestMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> names = resolveNames(page.getRecords());
        List<ProxySourceVO> records = page.getRecords().stream()
                .map(e -> ProxySourceVO.from(e, names.get(e.getOwnerTeacherId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<ProxyLineVO> proxyLines(String sourceRequestId) {
        MaterialRequest source = requireRequest(sourceRequestId);
        if (source.getRequestType() != RequestType.SELF
                || source.getStatus() != MaterialRequestStatus.TEACHER_APPROVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "源申领单不是老师已审核通过的本人申领单，不可代领");
        }
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(source.getId());
        Map<Long, BigDecimal> occupiedMap = occupiedQtyOf(lines.stream()
                .map(MaterialRequestLine::getId).collect(Collectors.toSet()));
        Set<Long> skuIds = lines.stream().map(MaterialRequestLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        return lines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    return ProxyLineVO.from(line,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit(),
                            occupiedMap.get(line.getId()));
                })
                .toList();
    }

    @Override
    public List<TeacherOptionVO> teacherOptions() {
        return sysUserRefMapper.selectTeacherOptions().stream()
                .map(t -> new TeacherOptionVO(String.valueOf(t.userId()), t.realName()))
                .toList();
    }

    @Override
    public List<ReturnableLineVO> returnableLines(String id) {
        MaterialRequest request = requireRequest(id);
        requireOperatorOrAdmin(request, "只能查看本人申领单的归还信息");
        List<StockDocumentLine> issueLines = documentLineMapper.selectPostedIssueLinesByRequestId(request.getId());
        if (issueLines.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> returnedMap = returnedQtyMap(issueLines);
        Set<Long> skuIds = issueLines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        return issueLines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    BigDecimal returned = returnedMap.getOrDefault(line.getId(), BigDecimal.ZERO);
                    return new ReturnableLineVO(
                            String.valueOf(line.getId()),
                            String.valueOf(line.getSkuId()),
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit(),
                            line.getQuantity(),
                            returned,
                            line.getQuantity().subtract(returned).max(BigDecimal.ZERO),
                            line.getFromLocationId() == null ? null : String.valueOf(line.getFromLocationId()));
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String returnRequest(String id, MaterialRequestReturnDTO dto) {
        MaterialRequest request = requireRequest(id);
        requireOperatorOrAdmin(request, "只能归还本人申领单");
        List<StockDocumentLine> issueLines = documentLineMapper.selectPostedIssueLinesByRequestId(request.getId());
        if (issueLines.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "该申领单无已过账的出库记录，无法归还（出库单过账后才能归还）");
        }
        Map<Long, StockDocumentLine> issueLineMap = issueLines.stream()
                .collect(Collectors.toMap(StockDocumentLine::getId, line -> line));
        Map<Long, BigDecimal> returnedMap = returnedQtyMap(issueLines);

        // 逐行校验：原出库行属于本申领单、归还量不超过可归还量
        List<StockReturnLineDTO> returnLines = new ArrayList<>(dto.lines().size());
        for (MaterialRequestReturnDTO.ReturnLineDTO lineDto : dto.lines()) {
            Long issueLineId = IdUtils.requireId(lineDto.issueLineId(), "原出库行 ID");
            StockDocumentLine issueLine = issueLineMap.get(issueLineId);
            if (issueLine == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "原出库行不存在或不属于本申领单：" + lineDto.issueLineId());
            }
            BigDecimal returnable = issueLine.getQuantity()
                    .subtract(returnedMap.getOrDefault(issueLineId, BigDecimal.ZERO)).max(BigDecimal.ZERO);
            if (lineDto.quantity().compareTo(returnable) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "归还量超过可归还量：可归还 " + returnable.stripTrailingZeros().toPlainString()
                                + "，本次 " + lineDto.quantity().stripTrailingZeros().toPlainString());
            }
            returnLines.add(new StockReturnLineDTO(lineDto.issueLineId(), lineDto.toLocationId(),
                    lineDto.quantity()));
        }

        // 复用库存单据归还引擎生成 RETURN_IN 草稿（审批/过账走既有流程，过账回增库存并生成结算）
        String documentId = stockDocumentService.createReturn(new StockReturnCreateDTO(
                dto.occurredAt() == null ? java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                        : dto.occurredAt(),
                String.valueOf(request.getOwnerTeacherId()),
                dto.reason(),
                returnLines));
        // 归还单关联申领单留档（出库/归还记录均可在库存单据按申领单追溯）
        StockDocument document = documentMapper.selectById(IdUtils.requireId(documentId, "归还单 ID"));
        document.setMaterialRequestId(request.getId());
        document.setOperatorId(request.getOperatorId());
        documentMapper.updateById(document);
        return documentId;
    }

    /** 申领单已过账出库行的累计已归还量（按原出库行汇总） */
    private Map<Long, BigDecimal> returnedQtyMap(List<StockDocumentLine> issueLines) {
        List<Long> issueLineIds = issueLines.stream().map(StockDocumentLine::getId).toList();
        if (issueLineIds.isEmpty()) {
            return Map.of();
        }
        return documentLineMapper.sumPostedReturnedByIssueLineIds(issueLineIds).stream()
                .collect(Collectors.toMap(StockDocumentLineMapper.IssueLineReturned::issueLineId,
                        StockDocumentLineMapper.IssueLineReturned::returnedQty));
    }

    /** 申领单经办归属校验：终审权持有者（管理员）可操作任意单，其余仅限本人经办 */
    private void requireOperatorOrAdmin(MaterialRequest request, String message) {
        if (!SecurityUtils.hasPermission(PERM_REQUEST_APPROVE)
                && !Objects.equals(request.getOperatorId(), SecurityUtils.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), message);
        }
    }

    @Override
    public void cancel(String id) {
        MaterialRequest request = requireRequest(id);
        // 非管理员只能取消本人经办的申领单
        if (!SecurityUtils.hasPermission(PERM_REQUEST_APPROVE)
                && !Objects.equals(request.getOperatorId(), SecurityUtils.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能取消本人申领单");
        }
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

    /**
     * 逐行写入批准量。
     *
     * @param defaultToRequested 未指定行批准量的缺省策略：true=老师审核（第一级）按申请量批准；
     *                           false=管理员终审（第二级）保持既有批准量，
     *                           代领行尚未写入批准量时回退按申请量
     */
    private void writeApprovedQuantities(MaterialRequest request, MaterialRequestApproveDTO dto,
            boolean defaultToRequested) {
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Map<Long, MaterialRequestLine> lineMap = lines.stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        Map<Long, BigDecimal> approvedMap = new HashMap<>();
        if (dto != null && dto.lines() != null && !dto.lines().isEmpty()) {
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
        }
        for (MaterialRequestLine line : lines) {
            if (approvedMap.containsKey(line.getId())) {
                continue;
            }
            if (defaultToRequested || line.getApprovedQty() == null) {
                // 老师审核缺省按申请量；终审遇未写入批准量的行（代领行）也按申请量
                approvedMap.put(line.getId(), line.getRequestedQty());
            } else {
                // 管理员终审缺省保持老师审核确定的批准量
                approvedMap.put(line.getId(), line.getApprovedQty());
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : approvedMap.entrySet()) {
            MaterialRequestLine line = lineMap.get(entry.getKey());
            line.setApprovedQty(entry.getValue());
            requestLineMapper.updateById(line);
        }
    }

    /** 源申领行已被有效代领单（未取消、未拒绝的 PROXY 单）占用的申请量，键为源行 ID */
    private Map<Long, BigDecimal> occupiedQtyOf(Set<Long> sourceLineIds) {
        if (sourceLineIds.isEmpty()) {
            return Map.of();
        }
        return requestLineMapper.sumOccupiedQtyBySourceLineIds(List.copyOf(sourceLineIds)).stream()
                .collect(Collectors.toMap(
                        MaterialRequestLineMapper.SourceLineOccupancy::sourceLineId,
                        MaterialRequestLineMapper.SourceLineOccupancy::occupiedQty));
    }

    /** 批量解析责任老师/代领人姓名（跨模块只读引用 sys_user；缺失用户返回空，前端回退显示 ID） */
    private Map<Long, String> resolveNames(Collection<MaterialRequest> requests) {
        Set<Long> userIds = new HashSet<>();
        for (MaterialRequest request : requests) {
            if (request.getOwnerTeacherId() != null) {
                userIds.add(request.getOwnerTeacherId());
            }
            if (request.getOperatorId() != null) {
                userIds.add(request.getOperatorId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserRefMapper.selectNamesByIds(List.copyOf(userIds)).stream()
                .filter(ref -> ref.realName() != null)
                .collect(Collectors.toMap(SysUserRefMapper.UserRef::userId,
                        SysUserRefMapper.UserRef::realName, (a, b) -> a));
    }

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

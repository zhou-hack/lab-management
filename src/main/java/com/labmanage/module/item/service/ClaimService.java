package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.document.enums.ApprovalTaskStatus;
import com.labmanage.module.item.dto.ClaimAvailabilityQuery;
import com.labmanage.module.item.dto.ClaimCreateDTO;
import com.labmanage.module.item.dto.ClaimDelegationDTO;
import com.labmanage.module.item.dto.ClaimPageQuery;
import com.labmanage.module.item.dto.ClaimPreviewAllocationDTO;
import com.labmanage.module.item.dto.ClaimPreviewDTO;
import com.labmanage.module.item.dto.ClaimPreviewLineDTO;
import com.labmanage.module.item.dto.ClaimReasonDTO;
import com.labmanage.module.item.dto.ClaimRejectDTO;
import com.labmanage.module.item.dto.ClaimReviewDTO;
import com.labmanage.module.item.dto.ClaimUpdateDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialClaimAllocation;
import com.labmanage.module.item.entity.MaterialClaimLine;
import com.labmanage.module.item.entity.MaterialNotification;
import com.labmanage.module.item.entity.MaterialReservation;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockLocationAdmin;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.enums.PickupMode;
import com.labmanage.module.item.mapper.MaterialClaimAllocationMapper;
import com.labmanage.module.item.mapper.MaterialClaimLineMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.ClaimPlanService.ApprovalPreview;
import com.labmanage.module.item.service.AssistantGrantService.PickupResolution;
import com.labmanage.module.item.vo.BookingTimeSnapshotVO;
import com.labmanage.module.item.vo.ClaimAvailabilityVO;
import com.labmanage.module.item.vo.ClaimAllocationVO;
import com.labmanage.module.item.vo.ClaimDetailVO;
import com.labmanage.module.item.vo.ClaimDocumentChainVO;
import com.labmanage.module.item.vo.ClaimLineVO;
import com.labmanage.module.item.vo.ClaimPreviewVO;
import com.labmanage.module.item.vo.ClaimReservationVO;
import com.labmanage.module.item.vo.ClaimShortageVO;
import com.labmanage.module.item.vo.ClaimSummaryVO;
import com.labmanage.module.item.vo.ClaimTemplateLineVO;
import com.labmanage.module.item.vo.ClaimTemplateVO;
import com.labmanage.module.item.vo.ItemDocumentEventVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    public static final String DOCUMENT_TYPE = "ITEM_CLAIM";
    public static final String REVIEW_STAGE = "ITEM_CLAIM_REVIEW";

    private static final long DEFAULT_DEPARTMENT_ID = 1L;
    private static final Map<String, SFunction<MaterialClaim, ?>> SORT_COLUMNS = Map.of(
            "createTime", MaterialClaim::getCreateTime,
            "bookingDate", MaterialClaim::getBookingDate,
            "bookingStartAt", MaterialClaim::getBookingStartAt,
            "bookingEndAt", MaterialClaim::getBookingEndAt,
            "status", MaterialClaim::getStatus,
            "revisionNo", MaterialClaim::getRevisionNo
    );

    private final MaterialClaimMapper claimMapper;
    private final MaterialClaimLineMapper lineMapper;
    private final MaterialClaimAllocationMapper allocationMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final FixedAssetMapper assetMapper;
    private final SysUserMapper userMapper;
    private final ClaimPlanService planService;
    private final ReservationService reservationService;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final ItemLocationAccessService locationAccessService;
    private final StockDocumentService stockDocumentService;
    private final AssistantGrantService assistantGrantService;
    private final StockLocationAdminMapper locationAdminMapper;
    private final NotificationService notificationService;

    public ClaimService(MaterialClaimMapper claimMapper,
                        MaterialClaimLineMapper lineMapper,
                        MaterialClaimAllocationMapper allocationMapper,
                        MaterialDocumentLineMapper documentLineMapper,
                        MaterialMapper materialMapper,
                        StockLocationMapper locationMapper,
                        FixedAssetMapper assetMapper,
                        SysUserMapper userMapper,
                        ClaimPlanService planService,
                        ReservationService reservationService,
                        ItemFlowService flowService,
                        ItemTimeService timeService,
                        ItemLocationAccessService locationAccessService,
                        StockDocumentService stockDocumentService,
                        AssistantGrantService assistantGrantService,
                        StockLocationAdminMapper locationAdminMapper,
                        NotificationService notificationService) {
        this.claimMapper = claimMapper;
        this.lineMapper = lineMapper;
        this.allocationMapper = allocationMapper;
        this.documentLineMapper = documentLineMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.assetMapper = assetMapper;
        this.userMapper = userMapper;
        this.planService = planService;
        this.reservationService = reservationService;
        this.flowService = flowService;
        this.timeService = timeService;
        this.locationAccessService = locationAccessService;
        this.stockDocumentService = stockDocumentService;
        this.assistantGrantService = assistantGrantService;
        this.locationAdminMapper = locationAdminMapper;
        this.notificationService = notificationService;
    }

    public PageResult<ClaimSummaryVO> pageMine(ClaimPageQuery query) {
        return page(query, SecurityUtils.currentUserId());
    }

    public PageResult<ClaimAvailabilityVO> availability(ClaimAvailabilityQuery query) {
        Long excludeClaimId = null;
        if (query.getClaimId() != null) {
            excludeClaimId = Ids.parse(query.getClaimId());
            requireOwnedClaim(excludeClaimId, SecurityUtils.currentUserId());
        }
        return planService.availability(query, excludeClaimId);
    }

    public PageResult<ClaimSummaryVO> pageAdmin(ClaimPageQuery query) {
        requireAdministrator();
        Long applicantId = query.applicantId() == null ? null : Ids.parse(query.applicantId());
        if (!Boolean.TRUE.equals(query.managedOnly())) {
            return page(query, applicantId);
        }
        Set<Long> managedLocationIds = new HashSet<>(locationAccessService.currentUserLocationIds());
        if (managedLocationIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.pageNum(), query.pageSize());
        }
        return page(query, applicantId, managedLocationIds);
    }

    public ClaimDetailVO detailMine(Long id) {
        return buildDetail(requireOwnedClaim(id, SecurityUtils.currentUserId()));
    }

    public ClaimDetailVO detailAdmin(Long id) {
        requireAdministrator();
        return buildDetail(requireClaim(id));
    }

    public ClaimDocumentChainVO documents(Long id) {
        MaterialClaim claim = requireClaim(id);
        Long currentUserId = SecurityUtils.currentUserId();
        if (!claim.getApplicantId().equals(currentUserId)
                && !SecurityUtils.hasPermission("item:claim:review")) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return new ClaimDocumentChainVO(str(id), str(id), stockDocumentService.listClaimDocuments(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO create(ClaimCreateDTO dto) {
        Long applicantId = SecurityUtils.currentUserId();
        String idempotencyKey = normalizeIdempotencyKey(dto.idempotencyKey());
        ClaimDetailVO existing = findIdempotentClaim(applicantId, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        ClaimPreviewVO preview = planService.preview(toPreviewDTO(
                dto.purpose(), dto.bookingDate(), dto.startPeriodId(), dto.endPeriodId(),
                dto.lines(), dto.allocations()));
        requireNoShortages(preview);

        Instant now = timeService.now();
        Long claimId = IdWorker.getId();
        SysUser applicant = userMapper.selectById(applicantId);
        BizDocument shared = new BizDocument();
        shared.setId(IdWorker.getId());
        shared.setDocumentNo("ITC" + claimId);
        shared.setDocumentType(DOCUMENT_TYPE);
        shared.setOwnerUserId(applicantId);
        shared.setDepartmentId(applicant == null || applicant.getDepartmentId() == null
                ? DEFAULT_DEPARTMENT_ID : applicant.getDepartmentId());
        shared.setSubmittedAt(now);
        shared.setIdempotencyKey(idempotencyKey);
        BizDocument persistedShared = flowService.createDocumentByOwnerIdempotency(shared);

        MaterialClaim concurrent = claimMapper.selectByBizDocumentIdForUpdate(persistedShared.getId());
        if (concurrent != null) {
            requireOwner(concurrent, applicantId);
            return buildDetail(concurrent);
        }
        if (!persistedShared.getDocumentNo().equals(shared.getDocumentNo())) {
            throw conflict("幂等键对应共享单据异常，请更换幂等键");
        }

        MaterialClaim claim = new MaterialClaim();
        claim.setId(claimId);
        claim.setBizDocumentId(persistedShared.getId());
        claim.setApplicantId(applicantId);
        claim.setPurpose(dto.purpose().trim());
        applySnapshot(claim, preview.bookingSnapshot());
        claim.setStatus(ClaimStatus.PENDING);
        claim.setPickupMode(PickupMode.SELF);
        claim.setRevisionNo(1);
        claimMapper.insert(claim);
        insertVersionRows(claim, preview);

        ApprovalTask task = createReviewTask(claim, 1, now);
        appendEvent(claim, null, ClaimStatus.PENDING, "SUBMIT", task.getId(), null, now);
        return buildDetail(claim);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO update(Long id, ClaimUpdateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireOwnedClaimForUpdate(id, operatorId);
        requireRevision(claim, dto.revisionNo());
        if (hasOutbound(id)) {
            throw conflict("已产生出库行，禁止修改申领");
        }
        ClaimStatus previousStatus = claim.getStatus();
        if (previousStatus != ClaimStatus.PENDING && previousStatus != ClaimStatus.APPROVED) {
            throw conflict("当前状态不可修改申领");
        }
        Instant now = timeService.now();
        if (now.isAfter(claim.getBookingEndAt())) {
            throw conflict("预约已过期，禁止修改申领");
        }

        ClaimPreviewVO preview = planService.preview(toPreviewDTO(
                dto.purpose(), dto.bookingDate(), dto.startPeriodId(), dto.endPeriodId(),
                dto.lines(), dto.allocations()), id);
        requireNoShortages(preview);
        if (timeService.now().isAfter(claim.getBookingEndAt())) {
            throw conflict("预约已过期，禁止修改申领");
        }

        reservationService.releaseForClaim(id, operatorId);
        flowService.cancelOpenTasks(claim.getBizDocumentId(), operatorId, "修改后重新审核");
        int newRevision = claim.getRevisionNo() + 1;
        BookingTimeSnapshotVO snapshot = preview.bookingSnapshot();
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getRevisionNo, dto.revisionNo())
                .in(MaterialClaim::getStatus, ClaimStatus.PENDING, ClaimStatus.APPROVED)
                .set(MaterialClaim::getPurpose, dto.purpose().trim())
                .set(MaterialClaim::getBookingDate, snapshot.bookingDate())
                .set(MaterialClaim::getStartPeriodId, Ids.parse(snapshot.startPeriodId()))
                .set(MaterialClaim::getEndPeriodId, Ids.parse(snapshot.endPeriodId()))
                .set(MaterialClaim::getBookingStartAt, snapshot.bookingStartAt())
                .set(MaterialClaim::getBookingEndAt, snapshot.bookingEndAt())
                .set(MaterialClaim::getReturnDeadline, snapshot.returnDeadline())
                .set(MaterialClaim::getStatus, ClaimStatus.PENDING)
                .set(MaterialClaim::getRevisionNo, newRevision)
                .set(MaterialClaim::getApprovedBy, null)
                .set(MaterialClaim::getApprovedAt, null)
                .set(MaterialClaim::getRejectReason, null)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领已变化，请刷新后重试");
        }

        MaterialClaim updated = requireClaim(id);
        insertVersionRows(updated, preview);
        ApprovalTask task = createReviewTask(updated, newRevision, now);
        appendEvent(updated, previousStatus, ClaimStatus.PENDING, "MODIFY", task.getId(), null, now);
        return buildDetail(updated);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ClaimDetailVO delegate(Long id, ClaimDelegationDTO dto) {
        Long teacherId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireOwnedClaimForUpdate(id, teacherId);
        if (claim.getStatus() != ClaimStatus.PENDING && claim.getStatus() != ClaimStatus.APPROVED) {
            throw conflict("仅待审核或已审核申领可以委派代领");
        }
        if (hasOutbound(id)) {
            throw conflict("已产生出库行，禁止变更代领委派");
        }
        Instant now = timeService.now();
        if (now.isAfter(claim.getBookingEndAt())) {
            throw conflict("预约已过期，禁止变更代领委派");
        }

        PickupMode previousMode = claim.getPickupMode() == null ? PickupMode.SELF : claim.getPickupMode();
        Long previousStudentId = claim.getDelegateStudentId();
        String requestedStudentId = dto == null ? null : trimToNull(dto.studentId());
        if (requestedStudentId == null) {
            if (previousMode == PickupMode.SELF) {
                return buildDetail(claim);
            }
            int updated = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                    .eq(MaterialClaim::getId, id)
                    .set(MaterialClaim::getPickupMode, PickupMode.SELF)
                    .set(MaterialClaim::getDelegateStudentId, null)
                    .set(MaterialClaim::getDelegateGrantId, null)
                    .set(MaterialClaim::getDelegatedAt, null)
                    .set(MaterialClaim::getUpdateTime, now)
                    .set(MaterialClaim::getUpdateUserId, teacherId));
            if (updated != 1) {
                throw conflict("申领已变化，请刷新后重试");
            }
            appendDelegationEvent(claim, "UNDELEGATE", str(previousStudentId), now);
            return buildDetail(requireClaim(id));
        }

        Long studentId = Ids.parse(requestedStudentId);
        if (previousMode == PickupMode.ASSISTANT && studentId.equals(previousStudentId)) {
            return buildDetail(claim);
        }
        SysUser student = userMapper.selectById(studentId);
        if (student == null || !"STUDENT".equals(student.getUserType())) {
            throw badRequest("代领人必须是学生账号");
        }
        if (!Integer.valueOf(1).equals(student.getEnabled())) {
            throw badRequest("代领学生账号必须处于启用状态");
        }
        var grant = assistantGrantService.findEffectiveGrantForUpdate(teacherId, studentId, now);
        if (grant == null) {
            throw conflict("当前教师对该学生没有有效助理授权");
        }
        int updated = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .set(MaterialClaim::getPickupMode, PickupMode.ASSISTANT)
                .set(MaterialClaim::getDelegateStudentId, studentId)
                .set(MaterialClaim::getDelegateGrantId, grant.getId())
                .set(MaterialClaim::getDelegatedAt, now)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, teacherId));
        if (updated != 1) {
            throw conflict("申领已变化，请刷新后重试");
        }
        DocumentEvent delegationEvent = appendDelegationEvent(claim,
                previousMode == PickupMode.ASSISTANT ? "REDELEGATE" : "DELEGATE", str(studentId), now);
        notifyStudentDelegated(claim, studentId, delegationEvent.getId(), now);
        return buildDetail(requireClaim(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO cancel(Long id, ClaimReasonDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireOwnedClaimForUpdate(id, operatorId);
        if (hasOutbound(id)) {
            throw conflict("已产生出库行，不能取消整张申领");
        }
        if (!isCancellableStatus(claim.getStatus())) {
            throw conflict("当前状态不可取消申领");
        }
        String reason = trimToNull(dto == null ? null : dto.reason());
        Instant now = timeService.now();
        reservationService.releaseForClaim(id, operatorId);
        flowService.cancelOpenTasks(claim.getBizDocumentId(), operatorId, reason);
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getRevisionNo, claim.getRevisionNo())
                .in(MaterialClaim::getStatus, ClaimStatus.PENDING, ClaimStatus.APPROVED, ClaimStatus.EXPIRED)
                .set(MaterialClaim::getStatus, ClaimStatus.CANCELLED)
                .set(MaterialClaim::getCancelledBy, operatorId)
                .set(MaterialClaim::getCancelledAt, now)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领状态已变化，请刷新后重试");
        }
        appendEvent(claim, claim.getStatus(), ClaimStatus.CANCELLED, "CANCEL", null, reason, now);
        return buildDetail(requireClaim(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO abandon(Long id, ClaimReasonDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireOwnedClaimForUpdate(id, operatorId);
        if (claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED) {
            throw conflict("仅部分实发的申领可以申请放弃剩余");
        }
        if (claim.getAbandonRequestedBy() != null) {
            throw conflict("已提交放弃剩余请求，请等待管理员关闭");
        }
        String reason = trimToNull(dto == null ? null : dto.reason());
        Instant now = timeService.now();
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getRevisionNo, claim.getRevisionNo())
                .eq(MaterialClaim::getStatus, ClaimStatus.PARTIALLY_ISSUED)
                .set(MaterialClaim::getAbandonRequestedBy, operatorId)
                .set(MaterialClaim::getAbandonRequestedAt, now)
                .set(MaterialClaim::getAbandonReason, reason)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领状态已变化，请刷新后重试");
        }
        appendEvent(claim, ClaimStatus.PARTIALLY_ISSUED, ClaimStatus.PARTIALLY_ISSUED,
                "ABANDON", null, reason, now);
        return buildDetail(requireClaim(id));
    }

    public ClaimTemplateVO template(Long id) {
        MaterialClaim claim = requireOwnedClaim(id, SecurityUtils.currentUserId());
        List<MaterialClaimLine> lines = currentLines(claim);
        Map<Long, Material> materials = loadMaterials(lines);
        List<ClaimTemplateLineVO> templateLines = lines.stream()
                .map(line -> {
                    Material material = materials.get(line.getMaterialId());
                    return new ClaimTemplateLineVO(str(line.getMaterialId()),
                            material == null ? null : material.getMaterialName(),
                            material == null ? null : material.getSpecification(),
                            material == null ? null : material.getMaterialType(),
                            material == null ? null : material.getQuantityMode(),
                            material == null ? null : material.getBaseUnit(),
                            line.getRequestedQuantity());
                })
                .toList();
        return new ClaimTemplateVO(claim.getPurpose(), templateLines);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ClaimPreviewVO check(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireClaimForUpdate(id);
        ReviewContext context = prepareReview(claim, operatorId, null);
        return planService.previewForApproval(context.previewDTO()).preview();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ClaimDetailVO approve(Long id, ClaimReviewDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireClaimForUpdate(id);
        ReviewContext context = prepareReview(claim, operatorId, dto.revisionNo());
        ApprovalPreview approvalPreview = planService.previewForApproval(context.previewDTO());
        requireNoShortages(approvalPreview.preview());
        Instant now = timeService.now();
        if (now.isAfter(claim.getBookingEndAt())) {
            throw conflict("预约已过期，不能审核通过");
        }
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getStatus, ClaimStatus.PENDING)
                .eq(MaterialClaim::getRevisionNo, dto.revisionNo())
                .set(MaterialClaim::getStatus, ClaimStatus.APPROVED)
                .set(MaterialClaim::getApprovedBy, operatorId)
                .set(MaterialClaim::getApprovedAt, now)
                .set(MaterialClaim::getRejectReason, null)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领状态或版本已变化，请刷新后重试");
        }
        reservationService.createForClaim(claim, context.lines(), context.allocations(), approvalPreview);
        ApprovalTask task = requireCurrentTask(claim.getBizDocumentId(), claim.getRevisionNo());
        flowService.completeTask(task.getId(), ApprovalTaskStatus.APPROVED, operatorId, "审核通过");
        appendEvent(claim, ClaimStatus.PENDING, ClaimStatus.APPROVED, "APPROVE",
                task.getId(), null, now);
        notifyApplicantApproved(claim, now);
        return buildDetail(requireClaim(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO reject(Long id, ClaimRejectDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireClaimForUpdate(id);
        prepareReview(claim, operatorId, dto.revisionNo());
        String reason = trimToNull(dto.reason());
        if (reason == null) {
            throw badRequest("驳回原因不能为空");
        }
        Instant now = timeService.now();
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getStatus, ClaimStatus.PENDING)
                .eq(MaterialClaim::getRevisionNo, dto.revisionNo())
                .set(MaterialClaim::getStatus, ClaimStatus.REJECTED)
                .set(MaterialClaim::getRejectReason, reason)
                .set(MaterialClaim::getApprovedBy, null)
                .set(MaterialClaim::getApprovedAt, null)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领状态或版本已变化，请刷新后重试");
        }
        ApprovalTask task = requireCurrentTask(claim.getBizDocumentId(), claim.getRevisionNo());
        flowService.completeTask(task.getId(), ApprovalTaskStatus.REJECTED, operatorId, reason);
        appendEvent(claim, ClaimStatus.PENDING, ClaimStatus.REJECTED, "REJECT",
                task.getId(), reason, now);
        return buildDetail(requireClaim(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimDetailVO close(Long id) {
        requireAdministrator();
        Long operatorId = SecurityUtils.currentUserId();
        MaterialClaim claim = requireClaimForUpdate(id);
        List<MaterialClaimAllocation> allocations = currentAllocations(claim);
        requireLocationIntersection(operatorId, allocations);
        if (claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED) {
            throw conflict("仅部分实发的申领可以关闭");
        }
        if (claim.getAbandonRequestedBy() == null) {
            throw conflict("申请人尚未提交放弃剩余请求");
        }
        Instant now = timeService.now();
        reservationService.releaseRemaining(id, claim.getRevisionNo(), operatorId);
        int rows = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, id)
                .eq(MaterialClaim::getRevisionNo, claim.getRevisionNo())
                .eq(MaterialClaim::getStatus, ClaimStatus.PARTIALLY_ISSUED)
                .set(MaterialClaim::getStatus, ClaimStatus.CLOSED)
                .set(MaterialClaim::getCloseReason, "ABANDONED")
                .set(MaterialClaim::getClosedBy, operatorId)
                .set(MaterialClaim::getClosedAt, now)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw conflict("申领状态已变化，请刷新后重试");
        }
        appendEvent(claim, ClaimStatus.PARTIALLY_ISSUED, ClaimStatus.CLOSED, "CLOSE",
                null, claim.getAbandonReason(), now);
        return buildDetail(requireClaim(id));
    }

    private PageResult<ClaimSummaryVO> page(ClaimPageQuery query, Long applicantId) {
        return page(query, applicantId, Set.of());
    }

    private PageResult<ClaimSummaryVO> page(ClaimPageQuery query, Long applicantId, Set<Long> managedLocationIds) {
        if (query.bookingDateFrom() != null && query.bookingDateTo() != null
                && query.bookingDateFrom().isAfter(query.bookingDateTo())) {
            throw badRequest("预约开始日期不能晚于结束日期");
        }
        ClaimStatus status = parseStatus(query.status());
        LambdaQueryWrapper<MaterialClaim> wrapper = Wrappers.<MaterialClaim>lambdaQuery()
                .eq(applicantId != null, MaterialClaim::getApplicantId, applicantId)
                .eq(status != null, MaterialClaim::getStatus, status)
                .ge(query.bookingDateFrom() != null, MaterialClaim::getBookingDate, query.bookingDateFrom())
                .le(query.bookingDateTo() != null, MaterialClaim::getBookingDate, query.bookingDateTo());
        if (!managedLocationIds.isEmpty()) {
            String locationIds = managedLocationIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            wrapper.apply("EXISTS (SELECT 1 FROM material_claim_allocation managed_allocation "
                    + "WHERE managed_allocation.claim_id = material_claim.id "
                    + "AND managed_allocation.revision_no = material_claim.revision_no "
                    + "AND managed_allocation.deleted = 0 AND managed_allocation.location_id IN ("
                    + locationIds + "))");
        }
        SFunction<MaterialClaim, ?> sortColumn = query.sortBy() == null ? MaterialClaim::getCreateTime
                : SORT_COLUMNS.getOrDefault(query.sortBy(), MaterialClaim::getCreateTime);
        wrapper.orderBy(true, "asc".equalsIgnoreCase(query.sortOrder()), sortColumn)
                .orderByDesc(MaterialClaim::getId);
        Page<MaterialClaim> page = claimMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        List<MaterialClaim> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
        }

        Set<Long> documentIds = records.stream().map(MaterialClaim::getBizDocumentId).collect(Collectors.toSet());
        Map<Long, BizDocument> documents = flowService.listDocumentsByIds(documentIds).stream()
                .collect(Collectors.toMap(BizDocument::getId, Function.identity()));
        Set<Long> userIds = records.stream().map(MaterialClaim::getApplicantId).collect(Collectors.toSet());
        Map<Long, SysUser> users = loadUsers(userIds);
        List<Long> claimIds = records.stream().map(MaterialClaim::getId).toList();
        Set<Long> issuedClaimIds = new HashSet<>(documentLineMapper.selectOutboundClaimIds(claimIds));
        Instant now = timeService.now();
        List<ClaimSummaryVO> result = records.stream()
                .map(claim -> toSummaryVO(claim, documents.get(claim.getBizDocumentId()),
                        users.get(claim.getApplicantId()), displayStatus(claim, issuedClaimIds.contains(claim.getId()), now)))
                .toList();
        return PageResult.of(result, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private ClaimDetailVO buildDetail(MaterialClaim claim) {
        BizDocument document = flowService.findDocumentById(claim.getBizDocumentId());
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领共享单据不存在");
        }
        List<MaterialClaimLine> lines = currentLines(claim);
        List<MaterialClaimAllocation> allocations = currentAllocations(claim);
        List<MaterialReservation> reservations = reservationService.listCurrent(claim.getId(), claim.getRevisionNo());
        List<DocumentEvent> events = flowService.listEvents(claim.getBizDocumentId());
        Map<Long, Material> materials = loadMaterials(lines);

        Set<Long> locationIds = new HashSet<>();
        allocations.forEach(allocation -> locationIds.add(allocation.getLocationId()));
        reservations.forEach(reservation -> locationIds.add(reservation.getLocationId()));
        Map<Long, StockLocation> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
        Set<Long> assetIds = reservations.stream().map(MaterialReservation::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FixedAsset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));

        Set<Long> userIds = new HashSet<>();
        addUserIds(claim, userIds);
        events.stream().map(DocumentEvent::getActorId).filter(java.util.Objects::nonNull).forEach(userIds::add);
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, MaterialClaimLine> linesById = lines.stream()
                .collect(Collectors.toMap(MaterialClaimLine::getId, Function.identity()));

        List<ClaimLineVO> lineVOs = lines.stream()
                .map(line -> toLineVO(line, materials.get(line.getMaterialId())))
                .toList();
        List<ClaimAllocationVO> allocationVOs = allocations.stream()
                .map(allocation -> {
                    MaterialClaimLine line = linesById.get(allocation.getClaimLineId());
                    StockLocation location = locations.get(allocation.getLocationId());
                    return new ClaimAllocationVO(str(allocation.getId()), str(allocation.getClaimId()),
                            str(allocation.getClaimLineId()), allocation.getRevisionNo(),
                            line == null ? null : str(line.getMaterialId()), str(allocation.getLocationId()),
                            location == null ? null : location.getLocationCode(),
                            location == null ? null : location.getLocationName(), allocation.getPlannedQuantity());
                })
                .toList();
        List<ClaimReservationVO> reservationVOs = reservations.stream()
                .map(reservation -> {
                    StockLocation location = locations.get(reservation.getLocationId());
                    FixedAsset asset = reservation.getFixedAssetId() == null ? null : assets.get(reservation.getFixedAssetId());
                    return new ClaimReservationVO(str(reservation.getId()), str(reservation.getClaimId()),
                            str(reservation.getClaimLineId()), str(reservation.getAllocationId()),
                            reservation.getRevisionNo(), str(reservation.getMaterialId()), str(reservation.getLocationId()),
                            location == null ? null : location.getLocationCode(),
                            location == null ? null : location.getLocationName(), str(reservation.getFixedAssetId()),
                            asset == null ? null : asset.getAssetCode(), reservation.getReservationRule(),
                            reservation.getAssetTotalSnapshot(), reservation.getReservedQuantity(),
                            reservation.getConsumedQuantity(), reservation.getReleasedQuantity(),
                            reservationService.remaining(reservation), reservation.getReservationStartAt(),
                            reservation.getReservationEndAt(), reservation.getStatus(), reservation.getClosedAt());
                })
                .toList();
        List<ItemDocumentEventVO> eventVOs = events.stream()
                .map(event -> toEventVO(event, users))
                .toList();

        boolean issued = hasOutbound(claim.getId());
        Instant now = timeService.now();
        Long currentUserId = SecurityUtils.currentUserId();
        Set<Long> userLocationIds = new HashSet<>(locationAccessService.userLocationIds(currentUserId));
        PickupResolution pickup = assistantGrantService.resolvePickup(claim);
        ActionState actions = actions(claim, lines, allocations, issued, now, currentUserId, userLocationIds, pickup);
        SysUser applicant = users.get(claim.getApplicantId());
        SysUser delegateStudent = claim.getDelegateStudentId() == null ? null : users.get(claim.getDelegateStudentId());
        return new ClaimDetailVO(str(claim.getId()), str(claim.getBizDocumentId()), str(claim.getId()),
                document.getDocumentNo(), str(claim.getApplicantId()),
                applicant == null ? null : applicant.getRealName(), claim.getPurpose(), claim.getBookingDate(),
                str(claim.getStartPeriodId()), str(claim.getEndPeriodId()), claim.getBookingStartAt(),
                claim.getBookingEndAt(), claim.getReturnDeadline(), timeService.issueEarliestAt(claim.getBookingDate()),
                displayStatus(claim, issued, now), claim.getRevisionNo(), str(claim.getApprovedBy()), claim.getApprovedAt(),
                claim.getRejectReason(), str(claim.getAbandonRequestedBy()), claim.getAbandonRequestedAt(),
                claim.getAbandonReason(), claim.getCloseReason(), str(claim.getClosedBy()), claim.getClosedAt(),
                str(claim.getCancelledBy()), claim.getCancelledAt(), pickup.pickupMode(), str(claim.getDelegateStudentId()),
                delegateStudent == null ? null : delegateStudent.getRealName(),
                delegateStudent == null ? null : delegateStudent.getAccountName(), str(claim.getDelegateGrantId()),
                claim.getDelegatedAt(), pickup.valid(), pickup.blockedReason(), lineVOs, allocationVOs, reservationVOs, eventVOs,
                actions.canReview(), actions.canModify(), actions.canCancel(), actions.canAbandon(), actions.canClose(),
                actions.canRebook(), actions.canIssue(), actions.canDelegate(), actions.actionReasons(),
                claim.getCreateTime(), claim.getUpdateTime());
    }

    private ReviewContext prepareReview(MaterialClaim claim, Long operatorId, Integer expectedRevision) {
        requireAdministrator();
        if (expectedRevision != null) {
            requireRevision(claim, expectedRevision);
        }
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw conflict("仅待审核申领可以执行审核操作");
        }
        if (timeService.now().isAfter(claim.getBookingEndAt())) {
            throw conflict("预约已过期，不能执行审核操作");
        }
        if (hasOutbound(claim.getId())) {
            throw conflict("已产生出库行的申领不能审核");
        }
        List<MaterialClaimLine> lines = currentLines(claim);
        List<MaterialClaimAllocation> allocations = currentAllocations(claim);
        if (lines.isEmpty() || allocations.isEmpty()) {
            throw conflict("申领当前版本缺少物料行或计划库位");
        }
        requireLocationIntersection(operatorId, allocations);
        return new ReviewContext(lines, allocations, toPreviewDTO(claim, lines, allocations));
    }

    private void requireAdministrator() {
        if (!SecurityUtils.hasPermission("item:claim:review")) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅管理员可执行该申领管理操作");
        }
    }

    private void requireLocationIntersection(Long operatorId, List<MaterialClaimAllocation> allocations) {
        Set<Long> managed = new HashSet<>(locationAccessService.userLocationIds(operatorId));
        boolean intersects = allocations.stream().anyMatch(allocation -> managed.contains(allocation.getLocationId()));
        if (!intersects) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅当前版本计划库位管理员可执行该操作");
        }
    }

    private ApprovalTask requireCurrentTask(Long documentId, Integer revisionNo) {
        ApprovalTask task = flowService.findLatestTask(documentId, REVIEW_STAGE, 1);
        if (task == null || !revisionNo.equals(task.getRoundNo())) {
            throw conflict("当前版本审核任务不存在或已变化");
        }
        return task;
    }

    private ApprovalTask createReviewTask(MaterialClaim claim, Integer roundNo, Instant now) {
        ApprovalTask task = new ApprovalTask();
        task.setId(IdWorker.getId());
        task.setDocumentId(claim.getBizDocumentId());
        task.setStageCode(REVIEW_STAGE);
        task.setSequenceNo(1);
        task.setRoundNo(roundNo);
        task.setRequiredRole("ADMIN");
        task.setStatus(ApprovalTaskStatus.READY);
        task.setAvailableAt(now);
        task.setTimeoutAction("NONE");
        ApprovalTask created = flowService.createTask(task);
        notifyReviewPending(claim, now);
        return created;
    }

    private void appendEvent(MaterialClaim claim, ClaimStatus fromStatus, ClaimStatus toStatus,
                             String action, Long taskId, String reason, Instant now) {
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(claim.getBizDocumentId());
        event.setEventType(action);
        event.setFromStatus(fromStatus == null ? null : fromStatus.name());
        event.setToStatus(toStatus == null ? null : toStatus.name());
        event.setActorId(SecurityUtils.currentUserId());
        event.setActorType("USER");
        event.setOccurredAt(now);
        event.setReason(reason);
        event.setTaskId(taskId);
        event.setRequestKey("ITEM_CLAIM:" + claim.getId() + ":" + claim.getRevisionNo() + ":" + action);
        flowService.appendEvent(event);
    }

    private DocumentEvent appendDelegationEvent(MaterialClaim claim, String action, String studentId, Instant now) {
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(claim.getBizDocumentId());
        event.setEventType(action);
        event.setFromStatus(claim.getStatus().name());
        event.setToStatus(claim.getStatus().name());
        event.setActorId(SecurityUtils.currentUserId());
        event.setActorType("USER");
        event.setOccurredAt(now);
        event.setReason(studentId);
        event.setRequestKey("ITEM_CLAIM:" + claim.getId() + ":DELEGATION:" + event.getId());
        flowService.appendEvent(event);
        return event;
    }

    private void notifyReviewPending(MaterialClaim claim, Instant now) {
        Set<Long> locationIds = currentAllocations(claim).stream()
                .map(MaterialClaimAllocation::getLocationId)
                .collect(Collectors.toSet());
        if (locationIds.isEmpty()) {
            return;
        }
        Set<Long> recipientIds = locationAdminMapper.selectList(Wrappers.<StockLocationAdmin>lambdaQuery()
                        .in(StockLocationAdmin::getLocationId, locationIds))
                .stream()
                .map(StockLocationAdmin::getUserId)
                .collect(Collectors.toSet());
        for (Long recipientId : recipientIds) {
            if (notificationService.canReceive(recipientId, "item:claim:review")) {
                createClaimNotification(claim, recipientId, "CLAIM_REVIEW_PENDING", "IMMEDIATE",
                        "REVIEW_PENDING:" + claim.getId() + ":" + claim.getRevisionNo(),
                        "库位申领待审批", "申领单 " + claim.getId() + "（第 " + claim.getRevisionNo()
                                + " 版）涉及您负责的库位，请及时审核。", now);
            }
        }
    }

    private void notifyApplicantApproved(MaterialClaim claim, Instant now) {
        Long recipientId = claim.getApplicantId();
        if (!notificationService.canReceive(recipientId, "item:claim:list")) {
            return;
        }
        createClaimNotification(claim, recipientId, "CLAIM_APPROVED", "IMMEDIATE",
                "CLAIM_APPROVED:" + claim.getId() + ":" + claim.getRevisionNo(),
                "申领已批准", "您的申领单 " + claim.getId() + " 已审核通过，请按预约时间领取物料。", now);
    }

    private void notifyStudentDelegated(MaterialClaim claim, Long studentId, Long eventId, Instant now) {
        if (!notificationService.canReceive(studentId, "item:pickup:list")) {
            return;
        }
        createClaimNotification(claim, studentId, "PICKUP_DELEGATED", "IMMEDIATE",
                "PICKUP_DELEGATED:" + claim.getId() + ":" + eventId,
                "新的代领任务", "申领单 " + claim.getId() + " 已委派给您代领，请查看代领详情。", now);
    }

    private void createClaimNotification(MaterialClaim claim, Long recipientId, String type, String stage,
                                         String dedupeKey, String title, String content, Instant now) {
        MaterialNotification notification = new MaterialNotification();
        notification.setDedupeKey(dedupeKey);
        notification.setRecipientUserId(recipientId);
        notification.setNotificationType(type);
        notification.setNotificationStage(stage);
        notification.setClaimId(claim.getId());
        notification.setBizDocumentId(claim.getBizDocumentId());
        notification.setTitle(title);
        notification.setContent(content);
        notificationService.createSystemNotification(notification, now);
    }

    private void insertVersionRows(MaterialClaim claim, ClaimPreviewVO preview) {
        Map<Long, MaterialClaimLine> linesByMaterial = new HashMap<>();
        preview.lines().forEach(previewLine -> {
            MaterialClaimLine line = new MaterialClaimLine();
            line.setId(IdWorker.getId());
            line.setClaimId(claim.getId());
            line.setRevisionNo(claim.getRevisionNo());
            line.setMaterialId(Ids.parse(previewLine.materialId()));
            line.setRequestedQuantity(previewLine.requestedQuantity());
            line.setIssuedQuantity(BigDecimal.ZERO);
            lineMapper.insert(line);
            linesByMaterial.put(line.getMaterialId(), line);
        });
        preview.allocations().forEach(planned -> {
            MaterialClaimLine line = linesByMaterial.get(Ids.parse(planned.materialId()));
            if (line == null) {
                throw conflict("计划分配与申领物料行不一致");
            }
            MaterialClaimAllocation allocation = new MaterialClaimAllocation();
            allocation.setId(IdWorker.getId());
            allocation.setClaimId(claim.getId());
            allocation.setClaimLineId(line.getId());
            allocation.setRevisionNo(claim.getRevisionNo());
            allocation.setLocationId(Ids.parse(planned.locationId()));
            allocation.setPlannedQuantity(planned.plannedQuantity());
            allocationMapper.insert(allocation);
        });
    }

    private ClaimPreviewDTO toPreviewDTO(MaterialClaim claim,
                                         List<MaterialClaimLine> lines,
                                         List<MaterialClaimAllocation> allocations) {
        Map<Long, Long> materialsByLine = lines.stream()
                .collect(Collectors.toMap(MaterialClaimLine::getId, MaterialClaimLine::getMaterialId));
        List<ClaimPreviewLineDTO> lineDTOs = lines.stream()
                .map(line -> new ClaimPreviewLineDTO(str(line.getMaterialId()), line.getRequestedQuantity()))
                .toList();
        List<ClaimPreviewAllocationDTO> allocationDTOs = allocations.stream()
                .map(allocation -> new ClaimPreviewAllocationDTO(str(materialsByLine.get(allocation.getClaimLineId())),
                        str(allocation.getLocationId()), allocation.getPlannedQuantity()))
                .toList();
        return new ClaimPreviewDTO(claim.getBookingDate(), str(claim.getStartPeriodId()), str(claim.getEndPeriodId()),
                lineDTOs, allocationDTOs, claim.getPurpose());
    }

    private ClaimPreviewDTO toPreviewDTO(String purpose,
                                         java.time.LocalDate bookingDate,
                                         String startPeriodId,
                                         String endPeriodId,
                                         List<ClaimPreviewLineDTO> lines,
                                         List<ClaimPreviewAllocationDTO> allocations) {
        return new ClaimPreviewDTO(bookingDate, startPeriodId, endPeriodId, lines, allocations, purpose);
    }

    private List<MaterialClaimLine> currentLines(MaterialClaim claim) {
        return lineMapper.selectList(Wrappers.<MaterialClaimLine>lambdaQuery()
                .eq(MaterialClaimLine::getClaimId, claim.getId())
                .eq(MaterialClaimLine::getRevisionNo, claim.getRevisionNo())
                .orderByAsc(MaterialClaimLine::getMaterialId)
                .orderByAsc(MaterialClaimLine::getId));
    }

    private List<MaterialClaimAllocation> currentAllocations(MaterialClaim claim) {
        return allocationMapper.selectList(Wrappers.<MaterialClaimAllocation>lambdaQuery()
                .eq(MaterialClaimAllocation::getClaimId, claim.getId())
                .eq(MaterialClaimAllocation::getRevisionNo, claim.getRevisionNo())
                .orderByAsc(MaterialClaimAllocation::getLocationId)
                .orderByAsc(MaterialClaimAllocation::getId));
    }

    private ActionState actions(MaterialClaim claim,
                                List<MaterialClaimLine> lines,
                                List<MaterialClaimAllocation> allocations,
                                boolean hasIssued,
                                Instant now,
                                Long currentUserId,
                                Set<Long> userLocationIds,
                                PickupResolution pickup) {
        boolean owner = claim.getApplicantId().equals(currentUserId);
        boolean plannedLocationAccess = allocations.stream()
                .anyMatch(allocation -> userLocationIds.contains(allocation.getLocationId()));
        boolean expired = isExpired(claim, hasIssued, now);
        BigDecimal unclaimed = lines.stream()
                .map(line -> line.getRequestedQuantity().subtract(line.getIssuedQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean reviewPermission = SecurityUtils.hasPermission("item:claim:review");
        boolean updatePermission = SecurityUtils.hasPermission("item:claim:update");
        boolean cancelPermission = SecurityUtils.hasPermission("item:claim:cancel");
        boolean abandonPermission = SecurityUtils.hasPermission("item:claim:abandon");
        boolean createPermission = SecurityUtils.hasPermission("item:claim:create");
        boolean issuePermission = SecurityUtils.hasPermission("item:claim:issue");
        boolean delegatePermission = SecurityUtils.hasPermission("item:claim:delegate");

        boolean canReview = reviewPermission && plannedLocationAccess && !hasIssued && !expired
                && claim.getStatus() == ClaimStatus.PENDING;
        boolean canModify = owner && updatePermission && !hasIssued && !expired
                && (claim.getStatus() == ClaimStatus.PENDING || claim.getStatus() == ClaimStatus.APPROVED);
        boolean canCancel = owner && cancelPermission && !hasIssued && isCancellableStatus(claim.getStatus());
        boolean canAbandon = owner && abandonPermission && claim.getStatus() == ClaimStatus.PARTIALLY_ISSUED
                && claim.getAbandonRequestedBy() == null;
        boolean canClose = reviewPermission && abandonPermission && plannedLocationAccess
                && claim.getStatus() == ClaimStatus.PARTIALLY_ISSUED && claim.getAbandonRequestedBy() != null;
        boolean canRebook = owner && createPermission;
        boolean canIssue = issuePermission && pickup.valid() && !userLocationIds.isEmpty() && unclaimed.signum() > 0
                && (claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.PARTIALLY_ISSUED)
                && timeService.isIssueWindow(now, claim.getBookingDate());
        boolean canDelegate = owner && delegatePermission && !hasIssued && !expired
                && (claim.getStatus() == ClaimStatus.PENDING || claim.getStatus() == ClaimStatus.APPROVED);

        Map<String, String> reasons = new LinkedHashMap<>();
        if (!canReview) {
            reasons.put("review", !reviewPermission ? "无申领审核权限"
                    : !plannedLocationAccess ? "仅当前版本计划库位管理员可审核"
                    : hasIssued ? "已产生出库行，禁止审核"
                    : claim.getStatus() != ClaimStatus.PENDING ? "当前状态不可审核" : "申领已过期");
        }
        if (!canModify) {
            reasons.put("modify", !owner ? "仅申请人本人可修改"
                    : !updatePermission ? "无申领修改权限"
                    : hasIssued ? "已产生出库行，禁止修改"
                    : claim.getStatus() != ClaimStatus.PENDING && claim.getStatus() != ClaimStatus.APPROVED
                    ? "当前状态不可修改" : "申领已过期，禁止修改");
        }
        if (!canCancel) {
            reasons.put("cancel", !owner ? "仅申请人本人可取消"
                    : !cancelPermission ? "无申领取消权限"
                    : hasIssued ? "已产生出库行，不能取消整张申领" : "当前状态不可取消");
        }
        if (!canAbandon) {
            reasons.put("abandon", !owner ? "仅申请人本人可放弃剩余"
                    : !abandonPermission ? "无放弃剩余权限"
                    : claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED ? "仅部分实发后可放弃剩余"
                    : "已提交放弃剩余请求");
        }
        if (!canClose) {
            reasons.put("close", !abandonPermission ? "无关闭申领权限"
                    : !reviewPermission ? "仅管理员可关闭申领"
                    : claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED ? "仅部分实发的申领可关闭"
                    : claim.getAbandonRequestedBy() == null ? "申请人尚未提交放弃剩余请求"
                    : "仅当前版本计划库位管理员可关闭");
        }
        if (!canRebook) {
            reasons.put("rebook", !owner ? "仅可复制本人申领模板" : "无申领创建权限");
        }
        if (!canIssue) {
            reasons.put("issue", !issuePermission ? "无申领实发权限"
                    : !pickup.valid() ? pickup.blockedReason()
                    : userLocationIds.isEmpty() ? "当前用户未配置库位管理关系"
                    : claim.getStatus() != ClaimStatus.APPROVED && claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED
                    ? "当前状态不可实发"
                    : unclaimed.signum() <= 0 ? "没有未领取数量" : "当前时间不在实发窗口内");
        }
        if (!canDelegate) {
            reasons.put("delegate", !owner ? "仅申请人本人可委派代领"
                    : !delegatePermission ? "无申领委派权限"
                    : hasIssued ? "已产生出库行，禁止变更代领委派"
                    : claim.getStatus() != ClaimStatus.PENDING && claim.getStatus() != ClaimStatus.APPROVED
                    ? "当前状态不可委派代领" : "预约已过期，禁止变更代领委派");
        }
        return new ActionState(canReview, canModify, canCancel, canAbandon, canClose, canRebook, canIssue, canDelegate,
                Collections.unmodifiableMap(reasons));
    }

    private ClaimSummaryVO toSummaryVO(MaterialClaim claim, BizDocument document, SysUser applicant,
                                       ClaimStatus displayStatus) {
        return new ClaimSummaryVO(str(claim.getId()), str(claim.getBizDocumentId()), str(claim.getId()),
                document == null ? null : document.getDocumentNo(), str(claim.getApplicantId()),
                applicant == null ? null : applicant.getRealName(), claim.getPurpose(), claim.getBookingDate(),
                claim.getBookingStartAt(), claim.getBookingEndAt(), claim.getReturnDeadline(), displayStatus,
                claim.getRevisionNo(), claim.getDelegatedAt(), claim.getCreateTime());
    }

    private ClaimLineVO toLineVO(MaterialClaimLine line, Material material) {
        return new ClaimLineVO(str(line.getId()), str(line.getClaimId()), line.getRevisionNo(),
                str(line.getMaterialId()), material == null ? null : material.getMaterialName(),
                material == null ? null : material.getSpecification(),
                material == null ? null : material.getMaterialType(),
                material == null ? null : material.getQuantityMode(),
                material == null ? null : material.getBaseUnit(), line.getRequestedQuantity(),
                line.getIssuedQuantity(), line.getRequestedQuantity().subtract(line.getIssuedQuantity()));
    }

    private ItemDocumentEventVO toEventVO(DocumentEvent event, Map<Long, SysUser> users) {
        SysUser actor = event.getActorId() == null ? null : users.get(event.getActorId());
        String actorName = "SYSTEM".equals(event.getActorType()) ? "系统"
                : actor == null ? null : actor.getRealName();
        return new ItemDocumentEventVO(str(event.getId()), str(event.getDocumentId()), event.getEventType(),
                event.getFromStatus(), event.getToStatus(), str(event.getActorId()), actorName,
                event.getActorType(), event.getOccurredAt(), event.getReason(), event.getRequestKey());
    }

    private Map<Long, Material> loadMaterials(List<MaterialClaimLine> lines) {
        Set<Long> materialIds = lines.stream().map(MaterialClaimLine::getMaterialId).collect(Collectors.toSet());
        return materialIds.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private Map<Long, SysUser> loadUsers(Set<Long> userIds) {
        return userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private void addUserIds(MaterialClaim claim, Set<Long> userIds) {
        userIds.add(claim.getApplicantId());
        if (claim.getDelegateStudentId() != null) userIds.add(claim.getDelegateStudentId());
        if (claim.getApprovedBy() != null) userIds.add(claim.getApprovedBy());
        if (claim.getAbandonRequestedBy() != null) userIds.add(claim.getAbandonRequestedBy());
        if (claim.getClosedBy() != null) userIds.add(claim.getClosedBy());
        if (claim.getCancelledBy() != null) userIds.add(claim.getCancelledBy());
    }

    private ClaimDetailVO findIdempotentClaim(Long ownerId, String idempotencyKey) {
        BizDocument existing = flowService.findByOwnerIdempotency(ownerId, DOCUMENT_TYPE, idempotencyKey);
        if (existing == null) {
            return null;
        }
        MaterialClaim claim = claimMapper.selectByBizDocumentIdForUpdate(existing.getId());
        if (claim == null) {
            throw conflict("幂等键对应申领不存在或尚未完成");
        }
        requireOwner(claim, ownerId);
        return buildDetail(claim);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw badRequest("幂等键长度不能超过128");
        }
        return normalized;
    }

    private void requireNoShortages(ClaimPreviewVO preview) {
        if (preview.shortages().isEmpty()) {
            return;
        }
        String details = preview.shortages().stream()
                .limit(5)
                .map(this::shortageText)
                .collect(Collectors.joining("；"));
        throw conflict("库存不足：" + details);
    }

    private String shortageText(ClaimShortageVO shortage) {
        return shortage.materialName() + "/" + (shortage.locationName() == null ? "无可用库位" : shortage.locationName())
                + "，缺口" + shortage.shortageQuantity().stripTrailingZeros().toPlainString();
    }

    private boolean hasOutbound(Long claimId) {
        return !documentLineMapper.selectOutboundClaimIds(List.of(claimId)).isEmpty();
    }

    private ClaimStatus displayStatus(MaterialClaim claim, boolean hasIssued, Instant now) {
        return isExpired(claim, hasIssued, now) ? ClaimStatus.EXPIRED : claim.getStatus();
    }

    private boolean isExpired(MaterialClaim claim, boolean hasIssued, Instant now) {
        return !hasIssued && (claim.getStatus() == ClaimStatus.PENDING || claim.getStatus() == ClaimStatus.APPROVED)
                && timeService.isIssueWindowExpired(now, claim.getBookingDate());
    }

    private boolean isCancellableStatus(ClaimStatus status) {
        return status == ClaimStatus.PENDING || status == ClaimStatus.APPROVED || status == ClaimStatus.EXPIRED;
    }

    private ClaimStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ClaimStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest("申领状态不合法");
        }
    }

    private void applySnapshot(MaterialClaim claim, BookingTimeSnapshotVO snapshot) {
        claim.setBookingDate(snapshot.bookingDate());
        claim.setStartPeriodId(Ids.parse(snapshot.startPeriodId()));
        claim.setEndPeriodId(Ids.parse(snapshot.endPeriodId()));
        claim.setBookingStartAt(snapshot.bookingStartAt());
        claim.setBookingEndAt(snapshot.bookingEndAt());
        claim.setReturnDeadline(snapshot.returnDeadline());
    }

    private MaterialClaim requireClaim(Long id) {
        MaterialClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return claim;
    }

    private MaterialClaim requireClaimForUpdate(Long id) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(id);
        if (claim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return claim;
    }

    private MaterialClaim requireOwnedClaim(Long id, Long ownerId) {
        MaterialClaim claim = requireClaim(id);
        requireOwner(claim, ownerId);
        return claim;
    }

    private MaterialClaim requireOwnedClaimForUpdate(Long id, Long ownerId) {
        MaterialClaim claim = requireClaimForUpdate(id);
        requireOwner(claim, ownerId);
        return claim;
    }

    private void requireOwner(MaterialClaim claim, Long ownerId) {
        if (!claim.getApplicantId().equals(ownerId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
    }

    private void requireRevision(MaterialClaim claim, Integer revisionNo) {
        if (!claim.getRevisionNo().equals(revisionNo)) {
            throw conflict("申领版本已变化，请刷新后重试");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    private record ReviewContext(
            List<MaterialClaimLine> lines,
            List<MaterialClaimAllocation> allocations,
            ClaimPreviewDTO previewDTO
    ) {
    }

    private record ActionState(
            boolean canReview,
            boolean canModify,
            boolean canCancel,
            boolean canAbandon,
            boolean canClose,
            boolean canRebook,
            boolean canIssue,
            boolean canDelegate,
            Map<String, String> actionReasons
    ) {
    }
}

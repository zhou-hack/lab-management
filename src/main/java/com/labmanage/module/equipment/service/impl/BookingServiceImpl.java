package com.labmanage.module.equipment.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.enums.ActorType;
import com.labmanage.module.document.enums.ApprovalStage;
import com.labmanage.module.document.enums.DocumentType;
import com.labmanage.module.document.enums.EventType;
import com.labmanage.module.document.enums.TaskStatus;
import com.labmanage.module.document.mapper.ApprovalTaskMapper;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.document.service.ApprovalTaskService;
import com.labmanage.module.document.service.DocumentService;
import com.labmanage.module.equipment.dto.BookingApproveRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingCheckoutRequest;
import com.labmanage.module.equipment.dto.BookingCreateRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.entity.EquipmentAsset;
import com.labmanage.module.equipment.entity.EquipmentBooking;
import com.labmanage.module.equipment.entity.EquipmentModel;
import com.labmanage.module.equipment.entity.EquipmentUsage;
import com.labmanage.module.equipment.enums.BookingStatus;
import com.labmanage.module.equipment.enums.EquipmentStatus;
import com.labmanage.module.equipment.enums.ReturnCondition;
import com.labmanage.module.equipment.enums.UsageStatus;
import com.labmanage.module.equipment.mapper.BookingRestrictionMapper;
import com.labmanage.module.equipment.mapper.EquipmentAssetMapper;
import com.labmanage.module.equipment.mapper.EquipmentBookingMapper;
import com.labmanage.module.equipment.mapper.EquipmentModelMapper;
import com.labmanage.module.equipment.mapper.EquipmentUsageMapper;
import com.labmanage.module.equipment.service.BookingService;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;
import com.labmanage.module.space.entity.Lab;
import com.labmanage.module.space.mapper.LabMapper;
import com.labmanage.roles.user.entity.SysUser;
import com.labmanage.roles.user.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 器材借用服务实现
 * <p>
 * 所有占用类变更（提交/审批/借出/归还）在同一事务内先
 * SELECT ... FOR UPDATE 锁资产行再复查，防止并发超借。
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final String PERM_TEACHER_APPROVE = "equipment:booking:teacher_approve";
    private static final String PERM_ADMIN_APPROVE = "equipment:booking:admin_approve";

    private final EquipmentBookingMapper bookingMapper;
    private final EquipmentAssetMapper assetMapper;
    private final EquipmentModelMapper modelMapper;
    private final EquipmentUsageMapper usageMapper;
    private final BookingRestrictionMapper restrictionMapper;
    private final BizDocumentMapper documentMapper;
    private final ApprovalTaskMapper taskMapper;
    private final LabMapper labMapper;
    private final SysUserMapper userMapper;
    private final DocumentService documentService;
    private final ApprovalTaskService taskService;

    public BookingServiceImpl(EquipmentBookingMapper bookingMapper,
                              EquipmentAssetMapper assetMapper,
                              EquipmentModelMapper modelMapper,
                              EquipmentUsageMapper usageMapper,
                              BookingRestrictionMapper restrictionMapper,
                              BizDocumentMapper documentMapper,
                              ApprovalTaskMapper taskMapper,
                              LabMapper labMapper,
                              SysUserMapper userMapper,
                              DocumentService documentService,
                              ApprovalTaskService taskService) {
        this.bookingMapper = bookingMapper;
        this.assetMapper = assetMapper;
        this.modelMapper = modelMapper;
        this.usageMapper = usageMapper;
        this.restrictionMapper = restrictionMapper;
        this.documentMapper = documentMapper;
        this.taskMapper = taskMapper;
        this.labMapper = labMapper;
        this.userMapper = userMapper;
        this.documentService = documentService;
        this.taskService = taskService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVO create(BookingCreateRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        Instant startAt = request.startAt().toInstant();
        Instant endAt = request.endAt().toInstant();
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "借用结束时间必须晚于开始时间");
        }
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();
        BizDocument existing = findIdempotentDocument(userId, idempotencyKey);
        if (existing != null) {
            return assembleByDocument(existing.getId());
        }

        EquipmentAsset asset = assetMapper.selectByIdForUpdate(request.assetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用设备不存在");
        }
        if (asset.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已停用，无法借用");
        }
        if (asset.getStatus() != EquipmentStatus.IDLE && asset.getStatus() != EquipmentStatus.RESERVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备当前状态不可借用");
        }
        if (asset.getValueLevel() == null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备尚未定级，禁止借用");
        }
        if (restrictionMapper.countActiveRestrictions(userId, utc(startAt), utc(endAt)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "存在生效中的借用限制，无法提交");
        }
        if (bookingMapper.countConflicts(asset.getId(), utc(startAt), utc(endAt), null) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该时段设备已被占用");
        }

        SysUser applicant = userMapper.selectById(userId);
        if (applicant == null || applicant.getEnabled() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "申请人账号不可用");
        }
        boolean student = "STUDENT".equals(applicant.getUserType());
        Long approvalTeacherId = null;
        if (student) {
            if (request.approvalTeacherId() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "学生借用须指定审批老师");
            }
            SysUser teacher = userMapper.selectById(request.approvalTeacherId());
            if (teacher == null || teacher.getEnabled() != 1 || !"TEACHER".equals(teacher.getUserType())) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "指定审批老师不存在或不可用");
            }
            approvalTeacherId = request.approvalTeacherId();
        }

        BizDocument document;
        try {
            document = documentService.create(
                    DocumentType.EQUIPMENT_BOOKING, userId, applicant.getDepartmentId(), idempotencyKey);
        } catch (DuplicateKeyException e) {
            // 并发同键提交：唯一键拦截后查询原单返回
            BizDocument winner = findIdempotentDocument(userId, idempotencyKey);
            if (winner == null) {
                throw e;
            }
            return assembleByDocument(winner.getId());
        }

        EquipmentBooking booking = new EquipmentBooking();
        booking.setDocumentId(document.getId());
        booking.setAssetId(asset.getId());
        booking.setApplicantId(userId);
        booking.setApprovalTeacherId(approvalTeacherId);
        booking.setPurpose(request.purpose());
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        booking.setValueLevelSnapshot(asset.getValueLevel());
        booking.setStatus(student ? BookingStatus.PENDING_TEACHER : BookingStatus.PENDING_ADMIN);
        bookingMapper.insert(booking);

        if (student) {
            taskService.createTask(document, ApprovalStage.TEACHER_REVIEW, 1, "TEACHER",
                    approvalTeacherId, TaskStatus.READY);
            taskService.createTask(document, ApprovalStage.ADMIN_REVIEW, 2, "ADMIN",
                    null, TaskStatus.WAITING);
        } else {
            taskService.createTask(document, ApprovalStage.ADMIN_REVIEW, 1, "ADMIN",
                    null, TaskStatus.READY);
        }
        documentService.appendEvent(document.getId(), EventType.SUBMIT, null,
                booking.getStatus().name(), userId, ActorType.USER, null, null, "SUBMIT");
        return assembleByDocument(document.getId());
    }

    @Override
    public PageResult<BookingVO> page(BookingPageQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean adminApprover = StpUtil.hasPermission(PERM_ADMIN_APPROVE);
        boolean teacherApprover = StpUtil.hasPermission(PERM_TEACHER_APPROVE);
        LambdaQueryWrapper<EquipmentBooking> wrapper = new LambdaQueryWrapper<>();
        String scope = query.getScope() == null ? "MINE" : query.getScope();
        switch (scope) {
            case "PENDING" -> {
                if (adminApprover) {
                    wrapper.eq(EquipmentBooking::getStatus, BookingStatus.PENDING_ADMIN);
                } else if (teacherApprover) {
                    wrapper.eq(EquipmentBooking::getApprovalTeacherId, userId)
                            .eq(EquipmentBooking::getStatus, BookingStatus.PENDING_TEACHER);
                } else {
                    return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
                }
            }
            case "ALL" -> {
                if (!adminApprover) {
                    throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅管理员可查看全部借用单");
                }
            }
            default -> wrapper.eq(EquipmentBooking::getApplicantId, userId);
        }
        if (query.getStatus() != null) {
            wrapper.eq(EquipmentBooking::getStatus, query.getStatus());
        }
        if (query.getAssetId() != null) {
            wrapper.eq(EquipmentBooking::getAssetId, query.getAssetId());
        }
        wrapper.orderByDesc(EquipmentBooking::getId);
        Page<EquipmentBooking> page = bookingMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public BookingVO detail(String id) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        Long userId = StpUtil.getLoginIdAsLong();
        boolean adminApprover = StpUtil.hasPermission(PERM_ADMIN_APPROVE);
        boolean self = Objects.equals(userId, booking.getApplicantId());
        boolean assignedTeacher = Objects.equals(userId, booking.getApprovalTeacherId());
        if (!adminApprover && !self && !assignedTeacher) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权查看该借用单");
        }
        return assemble(List.of(booking)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, BookingCancelRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        Long userId = StpUtil.getLoginIdAsLong();
        if (!Objects.equals(userId, booking.getApplicantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有申请人本人可以撤销借用单");
        }
        if (booking.getStatus() != BookingStatus.PENDING_TEACHER
                && booking.getStatus() != BookingStatus.PENDING_ADMIN) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用单当前状态不可撤销");
        }
        List<ApprovalTask> openTasks = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getDocumentId, booking.getDocumentId())
                .in(ApprovalTask::getStatus, TaskStatus.WAITING, TaskStatus.READY));
        for (ApprovalTask task : openTasks) {
            taskService.cancel(task.getId());
        }
        String fromStatus = booking.getStatus().name();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(request.reason());
        booking.setCancelledAt(Instant.now());
        bookingMapper.updateById(booking);
        documentService.appendEvent(booking.getDocumentId(), EventType.CANCEL,
                fromStatus, BookingStatus.CANCELLED.name(),
                userId, ActorType.USER, request.reason(), null, "CANCEL");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void teacherApprove(String id, BookingApproveRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        if (booking.getStatus() != BookingStatus.PENDING_TEACHER) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用单不在待老师审批状态");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        if (!Objects.equals(userId, booking.getApprovalTeacherId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有指定的审批老师可以处理该借用单");
        }
        ApprovalTask task = requireTask(booking.getDocumentId(), ApprovalStage.TEACHER_REVIEW);
        ApprovalTask nextTask = requireTask(booking.getDocumentId(), ApprovalStage.ADMIN_REVIEW);
        if (Boolean.TRUE.equals(request.approved())) {
            revalidateAsset(booking, null);
            taskService.approve(task.getId(), userId, request.comment());
            taskService.activate(nextTask.getId());
            booking.setStatus(BookingStatus.PENDING_ADMIN);
            bookingMapper.updateById(booking);
            documentService.appendEvent(booking.getDocumentId(), EventType.APPROVE,
                    BookingStatus.PENDING_TEACHER.name(), BookingStatus.PENDING_ADMIN.name(),
                    userId, ActorType.USER, request.comment(), task.getId(), "APPROVE:TEACHER_REVIEW");
        } else {
            if (!StringUtils.hasText(request.comment())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "驳回时必须填写审批意见");
            }
            taskService.reject(task.getId(), userId, request.comment());
            taskService.cancel(nextTask.getId());
            booking.setStatus(BookingStatus.REJECTED);
            bookingMapper.updateById(booking);
            documentService.appendEvent(booking.getDocumentId(), EventType.REJECT,
                    BookingStatus.PENDING_TEACHER.name(), BookingStatus.REJECTED.name(),
                    userId, ActorType.USER, request.comment(), task.getId(), "REJECT:TEACHER_REVIEW");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminApprove(String id, BookingApproveRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        if (booking.getStatus() != BookingStatus.PENDING_ADMIN) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用单不在待管理员审批状态");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        ApprovalTask task = requireTask(booking.getDocumentId(), ApprovalStage.ADMIN_REVIEW);
        if (Boolean.TRUE.equals(request.approved())) {
            EquipmentAsset asset = revalidateAsset(booking, booking.getId());
            taskService.approve(task.getId(), userId, request.comment());
            booking.setStatus(BookingStatus.APPROVED);
            bookingMapper.updateById(booking);
            if (asset.getStatus() == EquipmentStatus.IDLE) {
                asset.setStatus(EquipmentStatus.RESERVED);
                assetMapper.updateById(asset);
            }
            documentService.appendEvent(booking.getDocumentId(), EventType.APPROVE,
                    BookingStatus.PENDING_ADMIN.name(), BookingStatus.APPROVED.name(),
                    userId, ActorType.USER, request.comment(), task.getId(), "APPROVE:ADMIN_REVIEW");
        } else {
            if (!StringUtils.hasText(request.comment())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "驳回时必须填写审批意见");
            }
            taskService.reject(task.getId(), userId, request.comment());
            booking.setStatus(BookingStatus.REJECTED);
            bookingMapper.updateById(booking);
            documentService.appendEvent(booking.getDocumentId(), EventType.REJECT,
                    BookingStatus.PENDING_ADMIN.name(), BookingStatus.REJECTED.name(),
                    userId, ActorType.USER, request.comment(), task.getId(), "REJECT:ADMIN_REVIEW");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkout(String id, BookingCheckoutRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用单未获批准或已处理，无法借出");
        }
        Instant now = Instant.now();
        if (now.isBefore(booking.getStartAt())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用开始时间未到，无法借出");
        }
        if (now.isAfter(booking.getEndAt())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用时段已结束，无法借出");
        }
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(booking.getAssetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用设备不存在");
        }
        if (asset.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已停用，无法借出");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        EquipmentUsage usage = new EquipmentUsage();
        usage.setBookingId(booking.getId());
        usage.setBorrowerId(booking.getApplicantId());
        usage.setCheckoutOperatorId(userId);
        usage.setCheckedOutAt(now);
        usage.setDueAt(booking.getEndAt());
        usage.setCheckoutNote(request.note());
        usage.setStatus(UsageStatus.CHECKED_OUT);
        usageMapper.insert(usage);

        booking.setStatus(BookingStatus.CHECKED_OUT);
        bookingMapper.updateById(booking);

        asset.setStatus(EquipmentStatus.IN_USE);
        assetMapper.updateById(asset);

        documentService.appendEvent(booking.getDocumentId(), EventType.CHECKOUT,
                BookingStatus.APPROVED.name(), BookingStatus.CHECKED_OUT.name(),
                userId, ActorType.USER, request.note(), null, "CHECKOUT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(String id, BookingReturnRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借用单不在借出状态");
        }
        if (request.condition() == ReturnCondition.DAMAGED && !StringUtils.hasText(request.note())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "设备损坏归还必须填写归还说明");
        }
        EquipmentUsage usage = usageMapper.selectOne(new LambdaQueryWrapper<EquipmentUsage>()
                .eq(EquipmentUsage::getBookingId, booking.getId())
                .last("LIMIT 1"));
        if (usage == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借出流水不存在");
        }
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(booking.getAssetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用设备不存在");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        Instant now = Instant.now();
        usage.setStatus(UsageStatus.RETURNED);
        usage.setReturnOperatorId(userId);
        usage.setReturnedAt(now);
        usage.setReturnCondition(request.condition());
        usage.setReturnNote(request.note());
        usageMapper.updateById(usage);

        booking.setStatus(BookingStatus.RETURNED);
        bookingMapper.updateById(booking);

        // 资产状态重算：损坏转维护中；正常则按剩余生效单判定占用/空闲
        if (request.condition() == ReturnCondition.DAMAGED) {
            asset.setStatus(EquipmentStatus.MAINTAINING);
        } else {
            boolean stillOccupied = bookingMapper.countActiveExcluding(asset.getId(), booking.getId()) > 0;
            asset.setStatus(stillOccupied ? EquipmentStatus.RESERVED : EquipmentStatus.IDLE);
        }
        assetMapper.updateById(asset);

        documentService.appendEvent(booking.getDocumentId(), EventType.RETURN,
                BookingStatus.CHECKED_OUT.name(), BookingStatus.RETURNED.name(),
                userId, ActorType.USER, request.note(), null, "RETURN");
    }

    @Override
    public List<TeacherOptionVO> teacherOptions() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "TEACHER")
                        .eq(SysUser::getEnabled, 1)
                        .orderByAsc(SysUser::getPersonNo))
                .stream()
                .map(user -> new TeacherOptionVO(
                        user.getId().toString(), user.getRealName(), user.getPersonNo()))
                .toList();
    }

    /**
     * 幂等查询：同用户同类型同幂等键的原单
     */
    private BizDocument findIdempotentDocument(Long userId, String idempotencyKey) {
        return documentMapper.selectOne(new LambdaQueryWrapper<BizDocument>()
                .eq(BizDocument::getOwnerUserId, userId)
                .eq(BizDocument::getDocumentType, DocumentType.EQUIPMENT_BOOKING)
                .eq(BizDocument::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    /**
     * 审批前资产复查：锁资产行后校验启用、状态、定级与时段冲突
     */
    private EquipmentAsset revalidateAsset(EquipmentBooking booking, Long excludeBookingId) {
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(booking.getAssetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用设备不存在");
        }
        if (asset.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已停用，无法通过审批");
        }
        if (asset.getStatus() != EquipmentStatus.IDLE && asset.getStatus() != EquipmentStatus.RESERVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备状态已变化，无法通过审批");
        }
        if (asset.getValueLevel() == null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备尚未定级，无法通过审批");
        }
        if (bookingMapper.countConflicts(asset.getId(),
                utc(booking.getStartAt()), utc(booking.getEndAt()), excludeBookingId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该时段设备已被其他借用单占用");
        }
        return asset;
    }

    private EquipmentBooking requireBooking(Long bookingId) {
        EquipmentBooking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用单不存在");
        }
        return booking;
    }

    private ApprovalTask requireTask(Long documentId, ApprovalStage stage) {
        ApprovalTask task = taskMapper.selectOne(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getDocumentId, documentId)
                .eq(ApprovalTask::getStageCode, stage)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "审批任务不存在");
        }
        return task;
    }

    /**
     * Instant 转 UTC LocalDateTime（Mapper 时间参数）
     */
    private LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * 按单据 ID 装配单条 VO（幂等返回与新建返回共用）
     */
    private BookingVO assembleByDocument(Long documentId) {
        EquipmentBooking booking = bookingMapper.selectOne(new LambdaQueryWrapper<EquipmentBooking>()
                .eq(EquipmentBooking::getDocumentId, documentId)
                .last("LIMIT 1"));
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "借用单不存在");
        }
        return assemble(List.of(booking)).get(0);
    }

    private List<BookingVO> assemble(List<EquipmentBooking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Map<Long, BizDocument> documents = loadMap(
                bookings.stream().map(EquipmentBooking::getDocumentId).collect(Collectors.toSet()),
                documentMapper::selectBatchIds, BizDocument::getId);
        Map<Long, EquipmentAsset> assets = loadMap(
                bookings.stream().map(EquipmentBooking::getAssetId).collect(Collectors.toSet()),
                assetMapper::selectBatchIds, EquipmentAsset::getId);
        Set<Long> modelIds = assets.values().stream()
                .map(EquipmentAsset::getModelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EquipmentModel> models = loadMap(modelIds, modelMapper::selectBatchIds, EquipmentModel::getId);
        Set<Long> labIds = assets.values().stream()
                .map(EquipmentAsset::getLabId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Lab> labs = loadMap(labIds, labMapper::selectBatchIds, Lab::getId);
        Set<Long> userIds = new HashSet<>();
        bookings.forEach(booking -> {
            userIds.add(booking.getApplicantId());
            if (booking.getApprovalTeacherId() != null) {
                userIds.add(booking.getApprovalTeacherId());
            }
        });
        Map<Long, SysUser> users = loadMap(userIds, userMapper::selectBatchIds, SysUser::getId);
        return bookings.stream()
                .map(booking -> {
                    BizDocument document = documents.get(booking.getDocumentId());
                    EquipmentAsset asset = assets.get(booking.getAssetId());
                    EquipmentModel model = asset == null ? null : models.get(asset.getModelId());
                    Lab lab = asset == null ? null : labs.get(asset.getLabId());
                    SysUser applicant = users.get(booking.getApplicantId());
                    SysUser teacher = booking.getApprovalTeacherId() == null
                            ? null : users.get(booking.getApprovalTeacherId());
                    return new BookingVO(
                            booking.getId().toString(),
                            document != null ? document.getDocumentNo() : null,
                            booking.getAssetId().toString(),
                            asset != null ? asset.getAssetCode() : null,
                            model != null ? model.getEquipmentName() : null,
                            asset == null || asset.getLabId() == null ? null : asset.getLabId().toString(),
                            lab != null ? lab.getLabName() : null,
                            booking.getApplicantId().toString(),
                            applicant != null ? applicant.getRealName() : null,
                            booking.getApprovalTeacherId() == null
                                    ? null : booking.getApprovalTeacherId().toString(),
                            teacher != null ? teacher.getRealName() : null,
                            booking.getPurpose(),
                            booking.getStartAt(),
                            booking.getEndAt(),
                            booking.getValueLevelSnapshot(),
                            booking.getStatus(),
                            booking.getCancelReason(),
                            booking.getCancelledAt(),
                            booking.getCreateTime()
                    );
                })
                .toList();
    }

    private <T> Map<Long, T> loadMap(Collection<Long> ids,
                                     Function<Collection<Long>, List<T>> loader,
                                     Function<T, Long> keyGetter) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream().collect(Collectors.toMap(keyGetter, Function.identity()));
    }
}

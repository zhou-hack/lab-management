package com.labmanage.module.equipment.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.document.mapper.DocumentEventMapper;
import com.labmanage.module.equipment.dto.BookingApplyRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.dto.BookingReviewRequest;
import com.labmanage.module.equipment.entity.EquipmentAsset;
import com.labmanage.module.equipment.entity.EquipmentBooking;
import com.labmanage.module.equipment.entity.EquipmentUsage;
import com.labmanage.module.equipment.enums.BookingStatus;
import com.labmanage.module.equipment.enums.EquipmentStatus;
import com.labmanage.module.equipment.enums.ReturnCondition;
import com.labmanage.module.equipment.enums.ValueLevel;
import com.labmanage.module.equipment.mapper.EquipmentAssetMapper;
import com.labmanage.module.equipment.mapper.EquipmentBookingMapper;
import com.labmanage.module.equipment.mapper.EquipmentUsageMapper;
import com.labmanage.module.equipment.mapper.UserBookingRestrictionMapper;
import com.labmanage.module.equipment.service.BookingService;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;
import com.labmanage.roles.user.entity.SysUser;
import com.labmanage.roles.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 设备使用申请实现（学生单：教师初审 → 管理员终审；教师单：仅管理员终审；
 * 到期未还惰性推进 OVERDUE 并冻结申请人 7 天；复用保留表结构，零改表）
 * <p>
 * 冲突协议：申请期放开时段互斥，占用校验在管理员终审（requireNoOverlap）
 * 与借出登记（countOverlap）时按先到先得执行。
 */
@Service
public class BookingServiceImpl implements BookingService {

    /** 统一单据类型：设备使用申请 */
    private static final String DOC_TYPE_EQUIPMENT_BOOKING = "EQUIPMENT_BOOKING";

    /** 逾期冻结时长：进入 OVERDUE 时冻结申请人申请权限 7 天 */
    private static final Duration OVERDUE_FREEZE_DAYS = Duration.ofDays(7);

    private final EquipmentAssetMapper assetMapper;
    private final EquipmentBookingMapper bookingMapper;
    private final EquipmentUsageMapper usageMapper;
    private final BizDocumentMapper documentMapper;
    private final DocumentEventMapper eventMapper;
    private final SysUserMapper userMapper;
    private final UserBookingRestrictionMapper restrictionMapper;

    public BookingServiceImpl(EquipmentAssetMapper assetMapper,
                               EquipmentBookingMapper bookingMapper,
                               EquipmentUsageMapper usageMapper,
                               BizDocumentMapper documentMapper,
                               DocumentEventMapper eventMapper,
                               SysUserMapper userMapper,
                               UserBookingRestrictionMapper restrictionMapper) {
        this.assetMapper = assetMapper;
        this.bookingMapper = bookingMapper;
        this.usageMapper = usageMapper;
        this.documentMapper = documentMapper;
        this.eventMapper = eventMapper;
        this.userMapper = userMapper;
        this.restrictionMapper = restrictionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(BookingApplyRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser applicant = requireEnabledUser(userId, "申请人账号不存在或已停用");
        String userType = applicant.getUserType();

        // 仅学生/教师可提交使用申请；管理员无需申请
        if (!"STUDENT".equals(userType) && !"TEACHER".equals(userType)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅学生/教师可提交设备使用申请");
        }
        boolean isStudent = "STUDENT".equals(userType);

        // 惰性推进逾期未还单（置 OVERDUE + 冻结），保持状态机先行
        advanceOverdue();

        // 冻结拦截：逾期未还冻结期内禁止提交新申请
        Instant restrictedUntil = restrictionMapper.selectActiveRestrictionEndAt(userId);
        if (restrictedUntil != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "因逾期未还设备，申请权限已冻结至 " + restrictedUntil + "，请先归还后再申请");
        }

        Instant startAt = request.startAt();
        Instant endAt = request.endAt();
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "结束时间必须晚于开始时间");
        }
        if (!endAt.isAfter(Instant.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "结束时间必须晚于当前时间");
        }

        // 学生单必须指定审批教师（须为启用且有 TEACHER 角色的用户）
        Long teacherId = null;
        if (isStudent) {
            if (request.approvalTeacherId() == null || request.approvalTeacherId().isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "学生申请必须指定审批教师");
            }
            teacherId = Ids.parse(request.approvalTeacherId());
            SysUser teacher = requireEnabledUser(teacherId, "审批教师不存在或已停用");
            if (!"TEACHER".equals(teacher.getUserType())) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "指定的审批人不是教师");
            }
        }

        // 冲突协议：先锁设备行，校验在架与启用状态；
        // 申请期不校验时段互斥（学生/教师可同时段提交申请），
        // 实际占用在管理员终审（requireNoOverlap）与借出登记时按先到先得校验
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(Ids.parse(request.assetId()));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        if (asset.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已停用，不可申请");
        }
        if (asset.getStatus() != EquipmentStatus.IDLE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备当前不在架，不可申请");
        }

        // 单据底座：幂等键服务端生成，编号「前缀+雪花」
        BizDocument document = new BizDocument();
        document.setDocumentNo("EQ" + IdWorker.getIdStr());
        document.setDocumentType(DOC_TYPE_EQUIPMENT_BOOKING);
        document.setOwnerUserId(userId);
        document.setDepartmentId(applicant.getDepartmentId());
        document.setSubmittedAt(Instant.now());
        document.setIdempotencyKey(UUID.randomUUID().toString());
        documentMapper.insert(document);

        // 状态分流：学生单待教师初审 → 管理员终审；教师单直接待管理员终审
        BookingStatus status = isStudent
                ? BookingStatus.PENDING_TEACHER
                : BookingStatus.PENDING_ADMIN;

        EquipmentBooking booking = new EquipmentBooking();
        booking.setDocumentId(document.getId());
        booking.setAssetId(asset.getId());
        booking.setApplicantId(userId);
        booking.setApprovalTeacherId(teacherId);
        booking.setPurpose(request.purpose());
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        // 价值分级已移除，快照列恒为 NORMAL（列 NOT NULL，零改表保留）
        booking.setValueLevelSnapshot(ValueLevel.NORMAL);
        booking.setStatus(status);
        bookingMapper.insert(booking);

        writeEvent(document.getId(), "SUBMIT", null, status.name(), userId, "USER", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<BookingVO> myBookings(BookingPageQuery query) {
        advanceOverdue();
        query.setApplicantId(StpUtil.getLoginIdAsLong());
        query.setTeacherId(null);
        return PageUtils.toPageResult(bookingMapper.selectBookingPage(toPage(query), query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, BookingCancelRequest request) {
        EquipmentBooking booking = requireBooking(Ids.parse(id));
        if (!Objects.equals(booking.getApplicantId(), StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能撤销本人申请");
        }
        if (booking.getStatus() != BookingStatus.PENDING_TEACHER
                && booking.getStatus() != BookingStatus.PENDING_ADMIN
                && booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已借出或申请已结束，不可撤销");
        }
        BookingStatus from = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(request.reason());
        booking.setCancelledAt(Instant.now());
        bookingMapper.updateById(booking);
        writeEvent(booking.getDocumentId(), "CANCEL", from.name(), BookingStatus.CANCELLED.name(),
                StpUtil.getLoginIdAsLong(), "USER", request.reason());
    }

    @Override
    public PageResult<BookingVO> teacherPending(BookingPageQuery query) {
        query.setTeacherId(StpUtil.getLoginIdAsLong());
        query.setApplicantId(null);
        query.setStatus(BookingStatus.PENDING_TEACHER);
        return PageUtils.toPageResult(bookingMapper.selectBookingPage(toPage(query), query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void teacherReview(String id, BookingReviewRequest request) {
        long reviewerId = StpUtil.getLoginIdAsLong();
        EquipmentBooking booking = bookingMapper.selectByIdForUpdate(Ids.parse(id));
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申请单不存在");
        }
        if (booking.getStatus() != BookingStatus.PENDING_TEACHER) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申请单不在待初审状态");
        }
        if (!Objects.equals(booking.getApprovalTeacherId(), reviewerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能初审指定给自己的申请");
        }
        boolean approve = "approve".equals(request.action());
        if (!approve && (request.comment() == null || request.comment().isBlank())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "驳回必须填写审批意见");
        }

        BookingStatus from = booking.getStatus();
        if (approve) {
            // 教师初审通过 → 转管理员终审（不占用时段，冲突校验在管理员终审执行）
            booking.setStatus(BookingStatus.PENDING_ADMIN);
            bookingMapper.updateById(booking);
            writeEvent(booking.getDocumentId(), "APPROVE", from.name(), BookingStatus.PENDING_ADMIN.name(),
                    reviewerId, "USER", request.comment());
        } else {
            booking.setStatus(BookingStatus.REJECTED);
            bookingMapper.updateById(booking);
            writeEvent(booking.getDocumentId(), "REJECT", from.name(), BookingStatus.REJECTED.name(),
                    reviewerId, "USER", request.comment());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<BookingVO> allBookings(BookingPageQuery query) {
        advanceOverdue();
        query.setApplicantId(null);
        query.setTeacherId(null);
        return PageUtils.toPageResult(bookingMapper.selectBookingPage(toPage(query), query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminReview(String id, BookingReviewRequest request) {
        long reviewerId = StpUtil.getLoginIdAsLong();
        EquipmentBooking booking = bookingMapper.selectByIdForUpdate(Ids.parse(id));
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申请单不存在");
        }
        boolean approve = "approve".equals(request.action());
        if (!approve && (request.comment() == null || request.comment().isBlank())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "驳回必须填写审批意见");
        }

        // 管理员终审为必经环节：仅待终审单可审（学生单两级链第二级 / 教师单唯一审批）
        if (booking.getStatus() != BookingStatus.PENDING_ADMIN) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申请单不在待审批状态");
        }

        BookingStatus from = booking.getStatus();
        if (approve) {
            // 锁设备行串行化同设备并发终审，避免两条同槽单同时通过
            assetMapper.selectByIdForUpdate(booking.getAssetId());
            requireNoOverlap(booking);
            booking.setStatus(BookingStatus.APPROVED);
            bookingMapper.updateById(booking);
            writeEvent(booking.getDocumentId(), "APPROVE", from.name(), BookingStatus.APPROVED.name(),
                    reviewerId, "USER", request.comment());
        } else {
            booking.setStatus(BookingStatus.REJECTED);
            bookingMapper.updateById(booking);
            writeEvent(booking.getDocumentId(), "REJECT", from.name(), BookingStatus.REJECTED.name(),
                    reviewerId, "USER", request.comment());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkout(String id) {
        long operatorId = StpUtil.getLoginIdAsLong();
        advanceOverdue();
        // 锁序与申请一致：先设备行后申请单，避免死锁
        EquipmentBooking booking = bookingMapper.selectByIdForUpdate(Ids.parse(id));
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申请单不存在");
        }
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(booking.getAssetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅审批通过的申请可登记借出");
        }
        if (!booking.getEndAt().isAfter(Instant.now())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预约时段已过期，不可借出");
        }
        if (asset.getStatus() != EquipmentStatus.IDLE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备不在架，不可借出");
        }
        if (bookingMapper.countOverlap(asset.getId(), booking.getStartAt(), booking.getEndAt(), booking.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备时段已被其他在用申请占用");
        }
        if (usageMapper.selectByBookingId(booking.getId()) != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该申请已登记过借出");
        }

        // 同事务：借出流水 + 单据状态 + 设备置借出中
        EquipmentUsage usage = new EquipmentUsage();
        usage.setBookingId(booking.getId());
        usage.setBorrowerId(booking.getApplicantId());
        usage.setCheckoutOperatorId(operatorId);
        usage.setCheckedOutAt(Instant.now());
        usage.setDueAt(booking.getEndAt());
        usage.setStatus("CHECKED_OUT");
        usageMapper.insert(usage);

        booking.setStatus(BookingStatus.CHECKED_OUT);
        bookingMapper.updateById(booking);

        asset.setStatus(EquipmentStatus.IN_USE);
        assetMapper.updateById(asset);

        writeEvent(booking.getDocumentId(), "CHECKOUT", BookingStatus.APPROVED.name(),
                BookingStatus.CHECKED_OUT.name(), operatorId, "USER", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnAsset(String id, BookingReturnRequest request) {
        long operatorId = StpUtil.getLoginIdAsLong();
        advanceOverdue();
        EquipmentBooking booking = bookingMapper.selectByIdForUpdate(Ids.parse(id));
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申请单不存在");
        }
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(booking.getAssetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        if (booking.getStatus() != BookingStatus.CHECKED_OUT
                && booking.getStatus() != BookingStatus.OVERDUE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申请单不在借出状态");
        }
        EquipmentUsage usage = usageMapper.selectByBookingId(booking.getId());
        if (usage == null || !"CHECKED_OUT".equals(usage.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借出流水缺失或已归还");
        }
        boolean damaged = request.condition() == ReturnCondition.DAMAGED;
        if (damaged && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "归还损坏必须填写备注说明");
        }
        BookingStatus from = booking.getStatus();

        usage.setReturnOperatorId(operatorId);
        usage.setReturnedAt(Instant.now());
        usage.setReturnCondition(request.condition());
        usage.setReturnNote(request.note());
        usage.setStatus("RETURNED");
        usageMapper.updateById(usage);

        booking.setStatus(BookingStatus.RETURNED);
        bookingMapper.updateById(booking);

        // 完好恢复空闲；损坏转维护中（走线下维修回填）
        asset.setStatus(damaged ? EquipmentStatus.MAINTAINING : EquipmentStatus.IDLE);
        assetMapper.updateById(asset);

        writeEvent(booking.getDocumentId(), "RETURN", from.name(),
                BookingStatus.RETURNED.name(), operatorId, "USER", request.note());
    }

    @Override
    public List<TeacherOptionVO> teacherOptions() {
        return bookingMapper.selectTeacherOptions();
    }

    /* ---------------- 内部工具 ---------------- */

    /**
     * 惰性推进逾期未还单：到期仍未归还的 CHECKED_OUT 单置 OVERDUE，
     * 并对新增逾期单幂等冻结申请人 7 天申请权限（唯一键防重复，不延长）。
     * 在申请/列表/借出/归还路径的入口调用，无需定时任务。
     */
    private void advanceOverdue() {
        List<EquipmentBooking> candidates = bookingMapper.selectOverdueCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        bookingMapper.advanceOverdueByIds(
                candidates.stream().map(EquipmentBooking::getId).toList());
        for (EquipmentBooking booking : candidates) {
            restrictionMapper.insertOverdueFreeze(IdWorker.getId(), booking.getApplicantId(), booking.getDocumentId());
        }
    }

    private void requireNoOverlap(EquipmentBooking booking) {
        if (bookingMapper.countOverlap(booking.getAssetId(), booking.getStartAt(), booking.getEndAt(),
                booking.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该时段设备已被其他申请占用，无法通过");
        }
    }

    private EquipmentBooking requireBooking(Long bookingId) {
        EquipmentBooking booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申请单不存在");
        }
        return booking;
    }

    private SysUser requireEnabledUser(Long userId, String message) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), message);
        }
        return user;
    }

    /** 单据事件留痕（不可变历史；request_key 唯一防重复写入） */
    private void writeEvent(Long documentId, String eventType, String fromStatus, String toStatus,
                            Long actorId, String actorType, String reason) {
        DocumentEvent event = new DocumentEvent();
        event.setDocumentId(documentId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setOccurredAt(Instant.now());
        event.setReason(reason);
        event.setRequestKey(UUID.randomUUID().toString());
        eventMapper.insert(event);
    }

    private Page<BookingVO> toPage(BookingPageQuery query) {
        return PageUtils.toPage(query);
    }
}

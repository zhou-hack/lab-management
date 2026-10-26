package com.labmanage.module.booking.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingCheckoutDTO;
import com.labmanage.module.booking.dto.BookingCreateDTO;
import com.labmanage.module.booking.dto.BookingPageQuery;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.entity.ClassPeriod;
import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.flow.entity.BizDocument;
import com.labmanage.module.booking.flow.enums.DocumentEventType;
import com.labmanage.module.booking.flow.service.BookingFlowService;
import com.labmanage.module.booking.flow.vo.BookingTimelineEventVO;
import com.labmanage.module.booking.mapper.ClassPeriodMapper;
import com.labmanage.module.booking.mapper.LabBookingMapper;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import com.labmanage.module.booking.service.BookingExpiryService;
import com.labmanage.module.booking.service.BookingService;
import com.labmanage.module.booking.vo.AvailabilityVO;
import com.labmanage.module.booking.vo.BookingDetailVO;
import com.labmanage.module.booking.vo.BookingStatsVO;
import com.labmanage.module.booking.vo.BookingVO;
import com.labmanage.module.booking.vo.LabOccupancyVO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场地预约业务实现。
 * <p>
 * 权级流程：教师发起（PENDING/待管理员核对）→ 管理员核对批准（APPROVED，
 * 场地行锁内写占用）/整单驳回（REJECTED，原因详情可见）→ 开门签到
 * （CHECKIN 事件，IN_USE 派生）→ 结束清点（CHECKOUT 事件，FINISHED 派生）；
 * 本人可撤销；start_time 已过未核对由定时任务自动取消。
 * 冲突协议（数据库设计 8.1）：半开区间重叠，仅 released_at IS NULL 的 lab_slot
 * 参与；提交与审批双重校验，审批事务先 SELECT ... FOR UPDATE 锁场地行防并发双写。
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    /** 业务时区：页面展示与开放窗口按上海日历，时间点统一 UTC 传输存储 */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;
    private final ClassPeriodMapper periodMapper;
    private final BizSpaceMapper spaceMapper;
    private final SysUserMapper userMapper;
    private final BookingFlowService flowService;
    private final BookingExpiryService expiryService;

    public BookingServiceImpl(LabBookingMapper bookingMapper,
                              LabSlotMapper slotMapper,
                              ClassPeriodMapper periodMapper,
                              BizSpaceMapper spaceMapper,
                              SysUserMapper userMapper,
                              BookingFlowService flowService,
                              BookingExpiryService expiryService) {
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
        this.periodMapper = periodMapper;
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
        this.expiryService = expiryService;
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailVO create(BookingCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[预约提交] 开始：操作人={}, 场地ID={}, 时段=[{} ~ {}], 人数={}, 节次=[{}~{}]",
                operatorId, dto.spaceId(), dto.startAt(), dto.endAt(), dto.participantCount(),
                dto.startPeriodId(), dto.endPeriodId());

        // 学生纯只读：禁止发起（前端按钮已按权限隐藏，此处为服务端兜底 40300）
        requireNonStudent(operatorId, "发起场地预约");

        BizSpace space = requireSpace(dto.spaceId());
        if (space.getStatus() != SpaceStatus.AVAILABLE) {
            log.warn("[预约提交] 场地当前状态不可预约：操作人={}, 场地ID={}, 状态={}",
                    operatorId, dto.spaceId(), space.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地当前状态为 " + space.getStatus().getDisplayName() + "，不可预约");
        }
        if (dto.participantCount() > space.getCapacity()) {
            log.warn("[预约提交] 参与人数超过场地容量：操作人={}, 场地ID={}, 人数={}, 容量={}",
                    operatorId, dto.spaceId(), dto.participantCount(), space.getCapacity());
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "参与人数超过场地容量（" + space.getCapacity() + " 人）");
        }

        Instant now = Instant.now();
        if (!dto.startAt().isAfter(now)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "预约开始时间必须晚于当前时间");
        }
        validateWindow(space, dto.startAt(), dto.endAt());
        validatePeriodConsistency(dto);

        long conflicts = slotMapper.countConflict(dto.spaceId(), dto.startAt(), dto.endAt());
        if (conflicts > 0) {
            log.warn("[预约提交] 所选时段已被占用：操作人={}, 场地ID={}, 时段=[{} ~ {}], 冲突占用数={}",
                    operatorId, dto.spaceId(), dto.startAt(), dto.endAt(), conflicts);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "所选时段已被占用，请调整起止时间");
        }

        LabBooking booking = new LabBooking();
        booking.setLabId(dto.spaceId());
        booking.setApplicantId(operatorId);
        booking.setBookingDate(dto.bookingDate());
        booking.setStartPeriodId(dto.startPeriodId());
        booking.setEndPeriodId(dto.endPeriodId());
        booking.setCourseName(trimToNull(dto.courseName()));
        booking.setClassName(trimToNull(dto.className()));
        booking.setTeacherId(dto.teacherId());
        booking.setPurpose(dto.purpose().trim());
        booking.setParticipantCount(dto.participantCount());
        booking.setStartAt(dto.startAt());
        booking.setEndAt(dto.endAt());
        booking.setStatus(BookingStatus.PENDING);
        bookingMapper.insert(booking);

        // 权级流程建档：统一单据 + ADMIN_REVIEW 待核对任务 + SUBMIT 事件
        SysUser applicant = userMapper.selectById(operatorId);
        Long deptId = applicant == null ? null : applicant.getDepartmentId();
        flowService.onSubmit(booking, deptId);

        log.info("[预约提交] 完成：操作人={}, 预约ID={}, 场地={}, 状态=PENDING(待管理员核对), 耗时={}ms",
                operatorId, booking.getId(), space.getLabName(), (System.nanoTime() - begin) / 1_000_000);
        return detailEntity(booking.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, BookingCancelDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[预约撤销] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (!booking.getApplicantId().equals(operatorId)) {
            log.warn("[预约撤销] 非申请人本人，拒绝：操作人={}, 预约ID={}, 申请人={}",
                    operatorId, id, booking.getApplicantId());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅申请人本人可取消预约");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.APPROVED) {
            log.warn("[预约撤销] 当前状态不可取消：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待审批或已批准的预约可取消，当前状态：" + booking.getStatus().getDisplayName());
        }
        // 已开门签到（IN_USE 使用中）的单不允许撤销：撤销会立即释放占用槽并跳过结束清点，
        // 导致系统显示空闲而实际仍在使用（清单 B22）。已签到单须先由管理员结束清点。
        if (booking.getStatus() == BookingStatus.APPROVED) {
            BizDocument doc = requireFlowDocument(id);
            if (flowService.hasEvent(doc.getId(), DocumentEventType.CHECKIN)) {
                log.warn("[预约撤销] 已完成开门签到，拒绝撤销：操作人={}, 预约ID={}", operatorId, id);
                throw new BusinessException(ResultCode.CONFLICT.getCode(),
                        "该预约已完成开门签到（使用中），请先由管理员结束清点后，再行取消");
            }
        }

        String reason = dto == null ? null : dto.reason();
        doCancel(booking, operatorId, CancelType.MANUAL, reason);
        log.info("[预约撤销] 完成：操作人={}, 预约ID={}", operatorId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[预约核对批准] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (booking.getApplicantId().equals(operatorId)) {
            log.warn("[预约核对批准] 申请人不审自己的单，拒绝：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "申请人不审自己的单，请交由其他管理员核对");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("[预约核对批准] 状态非待核对，拒绝：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待核对的预约可批准，当前状态：" + booking.getStatus().getDisplayName());
        }
        BizDocument doc = requireFlowDocument(id);

        // 场地行锁：串行化同场地审批（文档 8.1），锁内复核场地状态与时段冲突
        BizSpace space = spaceMapper.selectByIdForUpdate(booking.getLabId());
        if (space == null) {
            log.warn("[预约核对批准] 场地不存在或已删除：操作人={}, 预约ID={}, 场地ID={}",
                    operatorId, id, booking.getLabId());
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (space.getStatus() != SpaceStatus.AVAILABLE) {
            log.warn("[预约核对批准] 场地当前状态不可批准：操作人={}, 预约ID={}, 场地={}, 状态={}",
                    operatorId, id, space.getLabName(), space.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地当前状态为 " + space.getStatus().getDisplayName() + "，无法批准");
        }
        long conflicts = slotMapper.countConflict(booking.getLabId(), booking.getStartAt(), booking.getEndAt());
        if (conflicts > 0) {
            log.warn("[预约核对批准] 时段冲突，拒绝：操作人={}, 预约ID={}, 场地ID={}, 冲突占用数={}",
                    operatorId, id, booking.getLabId(), conflicts);
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "时段已被其他预约占用（提交时无冲突，审批时被抢占），请驳回或要求调整时间");
        }

        LabSlot slot = new LabSlot();
        slot.setLabId(booking.getLabId());
        slot.setBookingId(booking.getId());
        slot.setStartAt(booking.getStartAt());
        slot.setEndAt(booking.getEndAt());
        slotMapper.insert(slot);

        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, id)
                .eq(LabBooking::getStatus, BookingStatus.PENDING)
                .set(LabBooking::getStatus, BookingStatus.APPROVED)
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约核对批准] 更新未命中（预约状态已变更）：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预约状态已变更，请刷新后重试");
        }

        // 流程记录：审批任务 APPROVED + APPROVE 事件
        flowService.onApprove(doc, booking, operatorId);
        log.info("[预约核对批准] 完成：操作人={}, 预约ID={}, 场地={}, 占用ID={}, 耗时={}ms",
                operatorId, id, space.getLabName(), slot.getId(), (System.nanoTime() - begin) / 1_000_000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, BookingRejectDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[预约整单驳回] 开始：操作人={}, 预约ID={}, 原因={}", operatorId, id, dto.reason());

        LabBooking booking = requireBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("[预约整单驳回] 状态非待核对，拒绝：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待核对的预约可驳回，当前状态：" + booking.getStatus().getDisplayName());
        }
        BizDocument doc = requireFlowDocument(id);

        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, id)
                .eq(LabBooking::getStatus, BookingStatus.PENDING)
                .set(LabBooking::getStatus, BookingStatus.REJECTED)
                .set(LabBooking::getRejectReason, dto.reason().trim())
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约整单驳回] 更新未命中（预约状态已变更）：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预约状态已变更，请刷新后重试");
        }

        flowService.onReject(doc, booking, operatorId, dto.reason().trim());
        log.info("[预约整单驳回] 完成：操作人={}, 预约ID={}, 申请人={}", operatorId, id, booking.getApplicantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[开门签到] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅已批准的预约可开门签到，当前状态：" + booking.getStatus().getDisplayName());
        }
        BizDocument doc = requireFlowDocument(id);
        if (flowService.hasEvent(doc.getId(), DocumentEventType.CHECKIN)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该预约已完成开门签到，请勿重复操作");
        }
        Instant now = Instant.now();
        if (now.isBefore(booking.getStartAt())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "尚未到达预约开始时间，不能开门签到");
        }

        flowService.onCheckin(doc, booking, operatorId, now);
        log.info("[开门签到] 完成：操作人={}, 预约ID={}, 签到时间={}", operatorId, id, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkout(Long id, BookingCheckoutDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[结束清点] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅已批准且已签到的预约可结束清点，当前状态：" + booking.getStatus().getDisplayName());
        }
        BizDocument doc = requireFlowDocument(id);
        if (!flowService.hasEvent(doc.getId(), DocumentEventType.CHECKIN)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "请先完成开门签到，再结束清点");
        }
        if (flowService.hasEvent(doc.getId(), DocumentEventType.CHECKOUT)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该预约已结束清点，请勿重复操作");
        }

        Instant now = Instant.now();
        String note = dto == null || dto.note() == null || dto.note().isBlank()
                ? "结束清点" : dto.note().trim();
        flowService.onCheckout(doc, booking, operatorId, now, note);

        // 清点结束释放占用槽（预约本身仍为 APPROVED，FINISHED 由事件/时间派生展示）
        int released = slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                .eq(LabSlot::getBookingId, id)
                .isNull(LabSlot::getReleasedAt)
                .set(LabSlot::getReleasedAt, now)
                .set(LabSlot::getReleaseReason, "结束清点")
                .set(LabSlot::getUpdateUserId, operatorId));
        log.info("[结束清点] 完成：操作人={}, 预约ID={}, 释放占用行数={}, 时间={}",
                operatorId, id, released, now);
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<BookingVO> pageMine(BookingPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        Page<LabBooking> page = bookingMapper.selectPage(PageUtils.toPage(query),
                buildWrapper(query).eq(LabBooking::getApplicantId, operatorId));
        PageResult<BookingVO> result = toPageResult(page);
        log.info("[我的预约] 完成：操作人={}, 页码={}, 页大小={}, 条件=[{}], 命中总数={}, 返回行数={}, 耗时={}ms",
                operatorId, query.getPageNum(), query.getPageSize(), buildFilterSummary(query),
                page.getTotal(), result.records().size(), (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    public PageResult<BookingVO> pageAll(BookingPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        Page<LabBooking> page = bookingMapper.selectPage(PageUtils.toPage(query), buildWrapper(query));
        PageResult<BookingVO> result = toPageResult(page);
        log.info("[全部预约] 完成：操作人={}, 页码={}, 页大小={}, 条件=[{}], 命中总数={}, 返回行数={}, 耗时={}ms",
                operatorId, query.getPageNum(), query.getPageSize(), buildFilterSummary(query),
                page.getTotal(), result.records().size(), (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    public PageResult<BookingVO> pageBoard(BookingPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        // 只读预约情况：全量可见（学生只读视角），不暴露任何写操作入口（前端按权限隐藏按钮）
        Page<LabBooking> page = bookingMapper.selectPage(PageUtils.toPage(query), buildWrapper(query));
        PageResult<BookingVO> result = toPageResult(page);
        log.info("[预约情况只读] 完成：操作人={}, 页码={}, 页大小={}, 命中总数={}, 返回行数={}, 耗时={}ms",
                operatorId, query.getPageNum(), query.getPageSize(),
                page.getTotal(), result.records().size(), (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    public BookingDetailVO detail(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        LabBooking booking = requireBooking(id);
        boolean canViewAll = StpUtil.hasPermission("booking:lab:approve")
                || StpUtil.hasPermission("booking:lab:view");
        if (!booking.getApplicantId().equals(operatorId) && !canViewAll) {
            log.warn("[预约详情] 非申请人且无查看权限，拒绝：操作人={}, 预约ID={}, 申请人={}",
                    operatorId, id, booking.getApplicantId());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权查看该预约详情");
        }
        log.info("[预约详情] 完成：操作人={}, 预约ID={}, 状态={}", operatorId, id, booking.getStatus());
        return detailEntity(id);
    }

    @Override
    public AvailabilityVO availability(Long spaceId, LocalDate date) {
        Long operatorId = SecurityUtils.currentUserId();
        BizSpace space = requireSpace(spaceId);

        // 上海当天 [零点, 次日零点) 转 UTC 后查询未释放占用
        Instant dayStart = date.atStartOfDay(SHANGHAI).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        List<AvailabilityVO.OccupiedSegment> occupied = slotMapper
                .listOccupiedInRange(spaceId, dayStart, dayEnd).stream()
                .map(slot -> new AvailabilityVO.OccupiedSegment(
                        String.valueOf(slot.getBookingId()), slot.getStartAt(), slot.getEndAt()))
                .toList();

        log.info("[可用时段] 完成：操作人={}, 场地ID={}, 日期={}, 占用段数={}, 开放窗口={}~{}",
                operatorId, spaceId, date, occupied.size(), space.getOpenStart(), space.getOpenEnd());
        return new AvailabilityVO(
                String.valueOf(space.getId()),
                space.getLabName(),
                space.getCapacity(),
                formatTime(space.getOpenStart()),
                formatTime(space.getOpenEnd()),
                date.toString(),
                occupied
        );
    }

    @Override
    public List<LabOccupancyVO> occupancy(LocalDate date, Long startPeriodId, Long endPeriodId) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();

        // 窗口推导：起止节次齐传按上海节次时刻，均缺省为全天 [00:00, 次日00:00)
        boolean wholeDay = startPeriodId == null && endPeriodId == null;
        Instant winStart;
        Instant winEnd;
        if (wholeDay) {
            winStart = date.atStartOfDay(SHANGHAI).toInstant();
            winEnd = date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        } else {
            if (startPeriodId == null || endPeriodId == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次与结束节次须同时指定");
            }
            ClassPeriod startPeriod = periodMapper.selectById(startPeriodId);
            ClassPeriod endPeriod = periodMapper.selectById(endPeriodId);
            if (startPeriod == null || endPeriod == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次不存在或已停用");
            }
            if (startPeriod.getPeriodNo() > endPeriod.getPeriodNo()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次不能晚于结束节次");
            }
            winStart = date.atTime(startPeriod.getStartClock()).atZone(SHANGHAI).toInstant();
            winEnd = date.atTime(endPeriod.getEndClock()).atZone(SHANGHAI).toInstant();
        }

        List<BizSpace> spaces = spaceMapper.selectList(
                new LambdaQueryWrapper<BizSpace>().orderByAsc(BizSpace::getId));
        List<LabSlot> slots = slotMapper.listOccupiedInRangeAll(winStart, winEnd);
        // 窗口内全部占用段按场地分组（保持开始时间升序），供前端四态派生
        Map<Long, List<LabSlot>> slotsByLab = new HashMap<>();
        for (LabSlot slot : slots) {
            slotsByLab.computeIfAbsent(slot.getLabId(), k -> new java.util.ArrayList<>()).add(slot);
        }
        List<Long> bookingIds = slots.stream().map(LabSlot::getBookingId).distinct().toList();
        Map<Long, LabBooking> bookingMap = new HashMap<>();
        if (!bookingIds.isEmpty()) {
            for (LabBooking b : bookingMapper.selectBatchIds(bookingIds)) {
                bookingMap.put(b.getId(), b);
            }
        }
        // 占用单的老师与申请人姓名（teacherId 为空时前端回退展示申请人）
        List<Long> userIds = bookingMap.values().stream()
                .flatMap(b -> java.util.stream.Stream.of(b.getTeacherId(), b.getApplicantId()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : userMapper.selectBatchIds(userIds)) {
                userMap.put(user.getId(), user);
            }
        }

        List<LabOccupancyVO> result = spaces.stream()
                .map(space -> {
                    List<LabSlot> labSlots = slotsByLab.getOrDefault(space.getId(), List.of());
                    LabSlot first = labSlots.isEmpty() ? null : labSlots.get(0);
                    LabBooking booking = first == null ? null : bookingMap.get(first.getBookingId());
                    SysUser teacher = booking != null && booking.getTeacherId() != null
                            ? userMap.get(booking.getTeacherId()) : null;
                    SysUser applicant = booking == null ? null : userMap.get(booking.getApplicantId());
                    boolean occupied = first != null && space.getStatus() == SpaceStatus.AVAILABLE;
                    List<LabOccupancyVO.OccupiedSlot> slotInfos = labSlots.stream()
                            .map(slot -> {
                                LabBooking b = bookingMap.get(slot.getBookingId());
                                SysUser slotTeacher = b != null && b.getTeacherId() != null
                                        ? userMap.get(b.getTeacherId()) : null;
                                SysUser slotApplicant = b == null ? null : userMap.get(b.getApplicantId());
                                return new LabOccupancyVO.OccupiedSlot(
                                        String.valueOf(slot.getBookingId()),
                                        slot.getStartAt(),
                                        slot.getEndAt(),
                                        b == null ? null : b.getCourseName(),
                                        slotTeacher == null ? null : slotTeacher.getRealName(),
                                        slotApplicant == null ? null : slotApplicant.getRealName()
                                );
                            })
                            .toList();
                    return new LabOccupancyVO(
                            String.valueOf(space.getId()),
                            space.getLabName(),
                            space.getLabType() == null ? null : space.getLabType().getCode(),
                            space.getLabType() == null ? null : space.getLabType().getDisplayName(),
                            space.getLocationDetail(),
                            space.getCapacity(),
                            formatTime(space.getOpenStart()),
                            formatTime(space.getOpenEnd()),
                            space.getStatus() == null ? null : space.getStatus().getCode(),
                            space.getStatus() == null ? null : space.getStatus().getDisplayName(),
                            occupied,
                            booking == null ? null : String.valueOf(booking.getId()),
                            booking == null ? null : booking.getCourseName(),
                            teacher == null ? null : teacher.getRealName(),
                            applicant == null ? null : applicant.getRealName(),
                            occupied ? first.getStartAt() : null,
                            occupied ? first.getEndAt() : null,
                            occupied ? slotInfos : List.of()
                    );
                })
                .toList();
        long occupiedCount = result.stream().filter(LabOccupancyVO::occupied).count();
        log.info("[场地占用总览] 完成：操作人={}, 日期={}, 节次=[{}~{}], 窗口=[{} ~ {}], 场地数={}, 占用数={}, 耗时={}ms",
                operatorId, date, startPeriodId, endPeriodId, winStart, winEnd,
                result.size(), occupiedCount, (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    public List<BookingVO> recent(Long spaceId) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        // 上海固定每日 24 小时档；时间点统一 UTC：窗口 [now, now+24h)，重叠即命中
        Instant now = Instant.now();
        Instant horizon = now.plus(java.time.Duration.ofHours(24));
        List<LabBooking> records = bookingMapper.selectList(new LambdaQueryWrapper<LabBooking>()
                .eq(LabBooking::getLabId, spaceId)
                .in(LabBooking::getStatus, BookingStatus.PENDING, BookingStatus.APPROVED)
                .lt(LabBooking::getStartAt, horizon)
                .gt(LabBooking::getEndAt, now)
                .orderByAsc(LabBooking::getStartAt)
                .last("LIMIT 100"));
        Map<Long, BizSpace> spaces = loadSpaces(records);
        Map<Long, SysUser> users = loadUsers(records);
        Map<Long, SysUser> teachers = loadTeachers(records);
        Map<Long, ClassPeriod> periods = loadPeriods(records);
        Map<Long, Map<String, Instant>> checkTimes =
                flowService.loadCheckTimes(records.stream().map(LabBooking::getId).toList());
        List<BookingVO> result = records.stream()
                .map(b -> {
                    Map<String, Instant> ct = checkTimes.get(b.getId());
                    Instant checkin = ct == null ? null : ct.get("CHECKIN");
                    Instant checkout = ct == null ? null : ct.get("CHECKOUT");
                    SysUser teacher = b.getTeacherId() == null ? null : teachers.get(b.getTeacherId());
                    return toVO(b, spaces.get(b.getLabId()), users.get(b.getApplicantId()),
                            teacher, periods, now, checkin, checkout);
                })
                .toList();
        log.info("[场地近期预约] 完成：操作人={}, 场地ID={}, 窗口=[{} ~ +24h], 返回条数={}, 耗时={}ms",
                operatorId, spaceId, now, result.size(), (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    public byte[] exportBookings(BookingPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        // 与分页同条件，去掉分页上限按导出上限 10000 行截断
        List<LabBooking> records = bookingMapper.selectList(buildWrapper(query).last("LIMIT 10000"));
        Map<Long, BizSpace> spaces = loadSpaces(records);
        Map<Long, SysUser> users = loadUsers(records);
        Map<Long, SysUser> teachers = loadTeachers(records);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("预约ID,场地,申请人,课程,班级,老师,事由,人数,预约日期,开始时间,结束时间,状态,驳回原因,提交时间\r\n");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm").withZone(SHANGHAI);
        for (LabBooking b : records) {
            BizSpace space = spaces.get(b.getLabId());
            SysUser applicant = users.get(b.getApplicantId());
            SysUser teacher = b.getTeacherId() == null ? null : teachers.get(b.getTeacherId());
            sb.append(b.getId()).append(',')
                    .append(csvCell(space == null ? null : space.getLabName())).append(',')
                    .append(csvCell(applicant == null ? null : applicant.getRealName())).append(',')
                    .append(csvCell(b.getCourseName())).append(',')
                    .append(csvCell(b.getClassName())).append(',')
                    .append(csvCell(teacher == null ? null : teacher.getRealName())).append(',')
                    .append(csvCell(b.getPurpose())).append(',')
                    .append(b.getParticipantCount()).append(',')
                    .append(b.getBookingDate() == null ? "" : b.getBookingDate()).append(',')
                    .append(fmt.format(b.getStartAt())).append(',')
                    .append(fmt.format(b.getEndAt())).append(',')
                    .append(b.getStatus() == null ? "" : b.getStatus().getDisplayName()).append(',')
                    .append(csvCell(b.getRejectReason())).append(',')
                    .append(fmt.format(b.getCreateTime())).append("\r\n");
        }
        log.info("[预约导出] 完成：操作人={}, 条件=[{}], 导出行数={}, 耗时={}ms",
                operatorId, buildFilterSummary(query), records.size(), (System.nanoTime() - begin) / 1_000_000);
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** CSV 单元格：含分隔符/引号/换行时按 RFC 4180 包裹转义 */
    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    @Override
    public BookingStatsVO stats() {
        Long operatorId = SecurityUtils.currentUserId();
        long pending = 0;
        long approved = 0;
        long rejected = 0;
        long cancelled = 0;
        for (Map<String, Object> row : bookingMapper.countGroupByStatus()) {
            long cnt = ((Number) row.get("cnt")).longValue();
            switch (String.valueOf(row.get("status"))) {
                case "PENDING" -> pending = cnt;
                case "APPROVED" -> approved = cnt;
                case "REJECTED" -> rejected = cnt;
                case "CANCELLED" -> cancelled = cnt;
                default -> log.warn("[预约统计] 未知状态不参与分档：status={}, cnt={}", row.get("status"), cnt);
            }
        }
        log.info("[预约统计] 完成：操作人={}, 总数={}, 待核对={}, 已批准={}, 已驳回={}, 已取消={}",
                operatorId, pending + approved + rejected + cancelled, pending, approved, rejected, cancelled);
        return new BookingStatsVO(pending + approved + rejected + cancelled, pending, approved, rejected, cancelled);
    }

    @Override
    public int autoCancelExpired() {
        Instant now = Instant.now();
        List<LabBooking> expired = bookingMapper.selectExpiredPending(now);
        if (expired.isEmpty()) {
            return 0;
        }
        int cancelled = 0;
        for (LabBooking booking : expired) {
            try {
                cancelled += expiryService.cancelOne(booking, now);
            } catch (Exception e) {
                // 单条失败不阻断整批；记录后继续，下一轮扫描会再次拾取仍 PENDING 的单
                log.error("[超时自动取消] 单条处理失败，跳过：预约ID={}, 错误={}", booking.getId(), e.getMessage(), e);
            }
        }
        log.info("[超时自动取消] 扫描完成：拾取={}, 实际取消={}, 扫描时间={}", expired.size(), cancelled, now);
        return cancelled;
    }

    // ------------------------------------------------------------- 组装/辅助

    private LambdaQueryWrapper<LabBooking> buildWrapper(BookingPageQuery query) {
        LambdaQueryWrapper<LabBooking> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(LabBooking::getStatus, query.getStatus());
        }
        if (query.getSpaceId() != null) {
            wrapper.eq(LabBooking::getLabId, query.getSpaceId());
        }
        if (query.getStartFrom() != null) {
            wrapper.ge(LabBooking::getStartAt, query.getStartFrom());
        }
        if (query.getStartTo() != null) {
            wrapper.le(LabBooking::getStartAt, query.getStartTo());
        }
        wrapper.orderByDesc(LabBooking::getCreateTime);
        return wrapper;
    }

    private PageResult<BookingVO> toPageResult(Page<LabBooking> page) {
        List<LabBooking> records = page.getRecords();
        Map<Long, BizSpace> spaces = loadSpaces(records);
        Map<Long, SysUser> users = loadUsers(records);
        Map<Long, SysUser> teachers = loadTeachers(records);
        Map<Long, ClassPeriod> periods = loadPeriods(records);
        Map<Long, Map<String, Instant>> checkTimes =
                flowService.loadCheckTimes(records.stream().map(LabBooking::getId).toList());
        Instant now = Instant.now();
        List<BookingVO> vos = records.stream()
                .map(b -> {
                    Map<String, Instant> ct = checkTimes.get(b.getId());
                    Instant checkin = ct == null ? null : ct.get("CHECKIN");
                    Instant checkout = ct == null ? null : ct.get("CHECKOUT");
                    SysUser teacher = b.getTeacherId() == null ? null : teachers.get(b.getTeacherId());
                    return toVO(b, spaces.get(b.getLabId()), users.get(b.getApplicantId()),
                            teacher, periods, now, checkin, checkout);
                })
                .toList();
        return PageResult.of(vos, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private Map<Long, BizSpace> loadSpaces(List<LabBooking> records) {
        List<Long> ids = records.stream().map(LabBooking::getLabId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BizSpace> map = new HashMap<>();
        for (BizSpace space : spaceMapper.selectBatchIds(ids)) {
            map.put(space.getId(), space);
        }
        return map;
    }

    private Map<Long, SysUser> loadUsers(List<LabBooking> records) {
        List<Long> ids = records.stream().map(LabBooking::getApplicantId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> map = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, SysUser> loadTeachers(List<LabBooking> records) {
        List<Long> ids = records.stream().map(LabBooking::getTeacherId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> map = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, ClassPeriod> loadPeriods(List<LabBooking> records) {
        List<Long> ids = new java.util.ArrayList<>();
        for (LabBooking b : records) {
            if (b.getStartPeriodId() != null) {
                ids.add(b.getStartPeriodId());
            }
            if (b.getEndPeriodId() != null) {
                ids.add(b.getEndPeriodId());
            }
        }
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClassPeriod> map = new HashMap<>();
        for (ClassPeriod p : periodMapper.selectPeriodsByIds(distinct)) {
            map.put(p.getId(), p);
        }
        return map;
    }

    private BookingVO toVO(LabBooking b, BizSpace space, SysUser applicant, SysUser teacher,
                           Map<Long, ClassPeriod> periods, Instant now,
                           Instant checkinAt, Instant checkoutAt) {
        ClassPeriod startPeriod = null;
        ClassPeriod endPeriod = null;
        if (periods != null) {
            if (b.getStartPeriodId() != null) {
                startPeriod = periods.get(b.getStartPeriodId());
            }
            if (b.getEndPeriodId() != null) {
                endPeriod = periods.get(b.getEndPeriodId());
            }
        }
        return new BookingVO(
                String.valueOf(b.getId()),
                String.valueOf(b.getLabId()),
                space == null ? null : space.getLabName(),
                b.getCourseName(),
                b.getClassName(),
                b.getTeacherId() == null ? null : String.valueOf(b.getTeacherId()),
                teacher == null ? null : teacher.getRealName(),
                b.getPurpose(),
                b.getParticipantCount(),
                b.getStartAt(),
                b.getEndAt(),
                b.getBookingDate(),
                startPeriod == null ? null : startPeriod.getPeriodName(),
                endPeriod == null ? null : endPeriod.getPeriodName(),
                b.getStatus(),
                b.getStatus() == null ? null : b.getStatus().getDisplayName(),
                runtimePhase(b, now, checkinAt, checkoutAt),
                String.valueOf(b.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                b.getRejectReason(),
                b.getCreateTime(),
                checkinAt,
                checkoutAt
        );
    }

    private BookingDetailVO detailEntity(Long id) {
        LabBooking b = requireBooking(id);
        BizSpace space = spaceMapper.selectById(b.getLabId());
        SysUser applicant = userMapper.selectById(b.getApplicantId());
        SysUser teacher = b.getTeacherId() == null ? null : userMapper.selectById(b.getTeacherId());
        ClassPeriod startPeriod = b.getStartPeriodId() == null ? null : periodMapper.selectById(b.getStartPeriodId());
        ClassPeriod endPeriod = b.getEndPeriodId() == null ? null : periodMapper.selectById(b.getEndPeriodId());
        List<BookingTimelineEventVO> timeline = flowService.timeline(id);
        Instant checkinAt = null;
        Instant checkoutAt = null;
        for (BookingTimelineEventVO e : timeline) {
            if ("CHECKIN".equals(e.eventType())) {
                checkinAt = e.occurredAt();
            } else if ("CHECKOUT".equals(e.eventType())) {
                checkoutAt = e.occurredAt();
            }
        }
        return new BookingDetailVO(
                String.valueOf(b.getId()),
                String.valueOf(b.getLabId()),
                space == null ? null : space.getLabName(),
                b.getCourseName(),
                b.getClassName(),
                b.getTeacherId() == null ? null : String.valueOf(b.getTeacherId()),
                teacher == null ? null : teacher.getRealName(),
                b.getPurpose(),
                b.getParticipantCount(),
                b.getStartAt(),
                b.getEndAt(),
                b.getBookingDate(),
                startPeriod == null ? null : startPeriod.getPeriodName(),
                endPeriod == null ? null : endPeriod.getPeriodName(),
                b.getStatus(),
                b.getStatus() == null ? null : b.getStatus().getDisplayName(),
                runtimePhase(b, Instant.now(), checkinAt, checkoutAt),
                b.getRejectReason(),
                b.getCancelReason(),
                b.getCancelType(),
                b.getCancelType() == null ? null : b.getCancelType().getDisplayName(),
                b.getCancelledAt(),
                String.valueOf(b.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                b.getCreateTime(),
                b.getUpdateTime(),
                checkinAt,
                checkoutAt,
                timeline
        );
    }

    /**
     * 派生展示字段（仅 APPROVED 有值）：
     * 已清点 → FINISHED；已签到 → IN_USE；否则按时间 UPCOMING/IN_USE/FINISHED。
     * 不落库，不参与状态机迁移。
     */
    private String runtimePhase(LabBooking b, Instant now, Instant checkinAt, Instant checkoutAt) {
        if (b.getStatus() != BookingStatus.APPROVED) {
            return null;
        }
        if (checkoutAt != null) {
            return "FINISHED";
        }
        if (checkinAt != null) {
            return "IN_USE";
        }
        if (now.isBefore(b.getStartAt())) {
            return "UPCOMING";
        }
        if (now.isBefore(b.getEndAt())) {
            return "IN_USE";
        }
        return "FINISHED";
    }

    /** 撤销/取消共用：按需释放占用 + 状态流转 CANCELLED + 流程事件。 */
    private void doCancel(LabBooking booking, Long operatorId, CancelType cancelType, String reason) {
        if (booking.getStatus() == BookingStatus.APPROVED) {
            Instant releaseAt = Instant.now();
            int released = slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                    .eq(LabSlot::getBookingId, booking.getId())
                    .isNull(LabSlot::getReleasedAt)
                    .set(LabSlot::getReleasedAt, releaseAt)
                    .set(LabSlot::getReleaseReason, cancelType == CancelType.MANUAL ? "MANUAL取消" : cancelType.name())
                    .set(LabSlot::getUpdateUserId, operatorId));
            log.info("[预约取消] 释放场地占用：预约ID={}, 释放行数={}, 释放时间={}", booking.getId(), released, releaseAt);
        }

        Instant cancelledAt = Instant.now();
        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, booking.getId())
                .in(LabBooking::getStatus, BookingStatus.PENDING, BookingStatus.APPROVED)
                .set(LabBooking::getStatus, BookingStatus.CANCELLED)
                .set(LabBooking::getCancelReason, reason)
                .set(LabBooking::getCancelType, cancelType)
                .set(LabBooking::getCancelledAt, cancelledAt)
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约取消] 更新未命中任何行：预约ID={}", booking.getId());
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预约状态已变更，请刷新后重试");
        }

        BizDocument doc = flowService.findByBookingId(booking.getId());
        if (doc != null) {
            boolean system = cancelType == CancelType.AUTO_EXPIRED;
            flowService.onCancel(doc, booking, system ? null : operatorId, system, reason);
        }
    }

    /** 学生纯只读：写操作身份门禁（前端按钮已按权限隐藏，此处为服务端兜底 40300） */
    private void requireNonStudent(Long operatorId, String action) {
        SysUser user = userMapper.selectById(operatorId);
        if (user != null && "STUDENT".equals(user.getUserType())) {
            log.warn("[{}] 学生禁止操作：操作人={}", action, operatorId);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "学生为只读身份，不能" + action);
        }
    }

    /**
     * 节次口径一致性校验：携带 bookingDate/节次时，须与 startAt/endAt 推导一致
     * （上海节次时刻 → UTC），保证时间轴三色小卡片与占用时段口径统一。
     */
    private void validatePeriodConsistency(BookingCreateDTO dto) {
        boolean hasPeriod = dto.bookingDate() != null || dto.startPeriodId() != null || dto.endPeriodId() != null;
        if (!hasPeriod) {
            return;
        }
        if (dto.bookingDate() == null || dto.startPeriodId() == null || dto.endPeriodId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "节次预约须同时指定日期、起始节次与结束节次");
        }
        ClassPeriod startPeriod = periodMapper.selectById(dto.startPeriodId());
        ClassPeriod endPeriod = periodMapper.selectById(dto.endPeriodId());
        if (startPeriod == null || endPeriod == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次不存在或已停用");
        }
        if (startPeriod.getPeriodNo() > endPeriod.getPeriodNo()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次不能晚于结束节次");
        }
        Instant expectStart = dto.bookingDate().atStartOfDay(SHANGHAI)
                .withHour(startPeriod.getStartClock().getHour())
                .withMinute(startPeriod.getStartClock().getMinute()).toInstant();
        Instant expectEnd = dto.bookingDate().atStartOfDay(SHANGHAI)
                .withHour(endPeriod.getEndClock().getHour())
                .withMinute(endPeriod.getEndClock().getMinute()).toInstant();
        if (!dto.startAt().equals(expectStart) || !dto.endAt().equals(expectEnd)) {
            log.warn("[预约提交] 节次时段与起止时间不一致：期望=[{}~{}], 实际=[{}~{}]",
                    expectStart, expectEnd, dto.startAt(), dto.endAt());
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "节次时段与所选起止时间不一致，请刷新后重试");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    /** 上海时区窗口校验：起止同日、不早于开放开始、不晚于开放结束 */
    private void validateWindow(BizSpace space, Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "预约开始时间必须早于结束时间");
        }
        LocalDate startDate = startAt.atZone(SHANGHAI).toLocalDate();
        LocalDate endDate = endAt.atZone(SHANGHAI).toLocalDate();
        if (!startDate.equals(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "预约起止时间须在同一天（按上海日期）");
        }
        LocalTime startLocal = startAt.atZone(SHANGHAI).toLocalTime();
        LocalTime endLocal = endAt.atZone(SHANGHAI).toLocalTime();
        if (startLocal.isBefore(space.getOpenStart()) || endLocal.isAfter(space.getOpenEnd())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "预约时段须在场地开放时间（" + formatTime(space.getOpenStart()) + "~"
                            + formatTime(space.getOpenEnd()) + "）内");
        }
    }

    private BizSpace requireSpace(Long id) {
        BizSpace space = spaceMapper.selectById(id);
        if (space == null) {
            log.warn("[预约查询] 场地不存在或已删除：场地ID={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return space;
    }

    private LabBooking requireBooking(Long id) {
        LabBooking booking = bookingMapper.selectById(id);
        if (booking == null) {
            log.warn("[预约查询] 预约不存在或已删除：预约ID={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return booking;
    }

    private BizDocument requireFlowDocument(Long bookingId) {
        BizDocument doc = flowService.findByBookingId(bookingId);
        if (doc == null) {
            log.warn("[预约流程] 流程单据缺失：预约ID={}", bookingId);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "预约流程档案缺失，无法执行该操作");
        }
        return doc;
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    /** 分页查询条件摘要（仅拼非空条件，用于日志排查） */
    private String buildFilterSummary(BookingPageQuery query) {
        StringBuilder sb = new StringBuilder();
        if (query.getStatus() != null) {
            sb.append("状态=").append(query.getStatus()).append(", ");
        }
        if (query.getSpaceId() != null) {
            sb.append("场地ID=").append(query.getSpaceId()).append(", ");
        }
        if (query.getStartFrom() != null) {
            sb.append("开始>=").append(query.getStartFrom()).append(", ");
        }
        if (query.getStartTo() != null) {
            sb.append("开始<=").append(query.getStartTo()).append(", ");
        }
        if (sb.isEmpty()) {
            return "无条件";
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}

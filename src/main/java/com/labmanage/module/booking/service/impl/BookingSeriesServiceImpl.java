package com.labmanage.module.booking.service.impl;

import com.labmanage.common.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.dto.SeriesCreateDTO;
import com.labmanage.module.booking.dto.SeriesPageQuery;
import com.labmanage.module.booking.entity.ClassPeriod;
import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabBookingSeries;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.enums.SeriesStatus;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.booking.flow.service.BookingFlowService;
import com.labmanage.module.booking.mapper.ClassPeriodMapper;
import com.labmanage.module.booking.mapper.LabBookingMapper;
import com.labmanage.module.booking.mapper.LabBookingSeriesMapper;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import com.labmanage.module.booking.service.BookingSeriesService;
import com.labmanage.module.booking.vo.PeriodVO;
import com.labmanage.module.booking.vo.SeriesDetailVO;
import com.labmanage.module.booking.vo.SeriesVO;
import com.labmanage.module.booking.vo.TeacherOptionVO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 场地节次时间轴与批量周期预约业务实现。
 * <p>
 * 周期预约交易协议（与单次预约一致，数据库设计 8.1）：
 * a) 提交期逐段预检冲突（仅未释放 lab_slot 参与），命中即 40900 单独列出日期；
 * b) 批准期在场地行锁（SELECT ... FOR UPDATE）内重检全部段，冲突整单拒绝，
 *    不部分占用；通过后逐周写 lab_booking（APPROVED）+ lab_slot 占用 + 流程档案；
 * c) 幂等键（申请人+key 唯一）防重复提交。
 */
@Service
public class BookingSeriesServiceImpl implements BookingSeriesService {

    private static final Logger log = LoggerFactory.getLogger(BookingSeriesServiceImpl.class);

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEKDAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final LabBookingSeriesMapper seriesMapper;
    private final ClassPeriodMapper periodMapper;
    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;
    private final BizSpaceMapper spaceMapper;
    private final SysUserMapper userMapper;
    private final BookingFlowService flowService;

    public BookingSeriesServiceImpl(LabBookingSeriesMapper seriesMapper,
                                    ClassPeriodMapper periodMapper,
                                    LabBookingMapper bookingMapper,
                                    LabSlotMapper slotMapper,
                                    BizSpaceMapper spaceMapper,
                                    SysUserMapper userMapper,
                                    BookingFlowService flowService) {
        this.seriesMapper = seriesMapper;
        this.periodMapper = periodMapper;
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
    }

    // ------------------------------------------------------------------ 节次

    @Override
    public List<PeriodVO> listPeriods() {
        Long operatorId = SecurityUtils.currentUserId();
        List<PeriodVO> result = periodMapper.listEnabled().stream()
                .map(p -> new PeriodVO(String.valueOf(p.getId()), p.getPeriodNo(), p.getPeriodName(),
                        formatTime(p.getStartClock()), formatTime(p.getEndClock())))
                .toList();
        log.info("[节次列表] 完成：操作人={}, 返回条数={}", operatorId, result.size());
        return result;
    }

    @Override
    public List<TeacherOptionVO> listTeachers() {
        Long operatorId = SecurityUtils.currentUserId();
        List<TeacherOptionVO> result = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "TEACHER")
                        .eq(SysUser::getEnabled, 1)
                        .orderByAsc(SysUser::getRealName))
                .stream()
                .map(u -> new TeacherOptionVO(String.valueOf(u.getId()), u.getRealName()))
                .toList();
        log.info("[上课老师选项] 完成：操作人={}, 返回条数={}", operatorId, result.size());
        return result;
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesDetailVO create(SeriesCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[周期预约提交] 开始：操作人={}, 场地ID={}, 星期={}, 节次=[{}~{}], 周期=[{}~{}]",
                operatorId, dto.spaceId(), dto.weekday(), dto.startPeriodId(), dto.endPeriodId(),
                dto.cycleStartDate(), dto.cycleEndDate());

        requireBookableIdentity(operatorId, "发起周期预约");
        if (dto.cycleStartDate().isAfter(dto.cycleEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "周期开始日期不能晚于结束日期");
        }
        BizSpace space = requireSpace(dto.spaceId());
        if (space.getStatus() != SpaceStatus.AVAILABLE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地当前状态为 " + space.getStatus().getDisplayName() + "，不可预约");
        }
        if (dto.participantCount() > space.getCapacity()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "参与人数超过场地容量（" + space.getCapacity() + " 人）");
        }
        Map<Long, ClassPeriod> periods = loadPeriodMap(List.of(dto.startPeriodId(), dto.endPeriodId()));
        ClassPeriod startPeriod = periods.get(dto.startPeriodId());
        ClassPeriod endPeriod = periods.get(dto.endPeriodId());
        if (startPeriod == null || endPeriod == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次不存在或已停用");
        }
        if (startPeriod.getPeriodNo() > endPeriod.getPeriodNo()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次不能晚于结束节次");
        }

        // 幂等键防重复提交
        if (seriesMapper.countByApplicantAndKey(operatorId, dto.idempotencyKey().trim()) > 0) {
            log.warn("[周期预约提交] 幂等键重复：操作人={}, key={}", operatorId, dto.idempotencyKey());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "该幂等键已提交过周期预约，请检查是否重复提交");
        }

        List<Segment> segments = buildSegments(dto.cycleStartDate(), dto.cycleEndDate(), dto.weekday(),
                startPeriod, endPeriod, space);
        if (segments.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "周期内没有匹配该星期的日期，请调整周期");
        }
        checkConflicts(space.getId(), segments, "提交");

        LabBookingSeries series = new LabBookingSeries();
        series.setLabId(dto.spaceId());
        series.setApplicantId(operatorId);
        series.setCourseName(trimToNull(dto.courseName()));
        series.setClassName(trimToNull(dto.className()));
        series.setTeacherId(dto.teacherId());
        series.setPurpose(dto.purpose().trim());
        series.setParticipantCount(dto.participantCount());
        series.setWeekday(dto.weekday());
        series.setStartPeriodId(dto.startPeriodId());
        series.setEndPeriodId(dto.endPeriodId());
        series.setCycleStartDate(dto.cycleStartDate());
        series.setCycleEndDate(dto.cycleEndDate());
        series.setStatus(SeriesStatus.PENDING);
        series.setIdempotencyKey(dto.idempotencyKey().trim());
        try {
            seriesMapper.insert(series);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：预检查通过后仍有极小窗口两条同时插入，唯一键兜底，
            // 统一转 409（避免落成 500 内部错误）
            log.warn("[周期预约提交] 幂等键并发冲突：操作人={}, key={}", operatorId, dto.idempotencyKey());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "该幂等键已提交过周期预约（重复提交），请刷新后重试");
        }

        SysUser applicant = userMapper.selectById(operatorId);
        flowService.onSeriesSubmit(series, applicant == null ? null : applicant.getDepartmentId());

        log.info("[周期预约提交] 完成：操作人={}, 规则ID={}, 场地={}, 段数={}, 状态=PENDING(待管理员核对), 耗时={}ms",
                operatorId, series.getId(), space.getLabName(), segments.size(),
                (System.nanoTime() - begin) / 1_000_000);
        return detailEntity(series.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[周期预约批准] 开始：操作人={}, 规则ID={}", operatorId, id);

        LabBookingSeries series = requireSeries(id);
        if (series.getApplicantId().equals(operatorId)) {
            log.warn("[周期预约批准] 申请人不审自己的单：操作人={}, 规则ID={}", operatorId, id);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "申请人不审自己的单，请交由其他管理员核对");
        }
        if (series.getStatus() != SeriesStatus.PENDING) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待核对的周期预约可批准，当前状态：" + series.getStatus().getDisplayName());
        }
        BizDocument doc = requireSeriesDocument(id);

        // 场地行锁串行化同场地批准；锁内复核场地状态与各周时段冲突
        BizSpace space = spaceMapper.selectByIdForUpdate(series.getLabId());
        if (space == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (space.getStatus() != SpaceStatus.AVAILABLE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地当前状态为 " + space.getStatus().getDisplayName() + "，无法批准");
        }
        // 锁内复核状态：并发批准时先到者已改 APPROVED，后者在此拦截，
        // 避免生成预约单后再回滚（确定性失败，不做无效写）
        LabBookingSeries fresh = seriesMapper.selectById(id);
        if (fresh == null || fresh.getStatus() != SeriesStatus.PENDING) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "周期预约状态已变更，请刷新后重试");
        }
        Map<Long, ClassPeriod> periods = loadPeriodMap(List.of(series.getStartPeriodId(), series.getEndPeriodId()));
        ClassPeriod startPeriod = periods.get(series.getStartPeriodId());
        ClassPeriod endPeriod = periods.get(series.getEndPeriodId());
        if (startPeriod == null || endPeriod == null) {
            // 提交后被停用/删除的节次：宁可整单驳回，也不生成失效占用
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "起始/结束节次已不存在，无法批准，请驳回该周期预约");
        }
        List<Segment> segments = buildSegments(series.getCycleStartDate(), series.getCycleEndDate(),
                series.getWeekday(), startPeriod, endPeriod, space);
        checkConflicts(space.getId(), segments, "批准");

        // 逐周生成预约单 + 占用槽 + 流程档案（创建即 APPROVED）
        SysUser applicant = userMapper.selectById(series.getApplicantId());
        Long deptId = applicant == null ? null : applicant.getDepartmentId();
        for (Segment seg : segments) {
            LabBooking booking = new LabBooking();
            booking.setLabId(series.getLabId());
            booking.setApplicantId(series.getApplicantId());
            booking.setBookingDate(seg.date());
            booking.setStartPeriodId(series.getStartPeriodId());
            booking.setEndPeriodId(series.getEndPeriodId());
            booking.setSeriesId(series.getId());
            booking.setCourseName(series.getCourseName());
            booking.setClassName(series.getClassName());
            booking.setTeacherId(series.getTeacherId());
            booking.setPurpose(series.getPurpose());
            booking.setParticipantCount(series.getParticipantCount());
            booking.setStartAt(seg.startAt());
            booking.setEndAt(seg.endAt());
            booking.setStatus(BookingStatus.APPROVED);
            bookingMapper.insert(booking);

            LabSlot slot = new LabSlot();
            slot.setLabId(series.getLabId());
            slot.setBookingId(booking.getId());
            slot.setStartAt(seg.startAt());
            slot.setEndAt(seg.endAt());
            slotMapper.insert(slot);

            flowService.onGeneratedApproved(booking, operatorId, deptId);
        }

        int rows = seriesMapper.update(null, new LambdaUpdateWrapper<LabBookingSeries>()
                .eq(LabBookingSeries::getId, id)
                .eq(LabBookingSeries::getStatus, SeriesStatus.PENDING)
                .set(LabBookingSeries::getStatus, SeriesStatus.APPROVED)
                .set(LabBookingSeries::getUpdateUserId, operatorId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "周期预约状态已变更，请刷新后重试");
        }
        flowService.onSeriesApprove(doc, series, operatorId);
        log.info("[周期预约批准] 完成：操作人={}, 规则ID={}, 场地={}, 生成预约单数={}, 耗时={}ms",
                operatorId, id, space.getLabName(), segments.size(), (System.nanoTime() - begin) / 1_000_000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, BookingRejectDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[周期预约驳回] 开始：操作人={}, 规则ID={}, 原因={}", operatorId, id, dto.reason());

        LabBookingSeries series = requireSeries(id);
        if (series.getApplicantId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "申请人不审自己的单，请交由其他管理员核对");
        }
        if (series.getStatus() != SeriesStatus.PENDING) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待核对的周期预约可驳回，当前状态：" + series.getStatus().getDisplayName());
        }
        BizDocument doc = requireSeriesDocument(id);

        int rows = seriesMapper.update(null, new LambdaUpdateWrapper<LabBookingSeries>()
                .eq(LabBookingSeries::getId, id)
                .eq(LabBookingSeries::getStatus, SeriesStatus.PENDING)
                .set(LabBookingSeries::getStatus, SeriesStatus.REJECTED)
                .set(LabBookingSeries::getRejectReason, dto.reason().trim())
                .set(LabBookingSeries::getUpdateUserId, operatorId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "周期预约状态已变更，请刷新后重试");
        }
        flowService.onSeriesReject(doc, series, operatorId, dto.reason().trim());
        log.info("[周期预约驳回] 完成：操作人={}, 规则ID={}", operatorId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, BookingCancelDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[周期预约撤销] 开始：操作人={}, 规则ID={}", operatorId, id);

        LabBookingSeries series = requireSeries(id);
        if (!series.getApplicantId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅申请人本人可取消周期预约");
        }
        if (series.getStatus() != SeriesStatus.PENDING && series.getStatus() != SeriesStatus.APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待审批或已批准的周期预约可取消，当前状态：" + series.getStatus().getDisplayName());
        }
        String reason = dto == null ? null : dto.reason();

        // 已批准：同步取消各周生成单并释放占用
        if (series.getStatus() == SeriesStatus.APPROVED) {
            int cancelled = 0;
            for (LabBooking booking : bookingMapper.selectBySeriesId(id)) {
                if (booking.getStatus() == BookingStatus.APPROVED) {
                    releaseSlot(booking.getId(), operatorId, "周期预约撤销");
                    updateBookingCancelled(booking.getId(), operatorId, reason, CancelType.MANUAL);
                    BizDocument bookingDoc = flowService.findByBookingId(booking.getId());
                    if (bookingDoc != null) {
                        flowService.onCancel(bookingDoc, booking, operatorId, false, "随周期预约撤销");
                    }
                    cancelled++;
                }
            }
            log.info("[周期预约撤销] 已撤销生成预约单数={}, 规则ID={}", cancelled, id);
        }

        Instant cancelledAt = Instant.now();
        int rows = seriesMapper.update(null, new LambdaUpdateWrapper<LabBookingSeries>()
                .eq(LabBookingSeries::getId, id)
                .in(LabBookingSeries::getStatus, SeriesStatus.PENDING, SeriesStatus.APPROVED)
                .set(LabBookingSeries::getStatus, SeriesStatus.CANCELLED)
                .set(LabBookingSeries::getCancelReason, reason)
                .set(LabBookingSeries::getCancelType, CancelType.MANUAL)
                .set(LabBookingSeries::getCancelledAt, cancelledAt)
                .set(LabBookingSeries::getUpdateUserId, operatorId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "周期预约状态已变更，请刷新后重试");
        }
        BizDocument doc = flowService.findBySeriesId(id);
        if (doc != null) {
            flowService.onSeriesCancel(doc, series, operatorId, false, reason == null ? "周期预约撤销" : reason);
        }
        log.info("[周期预约撤销] 完成：操作人={}, 规则ID={}", operatorId, id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int autoCancelExpired() {
        LocalDate today = LocalDate.now(SHANGHAI);
        List<LabBookingSeries> expired = seriesMapper.selectExpiredPending(today);
        if (expired.isEmpty()) {
            return 0;
        }
        int cancelled = 0;
        for (LabBookingSeries series : expired) {
            try {
                Instant now = Instant.now();
                int rows = seriesMapper.update(null, new LambdaUpdateWrapper<LabBookingSeries>()
                        .eq(LabBookingSeries::getId, series.getId())
                        .eq(LabBookingSeries::getStatus, SeriesStatus.PENDING)
                        .set(LabBookingSeries::getStatus, SeriesStatus.CANCELLED)
                        .set(LabBookingSeries::getCancelReason, "周期已结束仍未核对，系统自动取消")
                        .set(LabBookingSeries::getCancelType, CancelType.AUTO_EXPIRED)
                        .set(LabBookingSeries::getCancelledAt, now)
                        .set(LabBookingSeries::getUpdateUserId, null));
                if (rows == 0) {
                    continue;
                }
                BizDocument doc = flowService.findBySeriesId(series.getId());
                if (doc != null) {
                    flowService.onSeriesCancel(doc, series, null, true, "周期已结束仍未核对，系统自动取消");
                }
                cancelled++;
            } catch (Exception e) {
                log.error("[周期超时自动取消] 单条处理失败，跳过：规则ID={}, 错误={}", series.getId(), e.getMessage(), e);
            }
        }
        log.info("[周期超时自动取消] 扫描完成：拾取={}, 实际取消={}, 扫描日={}", expired.size(), cancelled, today);
        return cancelled;
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<SeriesVO> pageMine(SeriesPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        Page<LabBookingSeries> page = seriesMapper.selectPage(PageUtils.toPage(query),
                buildWrapper(query).eq(LabBookingSeries::getApplicantId, operatorId));
        PageResult<SeriesVO> result = toSeriesPage(page);
        log.info("[我的周期预约] 完成：操作人={}, 页码={}, 页大小={}, 命中总数={}",
                operatorId, query.getPageNum(), query.getPageSize(), page.getTotal());
        return result;
    }

    @Override
    public PageResult<SeriesVO> pageAll(SeriesPageQuery query) {
        Long operatorId = SecurityUtils.currentUserId();
        Page<LabBookingSeries> page = seriesMapper.selectPage(PageUtils.toPage(query), buildWrapper(query));
        PageResult<SeriesVO> result = toSeriesPage(page);
        log.info("[全部周期预约] 完成：操作人={}, 页码={}, 页大小={}, 命中总数={}",
                operatorId, query.getPageNum(), query.getPageSize(), page.getTotal());
        return result;
    }

    @Override
    public SeriesDetailVO detail(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        LabBookingSeries series = requireSeries(id);
        boolean canViewAll = SecurityUtils.hasPermission("booking:lab:approve")
                || SecurityUtils.hasPermission("booking:lab:view");
        if (!series.getApplicantId().equals(operatorId) && !canViewAll) {
            log.warn("[周期预约详情] 非申请本人且无查看权限，拒绝：操作人={}, 规则ID={}", operatorId, id);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权查看该周期预约详情");
        }
        log.info("[周期预约详情] 完成：操作人={}, 规则ID={}, 状态={}", operatorId, id, series.getStatus());
        return detailEntity(id);
    }

    // ------------------------------------------------------------- 组装/辅助

    private PageResult<SeriesVO> toSeriesPage(Page<LabBookingSeries> page) {
        List<LabBookingSeries> records = page.getRecords();
        Map<Long, BizSpace> spaces = loadSpaces(records);
        Map<Long, SysUser> users = loadUsers(records);
        Map<Long, ClassPeriod> periods = loadPeriodMap(records.stream()
                .flatMap(s -> java.util.stream.Stream.of(s.getStartPeriodId(), s.getEndPeriodId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, Long> bookedCounts = new HashMap<>();
        for (LabBookingSeries s : records) {
            bookedCounts.put(s.getId(), bookingMapper.countBySeriesId(s.getId()));
        }
        List<SeriesVO> vos = records.stream()
                .map(s -> toVO(s, spaces.get(s.getLabId()), users.get(s.getApplicantId()),
                        users.get(s.getTeacherId()), periods, bookedCounts.get(s.getId())))
                .toList();
        return PageResult.of(vos, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private SeriesVO toVO(LabBookingSeries s, BizSpace space, SysUser applicant, SysUser teacher,
                          Map<Long, ClassPeriod> periods, Long bookedCount) {
        return new SeriesVO(
                String.valueOf(s.getId()),
                String.valueOf(s.getLabId()),
                space == null ? null : space.getLabName(),
                s.getCourseName(),
                s.getClassName(),
                s.getTeacherId() == null ? null : String.valueOf(s.getTeacherId()),
                teacher == null ? null : teacher.getRealName(),
                s.getPurpose(),
                s.getParticipantCount(),
                s.getWeekday(),
                WEEKDAY_NAMES[s.getWeekday() == null ? 0 : Math.min(s.getWeekday(), 7)],
                nameOf(periods, s.getStartPeriodId()),
                nameOf(periods, s.getEndPeriodId()),
                s.getCycleStartDate(),
                s.getCycleEndDate(),
                weekCount(s),
                s.getStatus(),
                s.getStatus() == null ? null : s.getStatus().getDisplayName(),
                s.getRejectReason(),
                bookedCount == null ? 0 : bookedCount.intValue(),
                String.valueOf(s.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                s.getCreateTime()
        );
    }

    private SeriesDetailVO detailEntity(Long id) {
        LabBookingSeries s = requireSeries(id);
        BizSpace space = spaceMapper.selectById(s.getLabId());
        SysUser applicant = userMapper.selectById(s.getApplicantId());
        SysUser teacher = s.getTeacherId() == null ? null : userMapper.selectById(s.getTeacherId());
        Map<Long, ClassPeriod> periods = loadPeriodMap(List.of(s.getStartPeriodId(), s.getEndPeriodId()));
        List<LabBooking> generated = bookingMapper.selectBySeriesId(id);
        List<SeriesDetailVO.GeneratedBookingItem> items = generated.stream()
                .map(b -> new SeriesDetailVO.GeneratedBookingItem(
                        String.valueOf(b.getId()),
                        b.getBookingDate(),
                        formatRange(b.getStartAt(), b.getEndAt()),
                        b.getStatus() == null ? null : b.getStatus().name(),
                        b.getStatus() == null ? null : b.getStatus().getDisplayName()))
                .toList();
        return new SeriesDetailVO(
                String.valueOf(s.getId()),
                String.valueOf(s.getLabId()),
                space == null ? null : space.getLabName(),
                s.getCourseName(),
                s.getClassName(),
                s.getTeacherId() == null ? null : String.valueOf(s.getTeacherId()),
                teacher == null ? null : teacher.getRealName(),
                s.getPurpose(),
                s.getParticipantCount(),
                s.getWeekday(),
                WEEKDAY_NAMES[s.getWeekday() == null ? 0 : Math.min(s.getWeekday(), 7)],
                nameOf(periods, s.getStartPeriodId()),
                nameOf(periods, s.getEndPeriodId()),
                s.getCycleStartDate(),
                s.getCycleEndDate(),
                weekCount(s),
                s.getStatus(),
                s.getStatus() == null ? null : s.getStatus().getDisplayName(),
                s.getRejectReason(),
                s.getCancelReason(),
                s.getCancelType() == null ? null : s.getCancelType().getDisplayName(),
                s.getCancelledAt(),
                String.valueOf(s.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                s.getCreateTime(),
                s.getUpdateTime(),
                generated.stream().map(b -> String.valueOf(b.getId())).toList(),
                items
        );
    }

    private LambdaQueryWrapper<LabBookingSeries> buildWrapper(SeriesPageQuery query) {
        LambdaQueryWrapper<LabBookingSeries> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(LabBookingSeries::getStatus, query.getStatus());
        }
        if (query.getSpaceId() != null) {
            wrapper.eq(LabBookingSeries::getLabId, query.getSpaceId());
        }
        wrapper.orderByDesc(LabBookingSeries::getCreateTime);
        return wrapper;
    }

    private Map<Long, BizSpace> loadSpaces(List<LabBookingSeries> records) {
        List<Long> ids = records.stream().map(LabBookingSeries::getLabId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BizSpace> map = new HashMap<>();
        for (BizSpace space : spaceMapper.selectBatchIds(ids)) {
            map.put(space.getId(), space);
        }
        return map;
    }

    private Map<Long, SysUser> loadUsers(List<LabBookingSeries> records) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (LabBookingSeries s : records) {
            ids.add(s.getApplicantId());
            if (s.getTeacherId() != null) {
                ids.add(s.getTeacherId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> map = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, ClassPeriod> loadPeriodMap(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return periodMapper.selectPeriodsByIds(distinct).stream()
                .collect(Collectors.toMap(ClassPeriod::getId, Function.identity()));
    }

    private String nameOf(Map<Long, ClassPeriod> periods, Long id) {
        if (id == null || periods == null) {
            return null;
        }
        ClassPeriod p = periods.get(id);
        return p == null ? String.valueOf(id) : p.getPeriodName();
    }

    private int weekCount(LabBookingSeries s) {
        // 周期 [start,end] 内匹配星期的天数
        int count = 0;
        for (LocalDate d = s.getCycleStartDate(); !d.isAfter(s.getCycleEndDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() == s.getWeekday()) {
                count++;
            }
        }
        return count;
    }

    /** 周期内匹配星期的日期与推导时段片段（上海本地节次时刻 → UTC） */
    private List<Segment> buildSegments(LocalDate start, LocalDate end, int weekday,
                                        ClassPeriod startPeriod, ClassPeriod endPeriod, BizSpace space) {
        List<Segment> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() != weekday) {
                continue;
            }
            Instant segStart = toUtc(d, startPeriod.getStartClock());
            Instant segEnd = toUtc(d, endPeriod.getEndClock());
            LocalTime startLocal = startPeriod.getStartClock();
            LocalTime endLocal = endPeriod.getEndClock();
            if (startLocal.isBefore(space.getOpenStart()) || endLocal.isAfter(space.getOpenEnd())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "节次时段须在场地开放时间（" + formatTime(space.getOpenStart()) + "~"
                                + formatTime(space.getOpenEnd()) + "）内");
            }
            result.add(new Segment(d, segStart, segEnd));
        }
        return result;
    }

    private Instant toUtc(LocalDate date, LocalTime clock) {
        return LocalDateTime.of(date, clock).atZone(SHANGHAI).toInstant();
    }

    /** 逐段冲突校验：命中即 40900 并单独列出冲突日期与时间段 */
    private void checkConflicts(Long labId, List<Segment> segments, String phase) {
        List<String> conflicts = new ArrayList<>();
        for (Segment seg : segments) {
            if (slotMapper.countConflict(labId, seg.startAt(), seg.endAt()) > 0) {
                conflicts.add(seg.date() + " " + formatRange(seg.startAt(), seg.endAt()));
            }
        }
        if (!conflicts.isEmpty()) {
            String detail = String.join("；", conflicts);
            log.warn("[周期预约{}] 时段冲突，拒绝：场地ID={}, 冲突明细={}", phase, labId, detail);
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "周期内以下时段已被其他预约占用（" + phase + "阶段校验）：" + detail);
        }
    }

    private void releaseSlot(Long bookingId, Long operatorId, String reason) {
        slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                .eq(LabSlot::getBookingId, bookingId)
                .isNull(LabSlot::getReleasedAt)
                .set(LabSlot::getReleasedAt, Instant.now())
                .set(LabSlot::getReleaseReason, reason)
                .set(LabSlot::getUpdateUserId, operatorId));
    }

    private void updateBookingCancelled(Long bookingId, Long operatorId, String reason, CancelType type) {
        bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, bookingId)
                .in(LabBooking::getStatus, BookingStatus.APPROVED)
                .set(LabBooking::getStatus, BookingStatus.CANCELLED)
                .set(LabBooking::getCancelReason, reason == null ? "随周期预约撤销" : reason)
                .set(LabBooking::getCancelType, type)
                .set(LabBooking::getCancelledAt, Instant.now())
                .set(LabBooking::getUpdateUserId, operatorId));
    }

    /** 发起/审批身份门禁：学生纯只读，拒绝写操作（40300 兜底，前端按权限已隐藏入口） */
    private void requireBookableIdentity(Long operatorId, String action) {
        SysUser user = userMapper.selectById(operatorId);
        if (user != null && "STUDENT".equals(user.getUserType())) {
            log.warn("[{}] 学生禁止操作：操作人={}", action, operatorId);
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "学生为只读身份，不能" + action);
        }
    }

    private LabBookingSeries requireSeries(Long id) {
        LabBookingSeries series = seriesMapper.selectById(id);
        if (series == null) {
            log.warn("[周期预约查询] 规则不存在或已删除：规则ID={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return series;
    }

    private BizSpace requireSpace(Long id) {
        BizSpace space = spaceMapper.selectById(id);
        if (space == null) {
            log.warn("[周期预约查询] 场地不存在或已删除：场地ID={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return space;
    }

    private BizDocument requireSeriesDocument(Long seriesId) {
        BizDocument doc = flowService.findBySeriesId(seriesId);
        if (doc == null) {
            log.warn("[周期预约流程] 流程单据缺失：规则ID={}", seriesId);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "周期预约流程档案缺失，无法执行该操作");
        }
        return doc;
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    private String formatRange(Instant startAt, Instant endAt) {
        String s = startAt == null ? "" : startAt.atZone(SHANGHAI).format(HH_MM);
        String e = endAt == null ? "" : endAt.atZone(SHANGHAI).format(HH_MM);
        return s + "~" + e;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    /** 周期内一周的预约片段 */
    private record Segment(LocalDate date, Instant startAt, Instant endAt) {
    }
}
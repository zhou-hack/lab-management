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
import com.labmanage.module.booking.dto.BookingCreateDTO;
import com.labmanage.module.booking.dto.BookingPageQuery;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.mapper.LabBookingMapper;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import com.labmanage.module.booking.service.BookingService;
import com.labmanage.module.booking.vo.AvailabilityVO;
import com.labmanage.module.booking.vo.BookingDetailVO;
import com.labmanage.module.booking.vo.BookingStatsVO;
import com.labmanage.module.booking.vo.BookingVO;
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
import java.util.stream.Collectors;

/**
 * 场地预约业务实现。
 * <p>
 * 冲突协议（数据库设计 8.1）：半开区间重叠，仅 released_at IS NULL 的 lab_slot
 * 参与；待审批不占用，提交与审批双重校验，最后批准者遇冲突拒绝不抢占；
 * 审批事务先 SELECT ... FOR UPDATE 锁定场地行，串行化同场地审批防并发双写。
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    /** 业务时区：页面展示与开放窗口按上海本地日历，时间点统一 UTC 传输存储 */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;
    private final BizSpaceMapper spaceMapper;
    private final SysUserMapper userMapper;

    public BookingServiceImpl(LabBookingMapper bookingMapper,
                              LabSlotMapper slotMapper,
                              BizSpaceMapper spaceMapper,
                              SysUserMapper userMapper) {
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailVO create(BookingCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[预约提交] 开始：操作人={}, 场地ID={}, 时段=[{} ~ {}], 人数={}",
                operatorId, dto.spaceId(), dto.startAt(), dto.endAt(), dto.participantCount());

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

        long conflicts = slotMapper.countConflict(dto.spaceId(), dto.startAt(), dto.endAt());
        if (conflicts > 0) {
            log.warn("[预约提交] 所选时段已被占用：操作人={}, 场地ID={}, 时段=[{} ~ {}], 冲突占用数={}",
                    operatorId, dto.spaceId(), dto.startAt(), dto.endAt(), conflicts);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "所选时段已被占用，请调整起止时间");
        }

        LabBooking booking = new LabBooking();
        booking.setLabId(dto.spaceId());
        booking.setApplicantId(operatorId);
        booking.setPurpose(dto.purpose().trim());
        booking.setParticipantCount(dto.participantCount());
        booking.setStartAt(dto.startAt());
        booking.setEndAt(dto.endAt());
        booking.setStatus(BookingStatus.PENDING);
        bookingMapper.insert(booking);

        log.info("[预约提交] 完成：操作人={}, 预约ID={}, 场地={}, 状态=PENDING, 耗时={}ms",
                operatorId, booking.getId(), space.getLabName(), (System.nanoTime() - begin) / 1_000_000);
        return detailEntity(booking.getId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, BookingCancelDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[预约取消] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (!booking.getApplicantId().equals(operatorId)) {
            log.warn("[预约取消] 非申请人本人，拒绝：操作人={}, 预约ID={}, 申请人={}",
                    operatorId, id, booking.getApplicantId());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅申请人本人可取消预约");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.APPROVED) {
            log.warn("[预约取消] 当前状态不可取消：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待审批或已批准的预约可取消，当前状态：" + booking.getStatus().getDisplayName());
        }

        // 已批准的预约占用 lab_slot，取消须同步释放
        if (booking.getStatus() == BookingStatus.APPROVED) {
            Instant releaseAt = Instant.now();
            int released = slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                    .eq(LabSlot::getBookingId, id)
                    .isNull(LabSlot::getReleasedAt)
                    .set(LabSlot::getReleasedAt, releaseAt)
                    .set(LabSlot::getReleaseReason, "MANUAL取消")
                    .set(LabSlot::getUpdateUserId, operatorId));
            log.info("[预约取消] 释放场地占用：预约ID={}, 释放行数={}, 释放时间={}", id, released, releaseAt);
        }

        Instant cancelledAt = Instant.now();
        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, id)
                .set(LabBooking::getStatus, BookingStatus.CANCELLED)
                .set(LabBooking::getCancelReason, dto == null ? null : dto.reason())
                .set(LabBooking::getCancelType, CancelType.MANUAL)
                .set(LabBooking::getCancelledAt, cancelledAt)
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约取消] 更新未命中任何行（预约可能已被删除）：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[预约取消] 完成：操作人={}, 预约ID={}, 原状态={}, 取消时间={}",
                operatorId, id, booking.getStatus(), cancelledAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        long begin = System.nanoTime();
        log.info("[预约审批] 开始：操作人={}, 预约ID={}", operatorId, id);

        LabBooking booking = requireBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("[预约审批] 状态非待审批，拒绝：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待审批的预约可审批，当前状态：" + booking.getStatus().getDisplayName());
        }

        // 场地行锁：串行化同场地审批（文档 8.1），锁内复核场地状态与时段冲突
        BizSpace space = spaceMapper.selectByIdForUpdate(booking.getLabId());
        if (space == null) {
            log.warn("[预约审批] 场地不存在或已删除：操作人={}, 预约ID={}, 场地ID={}",
                    operatorId, id, booking.getLabId());
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (space.getStatus() != SpaceStatus.AVAILABLE) {
            log.warn("[预约审批] 场地当前状态不可批准：操作人={}, 预约ID={}, 场地={}, 状态={}",
                    operatorId, id, space.getLabName(), space.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地当前状态为 " + space.getStatus().getDisplayName() + "，无法批准");
        }
        long conflicts = slotMapper.countConflict(booking.getLabId(), booking.getStartAt(), booking.getEndAt());
        if (conflicts > 0) {
            log.warn("[预约审批] 时段冲突（已被其他预约占用），拒绝：操作人={}, 预约ID={}, 场地ID={}, 冲突占用数={}",
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
                .set(LabBooking::getStatus, BookingStatus.APPROVED)
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约审批] 更新未命中任何行（预约可能已被删除）：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[预约审批] 完成：操作人={}, 预约ID={}, 场地={}, 时段=[{} ~ {}], 占用ID={}, 耗时={}ms",
                operatorId, id, space.getLabName(), booking.getStartAt(), booking.getEndAt(),
                slot.getId(), (System.nanoTime() - begin) / 1_000_000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, BookingRejectDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[预约驳回] 开始：操作人={}, 预约ID={}, 原因={}", operatorId, id, dto.reason());

        LabBooking booking = requireBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("[预约驳回] 状态非待审批，拒绝：操作人={}, 预约ID={}, 状态={}",
                    operatorId, id, booking.getStatus());
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "仅待审批的预约可驳回，当前状态：" + booking.getStatus().getDisplayName());
        }

        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, id)
                .set(LabBooking::getStatus, BookingStatus.REJECTED)
                .set(LabBooking::getRejectReason, dto.reason().trim())
                .set(LabBooking::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[预约驳回] 更新未命中任何行（预约可能已被删除）：操作人={}, 预约ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[预约驳回] 完成：操作人={}, 预约ID={}, 申请人={}", operatorId, id, booking.getApplicantId());
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
    public BookingDetailVO detail(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        LabBooking booking = requireBooking(id);
        if (!booking.getApplicantId().equals(operatorId) && !StpUtil.hasPermission("booking:lab:approve")) {
            log.warn("[预约详情] 非申请人且无审批权限，拒绝：操作人={}, 预约ID={}, 申请人={}",
                    operatorId, id, booking.getApplicantId());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅申请人本人或审批人可查看预约详情");
        }
        log.info("[预约详情] 完成：操作人={}, 预约ID={}, 状态={}", operatorId, id, booking.getStatus());
        return detailEntity(id, operatorId);
    }

    @Override
    public AvailabilityVO availability(Long spaceId, LocalDate date) {
        Long operatorId = SecurityUtils.currentUserId();
        BizSpace space = requireSpace(spaceId);

        // 上海本地当天 [零点, 次日零点) 转 UTC 后查询未释放占用
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
        log.info("[预约统计] 完成：操作人={}, 总数={}, 待审批={}, 已批准={}, 已驳回={}, 已取消={}",
                operatorId, pending + approved + rejected + cancelled, pending, approved, rejected, cancelled);
        return new BookingStatsVO(pending + approved + rejected + cancelled, pending, approved, rejected, cancelled);
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
        Instant now = Instant.now();
        List<BookingVO> vos = records.stream()
                .map(b -> toVO(b, spaces.get(b.getLabId()), users.get(b.getApplicantId()), now))
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

    private BookingVO toVO(LabBooking b, BizSpace space, SysUser applicant, Instant now) {
        return new BookingVO(
                String.valueOf(b.getId()),
                String.valueOf(b.getLabId()),
                space == null ? null : space.getLabName(),
                b.getPurpose(),
                b.getParticipantCount(),
                b.getStartAt(),
                b.getEndAt(),
                b.getStatus(),
                b.getStatus() == null ? null : b.getStatus().getDisplayName(),
                runtimePhase(b, now),
                String.valueOf(b.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                b.getCreateTime()
        );
    }

    private BookingDetailVO detailEntity(Long id, Long operatorId) {
        LabBooking b = requireBooking(id);
        BizSpace space = spaceMapper.selectById(b.getLabId());
        SysUser applicant = userMapper.selectById(b.getApplicantId());
        return new BookingDetailVO(
                String.valueOf(b.getId()),
                String.valueOf(b.getLabId()),
                space == null ? null : space.getLabName(),
                b.getPurpose(),
                b.getParticipantCount(),
                b.getStartAt(),
                b.getEndAt(),
                b.getStatus(),
                b.getStatus() == null ? null : b.getStatus().getDisplayName(),
                runtimePhase(b, Instant.now()),
                b.getRejectReason(),
                b.getCancelReason(),
                b.getCancelType(),
                b.getCancelType() == null ? null : b.getCancelType().getDisplayName(),
                b.getCancelledAt(),
                String.valueOf(b.getApplicantId()),
                applicant == null ? null : applicant.getRealName(),
                b.getCreateTime(),
                b.getUpdateTime()
        );
    }

    /**
     * 派生展示字段（仅 APPROVED 有值）：UPCOMING 未开始 / IN_USE 使用中 /
     * FINISHED 已结束。不落库，不参与状态机迁移。
     */
    private String runtimePhase(LabBooking b, Instant now) {
        if (b.getStatus() != BookingStatus.APPROVED) {
            return null;
        }
        if (now.isBefore(b.getStartAt())) {
            return "UPCOMING";
        }
        if (now.isBefore(b.getEndAt())) {
            return "IN_USE";
        }
        return "FINISHED";
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

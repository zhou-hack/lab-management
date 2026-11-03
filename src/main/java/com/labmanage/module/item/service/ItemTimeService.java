package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.booking.entity.ClassPeriod;
import com.labmanage.module.booking.mapper.ClassPeriodMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 物料预约时间服务：上海本地日期与节次统一换算为 UTC 时间点。
 * <p>
 * 预占窗口均为半开区间 [startAt, endAt)；实发窗口为上海预约当天 [00:00, 次日00:00)。
 */
@Service
public class ItemTimeService {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final int MAX_BOOKING_DAYS_AHEAD = 3;
    private static final LocalTime RETURN_DEADLINE_CLOCK = LocalTime.of(23, 59);

    private final ClassPeriodMapper periodMapper;
    private final Clock clock;

    public ItemTimeService(ClassPeriodMapper periodMapper,
                           @Qualifier("itemClock") Clock clock) {
        this.periodMapper = periodMapper;
        this.clock = clock;
    }

    /**
     * 校验上海预约日期和起止节次，并生成预约、归还与实发所需的 UTC 时间点。
     * 预约日期允许为上海当天至 +3 天（首尾均含）。
     */
    public BookingWindow resolveBookingWindow(LocalDate bookingDate, Long startPeriodId, Long endPeriodId) {
        if (bookingDate == null || startPeriodId == null || endPeriodId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "预约日期、起始节次与结束节次均不能为空");
        }
        LocalDate today = today();
        if (bookingDate.isBefore(today) || bookingDate.isAfter(today.plusDays(MAX_BOOKING_DAYS_AHEAD))) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "预约日期仅允许为上海当天至 +3 天");
        }

        ClassPeriod startPeriod = requireEnabledPeriod(startPeriodId);
        ClassPeriod endPeriod = startPeriodId.equals(endPeriodId)
                ? startPeriod : requireEnabledPeriod(endPeriodId);
        if (startPeriod.getPeriodNo() > endPeriod.getPeriodNo()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次不能晚于结束节次");
        }

        Instant bookingStartAt = toUtc(bookingDate, startPeriod.getStartClock());
        Instant bookingEndAt = toUtc(bookingDate, endPeriod.getEndClock());
        if (!bookingStartAt.isBefore(bookingEndAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次起止时间顺序不正确");
        }
        Instant returnDeadline = toUtc(bookingDate, RETURN_DEADLINE_CLOCK);
        Instant issueEarliestAt = issueEarliestAt(bookingDate);
        return new BookingWindow(bookingDate, bookingStartAt, bookingEndAt, returnDeadline, issueEarliestAt);
    }

    /** 解析一组可跳选节次，按节次号排序去重并合并为极大连续段。 */
    public BookingSelection resolveBookingSelection(LocalDate bookingDate, Long startPeriodId, Long endPeriodId,
                                                    List<String> periodIds) {
        BookingWindow requestedEnvelope = startPeriodId == null && endPeriodId == null ? null
                : resolveBookingWindow(bookingDate, startPeriodId, endPeriodId);
        if (periodIds == null || periodIds.isEmpty()) {
            if (requestedEnvelope == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "预约日期与节次均不能为空");
            }
            return new BookingSelection(List.of(new BookingSegment(1, startPeriodId, endPeriodId, requestedEnvelope)),
                    requestedEnvelope);
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String raw : periodIds) {
            if (raw == null) {
                continue;
            }
            for (String value : raw.split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    uniqueIds.add(parsePeriodId(trimmed));
                }
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次不能为空");
        }
        List<ClassPeriod> selected = uniqueIds.stream().map(this::requireEnabledPeriod)
                .sorted(Comparator.comparing(ClassPeriod::getPeriodNo)).toList();
        List<BookingSegment> segments = new ArrayList<>();
        int groupStart = 0;
        for (int i = 1; i <= selected.size(); i++) {
            if (i < selected.size()
                    && selected.get(i).getPeriodNo().equals(selected.get(i - 1).getPeriodNo() + 1)) {
                continue;
            }
            ClassPeriod first = selected.get(groupStart);
            ClassPeriod last = selected.get(i - 1);
            BookingWindow window = resolveBookingWindow(bookingDate, first.getId(), last.getId());
            segments.add(new BookingSegment(segments.size() + 1, first.getId(), last.getId(), window));
            groupStart = i;
        }
        BookingWindow envelope = new BookingWindow(bookingDate,
                segments.get(0).window().bookingStartAt(),
                segments.get(segments.size() - 1).window().bookingEndAt(),
                segments.get(0).window().returnDeadline(), segments.get(0).window().issueEarliestAt());
        if (requestedEnvelope != null
                && (!startPeriodId.equals(segments.get(0).startPeriodId())
                || !endPeriodId.equals(segments.get(segments.size() - 1).endPeriodId()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起止节次与所选节次不一致");
        }
        return new BookingSelection(segments, envelope);
    }

    /** 将连续段展开为实际节次ID，供审核和编辑时重建原始选择。 */
    public List<String> expandPeriodIds(Long startPeriodId, Long endPeriodId) {
        ClassPeriod start = requireEnabledPeriod(startPeriodId);
        ClassPeriod end = requireEnabledPeriod(endPeriodId);
        if (start.getPeriodNo() > end.getPeriodNo()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "起始节次不能晚于结束节次");
        }
        return periodMapper.selectList(null).stream()
                .filter(period -> period.getPeriodNo() >= start.getPeriodNo()
                        && period.getPeriodNo() <= end.getPeriodNo())
                .sorted(Comparator.comparing(ClassPeriod::getPeriodNo))
                .map(period -> String.valueOf(period.getId()))
                .toList();
    }

    private Long parsePeriodId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次ID不合法");
        }
    }

    /** 当前 UTC 时间，统一截断为毫秒。 */
    public Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    /** 将任意时间点转换为上海自然日期。 */
    public LocalDate shanghaiDate(Instant instant) {
        return instant.atZone(SHANGHAI).toLocalDate();
    }

    /** 查询时钟当前时刻对应的上海自然日期。 */
    public LocalDate today() {
        return shanghaiDate(now());
    }

    /** 预约当天从上海 00:00 起即可实发。 */
    public Instant issueEarliestAt(LocalDate bookingDate) {
        return fullDayReservationWindow(bookingDate).startAt();
    }

    /** 实发窗口为上海预约当天 [00:00, 次日00:00)。 */
    public boolean isIssueWindow(Instant now, LocalDate bookingDate) {
        ReservationWindow window = fullDayReservationWindow(bookingDate);
        return !now.isBefore(window.startAt()) && now.isBefore(window.endAt());
    }

    /** 到达上海预约日次日 00:00 后，实发窗口结束。 */
    public boolean isIssueWindowExpired(Instant now, LocalDate bookingDate) {
        return !now.isBefore(fullDayReservationWindow(bookingDate).endAt());
    }

    /** 普通物料按预约起止时段预占。 */
    public ReservationWindow normalReservationWindow(Instant bookingStartAt, Instant bookingEndAt) {
        if (!bookingStartAt.isBefore(bookingEndAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "预占开始时间必须早于结束时间");
        }
        return new ReservationWindow(bookingStartAt, bookingEndAt);
    }

    /** 少于 5 台固定资产按上海预约日全天预占：[当天 00:00, 次日 00:00)。 */
    public ReservationWindow fullDayReservationWindow(LocalDate bookingDate) {
        return new ReservationWindow(bookingDate.atStartOfDay(SHANGHAI).toInstant(),
                bookingDate.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
    }

    /** 按全库保有总数选择固定资产预占窗口；4 台按全天，5 台起按预约时段。 */
    public ReservationWindow reservationWindow(BookingWindow bookingWindow, long ownedAssetTotal) {
        if (ownedAssetTotal < 5) {
            return fullDayReservationWindow(bookingWindow.bookingDate());
        }
        return normalReservationWindow(bookingWindow.bookingStartAt(), bookingWindow.bookingEndAt());
    }

    private ClassPeriod requireEnabledPeriod(Long periodId) {
        ClassPeriod period = periodMapper.selectById(periodId);
        if (period == null || !Integer.valueOf(1).equals(period.getEnabled())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "节次不存在或已停用");
        }
        return period;
    }

    private Instant toUtc(LocalDate date, LocalTime localTime) {
        return LocalDateTime.of(date, localTime).atZone(SHANGHAI).toInstant();
    }

    /** 日期与节次推导出的 UTC 预约时间、归还期限和最早实发时间。 */
    public record BookingWindow(
            LocalDate bookingDate,
            Instant bookingStartAt,
            Instant bookingEndAt,
            Instant returnDeadline,
            Instant issueEarliestAt
    ) {
    }

    public record BookingSegment(
            Integer segmentSeq,
            Long startPeriodId,
            Long endPeriodId,
            BookingWindow window
    ) {
    }

    public record BookingSelection(
            List<BookingSegment> segments,
            BookingWindow envelope
    ) {
    }

    /** 预占起止时间点，按半开区间 [startAt, endAt) 使用。 */
    public record ReservationWindow(
            Instant startAt,
            Instant endAt
    ) {
    }
}

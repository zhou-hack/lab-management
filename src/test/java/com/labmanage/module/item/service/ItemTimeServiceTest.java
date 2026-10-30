package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.booking.entity.ClassPeriod;
import com.labmanage.module.booking.mapper.ClassPeriodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemTimeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:30:00.123456789Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private ItemTimeService timeService;

    @BeforeEach
    void setUp() {
        ClassPeriodMapper periodMapper = mock(ClassPeriodMapper.class);
        Map<Long, ClassPeriod> periods = new HashMap<>();
        periods.put(1L, period(1, 1, "08:00", "08:45", 1));
        periods.put(2L, period(2, 2, "08:55", "09:40", 1));
        periods.put(3L, period(3, 3, "10:00", "10:45", 0));
        when(periodMapper.selectById(anyLong())).thenAnswer(invocation -> periods.get(invocation.getArgument(0)));
        timeService = new ItemTimeService(periodMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("预约日期允许上海当天至+3天，拒绝昨天与+4天")
    void bookingDateRange() {
        for (int days = 0; days <= 3; days++) {
            LocalDate date = TODAY.plusDays(days);
            ItemTimeService.BookingWindow window = timeService.resolveBookingWindow(date, 1L, 2L);
            assertThat(window.bookingDate()).isEqualTo(date);
        }
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY.minusDays(1), 1L, 2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当天至 +3 天");
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY.plusDays(4), 1L, 2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当天至 +3 天");
    }

    @Test
    @DisplayName("非法、停用、缺失及倒序节次均被拒绝")
    void invalidPeriods() {
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY, null, 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY, 99L, 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在或已停用");
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY, 3L, 3L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在或已停用");
        assertThatThrownBy(() -> timeService.resolveBookingWindow(TODAY, 2L, 1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("起始节次不能晚于结束节次");
    }

    @Test
    @DisplayName("上海节次及23:59归还期限正确换算为UTC")
    void shanghaiTimesConvertToUtc() {
        ItemTimeService.BookingWindow window = timeService.resolveBookingWindow(TODAY, 1L, 2L);

        assertThat(window.bookingStartAt()).isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));
        assertThat(window.bookingEndAt()).isEqualTo(Instant.parse("2026-11-10T01:40:00Z"));
        assertThat(window.returnDeadline()).isEqualTo(Instant.parse("2026-11-10T15:59:00Z"));
        assertThat(window.issueEarliestAt()).isEqualTo(Instant.parse("2026-11-09T16:00:00Z"));
        assertThat(timeService.now()).isEqualTo(Instant.parse("2026-11-10T00:30:00.123Z"));
        assertThat(timeService.today()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("预约当天均可实发，次日零时起禁止")
    void issueWindowBoundaries() {
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-09T15:59:59.999Z"), TODAY)).isFalse();
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-09T16:00:00Z"), TODAY)).isTrue();
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-10T00:00:00Z"), TODAY)).isTrue();
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-10T01:40:00Z"), TODAY)).isTrue();
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-10T15:59:59.999Z"), TODAY)).isTrue();
        assertThat(timeService.isIssueWindow(Instant.parse("2026-11-10T16:00:00Z"), TODAY)).isFalse();

        assertThat(timeService.isIssueWindowExpired(Instant.parse("2026-11-10T15:59:59.999Z"), TODAY)).isFalse();
        assertThat(timeService.isIssueWindowExpired(Instant.parse("2026-11-10T16:00:00Z"), TODAY)).isTrue();
    }

    @Test
    @DisplayName("4台固定资产全天预占，5台普通窗口预占")
    void assetTotalSelectsReservationRule() {
        ItemTimeService.BookingWindow booking = timeService.resolveBookingWindow(TODAY, 1L, 2L);

        ItemTimeService.ReservationWindow four = timeService.reservationWindow(booking, 4);
        ItemTimeService.ReservationWindow five = timeService.reservationWindow(booking, 5);

        assertThat(four.startAt()).isEqualTo(Instant.parse("2026-11-09T16:00:00Z"));
        assertThat(four.endAt()).isEqualTo(Instant.parse("2026-11-10T16:00:00Z"));
        assertThat(five.startAt()).isEqualTo(booking.bookingStartAt());
        assertThat(five.endAt()).isEqualTo(booking.bookingEndAt());
    }

    @Test
    @DisplayName("上海全天预占为[当日00:00,次日00:00)半开区间")
    void shanghaiDayIsHalfOpen() {
        ItemTimeService.ReservationWindow window = timeService.fullDayReservationWindow(TODAY);

        assertThat(window.startAt().atZone(ItemTimeService.SHANGHAI).toLocalDateTime())
                .isEqualTo(TODAY.atStartOfDay());
        assertThat(window.endAt().atZone(ItemTimeService.SHANGHAI).toLocalDateTime())
                .isEqualTo(TODAY.plusDays(1).atStartOfDay());
        assertThat(window.startAt()).isBefore(window.endAt());
        assertThat(window.endAt().minusMillis(1).atZone(ItemTimeService.SHANGHAI).toLocalDate())
                .isEqualTo(TODAY);
        assertThat(window.endAt().atZone(ItemTimeService.SHANGHAI).toLocalDate())
                .isEqualTo(TODAY.plusDays(1));
    }

    private static ClassPeriod period(int id, int number, String start, String end, int enabled) {
        ClassPeriod period = new ClassPeriod();
        period.setId((long) id);
        period.setPeriodNo(number);
        period.setPeriodName("第" + number + "节");
        period.setStartClock(LocalTime.parse(start));
        period.setEndClock(LocalTime.parse(end));
        period.setEnabled(enabled);
        return period;
    }
}

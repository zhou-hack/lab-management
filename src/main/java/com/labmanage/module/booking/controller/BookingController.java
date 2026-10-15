package com.labmanage.module.booking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingCreateDTO;
import com.labmanage.module.booking.dto.BookingPageQuery;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.service.BookingService;
import com.labmanage.module.booking.vo.AvailabilityVO;
import com.labmanage.module.booking.vo.BookingDetailVO;
import com.labmanage.module.booking.vo.BookingStatsVO;
import com.labmanage.module.booking.vo.BookingVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 场地预约接口。
 * 前缀 /api/v1/booking/labs；权限 booking:lab:*（RBAC 清单 2.4）。
 * <p>
 * 「我的预约」= 当前登录人为申请人；「全部预约」「统计」「审批/驳回」为
 * 审批视角（booking:lab:approve）；可用时段查询服务于预约表单。
 */
@RestController
@RequestMapping("/api/v1/booking/labs")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @SaCheckPermission("booking:lab:create")
    public Result<BookingDetailVO> create(@Valid @RequestBody BookingCreateDTO dto) {
        return Result.success(bookingService.create(dto));
    }

    @GetMapping
    @SaCheckPermission("booking:lab:list")
    public Result<PageResult<BookingVO>> pageMine(@Valid @ModelAttribute BookingPageQuery query) {
        return Result.success(bookingService.pageMine(query));
    }

    @GetMapping("/all")
    @SaCheckPermission("booking:lab:approve")
    public Result<PageResult<BookingVO>> pageAll(@Valid @ModelAttribute BookingPageQuery query) {
        return Result.success(bookingService.pageAll(query));
    }

    @GetMapping("/stats")
    @SaCheckPermission("booking:lab:approve")
    public Result<BookingStatsVO> stats() {
        return Result.success(bookingService.stats());
    }

    @GetMapping("/availability")
    @SaCheckPermission("booking:lab:create")
    public Result<AvailabilityVO> availability(
            @RequestParam Long spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(bookingService.availability(spaceId, date));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("booking:lab:list")
    public Result<BookingDetailVO> detail(@PathVariable Long id) {
        return Result.success(bookingService.detail(id));
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("booking:lab:cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @Valid @RequestBody(required = false) BookingCancelDTO dto) {
        bookingService.cancel(id, dto);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> approve(@PathVariable Long id) {
        bookingService.approve(id);
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody BookingRejectDTO dto) {
        bookingService.reject(id, dto);
        return Result.success();
    }
}

package com.labmanage.module.booking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.config.SensitiveOperationGuard;
import com.labmanage.module.booking.dto.BatchApproveDTO;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingCheckoutDTO;
import com.labmanage.module.booking.dto.BookingCreateDTO;
import com.labmanage.module.booking.dto.BookingPageQuery;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.service.BookingService;
import com.labmanage.module.booking.vo.AvailabilityVO;
import com.labmanage.module.booking.vo.BatchApproveResultVO;
import com.labmanage.module.booking.vo.BookingDetailVO;
import com.labmanage.module.booking.vo.BookingStatsVO;
import com.labmanage.module.booking.vo.BookingVO;
import com.labmanage.module.booking.vo.LabOccupancyVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final SensitiveOperationGuard sensitiveGuard;

    public BookingController(BookingService bookingService, SensitiveOperationGuard sensitiveGuard) {
        this.bookingService = bookingService;
        this.sensitiveGuard = sensitiveGuard;
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

    /**
     * 管理员强制取消（A2）：仅 APPROVED 单；释放占用槽、记 FORCE_CANCEL 事件，
     * 原因必填留痕。待审批单请使用驳回终止（语义不重叠）。
     */
    @PostMapping("/{id}/force_cancel")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> forceCancel(@PathVariable Long id, @Valid @RequestBody BookingCancelDTO dto) {
        sensitiveGuard.checkSafe("预约强制取消");
        bookingService.forceCancel(id, dto);
        return Result.success();
    }

    /**
     * 预约情况只读分页（booking:lab:view）：学生只读视角，
     * 全量查看预约情况与批准/驳回结果，不提供任何写操作。
     */
    @GetMapping("/board")
    @SaCheckPermission("booking:lab:view")
    public Result<PageResult<BookingVO>> pageBoard(@Valid @ModelAttribute BookingPageQuery query) {
        return Result.success(bookingService.pageBoard(query));
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

    /**
     * 场地占用总览：按日期 + 可选节次窗口返回全部场地的空闲/占用分档，
     * 占用附老师与课程信息（任意预约权限可读，学生看板同样可用）。
     */
    @GetMapping("/occupancy")
    @SaCheckPermission(value = {"booking:lab:view", "booking:lab:list",
            "booking:lab:create", "booking:lab:approve"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<java.util.List<LabOccupancyVO>> occupancy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long startPeriodId,
            @RequestParam(required = false) Long endPeriodId) {
        return Result.success(bookingService.occupancy(date, startPeriodId, endPeriodId));
    }

    /**
     * 场地近期预约（时间轴「24h 近期」卡片）：待审批/已批准单中与
     * [now, now+24h) 窗口重叠的记录，按开始时间升序。（任意预约权限可读）
     */
    @GetMapping("/recent")
    @SaCheckPermission(value = {"booking:lab:view", "booking:lab:list",
            "booking:lab:create", "booking:lab:approve"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public Result<java.util.List<com.labmanage.module.booking.vo.BookingVO>> recent(
            @RequestParam Long spaceId) {
        return Result.success(bookingService.recent(spaceId));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(
            value = {"booking:lab:list", "booking:lab:view", "booking:lab:approve"},
            mode = cn.dev33.satoken.annotation.SaMode.OR)
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
        sensitiveGuard.checkSafe("预约批准");
        bookingService.approve(id);
        return Result.success();
    }

    /**
     * 批量批准（单次预约，≤100 条）：逐单独立审批，互不影响；
     * 成功入 approvedIds，失败附原因入 failedItems（状态已变更/时段冲突等）。
     */
    @PostMapping("/batch_approve")
    @SaCheckPermission("booking:lab:approve")
    public Result<BatchApproveResultVO> batchApprove(@Valid @RequestBody BatchApproveDTO dto) {
        sensitiveGuard.checkSafe("预约批量批准");
        java.util.List<String> approvedIds = new java.util.ArrayList<>();
        java.util.List<BatchApproveResultVO.FailedItem> failedItems = new java.util.ArrayList<>();
        for (Long id : dto.ids()) {
            try {
                bookingService.approve(id);
                approvedIds.add(String.valueOf(id));
            } catch (BusinessException ex) {
                failedItems.add(new BatchApproveResultVO.FailedItem(String.valueOf(id), ex.getMessage()));
            }
        }
        return Result.success(new BatchApproveResultVO(approvedIds, failedItems));
    }

    /** 预约记录 CSV 导出（与「全部预约」同条件过滤，UTF-8 BOM，上限 10000 条） */
    @GetMapping("/all/export")
    @SaCheckPermission("booking:lab:approve")
    public ResponseEntity<byte[]> exportAll(@Valid @ModelAttribute BookingPageQuery query) {
        byte[] csv = bookingService.exportBookings(query);
        String filename = "bookings_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @PostMapping("/{id}/reject")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody BookingRejectDTO dto) {
        sensitiveGuard.checkSafe("预约驳回");
        bookingService.reject(id, dto);
        return Result.success();
    }

    /**
     * 开门签到：已批准且到达开始时间，管理员核对场地后开门（booking:lab:approve）。
     * 写 document_event(CHECKIN)，IN_USE 为派生态不落库。
     */
    @PostMapping("/{id}/checkin")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> checkin(@PathVariable Long id) {
        sensitiveGuard.checkSafe("开门签到");
        bookingService.checkin(id);
        return Result.success();
    }

    /**
     * 结束清点：已开门签到后，管理员清点完毕结束使用（booking:lab:approve）。
     * 写 document_event(CHECKOUT) 并释放占用槽，FINISHED 为派生态。
     */
    @PostMapping("/{id}/checkout")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> checkout(@PathVariable Long id,
                                 @Valid @RequestBody(required = false) BookingCheckoutDTO dto) {
        sensitiveGuard.checkSafe("结束清点");
        bookingService.checkout(id, dto);
        return Result.success();
    }
}

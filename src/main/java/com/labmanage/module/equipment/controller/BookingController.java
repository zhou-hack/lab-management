package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.BookingApproveRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingCheckoutRequest;
import com.labmanage.module.equipment.dto.BookingCreateRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.service.BookingService;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 器材借用管理（提交、审批、借出、归还）
 */
@Tag(name = "设备管理-借用")
@RestController
@RequestMapping("/api/v1/equipment/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "借用单分页查询（scope=MINE/PENDING/ALL）")
    @SaCheckPermission("equipment:booking:list")
    @GetMapping
    public Result<PageResult<BookingVO>> page(BookingPageQuery query) {
        return Result.success(bookingService.page(query));
    }

    @Operation(summary = "借用单详情（申请人/指定审批老师/管理员可见）")
    @SaCheckPermission("equipment:booking:list")
    @GetMapping("/{id}")
    public Result<BookingVO> detail(@PathVariable String id) {
        return Result.success(bookingService.detail(id));
    }

    @Operation(summary = "审批老师下拉选项")
    @SaCheckPermission("equipment:booking:create")
    @GetMapping("/teachers")
    public Result<List<TeacherOptionVO>> teacherOptions() {
        return Result.success(bookingService.teacherOptions());
    }

    @Operation(summary = "提交借用单（幂等键命中返回原单）")
    @SaCheckPermission("equipment:booking:create")
    @PostMapping
    public Result<BookingVO> create(@Valid @RequestBody BookingCreateRequest request) {
        return Result.success(bookingService.create(request));
    }

    @Operation(summary = "撤销借用单")
    @SaCheckPermission("equipment:booking:cancel")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable String id, @Valid @RequestBody BookingCancelRequest request) {
        bookingService.cancel(id, request);
        return Result.success();
    }

    @Operation(summary = "老师审批")
    @SaCheckPermission("equipment:booking:teacher_approve")
    @PostMapping("/{id}/teacher_approve")
    public Result<Void> teacherApprove(@PathVariable String id, @Valid @RequestBody BookingApproveRequest request) {
        bookingService.teacherApprove(id, request);
        return Result.success();
    }

    @Operation(summary = "管理员审批")
    @SaCheckPermission("equipment:booking:admin_approve")
    @PostMapping("/{id}/admin_approve")
    public Result<Void> adminApprove(@PathVariable String id, @Valid @RequestBody BookingApproveRequest request) {
        bookingService.adminApprove(id, request);
        return Result.success();
    }

    @Operation(summary = "借出确认")
    @SaCheckPermission("equipment:booking:checkout")
    @PostMapping("/{id}/checkout")
    public Result<Void> checkout(@PathVariable String id, @Valid @RequestBody BookingCheckoutRequest request) {
        bookingService.checkout(id, request);
        return Result.success();
    }

    @Operation(summary = "归还确认")
    @SaCheckPermission("equipment:booking:return")
    @PostMapping("/{id}/return")
    public Result<Void> confirmReturn(@PathVariable String id, @Valid @RequestBody BookingReturnRequest request) {
        bookingService.confirmReturn(id, request);
        return Result.success();
    }
}

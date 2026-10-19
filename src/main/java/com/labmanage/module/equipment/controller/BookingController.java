package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.BookingApplyRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.dto.BookingReviewRequest;
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
 * 设备使用申请（三权级：学生/教师申请，教师初审，管理员终审与借出归还）
 */
@Tag(name = "设备管理-使用申请")
@RestController
@RequestMapping("/api/v1/equipment/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Operation(summary = "提交设备使用申请（一台一单；学生须指定审批教师）")
    @SaCheckPermission("equipment:booking:apply")
    @PostMapping
    public Result<Void> apply(@Valid @RequestBody BookingApplyRequest request) {
        bookingService.apply(request);
        return Result.success();
    }

    @Operation(summary = "本人申请分页查询")
    @SaCheckPermission("equipment:booking:list_own")
    @GetMapping("/my")
    public Result<PageResult<BookingVO>> myBookings(BookingPageQuery query) {
        return Result.success(bookingService.myBookings(query));
    }

    @Operation(summary = "撤销本人申请（仅借出前）")
    @SaCheckPermission("equipment:booking:cancel")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable String id, @Valid @RequestBody(required = false) BookingCancelRequest request) {
        bookingService.cancel(id, request != null ? request : new BookingCancelRequest(null));
        return Result.success();
    }

    @Operation(summary = "教师待初审分页查询（指定审批教师待办）")
    @SaCheckPermission("equipment:booking:teacher_review")
    @GetMapping("/teacher_pending")
    public Result<PageResult<BookingVO>> teacherPending(BookingPageQuery query) {
        return Result.success(bookingService.teacherPending(query));
    }

    @Operation(summary = "教师初审（通过：普通即生效、贵重转终审；驳回意见必填）")
    @SaCheckPermission("equipment:booking:teacher_review")
    @PutMapping("/{id}/teacher_review")
    public Result<Void> teacherReview(@PathVariable String id, @Valid @RequestBody BookingReviewRequest request) {
        bookingService.teacherReview(id, request);
        return Result.success();
    }

    @Operation(summary = "全部申请分页查询（管理员）")
    @SaCheckPermission("equipment:booking:list_all")
    @GetMapping
    public Result<PageResult<BookingVO>> allBookings(BookingPageQuery query) {
        return Result.success(bookingService.allBookings(query));
    }

    @Operation(summary = "管理员终审/代批（贵重单终审；学生单超 24h 可代批）")
    @SaCheckPermission("equipment:booking:admin_review")
    @PutMapping("/{id}/admin_review")
    public Result<Void> adminReview(@PathVariable String id, @Valid @RequestBody BookingReviewRequest request) {
        bookingService.adminReview(id, request);
        return Result.success();
    }

    @Operation(summary = "借出发放登记（设备置借出中，同事务建借出流水）")
    @SaCheckPermission("equipment:booking:checkout")
    @PutMapping("/{id}/checkout")
    public Result<Void> checkout(@PathVariable String id) {
        bookingService.checkout(id);
        return Result.success();
    }

    @Operation(summary = "归还登记（完好恢复空闲；损坏备注必填并转维护中）")
    @SaCheckPermission("equipment:booking:return")
    @PutMapping("/{id}/return")
    public Result<Void> returnAsset(@PathVariable String id, @Valid @RequestBody BookingReturnRequest request) {
        bookingService.returnAsset(id, request);
        return Result.success();
    }

    @Operation(summary = "审批教师下拉选项（TEACHER 角色启用用户）")
    @SaCheckPermission("equipment:booking:apply")
    @GetMapping("/teacher_options")
    public Result<List<TeacherOptionVO>> teacherOptions() {
        return Result.success(bookingService.teacherOptions());
    }
}

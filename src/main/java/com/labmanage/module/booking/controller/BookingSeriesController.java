package com.labmanage.module.booking.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.config.SensitiveOperationGuard;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.dto.SeriesCreateDTO;
import com.labmanage.module.booking.dto.SeriesPageQuery;
import com.labmanage.module.booking.service.BookingSeriesService;
import com.labmanage.module.booking.vo.PeriodVO;
import com.labmanage.module.booking.vo.SeriesDetailVO;
import com.labmanage.module.booking.vo.SeriesVO;
import com.labmanage.module.booking.vo.TeacherOptionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场地节次时间轴与批量周期预约接口。
 * 前缀：/api/v1/booking/series、/api/v1/booking/periods；
 * 权限复用 booking:lab:*（发起=create、审批=approve、只读查询=view/list）。
 * <p>
 * 周期预约一次审批：批准时逐周生成预约单与占用槽；学生纯只读。
 */
@Tag(name = "场地周期预约")
@RestController
@RequestMapping("/api/v1/booking")
public class BookingSeriesController {

    private final BookingSeriesService seriesService;

    private final SensitiveOperationGuard sensitiveGuard;

    public BookingSeriesController(BookingSeriesService seriesService, SensitiveOperationGuard sensitiveGuard) {
        this.seriesService = seriesService;
        this.sensitiveGuard = sensitiveGuard;
    }

    /** 已启用节次列表（时间轴节次小卡片数据源；任意预约权限可读） */
    @GetMapping("/periods")
    @SaCheckPermission(value = {"booking:lab:view", "booking:lab:list",
            "booking:lab:create", "booking:lab:approve"}, mode = SaMode.OR)
    public Result<List<PeriodVO>> periods() {
        return Result.success(seriesService.listPeriods());
    }

    /** 上课老师选项（预约表单「老师」下拉；任意预约权限可读） */
    @GetMapping("/teachers")
    @SaCheckPermission(value = {"booking:lab:view", "booking:lab:list",
            "booking:lab:create", "booking:lab:approve"}, mode = SaMode.OR)
    public Result<List<TeacherOptionVO>> teachers() {
        return Result.success(seriesService.listTeachers());
    }

    /** 提交批量周期预约（老师；幂等键防重复；提交期逐段冲突预检） */
    @PostMapping("/series")
    @SaCheckPermission("booking:lab:create")
    public Result<SeriesDetailVO> createSeries(@Valid @RequestBody SeriesCreateDTO dto) {
        return Result.success(seriesService.create(dto));
    }

    /** 我的周期预约分页（申请人本人） */
    @GetMapping("/series")
    @SaCheckPermission("booking:lab:list")
    public Result<PageResult<SeriesVO>> pageSeries(@Valid @ModelAttribute SeriesPageQuery query) {
        return Result.success(seriesService.pageMine(query));
    }

    /** 全部周期预约分页（审批视角） */
    @GetMapping("/series/all")
    @SaCheckPermission("booking:lab:approve")
    public Result<PageResult<SeriesVO>> pageAllSeries(@Valid @ModelAttribute SeriesPageQuery query) {
        return Result.success(seriesService.pageAll(query));
    }

    /** 周期预约详情（本人或审批/只读权限，附生成的各周预约单） */
    @GetMapping("/series/{id}")
    @SaCheckPermission(value = {"booking:lab:list", "booking:lab:view", "booking:lab:approve"},
            mode = SaMode.OR)
    public Result<SeriesDetailVO> seriesDetail(@PathVariable Long id) {
        return Result.success(seriesService.detail(id));
    }

    /** 管理员批准周期预约（场地行锁内逐段冲突复检后批量生成占用） */
    @PostMapping("/series/{id}/approve")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> approveSeries(@PathVariable Long id) {
        sensitiveGuard.checkSafe("周期预约批准");
        seriesService.approve(id);
        return Result.success();
    }

    /** 管理员整单驳回周期预约（原因必填） */
    @PostMapping("/series/{id}/reject")
    @SaCheckPermission("booking:lab:approve")
    public Result<Void> rejectSeries(@PathVariable Long id, @Valid @RequestBody BookingRejectDTO dto) {
        sensitiveGuard.checkSafe("周期预约驳回");
        seriesService.reject(id, dto);
        return Result.success();
    }

    /** 申请人本人撤销周期预约（已批准时同步取消各周生成单并释放占用） */
    @PostMapping("/series/{id}/cancel")
    @SaCheckPermission("booking:lab:cancel")
    public Result<Void> cancelSeries(@PathVariable Long id,
                                     @Valid @RequestBody(required = false) BookingCancelDTO dto) {
        seriesService.cancel(id, dto);
        return Result.success();
    }
}
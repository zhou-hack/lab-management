package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.dto.MaterialRequestReturnDTO;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
import com.labmanage.module.item.vo.ReturnableLineVO;
import com.labmanage.module.item.vo.TeacherOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料申领管理（申领 → 两级审核 → 实发出库）
 */
@Tag(name = "物料申领管理", description = "申领单登记、两级审核与实发；本人申领经老师审核+管理员终审，"
        + "学生代理代领只能引用老师已审核通过的源单并直接交管理员终审；批准不扣库存，实发走库存单据过账回写")
@RestController
@RequestMapping("/api/v1/material-requests")
public class MaterialRequestController {

    private final MaterialRequestService requestService;

    public MaterialRequestController(MaterialRequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "登记申领单（本人申领 / 学生代理代领）",
            description = "requestType=SELF 本人申领：学生必填审批教师（ownerTeacherId）落草稿待老师初审，"
                    + "老师直落老师已审核交管理员终审；自动取开课定额快照并计算定额量"
                    + "（参与人数 × 课时 × 每人每课时定额），超定额标记 requires_extra_approval，"
                    + "危险/高价值物料标记 requires_dual_sign；requestType=PROXY 学生代理代领"
                    + "必须指定老师已审核通过的源申领单（sourceRequestId），行上 sourceLineId"
                    + " 绑定源申领行并校验不超过源行剩余可代领量（批准量 − 实发量 − 已占用量）；"
                    + "代领人强制为当前登录学生，须持有源单归属老师的有效助理授权（命中授权留档 grant_id），"
                    + "创建即 TEACHER_APPROVED 直接交管理员终审；返回申领单 ID")
    @RequirePermission("item:request:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody MaterialRequestCreateDTO dto) {
        return Result.success(requestService.create(dto));
    }

    @Operation(summary = "分页查询申领单",
            description = "支持按状态与申领类型（SELF 本人申领 / PROXY 学生代理代领）过滤，"
                    + "行上带责任老师与代领人姓名")
    @RequirePermission("item:request:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialRequestVO>> page(@Valid MaterialRequestQuery query) {
        return Result.success(requestService.page(query));
    }

    @Operation(summary = "申领单详情（含申领行）",
            description = "含申领类型、源申领单、责任老师与代领人姓名")
    @RequirePermission("item:request:list")
    @GetMapping("/{id}")
    public Result<MaterialRequestDetailVO> get(@Parameter(description = "申领单 ID") @PathVariable String id) {
        return Result.success(requestService.get(id));
    }

    @Operation(summary = "老师审核申领单（草稿 → 老师已审核）",
            description = "本人申领第一级审核：逐行写批准量（不超过申请量，未指定的行按申请量批准），"
                    + "审核通过后交管理员终审；学生代理代领单不经过本环节")
    @RequirePermission("item:request:teacher_approve")
    @PostMapping("/{id}/teacher-approve")
    public Result<Void> teacherApprove(@Parameter(description = "申领单 ID") @PathVariable String id,
                                       @Valid @RequestBody(required = false) MaterialRequestApproveDTO dto) {
        requestService.teacherApprove(id, dto);
        return Result.success();
    }

    @Operation(summary = "老师审核拒绝申领单（草稿 → 已拒绝）")
    @RequirePermission("item:request:teacher_approve")
    @PostMapping("/{id}/teacher-reject")
    public Result<Void> teacherReject(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.teacherReject(id);
        return Result.success();
    }

    @Operation(summary = "管理员终审申领单（老师已审核 → 已批准）",
            description = "第二级审核（终审）：本人申领与代理代领单均由管理员终审；"
                    + "逐行写批准量（不超过申请量），未指定的行保持既有批准量"
                    + "（老师已审核的单保持老师批准量，代领行按申请量）；批准不扣库存")
    @RequirePermission("item:request:approve")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@Parameter(description = "申领单 ID") @PathVariable String id,
                                @Valid @RequestBody(required = false) MaterialRequestApproveDTO dto) {
        requestService.approve(id, dto);
        return Result.success();
    }

    @Operation(summary = "管理员终审拒绝申领单（老师已审核 → 已拒绝）")
    @RequirePermission("item:request:approve")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.reject(id);
        return Result.success();
    }

    @Operation(summary = "学生代理代领源申领单分页",
            description = "查询老师已审核通过（TEACHER_APPROVED）的本人申领单作为可代领源单，"
                    + "支持按责任老师、实验室过滤；行上带责任老师姓名")
    @RequirePermission("item:request:create")
    @GetMapping("/proxy-sources")
    public Result<PageResult<ProxySourceVO>> proxySources(@Valid MaterialRequestQuery query) {
        return Result.success(requestService.proxySources(query));
    }

    @Operation(summary = "源申领单行（含剩余可代领量）",
            description = "代领表单选择源单后加载行：剩余可代领量 = 批准量 − 实发量 − "
                    + "已被有效代领单（未取消、未拒绝）占用的申请量；行上 sourceLineId 为代领提交参数")
    @RequirePermission("item:request:create")
    @GetMapping("/proxy-sources/{id}/lines")
    public Result<List<ProxyLineVO>> proxyLines(
            @Parameter(description = "源申领单 ID") @PathVariable String id) {
        return Result.success(requestService.proxyLines(id));
    }

    @Operation(summary = "取消申领单（未实发才可取消）")
    @RequirePermission("item:request:cancel")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "审批教师下拉选项（TEACHER 角色启用用户）",
            description = "学生申领必填审批教师（初审人）；老师申领无需选择，服务端强制归属本人")
    @RequirePermission("item:request:create")
    @GetMapping("/teacher_options")
    public Result<List<TeacherOptionVO>> teacherOptions() {
        return Result.success(requestService.teacherOptions());
    }

    @Operation(summary = "申领实发出库（生成出库单草稿）",
            description = "生成关联申领行的领用出库单（DRAFT），返回出库单 ID；"
                    + "后续经库存单据审批、过账完成扣库存并回写实发量，全部行发完自动推进 FULFILLED")
    @RequirePermission("item:request:issue")
    @PostMapping("/{id}/issue")
    public Result<String> issue(@Parameter(description = "申领单 ID") @PathVariable String id,
                                @Valid @RequestBody MaterialRequestIssueDTO dto) {
        return Result.success(requestService.issue(id, dto));
    }

    @Operation(summary = "申领单可归还行",
            description = "该申领单已过账出库行 + 累计已归还量 + 可归还量（出库量 − 已归还量）；"
                    + "非管理员仅限本人经办单")
    @RequirePermission("item:request:list")
    @GetMapping("/{id}/returnable_lines")
    public Result<List<ReturnableLineVO>> returnableLines(
            @Parameter(description = "申领单 ID") @PathVariable String id) {
        return Result.success(requestService.returnableLines(id));
    }

    @Operation(summary = "申领单归还（生成归还单草稿）",
            description = "按原出库行生成 RETURN_IN 归还单（DRAFT）并关联申领单留档，返回归还单 ID；"
                    + "后续经库存单据审批、过账回增库存并生成归还结算；非管理员仅限本人经办单")
    @RequirePermission("item:request:return")
    @PostMapping("/{id}/return")
    public Result<String> returnRequest(@Parameter(description = "申领单 ID") @PathVariable String id,
                                        @Valid @RequestBody MaterialRequestReturnDTO dto) {
        return Result.success(requestService.returnRequest(id, dto));
    }
}

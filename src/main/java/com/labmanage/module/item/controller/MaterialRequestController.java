package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.vo.BookingOptionVO;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料申领管理（申领 → 管理员终审 → 实发出库）
 */
@Tag(name = "物料申领管理", description = "申领单登记、管理员终审与实发；老师预约本人发起创建即「老师已审核」，"
        + "直接交管理员终审；学生代理代领只能引用老师端已审核通过的老师预约单（免老师级审核、"
        + "老师作为第一责任人继承源单），同样交管理员终审；批准不扣库存，实发走库存单据过账回写")
@RestController
@RequestMapping("/api/v1/material-requests")
public class MaterialRequestController {

    private final MaterialRequestService requestService;

    public MaterialRequestController(MaterialRequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "登记申领单（老师预约 / 学生代理代领）",
            description = "requestType=TEACHER（缺省）老师预约：老师本人发起并作为第一责任人（ownerTeacherId 必填），"
                    + "自动取开课定额快照并计算定额量"
                    + "（参与人数 × 课时 × 每人每课时定额），超定额标记 requires_extra_approval，"
                    + "危险/高价值物料标记 requires_dual_sign，创建即 TEACHER_APPROVED 直接交管理员终审；"
                    + "requestType=PROXY 学生代理代领：必须指定老师端已审核通过的源申领单（sourceRequestId），"
                    + "行上 sourceLineId 绑定源申领行并校验不超过源行剩余可代领量"
                    + "（批准量 − 实发量 − 已占用量），老师作为第一责任人继承源单（免老师级审核），"
                    + "创建即 TEACHER_APPROVED 直接交管理员终审；返回申领单 ID")
    @SaCheckPermission("item:request:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody MaterialRequestCreateDTO dto) {
        return Result.success(requestService.create(dto));
    }

    @Operation(summary = "分页查询申领单",
            description = "支持按状态与申领类型（TEACHER 老师预约 / PROXY 学生代理代领）过滤，"
                    + "行上带责任老师与经办人（代领单即代领人）信息")
    @SaCheckPermission("item:request:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialRequestVO>> page(@Valid MaterialRequestQuery query) {
        return Result.success(requestService.page(query));
    }

    @Operation(summary = "申领单详情（含申领行）",
            description = "含责任老师与经办人姓名")
    @SaCheckPermission("item:request:list")
    @GetMapping("/{id}")
    public Result<MaterialRequestDetailVO> get(@Parameter(description = "申领单 ID") @PathVariable String id) {
        return Result.success(requestService.get(id));
    }

    @Operation(summary = "管理员终审申领单（待终审 → 已批准）",
            description = "老师预约创建即「老师已审核」，由管理员终审；"
                    + "逐行写批准量（不超过申请量），未指定的行保持既有批准量"
                    + "（保持创建时批准量=申请量）；批准不扣库存")
    @SaCheckPermission("item:request:approve")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@Parameter(description = "申领单 ID") @PathVariable String id,
                                @Valid @RequestBody(required = false) MaterialRequestApproveDTO dto) {
        requestService.approve(id, dto);
        return Result.success();
    }

    @Operation(summary = "管理员终审拒绝申领单（老师已审核 → 已拒绝）")
    @SaCheckPermission("item:request:approve")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.reject(id);
        return Result.success();
    }

    @Operation(summary = "已预约课程候选搜索",
            description = "新建申领单选择关联场地预约用；仅待审批/已批准状态，"
                    + "按场地/课程/班级/用途前缀匹配，最多 20 条")
    @SaCheckPermission("item:request:create")
    @GetMapping("/booking-options")
    public Result<List<BookingOptionVO>> bookingOptions(
            @Parameter(description = "搜索关键字（场地/课程/班级/用途前缀）") @RequestParam(required = false) String keyword) {
        return Result.success(requestService.bookingOptions(keyword));
    }

    @Operation(summary = "老师候选搜索",
            description = "老师预约选择归属老师用；仅启用 TEACHER 角色用户，按姓名前缀匹配，最多 20 条")
    @SaCheckPermission("item:request:create")
    @GetMapping("/teacher-options")
    public Result<List<TeacherOptionVO>> teacherOptions(
            @Parameter(description = "搜索关键字（老师姓名前缀）") @RequestParam(required = false) String keyword) {
        return Result.success(requestService.teacherOptions(keyword));
    }

    @Operation(summary = "学生代理代领源申领单分页",
            description = "查询老师端已审核通过（TEACHER_APPROVED）的老师预约单作为可代领源单，"
                    + "支持按责任老师、实验室过滤；行上带责任老师姓名")
    @SaCheckPermission("item:request:create")
    @GetMapping("/proxy-sources")
    public Result<PageResult<ProxySourceVO>> proxySources(@Valid MaterialRequestQuery query) {
        return Result.success(requestService.proxySources(query));
    }

    @Operation(summary = "源申领单行（含剩余可代领量）",
            description = "代领表单选择源单后加载行：剩余可代领量 = 批准量 − 实发量 − "
                    + "已被有效代领单（未取消、未拒绝）占用的申请量；行上 lineId 为代领提交参数 sourceLineId")
    @SaCheckPermission("item:request:create")
    @GetMapping("/proxy-sources/{id}/lines")
    public Result<List<ProxyLineVO>> proxyLines(
            @Parameter(description = "源申领单 ID") @PathVariable String id) {
        return Result.success(requestService.proxyLines(id));
    }

    @Operation(summary = "取消申领单（未实发才可取消）")
    @SaCheckPermission("item:request:cancel")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "申领实发出库（生成出库单草稿）",
            description = "生成关联申领行的领用出库单（DRAFT），返回出库单 ID；"
                    + "后续经库存单据审批、过账完成扣库存并回写实发量，全部行发完自动推进 FULFILLED")
    @SaCheckPermission("item:request:issue")
    @PostMapping("/{id}/issue")
    public Result<String> issue(@Parameter(description = "申领单 ID") @PathVariable String id,
                                @Valid @RequestBody MaterialRequestIssueDTO dto) {
        return Result.success(requestService.issue(id, dto));
    }
}

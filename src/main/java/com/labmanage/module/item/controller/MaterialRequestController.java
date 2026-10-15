package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
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

/**
 * 物料申领管理（申领 → 审批 → 实发出库）
 */
@Tag(name = "物料申领管理", description = "申领单登记、审批与实发；批准不扣库存，实发走库存单据过账回写")
@RestController
@RequestMapping("/api/v1/material-requests")
public class MaterialRequestController {

    private final MaterialRequestService requestService;

    public MaterialRequestController(MaterialRequestService requestService) {
        this.requestService = requestService;
    }

    @Operation(summary = "登记申领单（草稿）",
            description = "自动取开课定额快照并计算定额量（参与人数 × 课时 × 每人每课时定额）；"
                    + "超定额标记 requires_extra_approval，危险/高价值物料标记 requires_dual_sign；返回申领单 ID")
    @SaCheckPermission("item:request:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody MaterialRequestCreateDTO dto) {
        return Result.success(requestService.create(dto));
    }

    @Operation(summary = "分页查询申领单")
    @SaCheckPermission("item:request:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialRequestVO>> page(@Valid MaterialRequestQuery query) {
        return Result.success(requestService.page(query));
    }

    @Operation(summary = "申领单详情（含申领行）")
    @SaCheckPermission("item:request:list")
    @GetMapping("/{id}")
    public Result<MaterialRequestDetailVO> get(@Parameter(description = "申领单 ID") @PathVariable String id) {
        return Result.success(requestService.get(id));
    }

    @Operation(summary = "审批申领单（草稿 → 已批准）",
            description = "逐行写批准量（不超过申请量，未指定的行按申请量批准）；批准不扣库存")
    @SaCheckPermission("item:request:approve")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@Parameter(description = "申领单 ID") @PathVariable String id,
                                @Valid @RequestBody(required = false) MaterialRequestApproveDTO dto) {
        requestService.approve(id, dto);
        return Result.success();
    }

    @Operation(summary = "拒绝申领单（草稿 → 已拒绝）")
    @SaCheckPermission("item:request:approve")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@Parameter(description = "申领单 ID") @PathVariable String id) {
        requestService.reject(id);
        return Result.success();
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

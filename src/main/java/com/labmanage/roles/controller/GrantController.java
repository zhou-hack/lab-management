package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.dto.GrantPageQueryDTO;
import com.labmanage.roles.service.AssistantGrantService;
import com.labmanage.roles.vo.GrantPageItemVO;
import com.labmanage.roles.vo.StudentOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 助理授权（老师发起；管理员审计）。ASSISTANT 角色由有效授权动态计算，不持久化。
 */
@Tag(name = "助理授权")
@RestController
@RequestMapping("/api/v1/grants")
public class GrantController {

    private final AssistantGrantService grantService;

    public GrantController(AssistantGrantService grantService) {
        this.grantService = grantService;
    }

    @Operation(summary = "我的授权分页（老师）")
    @RequirePermission("system:grant:list")
    @GetMapping
    public Result<PageResult<GrantPageItemVO>> pageMine(@Valid GrantPageQueryDTO query) {
        return Result.success(grantService.pageMine(query));
    }

    @Operation(summary = "创建授权（最长一学年）")
    @RequirePermission("system:grant:create")
    @OperationLog(moduleCode = "system", actionCode = "system:grant:create", targetTable = "assistant_grant")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody GrantCreateDTO dto) {
        grantService.create(dto);
        return Result.success();
    }

    @Operation(summary = "撤销授权（仅本人 ACTIVE 授权）")
    @RequirePermission("system:grant:revoke")
    @OperationLog(moduleCode = "system", actionCode = "system:grant:revoke", targetTable = "assistant_grant", targetId = "#id")
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        grantService.revoke(id);
        return Result.success();
    }

    @Operation(summary = "被授权学生搜索")
    @RequirePermission("system:grant:create")
    @GetMapping("/student_options")
    public Result<List<StudentOptionVO>> studentOptions(
            @RequestParam(required = false) @Size(max = 32, message = "关键字长度不能超过 32") String keyword) {
        return Result.success(grantService.studentOptions(keyword));
    }

    @Operation(summary = "授权审计分页（管理员全量）")
    @RequirePermission("system:grant:audit")
    @GetMapping("/audit")
    public Result<PageResult<GrantPageItemVO>> pageAudit(@Valid GrantPageQueryDTO query) {
        return Result.success(grantService.pageAudit(query));
    }
}

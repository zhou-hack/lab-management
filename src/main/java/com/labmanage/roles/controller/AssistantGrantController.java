package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.core.BasePageQuery;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.config.SensitiveOperationGuard;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.service.AssistantGrantService;
import com.labmanage.roles.vo.GrantVO;
import com.labmanage.roles.vo.StudentOptionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 助理授权接口（老师本人管理名下学生助理）。
 * 前缀 /api/v1/system/grants；权限 system:grant:list/create/revoke。
 */
@RestController
@RequestMapping("/api/v1/system/grants")
public class AssistantGrantController {

    private final AssistantGrantService grantService;

    private final SensitiveOperationGuard sensitiveGuard;

    public AssistantGrantController(AssistantGrantService grantService, SensitiveOperationGuard sensitiveGuard) {
        this.grantService = grantService;
        this.sensitiveGuard = sensitiveGuard;
    }

    @GetMapping
    @SaCheckPermission("system:grant:list")
    public Result<PageResult<GrantVO>> pageMine(@Valid @ModelAttribute BasePageQuery query) {
        return Result.success(grantService.pageMine(query));
    }

    @GetMapping("/students")
    @SaCheckPermission("system:grant:create")
    public Result<List<StudentOptionVO>> searchStudents(@RequestParam(required = false) String keyword) {
        return Result.success(grantService.searchStudents(keyword));
    }

    @PostMapping
    @SaCheckPermission("system:grant:create")
    public Result<Map<String, String>> create(@Valid @RequestBody GrantCreateDTO dto) {
        sensitiveGuard.checkSafe("助理授权创建");
        return Result.success(Map.of("id", String.valueOf(grantService.create(dto))));
    }

    @PostMapping("/{id}/revoke")
    @SaCheckPermission("system:grant:revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        sensitiveGuard.checkSafe("助理授权撤销");
        grantService.revoke(id);
        return Result.success(null);
    }
}

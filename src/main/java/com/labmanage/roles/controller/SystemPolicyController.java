package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.PolicyPageQueryDTO;
import com.labmanage.roles.dto.PolicyPublishDTO;
import com.labmanage.roles.service.SysPolicyService;
import com.labmanage.roles.vo.PolicyPageItemVO;
import com.labmanage.roles.vo.PolicySchemaFieldVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理-参数配置（业务策略版本化发布，发布后不可修改）
 */
@Tag(name = "系统-参数配置")
@RestController
@RequestMapping("/api/v1/system/policies")
public class SystemPolicyController {

    private final SysPolicyService sysPolicyService;

    public SystemPolicyController(SysPolicyService sysPolicyService) {
        this.sysPolicyService = sysPolicyService;
    }

    @Operation(summary = "策略分页列表（全部版本）")
    @RequirePermission("system:policy:list")
    @GetMapping
    public Result<PageResult<PolicyPageItemVO>> page(@Valid PolicyPageQueryDTO query) {
        return Result.success(sysPolicyService.page(query));
    }

    @Operation(summary = "全部策略编码的参数字段元数据（结构化表单 schema）")
    @RequirePermission("system:policy:list")
    @GetMapping("/schemas")
    public Result<Map<String, List<PolicySchemaFieldVO>>> schemas() {
        return Result.success(sysPolicyService.schemas());
    }

    @Operation(summary = "发布新版本（区间不重叠，发布即刷新读取缓存；敏感操作需二级认证）")
    @RequirePermission("system:policy:publish")
    @OperationLog(moduleCode = "system", actionCode = "system:policy:publish", targetTable = "business_policy")
    @PostMapping("/publish")
    public Result<Integer> publish(@Valid @RequestBody PolicyPublishDTO dto) {
        SecurityUtils.checkSafe();
        return Result.success(sysPolicyService.publish(dto));
    }
}

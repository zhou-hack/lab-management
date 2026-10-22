package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.LogPageQueryDTO;
import com.labmanage.roles.service.SysLogService;
import com.labmanage.roles.vo.LogPageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-操作日志（只读审计；日志由 @OperationLog 切面采集，只追加）
 */
@Tag(name = "系统-操作日志")
@RestController
@RequestMapping("/api/v1/system/logs")
public class SystemLogController {

    private final SysLogService logService;

    public SystemLogController(SysLogService logService) {
        this.logService = logService;
    }

    @Operation(summary = "操作日志分页列表")
    @SaCheckPermission("system:log:list")
    @GetMapping
    public Result<PageResult<LogPageItemVO>> page(@Valid LogPageQueryDTO query) {
        return Result.success(logService.page(query));
    }
}

package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.PersonBatchDepartmentDTO;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.service.SysPersonService;
import com.labmanage.roles.vo.PersonPageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-统一人员视图（名单与账号合并，一行一人）
 */
@Tag(name = "系统-统一人员视图")
@RestController
@RequestMapping("/api/v1/system/persons")
public class SystemPersonController {

    private final SysPersonService personService;

    public SystemPersonController(SysPersonService personService) {
        this.personService = personService;
    }

    @Operation(summary = "统一人员分页（名单+账号）")
    @SaCheckPermission(value = { "system:user:list", "system:roster:list" })
    @GetMapping
    public Result<PageResult<PersonPageItemVO>> page(@Validated PersonPageQueryDTO query) {
        return Result.success(personService.page(query));
    }

    @Operation(summary = "批量修改部门（名单与账号可混合，整批事务）")
    @SaCheckPermission("system:user:batch_dept")
    @PutMapping("/department")
    public Result<Integer> batchDepartment(@Validated @RequestBody PersonBatchDepartmentDTO dto) {
        // 批量变更数据归属，属敏感操作，需二次密码确认
        StpUtil.checkSafe();
        return Result.success(personService.batchUpdateDepartment(dto));
    }
}

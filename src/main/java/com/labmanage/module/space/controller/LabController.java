package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.LabCreateRequest;
import com.labmanage.module.space.dto.LabPageQuery;
import com.labmanage.module.space.dto.LabUpdateRequest;
import com.labmanage.module.space.service.LabService;
import com.labmanage.module.space.vo.LabOptionVO;
import com.labmanage.module.space.vo.LabVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实验室管理
 */
@Tag(name = "空间管理-实验室")
@RestController
@RequestMapping("/api/v1/space/labs")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @Operation(summary = "实验室分页查询")
    @SaCheckPermission("space:lab:list")
    @GetMapping
    public Result<PageResult<LabVO>> page(LabPageQuery query) {
        return Result.success(labService.page(query));
    }

    @Operation(summary = "实验室下拉选项")
    @SaCheckLogin
    @GetMapping("/options")
    public Result<List<LabOptionVO>> options() {
        return Result.success(labService.options());
    }

    @Operation(summary = "新增实验室")
    @SaCheckPermission("space:lab:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody LabCreateRequest request) {
        labService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改实验室")
    @SaCheckPermission("space:lab:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody LabUpdateRequest request) {
        labService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除实验室")
    @SaCheckPermission("space:lab:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        labService.delete(id);
        return Result.success();
    }
}

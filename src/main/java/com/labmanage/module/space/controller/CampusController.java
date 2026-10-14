package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.CampusCreateRequest;
import com.labmanage.module.space.dto.CampusPageQuery;
import com.labmanage.module.space.dto.CampusUpdateRequest;
import com.labmanage.module.space.service.CampusService;
import com.labmanage.module.space.vo.CampusVO;
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

/**
 * 校区管理
 */
@Tag(name = "空间管理-校区")
@RestController
@RequestMapping("/api/v1/space/campuses")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @Operation(summary = "校区分页查询")
    @SaCheckPermission("space:campus:list")
    @GetMapping
    public Result<PageResult<CampusVO>> page(CampusPageQuery query) {
        return Result.success(campusService.page(query));
    }

    @Operation(summary = "新增校区")
    @SaCheckPermission("space:campus:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CampusCreateRequest request) {
        campusService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改校区")
    @SaCheckPermission("space:campus:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody CampusUpdateRequest request) {
        campusService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除校区")
    @SaCheckPermission("space:campus:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        campusService.delete(id);
        return Result.success();
    }
}

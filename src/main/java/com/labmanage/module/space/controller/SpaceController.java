package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.service.SpaceService;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场地接口。
 * 前缀 /api/v1/space/spaces；权限 space:space:*。
 */
@RestController
@RequestMapping("/api/v1/space/spaces")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping
    @SaCheckPermission("space:space:list")
    public Result<PageResult<SpaceListVO>> page(@Valid @ModelAttribute SpacePageQuery query) {
        return Result.success(spaceService.page(query));
    }

    @GetMapping("/options")
    @SaCheckPermission("space:space:list")
    public Result<List<SpaceOptionVO>> options(@RequestParam(required = false) SpaceType spaceType,
                                                @RequestParam(required = false) Integer minCapacity,
                                                @RequestParam(required = false) Boolean availableOnly) {
        return Result.success(spaceService.options(spaceType, minCapacity, availableOnly));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("space:space:read")
    public Result<SpaceDetailVO> detail(@PathVariable Long id) {
        return Result.success(spaceService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("space:space:create")
    public Result<SpaceDetailVO> create(@Valid @RequestBody SpaceCreateDTO dto) {
        return Result.success(spaceService.create(dto));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("space:space:update")
    public Result<SpaceDetailVO> update(@PathVariable Long id, @Valid @RequestBody SpaceUpdateDTO dto) {
        return Result.success(spaceService.update(id, dto));
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission("space:space:update")
    public Result<Void> enable(@PathVariable Long id) {
        spaceService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission("space:space:update")
    public Result<Void> disable(@PathVariable Long id) {
        spaceService.disable(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("space:space:delete")
    public Result<Void> delete(@PathVariable Long id) {
        spaceService.delete(id);
        return Result.success();
    }
}

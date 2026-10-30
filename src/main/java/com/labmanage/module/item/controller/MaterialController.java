package com.labmanage.module.item.controller;

import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.MaterialPageQuery;
import com.labmanage.module.item.dto.MaterialUpsertDTO;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.service.MaterialService;
import com.labmanage.module.item.vo.MaterialOptionVO;
import com.labmanage.module.item.vo.MaterialVO;
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

@RestController
@RequestMapping("/api/v1/item/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    @RequirePermission("item:material:list")
    public Result<PageResult<MaterialVO>> page(@Valid @ModelAttribute MaterialPageQuery query) {
        return Result.success(materialService.page(query));
    }

    @GetMapping("/options")
    @RequirePermission(value = {"item:material:list", "item:claim:create"}, mode = RequirePermission.Mode.OR)
    public Result<List<MaterialOptionVO>> options(@RequestParam(required = false) MaterialType materialType,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(materialService.options(materialType, keyword));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:material:list")
    public Result<MaterialVO> detail(@PathVariable String id) {
        return Result.success(materialService.detail(Ids.parse(id)));
    }

    @PostMapping
    @RequirePermission("item:material:manage")
    public Result<MaterialVO> create(@Valid @RequestBody MaterialUpsertDTO dto) {
        return Result.success(materialService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("item:material:manage")
    public Result<MaterialVO> update(@PathVariable String id, @Valid @RequestBody MaterialUpsertDTO dto) {
        return Result.success(materialService.update(Ids.parse(id), dto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("item:material:manage")
    @OperationLog(moduleCode = "item", actionCode = "item:material:delete", targetTable = "material", targetId = "#id")
    public Result<Void> delete(@PathVariable String id) {
        materialService.delete(Ids.parse(id));
        return Result.success();
    }
}

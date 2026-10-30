package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.MaterialPlanMaterialQuery;
import com.labmanage.module.item.dto.MaterialPlanUpsertDTO;
import com.labmanage.module.item.service.MaterialPlanService;
import com.labmanage.module.item.vo.MaterialPlanMaterialVO;
import com.labmanage.module.item.vo.MaterialPlanVO;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/item/plans")
public class MaterialPlanController {

    private final MaterialPlanService planService;

    public MaterialPlanController(MaterialPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @RequirePermission("item:plan:list")
    public Result<List<MaterialPlanVO>> list(@RequestParam(required = false) Integer enabled) {
        return Result.success(planService.list(enabled));
    }

    @PostMapping
    @RequirePermission("item:plan:manage")
    public Result<MaterialPlanVO> create(@Valid @RequestBody MaterialPlanUpsertDTO dto) {
        return Result.success(planService.create(dto));
    }

    @GetMapping("/materials")
    @RequirePermission("item:plan:manage")
    public Result<PageResult<MaterialPlanMaterialVO>> materials(@Valid @ModelAttribute MaterialPlanMaterialQuery query) {
        return Result.success(planService.materials(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:plan:list")
    public Result<MaterialPlanVO> detail(@PathVariable String id) {
        return Result.success(planService.detail(Ids.parse(id)));
    }

    @PutMapping("/{id}")
    @RequirePermission("item:plan:manage")
    public Result<MaterialPlanVO> update(@PathVariable String id,
                                         @Valid @RequestBody MaterialPlanUpsertDTO dto) {
        return Result.success(planService.update(Ids.parse(id), dto));
    }
}

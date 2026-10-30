package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.StockLocationAdminsDTO;
import com.labmanage.module.item.dto.StockLocationPageQuery;
import com.labmanage.module.item.dto.StockLocationResponsibleHistoryPageQuery;
import com.labmanage.module.item.dto.StockLocationUpsertDTO;
import com.labmanage.module.item.service.StockLocationService;
import com.labmanage.module.item.vo.LocationInventoryVO;
import com.labmanage.module.item.vo.StockLocationAdminVO;
import com.labmanage.module.item.vo.StockLocationChangeVO;
import com.labmanage.module.item.vo.StockLocationOptionVO;
import com.labmanage.module.item.vo.StockLocationResponsibleHistoryVO;
import com.labmanage.module.item.vo.StockLocationVO;
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
@RequestMapping("/api/v1/item/locations")
public class StockLocationController {

    private final StockLocationService locationService;

    public StockLocationController(StockLocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    @RequirePermission("item:location:list")
    public Result<PageResult<StockLocationVO>> page(@Valid @ModelAttribute StockLocationPageQuery query) {
        return Result.success(locationService.page(query));
    }

    @GetMapping("/options")
    @RequirePermission(value = {"item:location:list", "item:claim:create"}, mode = RequirePermission.Mode.OR)
    public Result<List<StockLocationOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(locationService.options(keyword));
    }

    @GetMapping("/admin_options")
    @RequirePermission("item:location:manage")
    public Result<List<StockLocationAdminVO>> adminOptions(@RequestParam(required = false) String keyword) {
        return Result.success(locationService.adminOptions(keyword));
    }

    @GetMapping("/responsible_history")
    @RequirePermission("item:location:list")
    public Result<PageResult<StockLocationResponsibleHistoryVO>> responsibleHistory(
            @Valid @ModelAttribute StockLocationResponsibleHistoryPageQuery query) {
        return Result.success(locationService.responsibleHistory(query));
    }

    @GetMapping("/change_history")
    @RequirePermission("item:location:list")
    public Result<PageResult<StockLocationChangeVO>> changeHistory(
            @Valid @ModelAttribute StockLocationResponsibleHistoryPageQuery query) {
        return Result.success(locationService.changeHistory(query));
    }

    @GetMapping("/{id}/inventory")
    @RequirePermission("item:location:list")
    public Result<LocationInventoryVO> inventory(@PathVariable String id) {
        return Result.success(locationService.inventory(Ids.parse(id)));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:location:list")
    public Result<StockLocationVO> detail(@PathVariable String id) {
        return Result.success(locationService.detail(Ids.parse(id)));
    }

    @PostMapping
    @RequirePermission("item:location:manage")
    public Result<StockLocationVO> create(@Valid @RequestBody StockLocationUpsertDTO dto) {
        return Result.success(locationService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("item:location:manage")
    public Result<StockLocationVO> update(@PathVariable String id,
                                          @Valid @RequestBody StockLocationUpsertDTO dto) {
        return Result.success(locationService.update(Ids.parse(id), dto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("item:location:manage")
    public Result<Void> delete(@PathVariable String id) {
        locationService.delete(Ids.parse(id));
        return Result.success();
    }

    @PutMapping("/{id}/admins")
    @RequirePermission("item:location:manage")
    public Result<StockLocationVO> updateAdmins(@PathVariable String id,
                                                @Valid @RequestBody StockLocationAdminsDTO dto) {
        return Result.success(locationService.updateAdmins(Ids.parse(id), dto));
    }
}

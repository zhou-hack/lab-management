package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.SpaceBatchStatusDTO;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.service.SpaceService;
import com.labmanage.module.space.vo.SpaceBatchStatusVO;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import com.labmanage.module.space.vo.SpaceStatsVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.time.LocalDate;
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

    /** CSV 导出（与分页同条件；UTF-8 BOM，时间按上海时区展示） */
    @GetMapping("/export")
    @SaCheckPermission("space:space:list")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute SpacePageQuery query) {
        byte[] csv = spaceService.exportCsv(query);
        String filename = "spaces_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    /** 批量启停（target 仅 AVAILABLE/CLOSED；停用为危险操作，前端需二次确认） */
    @PostMapping("/batch_status")
    @SaCheckPermission("space:space:update")
    public Result<SpaceBatchStatusVO> batchStatus(@Valid @RequestBody SpaceBatchStatusDTO dto) {
        return Result.success(spaceService.batchStatus(dto));
    }

    @GetMapping("/options")
    @SaCheckPermission("space:space:list")
    public Result<List<SpaceOptionVO>> options(@RequestParam(required = false) SpaceType spaceType,
                                                @RequestParam(required = false) Integer minCapacity,
                                                @RequestParam(required = false) Boolean availableOnly) {
        return Result.success(spaceService.options(spaceType, minCapacity, availableOnly));
    }

    @GetMapping("/stats")
    @SaCheckPermission("space:space:list")
    public Result<SpaceStatsVO> stats() {
        return Result.success(spaceService.stats());
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

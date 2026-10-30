package com.labmanage.module.space.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.SpaceBatchStatusDTO;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.MaintenanceWindowCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.service.SpaceMaintenanceService;
import com.labmanage.module.space.service.SpaceService;
import com.labmanage.module.space.vo.MaintenanceWindowVO;
import com.labmanage.module.space.vo.SpaceBatchStatusVO;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceImportVO;
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
import org.springframework.web.multipart.MultipartFile;

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

    private final SpaceMaintenanceService maintenanceService;

    public SpaceController(SpaceService spaceService, SpaceMaintenanceService maintenanceService) {
        this.spaceService = spaceService;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    @RequirePermission("space:space:list")
    public Result<PageResult<SpaceListVO>> page(@Valid @ModelAttribute SpacePageQuery query) {
        return Result.success(spaceService.page(query));
    }

    /** 场地维修时段列表（生效中：含进行中与未来，场地管理「维修时段」弹窗） */
    @GetMapping("/{id}/maintenance_windows")
    @RequirePermission("space:space:read")
    public Result<List<MaintenanceWindowVO>> listMaintenanceWindows(@PathVariable Long id) {
        return Result.success(maintenanceService.listActive(id));
    }

    /** 设置维修时段（与已批准预约重叠 → 40900 拒绝，须先取消重叠预约） */
    @PostMapping("/{id}/maintenance_windows")
    @RequirePermission("space:space:update")
    public Result<MaintenanceWindowVO> createMaintenanceWindow(@PathVariable Long id,
                                                               @Valid @RequestBody MaintenanceWindowCreateDTO dto) {
        return Result.success(maintenanceService.create(id, dto));
    }

    /** 取消维修时段（幂等；不存在或已取消 → 40400） */
    @PostMapping("/{id}/maintenance_windows/{windowId}/cancel")
    @RequirePermission("space:space:update")
    public Result<Void> cancelMaintenanceWindow(@PathVariable Long id, @PathVariable Long windowId) {
        maintenanceService.cancel(id, windowId);
        return Result.success();
    }

    /** CSV 导出（与分页同条件；UTF-8 BOM，时间按上海时区展示） */
    @GetMapping("/export")
    @RequirePermission("space:space:list")
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
    @RequirePermission("space:space:update")
    public Result<SpaceBatchStatusVO> batchStatus(@Valid @RequestBody SpaceBatchStatusDTO dto) {
        return Result.success(spaceService.batchStatus(dto));
    }

    /** 导入模板下载（表头与导出完全一致的空 CSV，UTF-8 BOM） */
    @GetMapping("/import/template")
    @RequirePermission("space:space:create")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] csv = spaceService.importTemplateCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=spaces_import_template.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    /**
     * 场地批量导入（CSV/Excel，单文件 ≤10MB、≤5000 数据行；部分成功）。
     * 权限复用 space:space:create；仅写 lab 表，不影响预约等下游业务。
     */
    @PostMapping("/import")
    @RequirePermission("space:space:create")
    public Result<SpaceImportVO> importSpaces(@RequestParam("file") MultipartFile file) {
        return Result.success(spaceService.importSpaces(file));
    }

    @GetMapping("/options")
    @RequirePermission("space:space:list")
    public Result<List<SpaceOptionVO>> options(@RequestParam(required = false) SpaceType labType,
                                                @RequestParam(required = false) Integer minCapacity,
                                                @RequestParam(required = false) Boolean availableOnly,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(spaceService.options(labType, minCapacity, availableOnly, keyword));
    }

    @GetMapping("/stats")
    @RequirePermission("space:space:list")
    public Result<SpaceStatsVO> stats() {
        return Result.success(spaceService.stats());
    }

    @GetMapping("/{id}")
    @RequirePermission("space:space:read")
    public Result<SpaceDetailVO> detail(@PathVariable Long id) {
        return Result.success(spaceService.detail(id));
    }

    @PostMapping
    @RequirePermission("space:space:create")
    public Result<SpaceDetailVO> create(@Valid @RequestBody SpaceCreateDTO dto) {
        return Result.success(spaceService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("space:space:update")
    public Result<SpaceDetailVO> update(@PathVariable Long id, @Valid @RequestBody SpaceUpdateDTO dto) {
        return Result.success(spaceService.update(id, dto));
    }

    @PostMapping("/{id}/enable")
    @RequirePermission("space:space:update")
    public Result<Void> enable(@PathVariable Long id) {
        spaceService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    @RequirePermission("space:space:update")
    public Result<Void> disable(@PathVariable Long id) {
        spaceService.disable(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("space:space:delete")
    public Result<Void> delete(@PathVariable Long id) {
        spaceService.delete(id);
        return Result.success();
    }
}

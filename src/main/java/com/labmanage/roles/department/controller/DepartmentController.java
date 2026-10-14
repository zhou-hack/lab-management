package com.labmanage.roles.department.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labmanage.common.result.Result;
import com.labmanage.roles.department.entity.SysDepartment;
import com.labmanage.roles.department.mapper.SysDepartmentMapper;
import com.labmanage.roles.department.vo.DepartmentOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织（只读支撑：部门维护属后续基础数据模块）
 */
@Tag(name = "组织-部门")
@RestController
@RequestMapping("/api/v1/roles/departments")
public class DepartmentController {

    private final SysDepartmentMapper departmentMapper;

    public DepartmentController(SysDepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    @Operation(summary = "部门下拉选项（启用中，前端按 parentId 组装树）")
    @SaCheckLogin
    @GetMapping("/options")
    public Result<List<DepartmentOptionVO>> options() {
        List<SysDepartment> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>()
                        .eq(SysDepartment::getEnabled, 1)
                        .orderByAsc(SysDepartment::getId));
        List<DepartmentOptionVO> records = departments.stream()
                .map(department -> new DepartmentOptionVO(
                        department.getId().toString(),
                        department.getParentId() != null ? department.getParentId().toString() : null,
                        department.getDepartmentCode(),
                        department.getDepartmentName(),
                        department.getDepartmentType(),
                        department.getEnabled()
                ))
                .toList();
        return Result.success(records);
    }
}

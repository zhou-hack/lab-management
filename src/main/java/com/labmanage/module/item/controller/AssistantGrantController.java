package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.AssistantGrantCreateDTO;
import com.labmanage.module.item.dto.AssistantGrantPageQuery;
import com.labmanage.module.item.service.AssistantGrantService;
import com.labmanage.module.item.vo.AssistantGrantVO;
import com.labmanage.roles.vo.StudentOptionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/item/assistants")
public class AssistantGrantController {

    private final AssistantGrantService assistantGrantService;

    public AssistantGrantController(AssistantGrantService assistantGrantService) {
        this.assistantGrantService = assistantGrantService;
    }

    @GetMapping
    @RequirePermission("item:assistant:manage")
    public Result<PageResult<AssistantGrantVO>> pageMine(@Valid @ModelAttribute AssistantGrantPageQuery query) {
        return Result.success(assistantGrantService.pageMine(query));
    }

    @PostMapping
    @RequirePermission("item:assistant:manage")
    public Result<AssistantGrantVO> create(@Valid @RequestBody AssistantGrantCreateDTO dto) {
        return Result.success(assistantGrantService.create(dto));
    }

    @PostMapping("/{id}/revoke")
    @RequirePermission("item:assistant:manage")
    public Result<Void> revoke(@PathVariable String id) {
        assistantGrantService.revoke(Ids.parse(id));
        return Result.success();
    }

    @GetMapping("/student_options")
    @RequirePermission("item:assistant:manage")
    public Result<List<StudentOptionVO>> studentOptions(@RequestParam(required = false) String keyword) {
        return Result.success(assistantGrantService.studentOptions(keyword));
    }
}

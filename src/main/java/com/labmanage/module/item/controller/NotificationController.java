package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.NotificationPageQuery;
import com.labmanage.module.item.service.NotificationService;
import com.labmanage.module.item.vo.NotificationVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my")
    @RequirePermission("item:notification:list")
    public Result<PageResult<NotificationVO>> pageMine(@Valid @ModelAttribute NotificationPageQuery query) {
        return Result.success(notificationService.pageMine(query));
    }

    @GetMapping("/unread_count")
    @RequirePermission("item:notification:list")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    @RequirePermission("item:notification:read")
    public Result<Void> read(@PathVariable String id) {
        notificationService.read(Ids.parse(id));
        return Result.success();
    }

    @PostMapping("/read_all")
    @RequirePermission("item:notification:read")
    public Result<Integer> readAll() {
        return Result.success(notificationService.readAll());
    }
}

package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.NotificationPageQuery;
import com.labmanage.module.item.entity.MaterialNotification;
import com.labmanage.module.item.mapper.MaterialNotificationMapper;
import com.labmanage.module.item.vo.NotificationVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysPermissionMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final MaterialNotificationMapper notificationMapper;
    private final ItemTimeService timeService;
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;

    public NotificationService(MaterialNotificationMapper notificationMapper,
                               ItemTimeService timeService,
                               SysUserMapper userMapper,
                               SysPermissionMapper permissionMapper) {
        this.notificationMapper = notificationMapper;
        this.timeService = timeService;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
    }

    public boolean createSystemNotification(MaterialNotification notification, Instant now) {
        notification.setId(IdWorker.getId());
        notification.setReadAt(null);
        notification.setCreateTime(now);
        notification.setUpdateTime(now);
        try {
            return notificationMapper.insertSystemNotification(notification) == 1;
        } catch (DuplicateKeyException e) {
            long existing = notificationMapper.selectCount(Wrappers.<MaterialNotification>lambdaQuery()
                    .eq(MaterialNotification::getRecipientUserId, notification.getRecipientUserId())
                    .eq(MaterialNotification::getDedupeKey, notification.getDedupeKey()));
            if (existing > 0) {
                return false;
            }
            throw e;
        }
    }

    public MaterialNotification latestByType(Long recipientUserId, Long claimId, String notificationType) {
        return notificationMapper.selectOne(Wrappers.<MaterialNotification>lambdaQuery()
                .eq(MaterialNotification::getRecipientUserId, recipientUserId)
                .eq(MaterialNotification::getClaimId, claimId)
                .eq(MaterialNotification::getNotificationType, notificationType)
                .orderByDesc(MaterialNotification::getCreateTime)
                .orderByDesc(MaterialNotification::getId)
                .last("LIMIT 1"));
    }

    public boolean canReceive(Long userId, String businessPermission) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            return false;
        }
        List<String> permissions = permissionMapper.selectPermissionCodesByUserId(userId);
        return permissions.contains("item:notification:list") && permissions.contains(businessPermission);
    }

    public PageResult<NotificationVO> pageMine(NotificationPageQuery query) {
        Long currentUserId = SecurityUtils.currentUserId();
        var wrapper = Wrappers.<MaterialNotification>lambdaQuery()
                .eq(MaterialNotification::getRecipientUserId, currentUserId);
        if (Boolean.TRUE.equals(query.getUnread())) {
            wrapper.isNull(MaterialNotification::getReadAt);
        } else if (Boolean.FALSE.equals(query.getUnread())) {
            wrapper.isNotNull(MaterialNotification::getReadAt);
        }
        wrapper.orderByDesc(MaterialNotification::getCreateTime)
                .orderByDesc(MaterialNotification::getId);
        Page<MaterialNotification> page = notificationMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    public Long unreadCount() {
        return notificationMapper.selectCount(Wrappers.<MaterialNotification>lambdaQuery()
                .eq(MaterialNotification::getRecipientUserId, SecurityUtils.currentUserId())
                .isNull(MaterialNotification::getReadAt));
    }

    public void read(Long id) {
        Long currentUserId = SecurityUtils.currentUserId();
        int updated = notificationMapper.markRead(id, currentUserId, timeService.now());
        if (updated == 1) {
            return;
        }
        boolean exists = notificationMapper.selectCount(Wrappers.<MaterialNotification>lambdaQuery()
                .eq(MaterialNotification::getId, id)
                .eq(MaterialNotification::getRecipientUserId, currentUserId)) > 0;
        if (!exists) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "通知不存在");
        }
    }

    public int readAll() {
        return notificationMapper.markAllRead(SecurityUtils.currentUserId(), timeService.now());
    }

    private NotificationVO toVO(MaterialNotification notification) {
        return new NotificationVO(str(notification.getId()), notification.getDedupeKey(),
                str(notification.getRecipientUserId()), notification.getNotificationType(),
                notification.getNotificationStage(), notification.getReminderDate(),
                str(notification.getClaimId()), str(notification.getMaterialDocumentId()),
                str(notification.getBizDocumentId()), notification.getTitle(), notification.getContent(),
                notification.getReadAt(), notification.getCreateTime());
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}

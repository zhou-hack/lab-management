package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

@TableName("material_notification")
public class MaterialNotification extends BaseEntity {

    private String dedupeKey;
    private Long recipientUserId;
    private String notificationType;
    private String notificationStage;
    private LocalDate reminderDate;
    private Long claimId;
    private Long materialDocumentId;
    private Long bizDocumentId;
    private String title;
    private String content;
    private Instant readAt;

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public String getNotificationStage() { return notificationStage; }
    public void setNotificationStage(String notificationStage) { this.notificationStage = notificationStage; }
    public LocalDate getReminderDate() { return reminderDate; }
    public void setReminderDate(LocalDate reminderDate) { this.reminderDate = reminderDate; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Long getMaterialDocumentId() { return materialDocumentId; }
    public void setMaterialDocumentId(Long materialDocumentId) { this.materialDocumentId = materialDocumentId; }
    public Long getBizDocumentId() { return bizDocumentId; }
    public void setBizDocumentId(Long bizDocumentId) { this.bizDocumentId = bizDocumentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}

package com.labmanage.module.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 单据事件实体（不可变过程历史；一次操作一行，request_key 防重复写入）
 */
@TableName("document_event")
public class DocumentEvent extends BaseEntity {

    /** 所属单据（→ biz_document.id） */
    private Long documentId;

    /** 事件类型：SUBMIT/APPROVE/REJECT/CANCEL/CHECKOUT/RETURN */
    private String eventType;

    /** 变更前业务状态 */
    private String fromStatus;

    /** 变更后业务状态 */
    private String toStatus;

    /** 操作人（→ sys_user.id；系统事件为 NULL） */
    private Long actorId;

    /** 操作人类型：USER/SYSTEM */
    private String actorType;

    /** 发生时间（UTC） */
    private Instant occurredAt;

    /** 事由/备注（驳回/撤销时填写） */
    private String reason;

    /** 关联审批任务（→ approval_task.id；一期不用，恒 NULL） */
    private Long taskId;

    /** 请求键（单次操作唯一） */
    private String requestKey;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }
}

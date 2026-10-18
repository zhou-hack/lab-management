package com.labmanage.module.booking.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.booking.flow.enums.ActorType;
import com.labmanage.module.booking.flow.enums.DocumentEventType;

import java.time.Instant;

/**
 * 单据事件实体（document_event，不可变过程历史）。
 * <p>
 * 每次状态/流程动作追加一行；uk(document_id, request_key) 保证
 * 同一预约同一事件类型仅一行，兼作幂等键。
 */
@TableName("document_event")
public class DocumentEvent extends BaseEntity {

    /** 所属单据（→ biz_document.id） */
    private Long documentId;

    /** 事件类型 */
    private DocumentEventType eventType;

    /** 变更前业务状态（可空） */
    private String fromStatus;

    /** 变更后业务状态（可空；签到/清点使用派生态 IN_USE/FINISHED） */
    private String toStatus;

    /** 操作人（→ sys_user.id；系统事件为 NULL） */
    private Long actorId;

    /** 操作人类型：USER/SYSTEM */
    private ActorType actorType;

    /** 发生时间（UTC） */
    private Instant occurredAt;

    /** 事由/备注（驳回原因、撤销原因、清点备注等） */
    private String reason;

    /** 关联审批任务（→ approval_task.id，可空） */
    private Long taskId;

    /** 请求键（"事件类型:"+预约ID，防重复写入） */
    private String requestKey;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public DocumentEventType getEventType() {
        return eventType;
    }

    public void setEventType(DocumentEventType eventType) {
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

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
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

package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

@TableName("material_assistant_grant")
public class MaterialAssistantGrant extends BaseEntity {

    private Long teacherId;
    private Long studentId;
    private Instant startAt;
    private Instant endAt;
    private Instant revokedAt;
    private Long revokedBy;
    private String reason;

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Long getRevokedBy() { return revokedBy; }
    public void setRevokedBy(Long revokedBy) { this.revokedBy = revokedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

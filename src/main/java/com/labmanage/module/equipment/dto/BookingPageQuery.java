package com.labmanage.module.equipment.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.equipment.enums.BookingStatus;

/**
 * 设备申请单分页查询参数。
 * 数据范围字段（applicantId/teacherId）由服务端填充，不接收前端赋值：
 * 本人申请 applicantId=当前用户、教师待办 teacherId=当前教师、全量为空。
 */
public class BookingPageQuery extends BasePageQuery {

    /** 状态过滤 */
    private BookingStatus status;

    /** 申请人范围（服务端填充） */
    private Long applicantId;

    /** 审批教师范围（服务端填充） */
    private Long teacherId;

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }
}

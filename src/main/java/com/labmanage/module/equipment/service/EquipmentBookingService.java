package com.labmanage.module.equipment.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.equipment.dto.BookingApplyRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.dto.BookingReviewRequest;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;

import java.util.List;

/**
 * 设备使用申请服务（三权级：学生/教师申请，教师初审，管理员终审与借出归还）
 */
public interface EquipmentBookingService {

    /** 提交使用申请（一台一单；学生待教师初审，教师普通单直接通过、贵重单转管理员终审） */
    void apply(BookingApplyRequest request);

    /** 本人申请分页 */
    PageResult<BookingVO> myBookings(BookingPageQuery query);

    /** 撤销本人申请（仅借出前） */
    void cancel(String id, BookingCancelRequest request);

    /** 教师待初审分页（指定审批教师待办） */
    PageResult<BookingVO> teacherPending(BookingPageQuery query);

    /** 教师初审：通过（普通即生效，贵重转终审）/ 驳回（意见必填） */
    void teacherReview(String id, BookingReviewRequest request);

    /** 全部申请分页（管理员） */
    PageResult<BookingVO> allBookings(BookingPageQuery query);

    /** 管理员终审/代批（贵重单终审；学生单超 24h 可代批） */
    void adminReview(String id, BookingReviewRequest request);

    /** 借出发放登记（APPROVED 单；设备置借出中，同事务建流水） */
    void checkout(String id);

    /** 归还登记（完好恢复空闲；损坏必填备注转维护中） */
    void returnAsset(String id, BookingReturnRequest request);

    /** 审批教师下拉（TEACHER 角色启用用户） */
    List<TeacherOptionVO> teacherOptions();
}

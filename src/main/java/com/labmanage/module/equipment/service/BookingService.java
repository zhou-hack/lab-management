package com.labmanage.module.equipment.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.equipment.dto.BookingApproveRequest;
import com.labmanage.module.equipment.dto.BookingCancelRequest;
import com.labmanage.module.equipment.dto.BookingCheckoutRequest;
import com.labmanage.module.equipment.dto.BookingCreateRequest;
import com.labmanage.module.equipment.dto.BookingPageQuery;
import com.labmanage.module.equipment.dto.BookingReturnRequest;
import com.labmanage.module.equipment.vo.BookingVO;
import com.labmanage.module.equipment.vo.TeacherOptionVO;

import java.util.List;

/**
 * 器材借用服务（核心借用链）
 * <p>
 * 学生单两级：PENDING_TEACHER →（老师批）→ PENDING_ADMIN →（管理员批）→ APPROVED
 * →（借出）→ CHECKED_OUT →（归还）→ RETURNED；
 * 老师单一级：PENDING_ADMIN →（管理员批）→ APPROVED → ... 。
 */
public interface BookingService {

    /**
     * 提交借用单：锁资产行校验后同事务建单据、业务单与审批任务；
     * 幂等键命中时返回原单
     */
    BookingVO create(BookingCreateRequest request);

    /**
     * 借用单分页查询；scope 取值：
     * MINE=本人单据，PENDING=待我处理（老师/管理员按权限分流），
     * ALL=全量（仅管理员）
     */
    PageResult<BookingVO> page(BookingPageQuery query);

    /**
     * 借用单详情；仅申请人、指定审批老师与管理员可见
     */
    BookingVO detail(String id);

    /**
     * 撤销（仅 PENDING 态申请人本人；链式取消未完结审批任务）
     */
    void cancel(String id, BookingCancelRequest request);

    /**
     * 老师审批（仅指定审批老师；通过后激活管理员审批，驳回须填意见）
     */
    void teacherApprove(String id, BookingApproveRequest request);

    /**
     * 管理员审批（通过须复查资产与时段冲突；驳回须填意见）
     */
    void adminApprove(String id, BookingApproveRequest request);

    /**
     * 借出确认（仅已批准且在借用时间窗口内；生成借出流水，设备转使用中）
     */
    void checkout(String id, BookingCheckoutRequest request);

    /**
     * 归还确认（归还流水落账，设备按剩余生效单重算状态；损坏转维护中）
     */
    void confirmReturn(String id, BookingReturnRequest request);

    /**
     * 审批老师下拉选项（学生提交借用单时指定）
     */
    List<TeacherOptionVO> teacherOptions();
}

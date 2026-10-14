package com.labmanage.module.booking.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingCreateDTO;
import com.labmanage.module.booking.dto.BookingPageQuery;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.vo.AvailabilityVO;
import com.labmanage.module.booking.vo.BookingDetailVO;
import com.labmanage.module.booking.vo.BookingStatsVO;
import com.labmanage.module.booking.vo.BookingVO;

import java.time.LocalDate;

/**
 * 场地预约业务接口。
 * <p>
 * 一期：TEACHER/ADMIN 发起、ADMIN 审批；PENDING/APPROVED/REJECTED/CANCELLED 四态，
 * IN_USE/FINISHED 派生展示；待审批不占用时段（提交与审批双重冲突校验，
 * 最后批准者遇冲突拒绝不抢占）；审批事务按场地行锁串行化，防并发双写。
 */
public interface BookingService {

    /** 提交预约申请（PENDING，不写占用） */
    BookingDetailVO create(BookingCreateDTO dto);

    /** 申请人本人取消（PENDING 直接取消；APPROVED 同步释放占用） */
    void cancel(Long id, BookingCancelDTO dto);

    /** 审批通过（场地行锁内复核冲突后写 lab_slot） */
    void approve(Long id);

    /** 驳回（原因必填，回显申请人） */
    void reject(Long id, BookingRejectDTO dto);

    /** 我的预约分页（当前登录人为申请人） */
    PageResult<BookingVO> pageMine(BookingPageQuery query);

    /** 全部预约分页（审批视角） */
    PageResult<BookingVO> pageAll(BookingPageQuery query);

    /** 预约详情（申请人本人或持有审批权限） */
    BookingDetailVO detail(Long id);

    /** 场地某日可用时段（上海本地日期，返回未释放占用） */
    AvailabilityVO availability(Long spaceId, LocalDate date);

    /** 预约状态统计（预约管理页卡片） */
    BookingStatsVO stats();
}

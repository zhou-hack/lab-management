package com.labmanage.module.booking.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingCheckoutDTO;
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
 * 权级流程（仅教师发起、学生只读、管理员审批）：
 * 提交(PENDING/待管理员核对) → 批准(APPROVED) → 开门签到(IN_USE 派生态)
 * → 结束清点(FINISHED 派生态)；驳回整单 REJECTED（原因详情可见）；
 * 本人可撤销；开始时间已过仍未核对由定时任务自动取消。
 * IN_USE/FINISHED 不落 lab_booking，仅由 document_event 与时间派生展示。
 */
public interface BookingService {

    /** 提交预约申请（PENDING + 建单据/核对任务/提交事件，不写占用） */
    BookingDetailVO create(BookingCreateDTO dto);

    /** 申请人本人取消（PENDING 直接取消；APPROVED 同步释放占用），写撤销事件 */
    void cancel(Long id, BookingCancelDTO dto);

    /** 管理员核对通过（任务 APPROVED + 批准事件 + 场地行锁内写占用） */
    void approve(Long id);

    /** 管理员整单驳回（任务 REJECTED + 驳回事件，原因必填回显申请人） */
    void reject(Long id, BookingRejectDTO dto);

    /** 管理员开门签到（APPROVED 且到达开始时间，写 CHECKIN 事件） */
    void checkin(Long id);

    /** 管理员结束清点（已签到，写 CHECKOUT 事件并释放占用槽） */
    void checkout(Long id, BookingCheckoutDTO dto);

    /** 我的预约分页（当前登录人为申请人） */
    PageResult<BookingVO> pageMine(BookingPageQuery query);

    /** 全部预约分页（审批视角） */
    PageResult<BookingVO> pageAll(BookingPageQuery query);

    /** 全部预约 CSV 导出（与分页同条件，UTF-8 BOM、上海时区，上限 10000 行） */
    byte[] exportBookings(BookingPageQuery query);

    /** 预约情况只读分页（booking:lab:view，学生只读：看预约情况与批准/驳回结果） */
    PageResult<BookingVO> pageBoard(BookingPageQuery query);

    /** 预约详情（申请人本人或持有审批权限，附流程时间线） */
    BookingDetailVO detail(Long id);

    /** 场地某日可用时段（上海日期，返回未释放占用） */
    AvailabilityVO availability(Long spaceId, LocalDate date);

    /**
     * 场地占用总览：按日期 + 可选节次窗口筛选全部场地，
     * 空闲/占用分档展示，占用附老师与课程信息（任意预约权限可读）。
     * startPeriodId/endPeriodId 同时为空表示全天窗口，只传其一按 40000 处理。
     */
    java.util.List<com.labmanage.module.booking.vo.LabOccupancyVO> occupancy(
            LocalDate date, Long startPeriodId, Long endPeriodId);

    /** 场地近期预约（重叠 [now, now+24h) 窗口的待审批/已批准单，时间轴「24h 近期」卡片） */
    java.util.List<com.labmanage.module.booking.vo.BookingVO> recent(Long spaceId);

    /** 预约状态统计（预约管理页卡片） */
    BookingStatsVO stats();

    /** 定时任务：开始时间已过仍待管理员核对的预约自动取消 */
    int autoCancelExpired();
}

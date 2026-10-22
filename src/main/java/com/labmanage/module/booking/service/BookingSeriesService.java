package com.labmanage.module.booking.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.booking.dto.BookingCancelDTO;
import com.labmanage.module.booking.dto.BookingRejectDTO;
import com.labmanage.module.booking.dto.SeriesCreateDTO;
import com.labmanage.module.booking.dto.SeriesPageQuery;
import com.labmanage.module.booking.vo.PeriodVO;
import com.labmanage.module.booking.vo.SeriesDetailVO;
import com.labmanage.module.booking.vo.SeriesVO;
import com.labmanage.module.booking.vo.TeacherOptionVO;

import java.util.List;

/**
 * 场地节次时间轴与批量周期预约业务。
 * <p>
 * 周期预约：老师一次提交「固定星期 + 节次 + 使用周期」，PENDING 待管理员核对；
 * 批准时逐周生成 lab_booking（APPROVED）与 lab_slot 占用（场地行锁 + 半开区间
 * 双重冲突校验，冲突单独提示）；驳回/撤销整单生效并释放已生成占用。
 * 学生为纯只读，仅节次/近期/详情查询可视化，不提供任何写能力。
 */
public interface BookingSeriesService {

    /** 已启用节次列表（时间轴节次小卡片数据源） */
    List<PeriodVO> listPeriods();

    /** 上课老师选项（预约表单「老师」下拉；仅启用老师） */
    List<TeacherOptionVO> listTeachers();

    /** 提交批量周期预约（PENDING + 幂等键防重 + 提交期逐段冲突预检） */
    SeriesDetailVO create(SeriesCreateDTO dto);

    /** 我的周期预约分页（申请人本人） */
    PageResult<SeriesVO> pageMine(SeriesPageQuery query);

    /** 全部周期预约分页（审批视角） */
    PageResult<SeriesVO> pageAll(SeriesPageQuery query);

    /** 周期预约详情（含生成的各周预约单，本人或审批权限） */
    SeriesDetailVO detail(Long id);

    /** 管理员核对批准：逐周生成预约单 + 占用槽（场地行锁 + 冲突复检） */
    void approve(Long id);

    /** 管理员整单驳回（原因必填，不生成占用） */
    void reject(Long id, BookingRejectDTO dto);

    /** 申请人本人撤销（APPROVED 时同步取消已生成预约单并释放占用） */
    void cancel(Long id, BookingCancelDTO dto);

    /** 定时任务：周期已结束仍未核对的 PENDING 规则自动取消 */
    int autoCancelExpired();
}
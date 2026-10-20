package com.labmanage.module.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.entity.LabBooking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 场地预约 Mapper
 */
@Mapper
public interface LabBookingMapper extends BaseMapper<LabBooking> {

    /**
     * 按状态分组统计未删除预约单数（预约管理页统计卡片）。
     * 显式过滤 deleted=0（原生 SQL 不走 @TableLogic 自动过滤）。
     */
    @Select("SELECT status AS status, COUNT(*) AS cnt FROM lab_booking "
            + "WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 统计场地未结预约数（待审批/已批准；场地删除前引用检查）。
     */
    @Select("SELECT COUNT(*) FROM lab_booking WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND status IN ('PENDING', 'APPROVED')")
    long countActiveByLabId(@Param("labId") Long labId);

    /**
     * 超时未核对的待审批预约：PENDING 且开始时间已过（定时任务自动取消）。
     * 限 200 条分批，避免单次扫描过大；按开始时间升序优先处理最早超时单。
     */
    @Select("SELECT * FROM lab_booking WHERE deleted = 0 AND status = 'PENDING' "
            + "AND start_at <= #{now} ORDER BY start_at ASC LIMIT 200")
    List<LabBooking> selectExpiredPending(@Param("now") Instant now);

    /**
     * 周期预约规则生成的各周预约单（取消/详情核对用），按开始时间升序。
     */
    @Select("SELECT * FROM lab_booking WHERE deleted = 0 AND series_id = #{seriesId} "
            + "ORDER BY start_at ASC")
    List<LabBooking> selectBySeriesId(@Param("seriesId") Long seriesId);

    /**
     * 周期预约规则已生成的预约单数（SeriesVO.bookedCount）。
     */
    @Select("SELECT COUNT(*) FROM lab_booking WHERE deleted = 0 AND series_id = #{seriesId}")
    long countBySeriesId(@Param("seriesId") Long seriesId);
}

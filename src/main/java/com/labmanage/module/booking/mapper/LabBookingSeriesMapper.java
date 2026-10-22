package com.labmanage.module.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.entity.LabBookingSeries;
import com.labmanage.module.booking.enums.SeriesStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 场地周期预约规则 Mapper
 */
@Mapper
public interface LabBookingSeriesMapper extends BaseMapper<LabBookingSeries> {

    /**
     * 待审批且首个周期日已过（今天无首周匹配）的周期规则（定时任务自动取消）。
     * 限 100 条，按创建时间升序。
     */
    @Select("SELECT * FROM lab_booking_series WHERE deleted = 0 AND status = 'PENDING' "
            + "AND cycle_end_date < #{today} ORDER BY create_time ASC LIMIT 100")
    List<LabBookingSeries> selectExpiredPending(@Param("today") LocalDate today);

    /**
     * PENDING 规则按申请人 + 幂等键查重（重复提交提示，而非静默覆盖）。
     */
    @Select("SELECT COUNT(*) FROM lab_booking_series WHERE deleted = 0 "
            + "AND applicant_id = #{applicantId} AND idempotency_key = #{key}")
    long countByApplicantAndKey(@Param("applicantId") Long applicantId, @Param("key") String key);
}
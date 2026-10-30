package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialBookingPlanLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialBookingPlanLineMapper extends BaseMapper<MaterialBookingPlanLine> {

    @Select("SELECT * FROM material_booking_plan_line WHERE plan_id = #{planId} ORDER BY id ASC")
    List<MaterialBookingPlanLine> selectAllByPlanIdIncludingDeleted(@Param("planId") Long planId);

    @Update("UPDATE material_booking_plan_line SET default_quantity = #{quantity}, deleted = 0, "
            + "update_time = #{updateTime}, update_user_id = #{operatorId} WHERE id = #{id}")
    int restoreOrUpdate(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                        @Param("updateTime") Instant updateTime, @Param("operatorId") Long operatorId);

    @Update("UPDATE material_booking_plan_line SET deleted = 1, update_time = #{updateTime}, "
            + "update_user_id = #{operatorId} WHERE id = #{id} AND deleted = 0")
    int logicalDelete(@Param("id") Long id, @Param("updateTime") Instant updateTime,
                      @Param("operatorId") Long operatorId);
}

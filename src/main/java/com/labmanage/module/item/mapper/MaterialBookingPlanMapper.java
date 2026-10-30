package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialBookingPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialBookingPlanMapper extends BaseMapper<MaterialBookingPlan> {

    @Select("SELECT * FROM material_booking_plan WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MaterialBookingPlan selectByIdForUpdate(@Param("id") Long id);
}

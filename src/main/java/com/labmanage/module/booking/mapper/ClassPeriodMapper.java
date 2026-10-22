package com.labmanage.module.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.entity.ClassPeriod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 节次 Mapper（时间轴节次卡片数据源）
 */
@Mapper
public interface ClassPeriodMapper extends BaseMapper<ClassPeriod> {

    /**
     * 已启用节次列表（按节次号升序）。
     */
    @Select("SELECT * FROM class_period WHERE deleted = 0 AND enabled = 1 ORDER BY period_no ASC")
    List<ClassPeriod> listEnabled();

    /**
     * 按 ID 批量取节次（起始/结束节次名回填）。命名避开 MyBatis-Plus 内置注入的
     * selectByIds，避免方法签名冲突。
     */
    @Select("""
            <script>
            SELECT * FROM class_period WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ClassPeriod> selectPeriodsByIds(@Param("ids") List<Long> ids);
}
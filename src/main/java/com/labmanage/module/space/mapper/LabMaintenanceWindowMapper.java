package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.LabMaintenanceWindow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 场地维修时段 Mapper
 */
@Mapper
public interface LabMaintenanceWindowMapper extends BaseMapper<LabMaintenanceWindow> {

    /**
     * 场地在 [startAt, endAt) 区间内生效中的维修时段计数（半开区间重叠）。
     * 预约创建/审批与维修设置三方冲突校验共用。
     */
    @Select("SELECT COUNT(*) FROM lab_maintenance_window WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND cancelled_at IS NULL AND start_at < #{endAt} AND end_at > #{startAt}")
    long countOverlap(@Param("labId") Long labId,
                      @Param("startAt") Instant startAt,
                      @Param("endAt") Instant endAt);

    /**
     * 场地生效中的维修时段列表（含进行中与未来，按开始时间升序；维修时段设置弹窗用）。
     */
    @Select("SELECT * FROM lab_maintenance_window WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND cancelled_at IS NULL AND end_at > #{now} ORDER BY start_at ASC")
    List<LabMaintenanceWindow> listActiveByLab(@Param("labId") Long labId, @Param("now") Instant now);

    /**
     * 多个场地在 [startAt, endAt) 区间内生效中的维修时段（预约时间轴总览/分页列表用）。
     */
    @Select("""
            <script>
            SELECT * FROM lab_maintenance_window WHERE deleted = 0 AND cancelled_at IS NULL
              AND lab_id IN
              <foreach collection="labIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              AND start_at &lt; #{endAt} AND end_at &gt; #{startAt}
            ORDER BY lab_id ASC, start_at ASC
            </script>
            """)
    List<LabMaintenanceWindow> listActiveInRangeForLabs(@Param("labIds") List<Long> labIds,
                                                        @Param("startAt") Instant startAt,
                                                        @Param("endAt") Instant endAt);
}

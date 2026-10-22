package com.labmanage.module.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.entity.LabSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 场地占用 Mapper
 */
@Mapper
public interface LabSlotMapper extends BaseMapper<LabSlot> {

    /**
     * 半开区间重叠冲突计数（数据库设计 8.1）：
     * existing.start_at < #{endAt} AND existing.end_at > #{startAt}，
     * 仅统计未释放（released_at IS NULL）且未删除的占用。
     * 提交与审批双重校验均调用本方法。
     */
    @Select("SELECT COUNT(*) FROM lab_slot WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND released_at IS NULL AND start_at < #{endAt} AND end_at > #{startAt}")
    long countConflict(@Param("labId") Long labId,
                       @Param("startAt") Instant startAt,
                       @Param("endAt") Instant endAt);

    /**
     * 查询场地在 [startAt, endAt) 区间内未释放的占用（可用时段展示），
     * 按开始时间升序。
     */
    @Select("SELECT * FROM lab_slot WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND released_at IS NULL AND start_at < #{endAt} AND end_at > #{startAt} "
            + "ORDER BY start_at ASC")
    List<LabSlot> listOccupiedInRange(@Param("labId") Long labId,
                                      @Param("startAt") Instant startAt,
                                      @Param("endAt") Instant endAt);

    /**
     * 查询全部场地在 [startAt, endAt) 区间内未释放的占用（占用总览），
     * 按场地与开始时间升序。
     */
    @Select("SELECT * FROM lab_slot WHERE deleted = 0 "
            + "AND released_at IS NULL AND start_at < #{endAt} AND end_at > #{startAt} "
            + "ORDER BY lab_id ASC, start_at ASC")
    List<LabSlot> listOccupiedInRangeAll(@Param("startAt") Instant startAt,
                                         @Param("endAt") Instant endAt);

    /**
     * 统计场地未释放占用数（场地删除前引用检查）。
     */
    @Select("SELECT COUNT(*) FROM lab_slot WHERE lab_id = #{labId} AND deleted = 0 "
            + "AND released_at IS NULL")
    long countUnreleasedByLabId(@Param("labId") Long labId);
}

package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockLocationAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface StockLocationAdminMapper extends BaseMapper<StockLocationAdmin> {

    @Select("SELECT * FROM stock_location_admin WHERE location_id = #{locationId}")
    List<StockLocationAdmin> selectAllByLocationIdIncludingDeleted(@Param("locationId") Long locationId);

    @Update("UPDATE stock_location_admin SET deleted = 0, update_time = #{updateTime}, "
            + "update_user_id = #{operatorId} WHERE id = #{id}")
    int restore(@Param("id") Long id, @Param("updateTime") Instant updateTime,
                @Param("operatorId") Long operatorId);

    @Update("UPDATE stock_location_admin SET deleted = 1, update_time = #{updateTime}, "
            + "update_user_id = #{operatorId} WHERE id = #{id} AND deleted = 0")
    int logicalDelete(@Param("id") Long id, @Param("updateTime") Instant updateTime,
                      @Param("operatorId") Long operatorId);
}

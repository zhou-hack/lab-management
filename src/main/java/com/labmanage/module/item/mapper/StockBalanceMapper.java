package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface StockBalanceMapper extends BaseMapper<StockBalance> {

    @Select("""
            <script>
            SELECT * FROM stock_balance
            WHERE material_id = #{materialId} AND deleted = 0
            ORDER BY location_id ASC, id ASC
            <if test="forUpdate">FOR UPDATE</if>
            </script>
            """)
    List<StockBalance> selectByMaterialId(@Param("materialId") Long materialId,
                                          @Param("forUpdate") boolean forUpdate);

    @Select("""
            SELECT * FROM stock_balance
            WHERE material_id = #{materialId} AND location_id = #{locationId}
              AND (position_text = #{positionText} OR (position_text IS NULL AND #{positionText} IS NULL))
              AND deleted = 0
            FOR UPDATE
            """)
    StockBalance selectForUpdate(@Param("materialId") Long materialId,
                                 @Param("locationId") Long locationId,
                                 @Param("positionText") String positionText);

    @Update("""
            UPDATE stock_balance
            SET owned_quantity = owned_quantity + #{ownedDelta},
                available_quantity = available_quantity + #{availableDelta},
                version = version + 1,
                update_time = #{updateTime},
                update_user_id = #{operatorId}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
              AND owned_quantity + #{ownedDelta} >= 0
              AND available_quantity + #{availableDelta} >= 0
              AND owned_quantity + #{ownedDelta} >= available_quantity + #{availableDelta}
            """)
    int applyDelta(@Param("id") Long id,
                   @Param("expectedVersion") Integer expectedVersion,
                   @Param("ownedDelta") BigDecimal ownedDelta,
                   @Param("availableDelta") BigDecimal availableDelta,
                   @Param("updateTime") Instant updateTime,
                   @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE stock_balance
            SET position_text = #{positionText},
                version = version + 1,
                update_time = #{updateTime},
                update_user_id = #{operatorId}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
            """)
    int updatePosition(@Param("id") Long id,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("positionText") String positionText,
                       @Param("updateTime") Instant updateTime,
                       @Param("operatorId") Long operatorId);
}

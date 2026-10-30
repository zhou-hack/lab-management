package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialReservationMapper extends BaseMapper<MaterialReservation> {

    @Select("""
            <script>
            SELECT * FROM material_reservation
            WHERE material_id = #{materialId} AND deleted = 0 AND status = 'ACTIVE'
              AND reserved_quantity &gt; consumed_quantity + released_quantity
              AND reservation_start_at &lt; #{endAt} AND reservation_end_at &gt; #{startAt}
            <if test="excludeClaimId != null">
              AND claim_id &lt;&gt; #{excludeClaimId}
            </if>
            ORDER BY location_id ASC, id ASC
            </script>
            """)
    List<MaterialReservation> selectEffectiveOverlapping(@Param("materialId") Long materialId,
                                                         @Param("startAt") Instant startAt,
                                                         @Param("endAt") Instant endAt,
                                                         @Param("excludeClaimId") Long excludeClaimId);

    @Select("""
            SELECT * FROM material_reservation
            WHERE claim_id = #{claimId} AND status = 'ACTIVE' AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MaterialReservation> selectActiveByClaimIdForUpdate(@Param("claimId") Long claimId);

    @Update("""
            UPDATE material_reservation
            SET released_quantity = reserved_quantity - consumed_quantity,
                status = 'CLOSED', closed_at = #{closedAt},
                update_time = #{closedAt}, update_user_id = #{operatorId}
            WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0
              AND reserved_quantity > consumed_quantity + released_quantity
              AND consumed_quantity + released_quantity <= reserved_quantity
            """)
    int releaseRemaining(@Param("id") Long id,
                         @Param("closedAt") Instant closedAt,
                         @Param("operatorId") Long operatorId);

    @Select("""
            SELECT * FROM material_reservation
            WHERE fixed_asset_id = #{fixedAssetId} AND deleted = 0 AND status = 'ACTIVE'
              AND reserved_quantity > consumed_quantity + released_quantity
              AND reservation_start_at < #{endAt} AND reservation_end_at > #{startAt}
              AND claim_id <> #{excludeClaimId}
            ORDER BY id ASC
            """)
    java.util.List<MaterialReservation> selectEffectiveOverlappingByAsset(
            @Param("fixedAssetId") Long fixedAssetId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeClaimId") Long excludeClaimId);

    @Update("""
            UPDATE material_reservation
            SET status = CASE
                    WHEN reserved_quantity = consumed_quantity + released_quantity + #{quantity}
                    THEN 'CLOSED' ELSE 'ACTIVE' END,
                closed_at = CASE
                    WHEN reserved_quantity = consumed_quantity + released_quantity + #{quantity}
                    THEN #{changedAt} ELSE closed_at END,
                consumed_quantity = consumed_quantity + #{quantity},
                update_time = #{changedAt}, update_user_id = #{operatorId}
            WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0
              AND reserved_quantity - consumed_quantity - released_quantity >= #{quantity}
              AND #{quantity} > 0
            """)
    int consume(@Param("id") Long id,
                @Param("quantity") java.math.BigDecimal quantity,
                @Param("changedAt") Instant changedAt,
                @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE material_reservation
            SET status = CASE
                    WHEN reserved_quantity = consumed_quantity + released_quantity + #{quantity}
                    THEN 'CLOSED' ELSE 'ACTIVE' END,
                closed_at = CASE
                    WHEN reserved_quantity = consumed_quantity + released_quantity + #{quantity}
                    THEN #{changedAt} ELSE closed_at END,
                released_quantity = released_quantity + #{quantity},
                update_time = #{changedAt}, update_user_id = #{operatorId}
            WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0
              AND reserved_quantity - consumed_quantity - released_quantity >= #{quantity}
              AND #{quantity} > 0
            """)
    int releaseQuantity(@Param("id") Long id,
                        @Param("quantity") java.math.BigDecimal quantity,
                        @Param("changedAt") Instant changedAt,
                        @Param("operatorId") Long operatorId);
}

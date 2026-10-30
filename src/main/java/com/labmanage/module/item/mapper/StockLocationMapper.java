package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.vo.StockLocationChangeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface StockLocationMapper extends BaseMapper<StockLocation> {

    @Select("SELECT id FROM stock_location WHERE location_code = #{locationCode} AND deleted = 0 LIMIT 1")
    Long selectIdByCode(@Param("locationCode") String locationCode);

    @Select("SELECT * FROM stock_location WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    StockLocation selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT CASE WHEN
                EXISTS (SELECT 1 FROM stock_balance
                    WHERE location_id = #{id} AND owned_quantity > 0)
                OR EXISTS (SELECT 1 FROM fixed_asset
                    WHERE location_id = #{id} AND deleted = 0 AND status NOT IN ('LOST', 'SCRAPPED'))
                OR EXISTS (SELECT 1 FROM material_document
                    WHERE document_type = 'TRANSFER' AND deleted = 0 AND posted_at IS NOT NULL
                      AND transfer_status <> 'CLOSED'
                      AND (from_location_id = #{id} OR to_location_id = #{id}))
            THEN 1 ELSE 0 END
            """)
    int hasStockOrOpenTransfer(@Param("id") Long id);

    @Select("""
            <script>
            SELECT h.id AS id,
                   h.location_id AS locationId,
                   h.location_code AS locationCode,
                   h.location_name AS locationName,
                   h.change_type AS changeType,
                   h.operator_id AS operatorId,
                   u.real_name AS operatorName,
                   h.occurred_at AS occurredAt
            FROM (
                SELECT CONCAT('CREATE_', id) AS id, id AS location_id,
                       location_code, location_name, 'CREATE' AS change_type,
                       create_user_id AS operator_id, create_time AS occurred_at
                FROM stock_location
                UNION ALL
                SELECT CONCAT('DELETE_', id) AS id, id AS location_id,
                       location_code, location_name, 'DELETE' AS change_type,
                       update_user_id AS operator_id, update_time AS occurred_at
                FROM stock_location
                WHERE deleted = 1
            ) h
            LEFT JOIN sys_user u ON u.id = h.operator_id
            <if test="locationId != null">
                WHERE h.location_id = #{locationId}
            </if>
            ORDER BY h.occurred_at DESC, h.id DESC
            </script>
            """)
    Page<StockLocationChangeVO> selectChangeHistory(Page<StockLocationChangeVO> page,
                                                    @Param("locationId") Long locationId);

    @Update("UPDATE stock_location SET deleted = 1, enabled = 0, update_time = #{updateTime}, "
            + "update_user_id = #{operatorId} WHERE id = #{id} AND deleted = 0")
    int logicalDelete(@Param("id") Long id, @Param("updateTime") Instant updateTime,
                      @Param("operatorId") Long operatorId);
}

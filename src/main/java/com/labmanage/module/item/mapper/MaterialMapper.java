package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.enums.MaterialType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    @Select("SELECT id FROM material WHERE sku_code = #{skuCode} LIMIT 1")
    Long selectIdBySkuIncludingDeleted(@Param("skuCode") String skuCode);

    @Select("SELECT * FROM material WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Material selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT * FROM material WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<Material> selectBatchIdsIncludingDeleted(@Param("ids") Collection<Long> ids);

    @Select("""
            <script>
            SELECT id FROM material WHERE 1 = 1
            <if test="materialName != null and materialName != ''">
                AND material_name LIKE CONCAT('%', #{materialName}, '%')
            </if>
            <if test="materialType != null">
                AND material_type = #{materialType}
            </if>
            ORDER BY id
            </script>
            """)
    List<Long> selectIdsByNameAndTypeIncludingDeleted(@Param("materialName") String materialName,
                                                       @Param("materialType") MaterialType materialType);

    @Select("""
            SELECT CASE WHEN EXISTS (
                SELECT 1
                FROM material_document_line source_line
                JOIN material_document source_doc ON source_doc.id = source_line.material_document_id
                    AND source_doc.deleted = 0
                WHERE source_line.deleted = 0 AND source_line.material_id = #{materialId}
                  AND source_doc.document_type = 'TRANSFER' AND source_doc.business_scene = 'TRANSFER_SEND'
                  AND source_doc.posted_at IS NOT NULL
                  AND source_doc.transfer_status IN ('PENDING_RECEIPT', 'PARTIALLY_RECEIVED', 'PENDING_RETURN')
                  AND source_line.quantity
                      - COALESCE((
                          SELECT SUM(receipt_line.quantity)
                          FROM material_document_line receipt_line
                          JOIN material_document receipt_doc ON receipt_doc.id = receipt_line.material_document_id
                          WHERE receipt_line.source_material_document_line_id = source_line.id
                            AND receipt_doc.source_material_document_id = source_doc.id
                            AND receipt_doc.document_type = 'TRANSFER_RECEIPT'
                            AND receipt_doc.business_scene = 'TRANSFER_RECEIVE'
                            AND receipt_doc.posted_at IS NOT NULL AND receipt_doc.deleted = 0
                            AND receipt_line.deleted = 0
                      ), 0)
                      - COALESCE((
                          SELECT SUM(return_line.quantity)
                          FROM material_document_line return_line
                          JOIN material_document return_doc ON return_doc.id = return_line.material_document_id
                          WHERE return_line.source_material_document_line_id = source_line.id
                            AND return_doc.source_material_document_id = source_doc.id
                            AND return_doc.document_type = 'TRANSFER_RETURN'
                            AND return_doc.business_scene = 'TRANSFER_RETURN'
                            AND return_doc.posted_at IS NOT NULL AND return_doc.deleted = 0
                            AND return_line.deleted = 0
                      ), 0)
                      - COALESCE((
                          SELECT SUM(settlement.quantity)
                          FROM material_settlement settlement
                          JOIN material_document settlement_doc ON settlement_doc.id = settlement.material_document_id
                          WHERE settlement.source_material_document_line_id = source_line.id
                            AND settlement_doc.source_material_document_id = source_doc.id
                            AND settlement_doc.document_type = 'TRANSFER_SETTLEMENT'
                            AND settlement_doc.business_scene = 'TRANSFER_SETTLE'
                            AND settlement_doc.review_status = 'APPROVED'
                            AND settlement_doc.posted_at IS NOT NULL AND settlement_doc.deleted = 0
                            AND settlement.deleted = 0
                      ), 0) > 0
            ) THEN 1 ELSE 0 END
            """)
    int hasInTransitStock(@Param("materialId") Long materialId);

    @Update("UPDATE material SET deleted = 1, enabled = 0, update_time = #{updateTime}, "
            + "update_user_id = #{operatorId} WHERE id = #{id} AND deleted = 0")
    int logicalDelete(@Param("id") Long id, @Param("updateTime") Instant updateTime,
                      @Param("operatorId") Long operatorId);

    @Select("""
            <script>
            SELECT material_id AS materialId,
                   CAST(SUM(owned_quantity) AS DECIMAL(18,3)) AS ownedQuantity
            FROM (
                SELECT sb.material_id AS material_id, sb.owned_quantity AS owned_quantity
                FROM stock_balance sb
                WHERE sb.deleted = 0
                <if test="materialIds != null and !materialIds.isEmpty()">
                  AND sb.material_id IN
                  <foreach collection="materialIds" item="materialId" open="(" separator="," close=")">
                    #{materialId}
                  </foreach>
                </if>
                UNION ALL
                SELECT fa.material_id AS material_id, CAST(1 AS DECIMAL(18,3)) AS owned_quantity
                FROM fixed_asset fa
                WHERE fa.deleted = 0 AND fa.status NOT IN ('LOST', 'SCRAPPED')
                <if test="materialIds != null and !materialIds.isEmpty()">
                  AND fa.material_id IN
                  <foreach collection="materialIds" item="materialId" open="(" separator="," close=")">
                    #{materialId}
                  </foreach>
                </if>
            ) owned_quantities
            GROUP BY material_id
            </script>
            """)
    List<OwnedQuantityVO> selectOwnedQuantities(@Param("materialIds") List<Long> materialIds);

    record OwnedQuantityVO(Long materialId, BigDecimal ownedQuantity) {
    }
}

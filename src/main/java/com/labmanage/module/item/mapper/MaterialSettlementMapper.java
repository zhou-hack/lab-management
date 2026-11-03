package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.item.dto.StockSettlementPageQuery;
import com.labmanage.module.item.entity.MaterialSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialSettlementMapper extends BaseMapper<MaterialSettlement> {

    @Select("""
            SELECT * FROM material_settlement
            WHERE material_document_id = #{documentId} AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MaterialSettlement> selectByDocumentIdForUpdate(@Param("documentId") Long documentId);

    @Select("""
            <script>
            SELECT s.* FROM material_settlement s
            JOIN material_document d ON d.id = s.material_document_id AND d.deleted = 0
            WHERE s.deleted = 0
            <if test="query.documentNo != null and query.documentNo != ''">
              AND d.document_no LIKE CONCAT('%', #{query.documentNo}, '%')
            </if>
            <if test="query.materialId != null">
              AND s.material_id = #{query.materialId}
            </if>
            <if test="query.settlementType != null and query.settlementType != ''">
              AND s.settlement_type = #{query.settlementType}
            </if>
            <if test="query.locationId != null">
              AND d.location_id = #{query.locationId}
            </if>
            <if test="query.reviewStatus != null and query.reviewStatus != ''">
              AND d.review_status = #{query.reviewStatus}
            </if>
            <if test="startAt != null">
              AND d.occurred_at &gt;= #{startAt}
            </if>
            <if test="endAt != null">
              AND d.occurred_at &lt; #{endAt}
            </if>
            ORDER BY ${sortColumn} ${sortDirection}, s.id DESC
            </script>
            """)
    Page<MaterialSettlement> selectSettlementPage(Page<MaterialSettlement> page,
                                                   @Param("query") StockSettlementPageQuery query,
                                                   @Param("startAt") Instant startAt,
                                                   @Param("endAt") Instant endAt,
                                                   @Param("sortColumn") String sortColumn,
                                                   @Param("sortDirection") String sortDirection);
}

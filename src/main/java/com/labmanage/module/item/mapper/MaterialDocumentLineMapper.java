package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialDocumentLineMapper extends BaseMapper<MaterialDocumentLine> {

    @Select("""
            SELECT issue_line.material_id AS materialId,
                   issue_doc.location_id AS locationId,
                   issue_line.fixed_asset_id AS fixedAssetId,
                   claim.return_deadline AS returnDeadline,
                   issue_line.quantity
                       - COALESCE(returned.returned_quantity, 0)
                       - COALESCE(settled.settled_quantity, 0) AS quantity
            FROM material_document_line issue_line
            JOIN material_document issue_doc ON issue_doc.id = issue_line.material_document_id
                AND issue_doc.deleted = 0
            JOIN material_claim claim ON claim.id = issue_doc.business_chain_id AND claim.deleted = 0
            LEFT JOIN (
                SELECT return_line.source_material_document_line_id AS source_line_id,
                       SUM(return_line.quantity) AS returned_quantity
                FROM material_document_line return_line
                JOIN material_document return_doc ON return_doc.id = return_line.material_document_id
                WHERE return_doc.document_type = 'INBOUND' AND return_doc.business_scene = 'CLAIM_RETURN'
                  AND return_doc.posted_at IS NOT NULL AND return_doc.deleted = 0
                  AND return_line.deleted = 0
                  AND return_line.source_material_document_line_id IS NOT NULL
                GROUP BY return_line.source_material_document_line_id
            ) returned ON returned.source_line_id = issue_line.id
            LEFT JOIN (
                SELECT settlement.source_material_document_line_id AS source_line_id,
                       SUM(settlement.quantity) AS settled_quantity
                FROM material_settlement settlement
                JOIN material_document settlement_doc ON settlement_doc.id = settlement.material_document_id
                WHERE settlement_doc.document_type = 'INBOUND' AND settlement_doc.business_scene = 'CLAIM_RETURN'
                  AND settlement_doc.posted_at IS NOT NULL AND settlement_doc.deleted = 0
                  AND settlement.deleted = 0
                  AND settlement.source_material_document_line_id IS NOT NULL
                GROUP BY settlement.source_material_document_line_id
            ) settled ON settled.source_line_id = issue_line.id
            WHERE issue_doc.document_type = 'OUTBOUND' AND issue_doc.business_scene = 'CLAIM_ISSUE'
              AND issue_doc.posted_at IS NOT NULL AND issue_line.deleted = 0
              AND issue_line.material_id = #{materialId}
              AND issue_line.quantity
                  - COALESCE(returned.returned_quantity, 0)
                  - COALESCE(settled.settled_quantity, 0) > 0
            ORDER BY issue_line.id ASC
            """)
    List<OutstandingLoan> selectOutstandingByMaterial(@Param("materialId") Long materialId);

    @Select("""
            <script>
            SELECT DISTINCT doc.business_chain_id
            FROM material_document doc
            JOIN material_document_line line ON line.material_document_id = doc.id AND line.deleted = 0
            WHERE doc.business_chain_id IN
            <foreach collection="claimIds" item="claimId" open="(" separator="," close=")">
                #{claimId}
            </foreach>
              AND doc.document_type = 'OUTBOUND' AND doc.deleted = 0
            </script>
            """)
    List<Long> selectOutboundClaimIds(@Param("claimIds") List<Long> claimIds);

    @Select("""
            SELECT * FROM material_document_line
            WHERE material_document_id = #{documentId} AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MaterialDocumentLine> selectByDocumentIdForUpdate(@Param("documentId") Long documentId);

    @Select("""
            SELECT issue_line.id AS sourceLineId,
                   COALESCE(returned.returned_quantity, 0) AS returnedQuantity,
                   COALESCE(settled.settled_quantity, 0) AS settledQuantity,
                   issue_line.quantity
                       - COALESCE(returned.returned_quantity, 0)
                       - COALESCE(settled.settled_quantity, 0) AS remainingQuantity
            FROM material_document_line issue_line
            LEFT JOIN (
                SELECT return_line.source_material_document_line_id AS source_line_id,
                       SUM(return_line.quantity) AS returned_quantity
                FROM material_document_line return_line
                JOIN material_document return_doc ON return_doc.id = return_line.material_document_id
                WHERE return_doc.source_material_document_id = #{sourceDocumentId}
                  AND return_doc.document_type = 'INBOUND' AND return_doc.business_scene = 'CLAIM_RETURN'
                  AND return_doc.posted_at IS NOT NULL AND return_doc.deleted = 0
                  AND return_line.deleted = 0
                  AND return_line.source_material_document_line_id IS NOT NULL
                GROUP BY return_line.source_material_document_line_id
            ) returned ON returned.source_line_id = issue_line.id
            LEFT JOIN (
                SELECT settlement.source_material_document_line_id AS source_line_id,
                       SUM(settlement.quantity) AS settled_quantity
                FROM material_settlement settlement
                JOIN material_document settlement_doc ON settlement_doc.id = settlement.material_document_id
                WHERE settlement_doc.source_material_document_id = #{sourceDocumentId}
                  AND settlement_doc.document_type = 'INBOUND' AND settlement_doc.business_scene = 'CLAIM_RETURN'
                  AND settlement_doc.posted_at IS NOT NULL AND settlement_doc.deleted = 0
                  AND settlement.deleted = 0
                  AND settlement.source_material_document_line_id IS NOT NULL
                GROUP BY settlement.source_material_document_line_id
            ) settled ON settled.source_line_id = issue_line.id
            WHERE issue_line.material_document_id = #{sourceDocumentId} AND issue_line.deleted = 0
            ORDER BY issue_line.id ASC
            """)
    List<SourceLineProcessing> selectProcessingBySourceDocument(@Param("sourceDocumentId") Long sourceDocumentId);

    @Select("""
            <script>
            SELECT remaining.source_document_id AS sourceDocumentId,
                   remaining.material_id AS materialId,
                   SUM(remaining.remaining_quantity) AS outstandingQuantity
            FROM (
                SELECT issue_line.material_document_id AS source_document_id,
                       issue_line.material_id,
                       issue_line.quantity
                           - COALESCE((
                               SELECT SUM(return_line.quantity)
                               FROM material_document_line return_line
                               JOIN material_document return_doc ON return_doc.id = return_line.material_document_id
                               WHERE return_line.source_material_document_line_id = issue_line.id
                                 AND return_doc.source_material_document_id = issue_line.material_document_id
                                 AND return_doc.document_type = 'INBOUND'
                                 AND return_doc.business_scene = 'CLAIM_RETURN'
                                 AND return_doc.posted_at IS NOT NULL AND return_doc.deleted = 0
                                 AND return_line.deleted = 0
                           ), 0)
                           - COALESCE((
                               SELECT SUM(settlement.quantity)
                               FROM material_settlement settlement
                               JOIN material_document settlement_doc ON settlement_doc.id = settlement.material_document_id
                               WHERE settlement.source_material_document_line_id = issue_line.id
                                 AND settlement_doc.source_material_document_id = issue_line.material_document_id
                                 AND settlement_doc.document_type = 'INBOUND'
                                 AND settlement_doc.business_scene = 'CLAIM_RETURN'
                                 AND settlement_doc.posted_at IS NOT NULL AND settlement_doc.deleted = 0
                                 AND settlement.deleted = 0
                           ), 0) AS remaining_quantity
                FROM material_document_line issue_line
                WHERE issue_line.deleted = 0
                  AND issue_line.material_document_id IN
                  <foreach collection="sourceDocumentIds" item="sourceDocumentId" open="(" separator="," close=")">
                      #{sourceDocumentId}
                  </foreach>
            ) remaining
            WHERE remaining.remaining_quantity > 0
            GROUP BY remaining.source_document_id, remaining.material_id
            ORDER BY remaining.source_document_id ASC, remaining.material_id ASC
            </script>
            """)
    List<OutstandingMaterial> selectOutstandingMaterials(
            @Param("sourceDocumentIds") List<Long> sourceDocumentIds);

    @Select("""
            SELECT source_line.id AS sourceLineId,
                   COALESCE(received.received_quantity, 0) AS receivedQuantity,
                   COALESCE(returned.returned_quantity, 0) AS returnedQuantity,
                   COALESCE(settled.settled_quantity, 0) AS settledQuantity,
                   source_line.quantity
                       - COALESCE(received.received_quantity, 0)
                       - COALESCE(returned.returned_quantity, 0)
                       - COALESCE(settled.settled_quantity, 0) AS remainingQuantity
            FROM material_document_line source_line
            LEFT JOIN (
                SELECT child_line.source_material_document_line_id AS source_line_id,
                       SUM(child_line.quantity) AS received_quantity
                FROM material_document_line child_line
                JOIN material_document child_doc ON child_doc.id = child_line.material_document_id
                WHERE child_doc.source_material_document_id = #{sourceDocumentId}
                  AND child_doc.document_type = 'TRANSFER_RECEIPT'
                  AND child_doc.business_scene = 'TRANSFER_RECEIVE'
                  AND child_doc.posted_at IS NOT NULL AND child_doc.deleted = 0
                  AND child_line.deleted = 0
                  AND child_line.source_material_document_line_id IS NOT NULL
                GROUP BY child_line.source_material_document_line_id
            ) received ON received.source_line_id = source_line.id
            LEFT JOIN (
                SELECT child_line.source_material_document_line_id AS source_line_id,
                       SUM(child_line.quantity) AS returned_quantity
                FROM material_document_line child_line
                JOIN material_document child_doc ON child_doc.id = child_line.material_document_id
                WHERE child_doc.source_material_document_id = #{sourceDocumentId}
                  AND child_doc.document_type = 'TRANSFER_RETURN'
                  AND child_doc.business_scene = 'TRANSFER_RETURN'
                  AND child_doc.posted_at IS NOT NULL AND child_doc.deleted = 0
                  AND child_line.deleted = 0
                  AND child_line.source_material_document_line_id IS NOT NULL
                GROUP BY child_line.source_material_document_line_id
            ) returned ON returned.source_line_id = source_line.id
            LEFT JOIN (
                SELECT settlement.source_material_document_line_id AS source_line_id,
                       SUM(settlement.quantity) AS settled_quantity
                FROM material_settlement settlement
                JOIN material_document settlement_doc ON settlement_doc.id = settlement.material_document_id
                WHERE settlement_doc.source_material_document_id = #{sourceDocumentId}
                  AND settlement_doc.document_type = 'TRANSFER_SETTLEMENT'
                  AND settlement_doc.business_scene = 'TRANSFER_SETTLE'
                  AND settlement_doc.review_status = 'APPROVED'
                  AND settlement_doc.posted_at IS NOT NULL AND settlement_doc.deleted = 0
                  AND settlement.deleted = 0
                  AND settlement.source_material_document_line_id IS NOT NULL
                GROUP BY settlement.source_material_document_line_id
            ) settled ON settled.source_line_id = source_line.id
            WHERE source_line.material_document_id = #{sourceDocumentId} AND source_line.deleted = 0
            ORDER BY source_line.id ASC
            """)
    List<TransferLineProcessing> selectTransferProcessingBySourceDocument(
            @Param("sourceDocumentId") Long sourceDocumentId);

    record TransferLineProcessing(
            Long sourceLineId,
            BigDecimal receivedQuantity,
            BigDecimal returnedQuantity,
            BigDecimal settledQuantity,
            BigDecimal remainingQuantity
    ) {
    }

    record OutstandingMaterial(
            Long sourceDocumentId,
            Long materialId,
            BigDecimal outstandingQuantity
    ) {
    }

    record SourceLineProcessing(
            Long sourceLineId,
            BigDecimal returnedQuantity,
            BigDecimal settledQuantity,
            BigDecimal remainingQuantity
    ) {
    }

    record OutstandingLoan(
            Long materialId,
            Long locationId,
            Long fixedAssetId,
            Instant returnDeadline,
            BigDecimal quantity
    ) {
    }
}

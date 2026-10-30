package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.dto.OverduePageQuery;
import com.labmanage.module.item.entity.MaterialDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialDocumentMapper extends BaseMapper<MaterialDocument> {

    String UNRETURNED_FROM = """
            FROM material_document issue_doc
            JOIN material_claim claim ON claim.id = issue_doc.business_chain_id AND claim.deleted = 0
            JOIN (
                SELECT remaining.source_document_id,
                       SUM(CASE WHEN remaining.remaining_quantity > 0
                                THEN remaining.remaining_quantity ELSE 0 END) AS outstanding_quantity
                FROM (
                    SELECT issue_line.material_document_id AS source_document_id,
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
                ) remaining
                GROUP BY remaining.source_document_id
                HAVING SUM(CASE WHEN remaining.remaining_quantity > 0
                                THEN remaining.remaining_quantity ELSE 0 END) > 0
            ) outstanding ON outstanding.source_document_id = issue_doc.id
            """;

    String UNRETURNED_FILTERS = """
            WHERE issue_doc.deleted = 0 AND issue_doc.document_type = 'OUTBOUND'
              AND issue_doc.business_scene = 'CLAIM_ISSUE' AND issue_doc.posted_at IS NOT NULL
              AND claim.return_deadline &lt; #{now}
            <if test="applicantId != null">
              AND claim.applicant_id = #{applicantId}
            </if>
            <if test="query.claimId != null">
              AND claim.id = #{query.claimId}
            </if>
            <if test="query.sourceDocumentId != null">
              AND issue_doc.id = #{query.sourceDocumentId}
            </if>
            <if test="query.locationId != null">
              AND issue_doc.location_id = #{query.locationId}
            </if>
            <if test="startAt != null">
              AND claim.return_deadline &gt;= #{startAt}
            </if>
            <if test="endAt != null">
              AND claim.return_deadline &lt; #{endAt}
            </if>
            """;

    String RETURNED_FROM = """
            FROM material_document return_doc
            JOIN material_document issue_doc ON issue_doc.id = return_doc.source_material_document_id
                AND issue_doc.deleted = 0 AND issue_doc.document_type = 'OUTBOUND'
                AND issue_doc.business_scene = 'CLAIM_ISSUE' AND issue_doc.posted_at IS NOT NULL
            JOIN material_claim claim ON claim.id = return_doc.business_chain_id
                AND claim.id = issue_doc.business_chain_id AND claim.deleted = 0
            """;

    String RETURNED_FILTERS = """
            WHERE return_doc.deleted = 0 AND return_doc.document_type = 'INBOUND'
              AND return_doc.business_scene = 'CLAIM_RETURN' AND return_doc.return_status = 'OVERDUE'
              AND return_doc.posted_at IS NOT NULL
            <if test="applicantId != null">
              AND claim.applicant_id = #{applicantId}
            </if>
            <if test="query.claimId != null">
              AND claim.id = #{query.claimId}
            </if>
            <if test="query.sourceDocumentId != null">
              AND issue_doc.id = #{query.sourceDocumentId}
            </if>
            <if test="query.locationId != null">
              AND issue_doc.location_id = #{query.locationId}
            </if>
            <if test="startAt != null">
              AND return_doc.posted_at &gt;= #{startAt}
            </if>
            <if test="endAt != null">
              AND return_doc.posted_at &lt; #{endAt}
            </if>
            """;

    @Select("SELECT * FROM material_document WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MaterialDocument selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM material_document
            WHERE operator_id = #{operatorId} AND document_type = #{documentType}
              AND idempotency_key = #{idempotencyKey} AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    MaterialDocument selectByOperatorTypeIdempotencyForUpdate(@Param("operatorId") Long operatorId,
                                                               @Param("documentType") String documentType,
                                                               @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT COALESCE(MAX(document_seq), 0) + 1
            FROM material_document
            WHERE business_chain_id = #{businessChainId} AND document_type = #{documentType} AND deleted = 0
            """)
    Integer selectNextDocumentSeq(@Param("businessChainId") Long businessChainId,
                                  @Param("documentType") String documentType);

    @Update("""
            UPDATE material_document
            SET process_status = #{processStatus}, version = version + 1,
                update_time = #{updateTime}, update_user_id = #{operatorId}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
            """)
    int updateProcessStatus(@Param("id") Long id,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("processStatus") String processStatus,
                            @Param("updateTime") Instant updateTime,
                            @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE material_document
            SET review_status = #{reviewStatus}, reviewed_by = #{reviewerId},
                reviewed_at = #{reviewedAt}, review_reason = #{reviewReason},
                posted_at = #{postedAt}, version = version + 1,
                update_time = #{reviewedAt}, update_user_id = #{reviewerId}
            WHERE id = #{id} AND review_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion} AND deleted = 0
            """)
    int reviewSettlement(@Param("id") Long id,
                         @Param("expectedVersion") Integer expectedVersion,
                         @Param("reviewStatus") String reviewStatus,
                         @Param("reviewerId") Long reviewerId,
                         @Param("reviewedAt") Instant reviewedAt,
                         @Param("reviewReason") String reviewReason,
                         @Param("postedAt") Instant postedAt);

    @Select("<script>SELECT issue_doc.* " + UNRETURNED_FROM + UNRETURNED_FILTERS + """
            ORDER BY ${orderBy}
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<MaterialDocument> selectUnreturnedOverdues(@Param("query") OverduePageQuery query,
                                                     @Param("now") Instant now,
                                                     @Param("applicantId") Long applicantId,
                                                     @Param("startAt") Instant startAt,
                                                     @Param("endAt") Instant endAt,
                                                     @Param("orderBy") String orderBy,
                                                     @Param("offset") long offset,
                                                     @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) " + UNRETURNED_FROM + UNRETURNED_FILTERS + "</script>")
    long countUnreturnedOverdues(@Param("query") OverduePageQuery query,
                                 @Param("now") Instant now,
                                 @Param("applicantId") Long applicantId,
                                 @Param("startAt") Instant startAt,
                                 @Param("endAt") Instant endAt);

    @Select("<script>SELECT return_doc.* " + RETURNED_FROM + RETURNED_FILTERS + """
            ORDER BY ${orderBy}
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<MaterialDocument> selectReturnedOverdues(@Param("query") OverduePageQuery query,
                                                   @Param("applicantId") Long applicantId,
                                                   @Param("startAt") Instant startAt,
                                                   @Param("endAt") Instant endAt,
                                                   @Param("orderBy") String orderBy,
                                                   @Param("offset") long offset,
                                                   @Param("pageSize") int pageSize);

    @Select("<script>SELECT COUNT(*) " + RETURNED_FROM + RETURNED_FILTERS + "</script>")
    long countReturnedOverdues(@Param("query") OverduePageQuery query,
                               @Param("applicantId") Long applicantId,
                               @Param("startAt") Instant startAt,
                               @Param("endAt") Instant endAt);

    @Select("""
            SELECT doc.* FROM material_document doc
            JOIN material_claim claim ON claim.id = doc.business_chain_id AND claim.deleted = 0
            WHERE doc.deleted = 0 AND doc.document_type = 'OUTBOUND'
              AND doc.business_scene = 'CLAIM_ISSUE' AND doc.posted_at IS NOT NULL
              AND doc.process_status IN ('UNPROCESSED', 'PARTIAL')
              AND claim.return_deadline < #{now}
            ORDER BY claim.return_deadline ASC, doc.id ASC
            """)
    List<MaterialDocument> selectOverdueNotificationCandidates(@Param("now") Instant now);

    @Select("<script>SELECT DISTINCT claim.id " + UNRETURNED_FROM + """
            WHERE issue_doc.deleted = 0 AND issue_doc.document_type = 'OUTBOUND'
              AND issue_doc.business_scene = 'CLAIM_ISSUE' AND issue_doc.posted_at IS NOT NULL
              AND claim.booking_end_at &lt;= #{now}
              AND claim.status NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')
              AND (claim.close_reason IS NULL OR claim.close_reason &lt;&gt; 'ABANDONED')
            </script>
            """)
    List<Long> selectReturnReminderClaimIds(@Param("now") Instant now);

    @Select("<script>SELECT COUNT(DISTINCT claim.id) " + UNRETURNED_FROM + """
            WHERE issue_doc.deleted = 0 AND issue_doc.document_type = 'OUTBOUND'
              AND issue_doc.business_scene = 'CLAIM_ISSUE' AND issue_doc.posted_at IS NOT NULL
              AND claim.id = #{claimId}
              AND claim.booking_end_at &lt;= #{now}
              AND claim.status NOT IN ('CANCELLED', 'REJECTED', 'EXPIRED')
              AND (claim.close_reason IS NULL OR claim.close_reason &lt;&gt; 'ABANDONED')
            </script>
            """)
    int countReturnReminderClaim(@Param("claimId") Long claimId, @Param("now") Instant now);
}

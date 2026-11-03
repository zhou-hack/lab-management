package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MaterialClaimMapper extends BaseMapper<MaterialClaim> {

    @Select("SELECT * FROM material_claim WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MaterialClaim selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM material_claim WHERE biz_document_id = #{documentId} AND deleted = 0 FOR UPDATE")
    MaterialClaim selectByBizDocumentIdForUpdate(@Param("documentId") Long documentId);

    @Select("SELECT * FROM material_claim WHERE business_chain_no = #{businessChainNo} AND deleted = 0 AND claim_kind <> 'CHILD' LIMIT 1")
    MaterialClaim selectByBusinessChainNo(@Param("businessChainNo") String businessChainNo);

    @Select("SELECT COUNT(*) FROM material_claim WHERE business_chain_no = #{businessChainNo}")
    long countByBusinessChainNo(@Param("businessChainNo") String businessChainNo);

    @Select("""
            SELECT claim.* FROM material_claim claim
            WHERE claim.deleted = 0 AND claim.status IN ('PENDING', 'APPROVED')
              AND claim.claim_kind &lt;&gt; 'ROOT'
              AND claim.booking_date < #{today}
              AND NOT EXISTS (
                  SELECT 1 FROM material_document outbound
                  WHERE outbound.business_chain_id = claim.id
                    AND outbound.document_type = 'OUTBOUND' AND outbound.deleted = 0
              )
            ORDER BY claim.booking_end_at ASC, claim.id ASC
            """)
    List<MaterialClaim> selectExpirationCandidates(@Param("today") LocalDate today);
}

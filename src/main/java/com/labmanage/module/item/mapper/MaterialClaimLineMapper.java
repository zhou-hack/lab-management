package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialClaimLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface MaterialClaimLineMapper extends BaseMapper<MaterialClaimLine> {

    @Select("""
            SELECT * FROM material_claim_line
            WHERE claim_id = #{claimId} AND revision_no = #{revisionNo} AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MaterialClaimLine> selectCurrentByClaimIdForUpdate(@Param("claimId") Long claimId,
                                                            @Param("revisionNo") Integer revisionNo);

    @Update("""
            UPDATE material_claim_line
            SET issued_quantity = issued_quantity + #{quantity},
                update_time = #{updateTime}, update_user_id = #{operatorId}
            WHERE id = #{id} AND claim_id = #{claimId} AND revision_no = #{revisionNo} AND deleted = 0
              AND issued_quantity + #{quantity} <= requested_quantity
            """)
    int increaseIssuedQuantity(@Param("id") Long id,
                               @Param("claimId") Long claimId,
                               @Param("revisionNo") Integer revisionNo,
                               @Param("quantity") BigDecimal quantity,
                               @Param("updateTime") Instant updateTime,
                               @Param("operatorId") Long operatorId);
}

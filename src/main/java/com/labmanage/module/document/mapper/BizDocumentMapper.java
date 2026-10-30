package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.BizDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 统一单据 Mapper
 */
@Mapper
public interface BizDocumentMapper extends BaseMapper<BizDocument> {

    /** 按单据编号查询（场地预约 "LAB"+预约ID / 设备 EQ 前缀编号，均唯一）。 */
    @Select("SELECT * FROM biz_document WHERE document_no = #{documentNo} AND deleted = 0")
    BizDocument selectByDocumentNo(@Param("documentNo") String documentNo);

    @Select("""
            SELECT * FROM biz_document
            WHERE owner_user_id = #{ownerUserId} AND document_type = #{documentType}
              AND idempotency_key = #{idempotencyKey} AND deleted = 0
            FOR UPDATE
            """)
    BizDocument selectByOwnerIdempotencyForUpdate(@Param("ownerUserId") Long ownerUserId,
                                                   @Param("documentType") String documentType,
                                                   @Param("idempotencyKey") String idempotencyKey);
}

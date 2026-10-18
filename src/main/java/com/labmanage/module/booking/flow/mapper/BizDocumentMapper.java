package com.labmanage.module.booking.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.flow.entity.BizDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 统一单据 Mapper
 */
@Mapper
public interface BizDocumentMapper extends BaseMapper<BizDocument> {

    /** 按单据编号查询（场地预约编号 "LAB"+预约ID）。 */
    @Select("SELECT * FROM biz_document WHERE document_no = #{documentNo} AND deleted = 0")
    BizDocument selectByDocumentNo(@Param("documentNo") String documentNo);
}

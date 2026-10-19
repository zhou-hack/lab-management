package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.BizDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一单据 Mapper
 */
@Mapper
public interface BizDocumentMapper extends BaseMapper<BizDocument> {
}

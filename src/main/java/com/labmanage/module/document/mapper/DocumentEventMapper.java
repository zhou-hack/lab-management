package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.DocumentEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单据事件 Mapper
 */
@Mapper
public interface DocumentEventMapper extends BaseMapper<DocumentEvent> {
}

package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.DocumentEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单据事件 Mapper（不可变历史，只插入与查询）
 */
@Mapper
public interface DocumentEventMapper extends BaseMapper<DocumentEvent> {
}

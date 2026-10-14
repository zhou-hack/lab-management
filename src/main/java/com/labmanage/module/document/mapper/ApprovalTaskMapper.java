package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批阶段待办 Mapper
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {
}

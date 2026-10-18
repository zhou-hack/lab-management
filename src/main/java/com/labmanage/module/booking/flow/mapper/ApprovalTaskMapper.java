package com.labmanage.module.booking.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.flow.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批阶段任务 Mapper
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /** 按单据与阶段码查询任务（uk_approval_task 维度，最多一行）。 */
    @Select("SELECT * FROM approval_task WHERE document_id = #{documentId} "
            + "AND stage_code = #{stageCode} AND deleted = 0")
    ApprovalTask selectByDocumentStage(@Param("documentId") Long documentId,
                                       @Param("stageCode") String stageCode);
}

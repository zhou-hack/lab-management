package com.labmanage.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.document.entity.ApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通用审批阶段任务 Mapper
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    /** 按单据与阶段码查询任务（适用于该阶段仅有单轮任务的场景）。 */
    @Select("SELECT * FROM approval_task WHERE document_id = #{documentId} "
            + "AND stage_code = #{stageCode} AND deleted = 0")
    ApprovalTask selectByDocumentStage(@Param("documentId") Long documentId,
                                       @Param("stageCode") String stageCode);
}

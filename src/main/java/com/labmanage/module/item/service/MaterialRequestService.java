package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;

/**
 * 物料申领服务（登记 → 审批 → 实发出库；批准不扣库存，实发走库存单据过账回写）
 */
public interface MaterialRequestService {

    /** 登记申领单（DRAFT，自动取定额快照并标记超定额/双签），返回申领单 ID */
    String create(MaterialRequestCreateDTO dto);

    /** 分页查询 */
    PageResult<MaterialRequestVO> page(MaterialRequestQuery query);

    /** 详情（含申领行） */
    MaterialRequestDetailVO get(String id);

    /** 审批（DRAFT → APPROVED；逐行写批准量，不超过申请量） */
    void approve(String id, MaterialRequestApproveDTO dto);

    /** 拒绝（DRAFT → REJECTED） */
    void reject(String id);

    /** 取消（未实发状态才可取消） */
    void cancel(String id);

    /**
     * 实发出库：生成关联申领行的 ISSUE 草稿单（行上 request_line_id 回绑申领行），
     * 审批与过账走库存单据既有流程；过账时回写实发量并推进申领状态。返回出库单 ID。
     */
    String issue(String id, MaterialRequestIssueDTO dto);
}

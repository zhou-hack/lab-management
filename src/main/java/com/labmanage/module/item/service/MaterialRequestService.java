package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.dto.MaterialRequestReturnDTO;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
import com.labmanage.module.item.vo.ReturnableLineVO;
import com.labmanage.module.item.vo.TeacherOptionVO;

import java.util.List;

/**
 * 物料申领服务（登记 → 两级审核 → 实发出库；批准不扣库存，实发走库存单据过账回写）
 * <p>
 * 两级审核（2026-10-19）：SELF 本人申领经 DRAFT → TEACHER_APPROVED（老师审核）→ APPROVED（管理员终审）；
 * PROXY 学生代理代领只能引用老师已审核通过的源申领单（request_type=SELF、status=TEACHER_APPROVED），
 * 创建即 TEACHER_APPROVED，绑定责任老师与代领人留档，直接交由管理员终审。
 */
public interface MaterialRequestService {

    /** 登记申领单（SELF：DRAFT；PROXY：TEACHER_APPROVED），返回申领单 ID */
    String create(MaterialRequestCreateDTO dto);

    /** 分页查询 */
    PageResult<MaterialRequestVO> page(MaterialRequestQuery query);

    /** 详情（含申领行） */
    MaterialRequestDetailVO get(String id);

    /** 老师审核（DRAFT → TEACHER_APPROVED；逐行写批准量，不超过申请量） */
    void teacherApprove(String id, MaterialRequestApproveDTO dto);

    /** 老师审核拒绝（DRAFT → REJECTED） */
    void teacherReject(String id);

    /** 管理员终审（TEACHER_APPROVED → APPROVED；可逐行调整批准量，不超过申请量） */
    void approve(String id, MaterialRequestApproveDTO dto);

    /** 管理员终审拒绝（TEACHER_APPROVED → REJECTED，含学生代理代领单） */
    void reject(String id);

    /** 取消（未实发状态才可取消；取消的代领单释放源行占用） */
    void cancel(String id);

    /**
     * 实发出库：生成关联申领行的 ISSUE 草稿单（行上 request_line_id 回绑申领行），
     * 审批与过账走库存单据既有流程；过账时回写实发量并推进申领状态。返回出库单 ID。
     */
    String issue(String id, MaterialRequestIssueDTO dto);

    /** 学生代理代领源单分页（老师已审核通过的本人申领单，支持按责任老师筛选） */
    PageResult<ProxySourceVO> proxySources(MaterialRequestQuery query);

    /** 源申领单行含剩余可代领量（批准量 − 实发量 − 已被有效代领单占用申请量） */
    List<ProxyLineVO> proxyLines(String sourceRequestId);

    /** 审批教师下拉选项（TEACHER 角色启用用户；学生申领必填审批教师） */
    List<TeacherOptionVO> teacherOptions();

    /** 申领单可归还行（已过账出库行 + 累计已归还量 + 可归还量） */
    List<ReturnableLineVO> returnableLines(String id);

    /**
     * 申领单归还：生成绑定原出库行的 RETURN_IN 归还单草稿（关联申领单留档），
     * 审批与过账走库存单据既有流程，过账回增库存并生成 RETURNED 结算。返回归还单 ID。
     */
    String returnRequest(String id, MaterialRequestReturnDTO dto);
}

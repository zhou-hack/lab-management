package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
import com.labmanage.module.item.vo.BookingOptionVO;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
import com.labmanage.module.item.vo.TeacherOptionVO;

import java.util.List;

/**
 * 物料申领服务（登记 → 管理员终审 → 实发出库；批准不扣库存，实发走库存单据过账回写）
 * <p>
 * TEACHER 老师预约（单级管理员终审）+ PROXY 学生代理代领（2026-10-23 恢复）：
 * 代领只能引用老师端已审核通过（TEACHER、TEACHER_APPROVED）的老师预约单，
 * 老师作为第一责任人继承源单、免老师级审核，创建即 TEACHER_APPROVED 直接交管理员终审。
 */
public interface MaterialRequestService {

    /** 登记申领单（老师预约创建即 TEACHER_APPROVED；代理代领引用源单同样创建即 TEACHER_APPROVED），返回申领单 ID */
    String create(MaterialRequestCreateDTO dto);

    /** 分页查询（支持按申领类型过滤） */
    PageResult<MaterialRequestVO> page(MaterialRequestQuery query);

    /** 详情（含申领行） */
    MaterialRequestDetailVO get(String id);

    /** 管理员终审（TEACHER_APPROVED → APPROVED；可逐行调整批准量，不超过申请量） */
    void approve(String id, MaterialRequestApproveDTO dto);

    /** 管理员终审拒绝（TEACHER_APPROVED → REJECTED） */
    void reject(String id);

    /** 取消（未实发状态才可取消） */
    void cancel(String id);

    /**
     * 实发出库：生成关联申领行的 ISSUE 草稿单（行上 request_line_id 回绑申领行），
     * 审批与过账走库存单据既有流程；过账时回写实发量并推进申领状态。返回出库单 ID。
     */
    String issue(String id, MaterialRequestIssueDTO dto);

    /** 已预约课程候选搜索（仅待审批/已批准场地预约单，新建申领单选择关联预约用） */
    List<BookingOptionVO> bookingOptions(String keyword);

    /** 老师候选搜索（老师预约选择归属老师用；仅启用 TEACHER 角色用户） */
    List<TeacherOptionVO> teacherOptions(String keyword);

    /**
     * 学生代理代领源申领单分页：老师端已审核通过（TEACHER_APPROVED）的老师预约单，
     * 支持按责任老师、实验室过滤。
     */
    PageResult<ProxySourceVO> proxySources(MaterialRequestQuery query);

    /**
     * 源申领单行（含剩余可代领量 = 批准量 − 实发量 − 已被有效代领单占用的申请量），
     * 行上 lineId 为代领提交参数 sourceLineId。
     */
    List<ProxyLineVO> proxyLines(String sourceRequestId);
}

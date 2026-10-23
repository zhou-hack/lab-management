package com.labmanage.module.item.enums;

/**
 * 申领类型（2026-10-23 恢复学生代理代领）
 * <p>
 * TEACHER 老师预约：老师本人发起并作为第一责任人，创建即 TEACHER_APPROVED，仅由管理员终审；
 * PROXY 学生代理代领：只能引用老师端已审核通过的源申领单（request_type=TEACHER、status=TEACHER_APPROVED）
 * 并按源行剩余可代领量领取，老师作为第一责任人继承源单（免老师级审核），创建即 TEACHER_APPROVED，
 * 绑定源单归属老师（owner_teacher_id）与代领人（operator_id）留档，直接交由管理员终审，
 * 不能凭空新建空白申领。
 */
public enum RequestType {
    /** 老师预约 */
    TEACHER,
    /** 学生代理代领 */
    PROXY
}

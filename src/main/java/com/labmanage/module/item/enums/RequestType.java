package com.labmanage.module.item.enums;

/**
 * 申领类型（2026-10-19 两级审核定稿）
 * <p>
 * SELF 本人申领：学生个人发起，须经老师审核（DRAFT → TEACHER_APPROVED）再经管理员终审（→ APPROVED）；
 * PROXY 学生代理代领：只能引用老师已审核通过的源申领单（request_type=SELF、status=TEACHER_APPROVED）
 * 并按源行剩余可代领量领取，创建即 TEACHER_APPROVED，绑定责任老师（owner_teacher_id=源单归属老师）
 * 与代领人（operator_id）留档，直接交由管理员终审，不能凭空新建空白申领。
 */
public enum RequestType {
    /** 本人申领 */
    SELF,
    /** 学生代理代领 */
    PROXY
}

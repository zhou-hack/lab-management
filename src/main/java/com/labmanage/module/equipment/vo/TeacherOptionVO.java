package com.labmanage.module.equipment.vo;

/**
 * 老师下拉选项（借用单提交时指定审批老师；auth 模块落地后可扩展为统一人员组件）
 */
public record TeacherOptionVO(
        String id,
        String realName,
        String personNo
) {
}

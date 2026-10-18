package com.labmanage.module.booking.flow.enums;

/**
 * 单据事件操作人类型（document_event.actor_type）。
 */
public enum ActorType {

    /** 登录用户操作（教师/管理员） */
    USER,
    /** 系统事件（定时任务自动取消等） */
    SYSTEM
}

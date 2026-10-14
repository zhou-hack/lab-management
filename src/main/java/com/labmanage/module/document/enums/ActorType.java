package com.labmanage.module.document.enums;

/**
 * 单据事件操作人类型（SYSTEM 操作不能冒充管理员）
 */
public enum ActorType {

    /** 登录用户 */
    USER,

    /** 系统任务 */
    SYSTEM
}

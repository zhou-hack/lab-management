package com.labmanage.module.item.enums;

/**
 * 申领单领取方式（2026-10-24 定稿）：
 * 仅老师本人申领单可选「学生代理领取」，作为学生代理代领源单的过滤条件；
 * 学生本人申领与代领单固定为本人领取。
 */
public enum PickupMode {
    /** 本人领取 */
    SELF_PICKUP,
    /** 学生代理领取（老师单可选，持有效助理授权的学生可代领） */
    PROXY_PICKUP
}

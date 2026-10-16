package com.labmanage.roles.enums;

/**
 * 预置角色码（sys_role.role_code），id 为初始数据固定小 ID。
 * ASSISTANT 为附加角色，不持久化到 sys_user_role，由有效 assistant_grant 动态计算。
 * OPS_ADMIN 为最高级运维角色，仅 SQL 预置，不开放页面创建，不受数据范围约束。
 */
public enum RoleCode {

    ADMIN(1L),
    TEACHER(2L),
    STUDENT(3L),
    ASSISTANT(4L),
    OPS_ADMIN(5L);

    private final Long id;

    RoleCode(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}

package com.labmanage.roles.enums;

/**
 * 预置角色码（sys_role.role_code），id 为初始数据固定小 ID。
 * OPS_ADMIN 为最高级运维角色，仅 SQL 预置，不开放页面创建，不受数据范围约束。
 */
public enum RoleCode {

    ADMIN(1L),
    TEACHER(2L),
    STUDENT(3L),
    OPS_ADMIN(5L);

    private final Long id;

    RoleCode(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}

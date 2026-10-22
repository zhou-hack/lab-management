-- ============================================================================
-- 实验室设备管理系统 · RBAC 权限体系建表脚本（V2.0 Final）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.1 节重写，替代 V1.0 旧版。
-- ----------------------------------------------------------------------------
-- 与 V1.0 的主要差异：
--   1. 主键统一为 BIGINT 雪花 ID（MyBatis-Plus ASSIGN_ID），接口按字符串输出；
--   2. 新增 sys_person_roster 预录身份名单（学生/老师首登匹配学工号+手机号）；
--   3. 组织由 college_id/dept 字符串升级为 sys_department 树形表；
--   4. 权限独立为 sys_permission + sys_role_permission，不再挂载在菜单表；
--   5. 新增 sys_user_data_scope 数据范围表与 user_booking_restriction 预约限制表；
--   6. ASSISTANT 附加角色不持久化到 sys_user_role，由有效 assistant_grant 动态计算；
--   7. 全部表带公共字段 id/create_time/update_time/deleted/create_user_id/update_user_id；
--   8. 时间列 DATETIME(3) 存 UTC，默认值 UTC_TIMESTAMP(3)。
-- ----------------------------------------------------------------------------
-- 执行前提：
--   * MySQL 8.4.11 LTS， InnoDB / utf8mb4 / utf8mb4_0900_ai_ci；
--   * 服务端与连接会话时区均为 +00:00（Connector/J 参数 connectionTimeZone=UTC
--     &forceConnectionTimeZoneToSession=true），此时 CURRENT_TIMESTAMP 即 UTC；
--   * 跨模块引用（campus/building/lab/biz_document）只建列与索引，外键由所属
--     模块脚本补充（实施分组 A 的空间表、分组 B 的单据表）；
--   * 审计字段 create_user_id/update_user_id 统一不建外键，避免组织/用户表
--     循环依赖；其值由服务端写入 sys_user.id，系统任务为 NULL。
-- 初始数据：预置部门（设备处）、4 个角色（第 5 个角色 OPS_ADMIN 见增量脚本
--   sql/sys_ops_role.sql）、初始管理员 admin 及其角色绑定；
--   权限与菜单初始数据由业务模块脚本按 `模块:资源:操作` 规范登记。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. sys_department 组织表
-- ----------------------------------------------------------------------------
CREATE TABLE sys_department (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    parent_id       BIGINT       NULL                    COMMENT '父部门 ID（NULL=根节点，禁止组织循环由服务端校验）',
    department_code VARCHAR(64)  NOT NULL                COMMENT '部门编码',
    department_name VARCHAR(128) NOT NULL                COMMENT '部门名称',
    department_type VARCHAR(32)  NOT NULL                COMMENT '类型：SCHOOL/COLLEGE/ASSET_OFFICE',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_department_code (department_code),
    KEY idx_sys_department_parent (parent_id),
    CONSTRAINT fk_sys_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (id),
    CONSTRAINT chk_sys_department_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_department_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织表（SCHOOL/COLLEGE/ASSET_OFFICE）';

-- ----------------------------------------------------------------------------
-- 2. sys_person_roster 预录身份名单表
--    学校预录入的学生/老师身份名单；首登按学工号+手机号匹配建档，
--    不能由待登录用户自行修改身份类型。
-- ----------------------------------------------------------------------------
CREATE TABLE sys_person_roster (
    id            BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    person_no     VARCHAR(64)  NOT NULL                COMMENT '学工号（唯一，删除后不复用）',
    person_type   VARCHAR(32)  NOT NULL                COMMENT '身份类型：STUDENT/TEACHER',
    real_name     VARCHAR(128) NOT NULL                COMMENT '姓名',
    phone         VARCHAR(32)  NOT NULL                COMMENT '手机号（唯一，验证码登录匹配凭证）',
    department_id BIGINT       NOT NULL                COMMENT '所属部门（→ sys_department.id）',
    enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1有效 0停用',
    create_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT      NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_person_roster_no (person_no),
    UNIQUE KEY uk_sys_person_roster_phone (phone),
    KEY idx_sys_person_roster_dept (department_id),
    CONSTRAINT fk_sys_person_roster_dept FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_sys_person_roster_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_person_roster_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预录身份名单表（学生/老师首登匹配依据）';

-- ----------------------------------------------------------------------------
-- 3. sys_user 用户表
--    管理员账号与密码必填（后台导入建档）；学生/老师匹配有效名单后首登建档。
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id                  BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    roster_id           BIGINT       NULL                    COMMENT '名单 ID（→ sys_person_roster.id；管理员为 NULL）',
    user_type           VARCHAR(32)  NOT NULL                COMMENT '用户类型：STUDENT/TEACHER/ADMIN',
    real_name           VARCHAR(128) NOT NULL                COMMENT '姓名',
    account_name        VARCHAR(64)  NULL                    COMMENT '登录账号（管理员必填；唯一）',
    phone               VARCHAR(32)  NULL                    COMMENT '手机号（学生/老师必填；唯一）',
    person_no           VARCHAR(64)  NULL                    COMMENT '学工号（唯一，删除后不复用）',
    department_id       BIGINT       NOT NULL                COMMENT '所属部门（→ sys_department.id）',
    password_hash       VARCHAR(255) NULL                    COMMENT '密码散列（带算法参数；管理员必填）',
    password_changed_at DATETIME(3)  NULL                    COMMENT '最近改密时间（UTC，仅审计记录；2026-10-19 需求定稿不强制更换）',
    failed_login_count  INT          NOT NULL DEFAULT 0      COMMENT '连续登录失败次数（达 5 次锁定 30 分钟）',
    locked_until        DATETIME(3)  NULL                    COMMENT '锁定截止时间（UTC）',
    enabled             TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1正常 0停用',
    create_time         DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time         DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id      BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，自引用不建外键）',
    update_user_id      BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_roster (roster_id),
    UNIQUE KEY uk_sys_user_account (account_name),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_person_no (person_no),
    KEY idx_sys_user_dept_type (department_id, user_type, enabled),
    CONSTRAINT fk_sys_user_roster FOREIGN KEY (roster_id) REFERENCES sys_person_roster (id),
    CONSTRAINT fk_sys_user_dept FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_sys_user_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_user_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ----------------------------------------------------------------------------
-- 4. sys_role 角色表（一期预置，不开放自定义）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_role (
    id            BIGINT       NOT NULL                COMMENT '主键',
    role_code     VARCHAR(64)  NOT NULL                COMMENT '角色标识：STUDENT/TEACHER/ASSISTANT/ADMIN',
    role_name     VARCHAR(128) NOT NULL                COMMENT '角色名称',
    enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1正常 0停用',
    create_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT      NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    CONSTRAINT chk_sys_role_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_role_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ----------------------------------------------------------------------------
-- 5. sys_permission 权限表（标识规范：模块:资源:操作，如 item:material:create）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_permission (
    id              BIGINT       NOT NULL                COMMENT '主键',
    permission_code VARCHAR(128) NOT NULL                COMMENT '权限标识（模块:资源:操作，唯一）',
    permission_name VARCHAR(128) NOT NULL                COMMENT '权限名称',
    module_code     VARCHAR(64)  NOT NULL                COMMENT '所属模块编码',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1正常 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_module (module_code),
    CONSTRAINT chk_sys_permission_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_permission_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- ----------------------------------------------------------------------------
-- 6. sys_menu 菜单表（目录/页面/动作；动作绑定权限标识）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_menu (
    id             BIGINT       NOT NULL                COMMENT '主键',
    parent_id      BIGINT       NULL                    COMMENT '父菜单 ID（NULL=根节点）',
    menu_code      VARCHAR(64)  NOT NULL                COMMENT '菜单编码（唯一）',
    menu_name      VARCHAR(128) NOT NULL                COMMENT '菜单名称',
    route_path     VARCHAR(255) NULL                    COMMENT '路由地址（目录/页面有值）',
    component_key  VARCHAR(64)  NULL                    COMMENT '前端组件键（仅可对应前端白名单）',
    permission_id  BIGINT       NULL                    COMMENT '绑定权限（→ sys_permission.id，动作类必填）',
    menu_type      VARCHAR(32)  NOT NULL                COMMENT '类型：DIRECTORY/PAGE/ACTION',
    sort_order     INT          NOT NULL DEFAULT 0      COMMENT '同级排序（升序）',
    enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1正常 0停用',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_code (menu_code),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_permission (permission_id),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu (id),
    CONSTRAINT fk_sys_menu_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id),
    CONSTRAINT chk_sys_menu_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_menu_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表（动态路由来源）';

-- ----------------------------------------------------------------------------
-- 7. sys_role_permission 角色权限关联表
-- ----------------------------------------------------------------------------
CREATE TABLE sys_role_permission (
    id            BIGINT      NOT NULL                COMMENT '主键',
    role_id       BIGINT      NOT NULL                COMMENT '角色 ID（→ sys_role.id）',
    permission_id BIGINT      NOT NULL                COMMENT '权限 ID（→ sys_permission.id）',
    enabled       TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1有效 0停用',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_perm (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id),
    CONSTRAINT chk_sys_role_permission_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_role_permission_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- ----------------------------------------------------------------------------
-- 8. sys_role_menu 角色菜单关联表
-- ----------------------------------------------------------------------------
CREATE TABLE sys_role_menu (
    id            BIGINT      NOT NULL                COMMENT '主键',
    role_id       BIGINT      NOT NULL                COMMENT '角色 ID（→ sys_role.id）',
    menu_id       BIGINT      NOT NULL                COMMENT '菜单 ID（→ sys_menu.id）',
    enabled       TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1有效 0停用',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id),
    CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id),
    CONSTRAINT chk_sys_role_menu_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_role_menu_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

-- ----------------------------------------------------------------------------
-- 9. sys_user_role 用户角色关联表
--    主身份角色（STUDENT/TEACHER/ADMIN）每用户只能有一个，由服务端校验；
--    ASSISTANT 不在此手动授予，由有效 assistant_grant 动态计算。
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user_role (
    id            BIGINT      NOT NULL                COMMENT '主键',
    user_id       BIGINT      NOT NULL                COMMENT '用户 ID（→ sys_user.id）',
    role_id       BIGINT      NOT NULL                COMMENT '角色 ID（→ sys_role.id）',
    enabled       TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1有效 0停用',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT chk_sys_user_role_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_user_role_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- ----------------------------------------------------------------------------
-- 10. sys_user_data_scope 用户数据范围表
--     仅管理员写范围按归口授权；ALL 不填目标 ID，其余类型恰好填写对应目标。
--     campus_id/building_id/lab_id 为跨模块引用，外键由对应模块脚本补充。
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user_data_scope (
    id            BIGINT      NOT NULL                COMMENT '主键',
    user_id       BIGINT      NOT NULL                COMMENT '用户 ID（→ sys_user.id）',
    scope_type    VARCHAR(32) NOT NULL                COMMENT '范围类型：ALL/DEPARTMENT/CAMPUS/BUILDING/LAB',
    department_id BIGINT      NULL                    COMMENT '目标部门（→ sys_department.id，scope_type=DEPARTMENT 时必填）',
    campus_id     BIGINT      NULL                    COMMENT '目标校区（→ campus.id，跨模块，外键由空间模块补充）',
    building_id   BIGINT      NULL                    COMMENT '目标楼栋（→ building.id，跨模块，外键由空间模块补充）',
    lab_id        BIGINT      NULL                    COMMENT '目标实验室（→ lab.id，跨模块，外键由空间模块补充）',
    access_mode   VARCHAR(32) NOT NULL                COMMENT '操作级别：READ/WRITE（WRITE 含 READ）',
    -- 目标去重键：MySQL 唯一索引允许多个 NULL，用生成列将四目标归一为非空键
    scope_target_id BIGINT GENERATED ALWAYS AS (COALESCE(department_id, campus_id, building_id, lab_id, 0)) STORED COMMENT '范围目标归一键（生成列，勿写）',
    enabled       TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1有效 0停用',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_data_scope (user_id, scope_type, access_mode, scope_target_id),
    KEY idx_sys_user_data_scope_dept (department_id),
    CONSTRAINT fk_sys_user_data_scope_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_data_scope_dept FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_sys_user_data_scope_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_sys_user_data_scope_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户数据范围表（管理员归口授权）';

-- ----------------------------------------------------------------------------
-- 11. user_booking_restriction 预约限制表
--     时间内禁止指定 EQUIPMENT/LAB/ALL 预约；自动逾期冻结有源单据，
--     (source_document_id, restriction_type) 唯一保证扫描重跑不重复冻结；
--     手动创建（source_document_id 为 NULL）允许多条，由业务校验时段。
-- ----------------------------------------------------------------------------
CREATE TABLE user_booking_restriction (
    id                 BIGINT      NOT NULL                COMMENT '主键',
    user_id            BIGINT      NOT NULL                COMMENT '被限制用户（→ sys_user.id）',
    source_document_id BIGINT      NULL                    COMMENT '来源单据（→ biz_document.id，跨模块，外键由单据模块补充）',
    restriction_type   VARCHAR(32) NOT NULL                COMMENT '限制类型：EQUIPMENT/LAB/ALL',
    reason             TEXT        NULL                    COMMENT '限制原因',
    start_at           DATETIME(3) NOT NULL                COMMENT '限制开始时间（UTC）',
    end_at             DATETIME(3) NULL                    COMMENT '限制结束时间（UTC；NULL=直至撤销）',
    revoked_at         DATETIME(3) NULL                    COMMENT '撤销时间（UTC）',
    revoked_by         BIGINT      NULL                    COMMENT '撤销人（→ sys_user.id）',
    create_time        DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time        DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted            TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id     BIGINT      NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id     BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_booking_restriction_src (source_document_id, restriction_type),
    KEY idx_user_booking_restriction_user (user_id, start_at, end_at),
    CONSTRAINT fk_user_booking_restriction_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_booking_restriction_revoker FOREIGN KEY (revoked_by) REFERENCES sys_user (id),
    CONSTRAINT chk_user_booking_restriction_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约限制表（超期冻结与手动限制）';

-- ============================================================================
-- 初始数据
-- ============================================================================

-- 预置部门：设备处（管理员归口；学院等部门由基础数据模块维护）
INSERT INTO sys_department (id, parent_id, department_code, department_name, department_type, enabled)
VALUES (1, NULL, 'ASSET_OFFICE', '设备处', 'ASSET_OFFICE', 1);

-- 预置角色（权限清单四类业务角色；第 5 个角色 OPS_ADMIN 见 sql/sys_ops_role.sql；ID 固定小值，与雪花 ID 不冲突）
-- 2026-10-19 需求定稿权限层级：学生(代理) → 老师 → 实验室管理员 → 系统/运维管理员；
-- 角色 1 显示名同步改为"实验室管理员"（role_code=ADMIN 不变）。
INSERT INTO sys_role (id, role_code, role_name, enabled) VALUES
(1, 'ADMIN',     '实验室管理员', 1),
(2, 'TEACHER',   '老师',     1),
(3, 'STUDENT',   '学生',     1),
(4, 'ASSISTANT', '老师助理', 1);

-- 初始管理员账号（password_hash 为占位值，部署前必须替换为 BCrypt 密文并强制首登修改；
-- 密码规则：长度 ≥8 位且含数字+字母；2026-10-19 需求定稿不设有效期强制更换）
INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no,
                      department_id, password_hash, failed_login_count, enabled)
VALUES (1, NULL, 'ADMIN', '系统管理员', 'admin', NULL, NULL,
        1, '$2a$10$REPLACE_WITH_BCRYPT_HASH', 0, 1);

-- 管理员角色绑定
INSERT INTO sys_user_role (id, user_id, role_id, enabled) VALUES (1, 1, 1, 1);

-- ============================================================================
-- 系统模块权限与菜单初始数据（登记依据 docs/RBAC实施清单.md）
-- ID 段约定：system 模块 1-999，业务模块按 RBAC实施清单 第一节分段登记。
-- ============================================================================

-- system 模块权限（28 项；28-31 于 2026-10-20 收编自 dev/basic_model 与 dev/auth，
-- 绑定按需求定稿不授 ADMIN，由 OPS_ADMIN 动态覆盖；
-- 17-19 system:restriction:* 于 2026-10-21 随预约限制页面下线移除，编号弃用不复用）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(1,  'system:user:list',        '用户列表',       'system', 1),
(2,  'system:user:get',         '用户详情',       'system', 1),
(3,  'system:user:create',      '创建用户',       'system', 1),
(4,  'system:user:update',      '编辑用户',       'system', 1),
(5,  'system:user:enable',      '启用停用用户',   'system', 1),
(27, 'system:user:reset_password', '重置密码',    'system', 1),
(31, 'system:user:bind_role',   '绑定管理员角色', 'system', 1),
(6,  'system:roster:list',      '名单列表',       'system', 1),
(7,  'system:roster:create',    '名单录入',       'system', 1),
(8,  'system:roster:update',    '名单编辑',       'system', 1),
(9,  'system:roster:enable',    '名单启用停用',   'system', 1),
(29, 'system:roster:export',    '名单导出',       'system', 1),
(30, 'system:roster:import',    '名单导入',       'system', 1),
(10, 'system:department:list',   '组织列表',       'system', 1),
(11, 'system:department:create', '组织新增',       'system', 1),
(12, 'system:department:update', '组织编辑',       'system', 1),
(13, 'system:department:enable', '组织启用停用',   'system', 1),
(14, 'system:role:list',         '角色权限查看',   'system', 1),
(28, 'system:role:assign',       '调整角色权限',   'system', 1),
(15, 'system:scope:list',        '数据范围查看',   'system', 1),
(16, 'system:scope:grant',       '数据范围授权',   'system', 1),
(20, 'system:log:list',          '操作日志查询',   'system', 1),
(21, 'system:policy:list',       '策略参数查看',   'system', 1),
(22, 'system:policy:publish',    '策略参数发布',   'system', 1),
(23, 'system:grant:list',        '助理授权查看',   'system', 1),
(24, 'system:grant:create',      '助理授权新增',   'system', 1),
(25, 'system:grant:revoke',      '助理授权撤销',   'system', 1),
(26, 'system:grant:audit',       '助理授权审计',   'system', 1);

-- system 模块菜单（目录 1 + 用户组目录 12 + 页面 8；
--   2026-10-20 起用户管理与名单管理合并为"人员管理"统一视图（component SystemUserRoster，菜单 id 复用 2）；
--   2026-10-21 预约限制页面与接口下线（菜单 7、权限 17-19 移除，编号弃用不复用；user_booking_restriction 表与 Mapper 保留供预约模块逾期冻结））
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(1,  NULL, 'system',              '系统管理',     '/system',            NULL,              NULL, 'DIRECTORY', 90, 1),
(12, NULL, 'system_user_group',   '用户组',       '/system/user_group', NULL,              NULL, 'DIRECTORY', 40, 1),
(2,  12,   'system_user',         '人员管理',     '/system/user',       'SystemUserRoster', 1,    'PAGE',      1,  1),
(4,  1,    'system_department',   '组织管理',     '/system/department', 'SystemDepartment', 10,   'PAGE',      3,  1),
(5,  1,    'system_role',         '角色权限',     '/system/role',       'SystemRole',       14,   'PAGE',      4,  1),
(6,  1,    'system_scope',        '数据范围',     '/system/scope',      'SystemScope',      15,   'PAGE',      5,  1),
(8,  1,    'system_log',          '操作日志',     '/system/log',        'SystemLog',        20,   'PAGE',      7,  1),
(9,  1,    'system_policy',       '参数配置',     '/system/policy',     'SystemPolicy',     21,   'PAGE',      8,  1),
(10, 1,    'system_grant_audit',  '授权审计',     '/system/grant',      'SystemGrantAudit', 26,   'PAGE',      9,  1),
(11, NULL, 'grant_manage',        '助理管理',     '/grant',             'GrantManage',      23,   'PAGE',      50, 1);

-- 角色权限绑定：TEACHER 助理授权三项（23-25）。
-- 2026-10-19 需求定稿：system 模块操作（含用户/名单/组织/角色/范围/日志/参数等）
-- 全部移交运维管理员 OPS_ADMIN，由 sql/sys_ops_role.sql 动态绑定，不再绑定 ADMIN(1)；
-- ADMIN 保留场地/设备/物料/预约等业务模块（见 lab_management_all.sql 各模块绑定）。
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(24, 2, 23, 1), (25, 2, 24, 1), (26, 2, 25, 1);

-- 角色菜单绑定：TEACHER 助理管理页（系统管理/用户组菜单同上移交 OPS_ADMIN 动态绑定）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(11, 2, 11, 1);

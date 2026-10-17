-- ============================================================================
-- 实验室设备管理系统 · 空间模块建表与权限登记脚本（实施分组 A · 空间部分）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.2 节与 docs/RBAC实施清单.md 2.2 节。
-- ----------------------------------------------------------------------------
-- 内容：
--   1. 空间四表：campus（校区）/ building（楼栋）/ lab（实验室）/ lab_open_rule（开放规则）；
--   2. 补充跨模块外键：sys_user_data_scope.campus_id/building_id/lab_id、
--      assistant_grant_scope.lab_id（列与索引已在所属脚本创建）；
--   3. 权限登记 1000-1011（space:campus/building/lab × list/create/update/enable）；
--   4. 菜单登记 1000-1002（space 目录 + SpaceCampus + SpaceLab）与角色绑定。
-- 设计要点：
--   * 校区码全局唯一；楼栋码同校区唯一；实验室码全局唯一且同楼栋房间号唯一；
--   * lab_open_rule.open_time/close_time 为上海本地墙钟时间（CLOCK=TIME(0)），
--     不做时区转换；一期仅同日时间窗，open_time < close_time，同日多段分行保存；
--   * 外键一律 RESTRICT，不级联删除历史业务；审计字段不建外键；
--   * space:lab:list 为 ADMIN/TEACHER/STUDENT 共用只读目录权限，
--     写操作（create/update/enable）仅 ADMIN；
--   * 业务表不预置示例数据，校区/楼栋/实验室由管理端录入。
-- 执行前提：sys_rbac.sql、sys_ops_role.sql、assistant_grant.sql 已执行。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. campus 校区表
-- ----------------------------------------------------------------------------
CREATE TABLE campus (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    campus_code    VARCHAR(64)  NOT NULL                COMMENT '校区编码（唯一，删除后不复用）',
    campus_name    VARCHAR(128) NOT NULL                COMMENT '校区名称',
    address        VARCHAR(255) NULL                    COMMENT '地址',
    enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_code (campus_code),
    CONSTRAINT chk_campus_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_campus_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校区表';

-- ----------------------------------------------------------------------------
-- 2. building 楼栋表
-- ----------------------------------------------------------------------------
CREATE TABLE building (
    id            BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    campus_id     BIGINT       NOT NULL                COMMENT '所属校区（→ campus.id）',
    building_code VARCHAR(64)  NOT NULL                COMMENT '楼栋编码（同校区唯一，删除后不复用）',
    building_name VARCHAR(128) NOT NULL                COMMENT '楼栋名称',
    enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT      NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_building_campus_code (campus_id, building_code),
    CONSTRAINT fk_building_campus FOREIGN KEY (campus_id) REFERENCES campus (id),
    CONSTRAINT chk_building_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_building_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楼栋表';

-- ----------------------------------------------------------------------------
-- 3. lab 实验室表
--    实验室码全局唯一，同楼栋房间号唯一；capacity > 0；
--    lab_type 为 Java 枚举映射（SHORT=VARCHAR(32)），不用 MySQL ENUM。
-- ----------------------------------------------------------------------------
CREATE TABLE lab (
    id             BIGINT         NOT NULL                COMMENT '主键（雪花 ID）',
    building_id    BIGINT         NOT NULL                COMMENT '所属楼栋（→ building.id）',
    department_id  BIGINT         NOT NULL                COMMENT '管理部门（→ sys_department.id）',
    lab_code       VARCHAR(64)    NOT NULL                COMMENT '实验室编码（全局唯一，删除后不复用）',
    lab_name       VARCHAR(128)   NOT NULL                COMMENT '实验室名称',
    lab_type       VARCHAR(32)    NOT NULL                COMMENT '类型：TEACHING/RESEARCH/OPEN/COMPREHENSIVE',
    floor_no       VARCHAR(16)    NOT NULL                COMMENT '楼层',
    room_no        VARCHAR(64)    NOT NULL                COMMENT '房间号（同楼栋唯一）',
    capacity       INT            NOT NULL                COMMENT '容纳人数（> 0）',
    area_sqm       DECIMAL(10, 2) NULL                    COMMENT '建筑面积（平方米）',
    location_note  TEXT           NULL                    COMMENT '位置说明',
    enabled        TINYINT        NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time    DATETIME(3)    NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)    NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT         NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT         NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_code (lab_code),
    UNIQUE KEY uk_lab_building_room (building_id, room_no),
    KEY idx_lab_dept_enabled (department_id, enabled),
    KEY idx_lab_building_enabled (building_id, enabled),
    CONSTRAINT fk_lab_building FOREIGN KEY (building_id) REFERENCES building (id),
    CONSTRAINT fk_lab_department FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_lab_capacity CHECK (capacity > 0),
    CONSTRAINT chk_lab_type CHECK (lab_type IN ('TEACHING', 'RESEARCH', 'OPEN', 'COMPREHENSIVE')),
    CONSTRAINT chk_lab_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_lab_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室表';

-- ----------------------------------------------------------------------------
-- 4. lab_open_rule 实验室开放规则表
--    weekday 1-7（周一到周日）；一期仅同日时间窗，open_time < close_time；
--    同日多段开放时间分多行保存；valid_from/valid_to 为纯日期，不做时区转换。
-- ----------------------------------------------------------------------------
CREATE TABLE lab_open_rule (
    id             BIGINT      NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id         BIGINT      NOT NULL                COMMENT '所属实验室（→ lab.id）',
    weekday        INT         NOT NULL                COMMENT '星期：1-7（周一到周日）',
    open_time      TIME(0)     NOT NULL                COMMENT '开放时间（上海本地墙钟）',
    close_time     TIME(0)     NOT NULL                COMMENT '关闭时间（上海本地墙钟，须晚于开放时间）',
    valid_from     DATE        NOT NULL                COMMENT '有效期开始（纯日期）',
    valid_to       DATE        NOT NULL                COMMENT '有效期结束（纯日期，须不早于开始）',
    enabled        TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time    DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT      NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_lab_open_rule_lab (lab_id, weekday, enabled),
    CONSTRAINT fk_lab_open_rule_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT chk_lab_open_rule_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_lab_open_rule_window CHECK (open_time < close_time),
    CONSTRAINT chk_lab_open_rule_valid CHECK (valid_from <= valid_to),
    CONSTRAINT chk_lab_open_rule_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_lab_open_rule_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室开放规则表';

-- ----------------------------------------------------------------------------
-- 5. 补充跨模块外键（列与索引已在所属脚本创建）
-- ----------------------------------------------------------------------------
ALTER TABLE sys_user_data_scope
    ADD CONSTRAINT fk_sys_user_data_scope_campus FOREIGN KEY (campus_id) REFERENCES campus (id),
    ADD CONSTRAINT fk_sys_user_data_scope_building FOREIGN KEY (building_id) REFERENCES building (id),
    ADD CONSTRAINT fk_sys_user_data_scope_lab FOREIGN KEY (lab_id) REFERENCES lab (id);

ALTER TABLE assistant_grant_scope
    ADD CONSTRAINT fk_assistant_grant_scope_lab FOREIGN KEY (lab_id) REFERENCES lab (id);

-- ----------------------------------------------------------------------------
-- 6. 权限登记（space 模块 ID 段 1000-1999）
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(1000, 'space:campus:list',    '校区列表',     'space', 1),
(1001, 'space:campus:create',  '校区创建',     'space', 1),
(1002, 'space:campus:update',  '校区编辑',     'space', 1),
(1003, 'space:campus:enable',  '校区启用停用', 'space', 1),
(1004, 'space:building:list',  '楼栋列表',     'space', 1),
(1005, 'space:building:create','楼栋创建',     'space', 1),
(1006, 'space:building:update','楼栋编辑',     'space', 1),
(1007, 'space:building:enable','楼栋启用停用', 'space', 1),
(1008, 'space:lab:list',       '实验室目录',   'space', 1),
(1009, 'space:lab:create',     '实验室创建',   'space', 1),
(1010, 'space:lab:update',     '实验室编辑',   'space', 1),
(1011, 'space:lab:enable',     '实验室启用停用', 'space', 1);

-- ----------------------------------------------------------------------------
-- 7. 菜单登记（space 目录 + 校区楼栋页 + 实验室目录页）
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(1000, NULL, 'space',        '空间管理',   '/space',        NULL,          NULL, 'DIRECTORY', 60, 1),
(1001, 1000, 'space_campus', '校区楼栋',   '/space/campus', 'SpaceCampus', 1000, 'PAGE',      1,  1),
(1002, 1000, 'space_lab',    '实验室目录', '/space/lab',    'SpaceLab',    1008, 'PAGE',      2,  1);

-- ----------------------------------------------------------------------------
-- 8. 角色权限绑定
--    ADMIN 全量 12 项；TEACHER/STUDENT 仅 space:lab:list 只读目录；OPS_ADMIN 不涉及空间。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(35, 1, 1000, 1), (36, 1, 1001, 1), (37, 1, 1002, 1), (38, 1, 1003, 1),
(39, 1, 1004, 1), (40, 1, 1005, 1), (41, 1, 1006, 1), (42, 1, 1007, 1),
(43, 1, 1008, 1), (44, 1, 1009, 1), (45, 1, 1010, 1), (46, 1, 1011, 1),
(47, 2, 1008, 1),
(48, 3, 1008, 1);

-- ----------------------------------------------------------------------------
-- 9. 角色菜单绑定
--    ADMIN：space 目录 + 两个页面（一期空间维护页挂管理端布局）。
--    TEACHER/STUDENT 仅有 space:lab:list 权限不登记菜单；其只读实验室目录页
--    随分组 B 预约模块以学生端布局落地（届时按需另登记 front 布局菜单）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(18, 1, 1000, 1), (19, 1, 1001, 1), (20, 1, 1002, 1);

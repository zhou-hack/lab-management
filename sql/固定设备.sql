-- ============================================================================
-- 实验室设备管理系统 · 空间基础数据建表脚本（实施分组 A）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.2 节。
-- ----------------------------------------------------------------------------
-- 内容：
--   1. campus 校区表 / building 楼栋表 / lab 实验室表（空间层级：
--      campus → building → lab → equipment_asset）；
--   2. 为 sys_user_data_scope 补充 campus_id/building_id/lab_id 三个外键
--      （sys_rbac.sql 头注约定：跨模块引用只建列，外键由空间脚本补充）；
--   3. space 模块权限与菜单初始数据。
-- ----------------------------------------------------------------------------
-- 执行前提：
--   * MySQL 8.4.11 LTS，InnoDB / utf8mb4 / utf8mb4_0900_ai_ci；
--   * 必须先执行 sys_rbac.sql（依赖 sys_department / sys_user_data_scope /
--     sys_permission / sys_menu / sys_role_permission / sys_role_menu）；
--   * 服务端与连接会话时区均为 +00:00（Connector/J 参数
--     connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true）；
--   * 审计字段 create_user_id/update_user_id 统一不建外键，其值由服务端
--     写入 sys_user.id，系统任务为 NULL。
-- 种子 ID 分段约定（避免与雪花 ID 及其他模块冲突）：
--   * space 权限 101–112；space 菜单 121–125；
--   * sys_role_permission 关联 501–514；sys_role_menu 关联 601–605；
--   * 后续模块脚本（单据 201+/设备 141+ 等）不得复用本段。
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. campus 校区表
-- ----------------------------------------------------------------------------
CREATE TABLE campus (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    campus_code     VARCHAR(64)  NOT NULL                COMMENT '校区编码',
    campus_name     VARCHAR(128) NOT NULL                COMMENT '校区名称',
    address         VARCHAR(255) NULL                    COMMENT '校区地址',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_campus_code (campus_code),
    CONSTRAINT chk_campus_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_campus_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校区表（campus_code 唯一，删除后不复用）';

-- ----------------------------------------------------------------------------
-- 2. building 楼栋表
-- ----------------------------------------------------------------------------
CREATE TABLE building (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    campus_id       BIGINT       NOT NULL                COMMENT '所属校区（→ campus.id）',
    building_code   VARCHAR(64)  NOT NULL                COMMENT '楼栋编码（同校区唯一）',
    building_name   VARCHAR(128) NOT NULL                COMMENT '楼栋名称',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_building_campus_code (campus_id, building_code),
    KEY idx_building_campus_enabled (campus_id, enabled),
    CONSTRAINT fk_building_campus FOREIGN KEY (campus_id) REFERENCES campus (id),
    CONSTRAINT chk_building_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_building_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楼栋表（同校区楼栋码唯一）';

-- ----------------------------------------------------------------------------
-- 3. lab 实验室表
--    实验室码全局唯一，同楼栋房间号唯一；实验室保管位置与资产保管位置分开，
--    资产保管位置见 equipment_asset.lab_id（由设备模块脚本建外键）。
-- ----------------------------------------------------------------------------
CREATE TABLE lab (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    building_id     BIGINT       NOT NULL                COMMENT '所在楼栋（→ building.id）',
    department_id   BIGINT       NOT NULL                COMMENT '归口部门（→ sys_department.id）',
    lab_code        VARCHAR(64)  NOT NULL                COMMENT '实验室编码（全局唯一，删除后不复用）',
    lab_name        VARCHAR(128) NOT NULL                COMMENT '实验室名称',
    lab_type        VARCHAR(32)  NOT NULL                COMMENT '实验室类型（业务字典，如 TEACHING/RESEARCH）',
    floor_no        VARCHAR(16)  NOT NULL                COMMENT '楼层号',
    room_no         VARCHAR(64)  NOT NULL                COMMENT '房间号（同楼栋唯一）',
    capacity        INT          NOT NULL                COMMENT '容纳人数（须大于 0）',
    area_sqm        DECIMAL(10,2) NULL                   COMMENT '面积（平方米）',
    location_note   TEXT         NULL                    COMMENT '位置说明',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_code (lab_code),
    UNIQUE KEY uk_lab_building_room (building_id, room_no),
    KEY idx_lab_department_enabled (department_id, enabled),
    KEY idx_lab_building_enabled (building_id, enabled),
    CONSTRAINT fk_lab_building FOREIGN KEY (building_id) REFERENCES building (id),
    CONSTRAINT fk_lab_department FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_lab_capacity CHECK (capacity > 0),
    CONSTRAINT chk_lab_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_lab_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室表（lab_code 全局唯一，同楼栋房间号唯一）';

-- ----------------------------------------------------------------------------
-- 4. 为 sys_user_data_scope 补充空间外键
--    sys_rbac.sql 建表时 campus_id/building_id/lab_id 只建列与索引，
--    头注约定由本脚本补充外键（RESTRICT，不级联删除）。
-- ----------------------------------------------------------------------------
ALTER TABLE sys_user_data_scope
    ADD CONSTRAINT fk_sys_user_data_scope_campus  FOREIGN KEY (campus_id)  REFERENCES campus (id),
    ADD CONSTRAINT fk_sys_user_data_scope_building FOREIGN KEY (building_id) REFERENCES building (id),
    ADD CONSTRAINT fk_sys_user_data_scope_lab      FOREIGN KEY (lab_id)      REFERENCES lab (id);

-- ----------------------------------------------------------------------------
-- 5. space 模块权限初始数据（ID 101–112）
--    权限标识规范 `模块:资源:操作`，前后端一致。
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code) VALUES
(101, 'space:campus:list',   '校区查询', 'space'),
(102, 'space:campus:create', '校区新增', 'space'),
(103, 'space:campus:update', '校区修改', 'space'),
(104, 'space:campus:delete', '校区删除', 'space'),
(105, 'space:building:list',   '楼栋查询', 'space'),
(106, 'space:building:create', '楼栋新增', 'space'),
(107, 'space:building:update', '楼栋修改', 'space'),
(108, 'space:building:delete', '楼栋删除', 'space'),
(109, 'space:lab:list',   '实验室查询', 'space'),
(110, 'space:lab:create', '实验室新增', 'space'),
(111, 'space:lab:update', '实验室修改', 'space'),
(112, 'space:lab:delete', '实验室删除', 'space');

-- ----------------------------------------------------------------------------
-- 6. space 模块菜单初始数据（ID 121–125）
--    菜单类型存库值 DIRECTORY/PAGE/ACTION；route_path 与前端组件白名单
--    （frontend/src/router/dynamic.js viewModules）的 key 保持一致。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order) VALUES
(121, NULL, 'M_BASE_DATA',          '基础数据',   '/admin',       NULL,           NULL, 'DIRECTORY', 10),
(122, 121,  'C_SPACE_MANAGE',      '空间管理',   '/admin/space', '/admin/space', 101,  'PAGE',      1),
(123, 122,  'F_SPACE_CAMPUS_CREATE',   '校区新增',   NULL, NULL, 102, 'ACTION', 1),
(124, 122,  'F_SPACE_BUILDING_CREATE', '楼栋新增',   NULL, NULL, 106, 'ACTION', 2),
(125, 122,  'F_SPACE_LAB_CREATE',       '实验室新增', NULL, NULL, 110, 'ACTION', 3);

-- ----------------------------------------------------------------------------
-- 7. 角色权限绑定（sys_role_permission，ID 501–514）
--    ADMIN(1)：space 全量；TEACHER(2)/STUDENT(3)：仅 space:lab:list
--    （前台预约场景需要实验室下拉，后台管理页面不对学生/老师开放）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(501,  1, 101),
(502,  1, 102),
(503,  1, 103),
(504,  1, 104),
(505,  1, 105),
(506,  1, 106),
(507,  1, 107),
(508,  1, 108),
(509,  1, 109),
(510,  1, 110),
(511,  1, 111),
(512,  1, 112),
(513,  2, 109),
(514,  3, 109);

-- ----------------------------------------------------------------------------
-- 8. 角色菜单绑定（sys_role_menu，ID 601–605）
--    基础数据目录与空间管理页面仅 ADMIN(1) 可见。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(601, 1, 121),
(602, 1, 122),
(603, 1, 123),
(604, 1, 124),
(605, 1, 125);

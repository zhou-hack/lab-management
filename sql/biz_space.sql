-- ============================================================================
-- 实验室设备管理系统 · 场地模块建表脚本（实施分组 A · v1.1 单表版）
-- 依据 docs/场地模块开发文档清单.md v1.1、docs/数据库设计.md 4.2 节（单表修订）。
-- ----------------------------------------------------------------------------
-- 范围：场地单表 biz_space，以及场地模块权限（101-105）、菜单（1001-1005）
--   初始数据。一期不做场地图片表。
-- 取代：旧版四表脚本（campus/building/lab/lab_open_rule，sql/sys_space.sql），
--   四表已按业务确认整体废弃，本脚本为场地模块唯一实现。
-- ----------------------------------------------------------------------------
-- 关键约定：
--   1. 主键 BIGINT 雪花 ID（MyBatis-Plus ASSIGN_ID），接口按字符串输出；
--      初始数据使用固定小 ID（1/2/3），与雪花 ID 不冲突；
--   2. 表 InnoDB / utf8mb4_0900_ai_ci；
--   3. 时间点列 DATETIME(3) 存 UTC，默认 UTC_TIMESTAMP(3)；
--      open_start/open_end 为 TIME(0) 上海本地节次时间，不做时区转换；
--   4. 公共字段 id/create_time/update_time/deleted/create_user_id/update_user_id；
--      审计字段不建外键（避免组织/用户循环依赖）；
--   5. space_name 全局唯一；逻辑删除后名称不复用（不用 (业务键, deleted) 方案，
--      删除行仍占用唯一索引，服务层按含删除记录的查询前置校验转 40900）；
--   6. space_type/status 为 Java 枚举映射（SpaceType/SpaceStatus），
--      不用 MySQL ENUM，CHECK 约束双保险；status 落库（取代旧"派生不入库"方案）；
--   7. sys_user_data_scope.lab_id 跨模块引用在本脚本末尾补充外键；
--      campus_id/building_id 列按 RBAC 定稿保留，单表模式下无挂靠表，不加外键。
-- ----------------------------------------------------------------------------
-- 执行前提：先执行 sql/sys_rbac.sql（依赖 sys_permission/sys_menu/sys_role/
--   sys_role_permission/sys_role_menu/sys_user_data_scope，并预置角色 id=1 管理员、
--   id=2 教师、id=3 学生、管理员 id=1）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. biz_space 场地表（单表：教室/实训室空间主档）
-- ----------------------------------------------------------------------------
CREATE TABLE biz_space (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    space_name     VARCHAR(128) NOT NULL                COMMENT '场地名称（全局唯一，删除后不复用）',
    space_type     VARCHAR(32)  NOT NULL                COMMENT '类型：CLASSROOM教室/TRAINING_ROOM实训室',
    location       VARCHAR(128) NULL                    COMMENT '位置（自由文本，如 A 楼七层 A703）',
    capacity       INT          NOT NULL                COMMENT '容纳人数（>0）',
    facilities     TEXT         NULL                    COMMENT '配套设备清单（自由文本）',
    open_start     TIME(0)      NOT NULL DEFAULT '08:00:00' COMMENT '每日开放开始（上海本地时间）',
    open_end       TIME(0)      NOT NULL DEFAULT '21:00:00' COMMENT '每日开放结束（上海本地时间，须晚于 open_start）',
    status         VARCHAR(32)  NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE可用/MAINTAINING维护中/CLOSED关闭',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_space_name (space_name),
    KEY idx_biz_space_type (space_type),
    KEY idx_biz_space_status (status),
    CONSTRAINT chk_biz_space_type CHECK (space_type IN ('CLASSROOM', 'TRAINING_ROOM')),
    CONSTRAINT chk_biz_space_status CHECK (status IN ('AVAILABLE', 'MAINTAINING', 'CLOSED')),
    CONSTRAINT chk_biz_space_capacity CHECK (capacity > 0),
    CONSTRAINT chk_biz_space_open CHECK (open_start < open_end),
    CONSTRAINT chk_biz_space_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地表（教室/实训室空间主档，单表）';

-- ----------------------------------------------------------------------------
-- 2. 补充 RBAC 跨模块外键（sys_rbac.sql 仅建列与索引，外键由所属模块补充）
--    LAB 级数据范围目标在单表模式下为 biz_space.id；
--    campus_id/building_id 列保留（RBAC 定稿），无挂靠表不加外键。
-- ----------------------------------------------------------------------------
ALTER TABLE sys_user_data_scope
    ADD CONSTRAINT fk_sys_user_data_scope_lab
        FOREIGN KEY (lab_id) REFERENCES biz_space (id);

-- ============================================================================
-- 初始数据
-- ============================================================================

-- 场地：两间实训室 + 一间多媒体教室（固定小 ID 1-3）
INSERT INTO biz_space (id, space_name, space_type, location, capacity, facilities,
                       open_start, open_end, status, create_user_id, update_user_id)
VALUES
(1, 'A703 网络实训室', 'TRAINING_ROOM', 'A 楼七层 A703', 50,
    '网络综合布线实训环境：56 口配线架 12 组、路由交换设备 12 套、实训工位 50 个',
    '08:00', '21:00', 'AVAILABLE', 1, 1),
(2, 'C703 电子电工实训室', 'TRAINING_ROOM', 'C 楼七层 C703', 45,
    '示波器 20 台、直流稳压电源 20 台、电工电子实训台 12 位',
    '08:00', '21:00', 'AVAILABLE', 1, 1),
(3, '教学楼201多媒体教室', 'CLASSROOM', '教学楼二层 201', 60,
    '投影仪 1 台、音响系统 1 套、中控讲台 1 位',
    '08:00', '21:00', 'AVAILABLE', 1, 1);

-- ----------------------------------------------------------------------------
-- 权限初始数据（1001-1005，标识规范 模块:资源:操作，module_code='space'；
-- ID 段按 docs/RBAC实施清单.md 第一节：space 模块 1000-1999）
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
VALUES
(1001, 'space:space:list',   '场地列表',       'space', 1),
(1002, 'space:space:read',   '场地详情',       'space', 1),
(1003, 'space:space:create', '新建场地',       'space', 1),
(1004, 'space:space:update', '编辑/启停场地',  'space', 1),
(1005, 'space:space:delete', '删除场地',       'space', 1);

-- ----------------------------------------------------------------------------
-- 菜单初始数据（1001-1005，menu_type 取 DIRECTORY/PAGE/ACTION）
-- 1002 路由与组件沿用前端已落地白名单（/admin/space/labs → admin/space/LabList），
-- 页面标题仍为"场地管理"；真实菜单下发时无需调整前端映射。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key,
                      permission_id, menu_type, sort_order, enabled)
VALUES
(1001, NULL, 'space',        '空间管理', '/admin/space',      NULL,                    NULL, 'DIRECTORY', 10, 1),
(1002, 1001, 'space_labs',   '场地管理', '/admin/space/labs', 'admin/space/LabList',   NULL, 'PAGE',      1, 1),
(1003, 1002, 'space_create', '新建场地', NULL,                NULL,                    103,  'ACTION',    1, 1),
(1004, 1002, 'space_update', '编辑场地', NULL,                NULL,                    104,  'ACTION',    2, 1),
(1005, 1002, 'space_delete', '删除场地', NULL,                NULL,                    105,  'ACTION',    3, 1);

-- 管理员（role_id=1）拥有场地模块全部权限 1001-1005
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
VALUES
(1001, 1, 1001, 1), (1002, 1, 1002, 1), (1003, 1, 1003, 1), (1004, 1, 1004, 1), (1005, 1, 1005, 1);

-- 教师（role_id=2）/学生（role_id=3）只读：仅列表与详情
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
VALUES
(1006, 2, 1001, 1), (1007, 2, 1002, 1),
(1008, 3, 1001, 1), (1009, 3, 1002, 1);

-- 管理员可见场地模块全部菜单 1001-1005（师生经预约入口只读访问，无管理菜单）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
VALUES
(1001, 1, 1001, 1), (1002, 1, 1002, 1), (1003, 1, 1003, 1),
(1004, 1, 1004, 1), (1005, 1, 1005, 1);

-- 管理员数据范围：ALL + WRITE（WRITE 含 READ；无目标 ID，scope_target_id 归一生成列=0）
INSERT INTO sys_user_data_scope (id, user_id, scope_type, department_id, campus_id, building_id,
                                 lab_id, access_mode, enabled)
VALUES (1, 1, 'ALL', NULL, NULL, NULL, NULL, 'WRITE', 1);

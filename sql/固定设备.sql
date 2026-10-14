-- ============================================================================
-- 实验室设备管理系统 · 固定设备管理建表脚本（实施分组 C：设备模块）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.3 / 4.5 节。
-- ----------------------------------------------------------------------------
-- 内容：
--   1. equipment_category 多级设备分类树 / equipment_model 设备型号表；
--   2. equipment_asset 设备台账（空间层级末端：campus→building→lab→asset）；
--   3. equipment_location_history 位置变迁历史（追加型）；
--   4. equipment_booking 器材借用单（挂统一单据）/
--      equipment_usage 借出归还流水；
--   5. equipment 模块权限与菜单初始数据。
-- ----------------------------------------------------------------------------
-- 设计要点（评审稿 4.3 节文首约定）：
--   * 设备状态 IDLE/RESERVED/IN_USE/MAINTAINING/SCRAPPED，报废为最终态；
--     未来预约以预约时段为准，不能因一张未来预约就把设备永久标为
--     RESERVED 并拒绝其他不相交预约；
--   * 页面状态由实体物理状态与当前生效预约派生；
--     实验室禁用不自动报废室内设备；
--   * value_level 为空表示待分类，禁止借用（提交时服务端校验）；
--   * equipment_location_history / equipment_usage 为追加型历史，deleted 恒 0。
-- 借用审批链（本期按用户确认执行，与 PRD 普通设备免管理员审批有偏差）：
--   * 学生单：提交 PENDING_TEACHER（指定老师批）→ PENDING_ADMIN（管理员
--     审批发放）→ APPROVED → CHECKED_OUT → RETURNED；
--   * 老师单：提交 PENDING_ADMIN → APPROVED → CHECKED_OUT → RETURNED；
--   * 普通/贵重设备流程一致（value_level 仅作台账属性）。
-- 执行前提：
--   * MySQL 8.4.11 LTS，InnoDB / utf8mb4 / utf8mb4_0900_ai_ci；
--   * 必须先执行 sys_rbac.sql、空间.sql、document_base.sql
--     （依赖 sys_user / sys_department / sys_role / lab / biz_document /
--     sys_permission / sys_menu / sys_role_permission / sys_role_menu）；
--   * 服务端与连接会话时区均为 +00:00；
--   * 审计字段 create_user_id/update_user_id 统一不建外键；
--   * offering_id / lab_booking_id 为跨模块引用（course_offering / lab_booking
--     表属后续模块），只建列与索引，不建外键；uk(lab_booking_id, asset_id)
--     照建，MySQL 唯一索引允许多个 NULL，独立申请不冲突。
-- 种子 ID 分段约定：
--   * equipment 权限 141–161；equipment 菜单 161–170
--     （sys_permission 与 sys_menu 为不同表，ID 数值互不影响）；
--   * sys_role_permission 关联 515–535（仅 ADMIN）；sys_role_menu 关联 606–611；
--   * 536–548 / 612–619 曾用于 TEACHER/STUDENT 绑定，已收回（ID 不复用）；
--   * 本段不得与其他模块脚本（space 101–125/501–514/601–605）复用。
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. equipment_category 设备分类表（多级树）
--    分类码唯一；禁止组织循环由服务端校验（同 sys_department）。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_category (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    parent_id       BIGINT       NULL                    COMMENT '父分类 ID（NULL=根节点，禁止循环由服务端校验）',
    category_code   VARCHAR(64)  NOT NULL                COMMENT '分类编码（唯一，删除后不复用）',
    category_name   VARCHAR(128) NOT NULL                COMMENT '分类名称',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_category_code (category_code),
    KEY idx_equipment_category_parent (parent_id),
    CONSTRAINT fk_equipment_category_parent FOREIGN KEY (parent_id) REFERENCES equipment_category (id),
    CONSTRAINT chk_equipment_category_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_equipment_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备分类表（多级树，分类码唯一）';

-- ----------------------------------------------------------------------------
-- 2. equipment_model 设备型号表
--    型号码唯一；参考单价与每台实际采购价（equipment_asset.purchase_price）分开。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_model (
    id                    BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    category_id           BIGINT        NOT NULL                COMMENT '所属分类（→ equipment_category.id）',
    model_code            VARCHAR(64)   NOT NULL                COMMENT '型号码（唯一，删除后不复用）',
    equipment_name        VARCHAR(128)  NOT NULL                COMMENT '设备名称',
    brand                 VARCHAR(128)  NULL                    COMMENT '品牌',
    specification         VARCHAR(500)  NOT NULL                COMMENT '规格说明',
    unit_name             VARCHAR(16)   NOT NULL                COMMENT '计量单位（台/套等）',
    reference_price       DECIMAL(18,2) NULL                    COMMENT '参考单价（MONEY；实际采购价见 equipment_asset）',
    expected_life_months  INT           NULL                    COMMENT '预计寿命（月）',
    requires_data_erasure TINYINT       NOT NULL DEFAULT 0      COMMENT '归还须数据擦除：0否 1是',
    enabled               TINYINT       NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id        BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id        BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_model_code (model_code),
    KEY idx_equipment_model_category (category_id),
    CONSTRAINT fk_equipment_model_category FOREIGN KEY (category_id) REFERENCES equipment_category (id),
    CONSTRAINT chk_equipment_model_erasure CHECK (requires_data_erasure IN (0, 1)),
    CONSTRAINT chk_equipment_model_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_equipment_model_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备型号表（型号码唯一）';

-- ----------------------------------------------------------------------------
-- 3. equipment_asset 设备台账
--    资产编号唯一；每台/每套一行；序列号不强制全局唯一；
--    value_level 为空=待分类，禁止借用；状态枚举由服务层维护；
--    分类信息（classified_by/classified_at/classification_reason）记录
--    最近一次定级留痕。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_asset (
    id                   BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    model_id             BIGINT        NOT NULL                COMMENT '设备型号（→ equipment_model.id）',
    asset_code           VARCHAR(64)   NOT NULL                COMMENT '资产编号（唯一，删除后不复用）',
    serial_no            VARCHAR(128)  NULL                    COMMENT '序列号（不强制全局唯一）',
    lab_id               BIGINT        NOT NULL                COMMENT '保管实验室（→ lab.id；空间层级末端）',
    department_id        BIGINT        NOT NULL                COMMENT '归口部门（→ sys_department.id）',
    location_detail      VARCHAR(255)  NULL                    COMMENT '室内存放位置说明',
    custodian_id         BIGINT        NULL                    COMMENT '保管人（→ sys_user.id）',
    purchase_date        DATE          NULL                    COMMENT '采购日期（纯日期，不做时区转换）',
    purchase_price       DECIMAL(18,2) NULL                    COMMENT '采购单价（MONEY）',
    warranty_until       DATE          NULL                    COMMENT '保修截止（纯日期）',
    value_level          VARCHAR(32)   NULL                    COMMENT '价值分级：NORMAL/VALUABLE（NULL=待分类，禁止借用）',
    classification_reason TEXT         NULL                    COMMENT '定级理由',
    classified_by        BIGINT        NULL                    COMMENT '定级人（→ sys_user.id）',
    classified_at        DATETIME(3)   NULL                    COMMENT '定级时间（UTC）',
    status               VARCHAR(32)   NOT NULL                COMMENT '设备状态：IDLE/RESERVED/IN_USE/MAINTAINING/SCRAPPED（报废为最终态）',
    enabled              TINYINT       NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time          DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time          DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted              TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id       BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id       BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_asset_code (asset_code),
    KEY idx_equipment_asset_lab (lab_id, status, enabled),
    KEY idx_equipment_asset_department (department_id, status),
    KEY idx_equipment_asset_model (model_id),
    CONSTRAINT fk_equipment_asset_model FOREIGN KEY (model_id) REFERENCES equipment_model (id),
    CONSTRAINT fk_equipment_asset_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT fk_equipment_asset_department FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT fk_equipment_asset_custodian FOREIGN KEY (custodian_id) REFERENCES sys_user (id),
    CONSTRAINT fk_equipment_asset_classified_by FOREIGN KEY (classified_by) REFERENCES sys_user (id),
    CONSTRAINT chk_equipment_asset_value_level CHECK (value_level IN ('NORMAL', 'VALUABLE')),
    CONSTRAINT chk_equipment_asset_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_equipment_asset_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备台账表（资产编号唯一，未分类禁止借用）';

-- ----------------------------------------------------------------------------
-- 4. equipment_location_history 位置变迁历史（追加型）
--    仅允许空闲且无冲突业务的资产迁移；当前台账与历史同事务更新；
--    from_lab_id 可空（首装）；deleted 恒 0，不逻辑删除。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_location_history (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    asset_id        BIGINT       NOT NULL                COMMENT '设备（→ equipment_asset.id）',
    from_lab_id     BIGINT       NULL                    COMMENT '原实验室（→ lab.id；NULL=首装）',
    to_lab_id       BIGINT       NOT NULL                COMMENT '新实验室（→ lab.id）',
    from_location   VARCHAR(255) NULL                    COMMENT '原室内位置说明',
    to_location     VARCHAR(255) NULL                    COMMENT '新室内位置说明',
    moved_at        DATETIME(3)  NOT NULL                COMMENT '迁移时间（UTC）',
    operator_id     BIGINT       NOT NULL                COMMENT '经办人（→ sys_user.id）',
    reason          TEXT         NULL                    COMMENT '迁移原因',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_equipment_location_history_asset (asset_id, moved_at),
    CONSTRAINT fk_equipment_location_history_asset FOREIGN KEY (asset_id) REFERENCES equipment_asset (id),
    CONSTRAINT fk_equipment_location_history_from_lab FOREIGN KEY (from_lab_id) REFERENCES lab (id),
    CONSTRAINT fk_equipment_location_history_to_lab FOREIGN KEY (to_lab_id) REFERENCES lab (id),
    CONSTRAINT fk_equipment_location_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_equipment_location_history_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备位置变迁历史表（追加型）';

-- ----------------------------------------------------------------------------
-- 5. equipment_booking 器材借用单
--    document_id 唯一，单据关联一台实物；分类在提交时快照；
--    冲突判定用半开区间 [start_at, end_at)，有效冲突状态 APPROVED/CHECKED_OUT；
--    uk(lab_booking_id, asset_id) 照建（MySQL 唯一索引允许多个 NULL，
--    独立申请不冲突）；
--    状态：PENDING_TEACHER/PENDING_ADMIN/APPROVED/CHECKED_OUT/RETURNED/
--          REJECTED/CANCELLED。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_booking (
    id                   BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id          BIGINT       NOT NULL                COMMENT '统一单据（→ biz_document.id，唯一）',
    asset_id             BIGINT       NOT NULL                COMMENT '借用设备（→ equipment_asset.id）',
    applicant_id         BIGINT       NOT NULL                COMMENT '申请人（→ sys_user.id）',
    approval_teacher_id  BIGINT       NULL                    COMMENT '指定审批老师（→ sys_user.id；学生单必填，老师单为 NULL）',
    offering_id          BIGINT       NULL                    COMMENT '关联开课实例（→ course_offering.id，后续模块，暂不建外键）',
    lab_booking_id       BIGINT       NULL                    COMMENT '关联场地预约（→ lab_booking.id，后续模块，暂不建外键）',
    purpose              TEXT         NOT NULL                COMMENT '借用用途',
    start_at             DATETIME(3)  NOT NULL                COMMENT '借用开始时间（UTC）',
    end_at               DATETIME(3)  NOT NULL                COMMENT '借用结束时间（UTC，须晚于开始时间）',
    value_level_snapshot VARCHAR(32)  NOT NULL                COMMENT '提交时设备价值分级快照：NORMAL/VALUABLE',
    status               VARCHAR(32)  NOT NULL                COMMENT '借用状态：PENDING_TEACHER/PENDING_ADMIN/APPROVED/CHECKED_OUT/RETURNED/REJECTED/CANCELLED',
    cancel_reason        TEXT         NULL                    COMMENT '撤销原因',
    cancelled_at         DATETIME(3)  NULL                    COMMENT '撤销时间（UTC）',
    create_time          DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time          DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted              TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（单据历史，不逻辑删除）',
    create_user_id       BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id       BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_booking_document (document_id),
    UNIQUE KEY uk_equipment_booking_lab_asset (lab_booking_id, asset_id),
    KEY idx_equipment_booking_asset (asset_id, status, start_at, end_at),
    KEY idx_equipment_booking_applicant (applicant_id, create_time),
    KEY idx_equipment_booking_teacher (approval_teacher_id, status),
    KEY idx_equipment_booking_offering (offering_id),
    CONSTRAINT fk_equipment_booking_document FOREIGN KEY (document_id) REFERENCES biz_document (id),
    CONSTRAINT fk_equipment_booking_asset FOREIGN KEY (asset_id) REFERENCES equipment_asset (id),
    CONSTRAINT fk_equipment_booking_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id),
    CONSTRAINT fk_equipment_booking_teacher FOREIGN KEY (approval_teacher_id) REFERENCES sys_user (id),
    CONSTRAINT chk_equipment_booking_period CHECK (start_at < end_at),
    CONSTRAINT chk_equipment_booking_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='器材借用单表（document_id 唯一，一单对一台设备）';

-- ----------------------------------------------------------------------------
-- 6. equipment_usage 借出归还流水
--    booking_id 唯一；CHECKED_OUT/RETURNED；
--    代借代还保存实际操作者；归还条件 NORMAL/DAMAGED（DAMAGED 必填描述，
--    触发设备转 MAINTAINING）；deleted 恒 0。
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_usage (
    id                    BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    booking_id            BIGINT       NOT NULL                COMMENT '借用单（→ equipment_booking.id，唯一）',
    borrower_id           BIGINT       NOT NULL                COMMENT '借用人（→ sys_user.id）',
    checkout_operator_id  BIGINT       NOT NULL                COMMENT '借出经办人（→ sys_user.id；代借保存实际操作者）',
    checked_out_at        DATETIME(3)  NOT NULL                COMMENT '借出时间（UTC）',
    due_at                DATETIME(3)  NOT NULL                COMMENT '应还时间（UTC）',
    return_operator_id    BIGINT       NULL                    COMMENT '归还经办人（→ sys_user.id；未还为 NULL）',
    returned_at           DATETIME(3)  NULL                    COMMENT '归还时间（UTC；未还为 NULL）',
    return_condition      VARCHAR(32)  NULL                    COMMENT '归还状态：NORMAL/DAMAGED（DAMAGED 须填 return_note）',
    checkout_note         TEXT         NULL                    COMMENT '借出备注',
    return_note           TEXT         NULL                    COMMENT '归还备注（归还异常/DAMAGED 时必填）',
    status                VARCHAR(32)  NOT NULL                COMMENT '流水状态：CHECKED_OUT/RETURNED',
    create_time           DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time           DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（流水历史，不逻辑删除）',
    create_user_id        BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id        BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_usage_booking (booking_id),
    KEY idx_equipment_usage_status_due (status, due_at),
    KEY idx_equipment_usage_borrower (borrower_id, status),
    CONSTRAINT fk_equipment_usage_booking FOREIGN KEY (booking_id) REFERENCES equipment_booking (id),
    CONSTRAINT fk_equipment_usage_borrower FOREIGN KEY (borrower_id) REFERENCES sys_user (id),
    CONSTRAINT fk_equipment_usage_checkout_operator FOREIGN KEY (checkout_operator_id) REFERENCES sys_user (id),
    CONSTRAINT fk_equipment_usage_return_operator FOREIGN KEY (return_operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_equipment_usage_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='器材借出归还流水表（booking_id 唯一）';

-- ============================================================================
-- 初始数据
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 7. equipment 模块权限（ID 141–161，module_code='equipment'）
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code) VALUES
(141, 'equipment:category:list',          '设备分类查询', 'equipment'),
(142, 'equipment:category:create',        '设备分类新增', 'equipment'),
(143, 'equipment:category:update',        '设备分类修改', 'equipment'),
(144, 'equipment:category:delete',        '设备分类删除', 'equipment'),
(145, 'equipment:model:list',             '设备型号查询', 'equipment'),
(146, 'equipment:model:create',           '设备型号新增', 'equipment'),
(147, 'equipment:model:update',           '设备型号修改', 'equipment'),
(148, 'equipment:model:delete',           '设备型号删除', 'equipment'),
(149, 'equipment:asset:list',            '设备台账查询', 'equipment'),
(150, 'equipment:asset:create',          '设备台账新增', 'equipment'),
(151, 'equipment:asset:update',          '设备台账修改', 'equipment'),
(152, 'equipment:asset:delete',          '设备台账删除', 'equipment'),
(153, 'equipment:asset:transfer',        '设备迁移登记', 'equipment'),
(154, 'equipment:asset:classify',        '设备价值定级', 'equipment'),
(155, 'equipment:booking:create',         '借用单提交',   'equipment'),
(156, 'equipment:booking:cancel',         '借用单撤销',   'equipment'),
(157, 'equipment:booking:list',           '借用单查询',   'equipment'),
(158, 'equipment:booking:teacher_approve', '借用单老师审批', 'equipment'),
(159, 'equipment:booking:admin_approve',   '借用单管理员审批', 'equipment'),
(160, 'equipment:booking:checkout',        '借用确认借出', 'equipment'),
(161, 'equipment:booking:return',          '借用确认归还', 'equipment');

-- ----------------------------------------------------------------------------
-- 8. equipment 模块菜单（ID 161–170）
--    route_path 与前端组件白名单（frontend/src/router/dynamic.js viewModules）
--    的 key 完全一致；后台页面挂 /admin（AdminLayout），前台页面挂 /equipment
--    （FrontLayout，DEV 假登录演示用）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order) VALUES
(161, NULL, 'M_EQUIPMENT_ADMIN',   '设备管理',   '/admin',                    NULL,                         NULL, 'DIRECTORY', 20),
(162, 161,  'C_EQUIPMENT_CATEGORY', '分类管理', '/admin/equipment/category', '/admin/equipment/category', 141,  'PAGE',      1),
(163, 161,  'C_EQUIPMENT_MODEL',    '型号管理', '/admin/equipment/model',    '/admin/equipment/model',    145,  'PAGE',      2),
(164, 161,  'C_EQUIPMENT_ASSET',    '设备台账', '/admin/equipment/asset',    '/admin/equipment/asset',    149,  'PAGE',      3),
(165, 161,  'C_EQUIPMENT_BOOKING',  '借用管理', '/admin/equipment/booking',  '/admin/equipment/booking',  157,  'PAGE',      4),
(166, NULL, 'M_EQUIPMENT_FRONT',   '设备服务', '/equipment',                NULL,                         NULL, 'DIRECTORY', 10),
(167, 166,  'C_BOOKING_CREATE',    '发起借用', '/equipment/booking/create', '/equipment/booking/create', 155,  'PAGE',      1),
(168, 166,  'C_MY_BOOKINGS',       '我的借用', '/equipment/bookings/mine',  '/equipment/bookings/mine',  157,  'PAGE',      2),
(169, 166,  'C_TEACHER_APPROVALS', '借用审批', '/equipment/approvals',       '/equipment/approvals',      158,  'PAGE',      3),
(170, 164,  'F_EQUIPMENT_ASSET_CREATE', '设备登记', NULL, NULL, 150, 'ACTION', 1);

-- ----------------------------------------------------------------------------
-- 9. 角色权限绑定（sys_role_permission，ID 515–535）
--    当前阶段仅 ADMIN(1) 开放设备模块全量（分类 4 + 型号 4 + 台账 6 + 借用 7 = 21 项）。
--    TEACHER(2)/STUDENT(3) 不绑定任何设备权限（收权决策 2026-10-14：设备模块
--    仅管理员可用）；审批链业务代码保留，后续开放时重新登记绑定即可。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(515, 1, 141),
(516, 1, 142),
(517, 1, 143),
(518, 1, 144),
(519, 1, 145),
(520, 1, 146),
(521, 1, 147),
(522, 1, 148),
(523, 1, 149),
(524, 1, 150),
(525, 1, 151),
(526, 1, 152),
(527, 1, 153),
(528, 1, 154),
(529, 1, 155),
(530, 1, 156),
(531, 1, 157),
(532, 1, 158),
(533, 1, 159),
(534, 1, 160),
(535, 1, 161);

-- ----------------------------------------------------------------------------
-- 10. 角色菜单绑定（sys_role_menu，ID 606–611）
--     仅 ADMIN(1)：设备管理目录 + 后台 4 页 + 设备登记动作。
--     前台设备菜单定义（M_EQUIPMENT_FRONT 166 及子页）保留不绑定，
--     管理员经菜单树外的前台路由使用借用功能。
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(606, 1, 161),
(607, 1, 162),
(608, 1, 163),
(609, 1, 164),
(610, 1, 165),
(611, 1, 170);

-- ============================================================================
-- 实验室设备管理系统 · 各模块数据库整合脚本（除 sys_rbac.sql 、sys_policy.sql 、assistant_grant.sql 外的全部建表）
-- ============================================================================
-- 执行前提（顺序固定）：
--   1. sys_rbac.sql（RBAC 11 表 + 部门/角色/管理员种子）
--   2. sys_policy.sql（business_policy 业务策略参数表，组长文件）
--   3. assistant_grant.sql（assistant_grant / assistant_grant_scope，组长文件）
--   4. 本文件（按模块顺序：场地 → 单据审批 → 设备 → 物料）
-- 环境约定：
--   * MySQL 8.4.11 LTS，InnoDB / utf8mb4 / utf8mb4_0900_ai_ci；
--   * 服务端与连接会话时区均为 +00:00，时间列 DATETIME(3) 存 UTC，
--     默认值 UTC_TIMESTAMP(3)；会话 +00:00 时 ON UPDATE CURRENT_TIMESTAMP(3) 即 UTC；
--   * 审计字段 create_user_id/update_user_id 统一不建外键；
--   * 跨模块引用只建列与索引，外键由所属模块脚本补充；
--   * 命名依据 docs/数据库设计.md 评审稿；已统一：场地表=lab、数量列=*_qty、
--     计量单位=base_unit、位置文本=location_detail。
--
-- ----------------------------------------------------------------------------
-- 【新增数据表规则】（组员以后加表必须遵守，直接写进本文件对应模块分区）
-- ----------------------------------------------------------------------------
-- 1. 落位：新表写到所属模块分区内；新模块在文件末尾新起一个分区，分区编号顺延。
-- 2. 命名：表名、列名一律小写下划线；禁止拼音/驼峰。
--    相同含义必须复用既定名，不得另造新名：
--    场地→lab、数量→*_qty、计量单位→base_unit、位置说明→location_detail。
-- 3. 公共字段：每张表必须包含
--    id / create_time / update_time / deleted / create_user_id / update_user_id
--    （写法照抄本文件既有表；追加型历史表 deleted 恒 0，注释写明）。
-- 4. 类型记号：CODE=VARCHAR(64)、NAME=VARCHAR(128)、SHORT=VARCHAR(32)
--    （Java 枚举映射，不用 MySQL ENUM）、BOOL=TINYINT（0/1 + CHECK）、
--    QTY=DECIMAL(18,3)、MONEY=DECIMAL(18,2)、PRICE=DECIMAL(18,4)、
--    DATE=DATE、CLOCK=TIME(0)。
-- 5. 主键：BIGINT 雪花 ID（MyBatis-Plus ASSIGN_ID），接口按字符串输出；
--    初始数据可用固定小 ID，与雪花 ID 不冲突。
-- 6. 约束命名前缀：uk_ / idx_ / fk_ / chk_，后缀含表名，全局不重名；
--    外键默认 RESTRICT，不级联删除历史。
-- 7. 含可空列的组合唯一：用生成列归一
--    （参照 stock_location.lab_key、stocktake.active_key 模式）。
-- 8. 种子数据编号段（防冲突，勿占用他人段；新段先向数据库组长登记再使用）：
--      权限     sys_permission：      item 101-137，equipment 141-154，space 155-159；
--      菜单     sys_menu：            item 151-199，equipment 200-204，space 1001-1005；
--      权限绑定 sys_role_permission： item 201-237，space 251-259，equipment 515-528；
--      菜单绑定 sys_role_menu：       item 301-349，equipment 606-610，space 1101-1105。
--    权限码规范 `模块:资源:操作`（如 item:material:create）；
--    module_code 用模块代号；菜单 ACTION 层 menu_code 即权限码。
-- 9. 禁止：改他人分区的表结构/编号；DROP 既有表；改写已登记的历史种子。
--    变更先在模块分区头注释里登记，并同步设计文档。
-- ============================================================================
SET NAMES utf8mb4;

-- ############################################################################
-- 模块一：场地模块（实施分组 A · 单表版）
--   表：lab；并为 sys_user_data_scope、assistant_grant_scope 补充跨模块外键。
--   种子：场地 3 条（id 1-3）；权限 155-159；菜单 1001-1005；
--         权限绑定 251-259；菜单绑定 1101-1105。
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 1.1 lab 场地表（单表：教室/实训室空间主档）
-- ----------------------------------------------------------------------------
CREATE TABLE lab (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_name     VARCHAR(128) NOT NULL                COMMENT '场地名称（全局唯一，删除后不复用）',
    lab_type     VARCHAR(32)  NOT NULL                COMMENT '类型：CLASSROOM教室/TRAINING_ROOM实训室',
    location_detail VARCHAR(128) NULL                   COMMENT '位置（自由文本，如 A 楼七层 A703）',
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
    UNIQUE KEY uk_lab_name (lab_name),
    KEY idx_lab_type (lab_type),
    KEY idx_lab_status (status),
    CONSTRAINT chk_lab_type CHECK (lab_type IN ('CLASSROOM', 'TRAINING_ROOM')),
    CONSTRAINT chk_lab_status CHECK (status IN ('AVAILABLE', 'MAINTAINING', 'CLOSED')),
    CONSTRAINT chk_lab_capacity CHECK (capacity > 0),
    CONSTRAINT chk_lab_open CHECK (open_start < open_end),
    CONSTRAINT chk_lab_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地表（教室/实训室空间主档，单表）';

-- ----------------------------------------------------------------------------
-- 1.2 补充 RBAC 跨模块外键（sys_rbac.sql 仅建列与索引，外键由所属模块补充）
-- ----------------------------------------------------------------------------
ALTER TABLE sys_user_data_scope
    ADD CONSTRAINT fk_sys_user_data_scope_lab
        FOREIGN KEY (lab_id) REFERENCES lab (id);

-- 补全 assistant_grant_scope.lab_id 跨模块外键
-- （assistant_grant.sql 约定：空间模块补充）
ALTER TABLE assistant_grant_scope
    ADD CONSTRAINT fk_assistant_grant_scope_lab
        FOREIGN KEY (lab_id) REFERENCES lab (id);

-- ----------------------------------------------------------------------------
-- 1.3 场地种子数据（固定小 ID 1-3，与雪花 ID 不冲突）
-- ----------------------------------------------------------------------------
INSERT INTO lab (id, lab_name, lab_type, location_detail, capacity, facilities,
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
-- 1.4 场地权限（155-159，标识规范 模块:资源:操作，module_code='space'）
--     原编号 101-105 与物料模块 101-121 冲突，整合时移至 155-159。
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
VALUES
(155, 'space:space:list',   '场地列表',       'space', 1),
(156, 'space:space:read',   '场地详情',       'space', 1),
(157, 'space:space:create', '新建场地',       'space', 1),
(158, 'space:space:update', '编辑/启停场地',  'space', 1),
(159, 'space:space:delete', '删除场地',       'space', 1);

-- ----------------------------------------------------------------------------
-- 1.5 场地菜单（1001-1005，menu_type 取 DIRECTORY/PAGE/ACTION）
--     1002 路由与组件沿用前端已落地白名单（/admin/space/labs → admin/space/LabList）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key,
                      permission_id, menu_type, sort_order, enabled)
VALUES
(1001, NULL, 'space',        '空间管理', '/admin/space',      NULL,                    NULL, 'DIRECTORY', 10, 1),
(1002, 1001, 'space_labs',   '场地管理', '/admin/space/labs', 'admin/space/LabList',   NULL, 'PAGE',      1, 1),
(1003, 1002, 'space_create', '新建场地', NULL,                NULL,                    157,  'ACTION',    1, 1),
(1004, 1002, 'space_update', '编辑场地', NULL,                NULL,                    158,  'ACTION',    2, 1),
(1005, 1002, 'space_delete', '删除场地', NULL,                NULL,                    159,  'ACTION',    3, 1);

-- 管理员（role_id=1）拥有场地模块全部权限 155-159
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
VALUES
(251, 1, 155, 1), (252, 1, 156, 1), (253, 1, 157, 1), (254, 1, 158, 1), (255, 1, 159, 1);

-- 教师（role_id=2）/学生（role_id=3）只读：仅列表与详情
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
VALUES
(256, 2, 155, 1), (257, 2, 156, 1),
(258, 3, 155, 1), (259, 3, 156, 1);

-- 管理员可见场地模块全部菜单 1001-1005（师生经预约入口只读访问，无管理菜单）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
VALUES
(1101, 1, 1001, 1), (1102, 1, 1002, 1), (1103, 1, 1003, 1),
(1104, 1, 1004, 1), (1105, 1, 1005, 1);

-- 管理员数据范围：ALL + WRITE（WRITE 含 READ；无目标 ID，scope_target_id 归一生成列=0）
INSERT INTO sys_user_data_scope (id, user_id, scope_type, department_id, campus_id, building_id,
                                 lab_id, access_mode, enabled)
VALUES (1, 1, 'ALL', NULL, NULL, NULL, NULL, 'WRITE', 1);

-- ############################################################################
-- 模块二：统一单据与审批（实施分组 B）
--   表：biz_document / approval_task / document_event；
--   并为 user_booking_restriction 补充单据外键。内部域，无权限与菜单种子。
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 2.1 biz_document 统一单据表
--     来源人归属（owner_user_id）与经办人分开；幂等键由客户端提供，
--     不传时服务端生成 UUID，撞唯一键时返回原单。
-- ----------------------------------------------------------------------------
CREATE TABLE biz_document (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_no     VARCHAR(64)  NOT NULL                COMMENT '单据编号（前缀+雪花，唯一）',
    document_type   VARCHAR(32)  NOT NULL                COMMENT '单据类型：EQUIPMENT_BOOKING（后续扩展 LAB_BOOKING 等）',
    owner_user_id   BIGINT       NOT NULL                COMMENT '归属用户（→ sys_user.id）',
    department_id   BIGINT       NOT NULL                COMMENT '归属部门（→ sys_department.id）',
    policy_id       BIGINT       NULL                    COMMENT '审批策略（→ business_policy.id；外键在 2.4 补充）',
    submitted_at    DATETIME(3)  NULL                    COMMENT '提交时间（UTC）',
    idempotency_key VARCHAR(128) NOT NULL                COMMENT '幂等键（客户端提供，缺省服务端生成 UUID）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_document_no (document_no),
    UNIQUE KEY uk_biz_document_idem (owner_user_id, document_type, idempotency_key),
    KEY idx_biz_document_policy (policy_id),
    KEY idx_biz_document_owner (owner_user_id, document_type),
    KEY idx_biz_document_department (department_id),
    CONSTRAINT fk_biz_document_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_biz_document_department FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_biz_document_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一单据表（所有审批型业务共用）';

-- ----------------------------------------------------------------------------
-- 2.2 approval_task 审批阶段待办表
--     每个审批阶段一行；上一阶段通过后服务端将下一阶段 WAITING→READY；
--     timeout_action 本期固定 'NONE'。
-- ----------------------------------------------------------------------------
CREATE TABLE approval_task (
    id                     BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id            BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    stage_code             VARCHAR(64)  NOT NULL                COMMENT '审批阶段：TEACHER_REVIEW/ADMIN_REVIEW',
    sequence_no            INT          NOT NULL                COMMENT '阶段顺序号（从 1 起）',
    round_no               INT          NOT NULL                COMMENT '审批轮次（默认 1，驳回重报递增）',
    required_role          VARCHAR(64)  NOT NULL                COMMENT '要求角色码（→ sys_role.role_code）',
    assigned_user_id       BIGINT       NULL                    COMMENT '指定审批人（→ sys_user.id；NULL=按角色+范围匹配）',
    assigned_department_id BIGINT       NULL                    COMMENT '指定审批部门（→ sys_department.id）',
    status                 VARCHAR(32)  NOT NULL                COMMENT '任务状态：WAITING/READY/APPROVED/REJECTED/CANCELLED',
    available_at           DATETIME(3)  NOT NULL                COMMENT '任务生效时间（UTC，READY 时可操作）',
    remind_at              DATETIME(3)  NULL                    COMMENT '催办时间（UTC）',
    escalate_at            DATETIME(3)  NULL                    COMMENT '升级时间（UTC）',
    timeout_at             DATETIME(3)  NULL                    COMMENT '超时时间（UTC）',
    timeout_action         VARCHAR(32)  NOT NULL                COMMENT '超时动作：本期固定 NONE',
    decided_by             BIGINT       NULL                    COMMENT '实际审批人（→ sys_user.id；代批时与 assigned 不同）',
    decided_at             DATETIME(3)  NULL                    COMMENT '审批时间（UTC）',
    decision_comment       TEXT         NULL                    COMMENT '审批意见（驳回必填）',
    create_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted                TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id         BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id         BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_task (document_id, stage_code, sequence_no, round_no),
    KEY idx_approval_task_assignee (assigned_user_id, status, available_at),
    KEY idx_approval_task_dept_role (assigned_department_id, required_role, status),
    KEY idx_approval_task_status_timeout (status, timeout_at),
    KEY idx_approval_task_document (document_id),
    CONSTRAINT fk_approval_task_document FOREIGN KEY (document_id) REFERENCES biz_document (id),
    CONSTRAINT fk_approval_task_role FOREIGN KEY (required_role) REFERENCES sys_role (role_code),
    CONSTRAINT fk_approval_task_assigned_user FOREIGN KEY (assigned_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_approval_task_assigned_dept FOREIGN KEY (assigned_department_id) REFERENCES sys_department (id),
    CONSTRAINT fk_approval_task_decided_by FOREIGN KEY (decided_by) REFERENCES sys_user (id),
    CONSTRAINT chk_approval_task_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批阶段待办表（阶段流转）';

-- ----------------------------------------------------------------------------
-- 2.3 document_event 单据事件表（不可变过程历史）
-- ----------------------------------------------------------------------------
CREATE TABLE document_event (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id     BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    event_type      VARCHAR(32)  NOT NULL                COMMENT '事件类型：SUBMIT/APPROVE/REJECT/CANCEL/CHECKOUT/RETURN/... ',
    from_status     VARCHAR(32)  NULL                    COMMENT '变更前业务状态',
    to_status       VARCHAR(32)  NULL                    COMMENT '变更后业务状态',
    actor_id        BIGINT       NULL                    COMMENT '操作人（→ sys_user.id；系统事件为 NULL）',
    actor_type      VARCHAR(32)  NOT NULL                COMMENT '操作人类型：USER/SYSTEM',
    occurred_at     DATETIME(3)  NOT NULL                COMMENT '发生时间（UTC）',
    reason          TEXT         NULL                    COMMENT '事由/备注',
    task_id         BIGINT       NULL                    COMMENT '关联审批任务（→ approval_task.id）',
    request_key     VARCHAR(128) NOT NULL                COMMENT '请求键（单次操作唯一，防事件重复写入）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_event (document_id, request_key),
    KEY idx_document_event_type (document_id, event_type, occurred_at),
    CONSTRAINT fk_document_event_document FOREIGN KEY (document_id) REFERENCES biz_document (id),
    CONSTRAINT fk_document_event_actor FOREIGN KEY (actor_id) REFERENCES sys_user (id),
    CONSTRAINT fk_document_event_task FOREIGN KEY (task_id) REFERENCES approval_task (id),
    CONSTRAINT chk_document_event_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据事件表（不可变过程历史）';

-- ----------------------------------------------------------------------------
-- 2.4 为 user_booking_restriction 补充单据外键
--     sys_rbac.sql 建表时 source_document_id 只建列与索引，约定由单据脚本补充。
-- ----------------------------------------------------------------------------
ALTER TABLE user_booking_restriction
    ADD CONSTRAINT fk_user_booking_restriction_document
        FOREIGN KEY (source_document_id) REFERENCES biz_document (id);

-- 补全 biz_document.policy_id 跨模块外键（目标表见 sys_policy.sql）
ALTER TABLE biz_document
    ADD CONSTRAINT fk_biz_document_policy
        FOREIGN KEY (policy_id) REFERENCES business_policy (id);

-- ############################################################################
-- 模块三：固定设备模块（实施分组 C）
--   表：equipment_category / equipment_model / equipment_asset /
--       equipment_location_history / equipment_booking / equipment_usage。
--   产品定稿（2026-10-14）：纯管理员设备管理，无设备借用申请。
--   → 借用两张表结构保留（历史与后续开放预留），但不登记借用权限/菜单/绑定。
--   种子：权限 141-154（分类/型号/台账）；菜单 200-204；
--         权限绑定 515-528；菜单绑定 606-610。
--   注：菜单原编号 161-170 与物料 151-179 冲突，整合时移至 200-204 段；
--       借用权限（旧 155-161）与借用菜单 165-169/173 按产品定稿不再登记。
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 3.1 equipment_category 设备分类表（多级树）
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
-- 3.2 equipment_model 设备型号表
-- ----------------------------------------------------------------------------
CREATE TABLE equipment_model (
    id                    BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    category_id           BIGINT        NOT NULL                COMMENT '所属分类（→ equipment_category.id）',
    model_code            VARCHAR(64)   NOT NULL                COMMENT '型号码（唯一，删除后不复用）',
    equipment_name        VARCHAR(128)  NOT NULL                COMMENT '设备名称',
    brand                 VARCHAR(128)  NULL                    COMMENT '品牌',
    specification         VARCHAR(500)  NOT NULL                COMMENT '规格说明',
    base_unit             VARCHAR(16)   NOT NULL                COMMENT '计量单位（台/套等）',
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
-- 3.3 equipment_asset 设备台账
--     资产编号唯一；每台/每套一行；value_level 为空=待分类。
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
-- 3.4 equipment_location_history 位置变迁历史（追加型，deleted 恒 0）
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
-- 3.5 equipment_booking 器材借用单（结构保留；产品定稿暂不开放借用，
--     不登记借用权限/菜单；后续开放时重新登记即可）
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
-- 3.6 equipment_usage 借出归还流水（结构保留，理由同 3.5）
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

-- ----------------------------------------------------------------------------
-- 3.7 equipment 权限（141-154；分类 4 + 型号 4 + 台账 6）
--     借用权限按产品定稿（2026-10-14）不登记。
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code) VALUES
(141, 'equipment:category:list',   '设备分类查询', 'equipment'),
(142, 'equipment:category:create', '设备分类新增', 'equipment'),
(143, 'equipment:category:update', '设备分类修改', 'equipment'),
(144, 'equipment:category:delete', '设备分类删除', 'equipment'),
(145, 'equipment:model:list',      '设备型号查询', 'equipment'),
(146, 'equipment:model:create',    '设备型号新增', 'equipment'),
(147, 'equipment:model:update',    '设备型号修改', 'equipment'),
(148, 'equipment:model:delete',    '设备型号删除', 'equipment'),
(149, 'equipment:asset:list',      '设备台账查询', 'equipment'),
(150, 'equipment:asset:create',    '设备台账新增', 'equipment'),
(151, 'equipment:asset:update',    '设备台账修改', 'equipment'),
(152, 'equipment:asset:delete',    '设备台账删除', 'equipment'),
(153, 'equipment:asset:transfer',  '设备迁移登记', 'equipment'),
(154, 'equipment:asset:classify',  '设备价值定级', 'equipment');

-- ----------------------------------------------------------------------------
-- 3.8 equipment 菜单（200-204；原 161-170 与物料 151-179 冲突，移至 200 段；
--     借用菜单按产品定稿不登记）
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order) VALUES
(200, NULL, 'M_EQUIPMENT_ADMIN',    '设备管理', '/admin',                    NULL,                         NULL, 'DIRECTORY', 20),
(201, 200,  'C_EQUIPMENT_CATEGORY', '分类管理', '/admin/equipment/category', '/admin/equipment/category', 141,  'PAGE',      1),
(202, 200,  'C_EQUIPMENT_MODEL',    '型号管理', '/admin/equipment/model',    '/admin/equipment/model',    145,  'PAGE',      2),
(203, 200,  'C_EQUIPMENT_ASSET',    '设备台账', '/admin/equipment/asset',    '/admin/equipment/asset',    149,  'PAGE',      3),
(204, 203,  'F_EQUIPMENT_ASSET_CREATE', '设备登记', NULL, NULL, 150, 'ACTION', 1);

-- ----------------------------------------------------------------------------
-- 3.9 角色绑定：仅 ADMIN(1) 开放设备模块（14 项权限；老师/学生不绑定）
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
(528, 1, 154);

INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(606, 1, 200),
(607, 1, 201),
(608, 1, 202),
(609, 1, 203),
(610, 1, 204);

-- ############################################################################
-- 模块四：物料管理模块 · 第一阶段（实施分组 C）
--   表：material_category / material_sku / stock_location / stock_balance /
--       stock_document / stock_document_line / stock_movement。
--   种子：权限 101-121；菜单 151-179；权限绑定 201-221；菜单绑定 301-329。
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 4.1 material_category 物料分类表（树形）
-- ----------------------------------------------------------------------------
CREATE TABLE material_category (
    id            BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    parent_id     BIGINT       NULL                    COMMENT '父分类 ID（NULL=根节点，禁止循环由服务端校验）',
    category_code VARCHAR(64)  NOT NULL                COMMENT '分类编码（唯一，逻辑删除后不复用）',
    category_name VARCHAR(128) NOT NULL                COMMENT '分类名称',
    enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用（停用不影响历史引用）',
    create_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT      NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT      NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_category_code (category_code),
    KEY idx_item_category_parent (parent_id),
    CONSTRAINT fk_item_category_parent FOREIGN KEY (parent_id) REFERENCES material_category (id),
    CONSTRAINT chk_item_category_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_item_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料分类表（树形）';

-- ----------------------------------------------------------------------------
-- 4.2 material_sku 物料 SKU 主档表（按"不可互换即分 SKU"建档）
-- ----------------------------------------------------------------------------
CREATE TABLE material_sku (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    category_id    BIGINT       NOT NULL                COMMENT '所属分类（→ material_category.id）',
    sku_code       VARCHAR(64)  NOT NULL                COMMENT 'SKU 码（唯一，逻辑删除后不复用）',
    material_name  VARCHAR(128) NOT NULL                COMMENT '物料名称',
    specification  VARCHAR(500) NULL                    COMMENT '规格型号',
    base_unit      VARCHAR(16)  NOT NULL                COMMENT '基础计量单位（单位固定）',
    quantity_scale INT          NOT NULL DEFAULT 3      COMMENT '数量精度 0-3（整数单位不允许小数领用）',
    material_type  VARCHAR(32)  NOT NULL                COMMENT '物料类型：CONSUMABLE 耗材 / RETURNABLE 可回收',
    is_hazardous   TINYINT      NOT NULL DEFAULT 0      COMMENT '危险品：1是 0否（触发双人签字，申领阶段使用）',
    is_valuable    TINYINT      NOT NULL DEFAULT 0      COMMENT '高价值：1是 0否（触发双人签字，申领阶段使用）',
    enabled        TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用（停用不影响历史引用）',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_sku_code (sku_code),
    KEY idx_item_sku_category (category_id, enabled),
    CONSTRAINT fk_item_sku_category FOREIGN KEY (category_id) REFERENCES material_category (id),
    CONSTRAINT chk_item_sku_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_item_sku_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_sku_hazardous CHECK (is_hazardous IN (0, 1)),
    CONSTRAINT chk_item_sku_valuable CHECK (is_valuable IN (0, 1)),
    CONSTRAINT chk_item_sku_scale CHECK (quantity_scale BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料 SKU 主档表';

-- ----------------------------------------------------------------------------
-- 4.3 stock_location 库位表
-- ----------------------------------------------------------------------------
CREATE TABLE stock_location (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id          BIGINT       NULL                    COMMENT '所属实验室（→ lab.id，空间模块建成前允许 NULL，接入后收紧）',
    location_code   VARCHAR(64)  NOT NULL                COMMENT '库位码（同实验室内唯一）',
    location_name   VARCHAR(128) NOT NULL                COMMENT '库位名称',
    location_detail VARCHAR(255) NULL                    COMMENT '位置详述（如货架-层号）',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用（停用不影响历史引用）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    -- 归一键：MySQL 唯一索引允许多个 NULL，lab_id 为 NULL（通用库位）时用生成列归一
    lab_key         BIGINT GENERATED ALWAYS AS (COALESCE(lab_id, 0)) STORED COMMENT '归属归一键（生成列，勿写）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_location_code (lab_key, location_code),
    KEY idx_item_location_lab (lab_id, enabled),
    CONSTRAINT chk_item_location_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_item_location_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库位表';

-- ----------------------------------------------------------------------------
-- 4.4 stock_balance 库存余额表（可重建投影；库位+SKU 唯一）
-- ----------------------------------------------------------------------------
CREATE TABLE stock_balance (
    id                   BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    location_id          BIGINT        NOT NULL                COMMENT '库位（→ stock_location.id）',
    sku_id               BIGINT        NOT NULL                COMMENT '物料（→ material_sku.id）',
    stock_qty            DECIMAL(18,3) NOT NULL DEFAULT 0      COMMENT '当前库存量（非负，可用量）',
    safe_qty             DECIMAL(18,3) NOT NULL DEFAULT 0      COMMENT '安全库存（最低库存，即预警阈值）',
    target_qty           DECIMAL(18,3) NOT NULL DEFAULT 0      COMMENT '补货目标库存（≥ 安全库存）',
    moving_average_price DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '移动加权平均成本',
    version              INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本（条件更新递增）',
    create_time          DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time          DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted              TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除（余额为投影，正常不删除）',
    create_user_id       BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id       BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_balance (location_id, sku_id),
    KEY idx_item_balance_sku (sku_id),
    CONSTRAINT fk_item_balance_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_item_balance_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_item_balance_stock CHECK (stock_qty >= 0),
    CONSTRAINT chk_item_balance_safe CHECK (safe_qty >= 0),
    CONSTRAINT chk_item_balance_target CHECK (target_qty >= safe_qty),
    CONSTRAINT chk_item_balance_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存余额表';

-- ----------------------------------------------------------------------------
-- 4.5 stock_document 库存单据表（movement_type 区分业务动作）
-- ----------------------------------------------------------------------------
CREATE TABLE stock_document (
    id                 BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id        BIGINT       NOT NULL                COMMENT '统一单据 ID（→ biz_document.id；单据中心建成前由本模块预生成雪花 ID 占位）',
    movement_type      VARCHAR(32)  NOT NULL                COMMENT '业务类型：OPENING/PURCHASE_IN/ISSUE/RETURN_IN/TRANSFER/ADJUST/SCRAP/REVERSAL',
    status             VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING/APPROVED/POSTED/CANCELLED（已过账不可编辑）',
    operator_id        BIGINT       NULL                    COMMENT '实际经办人（→ sys_user.id；登录模块接入后由会话写入）',
    owner_teacher_id   BIGINT       NULL                    COMMENT '归属老师（→ sys_user.id，助理代办时记录）',
    grant_id           BIGINT       NULL                    COMMENT '使用的助理授权（→ assistant_grant.id，代办必选一条）',
    material_request_id BIGINT      NULL                    COMMENT '关联申领单（→ material_request.id，本阶段直发领用为 NULL）',
    lab_booking_id     BIGINT       NULL                    COMMENT '关联场地预约（→ lab_booking.id）',
    occurred_at        DATETIME(3)  NOT NULL                COMMENT '业务发生时间（UTC，采购/领用日期）',
    posted_at          DATETIME(3)  NULL                    COMMENT '过账时间（UTC，仅 POSTED 有值）',
    reversal_of_id     BIGINT       NULL                    COMMENT '冲销原单（→ stock_document.id，一期仅允许整单冲销一次）',
    reason             TEXT         NULL                    COMMENT '事由',
    vendor_name        VARCHAR(128) NULL                    COMMENT '供应商名称快照（定稿 v1.0 无此字段，登记扩展待评审）',
    create_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id     BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id     BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_stock_document (document_id),
    KEY idx_item_stock_doc_status_time (status, occurred_at),
    KEY idx_item_stock_doc_type (movement_type, occurred_at),
    KEY idx_item_stock_doc_owner (owner_teacher_id),
    KEY idx_item_stock_doc_reversal (reversal_of_id),
    CONSTRAINT fk_item_stock_doc_reversal FOREIGN KEY (reversal_of_id) REFERENCES stock_document (id),
    CONSTRAINT chk_item_stock_doc_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_stock_doc_posted CHECK (
        (status = 'POSTED' AND posted_at IS NOT NULL)
        OR (status <> 'POSTED' AND posted_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存单据表';

-- 补全 stock_document.grant_id 跨模块外键（目标表见 assistant_grant.sql）
ALTER TABLE stock_document
    ADD KEY idx_item_stock_doc_grant (grant_id),
    ADD CONSTRAINT fk_item_stock_doc_grant
        FOREIGN KEY (grant_id) REFERENCES assistant_grant (id);

-- ----------------------------------------------------------------------------
-- 4.6 stock_document_line 库存单据行表（quantity > 0；出入方向由 movement_type 校验）
-- ----------------------------------------------------------------------------
CREATE TABLE stock_document_line (
    id                    BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    stock_document_id     BIGINT        NOT NULL                COMMENT '所属单据（→ stock_document.id）',
    line_no               INT           NOT NULL                COMMENT '行号（单据内 1 起连续）',
    sku_id                BIGINT        NOT NULL                COMMENT '物料（→ material_sku.id）',
    from_location_id      BIGINT        NULL                    COMMENT '出库位（→ stock_location.id；ISSUE/调拨必填）',
    to_location_id        BIGINT        NULL                    COMMENT '入库位（→ stock_location.id；OPENING/PURCHASE_IN/RETURN_IN 必填）',
    quantity              DECIMAL(18,3) NOT NULL                COMMENT '数量（> 0，精度不超过 SKU.quantity_scale）',
    unit_price_snapshot   DECIMAL(18,4) NULL                    COMMENT '单价快照（入库登记价格 / 过账时成本）',
    amount_snapshot       DECIMAL(18,2) NULL                    COMMENT '金额快照（数量 × 单价，两位小数）',
    request_line_id       BIGINT        NULL                    COMMENT '绑定申领行（→ material_request_line.id，本阶段直发领用为 NULL）',
    original_issue_line_id BIGINT       NULL                    COMMENT '归还绑定的原出库行（→ stock_document_line.id，归还阶段使用）',
    adjust_direction      VARCHAR(32)   NULL                    COMMENT '盘点调账方向（盘点阶段使用）',
    create_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id        BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id        BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_stock_doc_line (stock_document_id, line_no),
    KEY idx_item_stock_doc_line_sku (sku_id),
    KEY idx_item_stock_doc_line_issue (original_issue_line_id),
    CONSTRAINT fk_item_stock_doc_line_doc FOREIGN KEY (stock_document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_item_stock_doc_line_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT fk_item_stock_doc_line_from FOREIGN KEY (from_location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_item_stock_doc_line_to FOREIGN KEY (to_location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_item_stock_doc_line_issue FOREIGN KEY (original_issue_line_id) REFERENCES stock_document_line (id),
    CONSTRAINT chk_item_stock_doc_line_qty CHECK (quantity > 0),
    CONSTRAINT chk_item_stock_doc_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存单据行表';

-- ----------------------------------------------------------------------------
-- 4.7 stock_movement 库存流水表（不可变，只追加；余额可由流水重建）
-- ----------------------------------------------------------------------------
CREATE TABLE stock_movement (
    id                BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    document_line_id  BIGINT        NOT NULL                COMMENT '来源单据行（→ stock_document_line.id）',
    location_id       BIGINT        NOT NULL                COMMENT '库位（→ stock_location.id）',
    sku_id            BIGINT        NOT NULL                COMMENT '物料（→ material_sku.id）',
    direction         VARCHAR(32)   NOT NULL                COMMENT '方向：IN 入库 / OUT 出库',
    qty_delta         DECIMAL(18,3) NOT NULL                COMMENT '净变动（IN 为正，OUT 为负）',
    before_qty        DECIMAL(18,3) NOT NULL                COMMENT '变更前余额',
    after_qty         DECIMAL(18,3) NOT NULL                COMMENT '变更后余额（非负）',
    value_delta       DECIMAL(18,2) NOT NULL                COMMENT '价值变动（IN 为入库金额，OUT 为负的出库成本）',
    before_avg_price  DECIMAL(18,4) NOT NULL                COMMENT '过账前移动加权均价',
    after_avg_price   DECIMAL(18,4) NOT NULL                COMMENT '过账后移动加权均价',
    occurred_at       DATETIME(3)   NOT NULL                COMMENT '发生时间（UTC，取单据 occurred_at）',
    movement_key      VARCHAR(128)  NOT NULL                COMMENT '幂等键（STK:{documentId}:{lineNo}:{方向}，唯一）',
    create_time       DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time       DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除（流水不可改写，纠错用冲销新单）',
    create_user_id    BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id    BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_movement_key (movement_key),
    KEY idx_item_movement_loc_sku_time (location_id, sku_id, occurred_at),
    KEY idx_item_movement_occurred (occurred_at),
    KEY idx_item_movement_line (document_line_id),
    KEY idx_item_movement_sku (sku_id),
    CONSTRAINT fk_item_movement_line FOREIGN KEY (document_line_id) REFERENCES stock_document_line (id),
    CONSTRAINT fk_item_movement_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_item_movement_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_item_movement_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_movement_qty CHECK (after_qty >= 0),
    CONSTRAINT chk_item_movement_dir CHECK (
        (direction = 'IN' AND qty_delta > 0)
        OR (direction = 'OUT' AND qty_delta < 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水表（不可变，只追加）';

-- ----------------------------------------------------------------------------
-- 4.8 物料一阶段权限（101-121，模块:资源:操作，module_code='item'）
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(101, 'item:category:list',    '物料分类查询',   'item', 1),
(102, 'item:category:create',  '物料分类新增',   'item', 1),
(103, 'item:category:update',  '物料分类修改',   'item', 1),
(104, 'item:category:enable',  '物料分类启停',   'item', 1),
(105, 'item:material:list',    '物料档案查询',   'item', 1),
(106, 'item:material:create',  '物料档案新增',   'item', 1),
(107, 'item:material:update',  '物料档案修改',   'item', 1),
(108, 'item:material:enable',  '物料档案启停',   'item', 1),
(109, 'item:location:list',    '库位查询',       'item', 1),
(110, 'item:location:create',  '库位新增',       'item', 1),
(111, 'item:location:update',  '库位修改',       'item', 1),
(112, 'item:location:enable',  '库位启停',       'item', 1),
(113, 'item:stock:create',     '库存单据登记',   'item', 1),
(114, 'item:stock:approve',    '库存单据审批',   'item', 1),
(115, 'item:stock:cancel',     '库存单据取消',   'item', 1),
(116, 'item:stock:post',       '库存单据过账',   'item', 1),
(117, 'item:stock:list',       '库存单据查询',   'item', 1),
(118, 'item:balance:list',     '库存余额查询',   'item', 1),
(119, 'item:ledger:list',      '库存台账查询',   'item', 1),
(120, 'item:warning:list',     '库存预警查询',   'item', 1),
(121, 'item:balance:update',   '库存预警配置',   'item', 1);

-- ----------------------------------------------------------------------------
-- 4.9 物料一阶段菜单（151-179：目录 151 → 页面 152-158 → 动作 159-179）
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
-- 目录：物料管理
(151, NULL, 'ITEM',            '物料管理', '/admin/item',             NULL, NULL, 'DIRECTORY', 50, 1),
-- 页面（挂目录 151）
(152, 151, 'ITEM_CATEGORY',    '物料分类', '/admin/item/category',    NULL, NULL, 'PAGE', 1, 1),
(153, 151, 'ITEM_MATERIAL',    '物料档案', '/admin/item/material',    NULL, NULL, 'PAGE', 2, 1),
(154, 151, 'ITEM_LOCATION',    '库位管理', '/admin/item/location',    NULL, NULL, 'PAGE', 3, 1),
(155, 151, 'ITEM_DOCUMENT',    '库存单据', '/admin/item/document',    NULL, NULL, 'PAGE', 4, 1),
(156, 151, 'ITEM_BALANCE',     '库存余额', '/admin/item/balance',     NULL, NULL, 'PAGE', 5, 1),
(157, 151, 'ITEM_LEDGER',      '库存台账', '/admin/item/ledger',      NULL, NULL, 'PAGE', 6, 1),
(158, 151, 'ITEM_WARNING',     '库存预警', '/admin/item/warning',     NULL, NULL, 'PAGE', 7, 1),
-- 动作：物料分类（权限 101-104）
(159, 152, 'item:category:list',   '物料分类查询', NULL, NULL, 101, 'ACTION', 1, 1),
(160, 152, 'item:category:create', '物料分类新增', NULL, NULL, 102, 'ACTION', 2, 1),
(161, 152, 'item:category:update', '物料分类修改', NULL, NULL, 103, 'ACTION', 3, 1),
(162, 152, 'item:category:enable', '物料分类启停', NULL, NULL, 104, 'ACTION', 4, 1),
-- 动作：物料档案（权限 105-108）
(163, 153, 'item:material:list',   '物料档案查询', NULL, NULL, 105, 'ACTION', 1, 1),
(164, 153, 'item:material:create', '物料档案新增', NULL, NULL, 106, 'ACTION', 2, 1),
(165, 153, 'item:material:update', '物料档案修改', NULL, NULL, 107, 'ACTION', 3, 1),
(166, 153, 'item:material:enable', '物料档案启停', NULL, NULL, 108, 'ACTION', 4, 1),
-- 动作：库位（权限 109-112）
(167, 154, 'item:location:list',   '库位查询', NULL, NULL, 109, 'ACTION', 1, 1),
(168, 154, 'item:location:create', '库位新增', NULL, NULL, 110, 'ACTION', 2, 1),
(169, 154, 'item:location:update', '库位修改', NULL, NULL, 111, 'ACTION', 3, 1),
(170, 154, 'item:location:enable', '库位启停', NULL, NULL, 112, 'ACTION', 4, 1),
-- 动作：库存单据（权限 113-117）
(171, 155, 'item:stock:create', '库存单据登记', NULL, NULL, 113, 'ACTION', 1, 1),
(172, 155, 'item:stock:approve', '库存单据审批', NULL, NULL, 114, 'ACTION', 2, 1),
(173, 155, 'item:stock:cancel', '库存单据取消', NULL, NULL, 115, 'ACTION', 3, 1),
(174, 155, 'item:stock:post', '库存单据过账', NULL, NULL, 116, 'ACTION', 4, 1),
(175, 155, 'item:stock:list', '库存单据查询', NULL, NULL, 117, 'ACTION', 5, 1),
-- 动作：库存余额（查询 118 / 预警配置 121）
(176, 156, 'item:balance:list',   '库存余额查询', NULL, NULL, 118, 'ACTION', 1, 1),
(177, 156, 'item:balance:update', '库存预警配置', NULL, NULL, 121, 'ACTION', 2, 1),
-- 动作：库存台账（119）
(178, 157, 'item:ledger:list', '库存台账查询', NULL, NULL, 119, 'ACTION', 1, 1),
-- 动作：库存预警（120）
(179, 158, 'item:warning:list', '库存预警查询', NULL, NULL, 120, 'ACTION', 1, 1);

-- ----------------------------------------------------------------------------
-- 4.10 物料一阶段管理员角色预绑定（权限 201-221、菜单 301-329）
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(201, 1, 101, 1), (202, 1, 102, 1), (203, 1, 103, 1), (204, 1, 104, 1),
(205, 1, 105, 1), (206, 1, 106, 1), (207, 1, 107, 1), (208, 1, 108, 1),
(209, 1, 109, 1), (210, 1, 110, 1), (211, 1, 111, 1), (212, 1, 112, 1),
(213, 1, 113, 1), (214, 1, 114, 1), (215, 1, 115, 1), (216, 1, 116, 1),
(217, 1, 117, 1), (218, 1, 118, 1), (219, 1, 119, 1), (220, 1, 120, 1),
(221, 1, 121, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(301, 1, 151, 1), (302, 1, 152, 1), (303, 1, 153, 1), (304, 1, 154, 1),
(305, 1, 155, 1), (306, 1, 156, 1), (307, 1, 157, 1), (308, 1, 158, 1),
(309, 1, 159, 1), (310, 1, 160, 1), (311, 1, 161, 1), (312, 1, 162, 1),
(313, 1, 163, 1), (314, 1, 164, 1), (315, 1, 165, 1), (316, 1, 166, 1),
(317, 1, 167, 1), (318, 1, 168, 1), (319, 1, 169, 1), (320, 1, 170, 1),
(321, 1, 171, 1), (322, 1, 172, 1), (323, 1, 173, 1), (324, 1, 174, 1),
(325, 1, 175, 1), (326, 1, 176, 1), (327, 1, 177, 1), (328, 1, 178, 1),
(329, 1, 179, 1);

-- ############################################################################
-- 模块五：物料管理模块 · 第二阶段（实施分组 C）
--   表：material_request / material_request_line / material_quota /
--       material_settlement / stocktake / stocktake_line。
--   种子：权限 122-137；菜单 180-199；权限绑定 222-237；菜单绑定 330-349。
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 5.1 material_request 物料申领单（单据头）
-- ----------------------------------------------------------------------------
CREATE TABLE material_request (
    id                    BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id           BIGINT       NOT NULL                COMMENT '统一单据 ID（→ biz_document.id；单据中心建成前由本模块预生成雪花 ID 占位）',
    lab_id                BIGINT       NULL                    COMMENT '领用实验室（→ lab.id，空间模块建成前允许 NULL）',
    owner_teacher_id      BIGINT       NULL                    COMMENT '归属老师（→ sys_user.id；登录模块接入后由会话写入）',
    operator_id           BIGINT       NULL                    COMMENT '经办人（→ sys_user.id；助理代领时为助理本人）',
    grant_id              BIGINT       NULL                    COMMENT '命中的助理授权（→ assistant_grant.id）',
    offering_id           BIGINT       NULL                    COMMENT '课程实例（→ course_offering.id，课程模块建成前允许 NULL）',
    lab_booking_id        BIGINT       NULL                    COMMENT '场地预约（→ lab_booking.id；预约与课次恰一非空，模块接入后收紧）',
    schedule_occurrence_id BIGINT      NULL                    COMMENT '课次（→ schedule_occurrence.id；不能与 lab_booking_id 同时填）',
    participant_count     INT          NOT NULL DEFAULT 0      COMMENT '参与人数（非负）',
    period_count          INT          NOT NULL DEFAULT 0      COMMENT '课时数（非负）',
    purpose               TEXT         NULL                    COMMENT '用途',
    status                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING/APPROVED/PARTIALLY_FULFILLED/FULFILLED/REJECTED/CANCELLED',
    create_time           DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time           DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted               TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id        BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id        BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_request_doc (document_id),
    KEY idx_item_request_owner (owner_teacher_id, status, create_time),
    KEY idx_item_request_grant (grant_id),
    CONSTRAINT chk_item_request_participant CHECK (participant_count >= 0),
    CONSTRAINT chk_item_request_period CHECK (period_count >= 0),
    CONSTRAINT chk_item_request_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_request_status CHECK (status IN
        ('DRAFT', 'PENDING', 'APPROVED', 'PARTIALLY_FULFILLED', 'FULFILLED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_item_request_booking CHECK (
        (lab_booking_id IS NULL) OR (schedule_occurrence_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料申领单表';

-- 补全 material_request.grant_id 跨模块外键（目标表见 assistant_grant.sql）
ALTER TABLE material_request
    ADD CONSTRAINT fk_item_request_grant
        FOREIGN KEY (grant_id) REFERENCES assistant_grant (id);

-- ----------------------------------------------------------------------------
-- 5.2 material_request_line 申领行（申领单+SKU 唯一）
-- ----------------------------------------------------------------------------
CREATE TABLE material_request_line (
    id                    BIGINT        NOT NULL               COMMENT '主键（雪花 ID）',
    request_id            BIGINT        NOT NULL               COMMENT '所属申领单（→ material_request.id）',
    sku_id                BIGINT        NOT NULL               COMMENT '物料（→ material_sku.id）',
    requested_qty         DECIMAL(18,3) NOT NULL               COMMENT '申请量（> 0）',
    quota_qty_snapshot    DECIMAL(18,3) NOT NULL DEFAULT 0     COMMENT '定额快照（参与人数 × 课时 × 每人每课时定额；缺定额记 0，须老师确认）',
    requires_extra_approval TINYINT     NOT NULL DEFAULT 0    COMMENT '超定额：1是 0否（进入老师签批阶段）',
    requires_dual_sign    TINYINT       NOT NULL DEFAULT 0    COMMENT '双人签字：1是 0否（危险/高价值物料）',
    approved_qty          DECIMAL(18,3) NULL                   COMMENT '批准量（审批时写入，不超过申请量）',
    fulfilled_qty         DECIMAL(18,3) NOT NULL DEFAULT 0    COMMENT '实发量（过账回写，不超过批准量）',
    create_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time           DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted               TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1删除',
    create_user_id        BIGINT        NULL                   COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id        BIGINT        NULL                   COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_request_line (request_id, sku_id),
    KEY idx_item_request_line_sku (sku_id),
    CONSTRAINT fk_item_request_line_request FOREIGN KEY (request_id) REFERENCES material_request (id),
    CONSTRAINT fk_item_request_line_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_item_request_line_qty CHECK (requested_qty > 0),
    CONSTRAINT chk_item_request_line_quota CHECK (quota_qty_snapshot >= 0),
    CONSTRAINT chk_item_request_line_fulfilled CHECK (fulfilled_qty >= 0 AND fulfilled_qty <= requested_qty),
    CONSTRAINT chk_item_request_line_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_request_line_flags CHECK (
        requires_extra_approval IN (0, 1) AND requires_dual_sign IN (0, 1)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料申领行表';

-- ----------------------------------------------------------------------------
-- 5.3 material_quota 领用定额（开课实例+SKU 唯一）
-- ----------------------------------------------------------------------------
CREATE TABLE material_quota (
    id                     BIGINT        NOT NULL               COMMENT '主键（雪花 ID）',
    offering_id            BIGINT        NOT NULL               COMMENT '开课实例（→ course_offering.id，跨模块只建列不建外键）',
    sku_id                 BIGINT        NOT NULL               COMMENT '物料（→ material_sku.id）',
    qty_per_student_period DECIMAL(18,3) NOT NULL               COMMENT '每人每课时定额（非负）',
    enabled                TINYINT       NOT NULL DEFAULT 1    COMMENT '启用：1启用 0停用（停用后新申请不再取默认定额）',
    create_time            DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time            DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted                TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1删除',
    create_user_id         BIGINT        NULL                   COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id         BIGINT        NULL                   COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_quota (offering_id, sku_id),
    KEY idx_item_quota_sku (sku_id),
    CONSTRAINT fk_item_quota_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_item_quota_qty CHECK (qty_per_student_period >= 0),
    CONSTRAINT chk_item_quota_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_item_quota_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='领用定额表';

-- ----------------------------------------------------------------------------
-- 5.4 material_settlement 物料结算（归还登记与核销同事务）
-- ----------------------------------------------------------------------------
CREATE TABLE material_settlement (
    id              BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    issue_line_id   BIGINT        NOT NULL                COMMENT '原出库行（→ stock_document_line.id）',
    settlement_type VARCHAR(32)   NOT NULL                COMMENT '结算类型：RETURNED 归还 / CONSUMED 耗用 / LOST 丢失 / DAMAGED 损坏',
    quantity        DECIMAL(18,3) NOT NULL                COMMENT '本次结算量（> 0）',
    operator_id     BIGINT        NULL                    COMMENT '经办人（→ sys_user.id；登录模块接入后由会话写入）',
    settled_at      DATETIME(3)   NOT NULL                COMMENT '结算时间（UTC）',
    return_line_id  BIGINT        NULL                    COMMENT '对应归还行（→ stock_document_line.id，同归还行唯一）',
    document_id     BIGINT        NOT NULL                COMMENT '幂等单据 ID（→ biz_document.id；单据中心建成前由本模块预生成占位）',
    reason          TEXT          NULL                    COMMENT '差异/核销原因',
    create_time     DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_settlement_return_line (return_line_id),
    KEY idx_item_settlement_issue (issue_line_id),
    KEY idx_item_settlement_doc (document_id),
    CONSTRAINT fk_item_settlement_issue FOREIGN KEY (issue_line_id) REFERENCES stock_document_line (id),
    CONSTRAINT fk_item_settlement_return FOREIGN KEY (return_line_id) REFERENCES stock_document_line (id),
    CONSTRAINT chk_item_settlement_qty CHECK (quantity > 0),
    CONSTRAINT chk_item_settlement_type CHECK (settlement_type IN ('RETURNED', 'CONSUMED', 'LOST', 'DAMAGED')),
    CONSTRAINT chk_item_settlement_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_settlement_return_type CHECK (
        (settlement_type = 'RETURNED' AND return_line_id IS NOT NULL)
        OR (settlement_type <> 'RETURNED' AND return_line_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料结算表';

-- ----------------------------------------------------------------------------
-- 5.5 stocktake 盘点单（同一库位最多一张 COUNTING/PENDING_REVIEW 活跃单）
-- ----------------------------------------------------------------------------
CREATE TABLE stocktake (
    id                  BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id         BIGINT       NOT NULL                COMMENT '统一单据 ID（→ biz_document.id；单据中心建成前由本模块预生成占位）',
    location_id         BIGINT       NOT NULL                COMMENT '盘点库位（→ stock_location.id）',
    semester_id         BIGINT       NULL                    COMMENT '学期（→ academic_semester.id，跨模块只建列不建外键）',
    count_date          DATE         NOT NULL                COMMENT '盘点日期（纯日期，不做时区转换）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/COUNTING/PENDING_REVIEW/COMPLETED/CANCELLED',
    started_at          DATETIME(3)  NULL                    COMMENT '开始时间（UTC）',
    finished_at         DATETIME(3)  NULL                    COMMENT '完成时间（UTC）',
    adjust_document_id  BIGINT       NULL                    COMMENT '生成的调账单（→ stock_document.id，差异非零时生成）',
    create_time         DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time         DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id      BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id      BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    -- 活跃归一键：COUNTING/PENDING_REVIEW 期间取库位 ID，其余状态为 NULL
    active_key          BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('COUNTING', 'PENDING_REVIEW') THEN location_id ELSE NULL END
    ) STORED COMMENT '活跃库位归一键（生成列，勿写）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_stocktake_doc (document_id),
    UNIQUE KEY uk_item_stocktake_active (active_key),
    KEY idx_item_stocktake_location (location_id, status),
    CONSTRAINT fk_item_stocktake_location FOREIGN KEY (location_id) REFERENCES stock_location (id),
    CONSTRAINT fk_item_stocktake_adjust FOREIGN KEY (adjust_document_id) REFERENCES stock_document (id),
    CONSTRAINT chk_item_stocktake_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_item_stocktake_status CHECK (status IN
        ('DRAFT', 'COUNTING', 'PENDING_REVIEW', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_item_stocktake_time CHECK (
        (status IN ('DRAFT', 'CANCELLED') AND started_at IS NULL AND finished_at IS NULL)
        OR (status IN ('COUNTING', 'PENDING_REVIEW') AND started_at IS NOT NULL AND finished_at IS NULL)
        OR (status = 'COMPLETED' AND started_at IS NOT NULL AND finished_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盘点单表';

-- ----------------------------------------------------------------------------
-- 5.6 stocktake_line 盘点行（盘点+SKU 唯一）
-- ----------------------------------------------------------------------------
CREATE TABLE stocktake_line (
    id                        BIGINT        NOT NULL               COMMENT '主键（雪花 ID）',
    stocktake_id              BIGINT        NOT NULL               COMMENT '所属盘点单（→ stocktake.id）',
    sku_id                    BIGINT        NOT NULL               COMMENT '物料（→ material_sku.id）',
    book_qty_snapshot         DECIMAL(18,3) NOT NULL               COMMENT '账面量快照（建单时从 stock_balance 复制）',
    counted_qty               DECIMAL(18,3) NULL                   COMMENT '实盘量（非负，未录入为 NULL）',
    balance_version_snapshot  INT           NOT NULL               COMMENT '余额版本快照（提交时校验一致）',
    difference_reason         TEXT          NULL                   COMMENT '差异原因（差异非零必填）',
    counted_by                BIGINT        NULL                   COMMENT '盘点人（→ sys_user.id，登录接入后写入）',
    counted_at                DATETIME(3)   NULL                   COMMENT '实盘时间（UTC）',
    create_time               DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time               DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted                   TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0正常 1删除',
    create_user_id            BIGINT        NULL                   COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id            BIGINT        NULL                   COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_stocktake_line (stocktake_id, sku_id),
    KEY idx_item_stocktake_line_sku (sku_id),
    CONSTRAINT fk_item_stocktake_line_take FOREIGN KEY (stocktake_id) REFERENCES stocktake (id),
    CONSTRAINT fk_item_stocktake_line_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_item_stocktake_line_book CHECK (book_qty_snapshot >= 0),
    CONSTRAINT chk_item_stocktake_line_counted CHECK (counted_qty IS NULL OR counted_qty >= 0),
    CONSTRAINT chk_item_stocktake_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盘点行表';

-- ----------------------------------------------------------------------------
-- 5.7 物料二阶段权限（122-137）
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(122, 'item:request:list',      '申领单查询',     'item', 1),
(123, 'item:request:create',    '申领单创建',     'item', 1),
(124, 'item:request:approve',   '申领单审批',     'item', 1),
(125, 'item:request:cancel',    '申领单取消',     'item', 1),
(126, 'item:request:issue',     '申领实发出库',   'item', 1),
(127, 'item:quota:list',        '领用定额查询',   'item', 1),
(128, 'item:quota:create',      '领用定额新增',   'item', 1),
(129, 'item:quota:update',      '领用定额修改',   'item', 1),
(130, 'item:quota:enable',      '领用定额启停',   'item', 1),
(131, 'item:settlement:create', '物料核销登记',   'item', 1),
(132, 'item:settlement:list',   '物料结算查询',   'item', 1),
(133, 'item:stocktake:list',    '盘点单查询',     'item', 1),
(134, 'item:stocktake:create',  '盘点单创建',     'item', 1),
(135, 'item:stocktake:count',   '盘点实盘录入',   'item', 1),
(136, 'item:stocktake:complete','盘点提交复核',   'item', 1),
(137, 'item:adjust:approve',    '盘点调账审批',   'item', 1);

-- ----------------------------------------------------------------------------
-- 5.8 物料二阶段菜单（180-199：页面 180-183 挂目录 151；动作 184-199 挂对应页面）
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
-- 页面：申领管理 / 领用定额 / 物料结算 / 盘点管理（挂目录 151）
(180, 151, 'ITEM_REQUEST',     '申领管理', '/admin/item/request',     NULL, NULL, 'PAGE', 8,  1),
(181, 151, 'ITEM_QUOTA',        '领用定额', '/admin/item/quota',        NULL, NULL, 'PAGE', 9,  1),
(182, 151, 'ITEM_SETTLEMENT',   '物料结算', '/admin/item/settlement',   NULL, NULL, 'PAGE', 10, 1),
(183, 151, 'ITEM_STOCKTAKE',    '盘点管理', '/admin/item/stocktake',    NULL, NULL, 'PAGE', 11, 1),
-- 动作：申领管理（权限 122-126）
(184, 180, 'item:request:list',       '申领单查询',   NULL, NULL, 122, 'ACTION', 1, 1),
(185, 180, 'item:request:create',     '申领单创建',   NULL, NULL, 123, 'ACTION', 2, 1),
(186, 180, 'item:request:approve',    '申领单审批',   NULL, NULL, 124, 'ACTION', 3, 1),
(187, 180, 'item:request:cancel',     '申领单取消',   NULL, NULL, 125, 'ACTION', 4, 1),
(188, 180, 'item:request:issue',      '申领实发出库', NULL, NULL, 126, 'ACTION', 5, 1),
-- 动作：领用定额（权限 127-130）
(189, 181, 'item:quota:list',   '领用定额查询', NULL, NULL, 127, 'ACTION', 1, 1),
(190, 181, 'item:quota:create', '领用定额新增', NULL, NULL, 128, 'ACTION', 2, 1),
(191, 181, 'item:quota:update', '领用定额修改', NULL, NULL, 129, 'ACTION', 3, 1),
(192, 181, 'item:quota:enable', '领用定额启停', NULL, NULL, 130, 'ACTION', 4, 1),
-- 动作：物料结算（权限 131-132）
(193, 182, 'item:settlement:create', '物料核销登记', NULL, NULL, 131, 'ACTION', 1, 1),
(194, 182, 'item:settlement:list',   '物料结算查询', NULL, NULL, 132, 'ACTION', 2, 1),
-- 动作：盘点管理（权限 133-137）
(195, 183, 'item:stocktake:list',     '盘点单查询',   NULL, NULL, 133, 'ACTION', 1, 1),
(196, 183, 'item:stocktake:create',   '盘点单创建',   NULL, NULL, 134, 'ACTION', 2, 1),
(197, 183, 'item:stocktake:count',    '盘点实盘录入', NULL, NULL, 135, 'ACTION', 3, 1),
(198, 183, 'item:stocktake:complete', '盘点提交复核', NULL, NULL, 136, 'ACTION', 4, 1),
(199, 183, 'item:adjust:approve',     '盘点调账审批', NULL, NULL, 137, 'ACTION', 5, 1);

-- ----------------------------------------------------------------------------
-- 5.9 物料二阶段管理员角色预绑定（权限 222-237、菜单 330-349）
-- ----------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(222, 1, 122, 1), (223, 1, 123, 1), (224, 1, 124, 1), (225, 1, 125, 1),
(226, 1, 126, 1), (227, 1, 127, 1), (228, 1, 128, 1), (229, 1, 129, 1),
(230, 1, 130, 1), (231, 1, 131, 1), (232, 1, 132, 1), (233, 1, 133, 1),
(234, 1, 134, 1), (235, 1, 135, 1), (236, 1, 136, 1), (237, 1, 137, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(330, 1, 180, 1), (331, 1, 181, 1), (332, 1, 182, 1), (333, 1, 183, 1),
(334, 1, 184, 1), (335, 1, 185, 1), (336, 1, 186, 1), (337, 1, 187, 1),
(338, 1, 188, 1), (339, 1, 189, 1), (340, 1, 190, 1), (341, 1, 191, 1),
(342, 1, 192, 1), (343, 1, 193, 1), (344, 1, 194, 1), (345, 1, 195, 1),
(346, 1, 196, 1), (347, 1, 197, 1), (348, 1, 198, 1), (349, 1, 199, 1);

-- ############################################################################
-- 附注：移除借用功能（2026-10-14 产品定稿：纯管理员设备管理，无设备借用）
--   本文件已按定稿不登记借用权限/菜单（借用两张表结构保留见模块三）。
--   曾执行过旧版种子（借用权限 155-161、借用菜单 165-169/173、
--   设备菜单旧编号 161-170、绑定 529-535/611）的库，请用独立的
--   「移除借用功能清理.sql」做增量清理——该脚本的 ID 基于旧编号，
--   与本文件的整合编号（物料菜单 151-199、设备权限 141-154）存在重叠，
--   故不并入本文件，全新库无需执行。
-- ############################################################################

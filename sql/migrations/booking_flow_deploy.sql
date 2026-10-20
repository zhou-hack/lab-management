-- ============================================================================
-- 场地预约「权级流程」增量部署脚本（幂等，可重复执行）
-- 版本：2026-10-17
--
-- 流程：教师发起(PENDING/待管理员核对) → 管理员核对批准(APPROVED)/整单驳回(REJECTED)
--       → 开门签到(CHECKIN 事件, IN_USE 派生) → 结束清点(CHECKOUT 事件, FINISHED 派生)；
--       start_time 已过未核对由定时任务自动取消(CANCELLED/AUTO_EXPIRED)。
-- 角色：仅教师发起；学生纯只读（booking:lab:view，看预约情况/批准驳回结果/驳回原因）；
--       审批/签到/清点仅管理员（booking:lab:approve）；教师不审核。
--
-- 内容：
--   1) 建 biz_document / approval_task / document_event 三张流程表（已存在则跳过）
--   2) 新增权限 3005 booking:lab:view（场地预约只读）
--   3) 新增学生只读菜单 3004（/booking/labs 同一路径，权限 3005），授予角色 STUDENT(3)
--   4) 学生仅有 view：无 create/cancel/approve，不能发起/撤销/审批
--
-- 说明：仅新增表结构与授权种子，不修改任何已有业务数据。
--       policy_id 列保留但本期不使用，故不添加 business_policy 外键
--       （避免依赖 sys_policy.sql 是否已部署）。
-- ============================================================================

-- 1) 统一单据表 -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_document (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_no     VARCHAR(64)  NOT NULL                COMMENT '单据编号（LAB+预约ID，唯一）',
    document_type   VARCHAR(32)  NOT NULL                COMMENT '单据类型：LAB_BOOKING',
    owner_user_id   BIGINT       NOT NULL                COMMENT '归属用户（→ sys_user.id）',
    department_id   BIGINT       NOT NULL                COMMENT '归属部门（→ sys_department.id）',
    policy_id       BIGINT       NULL                    COMMENT '审批策略（本期不用）',
    submitted_at    DATETIME(3)  NULL                    COMMENT '提交时间（UTC）',
    idempotency_key VARCHAR(128) NOT NULL                COMMENT '幂等键（=预约ID）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_document_no (document_no),
    UNIQUE KEY uk_biz_document_idem (owner_user_id, document_type, idempotency_key),
    KEY idx_biz_document_owner (owner_user_id, document_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一单据表（场地预约权级流程归属根）';

-- 2) 审批阶段待办表 ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_task (
    id                     BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id            BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    stage_code             VARCHAR(64)  NOT NULL                COMMENT '审批阶段：ADMIN_REVIEW',
    sequence_no            INT          NOT NULL                COMMENT '阶段顺序号（从 1 起）',
    round_no               INT          NOT NULL                COMMENT '审批轮次（默认 1）',
    required_role          VARCHAR(64)  NOT NULL                COMMENT '要求角色码（ADMIN）',
    assigned_user_id       BIGINT       NULL                    COMMENT '指定审批人（NULL=按角色匹配）',
    assigned_department_id BIGINT       NULL                    COMMENT '指定审批部门',
    status                 VARCHAR(32)  NOT NULL                COMMENT '任务状态：WAITING/READY/APPROVED/REJECTED/CANCELLED',
    available_at           DATETIME(3)  NOT NULL                COMMENT '任务生效时间（UTC）',
    remind_at              DATETIME(3)  NULL                    COMMENT '催办时间（UTC）',
    escalate_at            DATETIME(3)  NULL                    COMMENT '升级时间（UTC）',
    timeout_at             DATETIME(3)  NULL                    COMMENT '超时时间（=预约开始时间）',
    timeout_action         VARCHAR(32)  NOT NULL                COMMENT '超时动作：CANCEL/NONE',
    decided_by             BIGINT       NULL                    COMMENT '实际审批人（→ sys_user.id）',
    decided_at             DATETIME(3)  NULL                    COMMENT '审批时间（UTC）',
    decision_comment       TEXT         NULL                    COMMENT '审批意见（驳回必填）',
    create_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted                TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0',
    create_user_id         BIGINT       NULL                    COMMENT '创建人',
    update_user_id         BIGINT       NULL                    COMMENT '最后操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_task (document_id, stage_code, sequence_no, round_no),
    KEY idx_approval_task_status_timeout (status, timeout_at),
    KEY idx_approval_task_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批阶段待办表（管理员核对阶段流转）';

-- 3) 单据事件表（不可变过程历史）---------------------------------------------
CREATE TABLE IF NOT EXISTS document_event (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id     BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    event_type      VARCHAR(32)  NOT NULL                COMMENT '事件：SUBMIT/APPROVE/REJECT/CANCEL/CHECKIN/CHECKOUT/TIMEOUT_CANCEL',
    from_status     VARCHAR(32)  NULL                    COMMENT '变更前业务状态',
    to_status       VARCHAR(32)  NULL                    COMMENT '变更后业务状态（IN_USE/FINISHED 为派生态）',
    actor_id        BIGINT       NULL                    COMMENT '操作人（系统事件为 NULL）',
    actor_type      VARCHAR(32)  NOT NULL                COMMENT '操作人类型：USER/SYSTEM',
    occurred_at     DATETIME(3)  NOT NULL                COMMENT '发生时间（UTC）',
    reason          TEXT         NULL                    COMMENT '事由/审批意见/驳回原因/清点备注',
    task_id         BIGINT       NULL                    COMMENT '关联审批任务（→ approval_task.id）',
    request_key     VARCHAR(128) NOT NULL                COMMENT '请求键（事件类型:预约ID，防重复）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0',
    create_user_id  BIGINT       NULL                    COMMENT '创建人',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_event (document_id, request_key),
    KEY idx_document_event_type (document_id, event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据事件表（不可变过程历史）';

-- 4) 新增只读权限 3005 -------------------------------------------------------
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(3005, 'booking:lab:view', '场地预约只读', 'booking', 1);

-- 5) 学生只读菜单 3004：与教师 3003 同路径 /booking/labs、同本地组件，
--    但挂 view(3005) 权限；后端按角色下发，学生仅收到此菜单。
INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(3004, NULL, 'booking_user_view', '预约情况', '/booking/labs', 'BookingUserList', 3005, 'PAGE', 11, 1);

-- 6) 安全兜底：先回收再授权（旧库同 ID 残留会让 INSERT IGNORE 跳过新绑定，
--    回收后又把旧行删掉导致绑定缺失，顺序不可颠倒）
DELETE FROM sys_role_permission
WHERE role_id = 3 AND permission_id IN (3001, 3002, 3003, 3004);
DELETE FROM sys_role_permission
WHERE role_id = 1 AND permission_id IN (3001, 3003);
DELETE FROM sys_role_menu
WHERE role_id = 3 AND menu_id IN (3000, 3001, 3002, 3003);

-- 7) 授权：STUDENT(3) 仅 view 权限 + 只读菜单；ADMIN/TEACHER 顺带授予 view 不影响写权限
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(3008, 3, 3005, 1),
(3009, 1, 3005, 1),
(3010, 2, 3005, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(3005, 3, 3004, 1);

-- 8) 部署后人工校验：
-- SELECT role_id, permission_id FROM sys_role_permission WHERE permission_id BETWEEN 3001 AND 3005 ORDER BY role_id, permission_id;
-- 预期：学生(3) 仅 3005；教师(2) 3001/3002/3003/3005；管理员(1) 3002/3004/3005。

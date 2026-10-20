-- ============================================================================
-- 场地预约模块部署脚本（幂等，可重复执行）
-- 版本：2026-10-17；适用：已部署 RBAC + 场地 lab 表、尚未部署预约模块的库。
--
-- 角色流程（业务定稿）：
--   ADMIN(1)   ：管理端「预约管理 + 预约审批」菜单，booking:lab:* 全部权限
--   TEACHER(2) ：前台「场地预约」菜单，可发起预约/查看本人记录/撤销
--   STUDENT(3) ：不开放场地预约——无菜单、无任何 booking:lab:* 权限、前端无入口
--
-- 说明：仅新增 lab_booking/lab_slot 两张业务表与预约授权种子，不修改任何已有
--       业务数据；曾授予学生预约权限的库，脚本末尾会自动回收（幂等）。
-- ============================================================================

-- 1) 业务表：场地预约单
CREATE TABLE IF NOT EXISTS lab_booking (
    id                 BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id             BIGINT       NOT NULL                COMMENT '预约场地（→ lab.id）',
    applicant_id       BIGINT       NOT NULL                COMMENT '申请人（→ sys_user.id）',
    purpose            VARCHAR(512) NOT NULL                COMMENT '用途说明',
    participant_count  INT          NOT NULL                COMMENT '参与人数（>0，且不超过场地容量）',
    start_at           DATETIME(3)  NOT NULL                COMMENT '预约开始（UTC）',
    end_at             DATETIME(3)  NOT NULL                COMMENT '预约结束（UTC）',
    status             VARCHAR(32)  NOT NULL                COMMENT '状态：PENDING待审批/APPROVED已批准/REJECTED已驳回/CANCELLED已取消',
    endorse_teacher_id BIGINT       NULL                    COMMENT '背书老师（→ sys_user.id，一期简化流程预留）',
    endorse_at         DATETIME(3)  NULL                    COMMENT '背书时间（UTC，预留）',
    endorse_comment    VARCHAR(512) NULL                    COMMENT '背书意见（预留）',
    reject_reason      VARCHAR(512) NULL                    COMMENT '驳回原因',
    cancel_reason      VARCHAR(512) NULL                    COMMENT '取消原因',
    cancel_type        VARCHAR(32)  NULL                    COMMENT '取消类型：MANUAL人工/FORCE强制',
    cancelled_at       DATETIME(3)  NULL                    COMMENT '取消时间（UTC）',
    create_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id     BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id     BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_lab_booking_applicant (applicant_id, create_time),
    KEY idx_lab_booking_lab_status (lab_id, status, start_at),
    CONSTRAINT fk_lab_booking_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT fk_lab_booking_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id),
    CONSTRAINT chk_lab_booking_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_lab_booking_count CHECK (participant_count > 0),
    CONSTRAINT chk_lab_booking_window CHECK (start_at < end_at),
    CONSTRAINT chk_lab_booking_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地预约单（一期简化审批，背书字段预留）';

-- 2) 业务表：场地占用槽（批准时写入，取消/结束时释放；仅未释放记录参与冲突校验）
CREATE TABLE IF NOT EXISTS lab_slot (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id          BIGINT       NOT NULL                COMMENT '占用场地（→ lab.id）',
    booking_id      BIGINT       NOT NULL                COMMENT '来源预约（→ lab_booking.id，唯一）',
    start_at        DATETIME(3)  NOT NULL                COMMENT '占用开始（UTC）',
    end_at          DATETIME(3)  NOT NULL                COMMENT '占用结束（UTC）',
    released_at     DATETIME(3)  NULL                    COMMENT '释放时间（UTC；NULL 表示占用中）',
    release_reason  VARCHAR(512) NULL                    COMMENT '释放原因',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_slot_booking (booking_id),
    KEY idx_lab_slot_conflict (lab_id, released_at, start_at, end_at),
    CONSTRAINT fk_lab_slot_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT fk_lab_slot_booking FOREIGN KEY (booking_id) REFERENCES lab_booking (id),
    CONSTRAINT chk_lab_slot_window CHECK (start_at < end_at),
    CONSTRAINT chk_lab_slot_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地占用槽（仅未释放记录参与冲突校验）';

-- 3) 权限 3001-3004
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(3001, 'booking:lab:create',  '场地预约申请', 'booking', 1),
(3002, 'booking:lab:list',    '场地预约记录', 'booking', 1),
(3003, 'booking:lab:cancel',  '场地预约撤销', 'booking', 1),
(3004, 'booking:lab:approve', '场地预约审批', 'booking', 1);

-- 4) 菜单：管理端目录 + 预约管理/预约审批；用户端「场地预约」单页
INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(3000, NULL,  'booking',          '场地预约', '/admin/booking',          NULL,             NULL, 'DIRECTORY', 30, 1),
(3001, 3000,  'booking_all',      '预约管理', '/admin/booking/labs',     'BookingAdminList', 3002, 'PAGE', 1, 1),
(3002, 3000,  'booking_approval', '预约审批', '/admin/booking/approval', 'BookingApproval',  3004, 'PAGE', 2, 1),
(3003, NULL,  'booking_user',     '场地预约', '/booking/labs',           'BookingUserList',  3001, 'PAGE', 10, 1);

-- 5) 角色授权：ADMIN 全部；TEACHER 申请/记录/撤销；STUDENT 不授权
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(3001, 1, 3001, 1), (3002, 1, 3002, 1), (3003, 1, 3003, 1), (3004, 1, 3004, 1),
(3005, 2, 3001, 1), (3006, 2, 3002, 1), (3007, 2, 3003, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(3001, 1, 3000, 1), (3002, 1, 3001, 1), (3003, 1, 3002, 1),
(3004, 2, 3003, 1);

-- 6) 兜底回收：历史库若曾授予学生（role_id=3）预约权限/菜单，一律移除（幂等）
DELETE FROM sys_role_permission
WHERE role_id = 3 AND permission_id IN (3001, 3002, 3003, 3004);
DELETE FROM sys_role_menu
WHERE role_id = 3 AND menu_id IN (3000, 3001, 3002, 3003);

-- 7) 部署后校验（人工执行，预期学生 0 行、教师 3 权限+1 菜单、管理员 4 权限+3 菜单）：
-- SELECT role_id, COUNT(*) FROM sys_role_permission
-- WHERE permission_id BETWEEN 3001 AND 3004 GROUP BY role_id;
-- SELECT role_id, COUNT(*) FROM sys_role_menu
-- WHERE menu_id BETWEEN 3000 AND 3003 GROUP BY role_id;

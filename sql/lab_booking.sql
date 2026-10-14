-- ============================================================================
-- 实验室设备管理系统 · 场地预约模块建表脚本（v1.1）
-- 依据 docs/数据库设计.md 4.5 节（场地预约）、docs/RBAC实施清单.md 2.4 节。
-- ----------------------------------------------------------------------------
-- 一期裁剪说明（相对数据库设计 4.5 终态，均为后续分组落地时补齐）：
--   1. biz_document 单据表未落地：lab_booking.document_id 及
--      lab_booking_equipment / lab_booking_material / equipment_* 延后；
--   2. 课程模块未落地：offering_id / class_id 与 lab_slot.schedule_occurrence_id
--      延后，lab_slot.booking_id 暂为 NOT NULL（"恰一非空"约束待排课落地恢复）；
--   3. 学生背书链（PENDING_ENDORSE + endorse_* 字段保留但一期不启用）与
--      IN_USE/FINISHED 落库迁移、lab_usage 系统使用档案延后：一期仅
--      TEACHER/ADMIN 发起、ADMIN 审批，PENDING/APPROVED/REJECTED/CANCELLED
--      四态；IN_USE/FINISHED 由接口按当前时间派生展示（文档约定
--      end_at+2h 为归档宽限，不额外占用场地）；
--   4. 状态机（一期）：PENDING → APPROVED / REJECTED；PENDING / APPROVED →
--      CANCELLED（APPROVED 取消同步释放 lab_slot 占用）。
-- ----------------------------------------------------------------------------
-- 关键约定：
--   * 冲突判断为半开区间重叠（数据库设计 8.1）：
--     existing.start_at < request.end_at AND existing.end_at > request.start_at，
--     仅统计 released_at IS NULL 的未释放 lab_slot；
--   * 待审批不永久占用（提交与审批双重校验，最后批准者遇冲突拒绝，
--     不自动抢占）；审批事务先对 biz_space 行加锁（SELECT ... FOR UPDATE）
--     串行化同场地审批，防并发双写；
--   * 时间点列 DATETIME(3) 存 UTC；权限码按 RBAC清单 2.4 为 booking:lab:*，
--     初始数据 ID 段 3000-3999（权限 3001-3007，菜单 3201-3205，
--     角色绑定 3101-3107 / 3121-3125）。
-- ----------------------------------------------------------------------------
-- 执行前提：先执行 sql/sys_rbac.sql 与 sql/biz_space.sql。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. lab_booking 场地预约单表
-- ----------------------------------------------------------------------------
CREATE TABLE lab_booking (
    id                 BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id             BIGINT       NOT NULL                COMMENT '场地（→ biz_space.id，单表模式下 lab 即场地）',
    applicant_id       BIGINT       NOT NULL                COMMENT '申请人（→ sys_user.id）',
    purpose            VARCHAR(512) NOT NULL                COMMENT '使用事由',
    participant_count  INT          NOT NULL                COMMENT '参与人数（>0 且不超过场地容量，服务层校验）',
    start_at           DATETIME(3)  NOT NULL                COMMENT '预约开始（UTC 时间点）',
    end_at             DATETIME(3)  NOT NULL                COMMENT '预约结束（UTC 时间点，须晚于 start_at）',
    status             VARCHAR(32)  NOT NULL                COMMENT '状态：PENDING待审批/APPROVED已批准/REJECTED已驳回/CANCELLED已取消',
    endorse_teacher_id BIGINT       NULL                    COMMENT '背书老师（→ sys_user.id，学生预约链路预留，一期不使用）',
    endorse_at         DATETIME(3)  NULL                    COMMENT '背书时间（预留）',
    endorse_comment    VARCHAR(512) NULL                    COMMENT '背书意见（预留）',
    reject_reason      VARCHAR(512) NULL                    COMMENT '驳回原因',
    cancel_reason      VARCHAR(512) NULL                    COMMENT '取消原因',
    cancel_type        VARCHAR(32)  NULL                    COMMENT '取消类型：MANUAL本人/FORCE管理员强制（AUTO_EXPIRED 预留）',
    cancelled_at       DATETIME(3)  NULL                    COMMENT '取消时间（UTC）',
    create_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time        DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id     BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id     BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_lab_booking_applicant (applicant_id, create_time),
    KEY idx_lab_booking_lab_status (lab_id, status, start_at),
    CONSTRAINT chk_lab_booking_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_lab_booking_count CHECK (participant_count > 0),
    CONSTRAINT chk_lab_booking_window CHECK (start_at < end_at),
    CONSTRAINT chk_lab_booking_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT fk_lab_booking_lab FOREIGN KEY (lab_id) REFERENCES biz_space (id),
    CONSTRAINT fk_lab_booking_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地预约单表';

-- ----------------------------------------------------------------------------
-- 2. lab_slot 场地占用表（批准时写入；released_at IS NULL 的记录参与冲突判断）
-- ----------------------------------------------------------------------------
CREATE TABLE lab_slot (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id         BIGINT       NOT NULL                COMMENT '场地（→ biz_space.id）',
    booking_id     BIGINT       NOT NULL                COMMENT '占用来源预约单（→ lab_booking.id；课程排课来源待课程模块落地）',
    start_at       DATETIME(3)  NOT NULL                COMMENT '占用开始（UTC 时间点）',
    end_at         DATETIME(3)  NOT NULL                COMMENT '占用结束（UTC 时间点）',
    released_at    DATETIME(3)  NULL                    COMMENT '释放时间（NULL=未释放，未释放记录参与冲突；取消/强制结束写回）',
    release_reason VARCHAR(512) NULL                    COMMENT '释放原因（MANUAL取消/FORCE强制结束）',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_slot_booking (booking_id),
    KEY idx_lab_slot_conflict (lab_id, released_at, start_at, end_at),
    CONSTRAINT chk_lab_slot_window CHECK (start_at < end_at),
    CONSTRAINT chk_lab_slot_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT fk_lab_slot_lab FOREIGN KEY (lab_id) REFERENCES biz_space (id),
    CONSTRAINT fk_lab_slot_booking FOREIGN KEY (booking_id) REFERENCES lab_booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地占用表（预约/排课统一占用，释放即失效）';

-- ============================================================================
-- 初始数据
-- ============================================================================

-- 演示管理员密码：Admin@123456（仅联调与验收演示用；生产部署前必须替换
-- 并强制首登修改，见 sys_rbac.sql 初始数据说明）
UPDATE sys_user
SET password_hash = '$2a$10$aiic6ix1Y3yO.3iK0psmU.qqkpWEXMNyI0tYUFcZ4.KfXR.txw8ke'
WHERE id = 1;

-- ----------------------------------------------------------------------------
-- 权限初始数据（3001-3007，标识规范 模块:资源:操作，module_code='booking'）
-- 3005-3007 为登记项：背书链/强制结束/使用台账一期未实现，随对应迭代启用。
-- ----------------------------------------------------------------------------
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
VALUES
(3001, 'booking:lab:create',  '场地预约申请', 'booking', 1),
(3002, 'booking:lab:cancel',  '场地预约撤销', 'booking', 1),
(3003, 'booking:lab:list',    '场地预约记录', 'booking', 1),
(3004, 'booking:lab:approve', '场地预约审批', 'booking', 1),
(3005, 'booking:lab:endorse', '学生预约背书', 'booking', 1),
(3006, 'booking:lab:finish',  '场地强制结束', 'booking', 1),
(3007, 'booking:usage:list',  '场地使用台账', 'booking', 1);

-- ----------------------------------------------------------------------------
-- 菜单初始数据（3201-3205）：
--   ADMIN：/admin/booking（场地预约 + 预约审批，挂管理端布局）；
--   TEACHER：/booking（场地预约，挂前台布局，首页快捷入口同权）。
-- component_key 与前端本地组件白名单一致（router/dynamic.js）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key,
                      permission_id, menu_type, sort_order, enabled)
VALUES
(3201, NULL, 'admin_booking', '预约管理', '/admin/booking',         NULL,                          NULL, 'DIRECTORY', 20, 1),
(3202, 3201, 'booking_labs',   '场地预约', '/admin/booking/labs',    'admin/booking/BookingList',    3003, 'PAGE',      1,  1),
(3203, 3201, 'booking_audit',  '预约审批', '/admin/booking/approval', 'admin/booking/BookingApproval', 3004, 'PAGE',     2,  1),
(3204, NULL, 'booking',        '预约管理', '/booking',               NULL,                          NULL, 'DIRECTORY', 30, 1),
(3205, 3204, 'booking_mine',   '场地预约', '/booking/labs',          'booking/BookingList',         3003, 'PAGE',      1,  1);

-- 角色权限绑定：ADMIN 发起/撤销/查询/审批；TEACHER 发起/撤销/查询本人
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
VALUES
(3101, 1, 3001, 1), (3102, 1, 3002, 1), (3103, 1, 3003, 1), (3104, 1, 3004, 1), (3105, 1, 3007, 1),
(3106, 2, 3001, 1), (3107, 2, 3002, 1), (3108, 2, 3003, 1);

-- 角色菜单绑定：ADMIN 预约管理目录+两页；TEACHER 前台预约入口
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
VALUES
(3121, 1, 3201, 1), (3122, 1, 3202, 1), (3123, 1, 3203, 1),
(3124, 2, 3204, 1), (3125, 2, 3205, 1);

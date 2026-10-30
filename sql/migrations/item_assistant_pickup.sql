-- item 模块：老师助理授权与原申领单学生代领（38表 -> 39表，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 不生成代领子单，不改变既有审核、预占、实发和归还状态机。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_assistant_pickup //
CREATE PROCEDURE migrate_item_assistant_pickup()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') NOT IN (38, 39) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移前置校验失败：目标库必须为38或39表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_claim')
     OR NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_document') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移前置校验失败：物料申领或库存单据表不存在';
  END IF;

  CREATE TABLE IF NOT EXISTS material_assistant_grant (
    id BIGINT NOT NULL COMMENT '授权ID（雪花ID）',
    teacher_id BIGINT NOT NULL COMMENT '授权教师',
    student_id BIGINT NOT NULL COMMENT '被授权学生',
    start_at DATETIME(3) NOT NULL COMMENT '生效时间（UTC）',
    end_at DATETIME(3) NOT NULL COMMENT '失效时间（UTC，不含）',
    revoked_at DATETIME(3) DEFAULT NULL COMMENT '撤销时间（UTC）',
    revoked_by BIGINT DEFAULT NULL COMMENT '撤销人',
    reason VARCHAR(512) DEFAULT NULL COMMENT '授权说明',
    create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '授权历史恒保留，固定0',
    create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
    update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
    PRIMARY KEY (id),
    KEY idx_material_assistant_grant_teacher (teacher_id, start_at, end_at),
    KEY idx_material_assistant_grant_student (student_id, start_at, end_at),
    KEY idx_material_assistant_grant_revoked_by (revoked_by),
    CONSTRAINT fk_material_assistant_grant_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_material_assistant_grant_student FOREIGN KEY (student_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_material_assistant_grant_revoked_by FOREIGN KEY (revoked_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_material_assistant_grant_window CHECK (start_at < end_at),
    CONSTRAINT chk_material_assistant_grant_people CHECK (teacher_id <> student_id),
    CONSTRAINT chk_material_assistant_grant_revoke CHECK (revoked_at IS NULL OR revoked_by IS NOT NULL),
    CONSTRAINT chk_material_assistant_grant_deleted CHECK (deleted = 0)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师学生助理授权表';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim' AND column_name = 'pickup_mode') THEN
    ALTER TABLE material_claim ADD COLUMN pickup_mode VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT 'SELF本人领取/ASSISTANT学生代领' AFTER cancelled_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim' AND column_name = 'delegate_student_id') THEN
    ALTER TABLE material_claim ADD COLUMN delegate_student_id BIGINT DEFAULT NULL COMMENT '被委派学生' AFTER pickup_mode;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim' AND column_name = 'delegate_grant_id') THEN
    ALTER TABLE material_claim ADD COLUMN delegate_grant_id BIGINT DEFAULT NULL COMMENT '委派时授权快照ID' AFTER delegate_student_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim' AND column_name = 'delegated_at') THEN
    ALTER TABLE material_claim ADD COLUMN delegated_at DATETIME(3) DEFAULT NULL COMMENT '最近委派时间（UTC）' AFTER delegate_grant_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_document' AND column_name = 'pickup_user_id') THEN
    ALTER TABLE material_document ADD COLUMN pickup_user_id BIGINT DEFAULT NULL COMMENT '实际领取人，仅申领出库及其归还有值' AFTER responsible_teacher_id;
  END IF;

  UPDATE material_claim SET pickup_mode = 'SELF' WHERE pickup_mode IS NULL OR pickup_mode NOT IN ('SELF', 'ASSISTANT');
  UPDATE material_document d
  LEFT JOIN material_claim c ON c.id = d.business_chain_id
  SET d.pickup_user_id = COALESCE(d.responsible_teacher_id, c.applicant_id)
  WHERE d.pickup_user_id IS NULL AND d.business_scene IN ('CLAIM_ISSUE', 'CLAIM_RETURN');

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim' AND index_name = 'idx_material_claim_delegate') THEN
    ALTER TABLE material_claim ADD KEY idx_material_claim_delegate (delegate_student_id, status, booking_start_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_document' AND index_name = 'idx_material_document_pickup_user') THEN
    ALTER TABLE material_document ADD KEY idx_material_document_pickup_user (pickup_user_id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim' AND constraint_name = 'fk_material_claim_delegate_student') THEN
    ALTER TABLE material_claim ADD CONSTRAINT fk_material_claim_delegate_student
      FOREIGN KEY (delegate_student_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim' AND constraint_name = 'fk_material_claim_delegate_grant') THEN
    ALTER TABLE material_claim ADD CONSTRAINT fk_material_claim_delegate_grant
      FOREIGN KEY (delegate_grant_id) REFERENCES material_assistant_grant (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_document' AND constraint_name = 'fk_material_document_pickup_user') THEN
    ALTER TABLE material_document ADD CONSTRAINT fk_material_document_pickup_user
      FOREIGN KEY (pickup_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim' AND constraint_name = 'chk_material_claim_pickup') THEN
    ALTER TABLE material_claim ADD CONSTRAINT chk_material_claim_pickup CHECK (
      (pickup_mode = 'SELF' AND delegate_student_id IS NULL AND delegate_grant_id IS NULL AND delegated_at IS NULL)
      OR (pickup_mode = 'ASSISTANT' AND delegate_student_id IS NOT NULL AND delegate_grant_id IS NOT NULL AND delegated_at IS NOT NULL)
    );
  END IF;

  INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
  (4028, 'item:assistant:manage', '本人助理授权管理', 'item', 1),
  (4029, 'item:claim:delegate', '本人申领委派代领', 'item', 1),
  (4030, 'item:pickup:list', '本人学生代领查询', 'item', 1);

  INSERT IGNORE INTO sys_menu
  (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
  (4113, 4110, 'item_pickup_center', '我的代领', '/item/pickups', 'ItemPickupCenter', 4030, 'PAGE', 3, 1);

  INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
  (4241, 2, 4028, 1), (4242, 2, 4029, 1), (4243, 3, 4030, 1);
  INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
  (4311, 3, 4110, 1), (4312, 3, 4113, 1);

  DELETE rp FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.role_id = 5 AND p.module_code = 'item';
  DELETE FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (4100, 4101, 4110, 4111, 4112, 4113);

  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 39 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移完成校验失败：目标库必须为39表';
  END IF;
  IF (SELECT COUNT(*) FROM sys_permission WHERE module_code = 'item' AND id BETWEEN 4001 AND 4030) <> 30 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移完成校验失败：item权限必须为30项';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 1 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 22
     OR (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 2 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 12
     OR (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 3 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 1
     OR EXISTS (SELECT 1 FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 5 AND p.module_code = 'item') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移完成校验失败：角色权限绑定不正确';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 3 AND menu_id IN (4110, 4113) AND enabled = 1 AND deleted = 0) <> 2
     OR EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id IN (4100, 4101, 4111, 4112))
     OR EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (4100, 4101, 4110, 4111, 4112, 4113)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学生代领迁移完成校验失败：角色菜单绑定不正确';
  END IF;
END //
CALL migrate_item_assistant_pickup() //
DROP PROCEDURE migrate_item_assistant_pickup //
DELIMITER ;

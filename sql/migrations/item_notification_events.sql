-- item 模块：申领审批、批准、归还提醒和学生代领通知（40表不变，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 历史逾期通知保留；学生新增通知查询与已读权限。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_notification_events //
CREATE PROCEDURE migrate_item_notification_events()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移前置校验失败：目标库必须为40表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_notification') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移前置校验失败：material_notification不存在';
  END IF;

  ALTER TABLE material_notification
    MODIFY COLUMN material_document_id BIGINT DEFAULT NULL COMMENT '未还出库单ID，非逾期通知可空',
    MODIFY COLUMN notification_type VARCHAR(32) NOT NULL COMMENT '通知类型：审批/批准/归还提醒/代领/逾期',
    MODIFY COLUMN notification_stage VARCHAR(32) NOT NULL COMMENT 'IMMEDIATE/DAILY/TWO_HOURLY';

  ALTER TABLE material_notification DROP CHECK chk_material_notification_type;
  ALTER TABLE material_notification
    ADD CONSTRAINT chk_material_notification_type
    CHECK (notification_type IN ('CLAIM_REVIEW_PENDING', 'CLAIM_APPROVED', 'RETURN_REMINDER',
                                 'PICKUP_DELEGATED', 'OVERDUE_UNRETURNED'));

  ALTER TABLE material_notification DROP CHECK chk_material_notification_stage;
  ALTER TABLE material_notification
    ADD CONSTRAINT chk_material_notification_stage
    CHECK (notification_stage IN ('IMMEDIATE', 'DAILY', 'TWO_HOURLY'));

  ALTER TABLE material_notification DROP CHECK chk_material_notification_reminder;
  ALTER TABLE material_notification
    ADD CONSTRAINT chk_material_notification_reminder
    CHECK ((notification_stage IN ('IMMEDIATE', 'TWO_HOURLY') AND reminder_date IS NULL)
           OR (notification_stage = 'DAILY' AND reminder_date IS NOT NULL));

  INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
  (4244, 3, 4026, 1), (4245, 3, 4027, 1);

  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_notification'
        AND column_name = 'material_document_id' AND is_nullable = 'YES') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移完成校验失败：出库单ID未放开为空';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission rp
      JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 3 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移完成校验失败：学生通知权限不完整';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission
      WHERE id = 4244 AND role_id = 3 AND permission_id = 4026 AND enabled = 1 AND deleted = 0) <> 1
     OR (SELECT COUNT(*) FROM sys_role_permission
      WHERE id = 4245 AND role_id = 3 AND permission_id = 4027 AND enabled = 1 AND deleted = 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '通知事件迁移完成校验失败：学生通知绑定编号不一致';
  END IF;
END //
CALL migrate_item_notification_events() //
DROP PROCEDURE migrate_item_notification_events //
DELIMITER ;

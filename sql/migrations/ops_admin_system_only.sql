-- roles 模块：OPS_ADMIN 收敛为系统、人员与权限管理（幂等，可重复执行）
-- 版本：2026-10-28。
-- 仅允许现行 new_system/new_system_test；全新空库无需执行，基线已包含本口径。
-- 收回 space、booking、item 等业务权限和菜单，同时补齐 system% 菜单（含人员管理父目录）。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_ops_admin_system_only //
CREATE PROCEDURE migrate_ops_admin_system_only()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OPS_ADMIN迁移仅允许new_system/new_system_test';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 5 AND role_code = 'OPS_ADMIN') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OPS_ADMIN迁移前置校验失败：角色不存在';
  END IF;

  DELETE FROM sys_role_permission WHERE role_id = 5;
  DELETE FROM sys_role_menu WHERE role_id = 5;

  INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
  SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
  FROM sys_permission
  WHERE enabled = 1 AND deleted = 0 AND module_code = 'system';

  INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
  SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
  FROM sys_menu
  WHERE enabled = 1 AND deleted = 0 AND menu_code LIKE 'system%';

  IF EXISTS (SELECT 1 FROM sys_role_permission rp
             JOIN sys_permission p ON p.id = rp.permission_id
             WHERE rp.role_id = 5 AND p.module_code <> 'system') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OPS_ADMIN迁移失败：仍存在业务权限';
  END IF;
  IF EXISTS (SELECT 1 FROM sys_role_menu rm
             JOIN sys_menu m ON m.id = rm.menu_id
             WHERE rm.role_id = 5 AND m.menu_code NOT LIKE 'system%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OPS_ADMIN迁移失败：仍存在业务菜单';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission rp
      JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 5 AND rp.enabled = 1 AND rp.deleted = 0
        AND p.module_code = 'system' AND p.enabled = 1 AND p.deleted = 0)
     <> (SELECT COUNT(*) FROM sys_permission
         WHERE module_code = 'system' AND enabled = 1 AND deleted = 0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'OPS_ADMIN迁移失败：系统权限未完整绑定';
  END IF;
END //
CALL migrate_ops_admin_system_only() //
DROP PROCEDURE migrate_ops_admin_system_only //
DELIMITER ;

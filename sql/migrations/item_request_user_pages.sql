-- ============================================================================
-- 物料申领用户端初版：权限补绑 + 菜单注册（2026-10-24）
-- ----------------------------------------------------------------------------
-- 背景：老师/学生的物料申领与领用初版上线。流程定稿：
--   * 学生申领：必填审批教师，落 DRAFT（待老师初审）→ 老师初审通过 → 管理员终审；
--   * 老师申领：跳过同行初审直落 TEACHER_APPROVED，由管理员终审；
--   * 初版不预约时段，offeringId/场地预约/课次均不填（定额快照为空）。
-- 变更内容：
--   1) 权限补绑：TEACHER(2) 补 item:request:create(123)/cancel(125)；STUDENT(3) 补 cancel(125)；
--   2) 菜单 222「物料申领」（用户端，绑角色 2/3）、223「申领初审」（老师端，绑角色 2）；
--   3) 关联表 id 动态顺延分配，INSERT IGNORE + NOT EXISTS 幂等可重放。
-- 执行方式（PowerShell 示例）：
--   mysql --default-character-set=utf8mb4 -h"127.0.0.1" -P3306 -uroot -p"..." lab_v0_1 < sql/migrations/item_request_user_pages.sql
-- ============================================================================
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- 1. 权限补绑（幂等：已绑定则跳过）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY t.role_id, t.permission_id), t.role_id, t.permission_id, 1
FROM (
    SELECT 2 AS role_id, 123 AS permission_id
    UNION ALL SELECT 2, 125
    UNION ALL SELECT 3, 125
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = t.role_id AND rp.permission_id = t.permission_id AND rp.deleted = 0);

-- 2. 用户端页面菜单（幂等：先清同 ID 再插入）
DELETE FROM sys_role_menu WHERE menu_id IN (222, 223) AND role_id <> 5;
DELETE FROM sys_menu WHERE id IN (222, 223);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(222, NULL, 'C_ITEM_MY_REQUEST',     '物料申领', '/item/requests/my',     'ItemMyRequests',      123, 'PAGE', 25, 1),
(223, NULL, 'C_ITEM_TEACHER_REVIEW', '申领初审', '/item/requests/teacher', 'ItemTeacherApprove',  167, 'PAGE', 26, 1);

-- 3. 菜单绑定：物料申领 → 老师(2)/学生(3)；申领初审 → 老师(2)
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY t.menu_id, t.role_id), t.role_id, t.menu_id, 1
FROM (
    SELECT 2 AS role_id, 222 AS menu_id
    UNION ALL SELECT 3, 222
    UNION ALL SELECT 2, 223
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = t.role_id AND rm.menu_id = t.menu_id AND rm.deleted = 0);

-- 4. 断言验证（执行完成后核对输出应全为 OK）
SELECT IF(COUNT(*) = 3, 'OK  权限补绑 = 3（教师 create/cancel、学生 cancel）', CONCAT('FAIL 权限补绑 = ', COUNT(*))) AS result
FROM sys_role_permission
WHERE deleted = 0 AND enabled = 1
  AND ((role_id = 2 AND permission_id IN (123, 125)) OR (role_id = 3 AND permission_id = 125));

SELECT IF(COUNT(*) = 2, 'OK  菜单注册 = 2（222 物料申领 / 223 申领初审）', CONCAT('FAIL 菜单注册 = ', COUNT(*))) AS result
FROM sys_menu WHERE id IN (222, 223) AND deleted = 0 AND enabled = 1;

SELECT IF(COUNT(*) = 3, 'OK  菜单绑定 = 3（222→2/3，223→2）', CONCAT('FAIL 菜单绑定 = ', COUNT(*))) AS result
FROM sys_role_menu
WHERE deleted = 0 AND enabled = 1
  AND ((menu_id = 222 AND role_id IN (2, 3)) OR (menu_id = 223 AND role_id = 2));

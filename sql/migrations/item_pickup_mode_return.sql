-- ============================================================================
-- 申领单领取方式 + 出库/归还入口 + 库存余额可见（2026-10-24）
-- ----------------------------------------------------------------------------
-- 业务背景：
--   1) 库存余额对老师/学生开放查看（item:balance:list 补绑 2/3），物料剩余量全员可见；
--   2) 老师申领单新增领取方式 pickup_mode：SELF_PICKUP 本人领取（默认）/ PROXY_PICKUP
--      学生代理领取；仅标记「学生代理领取」且老师已审核的单才出现在代领源单中；
--   3) 申领单终审批准后提供「出库领取」（item:request:issue 补绑老师）与「归还」
--      （新权限 item:request:return=166）入口，出库/归还均生成库存单据留痕。
-- 幂等：ALTER 不可重复执行；种子先清同 ID 再插入；关联绑定动态 ID + NOT EXISTS。
-- ============================================================================
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- 1. material_request 加领取方式列
ALTER TABLE material_request
    ADD COLUMN pickup_mode VARCHAR(32) NOT NULL DEFAULT 'SELF_PICKUP'
        COMMENT '领取方式：SELF_PICKUP 本人领取 / PROXY_PICKUP 学生代理领取（仅老师本人申领单可选，代领源单过滤条件）'
        AFTER purpose,
    ADD CONSTRAINT chk_item_request_pickup CHECK (pickup_mode IN ('SELF_PICKUP', 'PROXY_PICKUP'));

-- 2. 新权限：申领单归还（登记 RETURN_IN 归还单）
DELETE FROM sys_role_permission WHERE permission_id = 166 AND role_id <> 5;
DELETE FROM sys_menu WHERE id = 224;
DELETE FROM sys_permission WHERE id = 166;

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(166, 'item:request:return', '申领单归还', 'item', 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(224, 180, 'item:request:return', '申领单归还', NULL, NULL, 166, 'ACTION', 7, 1);

-- 3. 角色权限补绑（幂等：动态 ID + NOT EXISTS）
--    库存余额查看 118 → 老师(2)/学生(3)；申领实发 126 → 老师(2)；归还 166 → 管理员(1)/老师(2)/学生(3)
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY t.role_id, t.permission_id), t.role_id, t.permission_id, 1
FROM (
    SELECT 2 AS role_id, 118 AS permission_id
    UNION ALL SELECT 3, 118
    UNION ALL SELECT 2, 126
    UNION ALL SELECT 1, 166
    UNION ALL SELECT 2, 166
    UNION ALL SELECT 3, 166
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = t.role_id AND rp.permission_id = t.permission_id AND rp.deleted = 0);

-- 4. 菜单绑定：归还动作 224 → 管理员(1)
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 224, 1
FROM (SELECT 1 AS role_id) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 1 AND rm.menu_id = 224 AND rm.deleted = 0);

-- 5. OPS_ADMIN(5) 补绑新权限与菜单（其全量绑定为初始化时快照，新增项需顺延）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + 1, 5, 166, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = 166 AND rp.deleted = 0);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + 1, 5, 224, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = 224 AND rm.deleted = 0);

-- 6. 断言验证（执行完成后核对输出应全为 OK）
SELECT IF(COUNT(*) = 1, 'OK  pickup_mode 列就绪', 'FAIL pickup_mode') AS result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_request' AND COLUMN_NAME = 'pickup_mode';

SELECT IF(COUNT(*) = 2, 'OK  库存余额开放 = 2（老师/学生）', CONCAT('FAIL 余额开放 = ', COUNT(*))) AS result
FROM sys_role_permission
WHERE deleted = 0 AND enabled = 1 AND permission_id = 118 AND role_id IN (2, 3);

SELECT IF(COUNT(*) = 1, 'OK  实发补绑老师 = 1', 'FAIL 实发补绑') AS result
FROM sys_role_permission
WHERE deleted = 0 AND enabled = 1 AND permission_id = 126 AND role_id = 2;

SELECT IF(COUNT(*) = 4, 'OK  归还权限 = 4（1/2/3/5）', CONCAT('FAIL 归还权限 = ', COUNT(*))) AS result
FROM sys_role_permission
WHERE deleted = 0 AND enabled = 1 AND permission_id = 166 AND role_id IN (1, 2, 3, 5);

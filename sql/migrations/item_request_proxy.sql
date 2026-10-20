-- ============================================================================
-- 申领两级审核 + 学生代理代领 增量迁移（2026-10-19）
-- ----------------------------------------------------------------------------
-- 适用：已按 lab_management_all.sql（2026-10-17 产品定稿整合版）初始化的运行库；
--      新库直接执行 lab_management_all.sql 正本（已含本脚本全部变更），勿重复执行。
-- 正本对应段：
--   * 5.1 material_request 建表（request_type / source_request_id 列与约束）
--   * 5.2 material_request_line 建表（source_line_id 列与约束）
--   * 5.11 权限 167 / 菜单 215 / 绑定 263-266、365-369
-- 业务规则：
--   * SELF 本人申领：DRAFT →（老师审核）TEACHER_APPROVED →（管理员终审）APPROVED；
--   * PROXY 学生代理代领：只能引用老师已审核通过的源申领单（request_type=SELF、
--     status=TEACHER_APPROVED），按源行剩余可代领量领取，创建即 TEACHER_APPROVED，
--     绑定责任老师并留档，直接交由管理员终审；不能凭空新建空白申领。
-- 执行方式（PowerShell 示例）：
--   mysql --default-character-set=utf8mb4 -h"127.0.0.1" -P3306 -uroot -p"..." < sql/migrations/item_request_proxy.sql
-- 幂等性：ALTER/ADD CONSTRAINT 不可重复执行，请一次性执行成功；
--        种子 INSERT 前先 DELETE 同 ID 记录（可安全重放）。
-- ============================================================================
SET time_zone = '+00:00';
SET NAMES utf8mb4;
USE lab_management;

-- 1. material_request 表：申领类型 + 源申领单 + 状态机扩展
ALTER TABLE material_request
    ADD COLUMN request_type VARCHAR(32) NOT NULL DEFAULT 'SELF'
        COMMENT '申领类型：SELF 本人申领 / PROXY 学生代理代领（2026-10-19 两级审核定稿）'
        AFTER purpose,
    ADD COLUMN source_request_id BIGINT NULL
        COMMENT '源申领单（代领时引用的老师已审核通过的申领单 → material_request.id）'
        AFTER request_type,
    ADD KEY idx_item_request_source (source_request_id),
    ADD CONSTRAINT chk_item_request_type CHECK (request_type IN ('SELF', 'PROXY')),
    ADD CONSTRAINT chk_item_request_source CHECK (
        (request_type = 'SELF') OR (source_request_id IS NOT NULL)
    );

ALTER TABLE material_request DROP CONSTRAINT chk_item_request_status;
ALTER TABLE material_request
    ADD CONSTRAINT chk_item_request_status CHECK (status IN
        ('DRAFT', 'TEACHER_APPROVED', 'PENDING', 'APPROVED',
         'PARTIALLY_FULFILLED', 'FULFILLED', 'REJECTED', 'CANCELLED'));

ALTER TABLE material_request
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        COMMENT '状态：DRAFT/TEACHER_APPROVED/APPROVED/PARTIALLY_FULFILLED/FULFILLED/REJECTED/CANCELLED；SELF 经 DRAFT→TEACHER_APPROVED（老师审核）→APPROVED（管理员终审）；PROXY 创建即 TEACHER_APPROVED，直接管理员终审';

ALTER TABLE material_request
    ADD CONSTRAINT fk_item_request_source
        FOREIGN KEY (source_request_id) REFERENCES material_request (id);

-- 2. material_request_line 表：代领行引用源申领行
ALTER TABLE material_request_line
    ADD COLUMN source_line_id BIGINT NULL
        COMMENT '源申领行（代领时引用的老师已审核单行 → material_request_line.id）'
        AFTER requires_dual_sign,
    ADD KEY idx_item_request_line_source (source_line_id),
    ADD CONSTRAINT fk_item_request_line_source
        FOREIGN KEY (source_line_id) REFERENCES material_request_line (id);

-- 3. 存量单据回填：两级审核上线前既有单据全部为本人申领语义
UPDATE material_request SET request_type = 'SELF' WHERE request_type IS NULL;

-- 4. 权限 / 菜单 / 角色绑定种子（幂等：先清同 ID 记录再插入）
--    注意：OPS_ADMIN(5) 由 sys_ops_role.sql 动态绑定了库内全部权限/菜单，
--    重放时若其已绑定 167/215，须先解除指向绑定，否则触发外键 1451。
DELETE FROM sys_role_menu WHERE id IN (365, 366, 367, 368, 369);
DELETE FROM sys_role_permission WHERE id IN (263, 264, 265, 266);
DELETE FROM sys_role_menu WHERE role_id = 5 AND menu_id = 215;
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 167;
DELETE FROM sys_menu WHERE id = 215;
DELETE FROM sys_permission WHERE id = 167;

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(167, 'item:request:teacher_approve', '申领单老师审核', 'item', 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(215, 180, 'item:request:teacher_approve', '申领单老师审核', NULL, NULL, 167, 'ACTION', 6, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(263, 2, 122, 1), (264, 2, 167, 1),
(265, 3, 123, 1), (266, 1, 167, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(365, 2, 152, 1), (366, 2, 215, 1),
(367, 3, 152, 1), (368, 3, 185, 1),
(369, 1, 215, 1);

-- 4b. OPS_ADMIN(5) 补绑（其全量动态绑定在上方解绑后不会自动恢复；
--     id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id = 167
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id = 215
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);

-- 5. 断言验证（执行完成后核对输出应全为 OK）
SELECT IF(COUNT(*) = 2, 'OK  request_type 枚举列就绪', 'FAIL request_type') AS result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'lab_management' AND TABLE_NAME = 'material_request'
  AND COLUMN_NAME IN ('request_type', 'source_request_id');

SELECT IF(COUNT(*) = 1, 'OK  source_line_id 就绪', 'FAIL source_line_id') AS result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'lab_management' AND TABLE_NAME = 'material_request_line'
  AND COLUMN_NAME = 'source_line_id';

SELECT IF(COUNT(*) = 4, 'OK  角色绑定 = 4（教师 122/167、学生 123、管理员 167）', CONCAT('FAIL 绑定 = ', COUNT(*))) AS result
FROM sys_role_permission WHERE id IN (263, 264, 265, 266);

SELECT IF(COUNT(*) = 5, 'OK  菜单绑定 = 5', CONCAT('FAIL 菜单绑定 = ', COUNT(*))) AS result
FROM sys_role_menu WHERE id IN (365, 366, 367, 368, 369);

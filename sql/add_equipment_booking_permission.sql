-- ============================================================================
-- 迁移：三权级设备使用申请与两级审批（2026-10-19 定稿）
-- ============================================================================
-- 方案见 docs/固定设备三权级流程清单.md：学生/教师提交设备使用申请，
-- 学生单经教师初审、贵重设备转管理员终审；管理员登记借出发放与归还。
-- 复用保留表结构（equipment_booking / equipment_usage / biz_document /
-- document_event），零改表。
--
-- 编号规则（两库 dev/test 实测空闲段，与 lab_management_all.sql 种子不冲突）：
--   * 权限 170-177（equipment 段顺延；原 162-169 与物料导入导出/盘点/审核段
--     160-167 冲突，2026-10-19 让号，与种子 3.7 节定稿一致）
--   * 菜单 218-221（两库 MAX(id)=174；218/219/220 用户端挂 NULL 根，
--     221 管理端审批页 route_path 以 /admin 开头亦挂 NULL 根，与种子库
--     扁平结构一致，实库侧栏渲染为顶级入口；原 208-211 与物料导入导出
--     菜单段 206-209 冲突，同步让号）
--   * 权限绑定 531-543（两库 MAX(id)=530）
--   * 菜单绑定 616-621（dev 实测 MAX(id)=615，613-615 段历史上被占用）
--
-- 同时预置教师/学生演示账号（首登须匹配 sys_person_roster 预录名单的
-- 契约在数据层满足：roster 预录 + sys_user 建档 + 角色绑定）：
--   teacher1 / student1，密码与 admin 相同（admin123，BCrypt，仅开发演示）。
-- 脚本按固定 ID 插入，可重复执行：已存在时跳过对应行。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 权限（equipment 段 170-177）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 170, 'equipment:booking:apply',         '设备使用申请',     'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 170);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 171, 'equipment:booking:list_own',      '本人设备申请查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 171);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 172, 'equipment:booking:cancel',        '撤销本人设备申请', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 172);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 173, 'equipment:booking:teacher_review', '设备申请教师初审', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 173);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 174, 'equipment:booking:admin_review',  '设备申请终审代批', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 174);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 175, 'equipment:booking:checkout',      '设备借出发放登记', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 175);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 176, 'equipment:booking:return',        '设备归还登记',     'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 176);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 177, 'equipment:booking:list_all',      '设备申请全量查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 177);

-- 2. 菜单（218-221，均挂 NULL 根；PAGE 层 menu_code 用 C_ 前缀种子规范）
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 218, NULL, 'C_EQUIPMENT_DIRECTORY',    '设备目录',     '/equipment/directory',           'EquipmentDirectory',        149, 'PAGE', 21, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 218);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 219, NULL, 'C_EQUIPMENT_MY_BOOKING',   '我的设备申请', '/equipment/bookings/my',          'MyEquipmentBookings',       171, 'PAGE', 22, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 219);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 220, NULL, 'C_EQUIPMENT_TEACHER_REVIEW', '设备申请初审', '/equipment/bookings/teacher',     'EquipmentTeacherApprove',   173, 'PAGE', 23, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 220);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 221, NULL, 'C_EQUIPMENT_BOOKING_ADMIN', '设备使用审批', '/admin/equipment/bookings',       'EquipmentBookingManage',    174, 'PAGE', 24, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 221);

-- 3. 角色权限绑定（531-543）
--    ADMIN：终审/借出/归还/全量；TEACHER/STUDENT：目录 + 申请 + 本人 + 撤销；
--    TEACHER 另有初审。149（台账查询）一并开放给教师/学生，
--    服务端按「无维护权限收敛为在架目录视图」下发只读数据。
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 531, 1, 174, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 531);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 532, 1, 175, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 532);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 533, 1, 176, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 533);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 534, 1, 177, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 534);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 535, 2, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 535);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 536, 2, 170, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 536);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 537, 2, 171, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 537);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 538, 2, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 538);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 539, 2, 173, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 539);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 540, 3, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 540);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 541, 3, 170, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 541);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 542, 3, 171, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 542);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 543, 3, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 543);

-- 4. 角色菜单绑定（616-621）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 616, 1, 221, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 616);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 617, 2, 218, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 617);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 618, 2, 219, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 618);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 619, 2, 220, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 619);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 620, 3, 218, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 620);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 621, 3, 219, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 621);

-- 5. 教师/学生演示账号（仅开发/测试环境；生产库不得执行本段）
--    密码与 admin 相同：admin123 的 BCrypt 哈希（见 计算机网络实训室种子数据.sql）
-- 5.1 预录名单（RBAC 定稿：学生/老师首登须匹配 sys_person_roster）
INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id, enabled)
SELECT 101, 'T001', 'TEACHER', '王老师', '13800000001', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = 101);

INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id, enabled)
SELECT 102, 'S001', 'STUDENT', '张同学', '13900000001', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = 102);

-- 5.2 用户建档（id 固定小 ID 2/3；密码 admin123，部署前必须替换）
INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash, password_changed_at)
SELECT 2, 101, 'TEACHER', '王老师', 'teacher1', '13800000001', 'T001', 1,
       '$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 2);

INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash, password_changed_at)
SELECT 3, 102, 'STUDENT', '张同学', 'student1', '13900000001', 'S001', 1,
       '$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 3);

-- 5.3 角色绑定（TEACHER=2 / STUDENT=3）
INSERT INTO sys_user_role (id, user_id, role_id, enabled)
SELECT 2, 2, 2, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE id = 2);

INSERT INTO sys_user_role (id, user_id, role_id, enabled)
SELECT 3, 3, 3, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE id = 3);

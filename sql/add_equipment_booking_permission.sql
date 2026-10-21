-- ============================================================================
-- 迁移：三权级设备使用申请与两级审批（2026-10-19 定稿）
-- ============================================================================
-- 方案见 docs/固定设备三权级流程清单.md：学生/教师提交设备使用申请，
-- 学生单经教师初审、贵重设备转管理员终审；管理员登记借出发放与归还。
-- 复用保留表结构（equipment_booking / equipment_usage / biz_document /
-- document_event），零改表。
--
-- 编号规则（2026-10-20 二次对齐远端 dev/database@9f757af，避开其占用）：
--   远端最新种子（整合 material-manage 分支后）已占：
--     * 权限 item 160-165/167（批量导入导出/余额/老师审核）、equipment 168/169（报修）、
--       141-154 设备基础、155-159 场地、3001-3005 场地预约
--     * 菜单 item 200-209/212-215、equipment 210/211（设备管理/设备登记）、151-199 item
--   * 权限 172-179（170/171 为台账导入导出，本组顺延；160-169 整段让出）
--   * 菜单 216-219（215 之后空闲；216/217/218 用户端挂 NULL 根，
--     219 管理端审批页 route_path 以 /admin 开头亦挂 NULL 根，与种子库
--     扁平结构一致，实库侧栏渲染为顶级入口）
--   * 权限绑定 545-557（远端 role_permission 515-532 已占、533/534 导入导出；545 起空闲）
--   * 菜单绑定 616-621（远端 608/610/1101-1105/3001-3005 + 622/623 导入导出；616-621 空闲）
--   * 演示账号 sys_user id=10/11（远端 sys_ops_role.sql 预置 opsadmin id=2，2-9 段
--     保留给运维/后续预置账号；sys_user_role 同步 10/11）
--
-- 同时预置教师/学生演示账号（首登须匹配 sys_person_roster 预录名单的
-- 契约在数据层满足：roster 预录 + sys_user 建档 + 角色绑定）：
--   teacher1 / student1，密码与 admin 相同（admin123，BCrypt，仅开发演示）。
-- 脚本按固定 ID 插入，可重复执行：已存在时跳过对应行。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 权限（equipment 段 172-179；160-169 整段已让给远端 item/报修）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 172, 'equipment:booking:apply',         '设备使用申请',     'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 172);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 173, 'equipment:booking:list_own',      '本人设备申请查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 173);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 174, 'equipment:booking:cancel',        '撤销本人设备申请', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 174);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 175, 'equipment:booking:teacher_review', '设备申请教师初审', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 175);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 176, 'equipment:booking:admin_review',  '设备申请终审代批', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 176);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 177, 'equipment:booking:checkout',      '设备借出发放登记', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 177);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 178, 'equipment:booking:return',        '设备归还登记',     'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 178);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 179, 'equipment:booking:list_all',      '设备申请全量查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 179);

-- 2. 菜单（216-219，均挂 NULL 根；PAGE 层 menu_code 用 C_ 前缀种子规范）
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 216, NULL, 'C_EQUIPMENT_DIRECTORY',    '设备目录',     '/equipment/directory',           'EquipmentDirectory',        149, 'PAGE', 21, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 216);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 217, NULL, 'C_EQUIPMENT_MY_BOOKING',   '我的设备申请', '/equipment/bookings/my',          'MyEquipmentBookings',       173, 'PAGE', 22, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 217);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 218, NULL, 'C_EQUIPMENT_TEACHER_REVIEW', '设备申请初审', '/equipment/bookings/teacher',     'EquipmentTeacherApprove',   175, 'PAGE', 23, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 218);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 219, NULL, 'C_EQUIPMENT_BOOKING_ADMIN', '设备使用审批', '/admin/equipment/bookings',       'EquipmentBookingManage',    176, 'PAGE', 24, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 219);

-- 3. 角色权限绑定（545-557）
--    ADMIN：终审/借出/归还/全量；TEACHER/STUDENT：目录 + 申请 + 本人 + 撤销；
--    TEACHER 另有初审。149（台账查询）一并开放给教师/学生，
--    服务端按「无维护权限收敛为在架目录视图」下发只读数据。
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 545, 1, 176, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 545);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 546, 1, 177, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 546);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 547, 1, 178, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 547);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 548, 1, 179, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 548);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 549, 2, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 549);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 550, 2, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 550);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 551, 2, 173, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 551);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 552, 2, 174, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 552);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 553, 2, 175, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 553);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 554, 3, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 554);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 555, 3, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 555);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 556, 3, 173, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 556);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 557, 3, 174, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 557);

-- 4. 角色菜单绑定（616-621）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 616, 1, 219, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 616);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 617, 2, 216, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 617);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 618, 2, 217, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 618);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 619, 2, 218, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 619);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 620, 3, 216, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 620);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 621, 3, 217, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 621);

-- 5. 教师/学生演示账号（仅开发/测试环境；生产库不得执行本段）
--    密码与 admin 相同：admin123 的 BCrypt 哈希（见 计算机网络实训室种子数据.sql）
-- 5.1 预录名单（RBAC 定稿：学生/老师首登须匹配 sys_person_roster）
INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id, enabled)
SELECT 101, 'T001', 'TEACHER', '王老师', '13800000001', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = 101);

INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id, enabled)
SELECT 102, 'S001', 'STUDENT', '张同学', '13900000001', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = 102);

-- 5.2 用户建档（id 固定小 ID 10/11；2-9 保留给运维/后续预置账号；
--    密码 admin123，部署前必须替换）
INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash, password_changed_at)
SELECT 10, 101, 'TEACHER', '王老师', 'teacher1', '13800000001', 'T001', 1,
       '$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 10);

INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash, password_changed_at)
SELECT 11, 102, 'STUDENT', '张同学', 'student1', '13900000001', 'S001', 1,
       '$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 11);

-- 5.3 角色绑定（TEACHER=2 / STUDENT=3；user_role id 与用户 id 同步 10/11）
INSERT INTO sys_user_role (id, user_id, role_id, enabled)
SELECT 10, 10, 2, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE id = 10);

INSERT INTO sys_user_role (id, user_id, role_id, enabled)
SELECT 11, 11, 3, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE id = 11);

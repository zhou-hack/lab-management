-- 独立物料测试身份、RBAC、库位及固定资产种子。与 booking_data_h2.sql 组合执行。

INSERT INTO sys_user (id, user_type, account_name, phone, password_hash, person_no,
                      real_name, department_id, enabled, create_time, update_time, deleted)
VALUES
(11, 'ADMIN', 'item_admin_a', '13800000011', '$2a$10$placeholder', 'IA011', '甲库管理员', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(12, 'ADMIN', 'item_admin_b', '13800000012', '$2a$10$placeholder', 'IA012', '乙库管理员', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(13, 'ADMIN', 'item_admin_reviewer', '13800000013', '$2a$10$placeholder', 'IA013', '甲库复核员', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(21, 'TEACHER', 'item_teacher', '13800000021', '$2a$10$placeholder', 'IT021', '物料教师', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(22, 'STUDENT', 'item_student', '13800000022', '$2a$10$placeholder', 'IS022', '代领学生甲', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(23, 'TEACHER', 'item_teacher_b', '13800000023', '$2a$10$placeholder', 'IT023', '物料教师乙', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0),
(24, 'STUDENT', 'item_student_b', '13800000024', '$2a$10$placeholder', 'IS024', '代领学生乙', 1, 1,
 '2026-09-01 00:00:00', '2026-09-01 00:00:00', 0);

INSERT INTO sys_role (id, role_code, role_name, enabled) VALUES
(1, 'ADMIN', '系统管理员', 1),
(2, 'TEACHER', '教师', 1),
(3, 'STUDENT', '学生', 1);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(4001, 'item:material:list', '物料档案查询', 'item', 1),
(4002, 'item:material:manage', '物料档案维护', 'item', 1),
(4003, 'item:asset:list', '固定资产查询', 'item', 1),
(4004, 'item:asset:manage', '固定资产维护', 'item', 1),
(4005, 'item:location:list', '库位查询', 'item', 1),
(4006, 'item:location:manage', '库位及管理员维护', 'item', 1),
(4007, 'item:stock:list', '库存查询', 'item', 1),
(4008, 'item:stock:inbound', '期初补货入库', 'item', 1),
(4009, 'item:document:list', '库存单据查询', 'item', 1),
(4010, 'item:movement:list', '库存流水查询', 'item', 1),
(4011, 'item:transfer:create', '库位移动发出', 'item', 1),
(4012, 'item:transfer:receive', '移动接收与退回', 'item', 1),
(4013, 'item:settlement:create', '核销登记', 'item', 1),
(4014, 'item:settlement:review', '核销复核', 'item', 1),
(4015, 'item:overdue:list', '逾期列表', 'item', 1),
(4016, 'item:claim:list', '申领查询', 'item', 1),
(4017, 'item:claim:create', '申领创建与再次预约', 'item', 1),
(4018, 'item:claim:update', '本人申领修改', 'item', 1),
(4019, 'item:claim:cancel', '本人申领取消', 'item', 1),
(4020, 'item:claim:abandon', '放弃剩余与关闭', 'item', 1),
(4021, 'item:claim:review', '申领审核', 'item', 1),
(4022, 'item:claim:issue', '申领实发', 'item', 1),
(4023, 'item:claim:return', '申领归还', 'item', 1),
(4024, 'item:plan:list', '本人方案查询', 'item', 1),
(4025, 'item:plan:manage', '本人方案维护', 'item', 1),
(4026, 'item:notification:list', '通知查询', 'item', 1),
(4027, 'item:notification:read', '通知已读', 'item', 1),
(4028, 'item:assistant:manage', '本人助理授权管理', 'item', 1),
(4029, 'item:claim:delegate', '本人申领委派代领', 'item', 1),
(4030, 'item:pickup:list', '本人学生代领查询', 'item', 1);

INSERT INTO sys_user_role (id, user_id, role_id, enabled) VALUES
(1101, 11, 1, 1), (1102, 12, 1, 1), (1103, 13, 1, 1), (1104, 21, 2, 1),
(1105, 22, 3, 1), (1106, 23, 2, 1), (1107, 24, 3, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(5001, 1, 4001, 1), (5002, 1, 4002, 1), (5003, 1, 4003, 1),
(5004, 1, 4004, 1), (5005, 1, 4005, 1), (5006, 1, 4006, 1),
(5007, 1, 4007, 1), (5008, 1, 4008, 1), (5009, 1, 4009, 1),
(5010, 1, 4010, 1), (5011, 1, 4011, 1), (5012, 1, 4012, 1),
(5013, 1, 4013, 1), (5014, 1, 4014, 1), (5015, 1, 4015, 1),
(5016, 1, 4016, 1), (5017, 1, 4017, 1), (5018, 1, 4018, 1),
(5019, 1, 4019, 1), (5020, 1, 4020, 1), (5021, 1, 4021, 1),
(5022, 1, 4022, 1), (5023, 1, 4023, 1), (5024, 1, 4024, 1),
(5025, 1, 4025, 1), (5026, 1, 4026, 1), (5027, 1, 4027, 1),
(5101, 2, 4015, 1), (5102, 2, 4016, 1), (5103, 2, 4017, 1),
(5104, 2, 4018, 1), (5105, 2, 4019, 1), (5106, 2, 4020, 1),
(5107, 2, 4024, 1), (5108, 2, 4025, 1), (5109, 2, 4026, 1),
(5110, 2, 4027, 1), (5111, 2, 4028, 1), (5112, 2, 4029, 1),
(5113, 3, 4030, 1), (5114, 3, 4026, 1), (5115, 3, 4027, 1);

INSERT INTO stock_location (id, location_code, location_name, description, enabled) VALUES
(101, 'ITEM_A', '物料甲库', '申领与移动源库', 1),
(102, 'ITEM_B', '物料乙库', '移动目标库', 1);

INSERT INTO stock_location_admin (id, location_id, user_id) VALUES
(11101, 101, 11), (11102, 101, 13), (11103, 102, 12);

INSERT INTO material (id, material_type, track_mode, sku_code, material_name,
                      specification, base_unit, quantity_mode, enabled) VALUES
(9001, 'FIXED_ASSET', 'SERIAL', 'ITEM_ASSET_FOUR', '四台总量测试资产', '全天预占', '台', 'EXACT', 1),
(9002, 'FIXED_ASSET', 'SERIAL', 'ITEM_ASSET_FIVE', '五台总量测试资产', '普通窗口预占', '台', 'EXACT', 1);

INSERT INTO fixed_asset (id, material_id, asset_code, location_id, position_text, status) VALUES
(9101, 9001, 'IA4-001', 101, 'A-1', 'IDLE'),
(9102, 9001, 'IA4-002', 101, 'A-2', 'IDLE'),
(9103, 9001, 'IA4-003', 101, 'A-3', 'IDLE'),
(9104, 9001, 'IA4-004', 101, 'A-4', 'IDLE'),
(9201, 9002, 'IA5-001', 101, 'B-1', 'IDLE'),
(9202, 9002, 'IA5-002', 101, 'B-2', 'IDLE'),
(9203, 9002, 'IA5-003', 101, 'B-3', 'IDLE'),
(9204, 9002, 'IA5-004', 101, 'B-4', 'IDLE'),
(9205, 9002, 'IA5-005', 101, 'B-5', 'IDLE');

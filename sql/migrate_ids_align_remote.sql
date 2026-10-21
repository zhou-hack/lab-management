-- ============================================================================
-- 迁移：设备申请 / 台账导入导出 编号对齐远端 dev/database 种子（2026-10-20 二次）
-- ============================================================================
-- 背景：远端 dev/database 在 2026-10-20 整合 material-manage 分支时重排了编号——
--       item 段占用权限 160-165/167 与菜单 200-209/212-215，设备报修权限移至
--       168/169、设备管理菜单移至 210/211。本组增量脚本编号需让出 160-169（权限）
--       与 200-215（菜单）整段。
-- 最终编号（与 add_asset_io_permission.sql / add_equipment_booking_permission.sql 一致）：
--   * 权限     170/171（台账导入/导出）；172-179（设备申请八权限）
--   * 菜单     220/221（设备导入/导出按钮）；216-219（设备申请四页面）
--   * 权限绑定 533/534（导入导出）；545-557（设备申请）
--   * 菜单绑定 622/623（导入导出）；616-621（设备申请）
--   * 演示账号 sys_user 2/3 → 10/11（2-9 保留给远端 opsadmin 等预置账号）
-- 本脚本对「2026-10-19 旧编号库」与「首轮已迁移中间态库」均可重放（幂等）：
--   各 UPDATE 以源 ID/权限码/菜单码守卫，目标 ID 已被占则自动跳过。
-- 执行：dev / test 各跑一次。事务 + 关闭外键检查。
-- ============================================================================
SET NAMES utf8mb4;
SET @old_fk = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- ============================================================
-- 1. 权限：160/161 → 170/171（导入导出）；162-169 → 172-179（设备申请）
--    （170/171 在中间态已就位，对应 160/161 迁移自动跳过）
-- ============================================================
UPDATE sys_permission SET id = 172
WHERE id = 162 AND permission_code = 'equipment:booking:apply'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 172) AS t);
UPDATE sys_permission SET id = 173
WHERE id = 163 AND permission_code = 'equipment:booking:list_own'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 173) AS t);
UPDATE sys_permission SET id = 174
WHERE id = 164 AND permission_code = 'equipment:booking:cancel'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 174) AS t);
UPDATE sys_permission SET id = 175
WHERE id = 165 AND permission_code = 'equipment:booking:teacher_review'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 175) AS t);
UPDATE sys_permission SET id = 176
WHERE id = 166 AND permission_code = 'equipment:booking:admin_review'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 176) AS t);
UPDATE sys_permission SET id = 177
WHERE id = 167 AND permission_code = 'equipment:booking:checkout'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 177) AS t);
UPDATE sys_permission SET id = 178
WHERE id = 168 AND permission_code = 'equipment:booking:return'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 178) AS t);
UPDATE sys_permission SET id = 179
WHERE id = 169 AND permission_code = 'equipment:booking:list_all'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 179) AS t);
UPDATE sys_permission SET id = 170
WHERE id = 160 AND permission_code = 'equipment:asset:import'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 170) AS t);
UPDATE sys_permission SET id = 171
WHERE id = 161 AND permission_code = 'equipment:asset:export'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_permission WHERE id = 171) AS t);

-- ============================================================
-- 2. 权限绑定：529/530 → 533/534；531-543 → 545-557（含 permission_id 同步）
-- ============================================================
-- 旧态 529/530（中间态已迁走则跳过）
UPDATE sys_role_permission SET id = 533, permission_id = 170
WHERE id = 529 AND permission_id = 160
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 533) AS t);
UPDATE sys_role_permission SET id = 534, permission_id = 171
WHERE id = 530 AND permission_id = 161
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 534) AS t);
-- 旧态 531-543（中间态已迁走则跳过）
UPDATE sys_role_permission SET id = 545, permission_id = 176 WHERE id = 531 AND permission_id = 166 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 545) AS t);
UPDATE sys_role_permission SET id = 546, permission_id = 177 WHERE id = 532 AND permission_id = 167 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 546) AS t);
UPDATE sys_role_permission SET id = 547, permission_id = 178 WHERE id = 533 AND permission_id = 168 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 547) AS t);
UPDATE sys_role_permission SET id = 548, permission_id = 179 WHERE id = 534 AND permission_id = 169 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 548) AS t);
UPDATE sys_role_permission SET id = 549, permission_id = 149 WHERE id = 535 AND permission_id = 149 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 549) AS t);
UPDATE sys_role_permission SET id = 550, permission_id = 172 WHERE id = 536 AND permission_id = 162 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 550) AS t);
UPDATE sys_role_permission SET id = 551, permission_id = 173 WHERE id = 537 AND permission_id = 163 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 551) AS t);
UPDATE sys_role_permission SET id = 552, permission_id = 174 WHERE id = 538 AND permission_id = 164 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 552) AS t);
UPDATE sys_role_permission SET id = 553, permission_id = 175 WHERE id = 539 AND permission_id = 165 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 553) AS t);
UPDATE sys_role_permission SET id = 554, permission_id = 149 WHERE id = 540 AND permission_id = 149 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 554) AS t);
UPDATE sys_role_permission SET id = 555, permission_id = 172 WHERE id = 541 AND permission_id = 162 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 555) AS t);
UPDATE sys_role_permission SET id = 556, permission_id = 173 WHERE id = 542 AND permission_id = 163 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 556) AS t);
UPDATE sys_role_permission SET id = 557, permission_id = 174 WHERE id = 543 AND permission_id = 164 AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_permission WHERE id = 557) AS t);
-- 中间/最终态 545-557 的 permission_id 修正为最终编号（旧引用 162-169 → 172-179）
UPDATE sys_role_permission SET permission_id = 176 WHERE id = 545 AND permission_id = 166;
UPDATE sys_role_permission SET permission_id = 177 WHERE id = 546 AND permission_id = 167;
UPDATE sys_role_permission SET permission_id = 178 WHERE id = 547 AND permission_id = 168;
UPDATE sys_role_permission SET permission_id = 179 WHERE id = 548 AND permission_id = 169;
UPDATE sys_role_permission SET permission_id = 172 WHERE id IN (550, 555) AND permission_id = 162;
UPDATE sys_role_permission SET permission_id = 173 WHERE id IN (551, 556) AND permission_id = 163;
UPDATE sys_role_permission SET permission_id = 174 WHERE id IN (552, 557) AND permission_id = 164;
UPDATE sys_role_permission SET permission_id = 175 WHERE id = 553 AND permission_id = 165;

-- ============================================================
-- 3. 菜单：173/174（旧）与 206/207（中间）→ 220/221；208-211 → 216-219
-- ============================================================
UPDATE sys_menu SET id = 220
WHERE id = 173 AND menu_code = 'equipment:asset:import'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 220) AS t);
UPDATE sys_menu SET id = 221
WHERE id = 174 AND menu_code = 'equipment:asset:export'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 221) AS t);
UPDATE sys_menu SET id = 220
WHERE id = 206 AND menu_code = 'equipment:asset:import'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 220) AS t);
UPDATE sys_menu SET id = 221
WHERE id = 207 AND menu_code = 'equipment:asset:export'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 221) AS t);
UPDATE sys_menu SET id = 216
WHERE id = 208 AND menu_code = 'C_EQUIPMENT_DIRECTORY'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 216) AS t);
UPDATE sys_menu SET id = 217
WHERE id = 209 AND menu_code = 'C_EQUIPMENT_MY_BOOKING'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 217) AS t);
UPDATE sys_menu SET id = 218
WHERE id = 210 AND menu_code = 'C_EQUIPMENT_TEACHER_REVIEW'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 218) AS t);
UPDATE sys_menu SET id = 219
WHERE id = 211 AND menu_code = 'C_EQUIPMENT_BOOKING_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE id = 219) AS t);

-- ============================================================
-- 4. 菜单绑定：614/615 → 622/623；622/623 与 616-621 的 menu_id 修正
-- ============================================================
-- 旧态 614/615（中间态已迁走则跳过）
UPDATE sys_role_menu SET id = 622, menu_id = 220
WHERE id = 614 AND menu_id = 173
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu WHERE id = 622) AS t);
UPDATE sys_role_menu SET id = 623, menu_id = 221
WHERE id = 615 AND menu_id = 174
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_role_menu WHERE id = 623) AS t);
-- 中间态 622/623 的 menu_id 修正（206/207 → 220/221）
UPDATE sys_role_menu SET menu_id = 220 WHERE id = 622 AND menu_id IN (206, 173);
UPDATE sys_role_menu SET menu_id = 221 WHERE id = 623 AND menu_id IN (207, 174);
-- 616-621 的 menu_id 修正（208-211 → 216-219）
UPDATE sys_role_menu SET menu_id = 216 WHERE id IN (617, 620) AND menu_id = 208;
UPDATE sys_role_menu SET menu_id = 217 WHERE id IN (618, 621) AND menu_id = 209;
UPDATE sys_role_menu SET menu_id = 218 WHERE id = 619 AND menu_id = 210;
UPDATE sys_role_menu SET menu_id = 219 WHERE id = 616 AND menu_id = 211;

-- ============================================================
-- 5. 演示账号 2 → 10、3 → 11（先更新全部引用列，再改 sys_user_role / sys_user 主键）
-- ============================================================
UPDATE biz_document SET owner_user_id = 10 WHERE owner_user_id = 2;
UPDATE biz_document SET owner_user_id = 11 WHERE owner_user_id = 3;
UPDATE document_event SET actor_id = 10 WHERE actor_id = 2;
UPDATE document_event SET actor_id = 11 WHERE actor_id = 3;
UPDATE equipment_booking SET applicant_id = 10 WHERE applicant_id = 2;
UPDATE equipment_booking SET applicant_id = 11 WHERE applicant_id = 3;
UPDATE equipment_booking SET approval_teacher_id = 10 WHERE approval_teacher_id = 2;
UPDATE equipment_booking SET approval_teacher_id = 11 WHERE approval_teacher_id = 3;
UPDATE equipment_usage SET borrower_id = 10 WHERE borrower_id = 2;
UPDATE equipment_usage SET borrower_id = 11 WHERE borrower_id = 3;
UPDATE approval_task SET assigned_user_id = 10 WHERE assigned_user_id = 2;
UPDATE approval_task SET assigned_user_id = 11 WHERE assigned_user_id = 3;
UPDATE approval_task SET decided_by = 10 WHERE decided_by = 2;
UPDATE approval_task SET decided_by = 11 WHERE decided_by = 3;
UPDATE equipment_asset SET classified_by = 10 WHERE classified_by = 2;
UPDATE equipment_asset SET classified_by = 11 WHERE classified_by = 3;
UPDATE equipment_asset SET custodian_id = 10 WHERE custodian_id = 2;
UPDATE equipment_asset SET custodian_id = 11 WHERE custodian_id = 3;
UPDATE equipment_location_history SET operator_id = 10 WHERE operator_id = 2;
UPDATE equipment_location_history SET operator_id = 11 WHERE operator_id = 3;
UPDATE equipment_usage SET checkout_operator_id = 10 WHERE checkout_operator_id = 2;
UPDATE equipment_usage SET checkout_operator_id = 11 WHERE checkout_operator_id = 3;
UPDATE equipment_usage SET return_operator_id = 10 WHERE return_operator_id = 2;
UPDATE equipment_usage SET return_operator_id = 11 WHERE return_operator_id = 3;
UPDATE sys_user_data_scope SET user_id = 10 WHERE user_id = 2;
UPDATE sys_user_data_scope SET user_id = 11 WHERE user_id = 3;
UPDATE user_booking_restriction SET revoked_by = 10 WHERE revoked_by = 2;
UPDATE user_booking_restriction SET revoked_by = 11 WHERE revoked_by = 3;
UPDATE user_booking_restriction SET user_id = 10 WHERE user_id = 2;
UPDATE user_booking_restriction SET user_id = 11 WHERE user_id = 3;
-- sys_user_role：主键与 user_id 同步
UPDATE sys_user_role SET id = 10, user_id = 10
WHERE id = 2 AND user_id = 2
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user_role WHERE id = 10) AS t);
UPDATE sys_user_role SET id = 11, user_id = 11
WHERE id = 3 AND user_id = 3
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user_role WHERE id = 11) AS t);
-- sys_user：演示账号主键迁移
UPDATE sys_user SET id = 10
WHERE id = 2 AND account_name = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user WHERE id = 10) AS t);
UPDATE sys_user SET id = 11
WHERE id = 3 AND account_name = 'student1'
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_user WHERE id = 11) AS t);

COMMIT;
SET FOREIGN_KEY_CHECKS = @old_fk;

-- ============================================================================
-- 节次表调整：按《福州软件职业技术学院教学作息时间表》（2026-10-08 起执行）
-- 由 8 节修订为 12 节（上午 1-4 / 下午 5-8 / 晚上 9-12）；
-- 场地开放结束由 21:00 放宽至 21:45（容纳第 12 节）。
-- ============================================================================
USE lab_management;

UPDATE class_period SET start_clock = '08:25:00', end_clock = '09:10:00' WHERE id = 1;
UPDATE class_period SET start_clock = '09:15:00', end_clock = '10:00:00' WHERE id = 2;
UPDATE class_period SET start_clock = '10:10:00', end_clock = '10:55:00' WHERE id = 3;
UPDATE class_period SET start_clock = '11:00:00', end_clock = '11:45:00' WHERE id = 4;
UPDATE class_period SET start_clock = '13:50:00', end_clock = '14:35:00' WHERE id = 5;
UPDATE class_period SET start_clock = '14:40:00', end_clock = '15:25:00' WHERE id = 6;
UPDATE class_period SET start_clock = '15:35:00', end_clock = '16:20:00' WHERE id = 7;
UPDATE class_period SET start_clock = '16:25:00', end_clock = '17:10:00' WHERE id = 8;

INSERT IGNORE INTO class_period (id, period_no, period_name, start_clock, end_clock, enabled,
                          create_user_id, update_user_id)
VALUES
(9,  9,  '第9节',  '18:30:00', '19:15:00', 1, 1, 1),
(10, 10, '第10节', '19:20:00', '20:05:00', 1, 1, 1),
(11, 11, '第11节', '20:10:00', '20:55:00', 1, 1, 1),
(12, 12, '第12节', '21:00:00', '21:45:00', 1, 1, 1);

UPDATE lab SET open_end = '21:45:00' WHERE deleted = 0;

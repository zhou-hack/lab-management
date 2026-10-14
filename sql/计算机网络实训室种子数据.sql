-- ============================================================================
-- 计算机网络实训室种子数据
-- 依据《计算机网络实训室设备清单与使用管理规范》初始化业务数据：
--   1) 空间层级（校区 → 楼栋 → 实验室）
--   2) 设备分类（网络设备 / 计算与存储 / 测试仪器仪表 / 常用工具，共 28 项）
--   3) 设备型号（一类一型号，品牌按文档选型建议统一为华为生态）
--   4) 设备台账（按文档参考数量入库，约 159 台/套；价值分级按单价 ≥ 5000 为贵重）
--   5) 菜单结构调整（管理端根目录 + 首页，保证前端侧栏唯一 /admin 根渲染）
--   6) 管理员密码（BCrypt，开发演示密码 admin123；生产部署必须替换）
--
-- 执行前提（按序）：
--   * 必须先执行 sys_rbac.sql、空间.sql、document_base.sql、固定设备.sql
--
-- 幂等性说明：
--   * 固定小 ID 与雪花 ID 不冲突；脚本仅在模块表为空的新库上执行一次，
--     重复执行将触发主键/唯一键冲突（种子脚本不设计为可重复执行）。
--   * dev（lab_management）与 test（lab_management_test）两库均需执行，
--     test 库为集成测试（AuthFlowTest 等）提供登录与菜单数据。
--
-- 审计约定：create_user_id/update_time 等由种子显式给出；时间一律 UTC。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 空间层级（文档：40～50 座标准规模的校内实训场地）
-- ----------------------------------------------------------------------------
INSERT INTO campus (id, campus_code, campus_name, address, enabled)
VALUES (1, 'MAIN', '主校区', '校内主校区', 1);

INSERT INTO building (id, campus_id, building_code, building_name, enabled)
VALUES (1, 1, 'TRAINING', '实训楼', 1);

INSERT INTO lab (id, building_id, department_id, lab_code, lab_name, lab_type,
                 floor_no, room_no, capacity, area_sqm, location_note, enabled)
VALUES (1, 1, 1, 'NET_LAB', '计算机网络实训室', 'TEACHING',
        '3', '301', 48, 160.00, '含软件实训、真机台架、综合布线、服务器与存储、仪器工具保管五个功能分区', 1);

-- ----------------------------------------------------------------------------
-- 2. 设备分类（文档二、设备清单；根 101–104，子分类 111–146）
-- ----------------------------------------------------------------------------
INSERT INTO equipment_category (id, parent_id, category_code, category_name, enabled, create_user_id) VALUES
(101, NULL, 'NET_DEV',    '网络设备',     1, 1),
(102, NULL, 'COMPUTE',    '计算与存储',   1, 1),
(103, NULL, 'INSTRUMENT', '测试仪器仪表', 1, 1),
(104, NULL, 'TOOL',       '常用工具',     1, 1),
(111, 101, 'ROUTER',       '企业级路由器',  1, 1),
(112, 101, 'SWITCH_L2',    '二层交换机',    1, 1),
(113, 101, 'SWITCH_L3',    '三层交换机',    1, 1),
(114, 101, 'FIREWALL',     '下一代防火墙',  1, 1),
(115, 101, 'WIRELESS_AC',  '无线控制器',    1, 1),
(116, 101, 'WIRELESS_AP',  '无线接入点',    1, 1),
(117, 101, 'SWITCH_POE',   'POE交换机',     1, 1),
(121, 102, 'STUDENT_PC',   '学生机',        1, 1),
(122, 102, 'TEACHER_PC',   '教师机',        1, 1),
(123, 102, 'SERVER',       '服务器',        1, 1),
(124, 102, 'NAS',          '网络存储',      1, 1),
(125, 102, 'KVM',          'KVM切换器',     1, 1),
(126, 102, 'PROJECTOR',    '投影交互平板',  1, 1),
(131, 103, 'CABLE_TESTER', '网络测线仪',    1, 1),
(132, 103, 'TRACKER',      '寻线仪',        1, 1),
(133, 103, 'MULTIMETER',   '数字万用表',    1, 1),
(134, 103, 'OPTICAL_POWER','光功率计套件',  1, 1),
(135, 103, 'VFL',          '红光笔',        1, 1),
(136, 103, 'CERT_TESTER',  '链路认证测试仪', 1, 1),
(137, 103, 'LABEL_PRINTER','标签打印机',    1, 1),
(138, 103, 'DEBUG_LAPTOP', '调试笔记本',    1, 1),
(139, 103, 'THERMO_HYGRO', '数字温湿度计',  1, 1),
(141, 104, 'CRIMP_TOOL',   '压线钳',        1, 1),
(142, 104, 'PUNCH_TOOL',   '打线刀',        1, 1),
(143, 104, 'STRIPPER',     '剥线器',        1, 1),
(144, 104, 'FIBER_TOOL',   '光纤冷接工具套装', 1, 1),
(145, 104, 'SCREWDRIVER',  '螺丝刀套装',    1, 1),
(146, 104, 'ESD_KIT',      '防静电套装',    1, 1);

-- ----------------------------------------------------------------------------
-- 3. 设备型号（ID 201–246；规格/参考价按文档典型规格）
--    requires_data_erasure=1：含存储介质的设备（归还须数据擦除）
-- ----------------------------------------------------------------------------
INSERT INTO equipment_model (id, category_id, model_code, equipment_name, brand, specification,
                             unit_name, reference_price, expected_life_months,
                             requires_data_erasure, enabled, create_user_id) VALUES
(201, 111, 'HUAWEI_AR6120',   '企业级路由器 AR6120',  '华为', 'GE 广域接口，OSPF/NAT/ACL 实训',                '台',  8000.00,  60, 0, 1, 1),
(202, 112, 'HUAWEI_S5735L24', '二层交换机 S5735',     '华为', '24 口千兆可网管，VLAN/STP/链路聚合',            '台',  4500.00,  60, 0, 1, 1),
(203, 113, 'HUAWEI_S6730H24','三层交换机 S6730',     '华为', '24 口千兆＋万兆上行，VLAN 间路由/DHCP/VRRP',    '台',  12000.00, 60, 0, 1, 1),
(204, 114, 'HUAWEI_USG6000E','下一代防火墙 USG6000E','华为', '安全策略/IPSec VPN/攻击防护',                   '台',  30000.00, 60, 0, 1, 1),
(205, 115, 'HUAWEI_AC6508',  '无线控制器 AC6508',    '华为', '含 license，支持目标 AP 数量',                  '台',  15000.00, 60, 0, 1, 1),
(206, 116, 'HUAWEI_AP263',   '无线接入点 AP263',      '华为', '面板式，POE 供电，Portal/802.1X 认证',           '个',  1200.00,  60, 0, 1, 1),
(207, 117, 'HUAWEI_S5735L8P','POE交换机 S5735-L8P',  '华为', '8 口千兆 POE，无线与安防监控供电',               '台',  2000.00,  60, 0, 1, 1),
(221, 121, 'LENOVO_M760T',   '学生机 ThinkCentre',   '联想', 'i5/16GB/256GB SSD/千兆网卡',                    '台',  4500.00,  60, 1, 1, 1),
(222, 122, 'LENOVO_M920T',   '教师机 ThinkCentre',   '联想', '高于学生机配置，双显示输出',                    '台',  7000.00,  60, 1, 1, 1),
(223, 123, 'HUAWEI_2288H',   '服务器 2288H V5',      '华为', '2 路 CPU/64GB 内存/RAID 磁盘阵列',               '台',  45000.00, 60, 1, 1, 1),
(224, 124, 'SYNOLOGY_DS920', '网络存储 DS920+',      '群晖', '4 盘位 NAS，教学资源与镜像分发',                 '台',  12000.00, 60, 1, 1, 1),
(225, 125, 'ATEN_CS1308',    'KVM 切换器 CS1308',   'ATEN','8 口数字 KVM，机柜内服务器本地管理',            '套',  3000.00,  60, 0, 1, 1),
(226, 126, 'SEEWO_86PANEL',  '86 寸触控一体机',      '希沃', '激光投影/86 寸交互平板，理论授课演示',           '套',  25000.00, 60, 0, 1, 1),
(231, 131, 'NF_8209',        '网络测线仪',            '福禄克','双绞线通断、线序检测',                          '个',  250.00,   60, 0, 1, 1),
(232, 132, 'MT_8200',        '寻线仪',                '福禄克','寻线＋测线一体',                                '套',  800.00,   60, 0, 1, 1),
(233, 133, 'VC_97',          '数字万用表 VC97',       '胜利', '数字式，含通鸣档',                              '块',  300.00,   60, 0, 1, 1),
(234, 134, 'OPT_POWER_SET',  '光功率计＋稳定光源套装','通用', '波长 1310/1550nm，光纤链路损耗测试',             '套',  3000.00,  60, 0, 1, 1),
(235, 135, 'VFL_650',        '红光笔',                '通用', '650nm 可视故障定位',                            '支',  150.00,   60, 0, 1, 1),
(236, 136, 'FLUKE_DSX8000',  '链路认证测试仪 DSX-8000','福禄克','布线工程验收级认证测试（高价值，教师在场使用）','台', 50000.00,  60, 0, 1, 1),
(237, 137, 'XP_58',          '标签打印机',            '芯烨', '热转印，配标签带，机柜线缆标识',                 '台',  1500.00,  60, 0, 1, 1),
(238, 138, 'THINKPAD_E14',   '调试笔记本',            '联想', '千兆网口＋USB 网卡/串口，设备调试维护',          '台',  6000.00,  60, 1, 1, 1),
(239, 139, 'TH_HYGRO',       '数字温湿度计',          '通用', '带记录功能，机房环境监测',                      '个',  200.00,   60, 0, 1, 1),
(241, 141, 'CRIMP_PLIER',    '压线钳',                '通用', '水晶头压制',                                    '把',  80.00,    36, 0, 1, 1),
(242, 142, 'PUNCH_110',      '打线刀 110 型',         '通用', '配线架端接',                                    '把',  60.00,    36, 0, 1, 1),
(243, 143, 'CABLE_STRIPPER', '剥线器',                '通用', '双绞线剥皮',                                    '把',  40.00,    36, 0, 1, 1),
(244, 144, 'FIBER_KIT',      '光纤冷接工具套装',      '通用', '光纤磨接、冷接子制作',                          '套',  400.00,   36, 0, 1, 1),
(245, 145, 'SCREW_SET',      '螺丝刀套装',            '通用', '含镊子、斜口钳，拆装理线',                      '套',  120.00,   36, 0, 1, 1),
(246, 146, 'ESD_SET',        '防静电套装',            '通用', '手环＋防静电垫＋手套',                          '套',  150.00,   36, 0, 1, 1);

-- ----------------------------------------------------------------------------
-- 4. 设备台账（约 159 台/套；ID 段 30001–30399）
--    * 资产编号 = 前缀 + 两位序号；status 全部 IDLE，保管人 = 管理员(1)
--    * 价值分级：单价 ≥ 5000 为 VALUABLE（贵重），其余 NORMAL
--    * 采购批次 2025-08-15：网络/计算设备保修 3 年，仪器工具保修 1 年
--    * 存放位置按文档功能分区：真机台架区/软件实训区/服务器与存储区/仪器工具保管区
-- ----------------------------------------------------------------------------
INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30000 + n, 201, CONCAT('R', LPAD(n, 2, '0')), 1, 1,
       CONCAT('真机台架区 第', n, '组台架'), 1, '2025-08-15', 8000.00, '2028-08-15',
       'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 16)
SELECT 30010 + n, 202, CONCAT('SW', LPAD(n, 2, '0')), 1, 1,
       CONCAT('真机台架区 第', FLOOR((n + 1) / 2), '组台架'), 1, '2025-08-15', 4500.00, '2028-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30030 + n, 203, CONCAT('LSW', LPAD(n, 2, '0')), 1, 1,
       CONCAT('真机台架区 第', n, '组台架'), 1, '2025-08-15', 12000.00, '2028-08-15',
       'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30040 + n, 204, CONCAT('FW', LPAD(n, 2, '0')), 1, 1,
       '真机台架区 第1组台架', 1, '2025-08-15', 30000.00, '2028-08-15',
       'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30051, 205, 'AC01', 1, 1, '真机台架区 第1组台架', 1, '2025-08-15', 15000.00, '2028-08-15',
        'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 4)
SELECT 30060 + n, 206, CONCAT('AP', LPAD(n, 2, '0')), 1, 1,
       CONCAT('真机台架区 第', n, '组台架'), 1, '2025-08-15', 1200.00, '2028-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30070 + n, 207, CONCAT('POE', LPAD(n, 2, '0')), 1, 1,
       '真机台架区 第1组台架', 1, '2025-08-15', 2000.00, '2028-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 40)
SELECT 30100 + n, 221, CONCAT('PC', LPAD(n, 2, '0')), 1, 1,
       CONCAT('软件实训区 第', n, '工位'), 1, '2025-08-15', 4500.00, '2028-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30201, 222, 'TPC01', 1, 1, '软件实训区 讲台', 1, '2025-08-15', 7000.00, '2028-08-15',
        'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30210 + n, 223, CONCAT('SRV', LPAD(n, 2, '0')), 1, 1,
       '服务器与存储区 标准机柜', 1, '2025-08-15', 45000.00, '2028-08-15',
       'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30221, 224, 'NAS01', 1, 1, '服务器与存储区 标准机柜', 1, '2025-08-15', 12000.00, '2028-08-15',
        'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30231, 225, 'KVM01', 1, 1, '服务器与存储区 标准机柜', 1, '2025-08-15', 3000.00, '2028-08-15',
        'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30241, 226, 'PRJ01', 1, 1, '软件实训区 讲台', 1, '2025-08-15', 25000.00, '2028-08-15',
        'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 10)
SELECT 30250 + n, 231, CONCAT('CT', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 250.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 4)
SELECT 30260 + n, 232, CONCAT('TR', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 800.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 4)
SELECT 30270 + n, 233, CONCAT('MM', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 300.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30280 + n, 234, CONCAT('OP', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 3000.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 4)
SELECT 30290 + n, 235, CONCAT('VL', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 150.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30301, 236, 'CTX01', 1, 1, '仪器工具保管区 专用柜', 1, '2025-08-15', 50000.00, '2026-08-15',
        'VALUABLE', '2025 年采购批次定价（高价值仪器）', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
VALUES (30311, 237, 'LP01', 1, 1, '仪器工具保管区 专用柜', 1, '2025-08-15', 1500.00, '2026-08-15',
        'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1);

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30320 + n, 238, CONCAT('LT', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 6000.00, '2026-08-15',
       'VALUABLE', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30330 + n, 239, CONCAT('TH', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 专用柜', 1, '2025-08-15', 200.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30340 + n, 241, CONCAT('CR', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 80.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30350 + n, 242, CONCAT('PU', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 60.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30360 + n, 243, CONCAT('ST', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 40.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 2)
SELECT 30370 + n, 244, CONCAT('FT', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 400.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30380 + n, 245, CONCAT('SD', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 120.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, location_detail,
                             custodian_id, purchase_date, purchase_price, warranty_until,
                             value_level, classification_reason, classified_by, classified_at,
                             status, enabled, create_user_id)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 8)
SELECT 30390 + n, 246, CONCAT('ESD', LPAD(n, 2, '0')), 1, 1,
       '仪器工具保管区 工具柜', 1, '2025-08-15', 150.00, '2026-08-15',
       'NORMAL', '2025 年采购批次定价', 1, UTC_TIMESTAMP(3), 'IDLE', 1, 1 FROM seq;

-- ----------------------------------------------------------------------------
-- 5. 菜单结构调整（ID 171–173；绑定 612–617）
--    前端 AdminLayout 以"唯一 /admin 根目录"渲染侧栏，而模块脚本产生了
--    121（基础数据）与 161（设备管理）两个 /admin 根目录——需补一个管理端
--    根目录将其挂为子树；同时补管理端/前台首页页菜单并绑定 ADMIN。
--    169 借用审批不绑定（收权决策：管理员审批走后台借用管理页）。
-- ----------------------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key,
                      permission_id, menu_type, sort_order) VALUES
(171, NULL, 'M_ADMIN_ROOT', '管理端', '/admin', NULL, NULL, 'DIRECTORY', 0),
(172, 171,  'C_ADMIN_HOME', '首页',   '/admin/home', '/admin/home', NULL, 'PAGE', 0),
(173, 166,  'C_FRONT_HOME', '首页',   '/home', '/home', NULL, 'PAGE', 0);

-- 模块脚本的两个 /admin 根目录挂到管理端根下
UPDATE sys_menu SET parent_id = 171 WHERE id IN (121, 161);

-- ADMIN(1) 菜单绑定：管理端根/首页 + 前台设备服务入口（发起借用、我的借用）
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(612, 1, 171),
(613, 1, 172),
(614, 1, 173),
(615, 1, 166),
(616, 1, 167),
(617, 1, 168);

-- ----------------------------------------------------------------------------
-- 6. 管理员密码（替换 sys_rbac.sql 占位值）
--    BCrypt 密文对应明文 admin123（开发/演示口令，满足 ≥8 位且含数字+字母）；
--    生产部署前必须修改并强制首登更换（90 天强制换密属后续 auth 模块范围）。
-- ----------------------------------------------------------------------------
UPDATE sys_user
SET password_hash     = '$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y',
    password_changed_at = UTC_TIMESTAMP(3),
    update_time       = UTC_TIMESTAMP(3)
WHERE id = 1 AND account_name = 'admin';

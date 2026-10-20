-- ############################################################################
-- DOCX 期初数据导入：《实训室设备物料清单与工作流程.docx》→ lab_management
-- 清洗依据 docs/数据库设计.md 10.1；2026-10-15；在 sys_rbac + lab_management_all 之后执行
--
-- 决策（管理员确认）：
--   1. DOCX 适用 5 个实训室，库内仅有 网络/电子电工：新建 嵌入式实训室(4)、
--      A303 软件工程实训室(5)、综合工程实训室(6)，另建 公共库房(7) 集中登记公共设备。
--   2. DOCX“在用”为台账标签，不映射 IN_USE：资产一律 IDLE，value_level NULL 待分类。
--   3. 缺价格 NULL 不填 0；采购价/采购日期未知 NULL；存放位置 A303-* 拆为
--      软件工程实训室(lab_id=5) + 室内位置（讲台/机柜1/机柜2/机柜区）。
--   4. 合并规格拆独立 SKU（29 源行 → 61 SKU），单一基础单位；酒精定“瓶”并标危险品。
--   5. 库存仅数量明确的 5 源行以 OPENING 单据过账（单据+行+流水+余额四件套，
--      不直接写 stock_balance）；下列 24 源行拆分后数量无法一一对应，只建 SKU
--      待实盘确认后另行 OPENING：
--        单股铜芯线(红/蓝/黄绿)、杜邦线(公对公/母对母/公对母)、网线(超五/超六)、
--        面包板(830/400孔)、万能板(5×7/7×9)、开关(船型/轻触)、接线端子/冷压端子、
--        香蕉插头/鳄鱼夹、排针/排母、网络模块/面板、电阻包/电位器、电容包/电解电容、
--        二极管/三极管(S8050/S8550)、LED(红/绿/黄)、IC(NE555/LM7805/LM358)、
--        数码管/蜂鸣器、焊锡丝/松香、热缩管/电工胶布、扎带/PVC线槽/螺丝、
--        键盘/鼠标、清洁套装/A4纸/U盘、防静电手环/垫、烙铁海绵/酒精/擦机布、
--        标签纸/记号笔
-- ############################################################################

-- ----------------------------------------------------------------------------
-- 1. 新场地（id 4-7；容量与位置为占位，待核实后在场地管理中调整）
-- ----------------------------------------------------------------------------
INSERT INTO lab (id, lab_name, lab_type, location_detail, capacity, facilities,
                 open_start, open_end, status, create_user_id, update_user_id)
VALUES
(4, '嵌入式实训室', 'TRAINING_ROOM', NULL, 45, NULL, '08:00', '21:00', 'AVAILABLE', 1, 1),
(5, 'A303 软件工程实训室', 'TRAINING_ROOM', 'A303', 61, NULL, '08:00', '21:00', 'AVAILABLE', 1, 1),
(6, '综合工程实训室', 'TRAINING_ROOM', NULL, 45, NULL, '08:00', '21:00', 'AVAILABLE', 1, 1),
(7, '公共库房', 'TRAINING_ROOM', NULL, 10, '公共物资集中存放', '08:00', '21:00', 'AVAILABLE', 1, 1);

-- ----------------------------------------------------------------------------
-- 2. 设备分类（11 个根分类，按 DOCX“大类”）
-- ----------------------------------------------------------------------------
INSERT INTO equipment_category (id, parent_id, category_code, category_name, enabled,
                                create_user_id, update_user_id)
VALUES
(1, NULL, 'INSTRUMENT',    '仪器仪表', 1, 1, 1),
(2, NULL, 'TRAIN_PLATFORM', '实训平台', 1, 1, 1),
(3, NULL, 'WELD_TOOL',     '焊接工具', 1, 1, 1),
(4, NULL, 'DEV_BOARD',     '开发板',   1, 1, 1),
(5, NULL, 'PERIPH_MODULE', '外设模块', 1, 1, 1),
(6, NULL, 'NETWORK_DEVICE', '网络设备', 1, 1, 1),
(7, NULL, 'TEST_TOOL',     '测试工具', 1, 1, 1),
(8, NULL, 'COMPUTER',      '计算机',   1, 1, 1),
(9, NULL, 'PERIPHERAL',    '外设',     1, 1, 1),
(10, NULL, 'TOOL_KIT',     '工具套装', 1, 1, 1),
(11, NULL, 'SAFETY_DEVICE', '安防设备', 1, 1, 1);

-- ----------------------------------------------------------------------------
-- 3. 设备型号（33 个；缺参考价 NULL 待完善；电脑类归还须数据擦除）
-- ----------------------------------------------------------------------------
INSERT INTO equipment_model (id, category_id, model_code, equipment_name, brand, specification,
                             base_unit, reference_price, expected_life_months,
                             requires_data_erasure, enabled, create_user_id, update_user_id)
VALUES
(1, 1, 'MOD001', '数字示波器', NULL, '双通道 100MHz 1GSa/s', '台', 6000, NULL, 0, 1, 1, 1),
(2, 1, 'MOD002', '函数信号发生器', NULL, '25MHz 任意波形', '台', 3000, NULL, 0, 1, 1, 1),
(3, 1, 'MOD003', '直流稳压电源', NULL, '0-30V/5A 双路可调', '台', 1500, NULL, 0, 1, 1, 1),
(4, 1, 'MOD004', '数字万用表', NULL, '三位半 自动量程', '块', 300, NULL, 0, 1, 1, 1),
(5, 1, 'MOD005', '交流毫伏表', NULL, '双通道 1mV-300V', '台', 2500, NULL, 0, 1, 1, 1),
(6, 2, 'MOD006', '电工电子实训台', NULL, '带配电箱、三相输出', '台', NULL, NULL, 0, 1, 1, 1),
(7, 2, 'MOD007', '模电数电实验箱', NULL, '含晶体管、门电路模块', '台', 2500, NULL, 0, 1, 1, 1),
(8, 3, 'MOD008', '恒温电烙铁', NULL, '60W 936D', '把', NULL, NULL, 0, 1, 1, 1),
(9, 3, 'MOD009', '热风枪拆焊台', NULL, '858D 数显', '台', 600, NULL, 0, 1, 1, 1),
(10, 4, 'MOD010', '正点原子Linux开发板', NULL, 'ATK-DLMP257B 双核1.5G/1G DDR4/8G EMMC', '套', 1500, NULL, 0, 1, 1, 1),
(11, 4, 'MOD011', 'STM32开发板', NULL, 'F103ZET6 核心板+扩展板', '套', 300, NULL, 0, 1, 1, 1),
(12, 4, 'MOD012', '51单片机实验箱', NULL, '含LED/数码管/电机/AD', '套', 200, NULL, 0, 1, 1, 1),
(13, 4, 'MOD013', '树莓派', NULL, '4B 4GB', '套', 400, NULL, 0, 1, 1, 1),
(14, 5, 'MOD014', '传感器模块套装', NULL, '温湿度/红外/超声/蓝牙', '套', 150, NULL, 0, 1, 1, 1),
(15, 6, 'MOD015', '二层交换机', NULL, '24口 千兆可网管', '台', 4200, NULL, 0, 1, 1, 1),
(16, 6, 'MOD016', '三层交换机', NULL, '48口 万兆上行', '台', 8500, NULL, 0, 1, 1, 1),
(17, 6, 'MOD017', '路由器', NULL, '多业务模块化', '台', 8000, NULL, 0, 1, 1, 1),
(18, 6, 'MOD018', '防火墙', NULL, '企业级百兆', '台', NULL, NULL, 0, 1, 1, 1),
(19, 6, 'MOD019', '网络机柜', NULL, '42U 含PDU', '个', 2500, NULL, 0, 1, 1, 1),
(20, 7, 'MOD020', '网线钳', NULL, '三用 RJ45/RJ11', '把', NULL, NULL, 0, 1, 1, 1),
(21, 7, 'MOD021', '网线测线仪', NULL, '通断/线序', '个', NULL, NULL, 0, 1, 1, 1),
(22, 7, 'MOD022', '寻线仪', NULL, '带电寻线', '个', NULL, NULL, 0, 1, 1, 1),
(23, 7, 'MOD023', '光纤熔接机', NULL, '单芯', '台', 25000, NULL, 0, 1, 1, 1),
(24, 8, 'MOD024', '教师机', NULL, 'i7/16G/512G SSD/独显', '台', 5000, NULL, 1, 1, 1, 1),
(25, 8, 'MOD025', '学生台式机', NULL, 'i5/16G/512G SSD', '台', NULL, NULL, 1, 1, 1, 1),
(26, 8, 'MOD026', 'GPU服务器', NULL, 'RTX4090×2/128G', '台', 22000, NULL, 1, 1, 1, 1),
(27, 9, 'MOD027', '投影仪', NULL, '4000流明', '台', 4500, NULL, 0, 1, 1, 1),
(28, 10, 'MOD028', '螺丝刀套装', NULL, '32件套', '套', 80, NULL, 0, 1, 1, 1),
(29, 10, 'MOD029', '镊子钳子套装', NULL, '尖头/弯头/斜口', '套', 60, NULL, 0, 1, 1, 1),
(30, 11, 'MOD030', '干粉灭火器', NULL, '4kg ABC', '具', 120, NULL, 0, 1, 1, 1),
(31, 11, 'MOD031', '急救箱', NULL, '含烫伤膏/纱布', '个', 150, NULL, 0, 1, 1, 1),
(32, 11, 'MOD032', '绝缘橡胶垫', NULL, '10kV 1m×1m', '块', 200, NULL, 0, 1, 1, 1),
(33, 11, 'MOD033', 'UPS不间断电源', NULL, '3KVA 在线式', '台', 3000, NULL, 0, 1, 1, 1);

-- ----------------------------------------------------------------------------
-- 4. 设备台账（按型号数量逐台展开，共 455 台/套；AST00001 起连续编号；
--    状态 IDLE、value_level NULL 待分类、采购价/日期 NULL、归口 设备处(1)）
-- ----------------------------------------------------------------------------
INSERT INTO equipment_asset (id, model_id, asset_code, serial_no, lab_id, department_id,
                             location_detail, custodian_id, purchase_date, purchase_price,
                             warranty_until, value_level, status, enabled,
                             create_user_id, update_user_id)
WITH RECURSIVE nums (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 100
), src (model_id, qty, lab_id, loc) AS (
    SELECT 1, 8, 2, NULL UNION ALL SELECT 2, 4, 2, NULL UNION ALL
    SELECT 3, 12, 2, NULL UNION ALL SELECT 4, 35, 2, NULL UNION ALL
    SELECT 5, 4, 2, NULL UNION ALL SELECT 6, 35, 2, NULL UNION ALL
    SELECT 7, 35, 2, NULL UNION ALL SELECT 8, 35, 2, NULL UNION ALL
    SELECT 9, 6, 2, NULL UNION ALL SELECT 10, 35, 4, NULL UNION ALL
    SELECT 11, 35, 4, NULL UNION ALL SELECT 12, 35, 4, NULL UNION ALL
    SELECT 13, 10, 4, NULL UNION ALL SELECT 14, 15, 4, NULL UNION ALL
    SELECT 15, 8, 1, NULL UNION ALL SELECT 16, 4, 1, NULL UNION ALL
    SELECT 17, 4, 5, '机柜2' UNION ALL SELECT 18, 2, 1, NULL UNION ALL
    SELECT 19, 2, 5, '机柜区' UNION ALL SELECT 20, 15, 1, NULL UNION ALL
    SELECT 21, 15, 1, NULL UNION ALL SELECT 22, 4, 1, NULL UNION ALL
    SELECT 23, 1, 1, NULL UNION ALL SELECT 24, 1, 5, '讲台' UNION ALL
    SELECT 25, 60, 5, NULL UNION ALL SELECT 26, 1, 5, '机柜1' UNION ALL
    SELECT 27, 1, 5, NULL UNION ALL SELECT 28, 8, 7, NULL UNION ALL
    SELECT 29, 8, 7, NULL UNION ALL SELECT 30, 6, 7, NULL UNION ALL
    SELECT 31, 3, 7, NULL UNION ALL SELECT 32, 6, 7, NULL UNION ALL
    SELECT 33, 2, 7, NULL
), expanded AS (
    SELECT src.model_id, src.lab_id, src.loc,
           ROW_NUMBER() OVER (ORDER BY src.model_id, nums.n) AS rn
    FROM src
    JOIN nums ON nums.n <= src.qty
)
SELECT rn, model_id, CONCAT('AST', LPAD(rn, 5, '0')), NULL, lab_id, 1, loc,
       NULL, NULL, NULL, NULL, NULL, 'IDLE', 1, 1, 1
FROM expanded;

-- ----------------------------------------------------------------------------
-- 5. 物料分类（7 个根分类，按 DOCX“分类”）
-- ----------------------------------------------------------------------------
INSERT INTO material_category (id, parent_id, category_code, category_name, enabled,
                               create_user_id, update_user_id)
VALUES
(1, NULL, 'CABLE',         '线材类', 1, 1, 1),
(2, NULL, 'CONNECTOR',     '连接器件类', 1, 1, 1),
(3, NULL, 'NET_SUPPLY',    '网络耗材类', 1, 1, 1),
(4, NULL, 'ELEC_COMP',     '电子元器件类', 1, 1, 1),
(5, NULL, 'WELD_SUPPLY',   '焊接安装辅料', 1, 1, 1),
(6, NULL, 'IT_SUPPLY',     'IT耗材类', 1, 1, 1),
(7, NULL, 'PROTECT_CLEAN', '防护清洁类', 1, 1, 1);

-- ----------------------------------------------------------------------------
-- 6. 物料 SKU（61 个；全部 CONSUMABLE、整数精度；酒精 SKU058 危险品）
-- ----------------------------------------------------------------------------
INSERT INTO material_sku (id, category_id, sku_code, material_name, specification, base_unit,
                          quantity_scale, material_type, is_hazardous, is_valuable, enabled,
                          create_user_id, update_user_id)
VALUES
(1, 1, 'SKU001', '单股铜芯线', 'BV 1.5mm² 红色', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(2, 1, 'SKU002', '单股铜芯线', 'BV 1.5mm² 蓝色', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(3, 1, 'SKU003', '单股铜芯线', 'BV 1.5mm² 黄绿', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(4, 1, 'SKU004', '多股软线', 'BVR 0.75mm²', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(5, 1, 'SKU005', '实验连接线', '鳄鱼夹-香蕉插头 50cm', '根', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(6, 1, 'SKU006', '杜邦线', '公对公 20cm', '排', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(7, 1, 'SKU007', '杜邦线', '母对母 20cm', '排', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(8, 1, 'SKU008', '杜邦线', '公对母 20cm', '排', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(9, 1, 'SKU009', '网线', '超五类 305m/箱', '箱', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(10, 1, 'SKU010', '网线', '超六类 305m/箱', '箱', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(11, 1, 'SKU011', '光纤跳线', 'SC-SC 单模 3m', '条', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(12, 1, 'SKU012', 'USB数据线', 'Type-C 1m', '条', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(13, 2, 'SKU013', '面包板', '830孔', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(14, 2, 'SKU014', '面包板', '400孔', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(15, 2, 'SKU015', '万能板', '5×7cm', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(16, 2, 'SKU016', '万能板', '7×9cm', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(17, 2, 'SKU017', '船型开关', 'KCD1', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(18, 2, 'SKU018', '轻触开关', '6×6', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(19, 2, 'SKU019', '接线端子', '10位排', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(20, 2, 'SKU020', '冷压端子', '叉形 1.25-3', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(21, 2, 'SKU021', '香蕉插头', '4mm', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(22, 2, 'SKU022', '鳄鱼夹', '小号 红黑', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(23, 2, 'SKU023', '排针', '2.54mm 40P', '条', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(24, 2, 'SKU024', '排母', '2.54mm 40P', '条', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(25, 3, 'SKU025', 'RJ45水晶头', '超五类 8P8C', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(26, 3, 'SKU026', '网络模块', '超六类', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(27, 3, 'SKU027', '网络面板', '双口 86型', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(28, 4, 'SKU028', '电阻包', '1/4W 常用值', '包', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(29, 4, 'SKU029', '电位器', '10kΩ', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(30, 4, 'SKU030', '瓷片电容包', '常用值', '包', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(31, 4, 'SKU031', '电解电容', '100μF 25V', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(32, 4, 'SKU032', '二极管', '1N4007', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(33, 4, 'SKU033', '三极管', 'S8050', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(34, 4, 'SKU034', '三极管', 'S8550', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(35, 4, 'SKU035', 'LED发光管', '5mm 红色', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(36, 4, 'SKU036', 'LED发光管', '5mm 绿色', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(37, 4, 'SKU037', 'LED发光管', '5mm 黄色', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(38, 4, 'SKU038', 'IC集成电路', 'NE555', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(39, 4, 'SKU039', 'IC集成电路', 'LM7805', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(40, 4, 'SKU040', 'IC集成电路', 'LM358', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(41, 4, 'SKU041', '数码管', '两位共阴 红色', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(42, 4, 'SKU042', '蜂鸣器', '5V 有源', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(43, 5, 'SKU043', '焊锡丝', '0.8mm 含松香', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(44, 5, 'SKU044', '松香', '盒装', '盒', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(45, 5, 'SKU045', '热缩管', 'Φ1-10 套装', '套', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(46, 5, 'SKU046', '电工胶布', '黑色', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(47, 5, 'SKU047', '扎带', '3×100', '包', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(48, 5, 'SKU048', 'PVC线槽', '25×25', '米', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(49, 5, 'SKU049', '螺丝', 'M3/M4 混装', '盒', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(50, 6, 'SKU050', '键盘', 'USB 有线', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(51, 6, 'SKU051', '鼠标', 'USB 有线', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(52, 6, 'SKU052', '屏幕清洁套装', '套装', '套', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(53, 6, 'SKU053', 'A4纸', '70g', '包', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(54, 6, 'SKU054', 'U盘', '32G', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(55, 7, 'SKU055', '防静电手环', '有线', '个', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(56, 7, 'SKU056', '防静电垫', '台面用', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(57, 7, 'SKU057', '烙铁海绵', '方形', '块', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(58, 7, 'SKU058', '酒精', '95% 500ml', '瓶', 0, 'CONSUMABLE', 1, 0, 1, 1, 1),
(59, 7, 'SKU059', '擦机布', '盒装', '包', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(60, 7, 'SKU060', '标签纸', '黄色 10×20', '卷', 0, 'CONSUMABLE', 0, 0, 1, 1, 1),
(61, 7, 'SKU061', '记号笔', '黑色细头', '支', 0, 'CONSUMABLE', 0, 0, 1, 1, 1);

-- ----------------------------------------------------------------------------
-- 7. 库位（通用总库房；各实训室库位待核实后补建再调拨）
-- ----------------------------------------------------------------------------
INSERT INTO stock_location (id, lab_id, location_code, location_name, location_detail, enabled,
                            create_user_id, update_user_id)
VALUES
(1, NULL, 'WH01', '实训中心总库房', '期初统一入账库位，后续按实盘分布调拨到各实训室', 1, 1, 1);

-- ----------------------------------------------------------------------------
-- 8. OPENING 期初单据（仅 5 行数量明确项；document_id 为模块预生成占位 ID）
-- ----------------------------------------------------------------------------
INSERT INTO stock_document (id, document_id, movement_type, status, operator_id,
                            occurred_at, posted_at, reason, create_user_id, update_user_id)
VALUES
(1, 20261015000001, 'OPENING', 'POSTED', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3),
 '期初导入：《实训室设备物料清单与工作流程.docx》中规格单一、数量明确的 5 项耗材；其余 SKU 待实盘确认后另行 OPENING。',
 1, 1);

-- 9. 单据行（入库位=总库房；期初无价格，本地保留的单价/金额快照列为可空，缺价即 NULL）
INSERT INTO stock_document_line (id, stock_document_id, line_no, sku_id,
                                 from_location_id, to_location_id, quantity,
                                 create_user_id, update_user_id)
VALUES
(1, 1, 1, 4, NULL, 1, 3, 1, 1),
(2, 1, 2, 5, NULL, 1, 60, 1, 1),
(3, 1, 3, 11, NULL, 1, 20, 1, 1),
(4, 1, 4, 12, NULL, 1, 30, 1, 1),
(5, 1, 5, 25, NULL, 1, 500, 1, 1);

-- 10. 库存流水（IN；本地保留价值追踪列且 NOT NULL，期初无价格数据，价值变动/均价均记 0）
INSERT INTO stock_movement (id, document_line_id, location_id, sku_id, direction,
                            qty_delta, before_qty, after_qty, value_delta,
                            before_avg_price, after_avg_price,
                            occurred_at, movement_key,
                            create_user_id, update_user_id)
VALUES
(1, 1, 1, 4, 'IN', 3, 0, 3, 0, 0, 0, UTC_TIMESTAMP(3), 'STK:20261015000001:1:IN', 1, 1),
(2, 2, 1, 5, 'IN', 60, 0, 60, 0, 0, 0, UTC_TIMESTAMP(3), 'STK:20261015000001:2:IN', 1, 1),
(3, 3, 1, 11, 'IN', 20, 0, 20, 0, 0, 0, UTC_TIMESTAMP(3), 'STK:20261015000001:3:IN', 1, 1),
(4, 4, 1, 12, 'IN', 30, 0, 30, 0, 0, 0, UTC_TIMESTAMP(3), 'STK:20261015000001:4:IN', 1, 1),
(5, 5, 1, 25, 'IN', 500, 0, 500, 0, 0, 0, UTC_TIMESTAMP(3), 'STK:20261015000001:5:IN', 1, 1);

-- 11. 库存余额（safe=DOCX 预警线，target=safe；version 0）
INSERT INTO stock_balance (id, location_id, sku_id, stock_qty, safe_qty, target_qty,
                           version, create_user_id, update_user_id)
VALUES
(1, 1, 4, 3, 1, 1, 0, 1, 1),
(2, 1, 5, 60, 20, 20, 0, 1, 1),
(3, 1, 11, 20, 5, 5, 0, 1, 1),
(4, 1, 12, 30, 10, 10, 0, 1, 1),
(5, 1, 25, 500, 200, 200, 0, 1, 1);

-- ----------------------------------------------------------------------------
-- 12. 型号数量按台账回填（未删除资产数；重复执行结果不变）
-- ----------------------------------------------------------------------------
UPDATE equipment_model m
SET m.quantity = (
    SELECT COUNT(*)
    FROM equipment_asset a
    WHERE a.model_id = m.id
      AND a.deleted = 0
);

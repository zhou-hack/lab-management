-- 物料与固定资产模块迁移：用于现有 22 表 new_system / new_system_test。
-- 可重复执行；只新增 16 表及 item 权限菜单，不迁移旧设备/物料，不插入业务库存。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DELIMITER //
DROP PROCEDURE IF EXISTS assert_item_material_prerequisites //
CREATE PROCEDURE assert_item_material_prerequisites()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料模块只允许在 new_system 或 new_system_test 执行';
  END IF;

  IF (SELECT COUNT(DISTINCT table_name)
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_type = 'BASE TABLE'
        AND table_name IN (
          'sys_department', 'sys_person_roster', 'sys_user', 'sys_role',
          'sys_permission', 'sys_menu', 'sys_user_role', 'sys_role_permission',
          'sys_role_menu', 'sys_user_data_scope', 'business_policy', 'lab',
          'lab_maintenance_window', 'biz_document', 'approval_task',
          'document_event', 'user_booking_restriction', 'class_period',
          'lab_booking_series', 'lab_booking', 'lab_slot', 'sys_operation_log'
        )) <> 22 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料模块前置校验失败：必须已有现行22表基础结构';
  END IF;

  IF (SELECT COUNT(*) FROM sys_role
      WHERE (id, role_code) IN ((1, 'ADMIN'), (2, 'TEACHER'), (3, 'STUDENT'), (5, 'OPS_ADMIN'))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料模块前置校验失败：预置角色不完整';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_permission
             WHERE (id BETWEEN 4001 AND 4027 OR permission_code LIKE 'item:%')
               AND (id, permission_code) NOT IN (
                 (4001, 'item:material:list'), (4002, 'item:material:manage'),
                 (4003, 'item:asset:list'), (4004, 'item:asset:manage'),
                 (4005, 'item:location:list'), (4006, 'item:location:manage'),
                 (4007, 'item:stock:list'), (4008, 'item:stock:inbound'),
                 (4009, 'item:document:list'), (4010, 'item:movement:list'),
                 (4011, 'item:transfer:create'), (4012, 'item:transfer:receive'),
                 (4013, 'item:settlement:create'), (4014, 'item:settlement:review'),
                 (4015, 'item:overdue:list'), (4016, 'item:claim:list'),
                 (4017, 'item:claim:create'), (4018, 'item:claim:update'),
                 (4019, 'item:claim:cancel'), (4020, 'item:claim:abandon'),
                 (4021, 'item:claim:review'), (4022, 'item:claim:issue'),
                 (4023, 'item:claim:return'), (4024, 'item:plan:list'),
                 (4025, 'item:plan:manage'), (4026, 'item:notification:list'),
                 (4027, 'item:notification:read')
               )) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料权限4000段或item权限码存在冲突';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_menu
             WHERE (id BETWEEN 4100 AND 4112
                    OR menu_code IN ('item_admin', 'item_workspace', 'item_teacher', 'item_claim_center', 'item_plan_list'))
               AND (id, menu_code) NOT IN (
                 (4100, 'item_admin'), (4101, 'item_workspace'), (4110, 'item_teacher'),
                 (4111, 'item_claim_center'), (4112, 'item_plan_list')
               )) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料菜单4100段或菜单码存在冲突';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_role_permission
             WHERE id BETWEEN 4201 AND 4277 AND permission_id NOT BETWEEN 4001 AND 4027) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料角色权限绑定4200段存在冲突';
  END IF;

  IF EXISTS (SELECT 1 FROM sys_role_menu
             WHERE id BETWEEN 4301 AND 4310 AND menu_id NOT BETWEEN 4100 AND 4112) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料角色菜单绑定4300段存在冲突';
  END IF;
END //
CALL assert_item_material_prerequisites() //
DROP PROCEDURE assert_item_material_prerequisites //
DELIMITER ;

-- 1. 统一物料主数据
CREATE TABLE IF NOT EXISTS material (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  material_type VARCHAR(32) NOT NULL COMMENT 'CONSUMABLE/FIXED_ASSET',
  track_mode VARCHAR(32) NOT NULL COMMENT 'QUANTITY/SERIAL',
  sku_code VARCHAR(64) DEFAULT NULL COMMENT '物料型号或SKU编码，可空',
  material_name VARCHAR(128) NOT NULL COMMENT '物料名称',
  specification VARCHAR(256) DEFAULT NULL COMMENT '规格型号',
  base_unit VARCHAR(32) NOT NULL COMMENT '基本单位',
  quantity_mode VARCHAR(32) NOT NULL COMMENT 'EXACT/ESTIMATE',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_sku (sku_code),
  KEY idx_material_name (material_name),
  KEY idx_material_type_enabled (material_type, enabled, deleted),
  CONSTRAINT chk_material_type_track CHECK (
    (material_type = 'CONSUMABLE' AND track_mode = 'QUANTITY')
    OR (material_type = 'FIXED_ASSET' AND track_mode = 'SERIAL')
  ),
  CONSTRAINT chk_material_quantity_mode CHECK (quantity_mode IN ('EXACT', 'ESTIMATE')),
  CONSTRAINT chk_material_enabled CHECK (enabled IN (0, 1)),
  CONSTRAINT chk_material_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一物料表';

-- 2. 库位主数据（先于固定资产，供当前库位引用）
CREATE TABLE IF NOT EXISTS stock_location (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  location_code VARCHAR(64) NOT NULL COMMENT '库位业务码',
  location_name VARCHAR(128) NOT NULL COMMENT '库位名称',
  description VARCHAR(512) DEFAULT NULL COMMENT '库位说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  active_location_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN location_code ELSE NULL END) STORED COMMENT '未删除库位编码，删除后为NULL以允许复用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_location_active_code (active_location_code),
  KEY idx_stock_location_name (location_name),
  CONSTRAINT chk_stock_location_enabled CHECK (enabled IN (0, 1)),
  CONSTRAINT chk_stock_location_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存库位表';

-- 3. 固定资产单台明细
CREATE TABLE IF NOT EXISTS fixed_asset (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  asset_code VARCHAR(64) NOT NULL COMMENT '单台资产唯一编码',
  price DECIMAL(18,2) DEFAULT NULL COMMENT '资产单价，唯一保留的价格字段',
  location_id BIGINT NOT NULL COMMENT '当前库位；IN_TRANSIT时保留源库位',
  position_text VARCHAR(256) DEFAULT NULL COMMENT '库内位置',
  status VARCHAR(32) NOT NULL COMMENT 'IDLE/ON_LOAN/MAINTENANCE/IN_TRANSIT/LOST/SCRAPPED',
  custodian_user_id BIGINT DEFAULT NULL COMMENT '遗留资产负责人列；应用不再读写，负责人取当前库位全部负责人',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fixed_asset_code (asset_code),
  KEY idx_fixed_asset_material_status (material_id, status, deleted),
  KEY idx_fixed_asset_location_status (location_id, status, deleted),
  KEY idx_fixed_asset_custodian (custodian_user_id),
  CONSTRAINT fk_fixed_asset_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_fixed_asset_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_fixed_asset_custodian FOREIGN KEY (custodian_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_fixed_asset_price CHECK (price IS NULL OR price >= 0),
  CONSTRAINT chk_fixed_asset_status CHECK (status IN ('IDLE', 'ON_LOAN', 'MAINTENANCE', 'IN_TRANSIT', 'LOST', 'SCRAPPED')),
  CONSTRAINT chk_fixed_asset_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定资产单台明细表';

-- 4. 库位管理员关系
CREATE TABLE IF NOT EXISTS stock_location_admin (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  location_id BIGINT NOT NULL COMMENT '库位ID',
  user_id BIGINT NOT NULL COMMENT '管理员用户ID',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，恢复关系应更新原行',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_location_admin_relation (location_id, user_id),
  KEY idx_stock_location_admin_user (user_id, location_id, deleted),
  CONSTRAINT fk_stock_location_admin_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_location_admin_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_stock_location_admin_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库位管理员关系表';

-- 5. 耗材库位余额
CREATE TABLE IF NOT EXISTS stock_balance (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  material_id BIGINT NOT NULL COMMENT '耗材物料ID',
  location_id BIGINT NOT NULL COMMENT '库位ID',
  position_text VARCHAR(256) DEFAULT NULL COMMENT '库内位置',
  position_key VARCHAR(256) GENERATED ALWAYS AS (COALESCE(position_text, '')) STORED COMMENT '位置归一键，勿写',
  owned_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '保有数量，含借出',
  available_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '库内可用数量',
  version INT NOT NULL DEFAULT 0 COMMENT '余额条件更新版本',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '库存历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_balance_position (material_id, location_id, position_key),
  KEY idx_stock_balance_location_material (location_id, material_id),
  CONSTRAINT fk_stock_balance_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_balance_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_stock_balance_owned CHECK (owned_quantity >= 0),
  CONSTRAINT chk_stock_balance_available CHECK (available_quantity >= 0 AND owned_quantity >= available_quantity),
  CONSTRAINT chk_stock_balance_version CHECK (version >= 0),
  CONSTRAINT chk_stock_balance_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='耗材库位余额表';

-- 6. 教师预设方案
CREATE TABLE IF NOT EXISTS material_booking_plan (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  owner_teacher_id BIGINT NOT NULL COMMENT '方案所属教师',
  plan_name VARCHAR(128) NOT NULL COMMENT '方案名称',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  KEY idx_material_booking_plan_owner (owner_teacher_id, enabled, deleted),
  CONSTRAINT fk_material_booking_plan_owner FOREIGN KEY (owner_teacher_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_booking_plan_enabled CHECK (enabled IN (0, 1)),
  CONSTRAINT chk_material_booking_plan_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料预约预设方案表';

-- 7. 预设方案行
CREATE TABLE IF NOT EXISTS material_booking_plan_line (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  plan_id BIGINT NOT NULL COMMENT '预设方案ID',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  default_quantity DECIMAL(18,3) NOT NULL COMMENT '默认申请数量',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，恢复关系应更新原行',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_booking_plan_line (plan_id, material_id),
  KEY idx_material_booking_plan_line_material (material_id),
  CONSTRAINT fk_material_plan_line_plan FOREIGN KEY (plan_id) REFERENCES material_booking_plan (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_plan_line_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_plan_line_quantity CHECK (default_quantity > 0),
  CONSTRAINT chk_material_plan_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料预约预设方案行表';

-- 8. 申领主表；id同时是business_chain_id
CREATE TABLE IF NOT EXISTS material_claim (
  id BIGINT NOT NULL COMMENT '申领ID及业务链ID',
  biz_document_id BIGINT NOT NULL COMMENT '共享单据ID',
  applicant_id BIGINT NOT NULL COMMENT '申请教师',
  purpose VARCHAR(512) NOT NULL COMMENT '用途',
  booking_date DATE NOT NULL COMMENT '上海预约日期',
  start_period_id BIGINT NOT NULL COMMENT '开始节次',
  end_period_id BIGINT NOT NULL COMMENT '结束节次',
  booking_start_at DATETIME(3) NOT NULL COMMENT '预约开始（UTC快照）',
  booking_end_at DATETIME(3) NOT NULL COMMENT '预约结束（UTC快照）',
  return_deadline DATETIME(3) NOT NULL COMMENT '上海预约当天23:59对应UTC时间点',
  status VARCHAR(32) NOT NULL COMMENT 'PENDING/APPROVED/PARTIALLY_ISSUED/EXPIRED/CANCELLED/CLOSED/REJECTED',
  revision_no INT NOT NULL DEFAULT 1 COMMENT '当前申领内容版本',
  approved_by BIGINT DEFAULT NULL COMMENT '最近审核人',
  approved_at DATETIME(3) DEFAULT NULL COMMENT '最近审核时间（UTC）',
  reject_reason VARCHAR(512) DEFAULT NULL COMMENT '驳回原因',
  abandon_requested_by BIGINT DEFAULT NULL COMMENT '放弃剩余请求人',
  abandon_requested_at DATETIME(3) DEFAULT NULL COMMENT '放弃剩余请求时间（UTC）',
  abandon_reason VARCHAR(512) DEFAULT NULL COMMENT '放弃剩余原因',
  close_reason VARCHAR(32) DEFAULT NULL COMMENT 'FULFILLED/ABANDONED',
  closed_by BIGINT DEFAULT NULL COMMENT '关闭人',
  closed_at DATETIME(3) DEFAULT NULL COMMENT '关闭时间（UTC）',
  cancelled_by BIGINT DEFAULT NULL COMMENT '取消人',
  cancelled_at DATETIME(3) DEFAULT NULL COMMENT '取消时间（UTC）',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '申领历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_claim_biz_document (biz_document_id),
  KEY idx_material_claim_applicant (applicant_id, create_time),
  KEY idx_material_claim_status_time (status, booking_start_at, booking_end_at),
  KEY idx_material_claim_approved_by (approved_by),
  KEY idx_material_claim_abandon_user (abandon_requested_by),
  KEY idx_material_claim_closed_by (closed_by),
  KEY idx_material_claim_cancelled_by (cancelled_by),
  CONSTRAINT fk_material_claim_biz_document FOREIGN KEY (biz_document_id) REFERENCES biz_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_start_period FOREIGN KEY (start_period_id) REFERENCES class_period (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_end_period FOREIGN KEY (end_period_id) REFERENCES class_period (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_abandon_user FOREIGN KEY (abandon_requested_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_closed_by FOREIGN KEY (closed_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_claim_window CHECK (booking_start_at < booking_end_at AND return_deadline >= booking_end_at),
  CONSTRAINT chk_material_claim_revision CHECK (revision_no >= 1),
  CONSTRAINT chk_material_claim_status CHECK (status IN ('PENDING', 'APPROVED', 'PARTIALLY_ISSUED', 'EXPIRED', 'CANCELLED', 'CLOSED', 'REJECTED')),
  CONSTRAINT chk_material_claim_close_reason CHECK (close_reason IS NULL OR close_reason IN ('FULFILLED', 'ABANDONED')),
  CONSTRAINT chk_material_claim_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料申领主表';

-- 9. 申领行；旧版本只读保留
CREATE TABLE IF NOT EXISTS material_claim_line (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  claim_id BIGINT NOT NULL COMMENT '申领ID',
  revision_no INT NOT NULL COMMENT '申领内容版本',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  requested_quantity DECIMAL(18,3) NOT NULL COMMENT '申请数量',
  issued_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '累计实发数量',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '版本历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_claim_line_version (claim_id, revision_no, material_id),
  KEY idx_material_claim_line_current (claim_id, revision_no),
  KEY idx_material_claim_line_material (material_id),
  CONSTRAINT fk_material_claim_line_claim FOREIGN KEY (claim_id) REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_line_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_claim_line_revision CHECK (revision_no >= 1),
  CONSTRAINT chk_material_claim_line_requested CHECK (requested_quantity > 0),
  CONSTRAINT chk_material_claim_line_issued CHECK (issued_quantity >= 0 AND issued_quantity <= requested_quantity),
  CONSTRAINT chk_material_claim_line_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料申领行表';

-- 10. 当前及历史计划库位分配
CREATE TABLE IF NOT EXISTS material_claim_allocation (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  claim_id BIGINT NOT NULL COMMENT '申领ID',
  claim_line_id BIGINT NOT NULL COMMENT '申领行ID',
  revision_no INT NOT NULL COMMENT '申领内容版本',
  location_id BIGINT NOT NULL COMMENT '计划库位ID',
  planned_quantity DECIMAL(18,3) NOT NULL COMMENT '计划分配数量',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '版本历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_claim_allocation (claim_line_id, location_id),
  KEY idx_material_claim_allocation_claim (claim_id, revision_no, location_id),
  KEY idx_material_claim_allocation_location (location_id, claim_id, revision_no),
  CONSTRAINT fk_material_allocation_claim FOREIGN KEY (claim_id) REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_allocation_line FOREIGN KEY (claim_line_id) REFERENCES material_claim_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_allocation_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_allocation_revision CHECK (revision_no >= 1),
  CONSTRAINT chk_material_allocation_quantity CHECK (planned_quantity > 0),
  CONSTRAINT chk_material_allocation_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='申领计划库位分配表';

-- 11. 审核后的预约预占
CREATE TABLE IF NOT EXISTS material_reservation (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  claim_id BIGINT NOT NULL COMMENT '申领ID',
  claim_line_id BIGINT NOT NULL COMMENT '申领行ID',
  allocation_id BIGINT NOT NULL COMMENT '计划分配ID',
  revision_no INT NOT NULL COMMENT '申领内容版本',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  location_id BIGINT NOT NULL COMMENT '预占库位ID',
  fixed_asset_id BIGINT DEFAULT NULL COMMENT '固定资产预占的单台资产',
  reservation_subject_id BIGINT GENERATED ALWAYS AS (COALESCE(fixed_asset_id, 0)) STORED COMMENT '预占对象归一键，勿写',
  reservation_rule VARCHAR(32) NOT NULL COMMENT 'BOOKING_WINDOW/SHANGHAI_DAY_LOW_TOTAL',
  asset_total_snapshot INT DEFAULT NULL COMMENT '审核时全库保有总数快照，排除LOST/SCRAPPED',
  reserved_quantity DECIMAL(18,3) NOT NULL COMMENT '初始预占数量',
  consumed_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '已消耗预占数量',
  released_quantity DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '已释放预占数量',
  reservation_start_at DATETIME(3) NOT NULL COMMENT '预占开始（UTC）',
  reservation_end_at DATETIME(3) NOT NULL COMMENT '预占结束（UTC，半开区间）',
  status VARCHAR(32) NOT NULL COMMENT 'ACTIVE/CLOSED',
  closed_at DATETIME(3) DEFAULT NULL COMMENT '关闭时间（UTC）',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '预占历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_reservation_subject (allocation_id, reservation_subject_id),
  KEY idx_material_reservation_claim (claim_id, revision_no),
  KEY idx_material_reservation_overlap (material_id, location_id, status, reservation_start_at, reservation_end_at),
  KEY idx_material_reservation_asset_overlap (fixed_asset_id, status, reservation_start_at, reservation_end_at),
  KEY idx_material_reservation_line (claim_line_id),
  KEY idx_material_reservation_allocation (allocation_id),
  CONSTRAINT fk_material_reservation_claim FOREIGN KEY (claim_id) REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_reservation_line FOREIGN KEY (claim_line_id) REFERENCES material_claim_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_reservation_allocation FOREIGN KEY (allocation_id) REFERENCES material_claim_allocation (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_reservation_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_reservation_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_reservation_asset FOREIGN KEY (fixed_asset_id) REFERENCES fixed_asset (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_reservation_revision CHECK (revision_no >= 1),
  CONSTRAINT chk_material_reservation_rule CHECK (reservation_rule IN ('BOOKING_WINDOW', 'SHANGHAI_DAY_LOW_TOTAL')),
  CONSTRAINT chk_material_reservation_asset_total CHECK (asset_total_snapshot IS NULL OR asset_total_snapshot >= 0),
  CONSTRAINT chk_material_reservation_reserved CHECK (reserved_quantity > 0),
  CONSTRAINT chk_material_reservation_consumed CHECK (consumed_quantity >= 0),
  CONSTRAINT chk_material_reservation_released CHECK (released_quantity >= 0),
  CONSTRAINT chk_material_reservation_balance CHECK (consumed_quantity + released_quantity <= reserved_quantity),
  CONSTRAINT chk_material_reservation_asset CHECK (fixed_asset_id IS NULL OR reserved_quantity = 1),
  CONSTRAINT chk_material_reservation_asset_rule CHECK (
    (fixed_asset_id IS NULL AND reservation_rule = 'BOOKING_WINDOW' AND asset_total_snapshot IS NULL)
    OR (fixed_asset_id IS NOT NULL AND asset_total_snapshot IS NOT NULL AND asset_total_snapshot > 0
        AND ((reservation_rule = 'BOOKING_WINDOW' AND asset_total_snapshot >= 5)
          OR (reservation_rule = 'SHANGHAI_DAY_LOW_TOTAL' AND asset_total_snapshot < 5)))
  ),
  CONSTRAINT chk_material_reservation_window CHECK (reservation_start_at < reservation_end_at),
  CONSTRAINT chk_material_reservation_status CHECK (status IN ('ACTIVE', 'CLOSED')),
  CONSTRAINT chk_material_reservation_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料分时段预占表';

-- 12. 库存单据；原移动行之后的处理均生成子单
CREATE TABLE IF NOT EXISTS material_document (
  id BIGINT NOT NULL COMMENT '库存单据主键',
  biz_document_id BIGINT NOT NULL COMMENT '共享单据ID',
  document_no VARCHAR(64) NOT NULL COMMENT '库存单据业务码',
  business_chain_id BIGINT DEFAULT NULL COMMENT '申领业务链ID，独立单据为空',
  document_type VARCHAR(32) NOT NULL COMMENT 'OUTBOUND/INBOUND/TRANSFER及移动子单/OPENING/ADJUST/SCRAP',
  business_scene VARCHAR(32) NOT NULL COMMENT '业务场景快照',
  document_seq INT DEFAULT NULL COMMENT '同业务链同类型序号',
  source_material_document_id BIGINT DEFAULT NULL COMMENT '来源库存单据ID',
  location_id BIGINT DEFAULT NULL COMMENT '业务库位ID',
  from_location_id BIGINT DEFAULT NULL COMMENT '移动源库位ID',
  to_location_id BIGINT DEFAULT NULL COMMENT '移动目标库位ID',
  transfer_status VARCHAR(32) DEFAULT NULL COMMENT 'PENDING_RECEIPT/PARTIALLY_RECEIVED/PENDING_RETURN/CLOSED',
  received_by BIGINT DEFAULT NULL COMMENT '移动接收或退回确认人',
  received_at DATETIME(3) DEFAULT NULL COMMENT '移动接收或退回确认时间（UTC）',
  review_status VARCHAR(32) DEFAULT NULL COMMENT 'PENDING_REVIEW/APPROVED/REJECTED',
  reviewed_by BIGINT DEFAULT NULL COMMENT '复核人',
  reviewed_at DATETIME(3) DEFAULT NULL COMMENT '复核时间（UTC）',
  review_reason VARCHAR(512) DEFAULT NULL COMMENT '复核意见',
  operator_id BIGINT NOT NULL COMMENT '经办管理员',
  responsible_teacher_id BIGINT DEFAULT NULL COMMENT '责任教师',
  occurred_at DATETIME(3) NOT NULL COMMENT '业务发生时间（UTC）',
  posted_at DATETIME(3) DEFAULT NULL COMMENT '过账时间（UTC），待复核或驳回为空',
  process_status VARCHAR(32) DEFAULT NULL COMMENT 'UNPROCESSED/PARTIAL/PROCESSED',
  return_status VARCHAR(32) DEFAULT NULL COMMENT 'ON_TIME/OVERDUE',
  overdue_reason VARCHAR(512) DEFAULT NULL COMMENT '逾期归还原因',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '库存单据请求幂等键',
  version INT NOT NULL DEFAULT 0 COMMENT '处理状态条件更新版本',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '单据历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_document_no (document_no),
  UNIQUE KEY uk_material_document_biz_document (biz_document_id),
  UNIQUE KEY uk_material_document_chain_seq (business_chain_id, document_type, document_seq),
  UNIQUE KEY uk_material_document_idempotency (operator_id, document_type, idempotency_key),
  KEY idx_material_document_chain (business_chain_id, document_type, occurred_at),
  KEY idx_material_document_source (source_material_document_id),
  KEY idx_material_document_transfer_pending (to_location_id, document_type, transfer_status, occurred_at),
  KEY idx_material_document_location_time (location_id, document_type, occurred_at),
  KEY idx_material_document_review (review_status, document_type, location_id),
  KEY idx_material_document_received_by (received_by),
  KEY idx_material_document_reviewed_by (reviewed_by),
  KEY idx_material_document_operator (operator_id),
  KEY idx_material_document_responsible (responsible_teacher_id),
  CONSTRAINT fk_material_document_biz_document FOREIGN KEY (biz_document_id) REFERENCES biz_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_chain FOREIGN KEY (business_chain_id) REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_source FOREIGN KEY (source_material_document_id) REFERENCES material_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_from_location FOREIGN KEY (from_location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_to_location FOREIGN KEY (to_location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_received_by FOREIGN KEY (received_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_responsible FOREIGN KEY (responsible_teacher_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_document_type CHECK (document_type IN ('OUTBOUND', 'INBOUND', 'TRANSFER', 'TRANSFER_RECEIPT', 'TRANSFER_RETURN', 'TRANSFER_SETTLEMENT', 'OPENING', 'ADJUST', 'SCRAP')),
  CONSTRAINT chk_material_document_scene CHECK (business_scene IN ('CLAIM_ISSUE', 'CLAIM_RETURN', 'OPENING', 'REPLENISHMENT', 'TRANSFER_SEND', 'TRANSFER_RECEIVE', 'TRANSFER_RETURN', 'TRANSFER_SETTLE', 'IN_STOCK_SETTLE')),
  CONSTRAINT chk_material_document_seq CHECK (document_seq IS NULL OR document_seq > 0),
  CONSTRAINT chk_material_document_source CHECK (source_material_document_id IS NULL OR source_material_document_id <> id),
  CONSTRAINT chk_material_document_locations CHECK (from_location_id IS NULL OR to_location_id IS NULL OR from_location_id <> to_location_id),
  CONSTRAINT chk_material_document_transfer_status CHECK (transfer_status IS NULL OR transfer_status IN ('PENDING_RECEIPT', 'PARTIALLY_RECEIVED', 'PENDING_RETURN', 'CLOSED')),
  CONSTRAINT chk_material_document_receive CHECK ((received_by IS NULL AND received_at IS NULL) OR (received_by IS NOT NULL AND received_at IS NOT NULL)),
  CONSTRAINT chk_material_document_review_status CHECK (review_status IS NULL OR review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
  CONSTRAINT chk_material_document_review_pair CHECK ((reviewed_by IS NULL AND reviewed_at IS NULL) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CONSTRAINT chk_material_document_review_actor CHECK (reviewed_by IS NULL OR reviewed_by <> operator_id),
  CONSTRAINT chk_material_document_review_scope CHECK ((document_type IN ('SCRAP', 'ADJUST', 'TRANSFER_SETTLEMENT') AND review_status IS NOT NULL) OR (document_type NOT IN ('SCRAP', 'ADJUST', 'TRANSFER_SETTLEMENT') AND review_status IS NULL)),
  CONSTRAINT chk_material_document_pending_post CHECK (review_status IS NULL OR review_status NOT IN ('PENDING_REVIEW', 'REJECTED') OR posted_at IS NULL),
  CONSTRAINT chk_material_document_approved_post CHECK (review_status IS NULL OR review_status <> 'APPROVED' OR posted_at IS NOT NULL),
  CONSTRAINT chk_material_document_regular_post CHECK (document_type IN ('SCRAP', 'ADJUST', 'TRANSFER_SETTLEMENT') OR posted_at IS NOT NULL),
  CONSTRAINT chk_material_document_process CHECK (process_status IS NULL OR process_status IN ('UNPROCESSED', 'PARTIAL', 'PROCESSED')),
  CONSTRAINT chk_material_document_return CHECK (return_status IS NULL OR return_status IN ('ON_TIME', 'OVERDUE')),
  CONSTRAINT chk_material_document_overdue CHECK (return_status IS NULL OR return_status <> 'OVERDUE' OR overdue_reason IS NOT NULL),
  CONSTRAINT chk_material_document_version CHECK (version >= 0),
  CONSTRAINT chk_material_document_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料库存单据主表';

-- 13. 库存单据行；source字段只引用原行，不覆盖原行
CREATE TABLE IF NOT EXISTS material_document_line (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  material_document_id BIGINT NOT NULL COMMENT '所属库存单据ID',
  claim_line_id BIGINT DEFAULT NULL COMMENT '申领行ID',
  source_material_document_line_id BIGINT DEFAULT NULL COMMENT '来源库存单据行ID',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  fixed_asset_id BIGINT DEFAULT NULL COMMENT '单台资产ID',
  quantity DECIMAL(18,3) NOT NULL COMMENT '本次实际数量；原移动行仅表示发出量',
  position_text VARCHAR(256) DEFAULT NULL COMMENT '实际库内位置快照',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '单据行历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  KEY idx_material_document_line_document (material_document_id),
  KEY idx_material_document_line_source (source_material_document_line_id),
  KEY idx_material_document_line_claim (claim_line_id),
  KEY idx_material_document_line_material (material_id, material_document_id),
  KEY idx_material_document_line_asset (fixed_asset_id, material_document_id),
  CONSTRAINT fk_material_document_line_document FOREIGN KEY (material_document_id) REFERENCES material_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_line_claim FOREIGN KEY (claim_line_id) REFERENCES material_claim_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_line_source FOREIGN KEY (source_material_document_line_id) REFERENCES material_document_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_line_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_document_line_asset FOREIGN KEY (fixed_asset_id) REFERENCES fixed_asset (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_document_line_source CHECK (source_material_document_line_id IS NULL OR source_material_document_line_id <> id),
  CONSTRAINT chk_material_document_line_quantity CHECK (quantity > 0),
  CONSTRAINT chk_material_document_line_asset CHECK (fixed_asset_id IS NULL OR quantity = 1),
  CONSTRAINT chk_material_document_line_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料库存单据行表';

-- 14. 归还、在途与在库核销明细
CREATE TABLE IF NOT EXISTS material_settlement (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  material_document_id BIGINT NOT NULL COMMENT '所属库存单据ID',
  source_material_document_line_id BIGINT DEFAULT NULL COMMENT '原出库或原移动发出行ID',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  fixed_asset_id BIGINT DEFAULT NULL COMMENT '单台资产ID',
  settlement_type VARCHAR(32) NOT NULL COMMENT 'CONSUMED/LOST/DAMAGED/SCRAP/ADJUST',
  quantity DECIMAL(18,3) NOT NULL COMMENT '核销数量；ADJUST用于在库差异调减',
  target_asset_status VARCHAR(32) DEFAULT NULL COMMENT '固定资产调整后状态：IDLE/MAINTENANCE/LOST/SCRAPPED',
  reason VARCHAR(512) NOT NULL COMMENT '核销原因',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '核销历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  KEY idx_material_settlement_document (material_document_id),
  KEY idx_material_settlement_source (source_material_document_line_id),
  KEY idx_material_settlement_material (material_id, settlement_type),
  KEY idx_material_settlement_asset (fixed_asset_id),
  CONSTRAINT fk_material_settlement_document FOREIGN KEY (material_document_id) REFERENCES material_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_settlement_source FOREIGN KEY (source_material_document_line_id) REFERENCES material_document_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_settlement_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_settlement_asset FOREIGN KEY (fixed_asset_id) REFERENCES fixed_asset (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_settlement_type CHECK (settlement_type IN ('CONSUMED', 'LOST', 'DAMAGED', 'SCRAP', 'ADJUST')),
  CONSTRAINT chk_material_settlement_quantity CHECK (quantity > 0),
  CONSTRAINT chk_material_settlement_asset CHECK (fixed_asset_id IS NULL OR quantity = 1),
  CONSTRAINT chk_material_settlement_target_asset CHECK (
    (fixed_asset_id IS NULL AND target_asset_status IS NULL)
    OR (fixed_asset_id IS NOT NULL AND (target_asset_status IS NULL OR target_asset_status IN ('IDLE', 'MAINTENANCE', 'LOST', 'SCRAPPED')))
  ),
  CONSTRAINT chk_material_settlement_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料核销明细表';

-- 15. 不可变库存流水
CREATE TABLE IF NOT EXISTS stock_movement (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  movement_key VARCHAR(128) NOT NULL COMMENT '流水幂等唯一键',
  movement_type VARCHAR(32) NOT NULL COMMENT '业务类型',
  direction VARCHAR(8) NOT NULL COMMENT 'IN/OUT',
  material_id BIGINT NOT NULL COMMENT '物料ID',
  fixed_asset_id BIGINT DEFAULT NULL COMMENT '单台资产ID',
  location_id BIGINT NOT NULL COMMENT '库存库位ID',
  quantity DECIMAL(18,3) NOT NULL COMMENT '变动数量',
  material_document_id BIGINT NOT NULL COMMENT '依据库存单据ID',
  material_document_line_id BIGINT DEFAULT NULL COMMENT '依据库存单据行ID；无实体行的核销可空',
  operator_id BIGINT NOT NULL COMMENT '经办人',
  occurred_at DATETIME(3) NOT NULL COMMENT '业务发生时间（UTC）',
  before_owned_quantity DECIMAL(18,3) DEFAULT NULL COMMENT '变动前保有数量，固定资产可空',
  after_owned_quantity DECIMAL(18,3) DEFAULT NULL COMMENT '变动后保有数量，固定资产可空',
  before_available_quantity DECIMAL(18,3) DEFAULT NULL COMMENT '变动前可用数量，固定资产可空',
  after_available_quantity DECIMAL(18,3) DEFAULT NULL COMMENT '变动后可用数量，固定资产可空',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '追加表保持创建值',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '流水历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '追加表不使用',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_movement_key (movement_key),
  KEY idx_stock_movement_material_time (material_id, location_id, occurred_at),
  KEY idx_stock_movement_asset_time (fixed_asset_id, occurred_at),
  KEY idx_stock_movement_document (material_document_id, material_document_line_id),
  KEY idx_stock_movement_location_time (location_id, occurred_at),
  KEY idx_stock_movement_operator (operator_id),
  CONSTRAINT fk_stock_movement_material FOREIGN KEY (material_id) REFERENCES material (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_movement_asset FOREIGN KEY (fixed_asset_id) REFERENCES fixed_asset (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_movement_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_movement_document FOREIGN KEY (material_document_id) REFERENCES material_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_movement_document_line FOREIGN KEY (material_document_line_id) REFERENCES material_document_line (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_stock_movement_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_stock_movement_direction CHECK (direction IN ('IN', 'OUT')),
  CONSTRAINT chk_stock_movement_quantity CHECK (quantity > 0),
  CONSTRAINT chk_stock_movement_asset CHECK (fixed_asset_id IS NULL OR quantity = 1),
  CONSTRAINT chk_stock_movement_before_owned CHECK (before_owned_quantity IS NULL OR before_owned_quantity >= 0),
  CONSTRAINT chk_stock_movement_after_owned CHECK (after_owned_quantity IS NULL OR after_owned_quantity >= 0),
  CONSTRAINT chk_stock_movement_before_available CHECK (before_available_quantity IS NULL OR before_available_quantity >= 0),
  CONSTRAINT chk_stock_movement_after_available CHECK (after_available_quantity IS NULL OR after_available_quantity >= 0),
  CONSTRAINT chk_stock_movement_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变库存流水表';

-- 16. 逾期通知；read_at之外内容只读
CREATE TABLE IF NOT EXISTS material_notification (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  dedupe_key VARCHAR(128) NOT NULL COMMENT '通知事件去重键',
  recipient_user_id BIGINT NOT NULL COMMENT '接收人',
  notification_type VARCHAR(32) NOT NULL COMMENT '通知类型：审批/批准/归还提醒/代领/逾期',
  notification_stage VARCHAR(32) NOT NULL COMMENT 'IMMEDIATE/DAILY/TWO_HOURLY',
  reminder_date DATE DEFAULT NULL COMMENT '每日提醒的上海自然日',
  claim_id BIGINT NOT NULL COMMENT '申领ID',
  material_document_id BIGINT DEFAULT NULL COMMENT '未还出库单ID，非逾期通知可空',
  biz_document_id BIGINT NOT NULL COMMENT '未还出库单对应的共享单据ID',
  title VARCHAR(128) NOT NULL COMMENT '通知标题',
  content VARCHAR(512) NOT NULL COMMENT '通知摘要',
  read_at DATETIME(3) DEFAULT NULL COMMENT '已读时间（UTC）',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '已读更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '通知历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，系统任务可空',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_notification_dedupe (recipient_user_id, dedupe_key),
  KEY idx_material_notification_recipient (recipient_user_id, read_at, create_time),
  KEY idx_material_notification_document (material_document_id, notification_stage, reminder_date),
  KEY idx_material_notification_claim (claim_id),
  CONSTRAINT fk_material_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_notification_claim FOREIGN KEY (claim_id) REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_notification_document FOREIGN KEY (material_document_id) REFERENCES material_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_notification_biz_document FOREIGN KEY (biz_document_id) REFERENCES biz_document (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_notification_type CHECK (notification_type IN ('CLAIM_REVIEW_PENDING', 'CLAIM_APPROVED', 'RETURN_REMINDER', 'PICKUP_DELEGATED', 'OVERDUE_UNRETURNED')),
  CONSTRAINT chk_material_notification_stage CHECK (notification_stage IN ('IMMEDIATE', 'DAILY', 'TWO_HOURLY')),
  CONSTRAINT chk_material_notification_reminder CHECK ((notification_stage IN ('IMMEDIATE', 'TWO_HOURLY') AND reminder_date IS NULL) OR (notification_stage = 'DAILY' AND reminder_date IS NOT NULL)),
  CONSTRAINT chk_material_notification_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料通知表';

-- item权限：4001-4027。TEACHER仅本人申领、方案、通知；ADMIN为管理端；OPS_ADMIN不参与物料流程。
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
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
(4027, 'item:notification:read', '通知已读', 'item', 1);

-- 3个页面及2个对应父目录；父子菜单绑定相同可见角色，不使用横杠路由。
INSERT IGNORE INTO sys_menu
(id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(4100, NULL, 'item_admin', '物料管理', '/admin/item', NULL, NULL, 'DIRECTORY', 50, 1),
(4101, 4100, 'item_workspace', '物料工作台', '/admin/item/workspace', 'ItemWorkspace', 4001, 'PAGE', 1, 1),
(4110, NULL, 'item_teacher', '物料服务', '/item', NULL, NULL, 'DIRECTORY', 25, 1),
(4111, 4110, 'item_claim_center', '我的物料申领', '/item/claims', 'ItemClaimCenter', 4016, 'PAGE', 1, 1),
(4112, 4110, 'item_plan_list', '我的预设方案', '/item/plans', 'ItemPlanList', 4024, 'PAGE', 2, 1);

-- ADMIN管理端权限：4001-4016、4020-4023、4026-4027。
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(4201, 1, 4001, 1), (4202, 1, 4002, 1), (4203, 1, 4003, 1),
(4204, 1, 4004, 1), (4205, 1, 4005, 1), (4206, 1, 4006, 1),
(4207, 1, 4007, 1), (4208, 1, 4008, 1), (4209, 1, 4009, 1),
(4210, 1, 4010, 1), (4211, 1, 4011, 1), (4212, 1, 4012, 1),
(4213, 1, 4013, 1), (4214, 1, 4014, 1), (4215, 1, 4015, 1),
(4216, 1, 4016, 1), (4217, 1, 4020, 1), (4218, 1, 4021, 1),
(4219, 1, 4022, 1), (4220, 1, 4023, 1), (4221, 1, 4026, 1),
(4222, 1, 4027, 1),
-- TEACHER本人权限：逾期4015、申领4016-4020、方案4024-4025、通知4026-4027。
(4231, 2, 4016, 1), (4232, 2, 4017, 1), (4233, 2, 4018, 1),
(4234, 2, 4019, 1), (4235, 2, 4020, 1), (4236, 2, 4024, 1),
(4237, 2, 4025, 1), (4238, 2, 4026, 1), (4239, 2, 4027, 1),
(4240, 2, 4015, 1);

-- 管理父子树仅ADMIN；教师父子树仅TEACHER；OPS_ADMIN/STUDENT无绑定。
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(4301, 1, 4100, 1), (4302, 1, 4101, 1),
(4308, 2, 4110, 1), (4309, 2, 4111, 1), (4310, 2, 4112, 1);

-- 兼容已执行过旧版种子的库：重放时收回 OPS_ADMIN 的物料流程权限和菜单。
DELETE rp FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = 5 AND p.module_code = 'item';
DELETE FROM sys_role_menu
WHERE role_id = 5 AND menu_id IN (4100, 4101, 4110, 4111, 4112);

DELIMITER //
DROP PROCEDURE IF EXISTS assert_item_material_complete //
CREATE PROCEDURE assert_item_material_complete()
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 38 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料模块完成校验失败：目标库必须为38表';
  END IF;
  IF (SELECT COUNT(*) FROM sys_permission WHERE module_code = 'item' AND id BETWEEN 4001 AND 4027) <> 27 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料权限种子不完整或编号不一致';
  END IF;
  IF (SELECT COUNT(*) FROM sys_menu WHERE id IN (4100, 4101, 4110, 4111, 4112)
      AND menu_code IN ('item_admin', 'item_workspace', 'item_teacher', 'item_claim_center', 'item_plan_list')) <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料菜单种子不完整或编号不一致';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 1 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 22
     OR (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 2 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 10
     OR EXISTS (SELECT 1 FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id IN (3, 5) AND p.module_code = 'item') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料角色权限绑定校验失败';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id IN (4100, 4101, 4110, 4111, 4112) AND enabled = 1 AND deleted = 0) <> 2
     OR (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 2 AND menu_id IN (4100, 4101, 4110, 4111, 4112) AND enabled = 1 AND deleted = 0) <> 3
     OR EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id IN (3, 5) AND menu_id IN (4100, 4101, 4110, 4111, 4112)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物料角色菜单绑定校验失败';
  END IF;
END //
CALL assert_item_material_complete() //
DROP PROCEDURE assert_item_material_complete //
DELIMITER ;

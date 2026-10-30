-- new_system 精简基线：仅用于全新的 new_system / new_system_test 空库。
DELIMITER //
CREATE PROCEDURE assert_new_system_database()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '本基线只允许在 new_system 或 new_system_test 空库执行';
  END IF;
END //
CALL assert_new_system_database()//
DROP PROCEDURE assert_new_system_database//
DELIMITER ;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET FOREIGN_KEY_CHECKS=0;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `parent_id` bigint DEFAULT NULL COMMENT '父部门 ID（NULL=根节点，禁止组织循环由服务端校验）',
  `department_code` varchar(64) NOT NULL COMMENT '部门编码',
  `department_name` varchar(128) NOT NULL COMMENT '部门名称',
  `department_type` varchar(32) NOT NULL COMMENT '类型：SCHOOL/COLLEGE/ASSET_OFFICE',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1启用 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`department_code`),
  KEY `idx_sys_department_parent` (`parent_id`),
  CONSTRAINT `fk_sys_department_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `chk_sys_department_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_department_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织表（SCHOOL/COLLEGE/ASSET_OFFICE）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_person_roster` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `person_no` varchar(64) NOT NULL COMMENT '学工号（唯一，删除后不复用）',
  `person_type` varchar(32) NOT NULL COMMENT '身份类型：STUDENT/TEACHER',
  `real_name` varchar(128) NOT NULL COMMENT '姓名',
  `phone` varchar(32) NOT NULL COMMENT '手机号（唯一，验证码登录匹配凭证）',
  `department_id` bigint NOT NULL COMMENT '所属部门（→ sys_department.id）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1有效 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_person_roster_no` (`person_no`),
  UNIQUE KEY `uk_sys_person_roster_phone` (`phone`),
  KEY `idx_sys_person_roster_dept` (`department_id`),
  CONSTRAINT `fk_sys_person_roster_dept` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `chk_sys_person_roster_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_person_roster_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预录身份名单表（学生/老师首登匹配依据）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `roster_id` bigint DEFAULT NULL COMMENT '名单 ID（→ sys_person_roster.id；管理员为 NULL）',
  `user_type` varchar(32) NOT NULL COMMENT '用户类型：STUDENT/TEACHER/ADMIN',
  `real_name` varchar(128) NOT NULL COMMENT '姓名',
  `account_name` varchar(64) DEFAULT NULL COMMENT '登录账号（管理员必填；唯一）',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号（学生/老师必填；唯一）',
  `person_no` varchar(64) DEFAULT NULL COMMENT '学工号（唯一，删除后不复用）',
  `department_id` bigint NOT NULL COMMENT '所属部门（→ sys_department.id）',
  `password_hash` varchar(255) DEFAULT NULL COMMENT '密码散列（带算法参数；管理员必填）',
  `password_changed_at` datetime(3) DEFAULT NULL COMMENT '最近改密时间（UTC，仅审计记录；2026-10-19 需求定稿不强制更换）',
  `failed_login_count` int NOT NULL DEFAULT '0' COMMENT '连续登录失败次数（达 5 次锁定 30 分钟）',
  `locked_until` datetime(3) DEFAULT NULL COMMENT '锁定截止时间（UTC）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1正常 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，自引用不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_roster` (`roster_id`),
  UNIQUE KEY `uk_sys_user_account` (`account_name`),
  UNIQUE KEY `uk_sys_user_phone` (`phone`),
  UNIQUE KEY `uk_sys_user_person_no` (`person_no`),
  KEY `idx_sys_user_dept_type` (`department_id`,`user_type`,`enabled`),
  CONSTRAINT `fk_sys_user_dept` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `fk_sys_user_roster` FOREIGN KEY (`roster_id`) REFERENCES `sys_person_roster` (`id`),
  CONSTRAINT `chk_sys_user_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_user_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键',
  `role_code` varchar(64) NOT NULL COMMENT '角色标识：STUDENT/TEACHER/ASSISTANT/ADMIN',
  `role_name` varchar(128) NOT NULL COMMENT '角色名称',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1正常 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  CONSTRAINT `chk_sys_role_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_role_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL COMMENT '主键',
  `permission_code` varchar(128) NOT NULL COMMENT '权限标识（模块:资源:操作，唯一）',
  `permission_name` varchar(128) NOT NULL COMMENT '权限名称',
  `module_code` varchar(64) NOT NULL COMMENT '所属模块编码',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1正常 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
  KEY `idx_sys_permission_module` (`module_code`),
  CONSTRAINT `chk_sys_permission_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_permission_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '主键',
  `parent_id` bigint DEFAULT NULL COMMENT '父菜单 ID（NULL=根节点）',
  `menu_code` varchar(64) NOT NULL COMMENT '菜单编码（唯一）',
  `menu_name` varchar(128) NOT NULL COMMENT '菜单名称',
  `route_path` varchar(255) DEFAULT NULL COMMENT '路由地址（目录/页面有值）',
  `component_key` varchar(64) DEFAULT NULL COMMENT '前端组件键（仅可对应前端白名单）',
  `permission_id` bigint DEFAULT NULL COMMENT '绑定权限（→ sys_permission.id，动作类必填）',
  `menu_type` varchar(32) NOT NULL COMMENT '类型：DIRECTORY/PAGE/ACTION',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同级排序（升序）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1正常 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`menu_code`),
  KEY `idx_sys_menu_parent` (`parent_id`),
  KEY `idx_sys_menu_permission` (`permission_id`),
  CONSTRAINT `fk_sys_menu_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_menu` (`id`),
  CONSTRAINT `fk_sys_menu_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `chk_sys_menu_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_menu_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表（动态路由来源）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID（→ sys_user.id）',
  `role_id` bigint NOT NULL COMMENT '角色 ID（→ sys_role.id）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1有效 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`),
  KEY `idx_sys_user_role_role` (`role_id`),
  CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_sys_user_role_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_user_role_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色 ID（→ sys_role.id）',
  `permission_id` bigint NOT NULL COMMENT '权限 ID（→ sys_permission.id）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1有效 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`,`permission_id`),
  KEY `idx_sys_role_permission_perm` (`permission_id`),
  CONSTRAINT `fk_sys_role_permission_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `fk_sys_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `chk_sys_role_permission_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_role_permission_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色 ID（→ sys_role.id）',
  `menu_id` bigint NOT NULL COMMENT '菜单 ID（→ sys_menu.id）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1有效 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`),
  CONSTRAINT `fk_sys_role_menu_menu` FOREIGN KEY (`menu_id`) REFERENCES `sys_menu` (`id`),
  CONSTRAINT `fk_sys_role_menu_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `chk_sys_role_menu_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_role_menu_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_data_scope` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID（→ sys_user.id）',
  `scope_type` varchar(32) NOT NULL COMMENT '范围类型：ALL/DEPARTMENT/CAMPUS/BUILDING/LAB',
  `department_id` bigint DEFAULT NULL COMMENT '目标部门（→ sys_department.id，scope_type=DEPARTMENT 时必填）',
  `campus_id` bigint DEFAULT NULL COMMENT '目标校区（→ campus.id，跨模块，外键由空间模块补充）',
  `building_id` bigint DEFAULT NULL COMMENT '目标楼栋（→ building.id，跨模块，外键由空间模块补充）',
  `lab_id` bigint DEFAULT NULL COMMENT '目标实验室（→ lab.id，跨模块，外键由空间模块补充）',
  `access_mode` varchar(32) NOT NULL COMMENT '操作级别：READ/WRITE（WRITE 含 READ）',
  `scope_target_id` bigint GENERATED ALWAYS AS (coalesce(`department_id`,`campus_id`,`building_id`,`lab_id`,0)) STORED COMMENT '范围目标归一键（生成列，勿写）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1有效 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_data_scope` (`user_id`,`scope_type`,`access_mode`,`scope_target_id`),
  KEY `idx_sys_user_data_scope_dept` (`department_id`),
  KEY `fk_sys_user_data_scope_lab` (`lab_id`),
  CONSTRAINT `fk_sys_user_data_scope_dept` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `fk_sys_user_data_scope_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`),
  CONSTRAINT `fk_sys_user_data_scope_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_sys_user_data_scope_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_user_data_scope_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户数据范围表（管理员归口授权）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `business_policy` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `policy_code` varchar(64) NOT NULL COMMENT '策略编码',
  `policy_version` int NOT NULL COMMENT '版本号（同码递增，从 1 起）',
  `policy_name` varchar(128) NOT NULL COMMENT '策略名称',
  `effective_from` datetime(3) NOT NULL COMMENT '生效开始（UTC）',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '生效结束（UTC，NULL=长期）',
  `config` json NOT NULL COMMENT '策略参数（固定结构 JSON）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1启用 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_policy_code_version` (`policy_code`,`policy_version`),
  CONSTRAINT `chk_business_policy_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_business_policy_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务策略参数表（版本化）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `lab_name` varchar(128) NOT NULL COMMENT '场地名称（全局唯一，删除后不复用）',
  `lab_type` varchar(32) NOT NULL COMMENT '类型：CLASSROOM教室/TRAINING_ROOM实训室',
  `location_detail` varchar(128) DEFAULT NULL COMMENT '位置（自由文本，如 A 楼七层 A703）',
  `capacity` int NOT NULL COMMENT '容纳人数（>0）',
  `facilities` text COMMENT '配套设备清单（自由文本）',
  `open_start` time NOT NULL DEFAULT '08:00:00' COMMENT '每日开放开始（上海本地时间）',
  `open_end` time NOT NULL DEFAULT '21:00:00' COMMENT '每日开放结束（上海本地时间，须晚于 open_start）',
  `status` varchar(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE可用/MAINTAINING维护中/CLOSED关闭',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '可见性：1可见 2不可见（停用；预约/查询列表只出 1，2026-10-19 需求定稿新增）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lab_name` (`lab_name`),
  KEY `idx_lab_type` (`lab_type`),
  KEY `idx_lab_status` (`status`),
  CONSTRAINT `chk_lab_capacity` CHECK ((`capacity` > 0)),
  CONSTRAINT `chk_lab_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_lab_open` CHECK ((`open_start` < `open_end`)),
  CONSTRAINT `chk_lab_status` CHECK ((`status` in (_utf8mb4'AVAILABLE',_utf8mb4'MAINTAINING',_utf8mb4'CLOSED'))),
  CONSTRAINT `chk_lab_type` CHECK ((`lab_type` in (_utf8mb4'CLASSROOM',_utf8mb4'TRAINING_ROOM'))),
  CONSTRAINT `chk_lab_visible` CHECK ((`visible` in (1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地表（教室/实训室空间主档，单表）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_maintenance_window` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `lab_id` bigint NOT NULL COMMENT '场地（→ lab.id）',
  `start_at` datetime(3) NOT NULL COMMENT '维修开始（UTC）',
  `end_at` datetime(3) NOT NULL COMMENT '维修结束（UTC）',
  `reason` varchar(512) DEFAULT NULL COMMENT '维修事由',
  `cancelled_at` datetime(3) DEFAULT NULL COMMENT '取消时间（UTC；NULL=生效中）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  KEY `idx_mw_lab_time` (`lab_id`,`start_at`,`end_at`),
  CONSTRAINT `fk_mw_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`),
  CONSTRAINT `chk_mw_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_mw_time` CHECK ((`start_at` < `end_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地维修时段（短期窗口，窗口内禁止新预约）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_document` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `document_no` varchar(64) NOT NULL COMMENT '单据编号（前缀+雪花，唯一）',
  `document_type` varchar(32) NOT NULL COMMENT '单据类型：EQUIPMENT_BOOKING（后续扩展 LAB_BOOKING 等）',
  `owner_user_id` bigint NOT NULL COMMENT '归属用户（→ sys_user.id）',
  `department_id` bigint NOT NULL COMMENT '归属部门（→ sys_department.id）',
  `policy_id` bigint DEFAULT NULL COMMENT '审批策略（→ business_policy.id；外键在 2.4 补充）',
  `submitted_at` datetime(3) DEFAULT NULL COMMENT '提交时间（UTC）',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键（客户端提供，缺省服务端生成 UUID）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_document_no` (`document_no`),
  UNIQUE KEY `uk_biz_document_idem` (`owner_user_id`,`document_type`,`idempotency_key`),
  KEY `idx_biz_document_policy` (`policy_id`),
  KEY `idx_biz_document_owner` (`owner_user_id`,`document_type`),
  KEY `idx_biz_document_department` (`department_id`),
  CONSTRAINT `fk_biz_document_department` FOREIGN KEY (`department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `fk_biz_document_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_biz_document_policy` FOREIGN KEY (`policy_id`) REFERENCES `business_policy` (`id`),
  CONSTRAINT `chk_biz_document_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一单据表（所有审批型业务共用）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_task` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `document_id` bigint NOT NULL COMMENT '所属单据（→ biz_document.id）',
  `stage_code` varchar(64) NOT NULL COMMENT '审批阶段：TEACHER_REVIEW/ADMIN_REVIEW',
  `sequence_no` int NOT NULL COMMENT '阶段顺序号（从 1 起）',
  `round_no` int NOT NULL COMMENT '审批轮次（默认 1，驳回重报递增）',
  `required_role` varchar(64) NOT NULL COMMENT '要求角色码（→ sys_role.role_code）',
  `assigned_user_id` bigint DEFAULT NULL COMMENT '指定审批人（→ sys_user.id；NULL=按角色+范围匹配）',
  `assigned_department_id` bigint DEFAULT NULL COMMENT '指定审批部门（→ sys_department.id）',
  `status` varchar(32) NOT NULL COMMENT '任务状态：WAITING/READY/APPROVED/REJECTED/CANCELLED',
  `available_at` datetime(3) NOT NULL COMMENT '任务生效时间（UTC，READY 时可操作）',
  `remind_at` datetime(3) DEFAULT NULL COMMENT '催办时间（UTC）',
  `escalate_at` datetime(3) DEFAULT NULL COMMENT '升级时间（UTC）',
  `timeout_at` datetime(3) DEFAULT NULL COMMENT '超时时间（UTC）',
  `timeout_action` varchar(32) NOT NULL COMMENT '超时动作：本期固定 NONE',
  `decided_by` bigint DEFAULT NULL COMMENT '实际审批人（→ sys_user.id；代批时与 assigned 不同）',
  `decided_at` datetime(3) DEFAULT NULL COMMENT '审批时间（UTC）',
  `decision_comment` text COMMENT '审批意见（驳回必填）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_task` (`document_id`,`stage_code`,`sequence_no`,`round_no`),
  KEY `idx_approval_task_assignee` (`assigned_user_id`,`status`,`available_at`),
  KEY `idx_approval_task_dept_role` (`assigned_department_id`,`required_role`,`status`),
  KEY `idx_approval_task_status_timeout` (`status`,`timeout_at`),
  KEY `idx_approval_task_document` (`document_id`),
  KEY `fk_approval_task_role` (`required_role`),
  KEY `fk_approval_task_decided_by` (`decided_by`),
  CONSTRAINT `fk_approval_task_assigned_dept` FOREIGN KEY (`assigned_department_id`) REFERENCES `sys_department` (`id`),
  CONSTRAINT `fk_approval_task_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_approval_task_decided_by` FOREIGN KEY (`decided_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_approval_task_document` FOREIGN KEY (`document_id`) REFERENCES `biz_document` (`id`),
  CONSTRAINT `fk_approval_task_role` FOREIGN KEY (`required_role`) REFERENCES `sys_role` (`role_code`),
  CONSTRAINT `chk_approval_task_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批阶段待办表（阶段流转）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_event` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `document_id` bigint NOT NULL COMMENT '所属单据（→ biz_document.id）',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：SUBMIT/APPROVE/REJECT/CANCEL/CHECKOUT/RETURN/... ',
  `from_status` varchar(32) DEFAULT NULL COMMENT '变更前业务状态',
  `to_status` varchar(32) DEFAULT NULL COMMENT '变更后业务状态',
  `actor_id` bigint DEFAULT NULL COMMENT '操作人（→ sys_user.id；系统事件为 NULL）',
  `actor_type` varchar(32) NOT NULL COMMENT '操作人类型：USER/SYSTEM',
  `occurred_at` datetime(3) NOT NULL COMMENT '发生时间（UTC）',
  `reason` text COMMENT '事由/备注',
  `task_id` bigint DEFAULT NULL COMMENT '关联审批任务（→ approval_task.id）',
  `request_key` varchar(128) NOT NULL COMMENT '请求键（单次操作唯一，防事件重复写入）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_event` (`document_id`,`request_key`),
  KEY `idx_document_event_type` (`document_id`,`event_type`,`occurred_at`),
  KEY `fk_document_event_actor` (`actor_id`),
  KEY `fk_document_event_task` (`task_id`),
  CONSTRAINT `fk_document_event_actor` FOREIGN KEY (`actor_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_document_event_document` FOREIGN KEY (`document_id`) REFERENCES `biz_document` (`id`),
  CONSTRAINT `fk_document_event_task` FOREIGN KEY (`task_id`) REFERENCES `approval_task` (`id`),
  CONSTRAINT `chk_document_event_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据事件表（不可变过程历史）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_booking_restriction` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '被限制用户（→ sys_user.id）',
  `source_document_id` bigint DEFAULT NULL COMMENT '来源单据（→ biz_document.id，跨模块，外键由单据模块补充）',
  `restriction_type` varchar(32) NOT NULL COMMENT '限制类型：EQUIPMENT/LAB/ALL',
  `reason` text COMMENT '限制原因',
  `start_at` datetime(3) NOT NULL COMMENT '限制开始时间（UTC）',
  `end_at` datetime(3) DEFAULT NULL COMMENT '限制结束时间（UTC；NULL=直至撤销）',
  `revoked_at` datetime(3) DEFAULT NULL COMMENT '撤销时间（UTC）',
  `revoked_by` bigint DEFAULT NULL COMMENT '撤销人（→ sys_user.id）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_booking_restriction_src` (`source_document_id`,`restriction_type`),
  KEY `idx_user_booking_restriction_user` (`user_id`,`start_at`,`end_at`),
  KEY `fk_user_booking_restriction_revoker` (`revoked_by`),
  CONSTRAINT `fk_user_booking_restriction_document` FOREIGN KEY (`source_document_id`) REFERENCES `biz_document` (`id`),
  CONSTRAINT `fk_user_booking_restriction_revoker` FOREIGN KEY (`revoked_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_user_booking_restriction_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_user_booking_restriction_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约限制表（超期冻结与手动限制）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_period` (
  `id` bigint NOT NULL COMMENT '主键（固定小 ID，与节次号一致）',
  `period_no` int NOT NULL COMMENT '节次号（全局唯一，升序）',
  `period_name` varchar(32) NOT NULL COMMENT '显示名（如 第1节）',
  `start_clock` time NOT NULL COMMENT '开始时刻（上海本地时间）',
  `end_clock` time NOT NULL COMMENT '结束时刻（上海本地时间，须晚于 start_clock）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用：1启用 0停用',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_period_no` (`period_no`),
  CONSTRAINT `chk_class_period_clock` CHECK ((`start_clock` < `end_clock`)),
  CONSTRAINT `chk_class_period_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_class_period_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节次定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_booking_series` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `lab_id` bigint NOT NULL COMMENT '预约场地（→ lab.id）',
  `applicant_id` bigint NOT NULL COMMENT '申请人（→ sys_user.id）',
  `course_name` varchar(128) DEFAULT NULL COMMENT '课程名称',
  `class_name` varchar(128) DEFAULT NULL COMMENT '班级',
  `teacher_id` bigint DEFAULT NULL COMMENT '上课老师（→ sys_user.id，跨模块只建列不建外键）',
  `purpose` varchar(512) NOT NULL COMMENT '用途说明',
  `participant_count` int NOT NULL COMMENT '参与人数（>0，且不超过场地容量）',
  `weekday` tinyint NOT NULL COMMENT '星期：1周一 … 7周日（ISO 8601）',
  `start_period_id` bigint NOT NULL COMMENT '起始节次（→ class_period.id）',
  `end_period_id` bigint NOT NULL COMMENT '结束节次（→ class_period.id）',
  `cycle_start_date` date NOT NULL COMMENT '周期开始日期（纯日期，上海本地）',
  `cycle_end_date` date NOT NULL COMMENT '周期结束日期（纯日期，须不早于开始日期）',
  `status` varchar(32) NOT NULL COMMENT '状态：PENDING待审批/APPROVED已批准/REJECTED已驳回/CANCELLED已取消',
  `reject_reason` varchar(512) DEFAULT NULL COMMENT '驳回原因',
  `cancel_reason` varchar(512) DEFAULT NULL COMMENT '取消原因',
  `cancel_type` varchar(32) DEFAULT NULL COMMENT '取消类型：MANUAL人工/AUTO_EXPIRED超时',
  `cancelled_at` datetime(3) DEFAULT NULL COMMENT '取消时间（UTC）',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键（防重复提交，与申请人联合唯一）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lab_booking_series_idem` (`applicant_id`,`idempotency_key`),
  KEY `idx_lab_booking_series_lab_day` (`lab_id`,`weekday`,`status`),
  KEY `idx_lab_booking_series_applicant` (`applicant_id`,`create_time`),
  KEY `fk_lab_booking_series_start_period` (`start_period_id`),
  KEY `fk_lab_booking_series_end_period` (`end_period_id`),
  CONSTRAINT `fk_lab_booking_series_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lab_booking_series_end_period` FOREIGN KEY (`end_period_id`) REFERENCES `class_period` (`id`),
  CONSTRAINT `fk_lab_booking_series_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`),
  CONSTRAINT `fk_lab_booking_series_start_period` FOREIGN KEY (`start_period_id`) REFERENCES `class_period` (`id`),
  CONSTRAINT `chk_lab_booking_series_count` CHECK ((`participant_count` > 0)),
  CONSTRAINT `chk_lab_booking_series_cycle` CHECK ((`cycle_start_date` <= `cycle_end_date`)),
  CONSTRAINT `chk_lab_booking_series_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_lab_booking_series_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_lab_booking_series_weekday` CHECK ((`weekday` between 1 and 7))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地周期预约规则（固定星期+节次+使用周期，一次审批批量落地）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_booking` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `lab_id` bigint NOT NULL COMMENT '预约场地（→ lab.id）',
  `applicant_id` bigint NOT NULL COMMENT '申请人（→ sys_user.id）',
  `series_id` bigint DEFAULT NULL COMMENT '来源周期规则（→ lab_booking_series.id；单次预约为 NULL，跨表引用只建列与索引）',
  `course_name` varchar(128) DEFAULT NULL COMMENT '课程名称（提交预约时填写）',
  `class_name` varchar(128) DEFAULT NULL COMMENT '班级（提交预约时填写）',
  `teacher_id` bigint DEFAULT NULL COMMENT '上课老师（→ sys_user.id，跨模块只建列不建外键）',
  `purpose` varchar(512) NOT NULL COMMENT '用途说明',
  `participant_count` int NOT NULL COMMENT '参与人数（>0，且不超过场地容量）',
  `booking_date` date DEFAULT NULL COMMENT '预约日期（纯日期，上海本地日界；新单必填，旧单兼容 NULL）',
  `start_period_id` bigint DEFAULT NULL COMMENT '起始节次（→ class_period.id；新单必填，旧单兼容 NULL，只建列不建外键）',
  `end_period_id` bigint DEFAULT NULL COMMENT '结束节次（→ class_period.id；新单必填，旧单兼容 NULL，只建列不建外键）',
  `start_at` datetime(3) NOT NULL COMMENT '预约开始（UTC，由日期+起始节次推导）',
  `end_at` datetime(3) NOT NULL COMMENT '预约结束（UTC，由日期+结束节次推导）',
  `status` varchar(32) NOT NULL COMMENT '状态：PENDING待审批/APPROVED已批准/REJECTED已驳回/CANCELLED已取消',
  `endorse_teacher_id` bigint DEFAULT NULL COMMENT '背书老师（→ sys_user.id，一期简化流程预留）',
  `endorse_at` datetime(3) DEFAULT NULL COMMENT '背书时间（UTC，预留）',
  `endorse_comment` varchar(512) DEFAULT NULL COMMENT '背书意见（预留）',
  `reject_reason` varchar(512) DEFAULT NULL COMMENT '驳回原因',
  `cancel_reason` varchar(512) DEFAULT NULL COMMENT '取消原因',
  `cancel_type` varchar(32) DEFAULT NULL COMMENT '取消类型：MANUAL人工/FORCE强制',
  `cancelled_at` datetime(3) DEFAULT NULL COMMENT '取消时间（UTC）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  KEY `idx_lab_booking_applicant` (`applicant_id`,`create_time`),
  KEY `idx_lab_booking_lab_status` (`lab_id`,`status`,`start_at`),
  KEY `idx_lab_booking_series` (`series_id`),
  CONSTRAINT `fk_lab_booking_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lab_booking_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`),
  CONSTRAINT `chk_lab_booking_count` CHECK ((`participant_count` > 0)),
  CONSTRAINT `chk_lab_booking_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_lab_booking_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_lab_booking_window` CHECK ((`start_at` < `end_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地预约单（日期+节次；一期简化审批，背书字段预留）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_slot` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `lab_id` bigint NOT NULL COMMENT '占用场地（→ lab.id）',
  `booking_id` bigint NOT NULL COMMENT '来源预约（→ lab_booking.id，唯一）',
  `start_at` datetime(3) NOT NULL COMMENT '占用开始（UTC）',
  `end_at` datetime(3) NOT NULL COMMENT '占用结束（UTC）',
  `released_at` datetime(3) DEFAULT NULL COMMENT '释放时间（UTC；NULL 表示占用中）',
  `release_reason` varchar(512) DEFAULT NULL COMMENT '释放原因',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（→ sys_user.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lab_slot_booking` (`booking_id`),
  KEY `idx_lab_slot_conflict` (`lab_id`,`released_at`,`start_at`,`end_at`),
  CONSTRAINT `fk_lab_slot_booking` FOREIGN KEY (`booking_id`) REFERENCES `lab_booking` (`id`),
  CONSTRAINT `fk_lab_slot_lab` FOREIGN KEY (`lab_id`) REFERENCES `lab` (`id`),
  CONSTRAINT `chk_lab_slot_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_lab_slot_window` CHECK ((`start_at` < `end_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地占用槽（仅未释放记录参与冲突校验）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `actor_id` bigint DEFAULT NULL COMMENT '操作者（→ sys_user.id；系统任务/匿名可空）',
  `actor_type` varchar(32) NOT NULL COMMENT '操作者类型：STUDENT/TEACHER/ADMIN/SYSTEM/UNKNOWN',
  `document_id` bigint DEFAULT NULL COMMENT '关联单据（跨模块引用，仅列+索引，外键由 biz_document 所属模块补充）',
  `module_code` varchar(64) NOT NULL COMMENT '模块码',
  `action_code` varchar(64) NOT NULL COMMENT '动作码（与权限码同规范）',
  `target_table` varchar(64) NOT NULL COMMENT '目标表',
  `target_id` bigint DEFAULT NULL COMMENT '目标行 ID',
  `request_id` varchar(128) NOT NULL COMMENT '请求关联 ID',
  `client_ip` varchar(45) DEFAULT NULL COMMENT '客户端 IP',
  `before_value` json DEFAULT NULL COMMENT '变更前快照（一期预留）',
  `after_value` json DEFAULT NULL COMMENT '变更后快照（一期预留）',
  `success` tinyint NOT NULL COMMENT '成功：1成功 0失败',
  `occurred_at` datetime(3) NOT NULL COMMENT '发生时间（UTC）',
  `create_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '创建时间（UTC）',
  `update_time` datetime(3) NOT NULL DEFAULT (utc_timestamp(3)) COMMENT '更新时间（UTC，追加表保持创建值）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：追加表固定 0',
  `create_user_id` bigint DEFAULT NULL COMMENT '创建人（同 actor_id）',
  `update_user_id` bigint DEFAULT NULL COMMENT '最后操作人（追加表不使用）',
  PRIMARY KEY (`id`),
  KEY `idx_sys_operation_log_actor_time` (`actor_id`,`occurred_at`),
  KEY `idx_sys_operation_log_document_time` (`document_id`,`occurred_at`),
  KEY `idx_sys_operation_log_module_action` (`module_code`,`action_code`),
  CONSTRAINT `fk_sys_operation_log_actor` FOREIGN KEY (`actor_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_sys_operation_log_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_sys_operation_log_success` CHECK ((`success` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表（只追加，管理员审计）';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `sys_department` VALUES (1,NULL,'ASSET_OFFICE','设备处','ASSET_OFFICE',1,'2026-10-22 07:11:38.995','2026-10-22 07:11:38.995',0,NULL,NULL),(9001,NULL,'INFO_COLLEGE','信息工程学院','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9002,9001,'CS_DEPT','计算机系','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9003,9001,'SE_DEPT','软件工程系','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9004,NULL,'MECH_COLLEGE','机械与能源工程学院','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9005,9004,'ME_DEPT','机械工程系','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9006,NULL,'MAT_COLLEGE','材料与化工学院','COLLEGE',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9007,NULL,'ACADEMIC_AFFAIRS','教务处','SCHOOL',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9008,NULL,'STUDENT_AFFAIRS','学生工作处','SCHOOL',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL),(9009,NULL,'LAB_CENTER','实验实训中心','SCHOOL',1,'2026-10-22 07:11:41.369','2026-10-22 07:11:41.369',0,NULL,NULL);
INSERT INTO `sys_person_roster` VALUES (9101,'S20240101','STUDENT','张明轩','13901100001',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9102,'S20240102','STUDENT','李雨桐','13901100002',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9103,'S20240103','STUDENT','王梓涵','13901100003',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9104,'S20240104','STUDENT','刘思远','13901100004',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9105,'S20240105','STUDENT','陈嘉怡','13901100005',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9106,'S20240106','STUDENT','杨浩然','13901100006',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9107,'S20240107','STUDENT','赵欣悦','13901100007',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9108,'S20240108','STUDENT','黄志强','13901100008',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9109,'S20240109','STUDENT','周雅静','13901100009',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9110,'S20240110','STUDENT','吴俊杰','13901100010',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9111,'S20240201','STUDENT','徐子墨','13901100011',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9112,'S20240202','STUDENT','孙若曦','13901100012',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9113,'S20240203','STUDENT','马天佑','13901100013',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9114,'S20240204','STUDENT','朱晓彤','13901100014',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9115,'S20240205','STUDENT','胡文博','13901100015',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9116,'S20240206','STUDENT','郭静怡','13901100016',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9117,'S20240207','STUDENT','林浩宇','13901100017',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9118,'S20240208','STUDENT','何佳琪','13901100018',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9119,'S20240301','STUDENT','罗建国','13901100019',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9120,'S20240302','STUDENT','郑海燕','13901100020',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9121,'S20240303','STUDENT','宋鹏飞','13901100021',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9122,'S20240304','STUDENT','唐梦琪','13901100022',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9123,'S20240305','STUDENT','许志远','13901100023',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9124,'S20240306','STUDENT','韩雪梅','13901100024',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9125,'T20240101','TEACHER','王建国','13901100025',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9126,'T20240102','TEACHER','李文静','13901100026',9003,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9127,'T20240103','TEACHER','张志强','13901100027',9005,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9128,'T20240104','TEACHER','刘晓燕','13901100028',9006,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9129,'T20240105','TEACHER','陈立群','13901100029',9007,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9130,'T20240106','TEACHER','杨秀兰','13901100030',9002,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9131,'T20240107','TEACHER','赵国庆','13901100031',9009,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL),(9132,'T20240108','TEACHER','孙丽华','13901100032',9006,1,'2026-10-22 07:11:41.376','2026-10-22 07:11:41.376',0,NULL,NULL);
INSERT INTO `sys_user` VALUES (1,NULL,'ADMIN','系统管理员','admin',NULL,NULL,1,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6',NULL,0,NULL,1,'2026-10-22 07:11:39.006','2026-10-22 07:18:57.728',0,NULL,NULL),(2,NULL,'ADMIN','运维管理员','opsadmin',NULL,NULL,1,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6',NULL,0,NULL,1,'2026-10-22 07:11:41.201','2026-10-22 07:18:57.728',0,NULL,NULL),(9201,NULL,'ADMIN','设备处专员','asset_admin','13902200001',NULL,1,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:11:41.385',0,NULL,NULL),(9202,NULL,'ADMIN','教务处专员','jwc_admin','13902200002',NULL,9007,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:11:41.385',0,NULL,NULL),(9203,NULL,'ADMIN','实训中心专员','lab_admin','13902200003',NULL,9009,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:11:41.385',0,NULL,NULL),(9204,9125,'TEACHER','王建国','T20240101',NULL,'T20240101',9002,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:27:10.589',0,NULL,NULL),(9205,9127,'TEACHER','张志强','T20240103',NULL,'T20240103',9005,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:27:10.589',0,NULL,NULL),(9206,9101,'STUDENT','张明轩','S20240101',NULL,'S20240101',9002,'$2a$10$1QumJE.PCP8E/1N9kjLoue1DPE6VuE0JV6TnH9FEqmcp3v9r3l5V6','2026-10-22 07:11:41.385',0,NULL,1,'2026-10-22 07:11:41.385','2026-10-22 07:27:10.589',0,NULL,NULL);
INSERT INTO `sys_role` VALUES (1,'ADMIN','实验室管理员',1,'2026-10-22 07:11:39.000','2026-10-22 07:11:39.000',0,NULL,NULL),(2,'TEACHER','老师',1,'2026-10-22 07:11:39.000','2026-10-22 07:11:39.000',0,NULL,NULL),(3,'STUDENT','学生',1,'2026-10-22 07:11:39.000','2026-10-22 07:11:39.000',0,NULL,NULL),(4,'ASSISTANT','老师助理',1,'2026-10-22 07:11:39.000','2026-10-22 07:11:39.000',0,NULL,NULL),(5,'OPS_ADMIN','运维管理员',1,'2026-10-22 07:11:41.190','2026-10-22 07:11:41.190',0,NULL,NULL);
INSERT INTO `sys_permission` VALUES (1,'system:user:list','用户列表','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(2,'system:user:get','用户详情','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(3,'system:user:create','创建用户','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(4,'system:user:update','编辑用户','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(5,'system:user:enable','启用停用用户','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(6,'system:roster:list','名单列表','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(7,'system:roster:create','名单录入','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(8,'system:roster:update','名单编辑','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(9,'system:roster:enable','名单启用停用','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(10,'system:department:list','组织列表','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(11,'system:department:create','组织新增','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(12,'system:department:update','组织编辑','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(13,'system:department:enable','组织启用停用','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(14,'system:role:list','角色权限查看','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(15,'system:scope:list','数据范围查看','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(16,'system:scope:grant','数据范围授权','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(20,'system:log:list','操作日志查询','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(21,'system:policy:list','策略参数查看','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(22,'system:policy:publish','策略参数发布','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(23,'system:grant:list','助理授权查看','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(24,'system:grant:create','助理授权新增','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(25,'system:grant:revoke','助理授权撤销','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(26,'system:grant:audit','助理授权审计','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(27,'system:user:reset_password','重置密码','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(28,'system:role:assign','调整角色权限','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(29,'system:roster:export','名单导出','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(30,'system:roster:import','名单导入','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(31,'system:user:bind_role','绑定管理员角色','system',1,'2026-10-22 07:11:39.016','2026-10-22 07:11:39.016',0,NULL,NULL),(101,'item:category:list','物料分类查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(102,'item:category:create','物料分类新增','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(103,'item:category:update','物料分类修改','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(104,'item:category:enable','物料分类启停','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(105,'item:material:list','物料档案查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(106,'item:material:create','物料档案新增','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(107,'item:material:update','物料档案修改','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(108,'item:material:enable','物料档案启停','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(109,'item:location:list','库位查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(110,'item:location:create','库位新增','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(111,'item:location:update','库位修改','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(112,'item:location:enable','库位启停','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(113,'item:stock:create','库存单据登记','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(114,'item:stock:approve','库存单据审批','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(115,'item:stock:cancel','库存单据取消','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(116,'item:stock:post','库存单据过账','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(117,'item:stock:list','库存单据查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(118,'item:balance:list','库存余额查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(119,'item:ledger:list','库存台账查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(120,'item:warning:list','库存预警查询','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(121,'item:balance:update','库存预警配置','item',1,'2026-10-22 07:11:40.509','2026-10-22 07:11:40.509',0,NULL,NULL),(122,'item:request:list','申领单查询','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(123,'item:request:create','申领单创建','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(124,'item:request:approve','申领单审批','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(125,'item:request:cancel','申领单取消','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(126,'item:request:issue','申领实发出库','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(127,'item:quota:list','领用定额查询','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(128,'item:quota:create','领用定额新增','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(129,'item:quota:update','领用定额修改','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(130,'item:quota:enable','领用定额启停','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(131,'item:settlement:create','物料核销登记','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(132,'item:settlement:list','物料结算查询','item',1,'2026-10-22 07:11:40.796','2026-10-22 07:11:40.796',0,NULL,NULL),(138,'item:category:delete','物料分类删除','item',1,'2026-10-22 07:11:40.525','2026-10-22 07:11:40.525',0,NULL,NULL),(139,'item:material:delete','物料档案删除','item',1,'2026-10-22 07:11:40.525','2026-10-22 07:11:40.525',0,NULL,NULL),(140,'item:location:delete','库位删除','item',1,'2026-10-22 07:11:40.525','2026-10-22 07:11:40.525',0,NULL,NULL),(141,'equipment:category:list','设备分类查询','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(142,'equipment:category:create','设备分类新增','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(143,'equipment:category:update','设备分类修改','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(144,'equipment:category:delete','设备分类删除','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(145,'equipment:model:list','设备型号查询','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(146,'equipment:model:create','设备型号新增','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(147,'equipment:model:update','设备型号修改','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(148,'equipment:model:delete','设备型号删除','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(149,'equipment:asset:list','设备台账查询','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(150,'equipment:asset:create','设备台账新增','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(151,'equipment:asset:update','设备台账修改','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(152,'equipment:asset:delete','设备台账删除','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(153,'equipment:asset:transfer','设备迁移登记','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(154,'equipment:asset:classify','设备价值定级','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(155,'space:space:list','场地列表','space',1,'2026-10-22 07:11:39.591','2026-10-22 07:11:39.591',0,NULL,NULL),(156,'space:space:read','场地详情','space',1,'2026-10-22 07:11:39.591','2026-10-22 07:11:39.591',0,NULL,NULL),(157,'space:space:create','新建场地','space',1,'2026-10-22 07:11:39.591','2026-10-22 07:11:39.591',0,NULL,NULL),(158,'space:space:update','编辑/启停场地','space',1,'2026-10-22 07:11:39.591','2026-10-22 07:11:39.591',0,NULL,NULL),(159,'space:space:delete','删除场地','space',1,'2026-10-22 07:11:39.591','2026-10-22 07:11:39.591',0,NULL,NULL),(160,'item:material:import','物料批量导入','item',1,'2026-10-22 07:11:40.815','2026-10-22 07:11:40.815',0,NULL,NULL),(161,'item:material:export','物料批量导出','item',1,'2026-10-22 07:11:40.815','2026-10-22 07:11:40.815',0,NULL,NULL),(162,'item:category:import','物料分类批量导入','item',1,'2026-10-22 07:11:40.815','2026-10-22 07:11:40.815',0,NULL,NULL),(163,'item:category:export','物料分类批量导出','item',1,'2026-10-22 07:11:40.815','2026-10-22 07:11:40.815',0,NULL,NULL),(164,'item:balance:import','期初余额批量导入','item',1,'2026-10-22 07:11:40.828','2026-10-22 07:11:40.828',0,NULL,NULL),(165,'item:balance:export','库存余额导出','item',1,'2026-10-22 07:11:40.828','2026-10-22 07:11:40.828',0,NULL,NULL),(166,'item:request:return','申领单归还','item',1,'2026-10-23 22:38:53.546','2026-10-23 22:38:53.546',0,NULL,NULL),(167,'item:request:teacher_approve','申领单老师审核','item',1,'2026-10-22 07:11:40.842','2026-10-22 07:11:40.842',0,NULL,NULL),(168,'equipment:repair:create','设备报修登记','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(169,'equipment:repair:list','设备报修查询','equipment',1,'2026-10-22 07:11:40.189','2026-10-22 07:11:40.189',0,NULL,NULL),(170,'equipment:asset:import','设备台账导入','equipment',1,'2026-10-23 12:03:55.635','2026-10-23 12:03:55.635',0,NULL,NULL),(171,'equipment:asset:export','设备台账导出','equipment',1,'2026-10-23 12:03:55.640','2026-10-23 12:03:55.640',0,NULL,NULL),(172,'equipment:booking:apply','设备使用申请','equipment',1,'2026-10-23 12:03:32.769','2026-10-23 12:03:32.769',0,NULL,NULL),(173,'equipment:booking:list_own','本人设备申请查询','equipment',1,'2026-10-23 12:03:32.781','2026-10-23 12:03:32.781',0,NULL,NULL),(174,'equipment:booking:cancel','撤销本人设备申请','equipment',1,'2026-10-23 12:03:32.786','2026-10-23 12:03:32.786',0,NULL,NULL),(175,'equipment:booking:teacher_review','设备申请教师初审','equipment',1,'2026-10-23 12:03:32.791','2026-10-23 12:03:32.791',0,NULL,NULL),(176,'equipment:booking:admin_review','设备申请终审代批','equipment',1,'2026-10-23 12:03:32.796','2026-10-23 12:03:32.796',0,NULL,NULL),(177,'equipment:booking:checkout','设备借出发放登记','equipment',1,'2026-10-23 12:03:32.803','2026-10-23 12:03:32.803',0,NULL,NULL),(178,'equipment:booking:return','设备归还登记','equipment',1,'2026-10-23 12:03:32.809','2026-10-23 12:03:32.809',0,NULL,NULL),(179,'equipment:booking:list_all','设备申请全量查询','equipment',1,'2026-10-23 12:03:32.813','2026-10-23 12:03:32.813',0,NULL,NULL),(3001,'booking:lab:create','场地预约申请','booking',1,'2026-10-22 07:11:40.998','2026-10-22 07:11:40.998',0,NULL,NULL),(3002,'booking:lab:list','场地预约记录','booking',1,'2026-10-22 07:11:40.998','2026-10-22 07:11:40.998',0,NULL,NULL),(3003,'booking:lab:cancel','场地预约撤销','booking',1,'2026-10-22 07:11:40.998','2026-10-22 07:11:40.998',0,NULL,NULL),(3004,'booking:lab:approve','场地预约审批','booking',1,'2026-10-22 07:11:40.998','2026-10-22 07:11:40.998',0,NULL,NULL),(3005,'booking:lab:view','场地预约只读','booking',1,'2026-10-22 07:11:40.998','2026-10-22 07:11:40.998',0,NULL,NULL);
INSERT INTO `sys_menu` VALUES (1,NULL,'system','系统管理','/system',NULL,NULL,'DIRECTORY',90,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(2,12,'system_user','人员管理','/system/user','SystemUserRoster',1,'PAGE',1,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(4,1,'system_department','组织管理','/system/department','SystemDepartment',10,'PAGE',3,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(5,1,'system_role','角色权限及名单','/system/role','SystemRole',14,'PAGE',4,1,'2026-10-22 07:11:39.023','2026-10-24 00:38:53.360',0,NULL,NULL),(6,1,'system_scope','数据范围','/system/scope','SystemScope',15,'PAGE',5,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(8,1,'system_log','操作日志','/system/log','SystemLog',20,'PAGE',7,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(9,1,'system_policy','参数配置','/system/policy','SystemPolicy',21,'PAGE',8,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(10,1,'system_grant_audit','授权审计','/system/grant','SystemGrantAudit',26,'PAGE',9,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(11,NULL,'grant_manage','助理管理','/grant','GrantManage',23,'PAGE',50,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(12,NULL,'system_user_group','用户组','/system/user_group',NULL,NULL,'DIRECTORY',40,1,'2026-10-22 07:11:39.023','2026-10-22 07:11:39.023',0,NULL,NULL),(151,NULL,'ITEM','物料管理','/admin/item',NULL,NULL,'DIRECTORY',50,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(152,151,'ITEM_WORKSPACE','物料工作台','/admin/item/workspace','ItemWorkspace',NULL,'PAGE',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(153,151,'ITEM_MATERIAL','物料档案','/admin/item/material',NULL,NULL,'PAGE',2,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(154,151,'ITEM_LOCATION','库位管理','/admin/item/location',NULL,NULL,'PAGE',3,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(155,151,'ITEM_DOCUMENT','库存单据','/admin/item/document',NULL,NULL,'PAGE',4,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(156,151,'ITEM_BALANCE','库存余额','/admin/item/balance',NULL,NULL,'PAGE',5,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(157,151,'ITEM_LEDGER','库存台账','/admin/item/ledger',NULL,NULL,'PAGE',6,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(158,151,'ITEM_WARNING','库存预警','/admin/item/warning',NULL,NULL,'PAGE',7,0,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(159,152,'item:category:list','物料分类查询',NULL,NULL,101,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(160,152,'item:category:create','物料分类新增',NULL,NULL,102,'ACTION',2,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(161,152,'item:category:update','物料分类修改',NULL,NULL,103,'ACTION',3,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(162,152,'item:category:enable','物料分类启停',NULL,NULL,104,'ACTION',4,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(163,153,'item:material:list','物料档案查询',NULL,NULL,105,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(164,153,'item:material:create','物料档案新增',NULL,NULL,106,'ACTION',2,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(165,153,'item:material:update','物料档案修改',NULL,NULL,107,'ACTION',3,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(166,153,'item:material:enable','物料档案启停',NULL,NULL,108,'ACTION',4,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(167,154,'item:location:list','库位查询',NULL,NULL,109,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(168,154,'item:location:create','库位新增',NULL,NULL,110,'ACTION',2,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(169,154,'item:location:update','库位修改',NULL,NULL,111,'ACTION',3,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(170,154,'item:location:enable','库位启停',NULL,NULL,112,'ACTION',4,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(171,155,'item:stock:create','库存单据登记',NULL,NULL,113,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(172,155,'item:stock:approve','库存单据审批',NULL,NULL,114,'ACTION',2,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(173,155,'item:stock:cancel','库存单据取消',NULL,NULL,115,'ACTION',3,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(174,155,'item:stock:post','库存单据过账',NULL,NULL,116,'ACTION',4,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(175,155,'item:stock:list','库存单据查询',NULL,NULL,117,'ACTION',5,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(176,156,'item:balance:list','库存余额查询',NULL,NULL,118,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(177,156,'item:balance:update','库存预警配置',NULL,NULL,121,'ACTION',2,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(178,157,'item:ledger:list','库存台账查询',NULL,NULL,119,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(179,158,'item:warning:list','库存预警查询',NULL,NULL,120,'ACTION',1,1,'2026-10-22 07:11:40.513','2026-10-22 07:11:40.513',0,NULL,NULL),(180,151,'ITEM_REQUEST','申领管理','/admin/item/request',NULL,NULL,'PAGE',8,0,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(181,151,'ITEM_QUOTA','领用定额','/admin/item/quota',NULL,NULL,'PAGE',9,0,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(182,151,'ITEM_SETTLEMENT','物料结算','/admin/item/settlement',NULL,NULL,'PAGE',10,0,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(184,180,'item:request:list','申领单查询',NULL,NULL,122,'ACTION',1,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(185,180,'item:request:create','申领单创建',NULL,NULL,123,'ACTION',2,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(186,180,'item:request:approve','申领单审批',NULL,NULL,124,'ACTION',3,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(187,180,'item:request:cancel','申领单取消',NULL,NULL,125,'ACTION',4,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(188,180,'item:request:issue','申领实发出库',NULL,NULL,126,'ACTION',5,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(189,181,'item:quota:list','领用定额查询',NULL,NULL,127,'ACTION',1,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(190,181,'item:quota:create','领用定额新增',NULL,NULL,128,'ACTION',2,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(191,181,'item:quota:update','领用定额修改',NULL,NULL,129,'ACTION',3,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(192,181,'item:quota:enable','领用定额启停',NULL,NULL,130,'ACTION',4,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(193,182,'item:settlement:create','物料核销登记',NULL,NULL,131,'ACTION',1,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(194,182,'item:settlement:list','物料结算查询',NULL,NULL,132,'ACTION',2,1,'2026-10-22 07:11:40.799','2026-10-22 07:11:40.799',0,NULL,NULL),(200,152,'item:category:delete','物料分类删除',NULL,NULL,138,'ACTION',5,1,'2026-10-22 07:11:40.528','2026-10-22 07:11:40.528',0,NULL,NULL),(201,153,'item:material:delete','物料档案删除',NULL,NULL,139,'ACTION',5,1,'2026-10-22 07:11:40.528','2026-10-22 07:11:40.528',0,NULL,NULL),(202,154,'item:location:delete','库位删除',NULL,NULL,140,'ACTION',5,1,'2026-10-22 07:11:40.528','2026-10-22 07:11:40.528',0,NULL,NULL),(205,NULL,'ITEM_ASSISTANT_PICKUP','物料代领','/item/assistant_pickup','ItemAssistantPickup',122,'PAGE',27,1,'2026-10-22 07:11:40.799','2026-10-23 22:11:41.287',0,NULL,NULL),(206,152,'item:category:import','物料分类批量导入',NULL,NULL,162,'ACTION',6,1,'2026-10-22 07:11:40.818','2026-10-22 07:11:40.818',0,NULL,NULL),(207,152,'item:category:export','物料分类批量导出',NULL,NULL,163,'ACTION',7,1,'2026-10-22 07:11:40.818','2026-10-22 07:11:40.818',0,NULL,NULL),(208,153,'item:material:import','物料批量导入',NULL,NULL,160,'ACTION',6,1,'2026-10-22 07:11:40.818','2026-10-22 07:11:40.818',0,NULL,NULL),(209,153,'item:material:export','物料批量导出',NULL,NULL,161,'ACTION',7,1,'2026-10-22 07:11:40.818','2026-10-22 07:11:40.818',0,NULL,NULL),(210,NULL,'C_EQUIPMENT_ADMIN','设备管理','/admin/equipment','EquipmentManage',149,'PAGE',20,1,'2026-10-22 07:11:40.194','2026-10-22 07:11:40.194',0,NULL,NULL),(211,210,'F_EQUIPMENT_ASSET_CREATE','设备登记',NULL,NULL,150,'ACTION',1,1,'2026-10-22 07:11:40.194','2026-10-22 07:11:40.194',0,NULL,NULL),(212,156,'item:balance:import','期初余额批量导入',NULL,NULL,164,'ACTION',3,1,'2026-10-22 07:11:40.831','2026-10-22 07:11:40.831',0,NULL,NULL),(213,156,'item:balance:export','库存余额导出',NULL,NULL,165,'ACTION',4,1,'2026-10-22 07:11:40.831','2026-10-22 07:11:40.831',0,NULL,NULL),(215,180,'item:request:teacher_approve','申领单老师审核',NULL,NULL,167,'ACTION',6,1,'2026-10-22 07:11:40.845','2026-10-22 07:11:40.845',0,NULL,NULL),(216,NULL,'C_EQUIPMENT_DIRECTORY','设备目录','/equipment/directory','EquipmentDirectory',149,'PAGE',21,1,'2026-10-23 12:03:32.816','2026-10-23 12:03:32.816',0,NULL,NULL),(217,NULL,'C_EQUIPMENT_MY_BOOKING','我的设备申请','/equipment/bookings/my','MyEquipmentBookings',173,'PAGE',22,1,'2026-10-23 12:03:32.821','2026-10-23 12:03:32.821',0,NULL,NULL),(218,NULL,'C_EQUIPMENT_TEACHER_REVIEW','设备申请初审','/equipment/bookings/teacher','EquipmentTeacherApprove',175,'PAGE',23,1,'2026-10-23 12:03:32.826','2026-10-23 12:03:32.826',0,NULL,NULL),(219,NULL,'C_EQUIPMENT_BOOKING_ADMIN','设备使用审批','/admin/equipment/bookings','EquipmentBookingManage',176,'PAGE',24,1,'2026-10-23 12:03:32.832','2026-10-23 12:03:32.832',0,NULL,NULL),(220,210,'equipment:asset:import','设备导入',NULL,NULL,170,'ACTION',2,1,'2026-10-23 12:03:55.650','2026-10-23 12:03:55.650',0,NULL,NULL),(221,210,'equipment:asset:export','设备导出',NULL,NULL,171,'ACTION',3,1,'2026-10-23 12:03:55.654','2026-10-23 12:03:55.654',0,NULL,NULL),(222,NULL,'C_ITEM_MY_REQUEST','物料申领','/item/requests/my','ItemMyRequests',123,'PAGE',25,1,'2026-10-23 21:45:59.475','2026-10-23 21:45:59.475',0,NULL,NULL),(223,NULL,'C_ITEM_TEACHER_REVIEW','申领初审','/item/requests/teacher','ItemTeacherApprove',167,'PAGE',26,1,'2026-10-23 21:45:59.475','2026-10-23 21:45:59.475',0,NULL,NULL),(224,180,'item:request:return','申领单归还',NULL,NULL,166,'ACTION',7,1,'2026-10-23 22:38:53.551','2026-10-23 22:38:53.551',0,NULL,NULL),(1001,NULL,'space','空间管理','/admin/space',NULL,NULL,'DIRECTORY',10,1,'2026-10-22 07:11:39.594','2026-10-22 07:11:39.594',0,NULL,NULL),(1002,1001,'space_labs','场地管理','/admin/space/labs','SpaceLabList',NULL,'PAGE',1,1,'2026-10-22 07:11:39.594','2026-10-22 07:11:39.594',0,NULL,NULL),(1003,1002,'space_create','新建场地',NULL,NULL,157,'ACTION',1,1,'2026-10-22 07:11:39.594','2026-10-22 07:11:39.594',0,NULL,NULL),(1004,1002,'space_update','编辑场地',NULL,NULL,158,'ACTION',2,1,'2026-10-22 07:11:39.594','2026-10-22 07:11:39.594',0,NULL,NULL),(1005,1002,'space_delete','删除场地',NULL,NULL,159,'ACTION',3,1,'2026-10-22 07:11:39.594','2026-10-22 07:11:39.594',0,NULL,NULL),(3000,NULL,'booking','场地预约','/admin/booking',NULL,NULL,'DIRECTORY',30,1,'2026-10-22 07:11:41.001','2026-10-22 07:11:41.001',0,NULL,NULL),(3001,3000,'booking_all','预约管理','/admin/booking/labs','BookingAdminList',3002,'PAGE',1,1,'2026-10-22 07:11:41.001','2026-10-22 07:11:41.001',0,NULL,NULL),(3002,3000,'booking_approval','预约审批','/admin/booking/approval','BookingApproval',3004,'PAGE',2,1,'2026-10-22 07:11:41.001','2026-10-22 07:11:41.001',0,NULL,NULL),(3003,NULL,'booking_user','场地预约','/booking/labs','BookingUserList',3001,'PAGE',10,1,'2026-10-22 07:11:41.001','2026-10-22 07:11:41.001',0,NULL,NULL),(3004,NULL,'booking_user_view','预约情况','/booking/labs','BookingUserList',3005,'PAGE',11,1,'2026-10-22 07:11:41.001','2026-10-22 07:11:41.001',0,NULL,NULL);
INSERT INTO `sys_user_role` VALUES (1,1,1,1,'2026-10-22 07:11:39.010','2026-10-22 07:11:39.010',0,NULL,NULL),(2,2,5,1,'2026-10-22 07:11:41.205','2026-10-22 07:11:41.205',0,NULL,NULL),(9301,9201,1,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(9302,9202,1,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(9303,9203,1,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(9304,9204,2,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(9305,9205,2,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(9306,9206,3,1,'2026-10-22 07:11:41.392','2026-10-22 07:11:41.392',0,NULL,NULL),(2113473420194459650,9204,1,1,'2026-10-23 03:31:44.469','2026-10-23 03:31:44.469',0,2,2);
INSERT INTO `sys_role_permission` VALUES (24,2,23,1,'2026-10-22 07:11:39.028','2026-10-22 07:11:39.028',0,NULL,NULL),(25,2,24,1,'2026-10-22 07:11:39.028','2026-10-22 07:11:39.028',0,NULL,NULL),(26,2,25,1,'2026-10-22 07:11:39.028','2026-10-22 07:11:39.028',0,NULL,NULL),(201,1,101,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(202,1,102,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(203,1,103,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(204,1,104,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(205,1,105,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(206,1,106,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(207,1,107,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(208,1,108,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(209,1,109,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(210,1,110,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(211,1,111,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(212,1,112,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(213,1,113,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(214,1,114,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(215,1,115,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(216,1,116,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(217,1,117,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(218,1,118,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(219,1,119,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(220,1,120,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(221,1,121,1,'2026-10-22 07:11:40.517','2026-10-22 07:11:40.517',0,NULL,NULL),(222,1,122,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(223,1,123,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(224,1,124,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(225,1,125,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(226,1,126,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(227,1,127,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(228,1,128,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(229,1,129,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(230,1,130,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(231,1,131,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(232,1,132,1,'2026-10-22 07:11:40.803','2026-10-22 07:11:40.803',0,NULL,NULL),(238,1,138,1,'2026-10-22 07:11:40.531','2026-10-22 07:11:40.531',0,NULL,NULL),(239,1,139,1,'2026-10-22 07:11:40.531','2026-10-22 07:11:40.531',0,NULL,NULL),(240,1,140,1,'2026-10-22 07:11:40.531','2026-10-22 07:11:40.531',0,NULL,NULL),(245,1,160,1,'2026-10-22 07:11:40.820','2026-10-22 07:11:40.820',0,NULL,NULL),(246,1,161,1,'2026-10-22 07:11:40.820','2026-10-22 07:11:40.820',0,NULL,NULL),(247,1,162,1,'2026-10-22 07:11:40.820','2026-10-22 07:11:40.820',0,NULL,NULL),(248,1,163,1,'2026-10-22 07:11:40.820','2026-10-22 07:11:40.820',0,NULL,NULL),(249,3,122,1,'2026-10-22 07:11:40.809','2026-10-22 07:11:40.809',0,NULL,NULL),(250,3,126,1,'2026-10-22 07:11:40.809','2026-10-22 07:11:40.809',0,NULL,NULL),(251,1,155,1,'2026-10-22 07:11:39.599','2026-10-22 07:11:39.599',0,NULL,NULL),(252,1,156,1,'2026-10-22 07:11:39.599','2026-10-22 07:11:39.599',0,NULL,NULL),(253,1,157,1,'2026-10-22 07:11:39.599','2026-10-22 07:11:39.599',0,NULL,NULL),(254,1,158,1,'2026-10-22 07:11:39.599','2026-10-22 07:11:39.599',0,NULL,NULL),(255,1,159,1,'2026-10-22 07:11:39.599','2026-10-22 07:11:39.599',0,NULL,NULL),(256,2,155,1,'2026-10-22 07:11:39.604','2026-10-22 07:11:39.604',0,NULL,NULL),(257,2,156,1,'2026-10-22 07:11:39.604','2026-10-22 07:11:39.604',0,NULL,NULL),(258,3,155,1,'2026-10-22 07:11:39.604','2026-10-22 07:11:39.604',0,NULL,NULL),(259,3,156,1,'2026-10-22 07:11:39.604','2026-10-22 07:11:39.604',0,NULL,NULL),(260,1,164,1,'2026-10-22 07:11:40.835','2026-10-22 07:11:40.835',0,NULL,NULL),(261,1,165,1,'2026-10-22 07:11:40.835','2026-10-22 07:11:40.835',0,NULL,NULL),(263,2,122,1,'2026-10-22 07:11:40.849','2026-10-22 07:11:40.849',0,NULL,NULL),(264,2,167,1,'2026-10-22 07:11:40.849','2026-10-22 07:11:40.849',0,NULL,NULL),(265,3,123,1,'2026-10-22 07:11:40.849','2026-10-22 07:11:40.849',0,NULL,NULL),(266,1,167,1,'2026-10-22 07:11:40.849','2026-10-22 07:11:40.849',0,NULL,NULL),(515,1,141,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(516,1,142,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(517,1,143,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(518,1,144,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(519,1,145,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(520,1,146,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(521,1,147,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(522,1,148,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(523,1,149,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(524,1,150,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(525,1,151,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(526,1,152,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(527,1,153,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(528,1,154,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(529,2,168,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(530,2,169,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(531,3,168,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(532,3,169,1,'2026-10-22 07:11:40.197','2026-10-22 07:11:40.197',0,NULL,NULL),(533,1,170,1,'2026-10-23 12:03:55.644','2026-10-23 12:03:55.644',0,NULL,NULL),(534,1,171,1,'2026-10-23 12:03:55.647','2026-10-23 12:03:55.647',0,NULL,NULL),(545,1,176,1,'2026-10-23 12:03:32.840','2026-10-23 12:03:32.840',0,NULL,NULL),(546,1,177,1,'2026-10-23 12:03:32.849','2026-10-23 12:03:32.849',0,NULL,NULL),(547,1,178,1,'2026-10-23 12:03:32.854','2026-10-23 12:03:32.854',0,NULL,NULL),(548,1,179,1,'2026-10-23 12:03:32.858','2026-10-23 12:03:32.858',0,NULL,NULL),(549,2,149,1,'2026-10-23 12:03:32.861','2026-10-23 12:03:32.861',0,NULL,NULL),(550,2,172,1,'2026-10-23 12:03:32.865','2026-10-23 12:03:32.865',0,NULL,NULL),(551,2,173,1,'2026-10-23 12:03:32.869','2026-10-23 12:03:32.869',0,NULL,NULL),(552,2,174,1,'2026-10-23 12:03:32.872','2026-10-23 12:03:32.872',0,NULL,NULL),(553,2,175,1,'2026-10-23 12:03:32.875','2026-10-23 12:03:32.875',0,NULL,NULL),(554,3,149,1,'2026-10-23 12:03:32.879','2026-10-23 12:03:32.879',0,NULL,NULL),(555,3,172,1,'2026-10-23 12:03:32.882','2026-10-23 12:03:32.882',0,NULL,NULL),(556,3,173,1,'2026-10-23 12:03:32.885','2026-10-23 12:03:32.885',0,NULL,NULL),(557,3,174,1,'2026-10-23 12:03:32.891','2026-10-23 12:03:32.891',0,NULL,NULL),(3001,1,3002,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3002,1,3004,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3005,2,3001,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3006,2,3002,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3007,2,3003,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3008,3,3005,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3009,1,3005,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(3010,2,3005,1,'2026-10-22 07:11:41.005','2026-10-22 07:11:41.005',0,NULL,NULL),(5001,5,1,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5002,5,2,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5003,5,3,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5004,5,4,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5005,5,5,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5006,5,6,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5007,5,7,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5008,5,8,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5009,5,9,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5010,5,10,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5011,5,11,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5012,5,12,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5013,5,13,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5014,5,14,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5015,5,15,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5016,5,16,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5017,5,20,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5018,5,21,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5019,5,22,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5020,5,23,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5021,5,24,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5022,5,25,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5023,5,26,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5024,5,27,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5025,5,28,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5026,5,29,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5027,5,30,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5028,5,31,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5029,5,101,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5030,5,102,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5031,5,103,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5032,5,104,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5033,5,105,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5034,5,106,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5035,5,107,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5036,5,108,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5037,5,109,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5038,5,110,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5039,5,111,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5040,5,112,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5041,5,113,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5042,5,114,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5043,5,115,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5044,5,116,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5045,5,117,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5046,5,118,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5047,5,119,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5048,5,120,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5049,5,121,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5050,5,122,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5051,5,123,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5052,5,124,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5053,5,125,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5054,5,126,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5055,5,127,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5056,5,128,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5057,5,129,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5058,5,130,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5059,5,131,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5060,5,132,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5061,5,138,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5062,5,139,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5063,5,140,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5064,5,141,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5065,5,142,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5066,5,143,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5067,5,144,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5068,5,145,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5069,5,146,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5070,5,147,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5071,5,148,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5072,5,149,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5073,5,150,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5074,5,151,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5075,5,152,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5076,5,153,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5077,5,154,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5078,5,155,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5079,5,156,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5080,5,157,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5081,5,158,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5082,5,159,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5083,5,160,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5084,5,161,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5085,5,162,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5086,5,163,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5087,5,164,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5088,5,165,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5089,5,167,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5090,5,168,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5091,5,169,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5092,5,170,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5093,5,171,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5094,5,172,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5095,5,173,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5096,5,174,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5097,5,175,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5098,5,176,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5099,5,177,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5100,5,178,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5101,5,179,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5102,5,3001,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5103,5,3002,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5104,5,3003,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5105,5,3004,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5106,5,3005,1,'2026-10-23 12:03:55.758','2026-10-23 12:03:55.758',0,NULL,NULL),(5107,2,123,1,'2026-10-23 21:45:59.463','2026-10-23 21:45:59.463',0,NULL,NULL),(5108,2,125,1,'2026-10-23 21:45:59.463','2026-10-23 21:45:59.463',0,NULL,NULL),(5109,3,125,1,'2026-10-23 21:45:59.463','2026-10-23 21:45:59.463',0,NULL,NULL),(5110,1,166,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5111,2,118,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5112,2,126,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5113,2,166,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5114,3,118,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5115,3,166,1,'2026-10-23 22:38:53.556','2026-10-23 22:38:53.556',0,NULL,NULL),(5116,5,166,1,'2026-10-23 22:38:53.564','2026-10-23 22:38:53.564',0,NULL,NULL);
INSERT INTO `sys_role_menu` VALUES (11,2,11,1,'2026-10-22 07:11:39.034','2026-10-22 07:11:39.034',0,NULL,NULL),(301,1,151,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(302,1,152,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(303,1,153,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(304,1,154,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(305,1,155,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(306,1,156,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(307,1,157,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(308,1,158,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(309,1,159,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(310,1,160,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(311,1,161,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(312,1,162,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(313,1,163,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(314,1,164,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(315,1,165,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(316,1,166,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(317,1,167,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(318,1,168,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(319,1,169,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(320,1,170,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(321,1,171,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(322,1,172,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(323,1,173,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(324,1,174,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(325,1,175,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(326,1,176,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(327,1,177,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(328,1,178,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(329,1,179,1,'2026-10-22 07:11:40.520','2026-10-22 07:11:40.520',0,NULL,NULL),(330,1,180,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(331,1,181,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(332,1,182,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(334,1,184,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(335,1,185,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(336,1,186,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(337,1,187,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(338,1,188,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(339,1,189,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(340,1,190,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(341,1,191,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(342,1,192,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(343,1,193,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(344,1,194,1,'2026-10-22 07:11:40.806','2026-10-22 07:11:40.806',0,NULL,NULL),(350,1,200,1,'2026-10-22 07:11:40.534','2026-10-22 07:11:40.534',0,NULL,NULL),(351,1,201,1,'2026-10-22 07:11:40.534','2026-10-22 07:11:40.534',0,NULL,NULL),(352,1,202,1,'2026-10-22 07:11:40.534','2026-10-22 07:11:40.534',0,NULL,NULL),(357,1,206,1,'2026-10-22 07:11:40.823','2026-10-22 07:11:40.823',0,NULL,NULL),(358,1,207,1,'2026-10-22 07:11:40.823','2026-10-22 07:11:40.823',0,NULL,NULL),(359,1,208,1,'2026-10-22 07:11:40.823','2026-10-22 07:11:40.823',0,NULL,NULL),(360,1,209,1,'2026-10-22 07:11:40.823','2026-10-22 07:11:40.823',0,NULL,NULL),(361,4,205,1,'2026-10-22 07:11:40.812','2026-10-23 12:03:55.700',0,NULL,NULL),(362,1,212,1,'2026-10-22 07:11:40.838','2026-10-22 07:11:40.838',0,NULL,NULL),(363,1,213,1,'2026-10-22 07:11:40.838','2026-10-22 07:11:40.838',0,NULL,NULL),(365,2,152,1,'2026-10-22 07:11:40.852','2026-10-22 07:11:40.852',0,NULL,NULL),(366,2,215,1,'2026-10-22 07:11:40.852','2026-10-22 07:11:40.852',0,NULL,NULL),(367,3,152,1,'2026-10-22 07:11:40.852','2026-10-22 07:11:40.852',0,NULL,NULL),(368,3,185,1,'2026-10-22 07:11:40.852','2026-10-22 07:11:40.852',0,NULL,NULL),(369,1,215,1,'2026-10-22 07:11:40.852','2026-10-22 07:11:40.852',0,NULL,NULL),(608,1,210,1,'2026-10-22 07:11:40.200','2026-10-22 07:11:40.200',0,NULL,NULL),(610,1,211,1,'2026-10-22 07:11:40.200','2026-10-22 07:11:40.200',0,NULL,NULL),(616,1,219,1,'2026-10-23 12:03:32.894','2026-10-23 12:03:32.894',0,NULL,NULL),(617,2,216,1,'2026-10-23 12:03:32.899','2026-10-23 12:03:32.899',0,NULL,NULL),(618,2,217,1,'2026-10-23 12:03:32.902','2026-10-23 12:03:32.902',0,NULL,NULL),(619,2,218,1,'2026-10-23 12:03:32.906','2026-10-23 12:03:32.906',0,NULL,NULL),(620,3,216,1,'2026-10-23 12:03:32.910','2026-10-23 12:03:32.910',0,NULL,NULL),(621,3,217,1,'2026-10-23 12:03:32.915','2026-10-23 12:03:32.915',0,NULL,NULL),(622,1,220,1,'2026-10-23 12:03:55.657','2026-10-23 12:03:55.657',0,NULL,NULL),(623,1,221,1,'2026-10-23 12:03:55.661','2026-10-23 12:03:55.661',0,NULL,NULL),(1101,1,1001,1,'2026-10-22 07:11:39.607','2026-10-22 07:11:39.607',0,NULL,NULL),(1102,1,1002,1,'2026-10-22 07:11:39.607','2026-10-22 07:11:39.607',0,NULL,NULL),(1103,1,1003,1,'2026-10-22 07:11:39.607','2026-10-22 07:11:39.607',0,NULL,NULL),(1104,1,1004,1,'2026-10-22 07:11:39.607','2026-10-22 07:11:39.607',0,NULL,NULL),(1105,1,1005,1,'2026-10-22 07:11:39.607','2026-10-22 07:11:39.607',0,NULL,NULL),(3001,1,3000,1,'2026-10-22 07:11:41.008','2026-10-22 07:11:41.008',0,NULL,NULL),(3002,1,3001,1,'2026-10-22 07:11:41.008','2026-10-22 07:11:41.008',0,NULL,NULL),(3003,1,3002,1,'2026-10-22 07:11:41.008','2026-10-22 07:11:41.008',0,NULL,NULL),(3004,2,3003,1,'2026-10-22 07:11:41.008','2026-10-22 07:11:41.008',0,NULL,NULL),(3005,3,3004,1,'2026-10-22 07:11:41.008','2026-10-22 07:11:41.008',0,NULL,NULL),(5001,5,1,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5002,5,4,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5003,5,5,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5004,5,6,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5005,5,8,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5006,5,9,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5007,5,10,1,'2026-10-23 12:03:55.819','2026-10-23 12:03:55.819',0,NULL,NULL),(5008,2,222,1,'2026-10-23 21:45:59.481','2026-10-23 21:45:59.481',0,NULL,NULL),(5009,3,222,1,'2026-10-23 21:45:59.481','2026-10-23 21:45:59.481',0,NULL,NULL),(5010,2,223,1,'2026-10-23 21:45:59.481','2026-10-23 21:45:59.481',0,NULL,NULL),(5011,1,224,1,'2026-10-23 22:38:53.560','2026-10-23 22:38:53.560',0,NULL,NULL),(5012,5,224,1,'2026-10-23 22:38:53.568','2026-10-23 22:38:53.568',0,NULL,NULL);
INSERT INTO `sys_user_data_scope` (`id`, `user_id`, `scope_type`, `department_id`, `campus_id`, `building_id`, `lab_id`, `access_mode`, `enabled`, `create_time`, `update_time`, `deleted`, `create_user_id`, `update_user_id`) VALUES (1,1,'ALL',NULL,NULL,NULL,NULL,'WRITE',1,'2026-10-22 07:11:41.147','2026-10-22 07:11:41.147',0,NULL,NULL),(9401,9201,'DEPARTMENT',1,NULL,NULL,NULL,'WRITE',1,'2026-10-22 07:11:41.396','2026-10-22 07:11:41.396',0,NULL,NULL),(9402,9202,'ALL',NULL,NULL,NULL,NULL,'WRITE',1,'2026-10-22 07:11:41.396','2026-10-22 07:11:41.396',0,NULL,NULL),(9403,9203,'DEPARTMENT',9009,NULL,NULL,NULL,'READ',1,'2026-10-22 07:11:41.396','2026-10-22 07:11:41.396',0,NULL,NULL);
INSERT INTO `business_policy` VALUES (1,'LOGIN_MAX_FAIL',1,'登录连续失败锁定阈值（次）','2026-10-14 00:00:00.000',NULL,'{\"maxFailCount\": 5}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(2,'LOGIN_LOCK_MINUTES',1,'登录锁定时长（分钟）','2026-10-14 00:00:00.000',NULL,'{\"lockMinutes\": 30}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(4,'SMS_CODE_TTL_SECONDS',1,'短信验证码有效期（秒）','2026-10-14 00:00:00.000',NULL,'{\"ttlSeconds\": 300}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(5,'SMS_RESEND_INTERVAL_SECONDS',1,'短信验证码重发间隔（秒）','2026-10-14 00:00:00.000',NULL,'{\"intervalSeconds\": 60}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(6,'SMS_DAILY_LIMIT',1,'短信验证码日发送上限（条）','2026-10-14 00:00:00.000',NULL,'{\"dailyLimit\": 10}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(7,'SMS_CODE_MAX_FAIL',1,'验证码错误作废次数（次）','2026-10-14 00:00:00.000',NULL,'{\"maxFailCount\": 5}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(8,'SESSION_TIMEOUT_STUDENT_TEACHER',1,'学生/老师会话有效期（秒）','2026-10-14 00:00:00.000',NULL,'{\"timeoutSeconds\": 28800}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL),(9,'SESSION_TIMEOUT_ADMIN',1,'管理员会话有效期（秒）','2026-10-14 00:00:00.000',NULL,'{\"timeoutSeconds\": 28800}',1,'2026-10-22 07:11:39.139','2026-10-22 07:11:39.139',0,NULL,NULL);
INSERT INTO `lab` VALUES (1,'A703 网络实训室','TRAINING_ROOM','A 楼七层 A703',50,'网络综合布线实训环境：56 口配线架 12 组、路由交换设备 12 套、实训工位 50 个','08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:39.585','2026-10-24 02:16:06.256',0,1,1),(2,'C703 电子电工实训室','TRAINING_ROOM','C 楼七层 C703',45,'示波器 20 台、直流稳压电源 20 台、电工电子实训台 12 位','08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:39.585','2026-10-24 02:16:06.256',0,1,1),(3,'教学楼201多媒体教室','CLASSROOM','教学楼二层 201',60,'投影仪 1 台、音响系统 1 套、中控讲台 1 位','08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:39.585','2026-10-24 02:16:06.256',0,1,1),(4,'嵌入式实训室','TRAINING_ROOM',NULL,45,NULL,'08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:41.253','2026-10-23 18:39:55.111',0,1,1),(5,'A303 软件工程实训室','TRAINING_ROOM','A303',61,NULL,'08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:41.253','2026-10-24 02:16:06.256',0,1,1),(6,'综合工程实训室','TRAINING_ROOM',NULL,45,NULL,'08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:41.253','2026-10-24 02:16:06.256',0,1,1),(7,'公共库房','TRAINING_ROOM',NULL,10,'公共物资集中存放','08:00:00','22:00:00','AVAILABLE',1,'2026-10-22 07:11:41.253','2026-10-24 02:16:06.256',0,1,1),(2113700949653516290,'2','TRAINING_ROOM','2',40,'2','08:00:00','22:00:00','AVAILABLE',1,'2026-10-23 18:35:51.728','2026-10-23 18:35:54.905',1,1,1);
INSERT INTO `lab_maintenance_window` VALUES (2113708396426420225,2,'2026-10-25 02:00:00.000','2026-10-25 03:00:00.000','冒烟：空调检修','2026-10-23 19:05:27.357','2026-10-23 19:05:27.169','2026-10-23 19:05:27.169',0,1,1),(2113710043294720001,4,'2026-10-25 00:30:00.000','2026-10-25 04:00:00.000','1',NULL,'2026-10-23 19:11:59.827','2026-10-23 19:11:59.827',0,1,1);
INSERT INTO `class_period` VALUES (1,1,'第1节','08:00:00','08:45:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(2,2,'第2节','08:55:00','09:40:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(3,3,'第3节','10:00:00','10:45:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(4,4,'第4节','10:55:00','11:40:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(5,5,'第5节','14:00:00','14:45:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(6,6,'第6节','14:55:00','15:40:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(7,7,'第7节','16:00:00','16:45:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(8,8,'第8节','16:55:00','17:40:00',1,'2026-10-22 07:11:40.881','2026-10-22 07:11:40.881',0,NULL,NULL),(9,9,'第9节','18:30:00','19:15:00',1,'2026-10-23 18:16:06.251','2026-10-23 18:16:06.251',0,NULL,NULL),(10,10,'第10节','19:20:00','20:05:00',1,'2026-10-23 18:16:06.251','2026-10-23 18:16:06.251',0,NULL,NULL),(11,11,'第11节','20:10:00','20:55:00',1,'2026-10-23 18:16:06.251','2026-10-23 18:16:06.251',0,NULL,NULL),(12,12,'第12节','21:00:00','21:45:00',1,'2026-10-23 18:16:06.251','2026-10-23 18:16:06.251',0,NULL,NULL);

-- 仅在新库内过滤设备、物料和物料代领授权种子。
CREATE TEMPORARY TABLE tmp_removed_permission (id BIGINT PRIMARY KEY);
INSERT INTO tmp_removed_permission (id)
SELECT id FROM sys_permission
WHERE module_code IN ('equipment', 'item')
   OR permission_code IN ('system:grant:list', 'system:grant:create', 'system:grant:revoke', 'system:grant:audit');

CREATE TEMPORARY TABLE tmp_removed_menu (id BIGINT PRIMARY KEY);
INSERT INTO tmp_removed_menu (id)
SELECT id FROM sys_menu
WHERE component_key LIKE 'Equipment%'
   OR component_key LIKE 'Item%'
   OR component_key IN ('GrantManage', 'SystemGrantAudit')
   OR route_path LIKE '%equipment%'
   OR route_path LIKE '%item%'
   OR route_path IN ('/grant', '/system/grant')
   OR permission_id IN (SELECT id FROM tmp_removed_permission);

DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM tmp_removed_permission);
DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'ASSISTANT');
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'ASSISTANT');
DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'ASSISTANT');
DELETE FROM sys_role WHERE role_code = 'ASSISTANT';

-- 先删子节点，再删目标父节点，避开 sys_menu 自引用外键。
DELETE FROM sys_menu WHERE parent_id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_menu WHERE parent_id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_menu WHERE parent_id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_menu WHERE parent_id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_menu WHERE id IN (SELECT id FROM tmp_removed_menu);
DELETE FROM sys_permission WHERE id IN (SELECT id FROM tmp_removed_permission);

DROP TEMPORARY TABLE tmp_removed_menu;
DROP TEMPORARY TABLE tmp_removed_permission;

-- OPS_ADMIN 仅管理系统、人员和权限，不参与场地、预约、物料等业务流程。
DELETE FROM sys_role_permission WHERE role_id = 5;
DELETE FROM sys_role_menu WHERE role_id = 5;
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
FROM sys_permission
WHERE enabled = 1 AND deleted = 0 AND module_code = 'system';
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
FROM sys_menu
WHERE enabled = 1 AND deleted = 0 AND menu_code LIKE 'system%';

SET FOREIGN_KEY_CHECKS=1;

-- ============================================================================
-- 物料与固定资产模块：旧item/equipment种子清理完成后新增，避免再次被清理。
-- 物料与固定资产18表结构（含学生代领与库位负责人历史）；不写任何物料、资产或库存业务数据。
-- ============================================================================
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
  CONSTRAINT chk_material_type_track CHECK ((material_type = 'CONSUMABLE' AND track_mode = 'QUANTITY') OR (material_type = 'FIXED_ASSET' AND track_mode = 'SERIAL')),
  CONSTRAINT chk_material_quantity_mode CHECK (quantity_mode IN ('EXACT', 'ESTIMATE')),
  CONSTRAINT chk_material_enabled CHECK (enabled IN (0, 1)),
  CONSTRAINT chk_material_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一物料表';

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

CREATE TABLE IF NOT EXISTS stock_location_responsible_history (
  id BIGINT NOT NULL COMMENT '负责人变动历史ID（雪花ID）',
  location_id BIGINT NOT NULL COMMENT '库位ID',
  location_code VARCHAR(64) NOT NULL COMMENT '变动时库位编码快照',
  location_name VARCHAR(128) NOT NULL COMMENT '变动时库位名称快照',
  before_snapshot TEXT NOT NULL COMMENT '变更前负责人JSON数组（userId/realName/accountName）',
  after_snapshot TEXT NOT NULL COMMENT '变更后负责人JSON数组（userId/realName/accountName）',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(128) NOT NULL COMMENT '操作人姓名快照',
  occurred_at DATETIME(3) NOT NULL COMMENT '实际变动时间（UTC）',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '追加表保持创建值',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '负责人历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '追加表不使用',
  PRIMARY KEY (id),
  KEY idx_location_responsible_history_location (location_id, occurred_at, id),
  KEY idx_location_responsible_history_occurred (occurred_at, id),
  KEY idx_location_responsible_history_operator (operator_id),
  CONSTRAINT fk_location_responsible_history_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_location_responsible_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_location_responsible_history_before_json CHECK (JSON_VALID(before_snapshot)),
  CONSTRAINT chk_location_responsible_history_after_json CHECK (JSON_VALID(after_snapshot)),
  CONSTRAINT chk_location_responsible_history_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库位负责人变动历史表（只追加）';

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

CREATE TABLE IF NOT EXISTS material_assistant_grant (
  id BIGINT NOT NULL COMMENT '授权ID（雪花ID）',
  teacher_id BIGINT NOT NULL COMMENT '授权教师',
  student_id BIGINT NOT NULL COMMENT '被授权学生',
  start_at DATETIME(3) NOT NULL COMMENT '生效时间（UTC）',
  end_at DATETIME(3) NOT NULL COMMENT '失效时间（UTC，不含）',
  revoked_at DATETIME(3) DEFAULT NULL COMMENT '撤销时间（UTC）',
  revoked_by BIGINT DEFAULT NULL COMMENT '撤销人',
  reason VARCHAR(512) DEFAULT NULL COMMENT '授权说明',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '授权历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  KEY idx_material_assistant_grant_teacher (teacher_id, start_at, end_at),
  KEY idx_material_assistant_grant_student (student_id, start_at, end_at),
  KEY idx_material_assistant_grant_revoked_by (revoked_by),
  CONSTRAINT fk_material_assistant_grant_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_assistant_grant_student FOREIGN KEY (student_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_assistant_grant_revoked_by FOREIGN KEY (revoked_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_assistant_grant_window CHECK (start_at < end_at),
  CONSTRAINT chk_material_assistant_grant_people CHECK (teacher_id <> student_id),
  CONSTRAINT chk_material_assistant_grant_revoke CHECK (revoked_at IS NULL OR revoked_by IS NOT NULL),
  CONSTRAINT chk_material_assistant_grant_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师学生助理授权表';

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
  pickup_mode VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT 'SELF本人领取/ASSISTANT学生代领',
  delegate_student_id BIGINT DEFAULT NULL COMMENT '被委派学生',
  delegate_grant_id BIGINT DEFAULT NULL COMMENT '委派时授权快照ID',
  delegated_at DATETIME(3) DEFAULT NULL COMMENT '最近委派时间（UTC）',
  create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
  update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '申领历史恒保留，固定0',
  create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_claim_biz_document (biz_document_id),
  KEY idx_material_claim_applicant (applicant_id, create_time),
  KEY idx_material_claim_status_time (status, booking_start_at, booking_end_at),
  KEY idx_material_claim_delegate (delegate_student_id, status, booking_start_at),
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
  CONSTRAINT fk_material_claim_delegate_student FOREIGN KEY (delegate_student_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_material_claim_delegate_grant FOREIGN KEY (delegate_grant_id) REFERENCES material_assistant_grant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_material_claim_window CHECK (booking_start_at < booking_end_at AND return_deadline >= booking_end_at),
  CONSTRAINT chk_material_claim_revision CHECK (revision_no >= 1),
  CONSTRAINT chk_material_claim_status CHECK (status IN ('PENDING', 'APPROVED', 'PARTIALLY_ISSUED', 'EXPIRED', 'CANCELLED', 'CLOSED', 'REJECTED')),
  CONSTRAINT chk_material_claim_close_reason CHECK (close_reason IS NULL OR close_reason IN ('FULFILLED', 'ABANDONED')),
  CONSTRAINT chk_material_claim_pickup CHECK (
    (pickup_mode = 'SELF' AND delegate_student_id IS NULL AND delegate_grant_id IS NULL AND delegated_at IS NULL)
    OR (pickup_mode = 'ASSISTANT' AND delegate_student_id IS NOT NULL AND delegate_grant_id IS NOT NULL AND delegated_at IS NOT NULL)
  ),
  CONSTRAINT chk_material_claim_deleted CHECK (deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料申领主表';

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
  pickup_user_id BIGINT DEFAULT NULL COMMENT '实际领取人，仅申领出库及其归还有值',
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
  KEY idx_material_document_pickup_user (pickup_user_id),
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
  CONSTRAINT fk_material_document_pickup_user FOREIGN KEY (pickup_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
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

INSERT INTO sys_menu
(id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(4100, NULL, 'item_admin', '物料管理', '/admin/item', NULL, NULL, 'DIRECTORY', 50, 1),
(4101, 4100, 'item_workspace', '物料工作台', '/admin/item/workspace', 'ItemWorkspace', 4001, 'PAGE', 1, 1),
(4110, NULL, 'item_teacher', '物料服务', '/item', NULL, NULL, 'DIRECTORY', 25, 1),
(4111, 4110, 'item_claim_center', '我的物料申领', '/item/claims', 'ItemClaimCenter', 4016, 'PAGE', 1, 1),
(4112, 4110, 'item_plan_list', '我的预设方案', '/item/plans', 'ItemPlanList', 4024, 'PAGE', 2, 1),
(4113, 4110, 'item_pickup_center', '我的代领', '/item/pickups', 'ItemPickupCenter', 4030, 'PAGE', 3, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(4201, 1, 4001, 1), (4202, 1, 4002, 1), (4203, 1, 4003, 1),
(4204, 1, 4004, 1), (4205, 1, 4005, 1), (4206, 1, 4006, 1),
(4207, 1, 4007, 1), (4208, 1, 4008, 1), (4209, 1, 4009, 1),
(4210, 1, 4010, 1), (4211, 1, 4011, 1), (4212, 1, 4012, 1),
(4213, 1, 4013, 1), (4214, 1, 4014, 1), (4215, 1, 4015, 1),
(4216, 1, 4016, 1), (4217, 1, 4020, 1), (4218, 1, 4021, 1),
(4219, 1, 4022, 1), (4220, 1, 4023, 1), (4221, 1, 4026, 1),
(4222, 1, 4027, 1),
(4231, 2, 4016, 1), (4232, 2, 4017, 1), (4233, 2, 4018, 1),
(4234, 2, 4019, 1), (4235, 2, 4020, 1), (4236, 2, 4024, 1),
(4237, 2, 4025, 1), (4238, 2, 4026, 1), (4239, 2, 4027, 1),
(4240, 2, 4015, 1), (4241, 2, 4028, 1), (4242, 2, 4029, 1),
(4243, 3, 4030, 1), (4244, 3, 4026, 1), (4245, 3, 4027, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(4301, 1, 4100, 1), (4302, 1, 4101, 1),
(4308, 2, 4110, 1), (4309, 2, 4111, 1), (4310, 2, 4112, 1),
(4311, 3, 4110, 1), (4312, 3, 4113, 1);

DELIMITER //
CREATE PROCEDURE assert_new_system_baseline_complete()
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线完成校验失败：必须为22张基础表加18张物料表，共40表';
  END IF;
  IF (SELECT COUNT(*) FROM sys_permission WHERE module_code = 'item' AND id BETWEEN 4001 AND 4030) <> 30 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线物料权限种子不完整';
  END IF;
  IF (SELECT COUNT(*) FROM sys_menu WHERE id IN (4100, 4101, 4110, 4111, 4112, 4113)
      AND menu_code IN ('item_admin', 'item_workspace', 'item_teacher', 'item_claim_center', 'item_plan_list', 'item_pickup_center')) <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线物料菜单种子不完整';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 1 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 22
     OR (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 2 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 12
     OR (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 3 AND p.module_code = 'item' AND rp.enabled = 1 AND rp.deleted = 0) <> 3
     OR EXISTS (SELECT 1 FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 5 AND p.module_code = 'item') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线物料角色权限绑定校验失败';
  END IF;
  IF (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id IN (4100, 4101) AND enabled = 1 AND deleted = 0) <> 2
     OR (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 2 AND menu_id IN (4110, 4111, 4112) AND enabled = 1 AND deleted = 0) <> 3
     OR (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 3 AND menu_id IN (4110, 4113) AND enabled = 1 AND deleted = 0) <> 2
     OR EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3 AND menu_id IN (4100, 4101, 4111, 4112))
     OR EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (4100, 4101, 4110, 4111, 4112, 4113)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线物料角色菜单绑定校验失败';
  END IF;
  IF EXISTS (SELECT 1 FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
      WHERE rp.role_id = 5 AND p.module_code <> 'system')
     OR EXISTS (SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
      WHERE rm.role_id = 5 AND m.menu_code NOT LIKE 'system%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新基线运维管理员不得持有业务权限或菜单';
  END IF;
END //
CALL assert_new_system_baseline_complete() //
DROP PROCEDURE assert_new_system_baseline_complete //
DELIMITER ;

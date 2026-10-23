# 数据库脚本说明

本目录为实验室设备管理系统 MySQL 8.4 数据库脚本。库名 `lab_management`，字符集 `utf8mb4 / utf8mb4_0900_ai_ci`，时间点列 `DATETIME(3)` 存 UTC，会话时区 `+00:00`。

每次改动数据库后，须在 [docs/数据库改动记录.md](../docs/数据库改动记录.md) 倒序登记。

## 一、全新建库

按以下顺序执行，一个都不能少、顺序不能乱（后面脚本依赖前面的表与种子）：

| 顺序 | 文件 | 是什么 / 有什么用 |
| --- | --- | --- |
| 1 | `sys_rbac.sql` | **RBAC 基准**。11 张表：部门、预录名单、用户、角色、权限、菜单、用户-角色、角色-权限、角色-菜单、数据范围、预约限制。含种子：设备处(id=1)、角色 ADMIN(实验室管理员)/TEACHER/STUDENT/ASSISTANT(1-4)、管理员 admin(id=1，密码哈希为占位值，部署前必须替换)、system 模块 26 项权限 + 9 个菜单（用户/名单合并为人员管理统一视图，预约限制与数据范围页面已下线；system 操作不绑 ADMIN，由 OPS_ADMIN 动态接管；TEACHER 仅绑助理授权三项） |
| 2 | `sys_policy.sql` | **业务策略参数表** `business_policy`。码+版本唯一、生效区间不重叠、发布后不可改；运行期按策略码读"当前生效的最新启用版本"（如预约时限等可调参数） |
| 3 | `assistant_grant.sql` | **助理授权** `assistant_grant` + `assistant_grant_scope`。老师授权学生代办（学生代理），有效授权动态计算出 ASSISTANT 附加角色，不持久化到用户-角色表 |
| 4 | `lab_management_all.sql` | **业务整合基线**（最大的一份）。分模块：① 场地 `lab` ② 统一单据与审批 `biz_document/approval_task/document_event` ③ 设备（分类/型号/台账/位置历史/借用/使用；含三权级使用申请与台账导入导出种子 170-179/216-221） ④ 物料一阶段（分类/SKU/库位/余额/单据/流水；只记数量不记金额） ⑤ 物料二阶段（申领/定额/结算；盘点已砍除、SELF 学生预约与老师审核已下线、定额全局化） ⑥ 场地预约（`class_period` 官方 12 节次 / `lab_booking_series` 周期规则 / `lab_booking` 预约单 / `lab_slot` 占用槽；预约管理页并入空间场地管理组）。含全部业务权限、菜单、角色绑定种子；头部注释有建表规则与编号段登记，新加表前先读 |
| 5 | `sys_operation_log.sql` | **操作日志表** `sys_operation_log`。只追加不改删；登录、审批、敏感操作审计；动作码与权限码同规范 |
| 6 | `sys_scope.sql` | **管理员数据范围种子**。给 admin(id=1) 授 ALL+WRITE（id=1）。无范围记录不自动授予全校数据，少了它管理员的写操作会全部 403 |
| 7 | `sys_ops_role.sql` | **运维管理员**。预置角色 OPS_ADMIN(id=5) + 账号 opsadmin(id=2)，动态绑定库内全部启用权限与菜单；不受数据范围约束。密码哈希同为占位值，部署前替换 |
| 8 | `docx_seed_import.sql` | **期初数据**。按《实训室设备物料清单与工作流程.docx》清洗导入：5 个实训室 + 公共库房、设备台账（一律 IDLE 待分类）、61 个物料 SKU、数量明确的库存以 OPENING 单据过账 |

执行前提提醒：`sys_rbac.sql` 永远是第一个；`sys_ops_role.sql` 必须在全部模块脚本之后（它要绑定库内已有权限）；整库执行前确认 MySQL 会话时区为 `+00:00`。

## 二、migrations/ 旧库迁移

**用途**：已经建过库、库里已有旧种子数据的环境（如组内早期开发库），不删库重建，改用这里的脚本把旧库"补/改"到当前定稿形态。**全新建库一律不执行本目录**（基线已含这些内容，执行了也会因幂等设计被跳过，但不要依赖这一点）。

除特别标注外脚本均为幂等（`INSERT IGNORE` / 范围 `DELETE` / `information_schema` 判存守卫），可重复执行：

| 文件 | 是什么 / 有什么用 |
| --- | --- |
| `booking_deploy.sql` | **预约模块首发部署**。适用：已有 RBAC + `lab` 表、还没有预约模块的库。建 `lab_booking`/`lab_slot` 两张表 + 预约授权种子；末尾自动回收曾误授给学生的预约权限 |
| `booking_flow_deploy.sql` | **预约权级流程增量**（2026-10-17 定稿）。适用：已有预约模块的旧库。补建 `biz_document/approval_task/document_event` 三张流程表（审批流：教师发起 → 管理员核对/驳回 → 签到清点，超时自动取消）；新增学生只读权限 3005 `booking:lab:view` + 学生只读菜单 3004。**注意：其 3005/3004 内容已吸收进基线 `lab_management_all.sql` 6.3-6.5 节，新建库无需执行** |
| `booking_student_revoke.sql` | **学生预约权限回收**。适用：执行过"师生均授预约权限"旧版种子的库。删除学生的预约权限(3001/3002/3003)与菜单 3003 绑定；只回收授权，不删学生历史预约数据 |
| `booking_period_series.sql` | **节次时间轴 + 批量周期预约**（2026-10-20，来自 dev/space）。适用：2026-10-19 前建成的库。建 `class_period`（含 8 条节次种子）与 `lab_booking_series`，`lab_booking` 追加日期/节次/series_id/课程/班级/上课老师 7 列（均可空，旧行不受影响）。**注意：全新建库无需执行，基线模块六已含全部内容** |
| `item_location_custodian.sql` | **库位保管人补列**（2026-10-19 需求定稿，来自 material-manage）。适用：建库早于该定稿的库。`stock_location` 幂等补 `custodian_id` 列与外键 |
| `item_stocktake_drop.sql` | **盘点功能全量下线**（2026-10-20，对应 2026-10-19 需求定稿，来自 material-manage）。适用：曾执行过含盘点（stocktake 两表、权限 133-137/166、菜单 183/195-199/214 及绑定）旧版种子的库。清理种子、DROP 盘点两表与 `adjust_direction` 列；**盘点历史随表删除不可恢复，执行前先备份** |
| `item_quantity_rename.sql` | **数量列改名**（2026-10-20）。适用：2026-10-20 前建成的库。`stock_document_line.qty`、`material_settlement.qty` 改名 `quantity` 对齐 Java 实体（旧列名导致查询报错）；含 CHECK 约束先 DROP 再重建，幂等可重放 |
| `equipment_repair_renumber.sql` | **设备报修与设备菜单改号**（2026-10-20 整合冲突裁决）。适用：已执行 2026-10-19 版正本（报修权限 160-161、设备菜单 202/204）的库。报修权限改号 168-169、设备菜单改号 210-211；按 module_code/menu_code 守卫只动 equipment 侧，OPS_ADMIN 绑定自动解绑补绑，幂等可重放 |
| `item_delete_permission.sql` | **物料 delete 权限段补登记 + 学生代理改号**（2026-10-20 整合，来自 material-manage）。适用：建库早于 2026-10-20 整合版的库。补权限 138-140、菜单 200-202、绑定 238-240/350-352；学生代理绑定 238-239/350 改号 249-250/361。**若库内菜单 202 为 equipment 占用，须先执行 `equipment_repair_renumber.sql`** |
| `item_transfer_permission.sql` | **物料批量导入/导出权限补登记**（2026-10-17，来自 material-manage）。适用：缺权限 160-163、菜单 206-209 种子的库。INSERT IGNORE 幂等 |
| `item_stock_transfer_permission.sql` | **库存余额导入/导出权限补登记**（2026-10-17，来自 material-manage）。适用：缺权限 164-165、菜单 212-213 种子的库。INSERT IGNORE 幂等 |
| `item_request_proxy.sql` | **申领两级审核 + 学生代理代领**（2026-10-19，来自 material-manage）。适用：已按 2026-10-17 版正本初始化的库。`material_request` 加 `request_type`/`source_request_id`、`material_request_line` 加 `source_line_id` 并扩展状态机；补权限 167、菜单 215 与角色绑定。**注意：ALTER/ADD CONSTRAINT 部分不可重复执行，须一次性执行成功** |
| `system_permission_extend.sql` | **system 权限扩展 28-31**（2026-10-20，收编 dev/basic_model 与 dev/auth）。适用：sys_rbac 仅 27 项 system 权限的旧库。补调整角色权限/名单导入导出/绑定管理员角色 4 项，OPS_ADMIN 同步补绑。INSERT IGNORE 幂等 |
| `system_user_roster_merge.sql` | **人员管理统一视图 + 预约限制下线**（2026-10-21，收编 dev/basic_model）。适用：2026-10-20 基线（31 项权限 + 12 菜单）之前建成的库。菜单 2 改人员管理（SystemUserRoster），删菜单 3/7 与权限 17-19；含 OPS_ADMIN 动态绑定回收，幂等 |
| `system_scope_offline.sql` | **数据范围页面下线**（2026-10-22，收编 dev/basic_model）。适用：含权限 15-16/菜单 6 的旧库。删菜单 6 与权限 15-16（编号弃用不复用）；`sys_user_data_scope` 表保留，管理员不再受数据范围约束。幂等 |
| `item_request_student_info.sql` | **申领单学生信息列 + TEACHER 类型**（2026-10-22，来自 material-manage）。适用：此前建成的库。`material_request` 加 student_no/student_name/student_class_name 三列，CHECK 放开 TEACHER。information_schema 判存幂等 |
| `item_request_remove_self.sql` | **学生预约（SELF）下线 + 单级审核定稿**（2026-10-22，来自 material-manage）。request_type 收紧 TEACHER/PROXY、状态去 DRAFT/PENDING、默认值变更；删权限 167/菜单 215/绑定 264/266/366/369。**SELF/PROXY 历史数据删除不可恢复，执行前先备份** |
| `item_request_remove_partially_fulfilled.sql` | **「部分实发」派生状态下线**（2026-10-22，来自 material-manage）。历史 PARTIALLY_FULFILLED 归位 APPROVED，状态 CHECK 收敛五值。判存幂等 |
| `item_quota_menu_merge.sql` | **领用定额独立菜单下线**（2026-10-22，来自 material-manage）。删菜单 181/189-192 与绑定 331/339-342（定额并入物料预约页签）；权限 127-130 保留。编号+编码双路幂等 |
| `item_quota_global.sql` | **领用定额转全局定额**（2026-10-22，来自 material-manage）。`material_quota.offering_id` 归一为 0、DEFAULT 0；同物料多行物理去重保留 id 最大一条。幂等可重放 |
| `item_movement_operator_name.sql` | **库存流水经办人姓名补列**（2026-10-23，来自 material-manage）。`stock_movement` 加 `operator_name VARCHAR(64) NULL`（台账直接登记必填姓名）。判存幂等 |
| `class_period_official_12.sql` | **节次按官方作息改 12 节**（2026-10-23，来自 dev/space）。1-8 节时刻按《福州软件职业技术学院教学作息时间表》修订，新增 9-12 节（至 21:45），`lab.open_end` 放宽 21:45。INSERT IGNORE 幂等 |
| `admin_space_center_nav.sql` | **管理端导航整合**（2026-10-23，来自 dev/space）。菜单 1001 更名「空间场地管理」，预约管理 3001/审批 3002 并入，目录 3000 停用保留；新增菜单 1010 综合管理 + ADMIN 绑定。幂等 |
| `equipment_model_quantity.sql` | **设备型号数量列**（2026-10-16 定稿，2026-10-23 收编）。适用：`equipment_model` 无 `quantity` 列的旧库。补列 + 按台账回填，判存幂等 |
| `equipment_booking_three_tier.sql` | **设备使用申请三权级 + 台账导入导出种子**（2026-10-23，收编固定设备管理与 space_zhou_hack）。补权限 170-179、菜单 216-221、绑定 533-534/545-559、菜单绑定 616-623；219 审批页挂设备管理 210 下。NOT EXISTS 幂等；执行后重跑 `sys_ops_role.sql` |

旧库升级顺序（按需跳过已具备的）：`booking_deploy.sql` → `booking_flow_deploy.sql` →（如曾授予学生权限）`booking_student_revoke.sql` → `booking_period_series.sql` → `item_location_custodian.sql` → `item_stocktake_drop.sql` → `item_quantity_rename.sql` → `equipment_repair_renumber.sql` → `item_delete_permission.sql` → `item_transfer_permission.sql` → `item_stock_transfer_permission.sql` → `item_request_proxy.sql` → `system_permission_extend.sql` → `system_user_roster_merge.sql` → `system_scope_offline.sql` → `item_request_student_info.sql` → `item_request_remove_self.sql` → `item_request_remove_partially_fulfilled.sql` → `item_quota_menu_merge.sql` → `item_quota_global.sql` → `item_movement_operator_name.sql` → `class_period_official_12.sql` → `admin_space_center_nav.sql` → `equipment_model_quantity.sql` → `equipment_booking_three_tier.sql` →（重跑 `sys_ops_role.sql` 刷新 OPS_ADMIN 全量绑定）。

## 三、常见问题

- **新建库报 1452 外键错误**：执行顺序错了，权限/菜单必须先于引用它的绑定出现，按上表顺序重跑。
- **新建库报 1062 主键冲突**：重复执行了某个脚本，或把 migrations 当建库脚本跑了。
- **登录后写操作全 403**：漏执行 `sys_scope.sql`（数据范围页面 2026-10-22 已下线，该种子无害保留；新版后端不再按数据范围拦截管理员）。
- **admin/opsadmin 登不上**：种子密码哈希是占位值，需替换为真实 BCrypt 密文。
- **新增表/种子**：先读 `lab_management_all.sql` 头部 9 条规则与编号段登记，新编号段先向数据库组长登记，改完在 [docs/数据库改动记录.md](../docs/数据库改动记录.md) 补一条记录。

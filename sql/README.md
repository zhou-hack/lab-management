# new_system 数据库脚本说明

## 唯一推荐初始化方式

全新初始化只使用 **`new_system_baseline.sql`**。不要在基线前后叠加执行其他拆分脚本，也不要把旧初始化脚本或 `migrations/` 当作建库流程。

- 允许的目标库：全新的空库 `new_system` 或 `new_system_test`。
- **禁止在 `lab_v0_1`、`lab_management` 执行本目录任何初始化或迁移脚本。**
- 基线使用 MySQL 8.x、`utf8mb4 / utf8mb4_0900_ai_ci`，时间点列使用 `DATETIME(3)` 存 UTC，会话时区为 `+00:00`。
- 基线自身会校验当前库名；执行前仍须主动确认目标库，避免连接错库。

## 本机 MySQL 客户端初始化命令

以下命令在 **PowerShell** 中执行，前提是本机 `mysql` 客户端已加入 `PATH`，且所用账号具有建库和初始化权限。命令只会创建缺失的数据库；若数据库已存在，必须先自行确认它确为允许初始化的空库。

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"

# 创建两个目标库（执行时输入 MySQL 密码）
mysql -u root -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS new_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS new_system_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 确保 Windows PowerShell 向 mysql 进程传递 UTF-8 内容
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# 分别初始化两个空库（每次执行时输入 MySQL 密码）
Get-Content -Raw -Encoding UTF8 ".\new_system_baseline.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\new_system_baseline.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

如只需其中一个环境，只执行对应的基线管道命令即可。基线末尾会断言 **40 张表**、30 项 item 权限、6 个 item 页面/目录菜单及角色绑定完整。

## 现有 22 表库的物料模块迁移

已经按上一版精简基线初始化的 `new_system` / `new_system_test`，依次追加执行 **`item_material.sql`**、**`migrations/item_assistant_pickup.sql`**、**`migrations/item_location_responsible_history.sql`**、**`migrations/item_location_code_reuse.sql`**、**`migrations/item_notification_events.sql`** 和 **`migrations/item_consumable_sku_clear.sql`**；不要重跑基线，也不要执行旧物料或设备 migrations：

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# 分别在确认后的现有22表目标库执行（每次执行时输入 MySQL 密码）
Get-Content -Raw -Encoding UTF8 ".\item_material.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\item_material.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_assistant_pickup.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_assistant_pickup.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_responsible_history.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_responsible_history.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_code_reuse.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_code_reuse.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_notification_events.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_notification_events.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_consumable_sku_clear.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_consumable_sku_clear.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

- 脚本只允许 `new_system` / `new_system_test`，前置断言要求现行 22 张基础表及 ADMIN/TEACHER/STUDENT/OPS_ADMIN 角色齐全，并检查 4000 段种子冲突。
- 使用 `CREATE TABLE IF NOT EXISTS` 与 `INSERT IGNORE`，完成后断言 38 表及角色绑定；可在同一目标库重复执行。若前置或完成断言失败，应先核对实际结构，不能改库名绕过。
- 只创建结构、权限、菜单和角色绑定，不插入物料档案、资产、库位、库存、申领或通知等业务数据。
- 本模块迁移与基线中的 16 表结构一致；基线在旧 item/equipment 种子清理段之后才写入新 item 种子，避免被旧清理段删除。

## 现有 38 表库的学生代领迁移

已包含物料模块的 `new_system` / `new_system_test` 单独执行 **`migrations/item_assistant_pickup.sql`**，升级为学生助理授权与原申领单代领的 39 表终态。脚本只新增授权表、委派/领取人字段及权限菜单，不生成或修改真实申领；可重复执行。

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 ".\migrations\item_assistant_pickup.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_assistant_pickup.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

## 现有 39 表库的库位负责人历史迁移

已包含学生代领的 `new_system` / `new_system_test` 单独执行 **`migrations/item_location_responsible_history.sql`**，升级为 40 表终态。脚本只新增 `stock_location_responsible_history`，不删除或回填 `fixed_asset.custodian_user_id` 遗留列，可重复执行。后续负责人集合实际变化由 `PUT /api/v1/item/locations/{id}/admins` 在同一事务追加历史，无变化、校验失败和回滚均不写历史。

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_responsible_history.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_responsible_history.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

## 现有 40 表库的库位编码复用迁移

已包含库位负责人历史的 `new_system` / `new_system_test` 单独执行 **`migrations/item_location_code_reuse.sql`**。脚本将原来的 `location_code` 单列唯一改为生成列 `active_location_code` 唯一：未删除行保留编码，已删除行为 `NULL`，因此支持同一编码多次删除重建；表总数仍为 40，已有库位、库存、单据与审计数据不删除、不清空，可重复执行。

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_code_reuse.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_location_code_reuse.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

## 现有 40 表库的通知事件迁移

已包含库位编码复用结构的 `new_system` / `new_system_test` 单独执行 **`migrations/item_notification_events.sql`**。脚本保留历史逾期通知，将 `material_document_id` 放开为空，扩展审批、批准、归还提醒、代领及每2小时阶段约束，并为 STUDENT 补充本人通知查询/已读权限；表总数和菜单不变，可重复执行。随后执行 **`migrations/item_consumable_sku_clear.sql`**，将耗材（含历史逻辑删除行）的 SKU 统一清空；固定资产 SKU、资产编码及业务单据不变，可重复执行。

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 ".\migrations\item_notification_events.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_notification_events.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
Get-Content -Raw -Encoding UTF8 ".\migrations\item_consumable_sku_clear.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\item_consumable_sku_clear.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

## 现有库的运维管理员权限收敛

2026-10-28 起 OPS_ADMIN 仅保留 system 模块权限和 `system%` 菜单，不参与 space、booking、item 等业务流程。已经初始化的 `new_system` / `new_system_test` 单独执行以下幂等迁移；全新空库不需要执行：

```powershell
Set-Location "d:\实验室管理\try\new_system\sql"
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding UTF8 ".\migrations\ops_admin_system_only.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system
Get-Content -Raw -Encoding UTF8 ".\migrations\ops_admin_system_only.sql" | mysql -u root -p --default-character-set=utf8mb4 new_system_test
```

迁移会重建 OPS_ADMIN 权限和菜单绑定，并补齐人员管理所需的系统父目录；其他角色及业务数据不变。

## 基线包含的 40 张表

| 范围 | 数量 | 表 |
| --- | ---: | --- |
| RBAC、用户与数据范围 | 10 | `sys_department`、`sys_person_roster`、`sys_user`、`sys_role`、`sys_permission`、`sys_menu`、`sys_user_role`、`sys_role_permission`、`sys_role_menu`、`sys_user_data_scope` |
| 业务策略 | 1 | `business_policy` |
| 场地与维护时段 | 2 | `lab`、`lab_maintenance_window` |
| 共享单据与审批 | 3 | `biz_document`、`approval_task`、`document_event` |
| 场地预约 | 5 | `user_booking_restriction`、`class_period`、`lab_booking_series`、`lab_booking`、`lab_slot` |
| 操作日志 | 1 | `sys_operation_log` |
| 物料与固定资产 | 18 | `material`、`fixed_asset`、`stock_location`、`stock_location_admin`、`stock_location_responsible_history`、`stock_balance`、`material_booking_plan`、`material_booking_plan_line`、`material_assistant_grant`、`material_claim`、`material_claim_line`、`material_claim_allocation`、`material_reservation`、`material_document`、`material_document_line`、`material_settlement`、`stock_movement`、`material_notification` |

旧设备、旧物料和旧 `assistant_grant/assistant_grant_scope` 不恢复；新物料体系统一使用上表及 `item:*` 权限，不复用旧表或旧编号。

## 其他保留脚本的使用边界

- `sys_rbac.sql`、`sys_policy.sql`、`sys_operation_log.sql`、`sys_scope.sql`、`sys_ops_role.sql` 作为分模块/历史参考脚本保留，**不是新基线的追加初始化步骤**。
- `item_material.sql` 仅用于上述现行 22 表库追加物料模块；不是旧版设备/物料库的通用升级脚本。
- `migrations/booking_*.sql` 等场地预约迁移，以及其余明确只服务场地或 RBAC 的迁移继续保留；保留不代表适用于全新初始化。
- **禁止批量执行旧 `migrations/`。** 对确有旧库升级需求的脚本，必须先逐个核对目标库、前置结构和脚本内容，再单独决定是否使用；不得用于上述全新建库流程。

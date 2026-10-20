# 场地预约「权级流程」数据库部署说明

- 对应脚本：`sql/migrations/booking_flow_deploy.sql`（幂等，可重复执行）
- 版本日期：2026-10-17
- 适用环境：dev、test 各自独立数据库，分别执行一次
- 配套代码：`dev/space` 分支提交 `4a95035` 及之后版本（后端必须先于或同时部署）

## 一、脚本做了什么

| 序号 | 类型 | 对象 | 内容 |
| --- | --- | --- | --- |
| 1 | DDL | `biz_document` | 统一单据表（`CREATE TABLE IF NOT EXISTS`，已存在则跳过） |
| 2 | DDL | `approval_task` | 审批阶段待办表（同上） |
| 3 | DDL | `document_event` | 单据事件表（同上，不可变过程历史） |
| 4 | DML | `sys_permission` | 新增权限 `3005 booking:lab:view`（场地预约只读） |
| 5 | DML | `sys_menu` | 新增菜单 `3004 预约情况`，路径 `/booking/labs`，挂权限 3005 |
| 6 | DML | `sys_role_permission` | 新增绑定 3008/3009/3010（学生 3、管理员 1、教师 2 均授 3005） |
| 7 | DML | `sys_role_menu` | 新增绑定 3005（学生 3 ↔ 菜单 3004） |
| 8 | DML | 回收 | 兜底回收学生历史误授的 3001/3003/3004 写权限与 3000-3003 菜单 |

部署后角色权限最终状态：

| 角色 | 预约权限 | 预约菜单 |
| --- | --- | --- |
| 管理员 ADMIN(1) | 3001/3002/3003/3004/3005 | 3000/3001/3002 |
| 教师 TEACHER(2) | 3001/3002/3003/3005 | 3003 |
| 学生 STUDENT(3) | **仅 3005（只读）** | **仅 3004（预约情况）** |

## 二、执行前检查

### 1. 备份目标库

```bash
mysqldump -u root -p --single-transaction --routines --triggers 目标库名 > backup_booking_flow_$(date +%Y%m%d).sql
```

### 2. 确认依赖的基础脚本已部署

必须已存在：`sys_role`、`sys_permission`、`sys_menu`、`sys_role_permission`、
`sys_role_menu`（RBAC）以及 `lab_booking`、`lab_slot`（预约模块，
见 `sql/migrations/booking_deploy.sql`）。缺基础表脚本会直接报错。

### 3. 确认固定主键未被占用（预期全部为空）

```sql
SELECT * FROM sys_permission      WHERE id = 3005;
SELECT * FROM sys_menu            WHERE id = 3004;
SELECT * FROM sys_role_permission WHERE id IN (3008, 3009, 3010);
SELECT * FROM sys_role_menu       WHERE id = 3005;
```

若任一查询返回了**其它模块**的数据，停止执行，先把号段冲突反馈开发，
改用不冲突的主键后再部署，不要手工改本脚本的 ID（前后端不依赖这组 ID，
但菜单与权限的绑定行互相引用，需整体替换）。

### 4. 确认角色与部门约定

```sql
SELECT id, role_code FROM sys_role WHERE role_code IN ('ADMIN','TEACHER','STUDENT');
-- 预期 1=ADMIN、2=TEACHER、3=STUDENT
SELECT id FROM sys_department WHERE id = 1;
-- 预期存在（biz_document.department_id 非空，缺省回退设备处 id=1）
```

## 三、执行（务必指定 utf8mb4）

```bash
mysql -u root -p --default-character-set=utf8mb4 目标库名 < sql/migrations/booking_flow_deploy.sql
```

注意：不加 `--default-character-set=utf8mb4` 时，Windows 中文环境下菜单名
「预约情况」可能写入乱码。脚本本身已声明 `utf8mb4/utf8mb4_0900_ai_ci`。

## 四、执行后校验

```sql
-- 权限与菜单
SELECT id, permission_code, permission_name FROM sys_permission WHERE id = 3005;
SELECT id, menu_name, route_path, permission_id FROM sys_menu WHERE id = 3004;

-- 三角色授权
SELECT role_id, GROUP_CONCAT(permission_id ORDER BY permission_id) AS perms
FROM sys_role_permission WHERE permission_id BETWEEN 3001 AND 3005
GROUP BY role_id ORDER BY role_id;
-- 预期：1 → 3001-3005；2 → 3001,3002,3003,3005；3 → 3005

-- 流程三表
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
WHERE TABLE_SCHEMA = '目标库名'
  AND TABLE_NAME IN ('biz_document','approval_task','document_event');
```

中文菜单显示正常、学生仅 3005/3004 即部署成功。

## 五、应用发布与验证

1. 先发布后端代码（含新 Mapper 与 `BookingTimeoutScheduler` 定时任务）。
2. 执行本 SQL。
3. 重启后端服务，使连接池、权限缓存与定时任务加载新结构。
4. 用学生账号重新登录：
   - 左侧菜单出现「预约情况」（`/booking/labs`）；
   - 列表走 `GET /api/v1/booking/labs/board`，可看全部预约与批准/驳回结果；
   - 详情可见驳回原因与流程时间线；
   - 页面无「发起预约/取消预约」按钮；
   - 直接调写接口（create/cancel/approve/checkin/checkout）返回 40300。
5. 用教师账号验证：仍可发起、撤销本人预约；用管理员账号验证：审批、
   开门签到、结束清点按钮与接口正常。

## 六、注意事项

- 脚本幂等：`IF NOT EXISTS` + `INSERT IGNORE`，重复执行不产生重复数据。
- 三张流程表若远端已由整合脚本 `lab_management_all.sql` 建立，将自动跳过，
  不会改动既有表结构；本脚本版本不带跨模块外键（`policy_id` 本期不用），
  避免依赖 `business_policy` 等表是否已部署。
- 时间一律 UTC：表默认值 `UTC_TIMESTAMP(3)`；远端 MySQL 服务与会话保持
  `+00:00`，Connector/J 保持
  `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true`。
- 历史 PENDING 预约：本功能上线前已存在的待审批单若没有对应 `biz_document`，
  新审批逻辑会提示「预约流程档案缺失」。上线时若库中存在此类历史单，
  需先做数据回填（当前 dev 库 lab_booking 为 0 行，无需回填）。
- 定时任务默认每 10 分钟扫描一次 `start_at` 已过仍 PENDING 的预约自动取消，
  启动后延迟 1 分钟首次执行；可用配置项
  `lab.booking.timeout-scan-delay`（毫秒）调整间隔。

## 七、回滚

回滚仅清理授权与菜单种子；三张流程表建议保留（空表或历史台账，无副作用）。

```sql
DELETE FROM sys_role_menu       WHERE role_id = 3 AND menu_id = 3004;
DELETE FROM sys_role_permission WHERE id IN (3008, 3009, 3010);
DELETE FROM sys_menu            WHERE id = 3004;
DELETE FROM sys_permission      WHERE id = 3005;
-- 如需彻底移除流程表（确认无数据后再执行，不可恢复）：
-- DROP TABLE document_event;
-- DROP TABLE approval_task;
-- DROP TABLE biz_document;
```

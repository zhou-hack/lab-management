# RBAC 实施清单（权限标识 / 菜单 / 认证契约）

> 定位：实施层登记文档，衔接 [权限清单.md](权限清单.md)（PRD 功能矩阵）与各模块建表脚本。
> 权限标识、菜单与初始数据以本文为准登记；功能范围变化时先改权限清单，再同步本文。
> 版本：实施登记 v0.2；整理日期：2026-10-27。

---

## 一、模块编码与初始数据 ID 段

权限标识规范 `模块:资源:操作`，模块编码如下。初始数据使用固定小 ID（与雪花 ID 不冲突），按模块分段：

| module_code | 模块 | 设计依据（数据库设计） | 初始数据 ID 段 |
| --- | --- | --- | --- |
| system | 身份、组织、权限、日志、策略 | 身份与 RBAC、公共支撑 | 1–999 |
| space | 场地台账、开放窗口与维修时段 | 空间与教学基础 | 1000–1999 |
| booking | 场地单次/周期预约、使用记录 | 公共单据、场地预约 | 3000–3999 |
| item | 物料、固定资产、申领预占、库存单据与通知 | 物料与固定资产 | 4000–4999（本次使用4001–4312） |
| document | 共享单据、审批与事件 | 公共单据与审批 | 随公共支撑脚本登记 |

各模块的权限/菜单/角色绑定初始数据随本模块建表脚本登记；system 模块初始数据已在 [sql/sys_rbac.sql](../sql/sys_rbac.sql)。

---

## 二、权限标识登记

「归属角色」为初始绑定。预置角色仅为 `ADMIN / TEACHER / STUDENT / OPS_ADMIN`。
OPS_ADMIN（运维管理员）为最高级预置运维角色，仅 SQL 预置不开放页面创建，仅绑定 system 模块启用权限和 system% 菜单；后续业务模块不得为其补绑流程权限，不受数据范围约束。
2026-10-20 起系统管理（用户/名单/组织/角色/数据范围/预约限制/参数配置）整体划归 OPS_ADMIN 专属，ADMIN 仅保留 system:log:list 且仅可见业务模块日志（module_code 不含 system/auth）。2026-10-21 预约限制页面与接口下线（system:restriction:* 权限与菜单移除），user_booking_restriction 表与 UserBookingRestrictionMapper.countActive 保留供预约模块逾期冻结使用。

### 2.1 system 模块（已出初始 SQL）

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| system:user:list / get / create / update / enable / reset_password / bind_role | 用户管理 | 仅 OPS_ADMIN（createAdmin 固定绑 ADMIN 角色且调用方须全校写；bind_role 仅老师账号可绑定/解绑 ADMIN 角色、调用方须全校写，变更踢目标用户会话；OPS_ADMIN 账号仅 OPS_ADMIN 本人可编辑/启停/重置/绑定操作） |
| system:roster:list / create / update / enable / export / import | 预录名单管理（export 导出 CSV 复用列表筛选；import 批量导入 CSV 部分成功、需二级认证） | 仅 OPS_ADMIN |
| system:department:list / create / update / enable | 组织管理 | 仅 OPS_ADMIN |
| system:role:list | 角色与权限查看（角色仍 SQL 预置不可增删） | 仅 OPS_ADMIN |
| system:role:assign | 调整角色权限（整集替换 + 踢持有者会话；锁 OPS_ADMIN 角色） | 仅 OPS_ADMIN |
| system:scope:list / grant | 数据范围查看 / 授权 | 仅 OPS_ADMIN |
| system:log:list | 操作日志查询 | OPS_ADMIN（全量）/ ADMIN（仅业务模块日志，module_code 不含 system/auth） |
| system:policy:list / publish | 策略参数查看 / 发布 | 仅 OPS_ADMIN |

### 2.2 space 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| space:space:list / read | 场地分页、详情、可用选项与统计查询 | STUDENT / TEACHER / ADMIN |
| space:space:create / update / delete | 场地新增、编辑、启停、删除与维修维护 | ADMIN |

> 批量导入、导出、批量启停和维修窗口操作复用上述查询或写权限；具体端点以场地模块契约为准。

### 2.3 booking 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| booking:lab:create / cancel | 单次或周期场地预约申请 / 撤销 | STUDENT / TEACHER |
| booking:lab:list | 场地预约记录（数据范围控制可见性） | STUDENT / TEACHER / ADMIN |
| booking:lab:approve | 场地单次与周期预约一级审批 / 冲突处理 | ADMIN |
| booking:lab:finish | 场地使用结束管理 | ADMIN |
| booking:usage:list | 场地使用台账 | TEACHER（本人）/ ADMIN（全部） |

> 签到、签退、可用性查询和跳选拆分不新增独立模块；接口权限按实际端点复用预约创建、列表或使用记录权限。所有学生和老师申请均只经过管理员一级审批。

### 2.4 item 模块（2026-10-27；2026-10-29 修订学生本人通知权限）

| id | permission_code | 名称 | 归属角色 |
| ---: | --- | --- | --- |
| 4001 | item:material:list | 物料档案查询 | ADMIN |
| 4002 | item:material:manage | 物料档案维护 | ADMIN |
| 4003 | item:asset:list | 固定资产查询 | ADMIN |
| 4004 | item:asset:manage | 固定资产维护 | ADMIN |
| 4005 | item:location:list | 库位查询 | ADMIN |
| 4006 | item:location:manage | 库位及管理员维护 | ADMIN |
| 4007 | item:stock:list | 库存查询 | ADMIN |
| 4008 | item:stock:inbound | 期初补货入库 | ADMIN |
| 4009 | item:document:list | 库存单据查询 | ADMIN |
| 4010 | item:movement:list | 库存流水查询 | ADMIN |
| 4011 | item:transfer:create | 库位移动发出 | ADMIN |
| 4012 | item:transfer:receive | 移动接收与退回 | ADMIN |
| 4013 | item:settlement:create | 核销登记 | ADMIN |
| 4014 | item:settlement:review | 核销复核 | ADMIN |
| 4015 | item:overdue:list | 逾期列表 | TEACHER（本人）/ ADMIN（全量） |
| 4016 | item:claim:list | 申领查询 | TEACHER（本人）/ ADMIN（全量） |
| 4017 | item:claim:create | 申领创建与再次预约 | TEACHER（本人） |
| 4018 | item:claim:update | 本人申领修改 | TEACHER（本人） |
| 4019 | item:claim:cancel | 本人申领取消 | TEACHER（本人） |
| 4020 | item:claim:abandon | 放弃剩余与关闭 | TEACHER（本人请求）/ ADMIN（关闭） |
| 4021 | item:claim:review | 申领审核 | ADMIN |
| 4022 | item:claim:issue | 申领实发 | ADMIN |
| 4023 | item:claim:return | 申领归还 | ADMIN |
| 4024 | item:plan:list | 本人方案查询 | TEACHER（本人） |
| 4025 | item:plan:manage | 本人方案维护 | TEACHER（本人） |
| 4026 | item:notification:list | 通知查询 | TEACHER / ADMIN / STUDENT（均仅本人通知） |
| 4027 | item:notification:read | 通知已读 | TEACHER / ADMIN / STUDENT（均仅本人通知） |
| 4028 | item:assistant:manage | 本人助理授权管理 | TEACHER（本人） |
| 4029 | item:claim:delegate | 本人申领委派代领 | TEACHER（本人） |
| 4030 | item:pickup:list | 本人学生代领查询 | STUDENT（本人被委派） |

- TEACHER 共 12 项：4015–4020、4024–4029；4015 仅查询本人逾期，不包含物料档案、库存、审核、实发、归还或复核权限，申领预检通过申领接口完成，不要求档案权限。助理授权与原申领单委派均强制本人归属。
- ADMIN 共 22 项：4001–4016、4020–4023、4026–4027；STUDENT 共 3 项：4026、4027、4030，OPS_ADMIN 为 0 项。学生只能读取和已读本人通知及查询本人被委派代领，不能新建申领、审核、实发、归还、查看逾期列表或查看资产编码。
- 功能权限不替代数据资格：ADMIN 的固定资产编辑与维修切换、审核、实发、归还、移动和复核须另校验 `stock_location_admin`；复核人不能是经办人。教师接口须强制本人数据过滤，学生代领接口须强制 `delegate_student_id = 当前学生`，全部角色的通知接口均须强制 `recipient_user_id = 当前用户`。
- 角色权限绑定 ID：ADMIN 4201–4222，TEACHER 4231–4242，STUDENT 4243–4245；旧 OPS_ADMIN 绑定 4251–4277 弃用不复用。
- 库位选项接口分离：`GET /api/v1/item/locations/options` 仅返回启用库位的 ID、编码、名称和状态，持有 `item:location:list` 或 `item:claim:create` 任一权限即可访问，供申领计划分配使用；原库位分页、详情、库存和管理接口仍仅 ADMIN 可用，不向教师开放负责人或库位管理数据。
- 首页待办过滤：`GET /api/v1/item/admin/claims?managedOnly=true` 在原有 ADMIN 审核权限上，再按当前版本计划库位与当前用户的 `stock_location_admin` 关系过滤，供首页“待我处理”使用；未传该参数时仍返回原管理端申领分页，不改变既有行为。
- 库存单据筛选：`GET /api/v1/item/documents` 支持 `businessChainId` 精确筛选，沿用 `item:document:list` 权限；前端以字符串提交完整雪花 ID，后端按 Long 精确匹配 `material_document.business_chain_id`。
- 耗材库内位置编辑：`PUT /api/v1/item/balances/{id}/position` 复用 `item:stock:inbound` 权限并另校验当前用户为库存所属库位管理员；入参仅 `positionText/version`，数量、物料和所属库位不可通过该接口修改，目标位置已有同种耗材时返回 40900，版本失效时提示刷新。

---

## 三、菜单登记

菜单类型 DIRECTORY/PAGE/ACTION；`component_key` 大驼峰，只能对应 `frontend/src/views` 内组件白名单。ACTION 类按钮菜单按需随页面登记（permission_id 必填）。学生/老师端业务页面菜单随各模块脚本登记。

### 3.1 system 模块

| menu_code | 名称 | route_path | component_key | 绑定权限 | 归属角色 |
| --- | --- | --- | --- | --- | --- |
| system（目录） | 系统管理 | /system | — | — | ADMIN / OPS_ADMIN（ADMIN 仅见其下操作日志页） |
| system_user | 人员管理 | /system/user | SystemUserRoster | system:user:list | 仅 OPS_ADMIN（名单管理 2026-10-21 并入为统一人员视图：名单与账号一行一人，列表数据走 GET /system/persons（需 system:user:list + system:roster:list 双权限）；名单操作接口仍由 system:roster:* 保护，旧地址 /system/roster 静态重定向至本页） |
| system_department | 组织管理 | /system/department | SystemDepartment | system:department:list | 仅 OPS_ADMIN |
| system_role | 角色权限 | /system/role | SystemRole | system:role:list | 仅 OPS_ADMIN |
| system_scope | 数据范围 | /system/scope | SystemScope | system:scope:list | 仅 OPS_ADMIN |
| system_log | 操作日志 | /system/log | SystemLog | system:log:list | ADMIN / OPS_ADMIN（ADMIN 仅业务模块日志） |
| system_policy | 参数配置 | /system/policy | SystemPolicy | system:policy:list | 仅 OPS_ADMIN |

### 3.2 item 模块（4 个页面 + 2 个父目录）

| id / parent_id | menu_code | 名称 | route_path | component_key | 绑定权限 | 归属角色 |
| --- | --- | --- | --- | --- | --- | --- |
| 4100 / NULL | item_admin | 物料管理（目录） | /admin/item | — | — | ADMIN |
| 4101 / 4100 | item_workspace | 物料工作台 | /admin/item/workspace | ItemWorkspace | item:material:list (4001) | ADMIN |
| 4110 / NULL | item_teacher | 物料服务（目录） | /item | — | — | TEACHER / STUDENT |
| 4111 / 4110 | item_claim_center | 我的物料申领 | /item/claims | ItemClaimCenter | item:claim:list (4016) | TEACHER |
| 4112 / 4110 | item_plan_list | 我的预设方案 | /item/plans | ItemPlanList | item:plan:list (4024) | TEACHER |
| 4113 / 4110 | item_pickup_center | 我的代领 | /item/pickups | ItemPickupCenter | item:pickup:list (4030) | STUDENT |

- 管理父子树仅对 ADMIN 可见；教师可见申领、方案及本人助理授权，学生仅可见“我的代领”。学生同时绑定父目录 4110 和子页 4113，不产生孤儿菜单；OPS_ADMIN 不绑定任何 item 菜单。
- 角色菜单绑定 ID：ADMIN 4301–4302，TEACHER 4308–4310，STUDENT 4311–4312；旧 OPS_ADMIN 绑定 4303–4307 弃用不复用。
- 路由只使用下划线或单词路径，不使用横杠；组件键须由前端白名单实现后才可开放页面，本次数据库脚本不修改前端。

---

## 四、认证接口契约

前缀 `/api/v1`；access token 经 `Authorization: Bearer <token>` 传递，前端双 token 存 sessionStorage。除标记「公开」的接口外均需登录。

**口令传输（2026-10-20 起，替代 RSA 公钥方案）**：登录、敏感操作确认的口令字段一律 BCrypt 哈希上传。前端先经 `GET /auth/salt` 按账号取该账号盐前缀（库中哈希前 29 位），用 bcryptjs 以同一盐复算出 60 位哈希后提交，服务端与库中哈希做常量时间比对，明文不进入报文；新密码强度（8-64 位含字母数字）在设置时由服务端校验。哈希即凭据可被重放，内网/开发场景接受该风险；传输层安全仍以 HTTPS 为准。

| 接口 | 方法/路径 | 说明 | 关键入参 | 成功 data | 主要错误 |
| --- | --- | --- | --- | --- | --- |
| 口令盐（公开） | GET /auth/salt | 登录/确认前按账号取 BCrypt 盐前缀；账号不存在/未设密码返回统一 40100 防账号枚举 | accountName | salt | 40100 账号或密码错误 |
| 账号密码登录（公开） | POST /auth/login | 全部用户类型统一账密登录 | accountName, password（哈希） | accessToken, refreshToken, expiresIn, userId, realName, userType, accountName, roles, activeRole | 40100 凭据错误/账号锁定（msg 含剩余次数或分钟）；40000 参数校验 |
| 刷新令牌（公开） | POST /auth/refresh | 轮换 access + refresh 双 token，保留活动角色 | refreshToken | 同登录 | 40100 refresh 无效或过期 |
| 当前身份 | GET /auth/profile | 登录后/刷新时加载 | — | userId, realName, userType, accountName, roles, permissions, menus, activeRole, switchableRoles | 40100 未登录 |
| 切换活动角色 | POST /auth/switch_role | 多角色用户校验持有后重签双 token，重新装配权限/菜单 | roleCode | 同 profile（新活动角色全量装配）+ 新双 token | 40300 未持有该角色或该角色不允许切换 |
| 敏感操作确认 | POST /auth/password/confirm | 审批、处置等二次确认，通过后 300 秒内敏感接口免再确认 | password（哈希） | validSeconds（300） | 40100 密码错误 |
| 注销 | POST /auth/logout | 无状态 no-op；前端清理双 token 与状态即可 | — | null | — |

实施规则：

1. 连续 5 次密码错误锁定 30 分钟（`failed_login_count`/`locked_until`），错误提示含剩余次数或剩余分钟。
2. 会话时长全局统一：access 2 小时、refresh 7 天，不再按用户类型区分；`POST /auth/refresh` 在用户仍启用时轮换双 token。
3. 90 天强制换密、首登强制设密链路已随登录重写下线；密码由管理员在用户管理中预置或重置。
4. 短信验证码登录与名单首登自动建档已下线，登录入口仅账密；`sys_person_roster` 名单表保留用于身份核验预留。

---

## 五、决策项（2026-10-14 全部确认，已随 roles 模块落地）

| # | 事项 | 已定方案 | 落地位置 |
| --- | --- | --- | --- |
| 1 | 短信通道与验证码存储 | 已随登录重写下线（2026-10-20）：登录入口仅账密，短信相关服务与端点整体移除 | —（已删除） |
| 2 | 权限缓存失效 | JWT 无状态后不再缓存：角色权限每请求实时从库加载，变更即时生效；无状态会话无法主动踢人，依赖 access 2 小时自然过期 | JwtAuthFilter / AuthService |
| 3 | 安全参数入 business_policy | 已登记 9 项（含 SMS_CODE_MAX_FAIL=5），见 [sql/sys_policy.sql](../sql/sys_policy.sql) | PolicyService 读取缓存 |
| 4 | admin 初始密码 | 脚本保持占位；dev 库已替换为开发用散列，生产部署前必须替换并强制首登修改 | 部署 checklist |

> roles 模块认证链路已实现并通过应用级验证：账密登录、刷新、profile、二级确认、注销、活动角色切换、锁定与限流。业务模块仅登记当前保留的场地、预约、共享单据与日志能力。
>
> 2026-10-20 登录重写：Sa-Token 整体下线，改 JWT 无状态双 token；口令传输由 RSA 公钥改为 BCrypt 盐预取 + 前端复算哈希；短信验证码、强制改密、名单首登建档链路删除；`@SaCheckPermission` 全面替换为 `@RequirePermission`。2026-10-21 整合：JWT 与一人多角色融合，access/refresh 携带活动角色，switch_role 校验持有后重签双 token。

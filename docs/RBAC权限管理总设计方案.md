# RBAC 权限管理总设计方案

> 定位：权限体系的总体设计文档，向下衔接三份既有文档——[权限清单.md](权限清单.md)（PRD 功能矩阵）、[数据库设计.md](数据库设计.md)（表结构评审稿）、[RBAC实施清单.md](RBAC实施清单.md)（权限/菜单/认证契约登记）。冲突时以数据库设计.md 为准，登记类细节以 RBAC实施清单.md 为准。
> 版本：v1.0；整理日期：2026-10-15；状态：system 模块已落地并通过应用级验证；当前业务边界为 space、booking、document 及公共日志，其他已删除模块不再登记权限或菜单。

---

## 一、设计目标与原则

### 1.1 目标

1. 三类用户（学生/老师/管理员）+ 四个预置角色的访问控制，覆盖功能权限（能点什么）与数据权限（能看/改哪些行）两个维度。
2. 所有授权决策在服务端完成，前端隐藏入口仅为体验优化，不作安全依据。
3. 权限变更可审计：授权、撤销、范围调整、账号启停全部落操作日志，只追加不修改。
4. 一期不开放自定义角色、不做字段级权限，保持模型简单可验证。

### 1.2 原则

| 原则 | 含义 | 落地体现 |
| --- | --- | --- |
| 服务端强制 | 每个受保护接口由后端校验权限，前端指令/菜单只是镜像 | `@RequirePermission` + 数据范围 Service 校验 |
| 最小权限 | 无范围记录不自动授予全校数据；未持有角色不能切换为对应活动角色 | sys_user_data_scope 空表即零范围；switch_role 校验持有关系 |
| 身份不可自提 | 用户不能通过客户端指定 user_type / role_code 提升身份 | 名单核验派生角色；OPS_ADMIN 仅 SQL 预置 |
| 权限可即时回收 | 停用、撤销、范围收窄在下一请求立即生效 | JWT 无缓存每请求实时加载权限；access 2 小时自然过期兜底 |
| 审计只追加 | 操作日志不外键、不更新、不删除 | sys_operation_log 仅 INSERT |

---

## 二、总体架构

```
┌────────────────────────────────────────────────────────────────┐
│ 前端（Vue3 + Element Plus）                                      │
│  路由守卫 → 动态路由（componentKey 白名单）→ v-permission 指令      │
│  Axios 拦截器：Bearer token、401 清态跳登录、403 提示               │
└──────────────┬─────────────────────────────────────────────────┘
               │ Authorization: Bearer <token>
┌──────────────▼─────────────────────────────────────────────────┐
│ 后端（Spring Boot + JWT/jjwt + MyBatis-Plus）                    │
│                                                                 │
│  认证层  AuthService：账密登录、切换角色重签、锁定限流、二级确认      │
│  装配层  JwtAuthFilter：解析 token 后每请求实时装配角色/权限         │
│  校验层  @RequirePermission（功能权限）+ SysScopeService（数据范围）│
│  业务层  space / booking / document：场地、预约、共享单据与事件       │
│  审计层  @OperationLog AOP：独立事务写 sys_operation_log            │
└──────────────┬─────────────────────────────────────────────────┘
               │
┌──────────────▼─────────────────────────────────────────────────┐
│ MySQL（UTC 会话）                                                │
│  RBAC 11 表 + 场地 / 预约 / 共享单据 / 业务策略 / 日志 / 限制表      │
└────────────────────────────────────────────────────────────────┘
```

三层含义：**身份**（你是谁，名单预录派生）、**角色**（你能干什么，权限集合）、**范围**（你能对哪些数据动手，显式授权行）。

---

## 三、核心概念模型

### 3.1 用户与角色分离

- **用户类型（sys_user.user_type）**：STUDENT / TEACHER / ADMIN（OPS_ADMIN 账号 user_type 为 OPS_ADMIN）。登录方式统一账密（2026-10-20 起，短信验证码登录下线）；JWT 双 token：access 2 小时 + refresh 7 天。
- **角色（sys_role.role_code）**：权限集合。主身份角色由 user_type 派生；老师账号可经用户管理页叠加绑定 ADMIN 角色（一人多角色，2026-10-20 起）。预置角色仅为 ADMIN、TEACHER、STUDENT、OPS_ADMIN。
- **活动角色（token 携带的生效身份视图）**：多角色用户登录后默认以主身份角色为活动角色，权限/菜单按活动角色装配；可经 POST /api/v1/auth/switch_role 在持有角色间切换（校验持有后重签双 token），前端右上角账户菜单提供切换入口。
- 用户类型与角色正交但受控：角色只决定权限集合，user_type 决定主身份与默认活动角色。

### 3.2 四个预置角色

| 角色 | id | 定位 | 权限来源 | 创建方式 | 数据范围 |
| --- | --- | --- | --- | --- | --- |
| ADMIN | 1 | 设备处业务管理员 | system:log:list + 场地、预约等业务权限；2026-10-20 起系统管理整体划归 OPS_ADMIN，ADMIN 仅保留系统管理中的操作日志页且仅可见业务模块日志（module_code 不含 system/auth） | 用户管理页 createAdmin（绑固定角色）；老师账号可经用户管理页绑定/解绑 ADMIN 角色（system:user:bind_role，仅老师可绑，变更踢会话） | 受 sys_user_data_scope 约束，无行即零范围 |
| TEACHER | 2 | 授课老师 | 场地查询、单次/周期预约及本人记录等业务权限 | 管理员预建账号并绑定主身份角色 | 本人 + 课程名单计算，不可登记范围行 |
| STUDENT | 3 | 在校学生 | 场地查询、单次/周期预约及本人记录；另有本人被委派物料代领的只读权限，以及本人通知查询/已读权限 | 管理员预建账号并绑定主身份角色 | 仅本人数据、本人被委派数据及本人通知 |
| OPS_ADMIN | 5 | 最高级运维管理员 | 仅绑定 system 模块权限和 system% 菜单；系统管理（用户/名单/组织/角色/数据范围/参数配置）自 2026-10-20 起为其专属；不参与场地、预约、物料等业务流程 | 仅 SQL 预置（sql/sys_ops_role.sql），页面不可创建；其权限绑定锁定不可页面调整（40900），其余 3 个预置角色的权限绑定可由运维管理员整集替换（system:role:assign） | 不受范围约束（面向系统配置与权限管理；实现层豁免见 SysScopeServiceImpl.isOpsAdmin） |

层级关系：OPS_ADMIN 为最高级运维角色，普通 ADMIN 无法通过任何页面操作创建或提升账号为 OPS_ADMIN；2026-10-20 起系统管理功能整体归 OPS_ADMIN 专属，形成新权限层级（OPS_ADMIN 管系统、ADMIN 管业务），ADMIN 仅保留操作日志页（仅业务模块日志）。角色固定 ID 的历史空号不复用。

### 3.3 权限标识

规范 `模块:资源:操作`（如 `booking:lab:create`），登记在 sys_permission，前后端一致。模块编码与初始数据 ID 段：

| module_code | 模块 | ID 段 | 状态 |
| --- | --- | --- | --- |
| system | 身份、组织、权限、日志、策略 | 1–999 | 已出 SQL 并落地 |
| space | 场地台账、开放窗口、维修时段 | 1000–1999 | 当前保留 |
| booking | 场地单次/周期预约、使用台账 | 3000–3999 | 当前保留 |
| document | 共享单据、审批与事件 | 随公共支撑脚本登记 | 当前保留 |

### 3.4 菜单与动态路由

- sys_menu 三种类型：DIRECTORY（目录）/ PAGE（页面）/ ACTION（按钮，permission_id 必填）。
- `component_key` 大驼峰，只能命中前端 `router/dynamic.js` 的本地组件白名单；白名单外的 key 不注册路由，从根上防止路由注入。
- 角色经 sys_role_menu 绑定菜单；登录/刷新时 profile 下发菜单树，前端重建动态路由；注销或 401 移除动态路由并清理状态。
- 兜底规则：catch-all 为 redirect 写法（`/:pathMatch(.*)*` → /404），重定向先于守卫执行，守卫以「落 /404 且存在 redirectedFrom」判定未注册路径（原始目标取 `to.redirectedFrom.path`，不能用 `to.matched` 检测 catch-all）；目标未在新身份路由表注册时，已知页面（router/index.js 的 KNOWN_PAGE_PATHS）落 403，未知路径首载回 homePath、应用内展示 404，避免低权账号跟随旧 redirect 误落 404。

---

## 四、访问控制链路

### 4.1 一次请求的完整校验链

```
请求 → JwtAuthFilter 解析 access token（无效/缺失 40100）
     → AuthService.buildLoginUser 实时装配（活动角色 + 权限，无缓存）
     → @RequirePermission("模块:资源:操作")（40300）
     → Controller 参数校验（40000）
     → Service 数据范围校验（仅管理员写操作，40300）
     → 业务规则校验（40400/40900）
     → @OperationLog 落审计（独立事务，不影响主流程）
```

### 4.2 权限装配（AuthService.buildLoginUser）

- 持有角色全集来自 sys_user_role 的有效静态角色关系。
- 装配结果以 token 内活动角色 claim 为准：`roles` 与 `permissions` 按活动角色对应的 sys_role、sys_permission 实时装配，每请求从库加载。
- 活动角色默认取主身份角色（user_type 派生，未持有时兜底第一个持有角色），多角色用户可经 switch_role 切换（校验持有后重签双 token，claim 失效自动回退主身份）。
- 无会话缓存：角色/权限变更下一请求立即生效；无状态会话无法主动踢人，依赖 access 2 小时自然过期。

### 4.3 权限失效矩阵（JWT 无状态，2026-10-20 起）

| 变更场景 | 失效动作 | 实现位置 |
| --- | --- | --- |
| 用户被停用 | refresh 时拒绝轮换；access 最长 2 小时内自然失效 | AuthService.refresh 校验用户启用状态 |
| 数据范围增删 | 范围实时查询无缓存，下一请求即时生效 | SysScopeService 直查 sys_user_data_scope |
| 角色权限调整 | ✅ 已实现（OPS_ADMIN 整集替换，锁 OPS_ADMIN 角色）：每请求实时加载，下一请求即时生效 | JwtAuthFilter → buildLoginUser |
| 老师绑定/解绑管理员角色 | 持有角色实时加载；已签发 token 内活动角色若失效，装配时自动回退主身份 | AuthService.buildLoginUser |

### 4.4 前端镜像层

- 路由守卫：未登录跳登录页；登录后按 profile.menus 重建动态路由。
- `v-permission` 指令：无权限移除元素（如新建/撤销按钮）。
- Axios 拦截器统一处理返回体与 401/403/网络错误，页面不重复实现认证逻辑。
- 上述全部为体验层；绕过前端直接调接口仍被 4.1 链路拦截。

---

## 五、数据范围设计（sys_user_data_scope）

### 5.1 语义

| 规则 | 内容 |
| --- | --- |
| 适用对象 | 仅 ADMIN 类型用户；学生/老师范围由本人 + 课程名单计算，不可登记范围行 |
| scope_type | ALL / DEPARTMENT（一期）；CAMPUS / BUILDING / LAB 为预留类型，不开放 |
| access_mode | READ / WRITE；WRITE 蕴含 READ |
| 存储 | 唯一键 (user_id, scope_type, access_mode, scope_target_id)，scope_target_id 为生成列归一（ALL 行目标为 NULL 也可去重）；同目标 READ/WRITE 可各存一行，升降级=增删行 |
| 匹配 | ALL 行匹配任意请求；DEPARTMENT 精确匹配，不向下继承子部门 |
| 空表语义 | 无任何范围行 = 零范围，不自动授予全校数据 |

### 5.2 强制执行点

| 操作 | 校验 |
| --- | --- |
| 创建管理员 | 调用方须全校写（requireAllWrite），防止部门管理员制造同僚 |
| 用户编辑（部门变更） | 迁出部门与迁入部门双查写权限 |
| 用户停用/启用 | 目标部门写权限 |
| 名单新建/批量/编辑/启停 | 目标部门写权限 |
| 根部门创建 | 全校写；子部门创建/编辑/启停按部门写 |
| 范围授权（scope:grant） | 调用方须全校写，防止放大自身范围 |
| 范围删除 | 禁止删除本人最后一条全校写范围（40900，防自锁） |

### 5.3 OPS_ADMIN 豁免

OPS_ADMIN 仅绑定 system 模块权限，不登记、也不需要范围行：`SysScopeServiceImpl.isOpsAdmin` 在 requireAllWrite / requireDepartmentWrite 入口对 OPS_ADMIN 直接放行（实现层豁免，接口权限码仍由 @RequirePermission 独立控制）。该豁免只用于系统、人员和权限管理，不授予任何业务模块权限；space、booking、item 等流程接口仍会因缺少权限返回 403。

---

## 六、场地与预约权限边界

- 场地读接口按 `space:space:list / read` 校验；场地写操作按 `space:space:create / update / delete` 校验，并进一步检查管理员数据范围。
- 学生、老师通过 `booking:lab:create / list / cancel` 提交、查看和取消本人单次或周期预约；详情、取消、签到和签退必须再校验单据归属。
- ADMIN 通过 `booking:lab:approve` 执行唯一一级审批；批准时在事务内重验场地状态、维修窗口、开放窗口和占用冲突。
- 预约单据、审批和事件使用共享单据关联；业务状态由所属预约表维护，过程事件与操作日志只追加，不因驳回或取消删除历史。

### 6.1 物料助理与学生代领边界

- 老师通过 `item:assistant:manage` 管理本人学生助理授权，通过 `item:claim:delegate` 将本人原申领单委派、改派或撤回；授权和申领归属均由服务端强制校验。
- 学生仅通过 `item:pickup:list` 查询 `delegate_student_id` 为本人且未撤回的原申领，不生成 PROXY 子单，不获得申领创建、审核、实发、归还或库位管理权限。
- 学生另持有 `item:notification:list/read`，仅用于查询和已读 `recipient_user_id` 为本人的通知，接收老师委派代领消息；复用顶部通知铃铛，不新增业务菜单，不因此获得逾期列表或其他物料权限。
- 实际出库仍由持有 `item:claim:issue` 且属于实际库位的 ADMIN 执行，并在实发时复核授权有效期、撤销状态和学生启用状态；库存单据分别记录责任教师、实际领取人和经办管理员。
- 助理授权是 item 域内的业务关系，不是第五种角色，不恢复动态 `ASSISTANT`；撤销或过期后禁止新的实发，但不阻断归还和历史审计。OPS_ADMIN 不得获得上述任何权限或菜单。

---

## 七、认证与会话安全

| 项 | 规则 | 实现 |
| --- | --- | --- |
| 登录方式 | 全部用户类型统一账号密码（2026-10-20 起短信验证码下线） | AuthService |
| 口令传输 | 前端经 GET /auth/salt 取 BCrypt 盐前缀，bcryptjs 同盐复算哈希上传，服务端常量时间比对，明文不进入报文 | AuthService.salt/login |
| 密码 | 8-64 位含字母数字，由服务端在设置时校验；密码由管理员预置或重置，强制改密链路已下线 | 用户管理重置 |
| 锁定 | 连续 5 次密码错误锁 30 分钟（failed_login_count/locked_until），错误提示含剩余分钟 | AuthService |
| 会话时长 | JWT 双 token：access 2 小时 + refresh 7 天，全局统一；POST /auth/refresh 轮换双 token（用户停用即拒绝） | JwtService |
| 二级确认 | 敏感操作（审批、策略发布等）先 POST /auth/password/confirm，通过后 300 秒内敏感接口免再确认（SecurityUtils.checkSafe） | AuthService.confirmPassword |
| token 传输 | `Authorization: Bearer <access token>`，双 token 存 sessionStorage，401 先单飞刷新重试再清态跳登录 | Axios 拦截器 |
| 密码存储 | 仅 BCrypt 单向散列；admin/opsadmin 脚本为占位哈希，部署前必须替换 | sys_rbac.sql / sys_ops_role.sql |

无状态会话无首登建档事务：`sys_person_roster` 名单表保留用于身份核验预留，登录直接使用预置账号。

---

## 八、审计设计（sys_operation_log）

- 表：actor_id（外键 RESTRICT，防删用户断链）、actor_type、module_code、action_code、target_table、target_id、request_id、client_ip、before_value/after_value（JSON）、success、occurred_at（UTC）。
- 采集：`@OperationLog(moduleCode, actionCode, targetTable, targetId)` 注解 + AOP 切面，独立事务写入（业务回滚不丢审计）；targetId 支持 SpEL（如 `#id`）。
- 只追加：不更新不删除；密码、验证码、token 不记录。
- 查询：GET /api/v1/system/logs（system:log:list，ADMIN / OPS_ADMIN），按操作人/模块/动作/时间分页；ADMIN 仅可见业务模块日志。

---

## 九、预约限制（user_booking_restriction）

与角色权限正交的**资格冻结**机制：角色决定"能不能进预约功能"，限制决定"这段时间内禁不禁止下单"。

- 当前预约业务的 restriction_type 使用 LAB / ALL；状态由 revoked_at/start_at/end_at 与 UTC 当前比较动态得出（ACTIVE/PENDING/ENDED/REVOKED）。
- 手工行 source_document_id 为 NULL 允许多条；自动逾期冻结行按 (source_document_id, restriction_type) 唯一。
- 管理端页面与接口已于 2026-10-21 下线（system:restriction:* 权限与菜单同步移除）；表与 `UserBookingRestrictionMapper.countActive` 保留，预约模块下单时调 countActive 拦截，逾期冻结由单据模块写入。

---

## 十、初始数据与 ID 约定

| 数据 | 固定 ID | 脚本 |
| --- | --- | --- |
| 部门「设备处」 | 1 | sql/sys_rbac.sql |
| 角色 ADMIN/TEACHER/STUDENT | 1-3 | sql/sys_rbac.sql |
| 角色 OPS_ADMIN | 5 | sql/sys_ops_role.sql |
| system 权限 | 1-31 段内按现行有效项登记，废弃编号不复用 | sql/sys_rbac.sql |
| system 菜单 | 1-11 段内按现行有效项登记，废弃编号不复用 | sql/sys_rbac.sql |
| 管理员 admin | 1 | sql/sys_rbac.sql |
| 运维管理员 opsadmin | 2 | sql/sys_ops_role.sql |
| admin 全校写范围 | 1 | sql/sys_scope.sql |
| 安全策略参数 9 项 | business_policy | sql/sys_policy.sql |

初始数据使用固定小 ID（system 段 1–999），与雪花 ID 不冲突；各业务模块按 ID 段随建表脚本登记本模块的权限/菜单/角色绑定。

---

## 十一、数据库表清单

RBAC 核心 11 表（sql/sys_rbac.sql）：sys_department、sys_person_roster、sys_user、sys_role、sys_permission、sys_menu、sys_role_permission、sys_role_menu、sys_user_role、sys_user_data_scope、user_booking_restriction。

公共支撑表包括 sys_operation_log、business_policy；业务表仅保留场地、单次/周期预约、节次与占用、使用记录，以及 biz_document、approval_task、document_event 等共享单据表。

关键约束：关系表组合唯一（不靠 `(业务键, deleted)` 解决重复创建）；可空目标去重用生成列归一；外键默认 RESTRICT；审计字段 create_user_id/update_user_id 不建外键防循环依赖。

---

## 十二、扩展规则（新模块如何接入）

1. 在[权限清单.md](权限清单.md)功能矩阵确认功能归属角色（PRD 层）。
2. 在[RBAC实施清单.md](RBAC实施清单.md)登记 `模块:资源:操作` 权限码与菜单（含 component_key 白名单名）。
3. 模块建表脚本内登记本模块 sys_permission/sys_menu/sys_role_permission/sys_role_menu 初始数据，使用本模块 ID 段。
4. 后端 Controller 加 `@RequirePermission`，写操作涉及部门归属的注入 SysScopeService 校验。
5. 敏感操作（审批、处置）加二级确认；关键变更加 `@OperationLog` 埋点。
6. 前端在 `router/dynamic.js` 登记组件白名单，页面按钮用 `v-permission`。
7. 新增预置角色：sys_role 固定小 ID + RoleCode 枚举 + 绑定脚本 + 同步 AGENTS.md 与数据库设计.md（OPS_ADMIN 为范例）；一期仍不开放页面自定义角色。

---

## 十三、实现现状

| 能力 | 状态 |
| --- | --- |
| 认证链路（取盐/登录/刷新/profile/二级确认/注销，JWT 双 token） | ✅ 已落地并验证（2026-10-20 登录重写，Sa-Token 下线；2026-10-21 整合一人多角色） |
| 锁定限流、静态持有角色与活动角色装配 | ✅ 已落地并验证 |
| 用户/名单（含 CSV 导出与批量导入）/组织/角色只读/数据范围/操作日志/参数配置 | ✅ 已落地并验证；2026-10-20 起系统管理页面整体归 OPS_ADMIN 专属，ADMIN 仅保留操作日志页；2026-10-21 用户与名单合并为统一人员视图（菜单 system_user「人员管理」→ SystemUserRoster，名单与账号一行一人，列表走 GET /system/persons 需 system:user:list + system:roster:list 双权限，旧地址 /system/roster 静态重定向）；预约限制页面与接口 2026-10-21 下线（表与 countActive 保留供预约模块逾期冻结） |
| OPS_ADMIN 运维角色 | ✅ 已落地并验证；仅绑定 system 模块权限和 system% 菜单，不参与业务流程；角色权限整集替换锁定 OPS_ADMIN 自身，其余 3 个预置角色可由 OPS_ADMIN 调整 |
| 一人多角色与活动角色切换 | ✅ 已落地并验证（2026-10-20）：老师可绑定 ADMIN，默认主身份登录；2026-10-21 起 switch_role 校验持有后重签双 token（JWT 融合版） |
| 敏感端点二级认证强制执行（checkSafe + 40101 + 前端确认弹窗重试） | ✅ 已落地并验证（策略发布等敏感端点接入） |
| 场地模块（单表场地、开放窗口、维修时段） | ✅ 当前保留；场地主档状态与预约占用分离 |
| 场地预约（单次/周期、12 节次、跳选拆分、一级审批、签到签退/取消） | ✅ 当前保留；提交与批准统一校验维修和占用冲突 |
| 共享单据与操作日志 | ✅ 当前保留；用于业务过程关联和只追加审计 |
| 自定义角色、字段级权限、SSO/人脸识别 | ⬜ 一期不做（二期预留 SSO） |

---

## 十四、已知边界与限制

1. 运维账号保护已实现：目标用户持有 OPS_ADMIN 角色时，编辑/启停/重置仅 OPS_ADMIN 本人可操作（SysUserServiceImpl.requireOpsAdminOperator，普通管理员调用返回 40300），普通 ADMIN 无法接管 opsadmin 账号；OPS_ADMIN 对普通账号的编辑/启停/重置不受此限（停用/重置本人仍被 40900 拦截）。
2. 无状态会话的生效延迟：角色/权限每请求实时装配，但已签发 access token 内的活动角色不会随客户端主观指定而变化；失效活动角色回退主身份，其他切换依赖 refresh/重登或 switch_role 重签。
3. 短信验证码链路已随登录重写下线（2026-10-20）；哈希即凭据可被重放的风险由内网/开发场景接受，传输层安全以 HTTPS 为准。
4. 数据范围类型仅 ALL/DEPARTMENT 开放（写操作按所属部门校验）；CAMPUS/BUILDING/LAB 为预留类型，不开放。部门精确匹配不继承。
5. 跨归口只读（显式 READ 行）规则已定，READ 行的页面化发放随业务模块可见性需求接入。

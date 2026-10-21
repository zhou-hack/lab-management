# RBAC 权限管理总设计方案

> 定位：权限体系的总体设计文档，向下衔接三份既有文档——[权限清单.md](权限清单.md)（PRD 功能矩阵）、[数据库设计.md](数据库设计.md) 4.1/4.6/4.9 节（表结构评审稿 v1.0）、[RBAC实施清单.md](RBAC实施清单.md)（权限/菜单/认证契约登记）。冲突时以数据库设计.md 为准，登记类细节以 RBAC实施清单.md 为准。
> 版本：v1.0；整理日期：2026-10-15；状态：system 模块已落地并通过应用级验证；space 模块于 2026-10-20 整体下线；业务模块（asset/booking/item/maintain/report）随实施分组 B→E 补充（原依赖 space 表的设计待重新评审）。

---

## 一、设计目标与原则

### 1.1 目标

1. 三类用户（学生/老师/管理员）+ 五个预置角色的访问控制，覆盖功能权限（能点什么）与数据权限（能看/改哪些行）两个维度。
2. 所有授权决策在服务端完成，前端隐藏入口仅为体验优化，不作安全依据。
3. 权限变更可审计：授权、撤销、范围调整、账号启停全部落操作日志，只追加不修改。
4. 一期不开放自定义角色、不做字段级权限，保持模型简单可验证。

### 1.2 原则

| 原则 | 含义 | 落地体现 |
| --- | --- | --- |
| 服务端强制 | 每个受保护接口由后端校验权限，前端指令/菜单只是镜像 | `@RequirePermission` + 数据范围 Service 校验 |
| 最小权限 | 无范围记录不自动授予全校数据；无授权不获得助理身份 | sys_user_data_scope 空表即零范围；ASSISTANT 动态计算 |
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
│  授权层  AssistantGrantService：助理授权生命周期，动态派生 ASSISTANT   │
│  审计层  @OperationLog AOP：独立事务写 sys_operation_log            │
└──────────────┬─────────────────────────────────────────────────┘
               │
┌──────────────▼─────────────────────────────────────────────────┐
│ MySQL（UTC 会话）                                                │
│  RBAC 11 表 + assistant_grant 2 表 + 业务策略/日志/限制表            │
└────────────────────────────────────────────────────────────────┘
```

三层含义：**身份**（你是谁，名单预录派生）、**角色**（你能干什么，权限集合）、**范围**（你能对哪些数据动手，显式授权行）。

---

## 三、核心概念模型

### 3.1 用户与角色分离

- **用户类型（sys_user.user_type）**：STUDENT / TEACHER / ADMIN（OPS_ADMIN 账号 user_type 为 OPS_ADMIN）。登录方式统一账密（2026-10-20 起，短信验证码登录下线）；JWT 双 token：access 2 小时 + refresh 7 天。
- **角色（sys_role.role_code）**：权限集合。主身份角色由 user_type 派生；老师账号可经用户管理页叠加绑定 ADMIN 角色（一人多角色，2026-10-20 起），ASSISTANT 是唯一可动态授予/回收的附加角色。
- **活动角色（token 携带的生效身份视图）**：多角色用户登录后默认以主身份角色为活动角色，权限/菜单按活动角色装配；可经 POST /api/v1/auth/switch_role 在持有角色间切换（校验持有后重签双 token），前端右上角账户菜单提供切换入口。
- 用户类型与角色正交但受控：角色只决定权限集合，user_type 决定主身份与默认活动角色。

### 3.2 五个预置角色

| 角色 | id | 定位 | 权限来源 | 创建方式 | 数据范围 |
| --- | --- | --- | --- | --- | --- |
| ADMIN | 1 | 设备处业务管理员 | 静态绑定 1 项 system 权限（20 操作日志；2026-10-20 起系统管理整体划归 OPS_ADMIN，ADMIN 仅保留操作日志页且仅可见业务模块日志，module_code 不含 system/auth） | 用户管理页 createAdmin（绑固定角色）；老师账号可经用户管理页绑定/解绑 ADMIN 角色（system:user:bind_role，仅老师可绑，变更踢会话） | 受 sys_user_data_scope 约束，无行即零范围 |
| TEACHER | 2 | 授课老师 | 静态绑定助理授权 3 项（23-25）+ 业务模块增量 | 名单首登自动建档派生 | 本人 + 课程名单计算，不可登记范围行 |
| STUDENT | 3 | 在校学生 | 业务模块增量 | 名单首登自动建档派生 | 仅本人数据 |
| ASSISTANT | 4 | 老师助理（附加角色） | 不持久化，由有效 assistant_grant 动态计算 | 老师授权产生，到期/撤销自动还原 | 授权范围（OFFERING/LAB + ISSUE/RETURN） |
| OPS_ADMIN | 5 | 最高级运维管理员 | 静态绑定全部权限（当前 28 项：system 模块；后续模块新增权限随各自脚本同步绑定）；系统管理（用户/名单/组织/角色/数据范围/参数配置/授权审计）自 2026-10-20 起为其专属 | 仅 SQL 预置（sql/sys_ops_role.sql），页面不可创建；其权限绑定锁定不可页面调整（40900），其余 4 个预置角色的权限绑定可由运维管理员整集替换（system:role:assign） | 不受范围约束（面向全系统数据与配置；实现层豁免见 SysScopeServiceImpl.isOpsAdmin） |

层级关系：OPS_ADMIN 为最高级运维角色，普通 ADMIN 无法通过任何页面操作创建或提升账号为 OPS_ADMIN；2026-10-20 起系统管理功能整体归 OPS_ADMIN 专属，形成新权限层级（OPS_ADMIN 管系统、ADMIN 管业务），ADMIN 仅保留操作日志页（仅业务模块日志）；ASSISTANT 附加在 STUDENT 之上，授权期内同时持有 STUDENT + ASSISTANT。

### 3.3 权限标识

规范 `模块:资源:操作`（如 `item:material:create`），登记在 sys_permission，前后端一致。模块编码与初始数据 ID 段：

| module_code | 模块 | ID 段 | 状态 |
| --- | --- | --- | --- |
| system | 身份、组织、权限、日志、策略 | 1–999 | 已出 SQL 并落地 |
| asset | 器材台账、工位 | 2000–2999 | 分组 B |
| booking | 场地预约、器材借用、使用台账 | 3000–3999 | 分组 C |
| item | 物料、配件库存 | 4000–4999 | 分组 D |
| maintain | 损坏、保养、报废 | 5000–5999 | 分组 E |
| report | 看板与报表 | 6000–6999 | 分组 E |

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

- 持有角色全集 = sys_user_role 静态角色 + 动态 ASSISTANT（assistant_grant 当前窗口存在有效行即追加）。
- 装配结果以 token 内活动角色 claim 为准：`roles` = 活动角色 + 动态 ASSISTANT；`permissions` = 活动角色（+ASSISTANT）对应的 sys_permission 集合，每请求实时从库加载。
- 活动角色默认取主身份角色（user_type 派生，未持有时兜底第一个持有的非 ASSISTANT 角色），多角色用户可经 switch_role 切换（校验持有后重签双 token，claim 失效自动回退主身份）。
- 无会话缓存：角色/权限变更下一请求立即生效；无状态会话无法主动踢人，依赖 access 2 小时自然过期。

### 4.3 权限失效矩阵（JWT 无状态，2026-10-20 起）

| 变更场景 | 失效动作 | 实现位置 |
| --- | --- | --- |
| 用户被停用 | refresh 时拒绝轮换；access 最长 2 小时内自然失效 | AuthService.refresh 校验用户启用状态 |
| 助理授权创建 | 下一请求实时装配自动追加 ASSISTANT，即时生效 | JwtAuthFilter → buildLoginUser |
| 助理授权撤销 | 下一请求实时装配自动移除 ASSISTANT，即时生效 | JwtAuthFilter → buildLoginUser |
| 数据范围增删 | 范围实时查询无缓存，下一请求即时生效 | SysScopeService 直查 sys_user_data_scope |
| 角色权限调整 | ✅ 已实现（OPS_ADMIN 整集替换，锁 OPS_ADMIN 角色）：每请求实时加载，下一请求即时生效（含 ASSISTANT 动态持有者） | JwtAuthFilter → buildLoginUser |
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
| 适用对象 | 仅 ADMIN 类型用户；学生/老师范围由本人+课程名单+有效助理授权计算，不可登记范围行 |
| scope_type | ALL / DEPARTMENT（一期）；CAMPUS / BUILDING / LAB 为预留类型（空间功能已下线，不开放） |
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

OPS_ADMIN 绑定全部权限但不登记、也不需要范围行：`SysScopeServiceImpl.isOpsAdmin` 在 requireAllWrite / requireDepartmentWrite 入口对 OPS_ADMIN 直接放行（实现层豁免，接口权限码仍由 @RequirePermission 独立控制）。无此豁免时，其全量写权限将因"空表=零范围"全部落空；豁免后 OPS_ADMIN 成为真正面向全系统的兜底角色。

---

## 六、助理授权设计（assistant_grant / assistant_grant_scope）

### 6.1 动态角色模型

- ASSISTANT 不持久化到 sys_user_role：存在至少一条 `status=ACTIVE 且 start_at<=now<end_at 且未删除` 的授权行，装配时即附加 ASSISTANT；授权到期/撤销/逻辑删除自动还原为纯学生。
- 同一学生可被多位老师授权；一笔代办动作必须明确选用一条授权。
- 老师、管理员用户不可被授权（创建时校验目标为启用状态的学生账号）。

### 6.2 授权约束

| 规则 | 值 |
| --- | --- |
| 时长上限 | 单笔最长 366 天（一学年） |
| 时间校验 | end_at > start_at 且 end_at > 当前时间（UTC 毫秒截断） |
| 授权权力 | 创建时至少选择一项（当前仅物料领取 MATERIAL_ISSUE），未知类型 40000 |
| 撤销 | 仅授权本人可撤，仅 ACTIVE 可撤；重复撤销 40900，他人撤销 40300 |
| 审计 | 创建/撤销写 sys_operation_log；撤销人/撤销时间落行 |
| 缓存失效 | 创建与撤销均踢学生会话（见 4.3） |

### 6.3 范围表（授权权力）

assistant_grant_scope 表达授权权力，一单可含多种：

- **MATERIAL（已开放，2026-10-20 起）**：物料领取（领还一体），无目标，action_code 固定 ISSUE_RETURN；创建授权时勾选，列表/详情按行聚合展示。
- **OFFERING / LAB（预留）**：课程/实验室级领还（恰一目标非空 + ISSUE/RETURN），随教学模块落地开放；LAB 目标随空间功能下线暂不可用。

同一授权同一类型同一目标同一动作唯一（生成列 scope_target_id 归一去重）；CHECK 保证 MATERIAL 行无目标且与 ISSUE_RETURN 配对。代办接口随物料模块落地后逐次校验「助理身份有效 + 授权含对应权力 + 范围覆盖目标」。

### 6.4 授权溯源

代办流水同时记录经办人（助理学号）与授权人（老师工号）；授权撤销或到期立即阻止新的代办，已出库未归还项保留由老师/管理员跟进，不删流水。

---

## 七、认证与会话安全

| 项 | 规则 | 实现 |
| --- | --- | --- |
| 登录方式 | 全部用户类型统一账号密码（2026-10-20 起短信验证码下线） | AuthService |
| 口令传输 | 前端经 GET /auth/salt 取 BCrypt 盐前缀，bcryptjs 同盐复算哈希上传，服务端常量时间比对，明文不进入报文 | AuthService.salt/login |
| 密码 | 8-64 位含字母数字，由服务端在设置时校验；密码由管理员预置或重置，强制改密链路已下线 | 用户管理重置 |
| 锁定 | 连续 5 次密码错误锁 30 分钟（failed_login_count/locked_until），错误提示含剩余分钟 | AuthService |
| 会话时长 | JWT 双 token：access 2 小时 + refresh 7 天，全局统一；POST /auth/refresh 轮换双 token（用户停用即拒绝） | JwtService |
| 二级确认 | 敏感操作（审批、报废处置等）先 POST /auth/password/confirm，通过后 300 秒内敏感接口免再确认（SecurityUtils.checkSafe） | AuthService.confirmPassword |
| token 传输 | `Authorization: Bearer <access token>`，双 token 存 sessionStorage，401 先单飞刷新重试再清态跳登录 | Axios 拦截器 |
| 密码存储 | 仅 BCrypt 单向散列；admin/opsadmin 脚本为占位哈希，部署前必须替换 | sys_rbac.sql / sys_ops_role.sql |

无状态会话无首登建档事务：`sys_person_roster` 名单表保留用于身份核验预留，登录直接使用预置账号。

---

## 八、审计设计（sys_operation_log）

- 表：actor_id（外键 RESTRICT，防删用户断链）、actor_type、module_code、action_code、target_table、target_id、request_id、client_ip、before_value/after_value（JSON）、success、occurred_at（UTC）。
- 采集：`@OperationLog(moduleCode, actionCode, targetTable, targetId)` 注解 + AOP 切面，独立事务写入（业务回滚不丢审计）；targetId 支持 SpEL（如 `#id`）。
- 只追加：不更新不删除；密码、验证码、token 不记录。
- 查询：GET /api/v1/system/logs（system:log:list，ADMIN / OPS_ADMIN），按操作人/模块/动作/时间分页。

---

## 九、预约限制（user_booking_restriction）

与角色权限正交的**资格冻结**机制：角色决定"能不能进预约功能"，限制决定"这段时间内禁不禁止下单"。

- restriction_type：EQUIPMENT / LAB / ALL；状态由 revoked_at/start_at/end_at 与 UTC 当前比较动态得出（ACTIVE/PENDING/ENDED/REVOKED）。
- 手工行 source_document_id 为 NULL 允许多条；自动逾期冻结行按 (source_document_id, restriction_type) 唯一。
- 管理端：GET/POST /api/v1/system/restrictions、PUT /{id}/revoke（system:restriction:*，ADMIN / OPS_ADMIN）——**页面与接口已于 2026-10-21 下线**（权限与菜单同步移除）；表与 `UserBookingRestrictionMapper.countActive` 保留，预约模块下单时调 countActive 拦截，逾期冻结由单据模块写入。

---

## 十、初始数据与 ID 约定

| 数据 | 固定 ID | 脚本 |
| --- | --- | --- |
| 部门「设备处」 | 1 | sql/sys_rbac.sql |
| 角色 ADMIN/TEACHER/STUDENT/ASSISTANT | 1-4 | sql/sys_rbac.sql |
| 角色 OPS_ADMIN | 5 | sql/sys_ops_role.sql |
| system 权限 31 项 | 1-31 | sql/sys_rbac.sql（26=授权审计、27=重置密码、28=调整角色权限、29-30=名单导出/导入、31=绑定管理员角色） |
| system 菜单 11 项 | 1-11 | sql/sys_rbac.sql |
| 管理员 admin | 1 | sql/sys_rbac.sql |
| 运维管理员 opsadmin | 2 | sql/sys_ops_role.sql |
| admin 全校写范围 | 1 | sql/sys_scope.sql |
| 安全策略参数 9 项 | business_policy | sql/sys_policy.sql |

初始数据使用固定小 ID（system 段 1–999），与雪花 ID 不冲突；各业务模块按 ID 段随建表脚本登记本模块的权限/菜单/角色绑定。

---

## 十一、数据库表清单

RBAC 核心 11 表（sql/sys_rbac.sql）：sys_department、sys_person_roster、sys_user、sys_role、sys_permission、sys_menu、sys_role_permission、sys_role_menu、sys_user_role、sys_user_data_scope、user_booking_restriction。

扩展表：assistant_grant、assistant_grant_scope（sql/assistant_grant.sql）；sys_operation_log、business_policy（公共支撑，sql/sys_policy.sql 等）。

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
| 锁定限流、动态 ASSISTANT 装配 | ✅ 已落地并验证 |
| 用户/名单（含 CSV 导出与批量导入）/组织/角色只读/数据范围/操作日志/参数配置 | ✅ 已落地并验证；2026-10-20 起系统管理页面整体归 OPS_ADMIN 专属，ADMIN 仅保留操作日志页；2026-10-21 用户与名单合并为统一人员视图（菜单 system_user「人员管理」→ SystemUserRoster，名单与账号一行一人，列表走 GET /system/persons 需 system:user:list + system:roster:list 双权限，旧地址 /system/roster 静态重定向）；预约限制页面与接口 2026-10-21 下线（表与 countActive 保留供预约模块逾期冻结） |
| 助理授权（创建/撤销/审计 + 权限缓存失效闭环） | ✅ 已落地并验证（范围表已建，业务接入待分组 B→E） |
| OPS_ADMIN 运维角色 | ✅ 已落地并验证；2026-10-15 由运维子集调整为全部权限；2026-10-17 新增角色权限整集替换（system:role:assign，锁 OPS_ADMIN 角色）；2026-10-20 空间功能下线后回至 30 项，同日新增 system:user:bind_role 后为 31 项；2026-10-20 系统管理整体划归 OPS_ADMIN 专属（ADMIN 剥离至仅操作日志 20，且日志仅可见业务模块）；2026-10-21 预约限制 3 项权限随页面接口下线后为 28 项 |
| 一人多角色与活动角色切换 | ✅ 已落地并验证（2026-10-20）：老师可绑定 ADMIN，默认主身份登录；2026-10-21 起 switch_role 校验持有后重签双 token（JWT 融合版） |
| 敏感端点二级认证强制执行（checkSafe + 40101 + 前端确认弹窗重试） | ✅ 已落地并验证（策略发布等敏感端点接入） |
| 空间模块（校区/楼栋/实验室/开放规则） | ❌ 已于 2026-10-20 整体下线（模块代码、页面、权限 1000–1011、菜单 1000–1002、四表全部移除；CAMPUS/BUILDING/LAB 范围类型回退为预留） |
| course/schedule 教学基础与 asset/booking/item/maintain/report 权限登记 | ⬜ 随分组 B→E（原依赖 space 表的设计待重新评审） |
| 助理授权范围（OFFERING/LAB + ISSUE/RETURN）业务接入 | ⬜ OFFERING 依赖 course_offering；LAB 目标随空间功能下线暂不可用，领用/归还接入随分组 D 重新评审 |
| 自定义角色、字段级权限、SSO/人脸识别 | ⬜ 一期不做（二期预留 SSO） |

---

## 十四、已知边界与限制

1. 运维账号保护已实现：目标用户持有 OPS_ADMIN 角色时，编辑/启停/重置仅 OPS_ADMIN 本人可操作（SysUserServiceImpl.requireOpsAdminOperator，普通管理员调用返回 40300），普通 ADMIN 无法接管 opsadmin 账号；OPS_ADMIN 对普通账号的编辑/启停/重置不受此限（停用/重置本人仍被 40900 拦截）。
2. 无状态会话的生效延迟：助理授权在未来时刻生效的，学生到点后下一请求即自动获得助理身份（每请求实时装配）；但已签发 access token 内的活动角色不会变化，依赖 refresh/重登更新（最长 2 小时）。
3. 短信验证码链路已随登录重写下线（2026-10-20）；哈希即凭据可被重放的风险由内网/开发场景接受，传输层安全以 HTTPS 为准。
4. 数据范围类型仅 ALL/DEPARTMENT 开放（写操作按所属部门校验）；CAMPUS/BUILDING/LAB 为预留类型，已随 2026-10-20 空间功能下线不再开放。部门精确匹配不继承。
5. 跨归口只读（显式 READ 行）规则已定，READ 行的页面化发放随业务模块可见性需求接入。

# RBAC 实施清单（权限标识 / 菜单 / 认证契约）

> 定位：实施层登记文档，衔接 [权限清单.md](权限清单.md)（PRD 功能矩阵）与各模块建表脚本。
> 权限标识、菜单与初始数据以本文为准登记；功能范围变化时先改权限清单，再同步本文。
> 版本：草案 v0.1（待评审）；整理日期：2026-10-14。

---

## 一、模块编码与初始数据 ID 段

权限标识规范 `模块:资源:操作`，模块编码如下。初始数据使用固定小 ID（与雪花 ID 不冲突），按模块分段：

| module_code | 模块 | 设计依据（数据库设计） | 初始数据 ID 段 |
| --- | --- | --- | --- |
| system | 身份、组织、权限、日志、策略 | 4.1 / 4.6 / 4.9 | 1–999 |
| asset | 器材台账、工位 | 4.3 | 2000–2999 |
| booking | 场地预约、器材借用、使用台账 | 4.4 / 4.5 | 3000–3999 |
| item | 物料、配件库存 | 4.7 | 4000–4999 |
| maintain | 损坏、保养、报废 | 4.8 | 5000–5999 |
| report | 看板与报表 | 8 | 6000–6999 |

> space 模块（校区/楼栋/实验室/课程排课，原 1000–1999 段）已于 2026-10-20 整体下线移除；段号留废不再使用，后续模块勿复用。

各模块的权限/菜单/角色绑定初始数据随本模块建表脚本登记；system 模块初始数据已在 [sql/sys_rbac.sql](../sql/sys_rbac.sql)。

---

## 二、权限标识登记

「归属角色」为初始绑定；ASSISTANT 为附加角色，由有效 assistant_grant 动态计算；授权权力落 assistant_grant_scope（当前开放 MATERIAL_ISSUE 物料领取，OFFERING/LAB 预留）。
OPS_ADMIN（运维管理员）为最高级预置运维角色，仅 SQL 预置不开放页面创建，绑定全部已登记权限（后续模块新增权限随各自脚本同步绑定 OPS_ADMIN），不受数据范围约束。
2026-10-20 起系统管理（用户/名单/组织/角色/数据范围/预约限制/参数配置/授权审计）整体划归 OPS_ADMIN 专属，ADMIN 仅保留 system:log:list 且仅可见业务模块日志（module_code 不含 system/auth）。2026-10-21 预约限制页面与接口下线（system:restriction:* 权限与菜单移除），user_booking_restriction 表与 UserBookingRestrictionMapper.countActive 保留供预约模块逾期冻结使用。

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
| system:grant:list / create / revoke | 助理授权（老师本人） | TEACHER / OPS_ADMIN |
| system:grant:audit | 助理授权全局审计 | 仅 OPS_ADMIN |

### 2.2 space 模块（已于 2026-10-20 整体下线）

space:campus/building/lab 共 12 项权限（ID 1000–1011）及其角色绑定、菜单 1000–1002 已随功能下线全部移除；sql/space.sql 已删除，段号留废。

### 2.3 asset 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| asset:equipment:list / get | 器材目录与详情（学生/老师仅在架可约） | STUDENT / TEACHER / ADMIN |
| asset:equipment:create / update | 器材台账维护 | ADMIN |
| asset:equipment:status | 器材状态变更（IDLE↔MAINTAINING↔SCRAPPED） | ADMIN |
| asset:workstation:list | 工位台账 | TEACHER / ADMIN |
| asset:workstation:create / update | 工位维护 | ADMIN |
| asset:workstation:bind | 工位学号绑定 | TEACHER |
| asset:workstation:report | 课中故障上报 | TEACHER |
| asset:workstation:handle | 故障处置（恢复/转维修） | ADMIN |

### 2.4 booking 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| booking:equipment:create / cancel | 器材借用申请 / 撤销 | STUDENT / TEACHER |
| booking:equipment:list | 借用记录（数据范围控制可见性） | STUDENT / TEACHER / ADMIN |
| booking:equipment:approve | 借用审批（老师，含被催办单） | TEACHER |
| booking:equipment:audit | 贵重器材审批 / 超时代批 | ADMIN |
| booking:equipment:checkout / return | 确认借出 / 归还（强制外观照） | STUDENT / TEACHER / ADMIN（代借代还） |
| booking:equipment:urge | 超期催办 | ADMIN |
| booking:lab:create / cancel | 场地预约申请 / 撤销 | STUDENT / TEACHER |
| booking:lab:list | 场地预约记录 | STUDENT / TEACHER / ADMIN |
| booking:lab:endorse | 学生预约单背书 | TEACHER |
| booking:lab:approve | 场地审批 / 冲突仲裁 | ADMIN |
| booking:lab:finish | 强制结束 | ADMIN |
| booking:usage:list | 场地使用台账 | TEACHER（本人）/ ADMIN（全部） |

### 2.5 item 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| item:material:list | 物料库存查看（学生只读实时可领） | STUDENT / TEACHER / ADMIN |
| item:material:create / update | 物料台账 | ADMIN |
| item:material:inbound | 采购入库 | ADMIN |
| item:material:request | 物料申领 | TEACHER |
| item:material:sign | 定额签批 | TEACHER |
| item:material:issue / return | 领用出库 / 回库登记 | TEACHER / ADMIN，ASSISTANT 按授权增量 |
| item:material:count | 盘点 | ADMIN，ASSISTANT 按授权增量 |
| item:material:report | 库存月报与预警 | ADMIN |

### 2.6 maintain 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| maintain:damage:list / handle | 损坏异常处理（登记/恢复/转报废） | ADMIN |
| maintain:plan:list / create / execute | 保养计划与执行登记 | ADMIN |
| maintain:scrap:list / create / approve / dispose | 报废鉴定审批与处置 | ADMIN |

### 2.7 report 模块

| permission_code | 名称 | 归属角色 |
| --- | --- | --- |
| report:board:view | 看板（器材/场地/物料/保养报废） | ADMIN |

> 备注：本人站内消息、通知列表为登录即可访问，不设独立权限码；看板如需分张控制，再拆 `report:board:equipment` 等。

---

## 三、菜单登记（system 模块）

菜单类型 DIRECTORY/PAGE/ACTION；`component_key` 大驼峰，只能对应 `frontend/src/views` 内组件白名单。ACTION 类按钮菜单按需随页面登记（permission_id 必填）。学生/老师端业务页面菜单随各模块脚本登记。

| menu_code | 名称 | route_path | component_key | 绑定权限 | 归属角色 |
| --- | --- | --- | --- | --- | --- |
| system（目录） | 系统管理 | /system | — | — | ADMIN / OPS_ADMIN（ADMIN 仅见其下操作日志页） |
| system_user | 人员管理 | /system/user | SystemUserRoster | system:user:list | 仅 OPS_ADMIN（名单管理 2026-10-21 并入为统一人员视图：名单与账号一行一人，列表数据走 GET /system/persons（需 system:user:list + system:roster:list 双权限）；名单操作接口仍由 system:roster:* 保护，旧地址 /system/roster 静态重定向至本页） |
| system_department | 组织管理 | /system/department | SystemDepartment | system:department:list | 仅 OPS_ADMIN |
| system_role | 角色权限 | /system/role | SystemRole | system:role:list | 仅 OPS_ADMIN |
| system_scope | 数据范围 | /system/scope | SystemScope | system:scope:list | 仅 OPS_ADMIN |
| system_log | 操作日志 | /system/log | SystemLog | system:log:list | ADMIN / OPS_ADMIN（ADMIN 仅业务模块日志） |
| system_policy | 参数配置 | /system/policy | SystemPolicy | system:policy:list | 仅 OPS_ADMIN |
| system_grant_audit | 助理授权审计 | /system/grant | SystemGrantAudit | system:grant:audit | 仅 OPS_ADMIN |
| grant_manage | 助理管理（老师端） | /grant | GrantManage | system:grant:list | TEACHER |

### 3.1 space 模块（已于 2026-10-20 整体下线）

space 目录及 space_campus / space_lab 两个页面菜单（ID 1000–1002）已随功能下线全部移除。

---

## 四、认证接口契约

前缀 `/api/v1`；access token 经 `Authorization: Bearer <token>` 传递，前端双 token 存 sessionStorage。除标记「公开」的接口外均需登录。

**口令传输（2026-10-20 起，替代 RSA 公钥方案）**：登录、敏感操作确认的口令字段一律 BCrypt 哈希上传。前端先经 `GET /auth/salt` 按账号取该账号盐前缀（库存哈希前 29 位），用 bcryptjs 以同一盐复算出 60 位哈希后提交，服务端与库中哈希做常量时间比对，明文不进入报文；新密码强度（8-64 位含字母数字）在设置时由服务端校验。哈希即凭据可被重放，内网/开发场景接受该风险；传输层安全仍以 HTTPS 为准。

| 接口 | 方法/路径 | 说明 | 关键入参 | 成功 data | 主要错误 |
| --- | --- | --- | --- | --- | --- |
| 口令盐（公开） | GET /auth/salt | 登录/确认前按账号取 BCrypt 盐前缀；账号不存在/未设密码返回统一 40100 防账号枚举 | accountName | salt | 40100 账号或密码错误 |
| 账号密码登录（公开） | POST /auth/login | 全部用户类型统一账密登录 | accountName, password（哈希） | accessToken, refreshToken, expiresIn, userId, realName, userType, accountName, roles, activeRole | 40100 凭据错误/账号锁定（msg 含剩余次数或分钟）；40000 参数校验 |
| 刷新令牌（公开） | POST /auth/refresh | 轮换 access + refresh 双 token，保留活动角色 | refreshToken | 同登录 | 40100 refresh 无效或过期 |
| 当前身份 | GET /auth/profile | 登录后/刷新时加载 | — | userId, realName, userType, accountName, roles, permissions, menus, activeRole, switchableRoles | 40100 未登录 |
| 切换活动角色 | POST /auth/switch_role | 多角色用户校验持有后重签双 token，重新装配权限/菜单 | roleCode | 同 profile（新活动角色全量装配）+ 新双 token | 40300 未持有该角色/目标为 ASSISTANT |
| 敏感操作确认 | POST /auth/password/confirm | 审批、报废处置等二次确认，通过后 300 秒内敏感接口免再确认 | password（哈希） | validSeconds（300） | 40100 密码错误 |
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
| 4 | assistant_grant 两表 SQL 归属 | 单独脚本 [sql/assistant_grant.sql](../sql/assistant_grant.sql)，随 roles 模块先行落地 | 已建表 |
| 5 | admin 初始密码 | 脚本保持占位；dev 库已替换为开发用散列，生产部署前必须替换并强制首登修改 | 部署 checklist |

> roles 模块认证链路已实现并通过应用级验证（2026-10-14）：登录/验证码/首登建档/profile/改密/二级确认/注销、动态 ASSISTANT 计算、锁定与限流。ASSISTANT 增量权限随物料模块登记后生效。
>
> 2026-10-20 登录重写：Sa-Token 整体下线，改 JWT 无状态双 token；口令传输由 RSA 公钥改为 BCrypt 盐预取 + 前端复算哈希；短信验证码、强制改密、名单首登建档链路删除；`@SaCheckPermission` 全面替换为 `@RequirePermission`。2026-10-21 整合：JWT 与一人多角色融合，access/refresh 携带活动角色，switch_role 校验持有后重签双 token。

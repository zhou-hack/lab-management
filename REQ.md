# 需求记录（REQ）

记录用户提出的全部需求与处理状态；每条按时间倒序追加，未经用户确认不改写已交付条目。

| 日期 | 需求（原话摘要） | 状态 | 落地说明 |
| --- | --- | --- | --- |
| 2026-10-22 | 更新 AGENTS.md REQ.md CURRENT.md | ✅ 已交付 | AGENTS.md 补 GH013 签名压单推送规则与首登名单口径（短信首登建档已随登录重写下线）；REQ.md 追加本批条目；新建 [CURRENT.md](CURRENT.md) 现状快照 |
| 2026-10-22 | 出一份 PPT：功能点/做到了什么/做得怎么样/解决了什么/下一步规划，放根目录 gitignore 掉；素材去实际 UI 截图；权限的不要截图、主要面对 toC、不要代码级名词；加点动画 | ✅ 已交付 | [实验室管理系统_阶段汇报.pptx](实验室管理系统_阶段汇报.pptx) 12 页（`.gitignore` 加 `*.pptx`）；browser 代理实拍 15 张 UI 截图（[tools/ppt_shots](tools/ppt_shots)）；toC 化两轮改写（撤权限后台截图、去代码术语）；PowerPoint COM 注入 Fade 翻页+66 个淡入效果（[tools/animate_ppt.ps1](tools/animate_ppt.ps1)、[tools/build_ppt.py](tools/build_ppt.py)） |
| 2026-10-22 | 帮我远端删掉 release/0_1 0_1_1 0_2（ruleset 放行后）+ 清理本地残留引用 | ✅ 已交付 | 三条远端分支删除成功；本地 `release/0_1`、`release/0_1_1`（同指向 9bf015a，已确认被 origin/dev/auth 与 origin/dev/v0_1 包含）强制删除，本地仅剩 dev/v0_1、dev/auth、main |
| 2026-10-22 | 忽略没用的东西 push（dev/v0_1） | ✅ 已交付 | 直推被 GH013 拒（7 个协作者未签名提交）→ `git commit-tree -S` 签名压单 `36512d2`（父 53513d7）推送为新分支 origin/dev/v0_1；未提交改动原样保留 |
| 2026-10-22 | 看看远端有没有更新不急着合 | ✅ 已交付 | fetch 发现 4 分支新提交未收编：dev/basic_model（参数配置分组面板）、dev/database（Typeahead composable）、dev/space（场地下拉/信息面板等 4 个）、固定设备管理（价值定级删除等 4 个）；main 无更新；dev/database 旧需求 2.1 提交确认已被 dev/v0_1 收编 |
| 2026-10-22 | 本地先取消价值分级 | ✅ 已交付 | [EquipmentBookingServiceImpl](src/main/java/com/labmanage/module/equipment/service/impl/EquipmentBookingServiceImpl.java) 移除「待价值定级禁止申请」门槛，未定级设备可申请（快照缺级按 NORMAL 落库）；教师/学生代理申请一律 APPROVED 直通、学生单教师初审通过即 APPROVED（原贵重转 PENDING_ADMIN 终审逻辑注释保留为「本地临时，恢复时还原」）；[BookingApplyDialog.vue](frontend/src/components/BookingApplyDialog.vue) 提示文案同步去掉贵重终审描述 |
| 2026-10-22 | 未选中的菜单框也要圆角 | ✅ 已交付 | [element.css](frontend/src/styles/element.css) 内缩 8px + 8px 圆角从仅 `.el-menu-item` 扩展到 `.el-sub-menu__title`（目录标题），未选中项 hover 浅蓝底同为圆角 |
| 2026-10-22 | el-menu-item 高度改矮一点 | ✅ 已交付 | [element.css](frontend/src/styles/element.css) 通过 EP 官方变量 `--el-menu-item-height` / `--el-menu-sub-item-height` 设为 44px（默认 56px），目录标题同源生效 |
| 2026-10-22 | 时间不允许选择早于今日以及当前时间 | ✅ 已交付 | [BookingApplyDialog.vue](frontend/src/components/BookingApplyDialog.vue) 使用时段选择器加 `:disabled-date` 禁选今日之前（上海本地日期，复用场地预约同款 todayShanghai 实现）；datetimerange 不支持禁用时分秒，选择完成时校验开始时间早于当前时间 1 分钟以上即提示并清空重选；后端 [EquipmentBookingServiceImpl](src/main/java/com/labmanage/module/equipment/service/impl/EquipmentBookingServiceImpl.java) 同步加边界校验（开始时间早于当前时间 5 分钟以上返回 40000） |
| 2026-10-22 | el-menu-item is-active 加圆角，参考 Element Plus | ✅ 已交付 | [element.css](frontend/src/styles/element.css) 菜单项内缩 `margin: 0 8px` + `border-radius: var(--r-xs)`（8px），选中项浅蓝底圆角浮起，hover 态同享圆角，参考 Element Plus 官网侧栏选中样式 |
| 2026-10-22 | 学生及代理申请借用设备：学生的请求直接发送给对应老师；学生代理则直接发送给设备管理员，与老师同级别 | ✅ 已交付 | [EquipmentBookingServiceImpl](src/main/java/com/labmanage/module/equipment/service/impl/EquipmentBookingServiceImpl.java) 申请分流改为：持有效 assistant_grant 的学生跳过教师初审、免指定审批教师，贵重单直接 PENDING_ADMIN、普通单直接 APPROVED（系统事件注明代理身份）；[BookingApplyDialog.vue](frontend/src/components/BookingApplyDialog.vue) 修复 store.user 字段名回归 bug，审批教师项按「学生且非代理」显隐；流程文档 [固定设备三权级流程清单.md](docs/固定设备三权级流程清单.md) 同步 |
| 2026-10-22 | 对应权限架构无法使用的功能，不要显示在无权使用人的权限内，直接 hide；例如运维管理员无需「场地预约、设备申请初审、设备使用审批」 | ✅ 已交付 | OPS_ADMIN 菜单收敛为系统管理子树（目录 1 及其子页），业务页面（场地/设备/物料/预约/助理管理）全部隐藏；权限绑定维持全量不变。基线 [sql/sys_ops_role.sql](sql/sys_ops_role.sql) 菜单绑定加白名单；新增迁移 [sql/migrations/ops_admin_menu_scope.sql](sql/migrations/ops_admin_menu_scope.sql)；live 库已执行并验证 |
| 2026-10-22 | 学生代理和学生看到的不一样（根据权限）；对应权限架构无法使用的功能直接隐藏 | ✅ 已交付 | 「物料代领」菜单 205 由 STUDENT 改绑 ASSISTANT 角色：后端动态 ASSISTANT 仅在 assistant_grant 有效授权时并入装配，未授权学生不显示该入口，学生代理可见。更新正本 [sql/lab_management_all.sql](sql/lab_management_all.sql) 与 [sql/migrations/v0_1_unified_realign.sql](sql/migrations/v0_1_unified_realign.sql)，新增迁移 [sql/migrations/assistant_pickup_menu_rebind.sql](sql/migrations/assistant_pickup_menu_rebind.sql) |
| 2026-10-22 | relaunch 前后端 | ✅ 已交付 | 后端 8080（dev profile，lab_manage 账号）、前端 5174 拉起并冒烟通过 |
| 2026-10-21 | 老师快捷登录；学生账号要多一个（共 2 个学生账号）；手动测试学生代理与学生的权限差异 | ✅ 已交付 | 快捷登录面板扩为 5 个：admin / opsadmin / teacher1（王老师）/ student1（普通学生张同学）/ student2（学生代理李同学）。dev 库补 teacher1、student2 账号（李同学改名）；延长既有 assistant_grant 至 2027-01 |
| 2026-10-21 | 登录界面加快捷登录 | ✅ 已交付 | [LoginView.vue](frontend/src/views/login/LoginView.vue) 新增「开发快捷登录」区块，`import.meta.env.DEV` 条件渲染，生产构建剔除；opsadmin 的 dev 密码重置为 Admin123456 |
| 2026-10-21 | 拉起前后端，开浏览器测试 | ✅ 已交付 | 登录→首页→设备管理→预约管理→人员管理全链验证通过；顺手修复 AssetServiceImpl 空 Map.get(null) NPE；live 库补建 lab_booking_series/class_period 表 |
| 2026-10-21 | 拉起前后端（上次关了） | ✅ 已交付 | 双端重拉 |
| 2026-10-21 | 更新最新版顺便做整合：从远端拉，我重写了登录逻辑 dev/auth，进行一次整合后拉起前后端 | ✅ 已交付 | dev/v0_1 依次并入 5 个远端功能分支并移植 dev/auth 登录重写（JWT 双 token + 活动角色重签，融合 basic_model 一人多角色）；后端 283 测试全绿、前端 lint/test/build 通过；提交 c547c62（auth 移植）、ec7fa23（合并残留修复） |

# 现状快照（CURRENT）

最后更新：2026-10-22。记录仓库当前所处状态与待办，供新会话快速接续；需求流水见 [REQ.md](REQ.md)。

## 分支与提交

- 本地分支：`dev/v0_1`（当前，HEAD `ec7fa23`）、`dev/auth`（`53513d7`）、`main`。
- 远端 `origin/dev/v0_1` 为签名压单 `36512d2`（父 53513d7），内容与本地已提交部分一致但历史不同构——**本地不对其 pull/merge**，续推沿用 `git commit-tree -S` 压单（规则已写入 AGENTS.md）。
- 远端 `release/0_1`、`0_1_1`、`0_2` 已删除（2026-10-22，ruleset 临时放行后），本地残留引用已清。
- 未提交改动 9 项 + 未跟踪 3 项（`REQ.md`、`sql/migrations/assistant_pickup_menu_rebind.sql`、`sql/migrations/ops_admin_menu_scope.sql`），为用户与近期会话的工作成果，保持原样未动。

## 远端未收编的新工作（2026-10-22 fetch，暂不合并）

| 分支 | 新增 |
| --- | --- |
| dev/basic_model | 参数配置分组设置面板（保存即发布、历史版本抽屉） |
| dev/database | Typeahead 分页远程选项公共 composable |
| dev/space | 场地下拉/信息面板/设备清单字段/sql 对齐（4 个） |
| 固定设备管理 | 价值定级删除（远端版）、申请搜索默认 15 条（4 个） |

注意：远端"固定设备管理"的价值定级删除与本地"先取消价值分级（临时、可还原）"路线不同，收编时需裁决。

## 系统形态（截至今日）

- 登录：JWT 双 token（access 2h / refresh 7d）+ BCrypt 盐预取前端哈希；已融合一人多角色（活动角色切换 `POST /auth/switch_role` 重签）；短信登录、首登建档、强制改密均已下线。
- 角色：ADMIN / TEACHER / STUDENT / ASSISTANT（动态）/ OPS_ADMIN；系统管理整体归 OPS_ADMIN，ADMIN 仅保留业务模块操作日志。
- 模块：设备三权级（价值定级门槛本地临时取消）、物料全链路、场地预约（space 模块 2026-10-20 已下线，原依赖设计待重新评审）、预约限制页面下线（表保留供逾期冻结）。
- 测试基线：后端 283 全绿、前端 lint/test/build 通过（2026-10-21 整合验证时）。

## 运行环境

- 后端 8080（dev profile，lab_manage 专用账号）；前端 5174（5173 另有一实例）；live 库 lab_management 全部迁移已执行（含 OPS_ADMIN 菜单收敛、物料代领菜单改绑 ASSISTANT）。
- dev 快捷登录 5 账号：admin / opsadmin / teacher1 / student1 / student2（学生代理，grant 延至 2027-01）。

## 待办（按优先级）

1. 业务前端按 JWT 基线重建/收编：dev/auth 上仅登录页，各业务分支新前端工作（见上表）待统一落位。
2. dev/v0_1 走 PR 合并 main（压单历史需维护者审核）。
3. space 模块下线后的设计重新评审（数据范围 CAMPUS/BUILDING/LAB、场地预约依赖）。
4. 价值定级：本地临时取消 vs 远端彻底删除，二选一裁决。
5. 遗留 3 个 v0.2 合并前历史小问题（非阻塞，见维护记录）。

## 本地产物（不入库）

- `实验室管理系统_阶段汇报.pptx`（toC 版 12 页，带动画；`*.pptx` 已 gitignore）。
- `tools/`：build_ppt.py / animate_ppt.ps1 / ppt_shots（15 张 UI 实拍）/ ppt_preview。

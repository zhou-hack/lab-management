# 仓库开发指引

本文件适用于整个仓库，供开发者和编码代理共同遵守。完整开发约定见 [docs/development.md](docs/development.md)；修改公共约定时同步更新两份文件。

## 项目文档约束

- 每次开始处理本项目任务时，必须先检查当前项目根目录下的 `docs` 目录；目录不存在时记录为不存在并继续。
- 递归读取 `docs` 目录内的文档文件，至少包含 `.md`、`.mdx`、`.txt`、`.adoc`、`.rst`、`.json`、`.yml`、`.yaml` 文件；不得只读取 `docs/development.md` 或依赖固定文件名。遇到无法识别或无法读取的文件，记录路径并说明影响。
- `docs` 目录中的文档内容视为本文件的追加约束，等同于把它们按读取顺序追加在本文件末尾；完成读取后再进行分析、修改、运行命令或创建文件。文档中的开发规范、接口约定、架构约束和验证要求都必须遵守。
- 约束优先级为：用户当前明确指示 > 本文件及其 `docs` 追加约束 > 工具或框架默认行为。不同文档冲突时，以目录路径排序后的后读取文档为准，并在任务说明中记录冲突；安全、版本控制和不得破坏用户改动的要求仍然适用。
- 修改、删除或新增 `docs` 内文档时，先读取受影响文件及其同目录相关文档；涉及公共约定时同步检查本文件，避免两处规则不一致。
- 文档读取只需在当前任务开始时完成一次；若用户在任务中修改了 `docs` 文件，或代理准备依赖新生成的文档，则重新读取受影响文档。

## 技术与目录

- 后端固定使用 Spring Boot 4.1.1、Eclipse Temurin JDK 17.0.20.1+1、MyBatis-Plus 3.5.17、Sa-Token 1.46.0、MySQL 8.4.11 LTS、Connector/J 26.7.0、Knife4j UI 4.5.0、springdoc 3.1.1、Maven 3.10.0、Maven Wrapper 3.3.4；不得静默更换已指定版本。
- Spring Boot 4.1.1 支持 Java 17 到 26，团队选择 Java 17 LTS 的最新稳定补丁。设置 `java.version = 17`、release 17，开发、测试、CI 保持一致，不启用预览特性。Spring Framework、Jackson、Tomcat、JUnit 与构建插件由 Boot 依赖管理控制；Connector/J 显式固定为 26.7.0，初始化时验证连接、事务及日期读写。
- MyBatis-Plus 使用 `com.baomidou:mybatis-plus-spring-boot4-starter`，分页解析模块 `mybatis-plus-jsqlparser` 同为 3.5.17；Sa-Token 使用 `cn.dev33:sa-token-spring-boot4-starter:1.46.0`。
- 文档集成采用 `com.github.xiaoymin:knife4j-openapi3-ui:4.5.0` 加 `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`，OpenAPI 输出配置为 `OPENAPI_3_0`。不直接使用面向 Boot 3 的 Knife4j Jakarta starter；初始化时验证页面、资源、OpenAPI 配置端点及 Bearer token 调试，此组合尚未进行应用级运行验证。
- 上述 MyBatis-Plus、Sa-Token、Knife4j 版本按 2026-10-13 官方发布记录与 Maven Central 核实为最新稳定版，发布未标注 LTS，不宣称它们是 LTS。
- 仓库为单体布局：后端 Maven 工程在仓库根目录（`pom.xml`、`src/main`、`src/test`、`.mvn`），前端独立工程在 `frontend/`，数据库脚本在 `sql/`，产品与规范文档在 `docs/`。
- 根包固定为 `com.labmanage`，启动类为 `LabApplication`。公共代码放在 `common`，配置放在 `config`，用户、角色、菜单及权限放在 `roles`，场地、设备、物料业务放在 `module` 的对应子包。
- 控制器负责校验和请求编排，service 负责业务，mapper 负责数据库访问；使用 DTO / VO 定义接口契约，不直接暴露实体或 ORM 分页对象。
- 后端公共地基已建立，新增模块在 `roles` 或 `module/{业务}` 下自行搭建，并复用公共层：
  - `common/core`: `BaseEntity`（含 id/createTime/updateTime/deleted）、`BasePageQuery`（pageNum/pageSize 校验）
  - `common/result`: `Result<T>`（code/msg/data）、`PageResult<T>`（records/total/pageNum/pageSize）、`ResultCode` 枚举
  - `common/exception`: `BusinessException` + `GlobalExceptionHandler`（统一处理校验、Sa-Token、404、500）
  - `common/serializer`: `InstantSerializer`（UTC 三位毫秒输出）、`InstantDeserializer`（ISO 8601 偏移量解析）
  - `common/utils`: `PageUtils`（MyBatis-Plus Page 与 PageResult 互转）
  - `config`: `JacksonConfig`、`MybatisPlusConfig`（分页插件）、`MyMetaObjectHandler`（审计字段填充）
  - 实体继承 `BaseEntity` 并加 `@TableName`；DTO 使用 record + Jakarta Validation；VO 使用 record；Service 接口+实现；Controller 加 `@SaCheckPermission` 并返回 `Result`/`PageResult`。
- 前端固定使用 Node.js 24.21.0 LTS、npm 12.2.0、Vue 3.5.43、Vite 8.3.4、Element Plus 2.14.7、Vue Router 5.4.0、Pinia 4.0.3、Axios 1.20.0、ESLint 10.12.0；采用 JavaScript、Vue 单文件组件与 `script setup`。前端工程根目录为 `frontend/`，源码在 `frontend/src` 下按 `api`、`components`、`layout`、`router`、`store`、`utils`、`views` 分目录；`@` 别名指向 `frontend/src`。
- 配套依赖为 `@vitejs/plugin-vue` 6.0.10、`@element-plus/icons-vue` 2.3.2、`@vue/devtools-api` 8.2.1、`@eslint/js` 10.0.1、`eslint-plugin-vue` 10.11.1、`vue-eslint-parser` 10.4.1、`globals` 17.13.0；测试使用 Vitest 5.0.4、Vue Test Utils 2.5.1、jsdom 30.1.2。ESLint 使用 flat config。
- 除团队已指定的版本或 Java 17 发布线外，新增版本按 2026-10-13 核实的最新稳定正式版固定。直接依赖写精确版本，提交 npm 锁文件；Node.js、npm 同步记录在 `engines`、`packageManager` 和 CI，Node.js 自带 npm 需升级至 12.2.0。禁止用 `--force` 或 `--legacy-peer-deps` 掩盖依赖冲突。
- Windows x64 的 JDK 与 Node.js 官方下载链接见 [开发文档](docs/development.md) 的“开发环境下载”小节；`JAVA_HOME` 指向 JDK 根目录，不包含 `bin`。
- 尊重已存在的模块边界和实现方式，聚焦当前任务，避免无关重构与依赖变更。

## 命名与契约

- 变量、方法、JSON 字段使用小驼峰；Java 类、Vue 组件使用大驼峰；包名全小写；数据库表和字段使用小写下划线，启用下划线到驼峰映射。
- 自定义 URL、普通目录和配置文件名禁止横杠，分词使用下划线。保留约定或工具要求的 `application-dev.yml`、`application-test.yml`、`v-permission`、`dev/{desc}` 等名称。
- API 前缀统一为 `/api/v1`，使用 RESTful 资源与 HTTP 方法。JSON 返回体为 `code`、`msg`、`data`；成功 `code = 0`，失败 `data = null`，HTTP 状态反映真实结果。
- 标准错误码为 40000、40100、40300、40400、40900、50000，分别对应 HTTP 400、401、403、404、409、500。新错误码登记在公共枚举；所有异常通过统一处理返回，不泄露内部实现或敏感数据。
- 分页请求为 `pageNum`、`pageSize`，默认 1、20，上限 100；响应 `data` 包含 `records`、`total`、`pageNum`、`pageSize`。后端校验分页参数，排序字段使用白名单。
- 业务时区为 `Asia/Shanghai`，时间点传输、计算与存储统一为 UTC；具体规则见下节。

## 数据库与实体（Final）

- Schema 以 [docs/数据库设计.md](docs/数据库设计.md)（评审稿 v1.1）为准；建表脚本按模块放 `sql/`，RBAC 已定稿为 [sql/sys_rbac.sql](sql/sys_rbac.sql)（11 张表：部门、名单、用户、角色、权限、菜单、三组关联、数据范围、预约限制）。后续模块脚本按实施分组 A→E 补充，不另行设计。
- 主键统一雪花 ID：`BIGINT`（MyBatis-Plus `ASSIGN_ID`），接口序列化为字符串，避免 JavaScript 精度损失；关联字段同类型。初始数据使用固定小 ID（如角色 1-4、管理员 1），与雪花 ID 不冲突。
- 公共字段：`id/create_time/update_time/deleted/create_user_id/update_user_id`；当前 `BaseEntity` 已覆盖前四项，后续接入数据库时补齐操作者字段。`version` 仅余额等需并发控制的可变表使用，从 0 起条件更新递增。
- 时间列 `DATETIME(3)` 存 UTC，列默认值 `UTC_TIMESTAMP(3)`；会话 `+00:00` 时 `ON UPDATE CURRENT_TIMESTAMP(3)` 即 UTC。
- 类型记号：`CODE=VARCHAR(64)`、`NAME=VARCHAR(128)`、`SHORT=VARCHAR(32)`（Java 枚举映射，不用 MySQL ENUM）、`BOOL=TINYINT`（仅 0/1，加 CHECK）、`QTY=DECIMAL(18,3)`、`MONEY=DECIMAL(18,2)`、`PRICE=DECIMAL(18,4)`、`DATE=DATE`（纯日期）、`CLOCK=TIME(0)`（上海节次）。
- 外键默认 `RESTRICT`，不级联删除历史业务；审计字段 `create_user_id/update_user_id` 不建外键（避免组织/用户循环依赖）；跨模块引用（如 `campus_id`、`source_document_id`）只建列与索引，外键由所属模块脚本补充。
- 唯一与删除：业务码、学工号逻辑删除后不复用；关系表保持一行唯一（组合唯一建索引），不用 `(业务键, deleted)` 解决重复创建；含可空列的目标去重使用生成列归一（参照 `sys_user_data_scope.scope_target_id`）。
- RBAC 定稿规则：预置角色 `ADMIN/TEACHER/STUDENT/ASSISTANT`；学生/老师首登须匹配 `sys_person_roster` 预录名单，不能自行提升身份；`ASSISTANT` 不持久化到 `sys_user_role`，由有效 `assistant_grant` 动态计算；管理员数据范围用 `sys_user_data_scope`（ALL/DEPARTMENT/CAMPUS/BUILDING/LAB + READ/WRITE），无范围记录不自动授予全校数据。
- 初始数据：预置部门"设备处"（id=1）、4 个角色、管理员 `admin`（id=1，密码哈希为占位值，部署前必须替换并强制首登修改）；权限与菜单初始数据由业务模块脚本按 `模块:资源:操作` 规范登记。
- 权限标识规范 `模块:资源:操作`（如 `item:material:create`），前后端一致，登记在 `sys_permission`。

## 时间与 UTC

- 页面展示、本地日历和业务日界线使用 `Asia/Shanghai`；JVM 使用 `-Duser.timezone=UTC`，容器时区也设 UTC。通过 UTC 时钟与显式 `ZoneId` 处理时间，不依赖系统或浏览器默认时区，不手动加减八小时。
- 时间点入参必须使用带 `Z` 或数字偏移量的 ISO 8601 / RFC 3339，Java 使用 `OffsetDateTime` 接收后转 `Instant`；出参使用 `Instant`，通过公共序列化器统一输出 UTC 三位毫秒格式，如 `2026-10-13T01:00:00.000Z`。`Z` 等价于 `+00:00`。
- 入参无偏移量、格式无效或小数秒超过三位时返回 HTTP 400 / `code = 40000`；服务端生成时间点归一化到毫秒精度。Jackson 默认时区为 UTC，关闭数值时间戳输出；不能仅靠全局时区配置保证类型转换正确。
- MySQL 时间点字段使用 `DATETIME(3)` 存 UTC 值。其本身无时区转换能力；统一 MyBatis TypeHandler 显式执行 `Instant` 与 UTC `LocalDateTime` 的 JDBC 读写转换，注册并验证 MyBatis-Plus 结果映射。数据库不存 `Z` 或偏移量后缀，不依赖驱动猜测字段时区。
- MySQL 服务与所有连接会话使用 `+00:00`；Connector/J 参数为 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true`，初始化校验全局及会话时区。
- 审计时间由服务端 UTC 时钟填充，不允许客户端覆盖；SQL 生成时间时使用 `UTC_TIMESTAMP(3)`。导入、导出、定时任务与直接 SQL 写入同样遵守 UTC 约定。
- 纯日期使用 `yyyy-MM-dd`、`LocalDate`、`DATE`，不做时区转换。前端时间工具按上海时区解析选择器值并提交 UTC，显式按上海时区展示；禁止把无偏移量字符串直接交给 `new Date`。
- 按上海日期查询先把当地当天与次日零点转 UTC，再用 `>= startUtc AND < endUtc`。历史数据迁移须确认原时区；验证等价偏移量、数据库往返、毫秒精度、上海午夜筛选、不同浏览器时区及非法输入。

## 权限与环境

- 使用 Sa-Token 实现 RBAC：用户关联角色，角色关联菜单与权限。权限标识为 `模块:资源:操作`，前后端一致。
- 每个受保护接口由后端校验权限；前端使用路由守卫、动态菜单及 `v-permission`，不能依赖前端隐藏按钮保证授权。
- 动态路由使用本地组件白名单映射；登录及刷新加载权限，注销或 401 后移除动态路由并清理用户状态。
- token 存在 `sessionStorage`，通过统一工具访问；Axios 添加 `Authorization: Bearer <token>`，后端配置对应读取方式。
- Axios 统一处理返回体、401、403 和网络错误，页面不得重复实现认证逻辑。
- 环境为 `dev`、`test`，使用对应 profile 配置及独立数据库；敏感配置由环境变量或未跟踪本地配置提供。
- 跨域来源使用环境白名单，允许必要请求头及 OPTIONS；不依赖跨域 cookie。Knife4j 按开发、测试环境启用。

## Git 与审核

- 仓库为 `https://github.com/zhou-hack/lab-management`。禁止直接推送 `main` 或他人分支，不得强推或改写他人历史。
- 执行 git 网络操作（clone/fetch/pull/push/ls-remote）及依赖下载前，先探测本地 Clash Verge 代理端口 `127.0.0.1:7897` 是否可连接（如 `Test-NetConnection 127.0.0.1 -Port 7897`）；可用则本次操作走代理：git 用命令级参数 `git -c http.proxy=http://127.0.0.1:7897 <command>`，其他工具用环境变量 `HTTP_PROXY`/`HTTPS_PROXY` 指向 `http://127.0.0.1:7897`。不得修改全局或仓库 git config，端口不可用时直连并在失败时报告，不重试堆积。
- 个人分支使用 `dev/{desc}`，描述仅包含小写英文字母、数字和下划线，并以字母开头，例如 `dev/item`、`dev/permission`、`dev/user_management`；同模块可用英文姓名或账号后缀，如 `dev/item_someone`。禁止中文或横杠，该规则优先于工具默认分支前缀。
- 为协作者首次初始化开发环境前，先只读检测 Git 仓库、远端地址和当前分支。目标仓库为 `zhou-hack/lab-management`，其 HTTPS 或 SSH 远端地址均可；当前分支须匹配 `^dev/[a-z][a-z0-9_]*$`，且不能是游离 HEAD 或已知属于他人的分支。
- 检测到仓库和当前分支符合上述要求时，直接沿用当前分支继续初始化，不再询问，不重新克隆、创建或切换分支。若本次会话已明确指定另一分支及新建或复用意图，按用户指示执行，不重复询问。
- 仅在检测不符合要求且本次会话未明确分支信息时，询问本人要使用的个人分支名，以及新建还是复用已有分支；远端缺失或指向其他仓库时一并确认仓库信息。在回复前只做只读检查，暂停分支创建或切换及后续项目初始化，不自行猜测或分配分支。
- 确认分支后检查本地与远端同名分支。新分支先 `git fetch origin`，再从最新 `origin/main` 创建；已有分支直接切换或跟踪远端分支，不覆盖、重建或使用他人的分支。切换前保留未提交改动。
- 操作示例见开发文档：`git switch -c dev/user_management origin/main`、`git commit -m "feature: 实现用户管理"`、`git push -u origin dev/user_management`。示例名须替换为本人确认的分支；暂存前检查忽略规则，提交前检查 `git diff --cached`，避免夹带凭据或无关改动。
- 提交使用 `feature:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`，后接简短描述。提交聚焦任务，不提交凭据、构建产物或无关文件。
- 通过 PR 合并到 `main`，至少由一名其他协作者审核并通过所需检查。PR 写明目的、变更、验证、接口或数据迁移影响；由有权限的维护者在批准后合并。
- 保留用户未提交的改动，不覆盖、回退或清理他人的工作。

## 验证与交付

- 初始化时配置 Maven Wrapper、前端 npm 锁文件及 `dev`、`lint`、`test`、`build` 脚本，`test` 使用 Vitest 单次运行。后端在仓库根目录执行 `./mvnw verify`（Windows PowerShell 为 `./mvnw.cmd verify`）；前端在 `frontend/` 目录执行 `npm ci`、`npm run lint`、`npm run test`、`npm run build`。
- 按变更风险验证关键行为，重点覆盖认证、权限拒绝、参数错误、分页和数据变更。纯文档修改检查约定一致性和链接即可。
- 仓库尚未初始化、工具缺失或外部服务不可用时，明确报告未执行的检查与原因，不声称通过。版本可获取及依赖范围匹配不等同于应用级运行验证。
- 数据库变更提交可复现 SQL 或迁移文件；接口和权限变更同步更新 Knife4j 文档与开发约定。
- 完成后简要说明变更、验证结果和实际限制；未经用户要求，不自动提交、推送或合并。


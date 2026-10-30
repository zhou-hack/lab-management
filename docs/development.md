# 实验室管理系统开发约定

仓库：[zhou-hack/lab-management](https://github.com/zhou-hack/lab-management)。本文约定项目技术栈、接口契约和协作流程；自动化开发约束见根目录 `AGENTS.md`。

## 1. 技术栈

| 范围 | 技术 | 版本或要求 |
| --- | --- | --- |
| 后端框架 | Spring Boot | 4.1.1 |
| Java 运行与编译 | Eclipse Temurin JDK 17 | 17.0.20.1+1，Java 17 LTS 最新稳定补丁 |
| ORM | MyBatis-Plus | 3.5.17，使用 Boot 4 专用 starter |
| 认证与权限 | JWT（jjwt） | 0.12.6，HS256 无状态会话 + RBAC（已替代 Sa-Token） |
| 数据库 | MySQL | 8.4.11 LTS |
| JDBC 驱动 | MySQL Connector/J | 26.7.0 |
| 接口文档 | Knife4j / Swagger（OpenAPI） | Knife4j UI 4.5.0 + springdoc 3.1.1 |
| 后端构建 | Maven / Maven Wrapper | 3.10.0 / 3.3.4 |
| 前端运行与包管理 | Node.js / npm | 24.21.0 LTS / 12.2.0 |
| 前端框架 | Vue 3 / Vite | 3.5.43 / 8.3.4 |
| UI | Element Plus | 2.14.7 |
| 路由 | Vue Router | 5.4.0 |
| 状态 | Pinia | 4.0.3 |
| HTTP | Axios | 1.20.0 |
| 代码规范 | ESLint | 10.12.0，使用 flat config |
| 前端测试 | Vitest / Vue Test Utils / jsdom | 5.0.4 / 2.5.1 / 30.1.2 |

版本核实日期为 2026-10-13。保留已指定的 Spring Boot、MySQL 与 Node.js 24.21.0 LTS 版本；JDK 按团队选择使用 Java 17 的最新稳定补丁，其余新增版本采用可获取的最新稳定正式版。Spring Boot 4.1.1 官方支持 Java 17 到 26；设置 `java.version = 17`、编译目标 release 17，开发、测试与 CI 使用同一 JDK，不启用预览特性。

已指定版本如需调整，须在 PR 中说明原因并经审核；不得默默替换。版本固定在 `pom.xml`、Maven Wrapper 配置和前端 `package.json`、`package-lock.json` 中，直接依赖使用精确版本，不写 `latest`、`^` 或 `~`。Node.js 与 npm 版本同步记录在 `engines`、`packageManager` 和 CI；Node.js 24.21.0 自带 npm 11.19.0，初始化环境时另行升级至约定的 npm 12.2.0。该 Node.js 版本满足当前 Vite、ESLint、Vitest、jsdom 与 npm 声明的运行版本要求。

后端 Spring Framework、Jackson、Tomcat、JUnit 和构建插件由 Spring Boot 4.1.1 的依赖管理统一控制，不逐个强制升级。Connector/J 使用 `com.mysql:mysql-connector-j:26.7.0`（覆盖 Boot 默认驱动版本），初始化时验证与 MySQL 8.4.11 的连接、事务和日期读写。版本核实及依赖声明检查不等同于应用已运行通过。

### 开发环境下载（Windows x64）

| 工具 | 版本 | 下载链接 |
| --- | --- | --- |
| Eclipse Temurin JDK 17 | 17.0.20.1+1 | [Adoptium 官方下载页（MSI 安装包）](https://adoptium.net/zh-CN/download?link=https%3A%2F%2Fgithub.com%2Fadoptium%2Ftemurin17-binaries%2Freleases%2Fdownload%2Fjdk-17.0.20.1%252B1%2FOpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.msi&vendor=Adoptium) |
| Node.js | 24.21.0 LTS | [Node.js 官方 MSI 安装包](https://nodejs.org/dist/v24.21.0/node-v24.21.0-x64.msi) |

`JAVA_HOME` 指向 JDK 安装根目录，不包含 `bin`；Node.js 安装完成后，将 npm 升级至项目约定版本：`npm install --global npm@12.2.0`。

### 后端依赖版本决策

截至 2026-10-13，按官方发布记录和 Maven Central 元数据锁定以下版本。MyBatis-Plus、Knife4j 的这些发布未标注 LTS，因此采用最新稳定正式版，不将其称为 LTS，不使用快照或预发布版；jjwt 固定 0.12.6 稳定版（2026-10-20 登录重写时引入，替代 Sa-Token）。

| Maven 坐标 | 固定版本 | 用途 |
| --- | --- | --- |
| `com.baomidou:mybatis-plus-spring-boot4-starter` | 3.5.17 | Spring Boot 4 ORM 集成 |
| `com.baomidou:mybatis-plus-jsqlparser` | 3.5.17 | 分页插件等 SQL 解析能力，与 ORM 版本一致 |
| `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | JWT access/refresh 双 token 签发与校验 |
| `com.github.xiaoymin:knife4j-openapi3-ui` | 4.5.0 | Knife4j 文档页面静态资源 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.1.1 | OpenAPI 生成及 Swagger 配置端点，支持 Boot 4 |

Knife4j 4.5.0 的 `knife4j-openapi3-jakarta-spring-boot-starter` 面向 Boot 3，不作为本项目 Boot 4 的集成依赖。采用独立 UI 资源加 springdoc 3.1.1，初始化时配置文档页面资源访问和 OpenAPI 端点；为旧版 UI 使用 `springdoc.api-docs.version: OPENAPI_3_0`。此组合是本项目集成方案，尚未进行应用级运行验证，也不表示 Knife4j 官方提供 Boot 4 starter。

初始化必须验证应用启动、分页查询、JWT 鉴权（取盐、登录、刷新、401 拦截），以及 `/doc.html`、`/v3/api-docs`、`/v3/api-docs/swagger-config` 的访问、接口模型展示和 Bearer token 调试。Knife4j starter 提供的增强配置不能直接视为独立 UI 方案已支持。

版本依据：[MyBatis-Plus 3.5.17](https://github.com/baomidou/mybatis-plus/releases/tag/v3.5.17)、[jjwt 0.12.6](https://github.com/jwtk/jjwt/releases/tag/0.12.6)、[Knife4j 4.5.0](https://github.com/xiaoymin/knife4j/releases/tag/v4.5.0)、[springdoc 官方兼容说明](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)。

### 前端工具与实现约定

前端统一使用 JavaScript、Vue 单文件组件与 Composition API（`script setup`），不额外引入 TypeScript 工具链。配套依赖固定为：

| npm 包 | 固定版本 | 用途 |
| --- | --- | --- |
| `@vitejs/plugin-vue` | 6.0.10 | Vue 单文件组件编译 |
| `@element-plus/icons-vue` | 2.3.2 | Element Plus 配套图标 |
| `@vue/devtools-api` | 8.2.1 | Pinia 要求的配套依赖 |
| `@eslint/js` | 10.0.1 | JavaScript 推荐规则 |
| `eslint-plugin-vue` | 10.11.1 | Vue 推荐规则 |
| `vue-eslint-parser` | 10.4.1 | Vue 单文件组件解析 |
| `globals` | 17.13.0 | 浏览器与 Node.js 全局变量 |

ESLint 使用 `eslint.config.js`，按浏览器源码与 Node.js 配置文件分别设置环境。npm 脚本统一为 `dev`（Vite）、`lint`（ESLint）、`test`（Vitest 单次运行）、`build`（Vite 构建）。组件测试使用 Vue Test Utils 和 jsdom。初始化安装时必须通过 npm 的 peer dependency 检查，不用 `--force` 或 `--legacy-peer-deps` 掩盖冲突。

版本依据：[Spring Boot 4.1.1 系统要求源码](https://github.com/spring-projects/spring-boot/blob/v4.1.1/documentation/spring-boot-docs/src/docs/antora/modules/ROOT/pages/system-requirements.adoc)、[Temurin JDK 17 发布信息](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse)、[Maven 正式版记录](https://maven.apache.org/docs/history.html)、[Maven Wrapper 元数据](https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/maven-metadata.xml)、[Connector/J 元数据](https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/maven-metadata.xml)、[Node.js 发布记录](https://nodejs.org/dist/index.json)。前端包版本取自 [npm Registry](https://registry.npmjs.org/) 各包的 `latest` 稳定发布标签，并核对 `engines` 与 `peerDependencies`。

## 2. 目录与命名

后端根包固定为 `com.labmanage`：

```text
com.labmanage
├── common          返回体、错误码、异常、分页、工具类
├── config          JWT 安全、跨域、Knife4j、序列化配置
├── roles           用户、角色、菜单、权限，RBAC 核心
├── module          业务模块，仅保留 space、booking、document 等子包
└── LabApplication  启动类
```

模块内部按职责划分 `controller`、`service`、`mapper`、`entity`、`dto`、`vo`；控制器只做入参校验和请求编排，业务逻辑放在 service，数据库访问放在 mapper。实体不直接作为接口请求或响应对象。

前端目录固定为：

```text
src
├── api             后端请求封装
├── components      公共组件
├── layout          侧边菜单、头部、后台布局
├── router          静态路由、RBAC 动态路由与守卫
├── store           Pinia 状态
├── utils           token 等工具
└── views           登录页和业务页面
```

变量、方法、JSON 字段采用小驼峰（如 `userId`）；Java 类和 Vue 组件采用大驼峰（如 `LabSite`、`UserList.vue`）；包名全小写。实体类使用单数业务名，数据库表和字段采用小写下划线（如 `lab_site`、`user_id`），配置 MyBatis-Plus 下划线到驼峰映射。

自定义 URL、普通目录和配置文件名不使用横杠 `-`，需要分词时使用下划线 `_`；代码标识符遵守上述驼峰规则。已指定的 `application-dev.yml`、`application-test.yml`、`v-permission`、`dev/{desc}` 及框架或工具规定的名称保留原写法，不对它们做机械替换。

## 3. 接口与统一返回

统一请求前缀为 `/api/v1`，Axios 的 `baseURL` 和开发代理使用相同前缀。资源路径使用名词复数，例如 `GET /api/v1/lab_sites`；读取使用 GET，创建使用 POST，完整更新使用 PUT，部分更新使用 PATCH，删除使用 DELETE。

JSON 接口统一返回 `code`、`msg`、`data`，成功时 `code = 0`，失败时 `data = null`：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 1
  }
}
```

| HTTP 状态 | 业务 code | 含义 |
| --- | --- | --- |
| 200 / 201 | 0 | 成功 / 创建成功 |
| 400 | 40000 | 参数格式或校验错误 |
| 401 | 40100 | 未登录、token 无效或过期 |
| 403 | 40300 | 无权限 |
| 404 | 40400 | 资源不存在 |
| 409 | 40900 | 唯一性或业务状态冲突 |
| 500 | 50000 | 未预期的服务端错误 |

创建成功使用 201，适用时返回 `Location`。删除成功使用 200 和 `data: null`，以保持统一返回体。HTTP 状态必须表达真实结果，禁止把所有异常包装成 HTTP 200；新增错误码统一登记在 `common` 中的错误码枚举，禁止各模块随意定义。参考 [RESTful API HTTP 状态码](https://restfulapi.net/http-status-codes/)。

全局异常处理器覆盖参数校验、业务异常、认证、授权及未预期异常。认证拦截器产生的错误也要遵守同一响应契约。服务端记录异常细节，响应不得暴露堆栈、SQL 或敏感信息。

分页参数为 `pageNum`（从 1 开始，默认 1）、`pageSize`（默认 20，上限 100）；响应放入 `data`：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 20
}
```

分页边界由后端校验；排序字段使用白名单，不直接拼接客户端传入的 SQL 字段。对外使用统一分页 VO，不直接暴露 ORM 分页对象。

## 4. 环境、安全与前后端协作

- 环境划分为 `dev`、`test`，分别使用 `application-dev.yml`、`application-test.yml`，通过 `spring.profiles.active` 显式选择；基础公共配置放在 `application.yml`。
- 数据库密码、token 密钥等使用环境变量或本地未跟踪配置，不提交真实凭据。测试使用独立数据库。
- 业务展示与日历规则使用 `Asia/Shanghai`；接口时间点、后端计算与数据库存储统一使用 UTC，具体格式及转换规则见下一节。
- 跨域允许来源按环境配置白名单，明确允许的方法和请求头，包括 `Authorization`；放行预检 OPTIONS。采用 token 请求头认证，不依赖跨域 cookie，不开放任意来源。
- 口令不上明文：登录与敏感操作确认前，前端按账号 `GET /auth/salt` 取该账号 BCrypt 盐前缀，用 bcryptjs 以同一盐复算出口令哈希后上传，服务端常量时间比对库中哈希。哈希即凭据可被重放，内网/开发场景接受该风险；传输层安全仍以 HTTPS 为准，禁止在任何场景直传明文口令。
- 本项目采用 `sessionStorage` 保存 access/refresh 双 token，通过统一工具读写，以浏览器标签页会话为生命周期。Axios 统一添加 `Authorization: Bearer <access token>`；401 时先以 refresh token 单飞刷新（`POST /auth/refresh` 轮换双 token）并重试原请求，刷新失败才清态跳登录。
- Axios 统一解包 `code/msg/data` 并处理网络错误；401 清除 token、用户、权限及已注册动态路由，跳转登录；403 提示无权限。退出登录同时调用后端注销接口并清理前端状态。
- RBAC 关系为用户关联角色、角色关联菜单及权限。权限标识采用 `模块:资源:操作`（例如 `booking:lab:create`）；前后端使用同一份标识。
- 登录后获取当前用户、菜单与权限；Vue Router 通过本地组件白名单映射菜单注册动态路由，刷新后重新加载，退出时移除。禁止直接执行后端下发的组件路径或代码。
- 按钮使用 `v-permission` 自定义指令控制可见性（随业务页面重建恢复登记），路由守卫检查登录与页面权限；后端对每个受保护接口执行权限校验（`@RequirePermission`）。隐藏菜单或按钮不能代替后端授权。
- Knife4j 仅在开发和测试环境按需启用；接口定义同步维护入参、出参、分页、权限要求和错误响应。

## 5. 时间与 UTC 规范

业务时区固定为 `Asia/Shanghai`，用于页面展示、业务日界线及本地日历规则；技术运行、接口传输及持久化采用 UTC。`Z` 与 `+00:00` 都表示 UTC，`+08:00` 表示上海当前使用的偏移量，不能把本地时间直接加上 `Z` 当作转换。

| 边界 | 统一约定 |
| --- | --- |
| 接口时间点入参 | ISO 8601 / RFC 3339，必须带 `Z` 或数字偏移量；允许其他偏移量，后端统一转换成 UTC |
| 接口时间点出参 | UTC，固定毫秒精度与 `Z` 后缀，例如 `2026-10-13T01:00:00.000Z` |
| Java 时间点 | 内部与响应使用 `Instant`，入参使用 `OffsetDateTime` 后转 `Instant`；统一公共序列化器输出三位毫秒 |
| MySQL 时间点 | `DATETIME(3)`，字段内容约定为 UTC，例如 `2026-10-13 01:00:00.000` |
| 纯日期 | 接口 `yyyy-MM-dd`、Java `LocalDate`、MySQL `DATE`；不进行 UTC 换算 |
| 页面显示与输入 | 按 `Asia/Shanghai` 显示和解析，提交前转换为 UTC 时间点 |

例如用户选择上海时间 `2026-10-13 09:00:00`，前端提交 `2026-10-13T01:00:00.000Z`；后端也可接受 `2026-10-13T09:00:00.000+08:00` 并归一化到同一个 UTC 时间点。数据库存 `2026-10-13 01:00:00.000`，读取后响应 `2026-10-13T01:00:00.000Z`，页面显示上海时间 `09:00:00`。

### 后端与数据库

- 开发、测试与 CI 的 JVM 使用 `-Duser.timezone=UTC`；容器进程时区也设为 UTC。显式使用 `Clock.systemUTC()`、`ZoneOffset.UTC` 和业务 `ZoneId.of("Asia/Shanghai")`，禁止依赖宿主机默认时区或手动加减八小时。
- Jackson 默认时区配置为 UTC，关闭日期时间数值时间戳输出；通过公共序列化器实现上述 `Instant` 格式。只设置 Jackson 时区不足以保证所有 Java 时间类型自动符合约定。
- 入参缺少偏移量、格式无效或小数秒超过三位时返回 HTTP 400 / `code = 40000`，避免隐式猜测时区或丢失精度。服务端生成时间点时统一归一化到毫秒精度，不对外混用秒或毫秒数字时间戳。
- MySQL 服务默认时区配置为 `+00:00`；Connector/J 连接参数统一设置 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true`，确保连接池中的每个数据库会话都使用 UTC。初始化检查 `@@global.time_zone` 和 `@@session.time_zone` 均为 `+00:00`。
- `DATETIME` 本身不携带时区，也不会自动将上海时间转换成 UTC。在 `common` 中实现统一 MyBatis TypeHandler：写入时将 `Instant` 转为 UTC `LocalDateTime`，通过 JDBC `setObject` 写入；读取时以 `LocalDateTime` 读取并按 UTC 转回 `Instant`。显式注册并验证实体字段映射，包括 MyBatis-Plus 的结果映射；不得依赖驱动猜测 `DATETIME` 的语义。
- 时间点字段如 `created_at`、`updated_at`、预约开始结束时间统一使用 `DATETIME(3)`，避免 `TIMESTAMP` 的 2038 年范围限制。审计时间由服务端公共填充器使用 UTC 时钟生成，不接受客户端覆盖；SQL 必须生成时间时显式使用 `UTC_TIMESTAMP(3)`，不用依赖环境时区的 `NOW()` 或本地字符串。
- 数据库不存 `Z` 或 `+00:00` 文本后缀；UTC 是字段值与读写转换的共同约定。导入、导出、脚本、后台任务与直接 SQL 写入都须遵守该约定。

### 前端与查询

- 统一时间工具负责转换；展示使用显式的 `Asia/Shanghai` 时区（例如 `Intl.DateTimeFormat` 的 `timeZone` 选项），不依赖用户浏览器所在时区。日期选择器的无偏移量值先按业务时区解释再转 UTC，不直接用 `new Date(无偏移量字符串)` 解析。
- 按“上海某天”查询时，后端根据 `Asia/Shanghai` 将当天零点与次日零点分别转成 UTC，使用 `>= startUtc AND < endUtc` 半开区间。日历和定时任务以业务时区计算，再转换成时间点；纯日期不能经过 `toISOString()` 转换而改变日期。
- 已有本地时间数据迁移前，明确原时区并转换成 UTC，不能只改字段说明。初始化必须验证 `+08:00` 与 `Z` 入参得到同一存储值、数据库往返不偏移、毫秒保留、跨上海午夜的日期筛选、不同浏览器时区下的展示，以及非法无时区入参的拒绝行为。

当前仓库仅有开发约定，以上配置、序列化器、TypeHandler 与前端时间工具需在项目初始化时落实并验证。

## 6. Git 协作与 PR

严禁直接推送 `main` 或他人分支。个人工作分支使用 `dev/{desc}`，`{desc}` 表示负责模块，仅使用小写英文字母、数字和下划线，并以字母开头，例如 `dev/booking`、`dev/permission`、`dev/user_management`；如有同模块协作冲突，可使用英文姓名或账号后缀，如 `dev/booking_jinbin`。分支名不使用中文或横杠。

### 初始化时先检测仓库与分支

为每位协作者首次初始化开发环境前，先只读检测是否处于 Git 仓库、远端是否指向 `zhou-hack/lab-management`（HTTPS 或 SSH 均可），以及当前分支是否匹配 `^dev/[a-z][a-z0-9_]*$`。当前分支不能是游离 HEAD 或已知属于他人的分支。

检测到仓库和当前分支符合要求时，直接沿用当前分支继续初始化，不再询问，也不重新克隆、创建或切换分支。开发者已在本次会话明确指定另一分支及新建或复用意图时，按该指示执行，不重复询问。

仅在检测不符合要求且没有明确分支信息时，询问：“请提供你要使用的个人开发分支（例如 `dev/user_management`），并说明是已有分支还是需要新建。”远端缺失或指向其他仓库时一并确认仓库信息。等待回复期间只做只读检查，暂停创建或切换分支及后续项目初始化，不自行分配分支。

需要创建或切换分支时，先检查本地和远端是否已有同名分支：新分支从最新 `origin/main` 创建，已有分支切换或跟踪其远端分支，不覆盖或重建，不使用他人的分支。

### Git 操作示例

以下以已确认新建的个人分支 `dev/user_management` 为例；执行时替换为本人确认的英文分支名。已有本地仓库跳过第 1 步，切换前先检查工作区，保留未提交改动。

```shell
# 1. 首次克隆仓库
git clone https://github.com/zhou-hack/lab-management.git lab_management
cd lab_management

# 2. 获取主分支最新状态，创建自己的开发分支
git fetch origin
git switch -c dev/user_management origin/main

# 3. 开发并完成检查后，确认改动再提交
git status
git add .
git diff --cached
git commit -m "feature: 实现用户管理"

# 4. 推送自己的分支
git push -u origin dev/user_management
```

`git add .` 前确认忽略规则已排除凭据、依赖目录和构建产物；提交前检查暂存内容，移除与本次任务无关的文件。已有本地个人分支使用 `git switch dev/user_management`；只有远端分支时，先 `git fetch origin`，再执行 `git switch --track origin/dev/user_management`。推送后在 GitHub 创建目标为 `main` 的 PR，经审核后合并。

提交信息使用 `类型: 描述`：`feature: 新增场地查询`、`fix: 修复权限校验`；文档使用 `docs:`，重构使用 `refactor:`，测试使用 `test:`，工具或依赖调整使用 `chore:`。一次提交聚焦一个完整变更，不夹带无关格式化或生成文件。

合并流程：

1. 从最新 `main` 创建自己的 `dev/{desc}` 分支；已有分支先获取远端变更，将 `origin/main` 合并到自己的分支并解决冲突。
2. 完成实现与相关验证。后端运行 `./mvnw verify`（Windows PowerShell 使用 `./mvnw.cmd verify`），前端运行 `npm ci`、`npm run lint`、`npm run test`、`npm run build`；初始化项目时补齐对应脚本。
3. 只提交和推送自己的分支，向 `main` 提交 PR。说明目的、主要变更、验证结果，以及接口、数据库或依赖变更和迁移方法；存在未执行检查时注明原因。
4. 至少一名其他协作者审核，检查接口约定、权限、数据兼容性和验证结果。作者根据意见修改，通过所需检查并获批准后，由有权限的维护者通过 PR 合并。
5. 合并后同步 `main`；只清理自己已合并且不再使用的分支。禁止绕过审核直接合并主分支或改写他人分支历史。

数据库结构变更需提交可复现的 SQL 或迁移文件，并说明对已有数据的影响。涉及公共接口、返回体、权限标识或技术版本的调整，同步更新本文和相关接口文档。

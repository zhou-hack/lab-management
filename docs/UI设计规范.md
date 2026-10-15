# 实验室设备管理系统 · UI 设计规范（v2.0）

> 本项目 UI 设计基座。**仅覆盖桌面端**：全部账号（管理员/老师/学生/助理）统一使用同一套
> 管理端侧边栏布局与组件语言，不再提供移动/H5 形态。令牌与组件类在
> `frontend/src/styles/` 下可直接落地，复制即用。
>
> v2.0 变更：移除 H5 全部内容（页头 hero、手机外壳、Vant 映射、触摸端规则、安全区），
> 新增统一桌面布局框架（第 7.4 节）；功能入口改为桌面三列网格。

---

## 1. 设计基调（四条原则）

1. **纯白大圆角卡片承载内容，以 0.5px 细线替代阴影做分隔**——界面几乎不用投影，层级靠底色差（`#f7f8fa` 页面底 / `#ffffff` 卡片面）与发丝线建立
2. **全部交互收敛到单一品牌蓝 `#3f86ff`** 及其渐变 / 透明度变体——彩色只出现在语义状态（成功/警告/危险），且克制使用
3. **PingFang SC 三档字重（400/500/600）+ 四级灰阶**建立信息层级——不靠字号堆叠，靠字重与灰度区分主次
4. **主按钮（渐变实心）与次按钮（浅蓝描边）形成操作优先级**——一屏最多一个主按钮

---

## 2. 设计令牌（Design Tokens · 复制即用）

```css
:root {
  /* ---------- 品牌主色 ---------- */
  --brand: #3f86ff;
  --brand-600: #2f74ee;          /* hover 按压 */
  --brand-700: #1f5fd6;          /* active */
  --brand-300: #8fb8ff;          /* 禁用 / 装饰 */
  --brand-100: #e2edff;          /* 选中背景 */
  --brand-50:  #f2f7ff;          /* hover 背景 */

  /* 品牌蓝渐变：135° 三段，用于实心主按钮 / 强调图标容器 / 分页选中 */
  --brand-grad:       linear-gradient(135deg, #5b9bff 0%, #3f86ff 48%, #2f74ee 100%);
  --brand-grad-hover: linear-gradient(135deg, #4d90ff 0%, #3f86ff 40%, #2568e0 100%);
  --brand-grad-soft:  linear-gradient(135deg, #eaf2ff 0%, #f6f9ff 100%);

  /* 透明度变体（描边、浅底、悬停态的黄金工具） */
  --brand-a04: rgba(63, 134, 255, .04);
  --brand-a08: rgba(63, 134, 255, .08);
  --brand-a12: rgba(63, 134, 255, .12);
  --brand-a20: rgba(63, 134, 255, .20);
  --brand-a32: rgba(63, 134, 255, .32);

  /* 品牌蓝投影：仅用于主按钮浮起，不给卡片用 */
  --brand-shadow:    0 6px 18px -6px rgba(63, 134, 255, .48);
  --brand-shadow-lg: 0 10px 28px -8px rgba(63, 134, 255, .52);

  /* ---------- 四级灰阶（信息层级） ---------- */
  --g1: #1a1a1a;   /* 一级：标题、核心数据 */
  --g2: #5c5c66;   /* 二级：正文、字段值 */
  --g3: #909099;   /* 三级：辅助说明、标签 */
  --g4: #c8c8d0;   /* 四级：占位、禁用、极弱信息 */

  /* ---------- 细线与底色 ---------- */
  --hair:        rgba(26, 26, 26, .07);   /* 0.5px 细线（主分隔） */
  --hair-strong: rgba(26, 26, 26, .11);   /* 稍强细线（表头/输入框） */
  --hair-brand:  rgba(63, 134, 255, .24);
  --bg: #f7f8fa;             /* 页面底：极浅灰 */
  --surface: #ffffff;        /* 卡片面：纯白 */
  --surface-sunken: #fafbfc; /* 卡片内嵌浅底（表头/输入底/分段条槽） */

  /* ---------- 语义色（克制使用） ---------- */
  --ok: #17b26a;      --ok-bg: rgba(23, 178, 106, .10);
  --warn: #f79009;    --warn-bg: rgba(247, 144, 9, .12);
  --danger: #f04438;  --danger-bg: rgba(240, 68, 56, .10);
  --info: #3f86ff;    --info-bg: var(--brand-a08);
  --muted: #909099;   --muted-bg: rgba(144, 144, 153, .12);

  /* ---------- 圆角 ---------- */
  --r-xs: 8px;   --r-sm: 10px;  --r-md: 14px;
  --r-lg: 18px;  --r-xl: 22px;  --r-full: 999px;

  /* ---------- 字重 ---------- */
  --fw-regular: 400;  --fw-medium: 500;  --fw-semibold: 600;

  /* ---------- 动效 ---------- */
  --ease: cubic-bezier(.22, .61, .36, 1);        /* 标准缓出 */
  --ease-spring: cubic-bezier(.34, 1.4, .5, 1);  /* 轻回弹 */
  --dur-fast: .16s;  --dur: .24s;  --dur-slow: .4s;

  /* ---------- 字体族 ---------- */
  --font: "PingFang SC", -apple-system, BlinkMacSystemFont, "HarmonyOS Sans SC",
          "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif;
  --font-num: "PingFang SC", "SF Pro Display", -apple-system, "DIN Alternate",
              "Helvetica Neue", Arial, sans-serif;
}

* { box-sizing: border-box; }
html, body {
  margin: 0; background: var(--bg); color: var(--g1);
  font-family: var(--font); font-size: 14px; line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}
```

---

## 3. 色彩使用规则

| 用途 | 取值 | 说明 |
|---|---|---|
| 页面背景 | `--bg #f7f8fa` | 永远是极浅灰，不用纯白 |
| 内容承载 | `--surface #fff` | 卡片、侧边栏、顶部通栏、表单区 |
| 品牌强调 | `--brand` / `--brand-grad` | 按钮、选中态、图标、链接 |
| 浅底强调 | `--brand-a08` | 选中背景、图标容器底、徽章 |
| 分隔 | `--hair`（0.5px） | 卡片边、列表分隔、表头下线 |
| 状态色 | ok / warn / danger | 只出现在状态胶囊、徽标、关键提示，且**一律配 10–12% 透明度浅底** |

**禁止**：大面积使用品牌蓝做背景、给卡片加灰色投影、使用纯黑 `#000` 正文。

---

## 4. 字体排印

| 角色 | 字号 / 字重 / 颜色 | 用途 |
|---|---|---|
| 页面大标题 | 21–23px · 600 · g1 | 页头主标题 |
| 区块标题 | 14.5–16px · 600 · g1 | 卡片头、分组名（`.sechead`） |
| 正文/字段值 | 14px · 400/500 · g1/g2 | 列表主文案、表单值 |
| 辅助说明 | 12–13px · 400 · g3 | 副标题、标签、时间 |
| 占位/极弱 | 11–12px · 400 · g4 | placeholder、空态、备注 |
| **指标数字** | 24–32px · 600 · g1 · `--font-num` | KPI，必须 `font-variant-numeric: tabular-nums` |

配套原子类：`.t-title / .t-body / .t-sub / .t-hint / .t-strong / .t-brand / .t-num`

---

## 5. 圆角与间距

**圆角阶梯**（从小到大，按组件尺寸取）：

- 输入框、小标签：`--r-xs 8px` / `--r-sm 10px`
- 图标容器（42–44px 方块）：`12–13px`（约边长 30%）
- 内嵌卡、列表项、上传框：`--r-md 14px`
- **主卡片、弹窗：`--r-lg 18px`**（标志性大圆角）
- 登录卡等特大容器：`--r-xl 22px`
- 按钮、胶囊、分段条：`--r-full 999px`（全圆角）

**间距**（8 的近似倍数）：

- 页面内容区内边距：`20px`（窄屏收缩，见第 10 节）
- 卡片内边距：`18–22px`（随断点收缩）
- 卡片间距 / 网格 gap：`10–12px`
- 组内元素 gap：`6–9px`
- 区块标题与内容间距：`10px`

---

## 6. 细线与阴影原则（本风格的灵魂）

- **分隔一律用 0.5px 发丝线** `border: .5px solid var(--hair)`，包括：卡片描边、列表分隔线、表头下划线、输入框描边、侧边栏与顶部通栏的边缘
- **卡片无阴影**。悬停反馈 = 描边变品牌色 + 轻微上浮，不是加深阴影
- 全系统只有两处允许投影：
  1. 主按钮：`--brand-shadow`（品牌蓝发光投影，hover 加深为 `--brand-shadow-lg`）
  2. 弹窗/Dialog：`0 24px 64px -20px rgba(15, 30, 60, .22)`

---

## 7. 核心组件规范

### 7.1 卡片 `.card`

```css
.card {
  background: var(--surface);
  border-radius: var(--r-lg);
  border: .5px solid var(--hair);
}
.card--hover:hover { border-color: var(--hair-brand); transform: translateY(-2px); }
```

卡片头 = 标题（15px·600·g1）+ 右侧操作，底部 0.5px 细线分隔；卡片体 padding `18px 22px`。

### 7.2 按钮（三级体系）

| 级别 | 样式 | 交互 |
|---|---|---|
| 主 `.btn--primary` | 品牌蓝渐变实心 + 白字 + `--brand-shadow` | hover 上浮 1.5px、投影加深；active `scale(.985)` |
| 次 `.btn--ghost` | 透明底 + `a32` 蓝描边 + 蓝字 | hover 填 `a08` 浅底、上浮 |
| 文字 `.btn--text` | 纯蓝字 | hover `a08` 浅底 |

统一：全圆角 `--r-full`、字重 500、默认高 36px（sm 30 / lg 46）。
**危险主按钮**用 `linear-gradient(135deg, #ff6a5e, #f04438)`，成功用 `#2fc97e → #17b26a`，各配同色系发光投影。

### 7.3 状态胶囊 `.pill`

高 22px、全圆角、12px 字、语义浅底 + 同色系字色 + 5px 圆点：
`pill--brand / --ok / --warn / --danger / --muted`（待定/中性用 muted）。

### 7.4 统一桌面布局框架（全部账号同一外壳）

**所有角色登录后进入同一个布局组件 `AdminLayout`，不允许再出现第二套导航形态。**

```
┌──────────────┬────────────────────────────────────┐
│ 侧边栏 aside  │ 顶部通栏 header（56px，白底 0.5px 底线）│
│ 220px / 64px │  折叠按钮 + 面包屑 …… 用户下拉        │
│ （折叠）       ├────────────────────────────────────┤
│ logo 区 56px  │                                    │
│ 首页（固定）   │        内容区 el-main               │
│ 动态菜单树     │        --bg 底，padding 20px        │
│ （按角色下发） │        <router-view />              │
└──────────────┴────────────────────────────────────┘
```

- **侧边栏**：白底、右侧 0.5px 细线；logo 区 = 28px 渐变方块图标 + 系统名（折叠时仅图标）；
  宽 220px，窗口 <1024px 默认折叠为 64px，折叠/展开有 `.24s` 过渡
- **菜单**：固定「首页」在最上方（`/admin/home`），其后为后端按角色下发的菜单树；
  只渲染目录（DIRECTORY）与页面（PAGE）节点，动作（ACTION）节点不进菜单；
  图标按 `componentKey` 白名单映射、未命中按菜单名关键字兜底
- **顶部通栏**：56px、白底、0.5px 底线；左侧折叠按钮 + 当前页面包屑；
  右侧用户下拉 = 28px 渐变圆形头像（姓名首字）+ 姓名，菜单项「修改密码 / 退出登录」，
  该下拉在所有页面保持一致，不得另造用户入口
- **内容区**：`--bg` 极浅灰底、padding 20px，页面卡片直接铺开，不再设居中最大宽度
- **路由**：业务页面全部由 `router/dynamic.js` 按菜单 `componentKey` 白名单挂到布局下；
  登录/设置密码页独立无框架

### 7.5 功能入口卡与列表

- **功能入口**（首页）：三列桌面网格 gap 12px（≤900px 收两列），卡片内 = `42×42 圆角 13px` 图标容器（语义浅底 + 同色系图标）+ 14.5px·500 主文案 + 12px·g3 副文案；hover 描边变蓝 + 上浮 2px，`active: scale(.98)`；未开放模块整卡置灰（opacity .55）
- **数据列表卡**：白卡内左图右文；图标方块 `44×44`；主行 15px·600 + 副行 12px·g3（用 `·` 分隔）
- 行内键值对：标签 11.5px·g3，值 13px·500·g1；多列均分 + 顶部 0.5px 细线

### 7.6 表单

- 标签：12.5px · 500 · g3，与输入框间距 7px
- 输入框：高 46px、`--r-md` 圆角、`hair-strong` 0.5px 描边、左图标（g4）
- focus：描边变 `--brand`、底色 `--brand-a04`、图标变蓝
- 分段控件（如登录页"账号密码/手机验证码"）：`surface-sunken` 槽 + 4px padding；选中项白底浮起 + 蓝字 + 轻投影，全圆角
- 单选/复选/开关：选中一律品牌蓝

### 7.7 流程步骤

- 横向 `.flow__node`：76px 最小宽节点卡 + 箭头（g4）；当前节点 `grad-soft` 底 + 蓝字
- 纵向步骤条：34×34 圆角方块节点（on 态 `a08` 底蓝图标），节点间 1.5px 竖线

### 7.8 弹层与反馈

- Dialog：`--r-lg` 大圆角，不要重阴影
- Toast/Message：全圆角胶囊 + 0.5px 细线 + 极浅投影
- 空态 `.empty`：60px 上下留白，图标 g4 + 13px·g3 文案；**接口未接入的列表一律展示真实空态，禁止填充虚构演示数据**
- 骨架屏 `.skeleton`：段落色 `surface-sunken` + 扫光动画

---

## 8. 图标规范

- 统一使用 `@element-plus/icons-vue`，尺寸常用 15–19px（按钮内 16–17，菜单 18）
- 不混用第二套图标库；颜色继承父级 `color`
- 语义图标容器 `.icon_box`：42–44px 圆角方块 + 10% 语义色浅底 + 同色系图标

---

## 9. 动效规范

| 场景 | 参数 |
|---|---|
| 标准过渡 | `all .24s cubic-bezier(.22,.61,.36,1)` |
| 按压反馈 | `active { transform: scale(.97–.985) }`，`.16s` |
| 悬停上浮 | `hover { transform: translateY(-2px) }`（按钮 -1.5px） |
| 光泽扫过 `.sweep` | 105° 白色渐变带，hover 时 0.7s 横扫（登录卡等点缀，每屏至多一处） |
| 弹层滑入 | 沿用组件库默认（0.3s），不改 |

尊重 `prefers-reduced-motion`：该偏好下关闭扫光与过渡动画。

---

## 10. 响应式断点

```
md 768 / lg 1024 / xl 1280 / 2xl 1600
```

- 侧边栏 <1024px 默认折叠为 64px；<768px 顶部通栏隐藏用户名
- 指标数字随断点缩放：30px（默认）→ 27（≤1440）→ 24（≤1280）→ 22（≤1024/768）
- 卡片 padding 同步收缩：22px → 18px（≤1280）→ 16px（≤1024）→ 14px（≤768）
- 表格窄屏：字号 12.5px + 横向滚动容器 `.table-scroll-x`
- 通用自适应网格：`.grid-responsive { repeat(auto-fit, minmax(220px, 1fr)) }`

> 本项目按桌面使用场景设计，不针对手机宽度做专门适配；以上断点仅保证桌面窗口缩放与较小显示器下不破坏布局。

---

## 11. 组件库映射（Element Plus 变量覆盖清单）

```css
--el-color-primary: var(--brand);
--el-text-color-primary/regular/secondary: var(--g1/g2/g3);
--el-border-color*: var(--hair-strong) / var(--hair);
--el-border-radius-base: var(--r-xs);
--el-box-shadow-light: none;
```

另需精修：卡片去阴影加大圆角；表格表头 `surface-sunken` 底 + g3 字、行 hover `a04`；tag 全圆角语义浅底；pagination 选中 = 渐变 + 品牌投影；菜单无右边线。

---

## 12. 页面骨架模板

### 统一布局内页面（唯一骨架）

```
el-main（--bg 底，padding 20px）
└── 页面容器（flex 纵向，gap 18px）
    ├── 页头：标题(21–23px·600) + t-sub 副标题（+ 右侧操作按钮）
    ├── 工具卡 .card：搜索/筛选表单（inline） + 主按钮（一屏最多一个）
    └── 数据卡 .card：el-table（细线表，外套 .table-scroll-x） → 底部分页
```

首页在统一骨架内使用：问候页头 → 三列功能入口网格（7.5） → 待办/记录双列卡（空态优先）。

---

## 13. Do / Don't 速查

| ✅ Do | ❌ Don't |
|---|---|
| 全部账号共用 AdminLayout 一套外壳 | 按角色另做导航布局、第二套首页 |
| 0.5px 发丝线做分隔 | 卡片灰色投影、1px 实色边框 |
| 单一品牌蓝 + 透明度变体 | 第二个强调色、彩色标题 |
| 渐变只用于：主按钮/图标容器/选中页码 | 给普通卡片、标签、页面背景用渐变 |
| 全圆角按钮与胶囊 | 直角或 4px 小圆角按钮 |
| 四级灰阶表达层级 | 纯黑正文、超过 4 档灰 |
| `active: scale(.98)` 按压反馈 | 花哨入场动画、弹跳 easing |
| 数字用 `tabular-nums` 等宽 | 数字抖动换行 |
| 接口未接入展示真实空态 | 虚构演示数据、占位假列表 |

---

*v2.0（2026-10-15）：桌面端统一化定稿。前身 v1.1 提炼自"智联教室"项目主题，H5 相关内容已全部移除。*

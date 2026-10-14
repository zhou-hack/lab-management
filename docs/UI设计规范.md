# 智联教室 · UI 设计规范（定稿 v1.1）

> 本文档从"智联教室"项目前端提炼，作为其他项目的 UI 设计基座。
> 全部数值均可直接落地为 CSS，配套设计令牌见第 2 节，复制即用。

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

  /* 品牌蓝渐变：135° 三段，用于实心主按钮 / 强调区块 / Tabs 指示条 / 页头 */
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
| 内容承载 | `--surface #fff` | 卡片、导航栏、表单区 |
| 品牌强调 | `--brand` / `--brand-grad` | 按钮、选中态、图标、链接、Tabs 指示条 |
| 浅底强调 | `--brand-a08` | 选中背景、图标容器底、徽章 |
| 分隔 | `--hair`（0.5px） | 卡片边、列表分隔、表头下线 |
| 状态色 | ok / warn / danger | 只出现在状态胶囊、徽标、关键提示，且**一律配 10–12% 透明度浅底** |

**禁止**：大面积使用品牌蓝做背景（仅页头 hero 允许）、给卡片加灰色投影、使用纯黑 `#000` 正文。

---

## 4. 字体排印

| 角色 | 字号 / 字重 / 颜色 | 用途 |
|---|---|---|
| 页面大标题 | 21–23px · 600 · g1 | 页头主标题、教室编号 |
| 区块标题 | 14.5–16px · 600 · g1 | 卡片头、导航栏标题、分组名（`.sechead`） |
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
- **主卡片、弹窗、底部弹层：`--r-lg 18px`**（标志性大圆角）
- 登录卡等特大容器：`--r-xl 22px`
- 按钮、胶囊、分段条：`--r-full 999px`（全圆角）

**间距**（8 的近似倍数）：

- 页面左右边距：H5 `14–16px`；桌面 `18–24px`
- 卡片内边距：`18–22px`（随断点收缩，见第 10 节）
- 卡片间距 / 网格 gap：`10–12px`
- 组内元素 gap：`6–9px`
- 区块标题与内容间距：`10px`

---

## 6. 细线与阴影原则（本风格的灵魂）

- **分隔一律用 0.5px 发丝线** `border: .5px solid var(--hair)`，包括：卡片描边、列表分隔线、表头下划线、输入框描边
- **卡片无阴影**。悬停反馈 = 描边变品牌色 + 轻微上浮，不是加深阴影
- 全系统只有三处允许投影：
  1. 主按钮：`--brand-shadow`（品牌蓝发光投影，hover 加深为 `--brand-shadow-lg`）
  2. 弹窗/Dialog：`0 24px 64px -20px rgba(15, 30, 60, .22)`
  3. 手机外壳机身（演示用，见第 11 节）

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

统一：全圆角 `--r-full`、字重 500、默认高 36px（sm 30 / lg 46）、块级 `width:100%`。
**危险主按钮**用 `linear-gradient(135deg, #ff6a5e, #f04438)`，成功用 `#2fc97e → #17b26a`，各配同色系发光投影。

### 7.3 状态胶囊 `.pill`

高 22px、全圆角、12px 字、语义浅底 + 同色系字色 + 5px 圆点：
`pill--brand / --ok / --warn / --danger / --muted`（待定/中性用 muted）。

### 7.4 页头 Hero（H5 标志性版式）

```
┌─────────────────────────┐
│ 蓝色渐变区 (160deg)       │  ← linear-gradient(160deg, #5b9bff, #3f86ff)
│ 返回箭头  页面标题(白)     │     白字，padding-bottom 多留 28–44px
│ 一行 12px 半透明白副标题   │
└──────────╮ ╭────────────┘
           ╲╱  ← 首张卡片 margin-top: -28px ~ -42px 上叠
```

- 顶部通栏渐变：`linear-gradient(160deg, #5b9bff 0%, #3f86ff 100%)`，白字
- 首页变体：渐变头部 + 头像/账号信息 + 右上角通知铃铛（白 12% 底圆钮 + 红点徽标）
- **首卡上叠**：第一张内容卡片用负 margin 压进渐变区底部，形成层次
- 无 hero 的子页面用白底导航栏：48–50px 高、16px·600 居中标题、0.5px 底线

### 7.5 列表项 / 功能入口卡

- **功能入口**（首页宫格）：两列网格 gap 11px，卡片内 = `42×42 圆角 13px` 图标容器（语义浅底 + 同色系图标）+ 14.5px·500 主文案 + 11.5px·g3 副文案；`active: scale(.98) + a04 浅底`
- **数据列表卡**：白卡内左图右文；图标方块 `44×44`（实验室场景用渐变蓝底白字编号）；主行 15px·600 + 副行 12px·g3（用 `·` 分隔）；右侧 `›` 箭头（g4）
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

- 底部弹层/Dialog：`--r-lg` 大圆角，不要重阴影
- Toast/Message：全圆角胶囊 + 0.5px 细线 + 极浅投影
- 空态：60px 上下留白，图标 g4 + 13px·g3 文案
- 骨架屏：段落色 `surface-sunken`

---

## 8. 图标规范

- **内联 SVG，24×24 画布，`stroke` 描边风格**：`stroke-width 1.6`、圆角线帽/线接、`currentColor` 取色（颜色继承父级 `color`）
- 不使用面性图标、不混用图标库；尺寸常用 15–19px（按钮内 16–17，导航 19）
- 语义图标容器：42–44px 圆角方块 + 10% 语义色浅底 + 同色系图标

---

## 9. 动效规范

| 场景 | 参数 |
|---|---|
| 标准过渡 | `all .24s cubic-bezier(.22,.61,.36,1)` |
| 按压反馈 | `active { transform: scale(.97–.985) }`，`.16s` |
| 悬停上浮 | `hover { transform: translateY(-2px) }`（按钮 -1.5px） |
| 光泽扫过 `.sweep` | 105° 白色渐变带，hover 时 0.7s 横扫 |
| 浅卡浮光 `.glow` | 右上角 180px 品牌蓝径向光斑，hover 淡入 |
| 弹层滑入 | 沿用组件库默认（0.3s），不改 |

**触摸设备**（`hover: none, pointer: coarse`）：移除一切 hover 上浮，避免误触错觉；可点区域 ≥36px。

---

## 10. 响应式断点

```
sm 480 / md 768 / lg 1024 / xl 1280 / 2xl 1600 / 1920
```

- 指标数字随断点缩放：30px（默认）→ 27（≤1440）→ 24（≤1280）→ 22（≤1024/768）→ 19（≤480）
- 卡片 padding 同步收缩：22px → 18px（≤1280）→ 16px（≤1024）→ 14px（≤768）
- 表格窄屏：字号 12.5px + 横向滚动容器 `.table-scroll-x`
- 通用自适应网格：`.grid-responsive { repeat(auto-fit, minmax(220px, 1fr)) }`
- iPhone 底部安全区：`padding-bottom: max(env(safe-area-inset-bottom), 12px)`

---

## 11. H5 手机模拟器外壳（演示神器）

宽屏（≥768px）把 body 变成 **390×844 圆角手机屏幕**，居中悬浮于深蓝舞台；真机自动恢复全屏。

```css
@media (min-width: 768px) {
  html {  /* 舞台：深蓝渐变 + 品牌蓝光晕 */
    height: 100%; display: flex; align-items: center; justify-content: center;
    overflow: hidden;
    background:
      radial-gradient(1100px 700px at 72% 18%, rgba(63,134,255,.22), transparent 62%),
      radial-gradient(900px 650px at 18% 85%, rgba(31,95,214,.26), transparent 60%),
      linear-gradient(158deg, #0c1f42 0%, #122c58 46%, #0a1730 100%);
  }
  body {  /* 机身：body 即屏幕 */
    width: 390px; height: min(844px, calc(100vh - 48px));
    padding-top: 44px;            /* 状态栏让位 */
    border-radius: 46px; overflow: hidden;
    transform: translateZ(0);     /* 关键：fixed 弹层约束进机身 */
    box-shadow:
      0 0 0 11px #141419,                             /* 深色边框 */
      0 0 0 12.5px rgba(255,255,255,.07),             /* 边框高光 */
      0 44px 90px -26px rgba(2,8,26,.85),             /* 落地投影 */
      0 0 130px -18px rgba(63,134,255,.48);           /* 品牌蓝光晕 */
  }
  body::before { /* 灵动岛：112×30 黑条，pointer-events:none，z-index 5000 */ }
  body::after  { /* 状态栏 "9:41" 时间，左上角 */ }
  #app { height: 100%; overflow-y: auto; scrollbar-width: none; }  /* 屏内滚动 */
  .van-popup--right, .van-popup--left { padding-top: 44px; }       /* 侧滑弹层让位 */
}
```

**三个落地要点**（都是踩过的坑）：
1. `transform: translateZ(0)` 让 body 成为 fixed 元素包含块——组件库 teleport 到 body 的弹窗/Toast 才不会弹出全屏
2. `padding-top: 44px` 预留状态栏，灵动岛和时间用伪元素 + `pointer-events: none`，不挡操作
3. 页面内部禁止 `min-height: 100vh`（vh 是窗口高度不是机身高度），改 `min-height: 100%`；也**不要用 `min-width` 媒体查询做多列网格**——媒体查询认窗口宽度，机身内会误触发

---

## 12. 组件库映射（变量覆盖清单）

**Vant 4（H5）**：
```css
--van-primary-color: var(--brand);
--van-text-color / -2 / -3: var(--g1 / g2 / g3);
--van-background: var(--bg);  --van-background-2: var(--surface);
--van-border-color: var(--hair);
--van-radius-lg: var(--r-md);  --van-button-border-radius: var(--r-full);
--van-nav-bar-height: 48px;
```
另需精修：Tabs 指示条 = `--brand-grad` 3px 全圆角；primary 按钮 = 渐变 + `--brand-shadow`；cell-group--inset = `--r-lg` + 0.5px 描边；popup/dialog = `--r-lg`。

**Element Plus（管理端）**：
```css
--el-color-primary: var(--brand);
--el-text-color-primary/regular/secondary: var(--g1/g2/g3);
--el-border-color*: var(--hair-strong) / var(--hair);
--el-border-radius-base: var(--r-xs);
--el-box-shadow-light: none;
```
另需精修：卡片去阴影加大圆角；表格表头 `surface-sunken` 底 + g3 字、行 hover `a04`；tag 全圆角语义浅底；pagination 选中 = 渐变 + 品牌投影；菜单无右边线。

---

## 13. 页面骨架模板

### H5 子页面（hero 版）

```html
<div class="page">            <!-- min-height:100%; background: var(--bg) -->
  <header class="hero">       <!-- 蓝渐变：160deg；白字；padding: 16px 18px 36px -->
    <div class="hero__bar">
      <button class="iconback">‹</button>   <!-- 30×30 透明，白字 -->
      <b>页面标题</b>
      <button class="hero__act">+ 新建</button>  <!-- 白12%底 + 白25%描边胶囊 -->
    </div>
    <p class="hero__sub">一行半透明副标题（rgba(255,255,255,.84)）</p>
  </header>
  <div class="card main">     <!-- margin: -28px 14px 0 上叠 -->
    …
  </div>
</div>
```

### 管理端页面

```
页面底 --bg → 区块标题(14.5px·600) → .card 白卡(18px 圆角+细线)
→ 卡内：工具行(搜索/筛选 + 主按钮) → el-table(细线表) → 底部分页
```

---

## 14. Do / Don't 速查

| ✅ Do | ❌ Don't |
|---|---|
| 0.5px 发丝线做分隔 | 卡片灰色投影、1px 实色边框 |
| 单一品牌蓝 + 透明度变体 | 第二个强调色、彩色标题 |
| 渐变只用于：主按钮/hero/Tabs 条/选中页码 | 给普通卡片、标签用渐变 |
| 全圆角按钮与胶囊 | 直角或 4px 小圆角按钮 |
| 四级灰阶表达层级 | 纯黑正文、超过 4 档灰 |
| `active: scale(.98)` 按压反馈 | 花哨入场动画、弹跳 easing |
| 数字用 `tabular-nums` 等宽 | 数字抖动换行 |
| 触摸端移除 hover 上浮 | 手机上保留悬停态 |

---

*来源：智联教室项目 `frontend/src/shared/theme.css` + `phone-frame.css` 及两端页面实现，2026-10 定稿。*

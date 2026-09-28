# 多级分销（前端）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 3 个 vben 后台 + 会员端 uniapp 的分销配置从「写死两级」改为「N 级可配置」，对接已完成的后端契约。

**Architecture:** 后端已把层级规则改为 JSON 数组（层级数=数组长度）。后台用 vben 原生的 `type: 'array'` 表单字段渲染动态层级表格；商品 SKU 沿用「元输入、分存储」的既有换算。会员端只放宽层级选项。

**Tech Stack:** Vue 3、vben admin（`VbenFormSchema` + `FormArraySchema`）、uni-app（Vue 3）、TypeScript。

**Spec:** `ruoyi-vue-pro/docs/superpowers/specs/2026-09-18-multilevel-brokerage-design.md`
**后端计划（前置，已完成）:** `ruoyi-vue-pro/docs/superpowers/plans/2026-09-18-multilevel-brokerage-backend.md`

## Global Constraints

- 后端契约（**已实现，勿改**）：
  - `TradeConfigBaseVO.brokerageLevels`: `[{ level: number, percent: number, fixedPrice: number }]`
    - `percent` = 百分比数值，`10` = 10%，允许小数
    - `fixedPrice` = **单位分**，单件商品固定佣金
  - `ProductSkuSaveReqVO.brokerageLevels` / `ProductSkuRespVO.brokerageLevels`: 同结构
  - 旧的 `brokerageFirstPercent` / `brokerageSecondPercent` / `firstBrokeragePrice` / `secondBrokeragePrice` **后端已删除**，前端继续传会被忽略
- 层级数上限 `MAX_LEVEL = 10`，前后端都要拦。
- 商品 `subCommissionType = true` 时必须配齐与全局**相同层级数**的规则。
- 后台金额一律「元输入、分存储」：参考现有 `brokerageWithdrawMinPrice` 与 `firstBrokeragePrice` 的 `formatToFraction` / `convertToInteger` 换算（×100 / ÷100）。
- 三套后台（`web-antd`、`web-ele`、`web-antdv-next`）文件结构基本一致，**改动必须三套同步**，否则后台行为不一致。
- vben 表单原生支持数组字段：`type: 'array'` + `children` + `arrayProps`，渲染为 `VbenFormFieldArray`（`packages/@core/ui-kit/form-ui/src/components/form-field-array.vue`）。**不要自己写数组组件**。
- 会员端 `yudao-mall-uniapp` 与后台 `yudao-ui-admin-vben` 是**两个独立的 git 仓库**，分别在各自目录提交。

---

## File Structure

**后台（每套 app 各一份，共 3 套）**

| 文件 | 职责 |
| --- | --- |
| `src/api/mall/trade/config/index.ts` | 配置接口类型：两个 percent → `brokerageLevels` |
| `src/views/mall/trade/config/data.ts` | 配置表单：两个输入框 → `type: 'array'` 层级表格 |
| `src/api/mall/product/spu/index.ts` | SKU 类型：两个固定价 → `brokerageLevels` |
| `src/views/mall/product/spu/form/index.vue` | SKU 表单初始化/提交换算 |
| `src/views/mall/product/spu/components/sku-list.vue` | SKU 表格的佣金列 |
| `src/views/mall/product/spu/components/spu-select.vue` | 选中 SPU 时的分→元换算 |

**会员端（`yudao-mall-uniapp`）**

| 文件 | 职责 |
| --- | --- |
| `sheep/api/trade/brokerage.js` | 层级相关接口参数放宽 |
| `pages/commission/team.vue` | 下级列表层级 tab 由 1/2 改为 1..N |
| `pages/commission/{index,goods}.vue` | 佣金展示按新结构 |

---

## Task 1: 后台配置页——动态层级表格（3 套 app）

**Files:**
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/api/mall/trade/config/index.ts`
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/trade/config/data.ts`

**Interfaces:**
- Consumes: 后端 `TradeConfigBaseVO.brokerageLevels`
- Produces: 配置页能提交 `brokerageLevels`

- [ ] **Step 1: 改类型定义（3 个文件）**

每个 `src/api/mall/trade/config/index.ts`，把：

```ts
  brokerageFirstPercent?: number;
  brokerageSecondPercent?: number;
```

替换为：

```ts
  brokerageLevels?: {
    level: number;
    percent: number;
    fixedPrice: number;
  }[];
```

- [ ] **Step 2: 改表单 schema（3 个文件）**

每个 `src/views/mall/trade/config/data.ts`，删除 `brokerageFirstPercent`（原 144-159 行附近）与 `brokerageSecondPercent`（原 161-176 行附近）两个字段对象，替换为**一个**数组字段：

```ts
  {
    fieldName: 'brokerageLevels',
    label: '分销层级',
    type: 'array',
    arrayProps: {
      min: 1,
      max: 10,
      addButtonText: '添加层级',
      showIndex: true,
    },
    help: '层级数量即分销级数，最多 10 级。返佣 = 订单金额 × 比例 + 固定佣金 × 购买数量',
    dependencies: {
      triggerFields: ['type'],
      show: (values) => values.type === 'brokerage',
    },
    children: [
      {
        fieldName: 'percent',
        label: '返佣比例（%）',
        component: 'InputNumber',
        componentProps: { min: 0, max: 100, precision: 2, class: 'w-full' },
      },
      {
        fieldName: 'fixedPrice',
        label: '固定佣金（元）',
        component: 'InputNumber',
        componentProps: { min: 0, precision: 2, class: 'w-full' },
      },
    ],
  },
```

- [ ] **Step 3: 处理元/分换算**

`brokerageLevels[].fixedPrice` 后端为**分**，前端按**元**输入。参照同文件 `brokerageWithdrawMinPrice`（同为「元输入、分存储」）的既有做法：
- 读取回填时：`fixedPrice` ÷ 100
- 提交前：`fixedPrice` × 100

若该页用的是统一的 form value transform（先在 `src/views/mall/trade/config/index.vue` 中确认），把换算挂到那里；否则在 `index.vue` 的提交/回填钩子里做。**`percent` 不需要换算**（前后端都是百分比数值）。

- [ ] **Step 4: 验证**

Run: `pnpm --filter @vben/web-antd dev`（在 `yudao-ui-admin-vben` 目录；web-ele / web-antdv-next 同理）
Expected: 分销配置页出现「分销层级」动态表格，可增删行；保存后刷新，层级与数值保持。

- [ ] **Step 5: 提交**

```bash
cd yudao-ui-admin-vben
git add apps/web-antd/src/api/mall/trade/config/index.ts apps/web-antd/src/views/mall/trade/config/data.ts \
        apps/web-ele/src/api/mall/trade/config/index.ts apps/web-ele/src/views/mall/trade/config/data.ts \
        apps/web-antdv-next/src/api/mall/trade/config/index.ts apps/web-antdv-next/src/views/mall/trade/config/data.ts
git commit -m "feat(mall): 分销配置页支持 N 级层级规则"
```

---

## Task 2: 后台商品 SKU——N 级佣金（3 套 app）

**Files:**
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/api/mall/product/spu/index.ts`
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/product/spu/form/index.vue`
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/product/spu/components/sku-list.vue`
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/product/spu/components/spu-select.vue`

**Interfaces:**
- Consumes: 后端 `ProductSkuSaveReqVO.brokerageLevels` / `ProductSkuRespVO.brokerageLevels`
- Produces: 商品保存时提交 `brokerageLevels`

**说明（本计划唯一的设计取舍）：** SKU 表格里原本是「一级佣金 / 二级佣金」两个固定列。层级数现在是动态的，**不做 N 个动态列**（列数不可预知、表格会很宽）；改为：表格内保留一个「分销佣金」单元格，显示已配层级数（如「已配 3 级」），点击打开一个弹窗，弹窗里用与 Task 1 相同的 `type: 'array'` 表单配置该 SKU 的各层级规则。这样三套 app 的 SKU 表格布局不变，改动面最小。

- [ ] **Step 1: 改类型（3 个文件）**

每个 `src/api/mall/product/spu/index.ts`，把：

```ts
  firstBrokeragePrice?: number | string; // 一级分销的佣金
  secondBrokeragePrice?: number | string; // 二级分销的佣金
```

替换为：

```ts
  brokerageLevels?: {
    level: number;
    percent: number;
    fixedPrice: number;
  }[];
```

- [ ] **Step 2: 改 `form/index.vue` 的初始化与提交换算（3 个文件）**

该文件现有 6 处涉及两个旧字段（初始化默认值 ×2、回填 ×2、提交换算 ×2、清空 ×2，见 grep 行号 63/64、213/214、251/252、288/289、309/310）。逐处改为操作 `brokerageLevels` 数组：
- 初始化：`brokerageLevels: []`
- 回填（后端 → 表单，分 → 元）：`fixedPrice` ÷ 100
- 提交（表单 → 后端，元 → 分）：`fixedPrice` × 100
- 切换 `subCommissionType` 关闭时：清空为 `[]`

- [ ] **Step 3: 改 `sku-list.vue` 的佣金列（3 个文件）**

把原有的两个 `<InputNumber v-model="row.firstBrokeragePrice">` / `row.secondBrokeragePrice`（行 428/440 附近）与详情展示（行 575/580 附近）替换为**一个**「分销佣金」单元格：显示 `row.brokerageLevels?.length ? \`已配 ${row.brokerageLevels.length} 级\` : '未配置'`，点击打开层级配置弹窗（Task 2 Step 4 的组件）。

- [ ] **Step 4: 新增层级配置弹窗（3 个文件）**

在 `src/views/mall/product/spu/components/` 下新增 `sku-brokerage-levels-modal.vue`，内部用 vben 表单渲染与 Task 1 Step 2 **完全相同**的 `type: 'array'` 字段（`children` 为 `percent` + `fixedPrice`，`arrayProps.max = 10`），把结果写回当前行 SKU 的 `brokerageLevels`。

- [ ] **Step 5: 改 `spu-select.vue` 的分→元换算（3 个文件）**

把行 151-155 附近针对 `firstBrokeragePrice` / `secondBrokeragePrice` 的 `× 100` 换算，改为对 `brokerageLevels[].fixedPrice` 逐项换算。

- [ ] **Step 6: 校验商品层级数**

商品保存前校验：`subCommissionType === true` 时 `brokerageLevels.length` 必须等于全局层级数（从配置接口读取）。不匹配时用 `message.error('商品独立分销的层级数必须与全局一致，当前应为 N 级')` 拦截（后端也会拦，这里是提前反馈）。

- [ ] **Step 7: 验证**

Run: `pnpm --filter @vben/web-antd dev`
Expected: 开启「独立分销」后可配置 N 级佣金；保存后重新打开商品，层级与金额保持；元/分换算无偏差（例：填 2 元 → 库里 200 分）。

- [ ] **Step 8: 提交**

```bash
cd yudao-ui-admin-vben
git add apps/web-antd/src/api/mall/product/spu apps/web-antd/src/views/mall/product/spu \
        apps/web-ele/src/api/mall/product/spu apps/web-ele/src/views/mall/product/spu \
        apps/web-antdv-next/src/api/mall/product/spu apps/web-antdv-next/src/views/mall/product/spu
git commit -m "feat(mall): 商品 SKU 支持 N 级分销佣金"
```

---

## Task 3: 后台分销用户列表——层级筛选放宽（3 套 app）

**Files:**
- Modify: `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/trade/brokerage/user/data.ts`
- Modify: 同目录 `index.vue`（若层级选项写死在页面里）

**Interfaces:**
- Consumes: 后端 `BrokerageUserPageReqVO.level`（无上限）、`BROKERAGE_USER_LEVEL_NOT_SUPPORT` 文案已带 `{}`
- Produces: 层级下拉支持 1..N

- [ ] **Step 1: 把层级筛选项由写死 1/2 改为按全局层级数生成**

先 grep 确认层级选项的写法：

```bash
cd yudao-ui-admin-vben
grep -rn "level" apps/web-antd/src/views/mall/trade/brokerage/user/data.ts
```

若选项是写死的 `[{label:'一级',value:1},{label:'二级',value:2}]`，改为从配置接口读 `brokerageLevels.length` 后生成：

```ts
options: Array.from({ length: levelCount }, (_, i) => ({
  label: i === 0 ? '一级' : `${i + 1} 级`,
  value: i + 1,
})),
```

- [ ] **Step 2: 验证**

Run: `pnpm --filter @vben/web-antd dev`
Expected: 分销用户列表的层级筛选出现 1..N 个选项，与配置页层级数一致。

- [ ] **Step 3: 提交**

```bash
cd yudao-ui-admin-vben
git add apps/web-antd/src/views/mall/trade/brokerage apps/web-ele/src/views/mall/trade/brokerage apps/web-antdv-next/src/views/mall/trade/brokerage
git commit -m "feat(mall): 分销用户列表层级筛选支持 N 级"
```

---

## Task 4: 会员端 uniapp——层级选项与佣金展示

**Files:**
- Modify: `yudao-mall-uniapp/sheep/api/trade/brokerage.js`
- Modify: `yudao-mall-uniapp/pages/commission/team.vue`
- Modify: `yudao-mall-uniapp/pages/commission/index.vue`、`goods.vue`

**Interfaces:**
- Consumes: 后端 App 接口（`AppBrokerageUserChildSummaryPageReqVO.level` 已放宽为 `@Min(1)`）
- Produces: 会员端可按 1..N 查看下级

- [ ] **Step 1: `team.vue` 层级 tab 由写死 1/2 改为动态**

现状（`pages/commission/team.vue`）：第 50-53 行两个写死的 tab（`state.level == 1` / `== 2`），第 285 行 `level: 1`，第 324 行 `state.level = e + ''`。
改为：tab 列表由全局层级数生成（层级数可从分销中心接口或新增的配置接口读取），`setType(n)` 传 `n` 而非硬编码。

- [ ] **Step 2: `brokerage.js` 放宽层级参数**

若层级参数有类型/枚举约束，放宽为 `number`（后端已改为 `@Min(1)`）。

- [ ] **Step 3: `index.vue` / `goods.vue` 佣金展示**

这两页展示「可赚佣金」。后端 `calculateProductBrokeragePrice` 现在返回的是**第 1 级**（浏览者是直属上级）的佣金；字段名未变（`brokerageMinPrice` / `brokerageMaxPrice`），因此**通常无需改动**——先运行确认，若展示正常则不动，避免无谓改动。

- [ ] **Step 4: 验证**

Run: 用 HBuilderX 运行到 H5（该项目无 CLI build 脚本，`package.json` 只有 prettier）
Expected: 分销中心下级列表出现 1..N 个层级 tab；切换后列表按层级刷新；商品页可赚佣金数值正确。

- [ ] **Step 5: 格式检查与提交**

```bash
cd yudao-mall-uniapp
npx prettier --write "pages/commission/**/*.vue" "sheep/api/trade/brokerage.js"
git add pages/commission sheep/api/trade/brokerage.js
git commit -m "feat(commission): 分销下级层级支持 N 级"
```

---

## Self-Review

**1. Spec coverage**

| Spec §5 要求 | 覆盖任务 |
| --- | --- |
| 后台 config（×3 app）动态层级表格 | Task 1 |
| 后台商品 SKU（×3 app）N 级 | Task 2 |
| 后台分销用户列表层级筛选 1..N | Task 3 |
| 会员端 uniapp 分销中心/下级列表 | Task 4 |

**2. Placeholder scan:** 无 TBD/TODO。Task 3 Step 1 与 Task 4 Step 2 需要先 grep/运行确认现有写法后再改，已给出具体命令与目标形态；Task 4 Step 3 明确说明「若展示正常则不改」，这是刻意的克制而非占位。

**3. Type consistency:** `brokerageLevels` 的元素结构 `{ level: number; percent: number; fixedPrice: number }` 在 Task 1/2 的 API 类型与表单 fieldName（`percent` / `fixedPrice`）中一致；`level` 由行序推导（`arrayProps.showIndex`）在 Task 1/2 中一致；`MAX_LEVEL = 10` 在 `arrayProps.max` 与 Task 2 Step 6 的校验中一致。

**4. 跨计划一致性:** 本计划依赖的 4 个后端契约字段（`brokerageLevels` ×4 处）均已在后端计划中实现并测试通过，无占位依赖。

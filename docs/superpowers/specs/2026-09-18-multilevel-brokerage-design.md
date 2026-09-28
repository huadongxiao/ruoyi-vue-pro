# 商城多级分销（N 级可配置）设计

## 背景

芋道商城当前只支持**二级分销**。"2" 被硬编码在四处：

| # | 位置 | 说明 |
| --- | --- | --- |
| 1 | `trade_config.brokerage_first_percent` / `brokerage_second_percent` | 全局返佣比例，两个固定列（`TradeConfigDO`） |
| 2 | `BrokerageRecordServiceImpl.addBrokerage()` | 手工走两级；私有 `addBrokerage(...)` 对 `level > 2` 抛 `IllegalArgumentException` |
| 3 | `product_sku.first_brokerage_price` / `second_brokerage_price` | 商品独立分销的固定佣金，两个固定列 |
| 4 | `BrokerageUserServiceImpl.getChildUserIdsByLevel()` | 下级查询只支持 1/2 级，其余抛 `BROKERAGE_USER_LEVEL_NOT_SUPPORT` |

有利条件：

- `trade_brokerage_user.bind_user_id` 只保存**直属上级**，本身就是一条链表，深度不受限。
- `trade_brokerage_record.source_user_level` 已是 `Integer`，`source_user_id` 已是 `Long`，均与层级数无关。
- 佣金的冻结、解冻、取消、提现、统计逻辑全部与层级数无关，无需改动。

因此本设计只替换「规则的存储方式」和「走几级」这两件事。

## 目标

- 后台可配置分销层级数 N，`1 ≤ N ≤ MAX_LEVEL(10)`。
- 每一级独立配置「返佣比例 %」+「固定金额（分）」，最终佣金 = `floor(基数 × 比例 / 100) + 固定金额`（**相加**）。
- 商品开启独立分销（`product_spu.sub_commission_type = true`）时，必须配齐当前 N 级的比例 + 固定金额（**全量覆盖**，不允许部分覆盖）。
- 链路中某个上级没有分销资格时：**跳过该人、继续往上找，且层号不压缩**——第 k 个上级永远使用第 k 级规则。
- 全端改造：后端 + 3 个 vben 后台（web-antd / web-ele / web-antdv-next）+ 会员端 uniapp。
- 存量二级数据在改造后行为**逐分不变**。

## 非目标

- 不新建分销层级的关系表（采用 JSON 列，见「方案」）。
- 不改变佣金的冻结/解冻/取消/提现/统计逻辑。
- 不改变 `trade_brokerage_user` 的绑定关系模型（链表保持）。
- 不引入「商品维度独立层级数」——商品的层级数恒等于全局层级数。
- 不改动提现手续费、提现方式、分佣模式（人人/指定）、绑定模式等既有字段。
- 不做破坏性删列；旧列保留一个版本以便回滚。

## 已确认的需求决策

| 决策 | 结论 |
| --- | --- |
| 每级规则形态 | 比例 % 与固定金额**同时可配** |
| 两者组合方式 | **相加**：`floor(基数 × 比例/100) + 固定金额` |
| 商品独立分销 | **全量覆盖**，必须配齐所有层级 |
| 无资格上级 | **跳级但不压缩层号** |
| 改动范围 | 后端 + 3 个 vben 后台 + 会员端 uniapp |
| 存储方案 | JSON 配置列（方案 A） |

## 方案

### 1. 数据结构

**`trade_config` 新增列 `brokerage_levels`（JSON）**

```json
[
  {"level": 1, "percent": 10,  "fixedPrice": 0},
  {"level": 2, "percent": 5,   "fixedPrice": 200},
  {"level": 3, "percent": 2.5, "fixedPrice": 0}
]
```

**`product_sku` 新增列 `brokerage_levels`（JSON）**，结构相同。

字段语义：

- `level`：层级序号，从 1 开始、连续。
- `percent`：**百分比数值**，`10` 表示 10%，允许小数（顺带解除现有 `Integer` 只能填整数比例的限制）。单位与旧 `brokerage_first_percent` 完全一致，迁移零换算。
- `fixedPrice`：**单位分**，**单件商品**的固定佣金。结算时乘以购买数量（沿用现有 `first/second_brokerage_price × count` 的语义）。
- **层级数 = 数组长度**，数组顺序即层级顺序。

`ProductSkuDO` 的 `@TableName` 需增加 `autoResultMap = true`，否则 `JacksonTypeHandler` 不生效。

### 2. 共享类型

新增值对象 `BrokerageLevelRule`（`level` / `percent` / `fixedPrice`）。

放置位置：`cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule`，位于 `yudao-module-product`。

理由：仓库中不存在独立的 `yudao-module-product-api` 模块，`ProductSkuRespDTO` 本身就位于 `yudao-module-product` 的 `api` 包内，且 `yudao-module-trade` 已在 `pom.xml` 直接依赖 `yudao-module-product`。同时现有 `ProductSkuRespDTO` 已经暴露 `firstBrokeragePrice` / `secondBrokeragePrice`，把分销规则类型放在同一位置与既有设计一致。

该类型被以下位置共用：

- `ProductSkuDO`、`ProductSkuRespDTO`、`ProductSkuSaveReqVO`、`ProductSkuRespVO`
- `TradeConfigDO`
- `BrokerageAddReqBO`

### 3. 分佣计算（核心）

`BrokerageRecordServiceImpl` 的改动：

**3.1 `addBrokerage(Long userId, BrokerageRecordBizTypeEnum bizType, List<BrokerageAddReqBO> list)` 由「写死两级」改为按配置循环：**

```java
TradeConfigDO config = tradeConfigService.getTradeConfig();
if (config == null || !isTrue(config.getBrokerageEnabled())) return;
List<BrokerageLevelRule> globalRules = config.getBrokerageLevels();
if (CollUtil.isEmpty(globalRules)) return;

BrokerageUserDO upline = brokerageUserService.getBindBrokerageUser(userId);
for (int level = 1; upline != null && level <= globalRules.size(); level++) {
    if (isTrue(upline.getBrokerageEnabled())) {
        addBrokerage(upline, list, config.getBrokerageFrozenDays(), globalRules, bizType, level);
    }
    // 跳级但不压缩层号：无论该上级是否有资格，层号都 +1
    upline = upline.getBindUserId() != null
            ? brokerageUserService.getBrokerageUser(upline.getBindUserId())
            : null;
}
```

**3.2 私有 `addBrokerage(...)` 改为「逐订单项 × 逐级规则」：**

参数由 `Integer brokeragePercent` 改为 `List<BrokerageLevelRule> globalRules`。对每个订单项：

```java
// 规则来源以 subCommissionType 为准，不能用 levels 是否为空来判断
List<BrokerageLevelRule> rules = BooleanUtil.isTrue(item.getSubCommissionType())
        ? item.getLevels() : globalRules;
// 独立分销但未配置佣金：旧行为是 0 佣金，禁止回落到全局（见下方「回归点」）
if (rules == null) continue;
if (level > rules.size()) continue;          // 商品层级数少于全局时的兜底

BrokerageLevelRule rule = rules.get(level - 1);
int basePrice = item.getBasePrice() != null ? item.getBasePrice() : 0;
int percent   = rule.getPercent() != null ? rule.getPercent().doubleValue() : 0;
int fixed     = (rule.getFixedPrice() != null ? rule.getFixedPrice() : 0)
                * (item.getCount() != null ? item.getCount() : 1);

// 保留旧 calculatePrice 的守卫语义：基数 ≤ 0 或比例 ≤ 0 时比例部分记 0
int brokeragePrice = (basePrice > 0 && percent > 0
        ? MoneyUtils.calculateRatePriceFloor(basePrice, Double.valueOf(percent)) : 0)
        + fixed;

if (brokeragePrice <= 0) continue;            // 保留现有「为 0 不记录」行为
```

- 删除 176-183 行对 `level > 2` 抛 `IllegalArgumentException` 的硬编码。
- 废弃旧 `calculatePrice(basePrice, percent, fixedPrice)` 的「固定优先」语义，改为相加。
- `sourceUserLevel` 传 `level`（`BrokerageRecordDO.source_user_level` 无需改动）。

**必须保留的两个回归点**（否则会造成错误分佣，已对照 `BrokerageRecordServiceImpl` 旧实现逐条核对）：

1. **空值守卫**：旧 `calculatePrice` 要求 `basePrice != null && basePrice > 0 && percent != null && percent > 0` 才走比例分支，否则返回 0。直接调用 `MoneyUtils.calculateRatePriceFloor(null, ...)` 会 NPE（`NumberUtil.toBigDecimal(null)` 抛异常），因此上面显式兜底 `basePrice`/`percent`/`fixedPrice`/`count` 为 0。
2. **规则来源不能用 `levels == null` 判断**：商品 `sub_commission_type = true` 但 SKU 两个旧佣金字段均为 `NULL` 时，旧行为是「固定佣金 0 → 返回 0」，即**不发佣金**。若按 `levels == null` 回落到全局规则，就会**凭空多发出全局佣金**。故规则来源以 `subCommissionType` 为准，且 `subCommissionType = true` 而 `levels == null` 时直接跳过。

**3.3 `BrokerageAddReqBO` 调整：**

- 新增 `Integer count`（购买数量）。
- `firstFixedPrice` / `secondFixedPrice` 删除，替换为 `List<BrokerageLevelRule> levels`（商品独立分销的全量覆盖规则；为 `null` 表示走全局）。
- 固定金额 × 数量的乘法从 `TradeOrderConvert` 移到分佣计算内部统一处理（全局与商品两条路径行为一致）。

**3.4 `TradeOrderConvert.convert(user, item, spu, sku)` 调整：**

- `subCommissionType = true` 时设置 `bo.setLevels(sku.getBrokerageLevels())`，不再逐字段拼 `first/secondFixedPrice`。
- 始终设置 `bo.setCount(item.getCount())`。

**3.5 `calculateProductBrokeragePrice(userId, spuId)`（App 商品页展示可赚佣金）：**

语义不变，仍展示**一级**（浏览者作为直属上级）的佣金区间：
- 独立分销：按 SKU 的 `brokerage_levels[0]` 计算 min/max。
- 全局比例：按 `trade_config.brokerage_levels[0]` 计算 min/max。

### 4. 下级查询

`BrokerageUserServiceImpl.getChildUserIdsByLevel(bindUserId, level)` 由「只支持 1/2 级」改为 BFS：

- `level == null`：逐层向下直到没有下级（受 `MAX_LEVEL` 保护，防脏数据成环）。
- `level == k`：向下走 k 层。
- 超出当前全局配置的层级数时抛新的错误码（见 §6）。

调用方不变：后台 `getBrokerageUserPage`、App `getBrokerageUserChildSummaryPage`、`getBrokerageUserCountByBindUserId`。

### 5. 前端改动

| 端 | 文件 | 改动 |
| --- | --- | --- |
| 后台配置 | `yudao-ui-admin-vben/apps/{web-antd,web-ele,web-antdv-next}/src/views/mall/trade/config/data.ts` | 两个比例输入框 → **动态层级表格**（增删行，每行：层级 / 比例% / 固定金额元） |
| 后台配置 API | 同目录 `src/api/mall/trade/config/index.ts` | 类型定义：`brokerageFirstPercent/SecondPercent` → `brokerageLevels: BrokerageLevelRule[]` |
| 后台商品 SKU | `.../src/views/mall/product/spu/form/index.vue` | 「一级/二级佣金」两个输入 → 动态 N 级；校验必须配齐 |
| 后台分销用户 | `.../src/views/mall/trade/brokerage/user/index.vue`（+ `data.ts`） | 层级筛选 1/2 → 1..N |
| 会员端 | `yudao-mall-uniapp/pages/commission/team.vue` | 下级列表层级选项 1/2 → 1..N |
| 会员端 | `yudao-mall-uniapp/pages/commission/{index,goods}.vue` | 分销中心/商品佣金展示按新结构 |
| 会员端 API | `yudao-mall-uniapp/sheep/api/trade/brokerage.js` | 接口类型随层级放宽 |

动态层级表格**无需自定义组件**：vben 表单原生支持数组字段——`FormSchema` 存在 `type: 'array'` 分支（`FormArraySchema`），以 `children` 定义列、`arrayProps` 传 `min`/`max`/`addButtonText`/`showIndex`，渲染为内置组件 `VbenFormFieldArray`（`packages/@core/ui-kit/form-ui/src/components/form-field-array.vue`）。三套后台的 `config/data.ts` 可声明式表达层级表格：

```ts
{
  fieldName: 'brokerageLevels',
  label: '分销层级',
  type: 'array',
  arrayProps: { min: 1, max: 10, addButtonText: '添加层级', showIndex: true },
  children: [
    { fieldName: 'percent',    label: '返佣比例（%）', component: 'InputNumber', componentProps: { min: 0, max: 100, precision: 2 } },
    { fieldName: 'fixedPrice', label: '固定佣金（元）', component: 'InputNumber', componentProps: { min: 0, precision: 2 } },
  ],
}
```

- 行内不存 `level`：层级序号由行序推导，提交时按 index 写入 `level = index + 1`。
- `arrayProps.max` 与后端 `MAX_LEVEL` 对应（前端拦截 + 后端强校验，两处都要）。
- `fixedPrice` 后端单位为**分**，前端按**元**输入，需比照现有 `brokerageWithdrawMinPrice`（同为「元输入、分存储」）的换算方式处理。

### 6. 校验与错误码

- 后台保存全局配置时：
  - `brokerageLevels` 非空，`level` 从 1 连续递增；
  - `percent ∈ [0, 100]`，`fixedPrice ≥ 0`；
  - 数组长度 ≤ `MAX_LEVEL`。
- 后台保存商品时：`subCommissionType = true` 时，`brokerageLevels.size()` 必须等于当前全局层级数，否则报错。
- 错误码（`ErrorCodeConstants`，`yudao-module-trade-api`，沿用 `1_011_007_0xx` 段）：
  - `BROKERAGE_USER_LEVEL_NOT_SUPPORT`（`1_011_007_008`）：文案由「目前只支持 level 小于等于 2」改为「分销层级必须在 1 到 {} 之间」，并按当前全局层级数校验。
  - 新增 `BROKERAGE_LEVEL_CONFIG_INVALID`（`1_011_007_010`）：层级配置不合法（层级不连续、比例越界、超过最大层级数）。
  - 新增 `BROKERAGE_PRODUCT_LEVEL_MISMATCH`（`1_011_007_011`）：商品独立分销的层级数与全局配置不一致。

### 7. 数据迁移

对存量数据做一次性迁移，保证二级配置的结果逐分不变。

**全局（`trade_config`，MySQL 语法）：**

```sql
ALTER TABLE trade_config ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则';
UPDATE trade_config SET brokerage_levels = JSON_ARRAY(
  JSON_OBJECT('level', 1, 'percent', COALESCE(brokerage_first_percent, 0),  'fixedPrice', 0),
  JSON_OBJECT('level', 2, 'percent', COALESCE(brokerage_second_percent, 0), 'fixedPrice', 0)
);
-- 回滚余量：本版本不执行下列语句，下个版本再清理
-- ALTER TABLE trade_config DROP COLUMN brokerage_first_percent;
-- ALTER TABLE trade_config DROP COLUMN brokerage_second_percent;
```

**商品（`product_sku`）：**

```sql
ALTER TABLE product_sku ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则（独立分销时全量覆盖）';
UPDATE product_sku SET brokerage_levels = JSON_ARRAY(
  JSON_OBJECT('level', 1, 'percent', 0, 'fixedPrice', COALESCE(first_brokerage_price, 0)),
  JSON_OBJECT('level', 2, 'percent', 0, 'fixedPrice', COALESCE(second_brokerage_price, 0))
) WHERE first_brokerage_price IS NOT NULL OR second_brokerage_price IS NOT NULL;
-- 回滚余量：本版本不执行
-- ALTER TABLE product_sku DROP COLUMN first_brokerage_price;
-- ALTER TABLE product_sku DROP COLUMN second_brokerage_price;
```

正确性说明：旧逻辑全局走比例（固定金额为 0），旧逻辑商品走固定金额（比例按「固定优先」忽略）。新语义为相加，故上述 `percent = 0` / `fixedPrice = 0` 的迁移结果与旧行为**逐分一致**。

由于迁移使用 MySQL 的 `JSON_ARRAY` / `JSON_OBJECT`，需确认目标库为 MySQL 5.7+。若为其他数据库，迁移语句需按方言改写（或由后台重新保存一次配置生成 JSON）。

## 测试

- `BrokerageRecordServiceImplTest`（`yudao-module-trade`）：
  - 3 级 / 4 级配置下的分佣金额；
  - 相加语义（比例部分 + 固定金额）；
  - 无资格上级被跳过且**层号不压缩**；
  - 商品全量覆盖生效；
  - 2 级存量配置回归：与改造前结果逐分一致。
- `BrokerageUserServiceImplTest`：
  - N 级下级查询（`level = k` 与 `level = null`）；
  - 超出最大层级时的错误码。
- 前端：动态层级表格增删行、商品层级数不匹配时的报错。

## 风险与已知取舍

- **法规风险**：三级及以上返佣在国内有被认定为传销的风险。`MAX_LEVEL` 上限 + 后台提示文字是缓解手段，不构成合规保证。
- **链路查询次数**：每往上一级需要一次 `getBrokerageUser` 查询，`N = 10` 时每笔订单最多约 10 次查询。本期按最简实现（`ponytail:` 已在代码中标注），若后续成为热点，可改为一次递归 CTE / 批量按 id 集合查询整条链路。
- **层级数变更后的存量商品**：全局层级数从 5 改为 3 时，已配置 5 级的商品在读取时按当前全局层级数**截断**（只取前 3 级），不做全量回填校验；反之从 3 增到 5 时，存量商品多出的层级视为 0 佣金。此策略为默认选择，若需强制回填需另行设计。
- **`ProductSkuRespDTO` 体积**：SKU 列表接口会带上 `brokerageLevels`，层级很多时会增大响应体；当前上限 10 级可接受。

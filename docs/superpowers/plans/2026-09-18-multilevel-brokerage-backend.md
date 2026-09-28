# 多级分销（后端）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把商城分销从写死的二级改造成「后台可配置 N 级」，每级支持「比例 + 固定金额」相加计佣，商品可全量覆盖。

**Architecture:** 层级规则以 JSON 数组存进 `trade_config.brokerage_levels`（全局）和 `product_sku.brokerage_levels`（商品覆盖），层级数 = 数组长度。分佣时按 `trade_brokerage_user.bind_user_id` 链表逐级上溯，每级用对应下标的规则计算；上级无资格则跳过但层号不压缩。绑定表与佣金记录表**无需改动**（`source_user_level` 已是 Integer）。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus（`JacksonTypeHandler` + `autoResultMap`）、JUnit 5 + Mockito（`BaseDbUnitTest`）、H2（测试）、MySQL 5.7+（生产）。

**Spec:** `ruoyi-vue-pro/docs/superpowers/specs/2026-09-18-multilevel-brokerage-design.md`

## Global Constraints

- 层级数上限 `MAX_LEVEL = 10`。
- `percent`：**百分比数值**，`10` = 10%，类型 `BigDecimal`，允许小数；范围 `[0, 100]`。
- `fixedPrice`：**单位分**，**单件商品**的固定佣金；结算时乘以购买数量；`>= 0`。
- 计佣语义：**相加** —— `floor(基数 × percent / 100) + fixedPrice × count`。
  - 基数 `<= 0` 或 `percent <= 0` 时，比例部分记 **0**（不是 null、不抛异常）。
  - 结果 `<= 0` 时不生成佣金记录（沿用现有行为）。
- 商品 `sub_commission_type = true` 时，`brokerage_levels` **全量覆盖**全局规则，且必须配齐当前 N 级。
- 规则来源判定**必须以 `subCommissionType` 为准**，不能用 `levels` 是否为空判断（否则会给「独立分销但未配佣金」的商品凭空发全局佣金）。
- 上级无分销资格：**跳过该人、层号不压缩**（第 k 个上级永远用第 k 级规则）。
- 旧列 `brokerage_first_percent` / `brokerage_second_percent` / `first_brokerage_price` / `second_brokerage_price`：**本期保留不 DROP**（留回滚余量），但 Java 代码不再读写它们。
- 模块路径：`ruoyi-vue-pro/yudao-module-mall/yudao-module-product`、`.../yudao-module-trade`、`.../yudao-module-trade-api`。
- 构建/测试：`yudao-module-member` 与 mall 子模块**不在根 reactor**（根 pom 里被注释）。命令在模块目录内单独执行：`mvn -o test -Dtest=<TestClass>`。

---

## File Structure

**新建**

| 文件 | 职责 |
| --- | --- |
| `yudao-module-product/.../api/sku/dto/BrokerageLevelRule.java` | 层级规则值对象，product 与 trade 共用 |

**修改（后端核心）**

| 文件 | 改动 |
| --- | --- |
| `yudao-module-trade/.../dal/dataobject/config/TradeConfigDO.java` | 去掉两个 percent 字段，新增 `brokerageLevels` |
| `yudao-module-trade/.../controller/admin/config/vo/TradeConfigBaseVO.java` | 同步 VO |
| `yudao-module-trade/.../service/brokerage/bo/BrokerageAddReqBO.java` | 去两固定价，加 `subCommissionType`/`levels`/`count` |
| `yudao-module-trade/.../service/brokerage/BrokerageRecordServiceImpl.java` | 分佣改 N 级 + 相加 |
| `yudao-module-trade/.../convert/order/TradeOrderConvert.java` | `convert(...)` 改为传 levels/count/flag |
| `yudao-module-trade/.../service/brokerage/BrokerageUserServiceImpl.java` | 下级查询改 BFS |
| `yudao-module-product/.../dal/dataobject/sku/ProductSkuDO.java` | 去两固定价，加 `brokerageLevels` + `autoResultMap` |
| `yudao-module-product/.../api/sku/dto/ProductSkuRespDTO.java` | 同步 DTO |
| `yudao-module-product/.../controller/admin/spu/vo/ProductSkuSaveReqVO.java`、`ProductSkuRespVO.java` | 同步 VO |
| `yudao-module-trade/.../controller/app/brokerage/vo/user/AppBrokerageUserChildSummaryPageReqVO.java` | `@Range(1,2)` → `@Min(1)` |
| `yudao-module-trade-api/.../enums/ErrorCodeConstants.java` | 改 1 个错误码文案 + 新增 2 个 |

**修改（测试）**

| 文件 | 改动 |
| --- | --- |
| `yudao-module-trade/src/test/.../brokerage/BrokerageRecordServiceImplTest.java` | 重写 `calculatePrice` 三个用例 + 新增 N 级用例 |
| `yudao-module-trade/src/test/.../brokerage/BrokerageUserServiceImplTest.java` | 新增 N 级下级查询用例 |

---

## Task 1: 共享类型 + 全局配置字段 + 分佣计算 N 级化

**Files:**
- Create: `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/api/sku/dto/BrokerageLevelRule.java`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/config/TradeConfigDO.java`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/brokerage/bo/BrokerageAddReqBO.java`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageRecordServiceImpl.java`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvert.java:265-276`
- Test: `yudao-module-mall/yudao-module-trade/src/test/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageRecordServiceImplTest.java`

**Interfaces:**
- Consumes: 无（起始任务）
- Produces:
  - `BrokerageLevelRule(Integer level, BigDecimal percent, Integer fixedPrice)`，包 `cn.iocoder.yudao.module.product.api.sku.dto`
  - `TradeConfigDO.getBrokerageLevels()` → `List<BrokerageLevelRule>`
  - `BrokerageAddReqBO`：`bizId:String`、`basePrice:Integer`、`subCommissionType:Boolean`、`levels:List<BrokerageLevelRule>`、`count:Integer`、`sourceUserId:Long`、`title:String`
  - `BrokerageRecordServiceImpl.calculatePrice(Integer basePrice, BigDecimal percent, Integer fixedPrice, Integer count)` → `int`（包级可见，便于测试）
  - `BrokerageRecordServiceImpl.addBrokerage(Long, BrokerageRecordBizTypeEnum, List<BrokerageAddReqBO>)` 行为：按 `trade_config.brokerageLevels` 逐级上溯

- [ ] **Step 1: 新建共享值对象 `BrokerageLevelRule`**

`yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/api/sku/dto/BrokerageLevelRule.java`：

```java
package cn.iocoder.yudao.module.product.api.sku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分销层级规则
 *
 * 放在 product 的 api 包：product 不依赖 trade，而 trade 已依赖 product，
 * 因此该类型必须由 product 侧持有，trade 通过 {@link ProductSkuRespDTO} 读取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageLevelRule {

    /**
     * 层级，从 1 开始连续
     */
    private Integer level;
    /**
     * 返佣比例，百分比数值。例如 10 表示 10%，允许小数
     */
    private BigDecimal percent;
    /**
     * 固定佣金，单位：分。单件商品的金额，结算时乘以购买数量
     */
    private Integer fixedPrice;

}
```

- [ ] **Step 2: `TradeConfigDO` 用 `brokerageLevels` 替换两个 percent 字段**

在 `TradeConfigDO.java` 中，把：

```java
    /**
     * 一级返佣比例
     */
    private Integer brokerageFirstPercent;
    /**
     * 二级返佣比例
     */
    private Integer brokerageSecondPercent;
```

替换为：

```java
    /**
     * 分销层级规则。数组长度即层级数，顺序即层级顺序
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<BrokerageLevelRule> brokerageLevels;
```

并补 import：

```java
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
```

（`JacksonTypeHandler` 与 `TradeConfigDO` 上的 `@TableName(value = "trade_config", autoResultMap = true)` 均已存在，无需改动。）

- [ ] **Step 3: 改写 `BrokerageAddReqBO`**

整体替换为：

```java
package cn.iocoder.yudao.module.trade.service.brokerage.bo;

import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 佣金 增加 Request BO
 *
 * @author owen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageAddReqBO {

    /**
     * 业务编号
     */
    @NotBlank(message = "业务编号不能为空")
    private String bizId;
    /**
     * 佣金基数
     */
    @NotNull(message = "佣金基数不能为空")
    private Integer basePrice;
    /**
     * 是否商品独立分销
     *
     * 为 true 时使用 {@link #levels}，为 false 时使用全局配置
     */
    private Boolean subCommissionType;
    /**
     * 商品独立分销的层级规则（全量覆盖）。
     * 仅当 {@link #subCommissionType} 为 true 时生效；为空表示该商品未配置佣金，记为 0
     */
    private List<BrokerageLevelRule> levels;
    /**
     * 购买数量。固定佣金按单件配置，需乘以该值
     */
    @NotNull(message = "购买数量不能为空")
    private Integer count;
    /**
     * 来源用户编号
     */
    @NotNull(message = "来源用户编号不能为空")
    private Long sourceUserId;
    /**
     * 佣金记录标题
     */
    @NotEmpty(message = "佣金记录标题不能为空")
    private String title;

}
```

- [ ] **Step 4: 重写 `BrokerageRecordServiceImpl.addBrokerage` 与 `calculatePrice`**

（a）把现有 `addBrokerage(Long, BrokerageRecordBizTypeEnum, List<BrokerageAddReqBO>)` 方法体整体替换为：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBrokerage(Long userId, BrokerageRecordBizTypeEnum bizType, List<BrokerageAddReqBO> list) {
        TradeConfigDO tradeConfig = tradeConfigService.getTradeConfig();
        // 0 未启用分销功能
        if (tradeConfig == null || !BooleanUtil.isTrue(tradeConfig.getBrokerageEnabled())) {
            log.error("[addBrokerage][增加佣金失败：brokerageEnabled 未配置，userId({}) bizType({}) list({})", userId, bizType, list);
            return;
        }
        // 0.1 校验层级规则已配置
        List<BrokerageLevelRule> globalRules = tradeConfig.getBrokerageLevels();
        if (CollUtil.isEmpty(globalRules)) {
            log.error("[addBrokerage][增加佣金失败：brokerageLevels 未配置，userId({}) bizType({})", userId, bizType);
            return;
        }

        // 逐级向上分佣。注意：上级无分销资格时跳过该人，但层号不压缩
        BrokerageUserDO upline = brokerageUserService.getBindBrokerageUser(userId);
        for (int level = 1; upline != null && level <= globalRules.size(); level++) {
            if (BooleanUtil.isTrue(upline.getBrokerageEnabled())) {
                addBrokerage(upline, list, tradeConfig.getBrokerageFrozenDays(), globalRules, bizType, level);
            }
            // 继续往上找一级
            upline = upline.getBindUserId() != null
                    ? brokerageUserService.getBrokerageUser(upline.getBindUserId())
                    : null;
        }
    }
```

（b）把私有方法 `addBrokerage(BrokerageUserDO, List, Integer, Integer, BrokerageRecordBizTypeEnum, Integer)` 整体替换为：

```java
    /**
     * 增加某一级用户的佣金
     *
     * @param user                用户
     * @param list                佣金增加参数列表
     * @param brokerageFrozenDays 冻结天数
     * @param globalRules         全局层级规则
     * @param bizType             业务类型
     * @param sourceUserLevel     来源用户等级，从 1 开始
     */
    private void addBrokerage(BrokerageUserDO user, List<BrokerageAddReqBO> list, Integer brokerageFrozenDays,
                              List<BrokerageLevelRule> globalRules, BrokerageRecordBizTypeEnum bizType,
                              Integer sourceUserLevel) {
        // 1.1 处理冻结时间
        LocalDateTime unfreezeTime = null;
        if (brokerageFrozenDays != null && brokerageFrozenDays > 0) {
            unfreezeTime = LocalDateTime.now().plusDays(brokerageFrozenDays);
        }
        // 1.2 计算分佣
        int totalBrokerage = 0;
        List<BrokerageRecordDO> records = new ArrayList<>();
        for (BrokerageAddReqBO item : list) {
            // 规则来源以 subCommissionType 为准：
            // 商品独立分销用商品配置（为空表示该商品未配佣金，记 0，不能回落到全局）；
            // 否则用全局配置
            List<BrokerageLevelRule> rules = BooleanUtil.isTrue(item.getSubCommissionType())
                    ? item.getLevels() : globalRules;
            if (CollUtil.isEmpty(rules) || sourceUserLevel > rules.size()) {
                continue;
            }
            BrokerageLevelRule rule = rules.get(sourceUserLevel - 1);
            int brokeragePrice = calculatePrice(item.getBasePrice(), rule.getPercent(), rule.getFixedPrice(),
                    item.getCount());
            if (brokeragePrice <= 0) {
                continue;
            }
            totalBrokerage += brokeragePrice;
            // 创建记录实体
            records.add(BrokerageRecordConvert.INSTANCE.convert(user, bizType, item.getBizId(),
                    brokerageFrozenDays, brokeragePrice, unfreezeTime, item.getTitle(),
                    item.getSourceUserId(), sourceUserLevel));
        }
        if (CollUtil.isEmpty(records)) {
            return;
        }
        // 1.3 保存佣金记录
        brokerageRecordMapper.insertBatch(records);

        // 2. 更新用户佣金
        if (brokerageFrozenDays != null && brokerageFrozenDays > 0) { // 更新用户冻结佣金
            brokerageUserService.updateUserFrozenPrice(user.getId(), totalBrokerage);
        } else { // 更新用户可用佣金
            brokerageUserService.updateUserPrice(user.getId(), totalBrokerage);
        }
    }
```

（c）把 `calculatePrice(Integer, Integer, Integer)` 整体替换为：

```java
    /**
     * 计算佣金 = 比例部分 + 固定部分
     *
     * @param basePrice  佣金基数，单位：分
     * @param percent    返佣比例，百分比数值。例如 10 表示 10%
     * @param fixedPrice 固定佣金，单位：分（单件）
     * @param count      购买数量
     * @return 佣金
     */
    int calculatePrice(Integer basePrice, BigDecimal percent, Integer fixedPrice, Integer count) {
        // 1. 比例部分：基数与比例都为正时才计算，否则记 0（沿用旧实现的守卫语义，避免 NPE）
        int percentPart = 0;
        if (basePrice != null && basePrice > 0 && percent != null && percent.compareTo(BigDecimal.ZERO) > 0) {
            percentPart = MoneyUtils.calculateRatePriceFloor(basePrice, percent.doubleValue());
        }
        // 2. 固定部分：单件固定佣金 × 购买数量
        int fixedPart = (fixedPrice != null ? fixedPrice : 0) * (count != null ? count : 1);
        // 3. 相加
        return percentPart + fixedPart;
    }
```

（d）补充 import：

```java
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import java.math.BigDecimal;
```

（e）`calculateProductBrokeragePrice`（App 商品页展示可赚佣金）**移到 Task 2 Step 4 执行**。

> 原因：它需要 `ProductSkuRespDTO.getBrokerageLevels()`，而该字段由 Task 2 引入。若放在 Task 1 会造成「Task 1 依赖 Task 2」的循环。Task 1 的验收标准是「分佣计算 N 级化 + 其单元测试通过」，不包含 App 商品页展示。

- [ ] **Step 5: 改写 `TradeOrderConvert.convert(...)`**

把 `TradeOrderConvert.java:265-276` 的 default 方法整体替换为：

```java
    default BrokerageAddReqBO convert(MemberUserRespDTO user, TradeOrderItemDO item,
                                      ProductSpuRespDTO spu, ProductSkuRespDTO sku) {
        // 商品独立分销时，使用 SKU 配置的层级规则（全量覆盖）；否则为 null，走全局配置
        boolean subCommissionType = BooleanUtil.isTrue(spu.getSubCommissionType());
        return new BrokerageAddReqBO().setBizId(String.valueOf(item.getId())).setSourceUserId(item.getUserId())
                .setBasePrice(item.getPayPrice())
                .setCount(item.getCount())
                .setSubCommissionType(subCommissionType)
                .setLevels(subCommissionType ? sku.getBrokerageLevels() : null)
                .setTitle(StrUtil.format("{}成功购买{}", user.getNickname(), item.getSpuName()));
    }
```

> 注意：固定佣金 × 数量的乘法**从本方法移入 `calculatePrice`**，因此这里不再乘 `item.getCount()`。

- [ ] **Step 6: 改写测试（先写失败用例）**

在 `BrokerageRecordServiceImplTest.java` 中：

（a）**删除** 现有的 `testCalculatePrice_useFixedPrice`、`testCalculatePrice_usePercent`、`testCalculatePrice_fixedPriceIsZero` 三个方法（语义已由「固定优先」改为「相加」，这三条断言不再成立）。

（b）新增以下用例：

```java
    @Test
    public void testCalculatePrice_additive() {
        // 基数 1000 分：比例 10% => 100 分；固定 88 分 × 2 件 => 176 分；合计 276
        int brokerage = brokerageRecordService.calculatePrice(1000, new BigDecimal("10"), 88, 2);
        assertEquals(276, brokerage);
    }

    @Test
    public void testCalculatePrice_percentOnly() {
        // 固定为 0 时，只算比例部分：1000 × 10% = 100
        int brokerage = brokerageRecordService.calculatePrice(1000, new BigDecimal("10"), 0, 1);
        assertEquals(100, brokerage);
    }

    @Test
    public void testCalculatePrice_fixedOnly() {
        // 比例为 0 时，只算固定部分：88 × 3 = 264
        int brokerage = brokerageRecordService.calculatePrice(1000, BigDecimal.ZERO, 88, 3);
        assertEquals(264, brokerage);
    }

    @Test
    public void testCalculatePrice_nullAndNonPositiveGuards() {
        // 基数 null / 0 / 负数 或 比例 null / 0 时，比例部分记 0，且不抛 NPE
        assertEquals(0, brokerageRecordService.calculatePrice(null, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(0, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(-100, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(1000, null, null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(1000, BigDecimal.ZERO, null, 1));
    }

    @Test
    public void testAddBrokerage_threeLevels() {
        // 1. mock 配置：3 级，一级 10%+100 分，二级 5%，三级 2%，不冻结
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageEnabled(true).brokerageFrozenDays(0)
                .brokerageLevels(ListUtil.of(
                        new BrokerageLevelRule(1, new BigDecimal("10"), 100),
                        new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                        new BrokerageLevelRule(3, new BigDecimal("2"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        // 2. mock 链路：买家 1000L -> 上级 200L -> 300L -> 400L，均有资格
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, 300L, true));
        when(brokerageUserService.getBrokerageUser(300L)).thenReturn(buildUser(300L, 400L, true));
        when(brokerageUserService.getBrokerageUser(400L)).thenReturn(buildUser(400L, null, true));
        // 3. 订单项：基数 1000 分，数量 1
        BrokerageAddReqBO item = new BrokerageAddReqBO();
        item.setBizId("1").setBasePrice(1000).setSourceUserId(1000L)
                .setSubCommissionType(false).setCount(1).setTitle("测试订单项");
        // 4. 调用
        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));
        // 5. 断言：一级 1000*10%+100=200；二级 1000*5%=50；三级 1000*2%=20
        List<BrokerageRecordDO> records = brokerageRecordMapper.selectListByBizTypeAndBizId(
                BrokerageRecordBizTypeEnum.ORDER.getType(), "1");
        assertEquals(3, records.size());
        assertEquals(200, findRecord(records, 200L).getPrice());
        assertEquals(1, findRecord(records, 200L).getSourceUserLevel());
        assertEquals(50, findRecord(records, 300L).getPrice());
        assertEquals(2, findRecord(records, 300L).getSourceUserLevel());
        assertEquals(20, findRecord(records, 400L).getPrice());
        assertEquals(3, findRecord(records, 400L).getSourceUserLevel());
    }

    @Test
    public void testAddBrokerage_skipDisabledWithoutCompressingLevel() {
        // 3 级配置；一级无资格被跳过，二级、三级仍分别用第 2、3 级规则（层号不压缩）
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageEnabled(true).brokerageFrozenDays(0)
                .brokerageLevels(ListUtil.of(
                        new BrokerageLevelRule(1, new BigDecimal("10"), 0),
                        new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                        new BrokerageLevelRule(3, new BigDecimal("2"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, 300L, false)); // 一级无资格
        when(brokerageUserService.getBrokerageUser(300L)).thenReturn(buildUser(300L, 400L, true));
        when(brokerageUserService.getBrokerageUser(400L)).thenReturn(buildUser(400L, null, true));

        BrokerageAddReqBO item = new BrokerageAddReqBO();
        item.setBizId("2").setBasePrice(1000).setSourceUserId(1000L)
                .setSubCommissionType(false).setCount(1).setTitle("测试订单项");
        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        List<BrokerageRecordDO> records = brokerageRecordMapper.selectListByBizTypeAndBizId(
                BrokerageRecordBizTypeEnum.ORDER.getType(), "2");
        assertEquals(2, records.size());                       // 一级被跳过，不产生记录
        assertEquals(50, findRecord(records, 300L).getPrice()); // 二级仍是 5%
        assertEquals(2, findRecord(records, 300L).getSourceUserLevel());
        assertEquals(20, findRecord(records, 400L).getPrice()); // 三级仍是 2%
        assertEquals(3, findRecord(records, 400L).getSourceUserLevel());
    }

    @Test
    public void testAddBrokerage_productOverride() {
        // 全局 3 级，但商品全量覆盖为「一级 0% + 固定 500」，二级/三级为 0
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageEnabled(true).brokerageFrozenDays(0)
                .brokerageLevels(ListUtil.of(
                        new BrokerageLevelRule(1, new BigDecimal("10"), 0),
                        new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                        new BrokerageLevelRule(3, new BigDecimal("2"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, null, true));

        BrokerageAddReqBO item = new BrokerageAddReqBO();
        item.setBizId("3").setBasePrice(1000).setSourceUserId(1000L).setCount(1).setTitle("测试订单项")
                .setSubCommissionType(true)
                .setLevels(ListUtil.of(
                        new BrokerageLevelRule(1, BigDecimal.ZERO, 500),
                        new BrokerageLevelRule(2, BigDecimal.ZERO, 0),
                        new BrokerageLevelRule(3, BigDecimal.ZERO, 0)));
        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        List<BrokerageRecordDO> records = brokerageRecordMapper.selectListByBizTypeAndBizId(
                BrokerageRecordBizTypeEnum.ORDER.getType(), "3");
        assertEquals(1, records.size());
        assertEquals(500, records.get(0).getPrice()); // 用商品配置的固定 500，而不是全局的 10%
    }

    @Test
    public void testAddBrokerage_productSubCommissionWithoutLevelsPaysNothing() {
        // 回归点：商品开启独立分销但未配佣金 -> 旧行为是 0 佣金，禁止回落到全局
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageEnabled(true).brokerageFrozenDays(0)
                .brokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, null, true));

        BrokerageAddReqBO item = new BrokerageAddReqBO();
        item.setBizId("4").setBasePrice(1000).setSourceUserId(1000L).setCount(1).setTitle("测试订单项")
                .setSubCommissionType(true).setLevels(null); // 未配置
        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        assertTrue(brokerageRecordMapper.selectListByBizTypeAndBizId(
                BrokerageRecordBizTypeEnum.ORDER.getType(), "4").isEmpty());
    }
```

（c）在测试类中补两个测试辅助方法：

```java
    private static BrokerageUserDO buildUser(Long id, Long bindUserId, boolean enabled) {
        return BrokerageUserDO.builder().id(id).bindUserId(bindUserId).brokerageEnabled(enabled).build();
    }

    private static BrokerageRecordDO findRecord(List<BrokerageRecordDO> records, Long userId) {
        return records.stream().filter(r -> r.getUserId().equals(userId)).findFirst().orElseThrow();
    }
```

（d）测试类需补 import：

```java
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.service.brokerage.bo.BrokerageAddReqBO;

import java.math.BigDecimal;
import java.util.List;
```

> `ListUtil`、`TradeConfigDO`、`BrokerageRecordDO`、`BrokerageRecordBizTypeEnum`、`assertEquals`、`assertTrue`、`when` 原文件已 import，无需重复。

- [ ] **Step 7: 运行测试，确认先失败**

Run: `mvn -o test -Dtest=BrokerageRecordServiceImplTest`（在 `yudao-module-mall/yudao-module-trade` 目录）
Expected: 编译失败或测试失败（`calculatePrice` 签名变更、`BrokerageLevelRule` 尚未被使用等）。这一步的目的仅是确认测试确实在测新行为。

- [ ] **Step 8: 编译并运行，确认通过**

Run: `mvn -o test -Dtest=BrokerageRecordServiceImplTest`（在 `yudao-module-mall/yudao-module-trade` 目录）
Expected: `BUILD SUCCESS`，Tests run: 全部通过。

- [ ] **Step 9: 提交**

```bash
git add yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/api/sku/dto/BrokerageLevelRule.java \
        yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/config/TradeConfigDO.java \
        yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/brokerage/bo/BrokerageAddReqBO.java \
        yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageRecordServiceImpl.java \
        yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvert.java \
        yudao-module-mall/yudao-module-trade/src/test/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageRecordServiceImplTest.java
git commit -m "feat(trade): 分销分佣支持 N 级配置与比例+固定金额相加"
```

---

## Task 2: 商品侧 SKU 支持 N 级佣金

**Files:**
- Modify: `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/dal/dataobject/sku/ProductSkuDO.java:80-87`
- Modify: `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/api/sku/dto/ProductSkuRespDTO.java:62-69`
- Modify: `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/controller/admin/spu/vo/ProductSkuSaveReqVO.java:47-51`
- Modify: `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/controller/admin/spu/vo/ProductSkuRespVO.java:43-46`

**Interfaces:**
- Consumes: `BrokerageLevelRule`（Task 1）
- Produces: `ProductSkuDO.getBrokerageLevels()`、`ProductSkuRespDTO.getBrokerageLevels()` → `List<BrokerageLevelRule>`

- [ ] **Step 1: `ProductSkuDO` 换字段并开启 `autoResultMap`**

把 `ProductSkuDO.java` 的类注解：

```java
@TableName("product_sku")
```

改为：

```java
@TableName(value = "product_sku", autoResultMap = true)
```

并把两个固定价字段：

```java
    /**
     * 一级分销的佣金，单位：分
     */
    private Integer firstBrokeragePrice;
    /**
     * 二级分销的佣金，单位：分
     */
    private Integer secondBrokeragePrice;
```

替换为：

```java
    /**
     * 分销层级规则。商品独立分销时全量覆盖全局配置，数组长度必须与全局层级数一致
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<BrokerageLevelRule> brokerageLevels;
```

补 import：

```java
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.List;
```

- [ ] **Step 2: `ProductSkuRespDTO` 换字段**

把两个固定价字段替换为：

```java
    /**
     * 分销层级规则
     */
    private List<BrokerageLevelRule> brokerageLevels;
```

补 import `cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;`（同包，可省略 import）。

- [ ] **Step 3: `ProductSkuSaveReqVO` / `ProductSkuRespVO` 换字段**

两处均把：

```java
    @Schema(description = "一级分销的佣金，单位：分", example = "199")
    private Integer firstBrokeragePrice;

    @Schema(description = "二级分销的佣金，单位：分", example = "19")
    private Integer secondBrokeragePrice;
```

替换为：

```java
    @Schema(description = "分销层级规则，商品独立分销时需配齐所有层级")
    private List<BrokerageLevelRule> brokerageLevels;
```

（两文件均已 `import java.util.List;`。）

- [ ] **Step 4: 编译定位所有残留引用**

Run: `mvn -o compile`（在 `yudao-module-mall/yudao-module-product` 目录）
Expected: 报错指出仍引用 `firstBrokeragePrice` / `secondBrokeragePrice` 的位置（如 convert / service）。逐一改为读写 `brokerageLevels`。

- [ ] **Step 5: 再次编译，确认通过**

Run: `mvn -o compile`（在 `yudao-module-mall/yudao-module-product` 目录）
Expected: `BUILD SUCCESS`

- [ ] **Step 6: 改造 trade 侧的 `calculateProductBrokeragePrice`（原 Task 1 Step 4(e)）**

在 `BrokerageRecordServiceImpl.java` 中删除对 `tradeConfig.getBrokerageFirstPercent()` 与 `sku.getFirstBrokeragePrice()` 的引用，改为：

```java
        // 3.1 取一级规则（浏览者作为直属上级，拿到的是第 1 级佣金）
        BrokerageLevelRule firstRule = CollUtil.getFirst(tradeConfig.getBrokerageLevels());
        if (firstRule == null) {
            return respVO;
        }
        if (BooleanUtil.isTrue(spu.getSubCommissionType())) {
            // 3.2.1 商品独立分销：取 SKU 第 1 级的固定佣金
            Integer fixedMinPrice = getMinValue(skuList,
                    sku -> getLevelFixedPrice(sku.getBrokerageLevels(), 1));
            Integer fixedMaxPrice = getMaxValue(skuList,
                    sku -> getLevelFixedPrice(sku.getBrokerageLevels(), 1));
            respVO.setBrokerageMinPrice(calculatePrice(null, null, fixedMinPrice, 1))
                    .setBrokerageMaxPrice(calculatePrice(null, null, fixedMaxPrice, 1));
        } else {
            // 3.2.2 全局比例模式
            Integer spuMinPrice = getMinValue(skuList, ProductSkuRespDTO::getPrice);
            Integer spuMaxPrice = getMaxValue(skuList, ProductSkuRespDTO::getPrice);
            respVO.setBrokerageMinPrice(calculatePrice(spuMinPrice, firstRule.getPercent(), 0, 1))
                    .setBrokerageMaxPrice(calculatePrice(spuMaxPrice, firstRule.getPercent(), 0, 1));
        }
        return respVO;
```

并在同类中新增私有工具方法：

```java
    /**
     * 取指定层级的固定佣金，缺失时按 0 处理
     */
    private static Integer getLevelFixedPrice(List<BrokerageLevelRule> rules, int level) {
        if (CollUtil.isEmpty(rules) || rules.size() < level) {
            return 0;
        }
        return ObjectUtil.defaultIfNull(rules.get(level - 1).getFixedPrice(), 0);
    }
```

> `calculatePrice(null, null, fixedMinPrice, 1)` 的比例部分为 0（basePrice/percent 为 null），只输出固定部分——正是旧逻辑「商品独立分销取固定佣金」的语义。

- [ ] **Step 7: 编译并运行 trade 测试**

Run: `mvn -o test -Dtest=BrokerageRecordServiceImplTest,BrokerageUserServiceImplTest`（在 `yudao-module-mall/yudao-module-trade` 目录）
Expected: `BUILD SUCCESS`

- [ ] **Step 8: 提交**

```bash
git add yudao-module-mall
git commit -m "feat: SKU 分销佣金改为 N 级层级规则"
```
```

(Task 2 includes 提交 step)

---

## Task 3: 下级查询支持 N 级

**Files:**
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageUserServiceImpl.java:351-384`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/controller/app/brokerage/vo/user/AppBrokerageUserChildSummaryPageReqVO.java:25-28`
- Test: `yudao-module-mall/yudao-module-trade/src/test/java/cn/iocoder/yudao/module/trade/service/brokerage/BrokerageUserServiceImplTest.java`

**Interfaces:**
- Consumes: `TradeConfigDO.getBrokerageLevels()`（Task 1）
- Produces: `BrokerageUserServiceImpl.getChildUserIdsByLevel(Long, Integer)` 支持任意 N；超出层级时抛 `BROKERAGE_USER_LEVEL_NOT_SUPPORT`

- [ ] **Step 1: 放宽 App VO 的层级校验**

`AppBrokerageUserChildSummaryPageReqVO.java`：

```java
    @Schema(description = "下级的级别", requiredMode = Schema.RequiredMode.REQUIRED, example = "1") // 1 - 直接下级；2 - 间接下级
    @NotNull(message = "下级的级别不能为空")
    @Min(value = 1, message = "下级的级别最小为 {value}")
    private Integer level;
```

把 import `org.hibernate.validator.constraints.Range` 换成 `javax.validation.constraints.Min`。

- [ ] **Step 2: 改写 `getChildUserIdsByLevel` 为 BFS**

把 `BrokerageUserServiceImpl.java:359-384` 的该方法整体替换为：

```java
    /**
     * 根据绑定用户编号，获得下级用户编号列表
     *
     * @param bindUserId 绑定用户编号
     * @param level      下级用户的层级。为空时查询全部层级
     * @return 下级用户编号列表
     */
    private List<Long> getChildUserIdsByLevel(Long bindUserId, Integer level) {
        if (bindUserId == null) {
            return Collections.emptyList();
        }
        // 1. 校验层级不超过当前配置的层级数
        int maxLevel = getBrokerageLevelCount();
        if (level != null && (level < 1 || level > maxLevel)) {
            throw exception(BROKERAGE_USER_LEVEL_NOT_SUPPORT, maxLevel);
        }
        // 2. 逐层向下
        int walkLevel = level != null ? level : maxLevel;
        if (walkLevel <= 0) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        List<Long> current = Collections.singletonList(bindUserId);
        for (int i = 1; i <= walkLevel; i++) {
            List<Long> childIds = brokerageUserMapper.selectIdListByBindUserIdIn(current);
            if (CollUtil.isEmpty(childIds)) {
                break;
            }
            // level 为空时收集所有层级；否则只收集目标层级
            if (level == null || i == level) {
                result.addAll(childIds);
            }
            current = childIds;
        }
        return result;
    }

    /**
     * 获得当前全局配置的分销层级数
     */
    private int getBrokerageLevelCount() {
        TradeConfigDO tradeConfig = tradeConfigService.getTradeConfig();
        return tradeConfig == null || CollUtil.isEmpty(tradeConfig.getBrokerageLevels())
                ? 0 : tradeConfig.getBrokerageLevels().size();
    }
```

- [ ] **Step 3: 写失败测试**

在 `BrokerageUserServiceImplTest.java` 新增：

```java
    @Test
    public void testGetChildUserIdsByLevel_threeLevels() {
        // mock 配置：3 级
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageLevels(ListUtil.of(
                        new BrokerageLevelRule(1, new BigDecimal("10"), 0),
                        new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                        new BrokerageLevelRule(3, new BigDecimal("2"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        // mock 链路：1 -> [2] ; 2 -> [3] ; 3 -> [4]
        when(brokerageUserMapper.selectIdListByBindUserIdIn(Collections.singleton(1L))).thenReturn(ListUtil.of(2L));
        when(brokerageUserMapper.selectIdListByBindUserIdIn(ListUtil.of(2L))).thenReturn(ListUtil.of(3L));
        when(brokerageUserMapper.selectIdListByBindUserIdIn(ListUtil.of(3L))).thenReturn(ListUtil.of(4L));
        // 断底层级。注意：getChildUserIdsByLevel 是 private，需通过公开方法间接验证
        // getBrokerageUserCountByBindUserId 返回 childIds 的数量
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 1));
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 3));
        // 断言全部层级
        assertEquals(3L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, null));
    }

    @Test
    public void testGetChildUserIdsByLevel_outOfRange() {
        TradeConfigDO config = TradeConfigDO.builder()
                .brokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)))
                .build();
        when(tradeConfigService.getTradeConfig()).thenReturn(config);
        // 只有 1 级，查第 2 级应报错
        assertThrows(ServiceException.class,
                () -> brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
    }
```

并在测试类补 `@MockBean private TradeConfigService tradeConfigService;`（若尚未存在）与 import：

```java
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import java.math.BigDecimal;
```

- [ ] **Step 4: 运行测试**

Run: `mvn -o test -Dtest=BrokerageUserServiceImplTest`（在 `yudao-module-mall/yudao-module-trade` 目录）
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add yudao-module-mall/yudao-module-trade
git commit -m "feat(trade): 下级分销查询支持 N 级"
```

---

## Task 4: 配置校验与错误码

**Files:**
- Modify: `yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java:104`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/config/vo/TradeConfigBaseVO.java:70-78`
- Modify: `yudao-module-mall/yudao-module-trade/src/main/java/cn/iocoder/yudao/module/trade/service/config/TradeConfigServiceImpl.java`

**Interfaces:**
- Consumes: `BrokerageLevelRule`（Task 1）
- Produces: 错误码 `BROKERAGE_LEVEL_CONFIG_INVALID`（`1_011_007_010`）、`BROKERAGE_PRODUCT_LEVEL_MISMATCH`（`1_011_007_011`）；`BROKERAGE_USER_LEVEL_NOT_SUPPORT` 文案带 `{}`

- [ ] **Step 1: 改错误码**

`ErrorCodeConstants.java:104` 改为：

```java
    ErrorCode BROKERAGE_USER_LEVEL_NOT_SUPPORT = new ErrorCode(1_011_007_008, "分销层级必须在 1 到 {} 之间");
    ErrorCode BROKERAGE_LEVEL_CONFIG_INVALID = new ErrorCode(1_011_007_010, "分销层级配置不合法，层级需从 1 连续递增，比例 0-100，固定佣金不小于 0，且不超过 {} 级");
    ErrorCode BROKERAGE_PRODUCT_LEVEL_MISMATCH = new ErrorCode(1_011_007_011, "商品独立分销的层级数必须与全局一致，当前应为 {} 级");
```

- [ ] **Step 2: 改 `TradeConfigBaseVO`**

把：

```java
    @Schema(description = "一级返佣比例", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "一级返佣比例不能为空")
    @Range(min = 0, max = 100, message = "一级返佣比例必须在 0 - 100 之间")
    private Integer brokerageFirstPercent;

    @Schema(description = "二级返佣比例", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "二级返佣比例不能为空")
    @Range(min = 0, max = 100, message = "二级返佣比例必须在 0 - 100 之间")
    private Integer brokerageSecondPercent;
```

替换为：

```java
    @Schema(description = "分销层级规则", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "分销层级规则不能为空")
    private List<BrokerageLevelRule> brokerageLevels;
```

补 import `cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;`。

- [ ] **Step 3: 新增层级配置校验工具**

新建 `yudao-module-mall/yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/api/sku/dto/BrokerageLevelRuleValidator.java`：

```java
package cn.iocoder.yudao.module.product.api.sku.dto;

import cn.hutool.core.collection.CollUtil;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分销层级规则校验工具
 */
public class BrokerageLevelRuleValidator {

    /**
     * 分销层级上限
     */
    public static final int MAX_LEVEL = 10;

    /**
     * 校验层级规则是否合法：层级从 1 连续递增、比例 0-100、固定佣金 >= 0、不超过 MAX_LEVEL
     *
     * @return 合法返回 true
     */
    public static boolean isValid(List<BrokerageLevelRule> rules) {
        if (CollUtil.isEmpty(rules) || rules.size() > MAX_LEVEL) {
            return false;
        }
        for (int i = 0; i < rules.size(); i++) {
            BrokerageLevelRule rule = rules.get(i);
            if (rule == null || rule.getLevel() == null || rule.getLevel() != i + 1) {
                return false;
            }
            BigDecimal percent = rule.getPercent();
            if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0
                    || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                return false;
            }
            if (rule.getFixedPrice() != null && rule.getFixedPrice() < 0) {
                return false;
            }
        }
        return true;
    }

}
```

- [ ] **Step 4: 在配置保存时校验**

在 `TradeConfigServiceImpl` 保存配置的方法中（`saveTradeConfig` 或等价方法）开头加入：

```java
        // 校验分销层级配置
        if (BooleanUtil.isTrue(reqVO.getBrokerageEnabled())
                && !BrokerageLevelRuleValidator.isValid(reqVO.getBrokerageLevels())) {
            throw exception(BROKERAGE_LEVEL_CONFIG_INVALID, BrokerageLevelRuleValidator.MAX_LEVEL);
        }
```

补 import：

```java
import cn.hutool.core.util.BooleanUtil;
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRuleValidator;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.BROKERAGE_LEVEL_CONFIG_INVALID;
```

- [ ] **Step 5: 编译**

Run: `mvn -o compile`（分别执行于 `yudao-module-mall/yudao-module-product`、`yudao-module-trade-api`、`yudao-module-trade`）
Expected: 三处均 `BUILD SUCCESS`（若 `TradeConfigServiceImpl` 的方法名与上面不一致，按实际方法名落点）。

- [ ] **Step 6: 提交**

```bash
git add yudao-module-mall
git commit -m "feat(trade): 分销层级配置校验与错误码"
```

---

## Task 5: 数据迁移 SQL

**Files:**
- Create: `ruoyi-vue-pro/sql/mysql/2026-09-18-multilevel-brokerage.sql`

**Interfaces:**
- Consumes: Task 1/2 的列定义
- Produces: 存量二级数据迁移为两级 `brokerage_levels`

- [ ] **Step 1: 写迁移脚本**

`sql/mysql/2026-09-18-multilevel-brokerage.sql`：

```sql
-- 多级分销改造：新增层级规则 JSON 列，并迁移存量二级数据
-- 兼容性：MySQL 5.7+（依赖 JSON_ARRAY / JSON_OBJECT）

-- 1. 全局配置
ALTER TABLE trade_config
    ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则，数组长度即层级数';

UPDATE trade_config
SET brokerage_levels = JSON_ARRAY(
        JSON_OBJECT('level', 1, 'percent', COALESCE(brokerage_first_percent, 0), 'fixedPrice', 0),
        JSON_OBJECT('level', 2, 'percent', COALESCE(brokerage_second_percent, 0), 'fixedPrice', 0)
    );

-- 2. 商品 SKU（仅迁移配置过独立分销佣金的 SKU）
ALTER TABLE product_sku
    ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则，商品独立分销时全量覆盖全局';

UPDATE product_sku
SET brokerage_levels = JSON_ARRAY(
        JSON_OBJECT('level', 1, 'percent', 0, 'fixedPrice', COALESCE(first_brokerage_price, 0)),
        JSON_OBJECT('level', 2, 'percent', 0, 'fixedPrice', COALESCE(second_brokerage_price, 0))
    )
WHERE first_brokerage_price IS NOT NULL
   OR second_brokerage_price IS NOT NULL;

-- 3. 回滚余量：本期不删除旧列，确认稳定后的下个版本再执行
-- ALTER TABLE trade_config DROP COLUMN brokerage_first_percent;
-- ALTER TABLE trade_config DROP COLUMN brokerage_second_percent;
-- ALTER TABLE product_sku DROP COLUMN first_brokerage_price;
-- ALTER TABLE product_sku DROP COLUMN second_brokerage_price;
```

- [ ] **Step 2: 校验迁移正确性（人工核对）**

在目标库执行后逐条核对：

```sql
-- 全局：应能看到 level 1/2 且 percent 与旧列一致
SELECT id, brokerage_first_percent, brokerage_second_percent, brokerage_levels FROM trade_config;

-- 商品：迁移行数应等于旧列非空的行数
SELECT (SELECT COUNT(*) FROM product_sku WHERE brokerage_levels IS NOT NULL) AS migrated,
       (SELECT COUNT(*) FROM product_sku
        WHERE first_brokerage_price IS NOT NULL OR second_brokerage_price IS NOT NULL) AS expected;
```

Expected: `migrated = expected`；`brokerage_levels` 中 `percent`/`fixedPrice` 与旧列逐条一致。

- [ ] **Step 3: 提交**

```bash
git add sql/mysql/2026-09-18-multilevel-brokerage.sql
git commit -m "feat(sql): 多级分销数据迁移脚本"
```

---

## Self-Review

**1. Spec coverage**

| Spec 章节 | 覆盖任务 |
| --- | --- |
| §1 数据结构（两个 JSON 列） | Task 1 Step 2、Task 2 Step 1 |
| §2 共享类型 `BrokerageLevelRule` | Task 1 Step 1 |
| §3.1 分佣循环改 N 级 + 跳级不压缩 | Task 1 Step 4(a)(b) |
| §3.2 相加语义 + 空值守卫 | Task 1 Step 4(c)、Step 6 |
| §3.3 `BrokerageAddReqBO` 调整 | Task 1 Step 3 |
| §3.4 `TradeOrderConvert` 调整 | Task 1 Step 5 |
| §3.5 `calculateProductBrokeragePrice` | Task 2 Step 6 |
| §4 下级查询 BFS | Task 3 |
| §6 校验与错误码 | Task 4 |
| §7 数据迁移 | Task 5 |
| §5 前端（3 个 vben app + 会员端 uniapp） | **不在本计划内** — 见下方「后续计划」 |
| §测试 | Task 1 Step 6、Task 3 Step 3 |

**2. Placeholder scan:** 无 TBD/TODO；每个代码步骤都给了可粘贴的完整代码。Task 4 Step 5 与 Task 2 Step 4 需要按编译错误定位实际落点，已明确说明这是刻意的定位步骤而非占位。

**3. Type consistency:** `BrokerageLevelRule(level:Integer, percent:BigDecimal, fixedPrice:Integer)` 在 Task 1/2/3/4 中一致；`calculatePrice(Integer, BigDecimal, Integer, Integer):int` 在 Task 1 定义并在其测试中使用；`BrokerageAddReqBO` 的 7 个字段名在 Task 1 Step 3 定义、Step 5/6 使用，一致；`getBrokerageLevelCount()` 与 `getChildUserIdsByLevel` 同类一致。

---

## 后续计划（不在本计划内）

**前端计划（另行编写）**，覆盖 spec §5：

- 3 个 vben 后台（`web-antd` / `web-ele` / `web-antdv-next`）的 `mall/trade/config/data.ts` 用 `type: 'array'` 声明式动态层级表格（`arrayProps.max = 10`）
- 3 个后台的商品 SKU 表单（`mall/product/spu/form/index.vue`）改为 N 级
- 3 个后台的分销用户列表层级筛选 1..N
- 会员端 `yudao-mall-uniapp/pages/commission/{team,index,goods}.vue` + `sheep/api/trade/brokerage.js`

拆成独立计划的原因：后端可独立编译、独立测试、独立发布（API 先就绪）；前端 4 个端各自可独立验收。两个计划合在一起会让单个计划无法独立产生「可运行、可测试」的产物。

# 收款码支付（扫码转账 + 人工确认）设计

## 背景

商城当前支付走 `yudao-module-pay`：下单时 `TradeOrderUpdateServiceImpl.createOrder` 调用 `payOrderApi.createOrder(...)` 创建支付单，用户在收银台选择渠道支付，渠道异步回调 `POST /trade/order/update-paid`，最终由 `updateOrderPaid(id, payOrderId)` 把订单置为「待发货」。

本需求改为**不接支付通道**：管理员在后台维护静态收款二维码（微信/支付宝），用户在商城端选择支付方式后**直接看到收款码**，用微信/支付宝扫码转账，再回商城**自报付款信息**，管理员**人工核对到账**后确认订单。

关键约束：**静态收款码没有支付回调**。系统无法自动得知是否到账，因此必须引入「用户自报 + 管理员确认」这一人工环节。

## 目标

- 后台可维护收款码：上传图片、选择类型（微信/支付宝）、启用/停用、**设为默认**；每类型可有多个，支付页展示该类型的默认码。
- 商城端支付流程改为「扫码转账」：选择微信/支付宝 → 展示对应收款码 → 用户转账 → 填写付款信息并提交。
- 用户提交后订单进入「待确认」（不改订单主状态，由凭证表表达）。
- 后台可查看待确认列表并**确认到账**（订单变「待发货」并触发后续逻辑）或**驳回**（用户可重新提交）。
- 复用既有支付成功后的后置处理（`TradeOrderHandler.afterPayOrder`），保证**分销佣金、订单日志等逻辑不被绕过**。

## 非目标

- 不接入任何真实支付通道（微信/支付宝 API），不产生渠道回调。
- 不修改订单主状态机（`TradeOrderStatusEnum` 不新增枚举值）。
- 不做对账超时自动处理（用户明确选择「不设超时」）。
- 不处理微信小程序端（小程序禁止引导站外扫码支付，见「平台限制」）。
- 不改动现有的分销、提现、售后逻辑本身。

## 已确认的决策

| # | 决策 |
| --- | --- |
| 1 | 支付确认方式：**用户自报 + 管理员确认** |
| 2 | 对账依据：用户填 **付款金额 + 交易单号** |
| 3 | 与在线支付的关系：扫码转账**完全替代**在线支付 |
| 4 | 收款码数量：每类型可多个，**管理员指定默认** |
| 5 | 驳回后：订单**仍是「待支付」**（主状态全程未变），凭证置为已驳回，用户可重新提交 |
| 6 | 「待确认」表达：**只靠凭证表**，不动订单表 |
| 7 | 待确认超时：**不设超时** |

## 流程

```
【后台】收款码管理
  上传图片 + 选类型(微信/支付宝) + 启用 + 设为默认（每类型唯一默认）

【用户】下单 → 订单 UNPAID
   ↓  进支付页（「扫码转账」页，非收银台）
   ↓  GET 该类型默认收款码 → 展示二维码图片 + 应付金额
   ↓  微信/支付宝扫码转账（金额由用户自己填）
   ↓  填【付款金额 + 交易单号】→ 点「我已支付」
   ↓  POST 提交凭证(status=待确认)；订单仍 UNPAID，页面显示「等待确认」

【后台】「待确认付款」列表
   ├─ 确认到账 → 订单 UNDELIVERED + payStatus=true + payTime + 跑 afterPayOrder
   │             凭证 status=已确认
   └─ 驳回    → 凭证 status=已驳回 + 理由；订单仍 UNPAID
                用户在订单详情看到驳回理由与「重新提交」
```

时序（确认到账，高风险路径）：

```
管理员点击「确认到账」
  → PaymentProofService.confirm(proofId, operatorId)
     → 校验凭证存在且 status=待确认
     → TradeOrderUpdateService.updateOrderPaidByOffline(orderId, operatorId)
        → 校验订单存在、status=UNPAID 且 !payStatus
        → 更新订单 status=UNDELIVERED, payStatus=true, payTime=now, payChannelCode=OFFLINE
        → tradeOrderHandlers.forEach(h -> h.afterPayOrder(order, orderItems))   // 关键：分销佣金在此触发
        → 记录订单日志（⚠️ 操作人必须是管理员，见下方「订单日志的操作人」）
     → 凭证 status=已确认, auditUserId, auditTime
```

### 订单日志的操作人（易错点）

现有 `updateOrderPaid:317` 写的是：

```java
TradeOrderLogUtils.setUserInfo(order.getUserId(), UserTypeEnum.MEMBER.getValue());
```

这是**会员支付**场景，把操作人记为会员，正确。

但离线确认是**管理员**操作。若照抄这一行，订单日志会把「确认收款」错误地记成会员干的（钱的轨迹记错人）。离线方法必须改为：

```java
TradeOrderLogUtils.setUserInfo(operatorId, UserTypeEnum.ADMIN.getValue());
```

同理 `@TradeOrderLog(operateType = ...)` 也要用新增的 `ADMIN_CONFIRM_PAY`，而不是 `MEMBER_PAY`。

### `afterPayOrder` 会触发哪些逻辑（已核实）

复用 handler 链会触发以下 **4 个**实现——与在线支付完全一致，这正是我们要的：

| Handler | 作用 |
| --- | --- |
| `TradeMemberPointOrderHandler` | 会员积分 |
| `TradeCouponOrderHandler` | 优惠券核销 |
| `TradeCombinationOrderHandler` | 拼团成团 |
| `TradeBrokerageOrderHandler` | **分销佣金** |

其余 handler 不实现 `afterPayOrder`。特别注意 `TradeStatusSyncToWxaOrderHandler`：它只实现 `afterDeliveryOrder`/`afterReceiveOrder`，且开头有 `payChannelCode == PayChannelEnum.WX_LITE.getCode()` 守卫——离线流写入的 `"offline"` 不等于 `WX_LITE`，**直接短路返回**，无需担心。

## 数据模型

### 表 1：`trade_payment_qrcode`（收款码）

```sql
CREATE TABLE trade_payment_qrcode (
  id            bigint NOT NULL AUTO_INCREMENT,
  type          int     NOT NULL COMMENT '支付类型：1-微信 2-支付宝',
  name          varchar(64)  NOT NULL COMMENT '名称，如「微信收款码-张三」',
  pic_url       varchar(512) NOT NULL COMMENT '收款码图片地址',
  default_flag  bit     NOT NULL DEFAULT FALSE COMMENT '是否该类型的默认收款码',
  enabled       bit     NOT NULL DEFAULT TRUE  COMMENT '是否启用',
  sort          int     NOT NULL DEFAULT 0,
  remark        varchar(255) DEFAULT NULL,
  -- BaseDO
  creator, create_time, updater, update_time, deleted, tenant_id
);
```

约束：**同一 `type` 下 `default_flag = true` 的记录最多一条**（在服务层保证：设置默认时先清掉同类型其他默认）。

### 表 2：`trade_order_payment_proof`（付款报备）

```sql
CREATE TABLE trade_order_payment_proof (
  id                   bigint NOT NULL AUTO_INCREMENT,
  order_id             bigint NOT NULL COMMENT '订单编号',
  user_id              bigint NOT NULL COMMENT '提交用户编号',
  type                 int    NOT NULL COMMENT '支付类型：1-微信 2-支付宝',
  qrcode_id            bigint DEFAULT NULL COMMENT '当时展示的收款码编号（便于追溯）',
  proof_price          int    NOT NULL COMMENT '用户自报的付款金额，单位：分',
  proof_transaction_id varchar(128) NOT NULL COMMENT '交易单号',
  proof_time           datetime DEFAULT NULL COMMENT '用户自报的付款时间',
  status               int    NOT NULL COMMENT '状态：0-待确认 1-已确认 2-已驳回',
  audit_user_id        bigint DEFAULT NULL COMMENT '审核管理员编号',
  audit_time           datetime DEFAULT NULL,
  reject_reason        varchar(255) DEFAULT NULL COMMENT '驳回理由',
  -- BaseDO
);
CREATE INDEX idx_order_id ON trade_order_payment_proof (order_id);
CREATE INDEX idx_status   ON trade_order_payment_proof (status);
```

**为什么独立表而不是订单加字段**：驳回后可重新提交，需要保留每一次提交的历史（钱的事必须可审计）；且「待确认」列表可以直接查 `status=0`，无需 join 订单表。

### 枚举（`yudao-module-trade-api`）

- `PaymentQrcodeTypeEnum`：`WECHAT(1, "微信")`、`ALIPAY(2, "支付宝")`
- `PaymentProofStatusEnum`：`WAIT_CONFIRM(0, "待确认")`、`CONFIRMED(1, "已确认")`、`REJECTED(2, "已驳回")`

### 订单表

**不新增字段。** 订单主状态保持 `UNPAID`；「待确认」由凭证表 `status=0` 表达。

约定：离线确认到账时，订单 `payChannelCode` 固定写常量 `"offline"`（表示线下收款）。`payOrderId` **仍然保留**由 `createOrder` 创建的支付单号——该支付单永远不会被支付，保留它是为了不动那 5 处依赖（见「为什么保留支付单」）。

### 收款码缺失时的行为

商城端 `GET /trade/payment-qrcode/get-default?type=` 在**该类型没有启用中的默认码**时返回空对象；前端支付页提示「商家暂未配置该支付方式，请联系客服」，并**不展示提交入口**（避免用户无法付款却能提交报备）。

## 接口

### 后台（admin-api）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/trade/payment-qrcode/page` | 分页 |
| GET | `/trade/payment-qrcode/list-by-type?type=` | 按类型查列表 |
| POST | `/trade/payment-qrcode/create` | 新增（上传图片复用 infra 文件上传） |
| PUT | `/trade/payment-qrcode/update` | 编辑 |
| DELETE | `/trade/payment-qrcode/delete?id=` | 删除 |
| PUT | `/trade/payment-qrcode/set-default?id=` | 设为默认（同类型互斥） |
| PUT | `/trade/payment-qrcode/update-enabled?id=&enabled=` | 启用/停用 |
| GET | `/trade/payment-proof/page?status=` | 付款报备分页（`status=0` 即待确认列表） |
| PUT | `/trade/payment-proof/confirm?id=` | 确认到账 |
| PUT | `/trade/payment-proof/reject` | 驳回（`{id, rejectReason}`） |

权限点：`trade:payment-qrcode:*`、`trade:payment-proof:query|confirm|reject`。

### 商城（app-api）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/trade/payment-qrcode/get-default?type=` | 取该类型默认收款码（返回图片地址、名称、类型） |
| POST | `/trade/order/payment-proof/submit` | 提交付款信息 `{orderId, type, proofPrice, proofTransactionId, proofTime?}` |
| GET | `/trade/order/payment-proof/get?orderId=` | 查该订单当前报备状态（用于订单详情展示/驳回理由） |

`submit` 校验：
1. 订单存在且属于当前登录用户；
2. 订单 `status=UNPAID`；
3. 该订单**不存在** `status=0`（待确认）的凭证（防止重复提交）；
4. `proofTransactionId` 非空。

## 需要改动的现有代码

| 文件 | 改动 | 风险 |
| --- | --- | --- |
| `TradeOrderUpdateServiceImpl.createOrder` | **保持原样，不动**（继续创建支付单）。原因见「为什么保留支付单」 | 无 |
| `TradeOrderUpdateServiceImpl` | **新增** `updateOrderPaidByOffline(Long orderId, Long operatorId)`：不校验支付单，直接置 `UNDELIVERED`，**必须复用 `tradeOrderHandlers.forEach(h -> h.afterPayOrder(...))`** | **高** |
| `TradeOrderUpdateServiceImpl.cancelOrderBySystem` | **新增守卫**：订单存在 `status=0`（待确认）的付款报备时，跳过自动取消 | **高** |
| `TradeOrderOperateTypeEnum` | 新增 `ADMIN_CONFIRM_PAY`（订单日志区分「会员支付」与「管理员确认收款」） | 低 |
| 会员端支付页（uniapp） | 收银台 → 扫码转账页 | 中 |
| 后台菜单/权限 | 新增「收款码管理」「待确认付款」菜单与权限。**需在 `system_menu` 表插入菜单记录**（参照现有「分销用户/佣金记录」菜单，属纯 SQL，无对应代码；仓库里没有商城全量 SQL，只能手写 insert） | 低 |

> **不要复用 `updateOrderPaid`**：它第 301 行 `validatePayOrderPaid` 要求支付单**真实存在且已支付**，而离线场景的支付单永远是「未支付」，必然失败。但**必须复用它的第 313 行 handler 链**，否则**分销佣金不会发放**。

### 为什么保留支付单（`createOrder` 不动）

原设想是移除 `createOrder` 里的 `payOrderApi.createOrder(...)`，但审计发现 `payOrderId` 在 9 个文件被引用，其中 **5 处会因它为空而出问题**：

| 位置 | 代码 | `payOrderId = null` 的后果 |
| --- | --- | --- |
| `TradeOrderUpdateServiceImpl:567`（会员取消） | 取消前查支付单，防「回调延迟」 | `getOrder(null)`，行为不确定 |
| `TradeOrderUpdateServiceImpl:611`（系统自动取消） | 同上 | 同上 |
| `TradeOrderUpdateServiceImpl:732`（改价） | `payOrderApi.updatePayOrderPrice(order.getPayOrderId(), ...)` | 改价失败 |
| `AppTradeOrderController:106` | 订单详情 `sync=true` → `syncOrderPayStatusQuietly(id, null)` | `getOrder(null)` |
| `TradeStatusSyncToWxaOrderHandler:57,80` | 支付后 handler 链内查支付单 | 同上 |

**保留支付单则上述全部不受影响**：支付单被创建但永远不会被支付，于是

- 取消订单的守卫查到「未支付」→ 正常取消；
- 改价时支付单存在 → 正常同步价格；
- `syncOrderPayStatusQuietly` 查到未支付 → 直接返回，什么都不做。

代价仅是库里多一条永远未支付的支付单记录，换来 5 处依赖**零改动**。

### 待确认订单不能被自动取消（必须加守卫）

`cancelOrderBySystem` 是「待支付超时自动取消」（`PAY_TIMEOUT`）。而「待确认」订单的主状态**仍是 `UNPAID`**，因此会在管理员尚未核对时**被自动取消、释放库存**——与设计意图直接冲突。

**必须在 `cancelOrderBySystem` 内新增守卫：若该订单存在 `status=0`（待确认）的付款报备，则跳过自动取消。**

## 风险与已知取舍

1. **微信小程序平台限制**：小程序规则禁止引导用户扫码站外支付，会被拒审/下架。此流程**只在 H5 / App 合法**；小程序端需另行处理（走微信支付，或引导到 H5）。
2. **无自动回调**：到账完全依赖人工核对，订单量上升后是运营瓶颈。
3. **凭证可伪造**：用户填的金额与交易单号可虚构。缓解：交易单号必填；管理员核对时以**收款端实际记录**为准；必要时要求上传付款截图（本期未做）。
4. **不设超时（已确认的取舍）**：待确认凭证会一直停留，订单长期占用库存，需管理员主动处理。**注意**：既有的 `cancelOrderBySystem`（待支付超时）**必须加守卫跳过待确认订单**，否则订单会在核对期间被自动取消。
5. **`payOrderId` 依赖（已审计并规避）**：审计发现 5 处依赖 `payOrderId`（会员取消、系统自动取消、管理员改价、订单详情 `sync=true`、微信小程序状态同步 handler）。本方案通过**保留 `createOrder` 创建支付单**规避，这些位置无需改动。若日后有人「清理」掉这行支付单创建，将同时打断这 5 处——spec 保留此条作为记录。

## 测试要点

- 收款码：设默认时同类型互斥；停用的码不返回给商城端；无默认码时的返回（应为空且前端有提示）。
- 提交报备：非本人订单、已支付订单、重复提交（已有待确认）均拒绝。
- 确认到账：订单变为 `UNDELIVERED`；`payStatus=true`；**`afterPayOrder` 的 4 个 handler 全部被触发**（用分销佣金记录、优惠券核销、积分、拼团成团验证）；凭证变已确认；重复确认被拒。
- **订单日志操作人**：确认到账后，订单日志的操作人应为**管理员**（`UserTypeEnum.ADMIN` + `operatorId`）、操作类型为 `ADMIN_CONFIRM_PAY`，**不能**是会员。
- 驳回：凭证变已驳回且带理由；订单仍 `UNPAID`；用户可再次提交。
- **自动取消守卫**：存在待确认报备的订单，执行 `cancelOrderBySystem` 后**不应被取消**；无报备的待支付订单仍应正常超时取消（回归）。
- 回归：保留支付单后，会员取消订单、管理员改价、订单详情 `sync=true` 均正常（证明 `payOrderId` 未被破坏）。

## 待办（本期不做）

- 用户上传付款凭证截图
- 待确认超时自动驳回
- 收款码使用次数统计
- 微信小程序端的合规替代方案

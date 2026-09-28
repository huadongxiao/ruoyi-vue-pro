# 多商户商城一期 · 商户入驻 + 多商户化 设计文档

- 日期：2026-09-28
- 状态：待评审（draft pending review）
- 范围：`E:\dm-project\yudao`（ruoyi-vue-pro 单体 + yudao-mall-uniapp + yudao-ui-admin-vben）
- 关联资料：`docs/huifu/企业商户进件-KYC.md`（汇付进件接口快照）

## 1. 背景与目标

现有商城是单商户模型：`yudao-module-mall` 下 product/promotion/trade/statistics 四个子模块，`ProductSpuDO`、`TradeOrderDO` 均无商户维度，全库无 merchant/入驻概念。本项目在其上构建**多商户商城**：商户自助入驻、自主管理商品与订单，买家在 C 端浏览/购买，款项直达商户汇付账户。

一期目标：商户入驻（汇付进件闭环）→ 商户自主上架商品 → 买家单店下单支付 → 商户发货/售后，全链路数据隔离。

## 2. 已确认决策（需求澄清结论）

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 商业模式 | **商户自主收款**：平台不碰资金、不抽佣、无分账结算；支付通道对接**汇付（斗拱）**，商户进件也走汇付 |
| D2 | C 端下单模式 | **一期单店下单**（不做跨店购物车/拆单） |
| D3 | 商家端后台 | **同一 admin（yudao-ui-admin-vben）加商户角色**，不建独立商家端 |
| D4 | 入驻申请入口 | **admin 后台自助入驻**（资质表单 + 传图在 PC 后台完成） |
| D5 | 审核分工 | **提交即推汇付，以汇付 `audit_status` 为准**，平台不做人工资质预审；运营仅可停用生效商户 |
| D6 | 架构方案 | **方案 A**：新建 `yudao-module-merchant` 模块 + 现有 mall 模块加 `merchantId` 维度（弃用 B 多租户迁移、C 塞入现有模块） |

## 3. 范围

### 3.1 一期范围内

- 商户注册与入驻申请（admin 后台）
- 汇付企业商户进件闭环：图片上传 → 进件 → 回调/轮询 → 商户业务开通
- 商品/购物车/订单/售后加 `merchant_id` 维度与数据隔离
- 汇付 H5 支付渠道（按订单商户 `huifu_id` 收款）+ 退款
- 商户角色与菜单裁剪、数据强制过滤
- C 端店铺主页（店铺信息 + 商品列表）与商品挂店铺
- 分期：P1 商户域 → P2 商品多商户化 → P3 交易闭环 → P4 商家后台完善

### 3.2 一期明确不做（Out of Scope）

跨店购物车与按店拆单、平台抽佣/分账结算、店铺装修、独立商家端工程、个人商户进件、汇付页面版进件、小程序支付、汇付侧商户状态变更联动、APP 端。

## 4. 总体架构（模块划分）

| 位置 | 改动 |
|---|---|
| **新建** `yudao-module-mall/yudao-module-merchant` | 商户域：入驻申请状态机、商户资料、汇付进件客户端（图片上传/进件/状态查询/业务开通）、`huifu_id↔merchantId` 映射、商户停用 |
| `yudao-module-mall/yudao-module-product` | `ProductSpuDO`/`ProductSkuDO` 加 `merchant_id`；商户商品 CRUD；C 端按店铺查商品接口 |
| `yudao-module-mall/yudao-module-trade` | cart/order/order_item/售后单加 `merchant_id`；单店结算约束；商户订单/发货/售后接口与数据权限 |
| `yudao-module-pay` | 新增汇付渠道（渠道码 `HUIFU`）：发起支付、回调验签、退款；渠道全局配置存 `pay_channel` |
| `yudao-ui-admin-vben` | 商户注册/入驻申请页、商户角色菜单、商品/订单/售后页复用（数据权限裁剪） |
| `yudao-mall-uniapp` | 店铺主页、商品详情挂店铺入口、按店铺筛选 |

模块边界：merchant 只管商户身份与进件，不管交易；product/trade 只加维度不感知汇付；pay 只通过 `merchant_id → huifu_id` 映射拿收款方，不依赖入驻流程。

## 5. 数据模型

### 5.1 新增表

```
merchant                  商户主表
  id, user_id             -- 绑定的 admin 账号（AdminUserDO.id）
  name, short_name, logo, remark
  status                  -- 0草稿 1进件中 2生效 3拒绝 4停用
  huifu_id, ext_mer_id    -- ext_mer_id 恒等于 merchant.id，提交时写入
  contact_name, contact_mobile, contact_email
  settle_card_no_masked   -- 结算卡号（脱敏展示用）
  submit_time, effect_time

merchant_apply            进件申请单（每次进件一条，保留历史）
  id, merchant_id, req_seq_id, req_date, apply_no
  status                  -- 0进件中 1通过 2拒绝 3失败 4提交失败
  audit_status, audit_desc, huifu_id, token_no
  submit_time, audit_time, raw_notify_json

merchant_image            资质图片映射
  id, merchant_id, biz_type(F02/F03/F07/F08/F13/F15/F22/F24/F55/F56/F105...)
  infra_file_id           -- 本平台 infra 文件 id
  huifu_file_id           -- 汇付图片上传接口返回的 file_id
```

### 5.2 现有表加字段（不拆表，加索引）

| 表 | 新增 |
|---|---|
| `product_spu` / `product_sku` | `merchant_id`（索引） |
| `trade_cart` | `merchant_id`（加购时从 spu 带出） |
| `trade_order` / `trade_order_item` | `merchant_id`（索引） |
| 售后单 | `merchant_id` |

支付单不冗余商户号：支付时经 order → merchant 取 `huifu_id`。存量单商户数据迁移：`merchant_id` 回填一个「平台自营商户」（P2 迁移脚本）。

## 6. 入驻流程（状态机 + 汇付交互）

```
商户注册（admin 开放注册入口，初始角色=待生效商家，绑定 merchant 草稿记录）
  → 登录后台 → 填资质 + 传图（先存本平台 infra，不直传汇付）
  → [提交]
      ① 图片逐张推 汇付图片上传接口 → 获 huifu file_id（存 merchant_image）
      ② 调 POST /v2/merchant/basicdata/ent
         sys_id=平台渠道商号, ext_mer_id=merchantId,
         async_return_url=平台回调地址, 必填资质字段（见 KYC 文档）
      ③ 写 merchant_apply: req_seq_id/apply_no, status=进件中; merchant.status=1
  → [汇付审核中] 双通道收敛（幂等）:
      · 回调：汇付 POST async_return_url → 验签 → 按 req_seq_id 幂等处理
      · 兜底：定时任务扫描"进件中"超 15 分钟的申请单 → 调 申请单状态查询
  → audit_status=Y → 调 商户业务开通（收款产品）→ 成功后 merchant.status=2 生效
      · 开通失败 → 停在"待开通"态，定时任务重试
  → audit_status=N/F → merchant_apply=拒绝/失败 + audit_desc; merchant.status=3
      → 商户修改资料重新提交（新申请单，旧单留档）
```

约束与幂等：

- 同一商户存在「进件中」申请单时，禁止重复提交。
- 回调按 `merchant_id + req_seq_id + audit_status` 去重，重复回调直接返回成功不改状态。
- 运营操作：仅「停用生效商户」（平台侧：下架全部商品 + 禁止登录后台）；汇付侧状态变更接口二期。

## 7. 交易链路改造（一期单店）

- **购物车**：`trade_cart` 加 `merchant_id`；结算页按店铺分组，**一次只能结算同一店铺**的勾选商品（含多店商品时按店分组分别结算）。
- **下单**：订单头/明细写 `merchant_id`；单店无跨店优惠分摊问题，现有 `TradePriceCalculator` 金额逻辑不动。
- **支付**：PayOrder → order.merchant_id → merchant.huifu_id → 汇付下单（钱直进商户账户）。
- **履约**：发货、调价、改地址、售后处理等 admin 接口经 `@MerchantScope` 过滤，商户仅处理本店订单。
- **C 端**：商品列表/搜索支持按店铺筛选；新增店铺主页 app-api（店铺信息 + 店铺 spu 列表）；商品详情展示所属店铺。

## 8. 支付链路（汇付渠道）

- yudao pay 渠道模型新增 `HUIFU` 渠道码；全局配置（`sys_id`、`product_id`、加签私钥、网关地址、回调地址）存 `pay_channel`。
- 每笔支付按订单商户 `huifu_id` 发起；**一期支付形态：汇付 H5 支付**（覆盖 uniapp H5/公众号；小程序支付二期）。
- 支付回调：验签 → 幂等 → 走 yudao 现有 PayOrder 状态机 → 订单支付成功 → 触发后续履约。
- 退款：商户后台发起 → 汇付退款接口 → 原路退回，结果回写订单售后流程。

## 9. 权限与数据隔离（防越权核心）

双层防护，缺一不可：

1. **菜单/功能裁剪**：商户角色可见 = 入驻信息、商品管理、订单、售后、店铺设置；隐藏平台运营、会员、推广、财务等菜单（vben 动态菜单按角色下发）。
2. **数据强制过滤**：mall 模块 admin 查询统一走 `@MerchantScope` 切面 + MyBatis 拦截器——登录账号绑定 `merchant_id` 时自动追加 `merchant_id = ?`；平台账号无绑定则不过滤。
   - 覆盖：商品分页、订单分页、售后分页；
   - **详情/操作类接口必须显式校验资源归属**，不归属返回 403（分页过滤防不了详情直查）。

不依赖前端隐藏作为安全手段。

## 10. 错误处理

| 故障 | 处理 |
|---|---|
| 汇付进件请求超时/系统异常 | `merchant_apply=提交失败` + 原始报文日志留存，允许重试（幂等键 `req_seq_id` 当日唯一） |
| 进件回调丢失 | 定时轮询「申请单状态查询」兜底 |
| 回调验签失败 | 拒绝处理 + ERROR 告警日志，不改任何状态 |
| 支付回调重复/乱序 | 复用 yudao PayOrder 现有幂等机制 |
| 商户业务开通失败 | 停「待开通」态，定时任务重试（有上限后转人工） |
| 图片推汇付失败 | 提交整体回滚（已传图片保留缓存可复用），提示重试 |
| 汇付返回业务拒绝码 | 记录 `resp_code/resp_desc`，映射为可读驳回提示展示给商户 |

## 11. 测试策略

- **单测**：汇付客户端（加签/验签/请求构造/响应解析）、入驻状态机全部合法/非法流转、`@MerchantScope` 拦截器生成的 SQL 条件。
- **集成**：mock 汇付网关跑通 提交→回调→业务开通→生效 全链路；支付回调→订单状态流转；退款回写。
- **越权专项（最重要）**：商户 A 凭证查询/修改商户 B 的商品、订单、售后详情与列表 → 全部 403/空集；平台账号不受过滤影响。
- 覆盖入口：每个 P 阶段验收时执行。

## 12. 分期计划（每个子项目独立走 spec → plan → impl）

| 阶段 | 内容 | 完成标准 |
|---|---|---|
| P1 商户域 | merchant 模块、注册/入驻表单、汇付进件闭环（不含支付） | 测试商户走完 提交→汇付审核通过→业务开通→生效 |
| P2 商品多商户化 | spu/sku 加维度、商户商品管理、C 端店铺页、存量数据迁移 | 商户上架商品，C 端店铺页可见 |
| P3 交易闭环 | cart/order 加维度、单店结算、汇付 H5 支付、发货/售后 + 数据权限 | 买家下单支付成功→商户发货→售后完成，越权专项通过 |
| P4 商家后台完善 | 角色菜单打磨、按商户统计、停用风控 | 运营可停用商户并阻断其交易 |

## 13. 风险与开放问题

| 风险 | 缓解 |
|---|---|
| merchant_id 过滤遗漏导致越权 | `@MerchantScope` 统一拦截 + 详情归属校验 + §11 越权专项测试兜底 |
| 汇付进件字段多且校验证照/法人一致性 | 表单前端校验 + 提交前格式核对；失败信息完整透传商户 |
| 方案 A 字段散落多表 | 所有新增 `merchant_id` 字段与过滤逻辑 code review checklist 化 |
| 存量单商户数据 | P2 迁移脚本回填「平台自营商户」 |

开放问题（不阻塞 P1，P3 前需定）：

1. C 端主力端是 H5 还是微信小程序？若小程序为主，§8 的一期支付形态需改为小程序支付。
2. 商户注册入口是否需要验证码/邀请码限制（防垃圾注册）？默认：开放注册 + 汇付审核天然兜底。

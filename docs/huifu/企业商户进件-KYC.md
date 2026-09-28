# 汇付（斗拱）- 企业商户进件（KYC）

> 来源：[汇付文档中心 - 企业商户进件](https://paas.huifu.com/open/doc/api/#/shgl/shjj/api_shjj_qyshjbxxrz_kyc)
> 文档最近更新：2026.09.18 ｜ 抓取时间：2026-09-28
> 用途：多商户商城「商户入驻」模块对接汇付的接口参考

## 应用场景

通过此接口在汇付为企业类商户进行基本信息开户。传入企业商户基本资料和图片资料，开通汇付账号，为商户绑定银行卡、配置结算和取现等功能。

**适用对象**：企业商户开通汇付账号并填写基本信息、配置结算和取现等功能时调用。

企业商户包括：

- 政府机构
- 国营企业
- 私营企业
- 外资企业
- 个体工商户（有营业执照）
- 其它组织
- 事业单位
- 业主委员会

## 接口说明

- **请求方式**：`POST`
- **地址**：`https://api.huifu.com/v2/merchant/basicdata/ent`
- **支持格式**：`JSON`
- **加签验签**：见 [接入指引-开发指南](https://paas.huifu.com/open/doc/guide/#/api_v2jqyq)

## 公共参数

### 公共请求参数

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| sys_id | 系统号 | String | 32 | Y | 渠道商或商户的 huifu_id：<br>（1）主体为渠道商时，填渠道商 huifu_id；<br>（2）主体为总部商户时，填商户 huifu_id |
| product_id | 产品号 | String | 32 | Y | 汇付分配的产品号，示例值：`YYZY` |
| sign | 加签结果 | String | 512 | Y | [接口加签验签说明](https://paas.huifu.com/open/doc/guide/#/api_v2jqyq) |
| data | 数据 | Json | 4096 | Y | 业务请求参数，具体值参考下文请求参数 |

### 公共返回参数

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| sign | 签名 | String | 512 | Y | [接口加签验签说明](https://paas.huifu.com/open/doc/guide/#/api_v2jqyq) |
| data | 响应内容体 | Json | - | N | 业务返回参数 |

> 「必填」列取值：`Y` 必填；`N` 非必填；`C` 条件必填。

## 注意

- 系统会验证**证照号、商户名称、法人姓名、法人证件号**的一致性，请正确填写。
- `card_info` 卡信息：对私结算会验证【银行卡户名】【银行卡号】【持卡人证件号码】，请正确上送参数值。
- `card_info` 结算类型为对私法人结算时，**持卡人姓名必须与法人姓名相同**。
- 请上送 utf-8 格式的字符，否则影响后期 AT 入驻。

## 请求参数（data）

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| req_seq_id | 请求流水号 | String | 32 | Y | 当日唯一，示例值：`2022012614120615001` |
| req_date | 请求日期 | String | 8 | Y | 格式：`yyyyMMdd` |
| upper_huifu_id | 渠道商号 | String | 18 | C | 进件完成后归属的渠道商 huifu_id；`sys_id` 主体为渠道商时填写，总部商户时选填 |
| reg_name | 商户名称 | String | 64 | Y | 必须与企业证照上的名称一致；个体工商户执照无名称时填「个体户XXX」（XXX 为经营者姓名），汉字按 2 字符计 |
| short_name | 商户简称 | String | 64 | Y | 最少 4 个字符；展示在消费账单上 |
| receipt_name | 小票名称 | String | 50 | Y | 展示在 POS 小票上 |
| mer_en_name | 商户英文名称 | String | 256 | N | |
| ent_type | 公司类型 | String | 1 | Y | 1：政府机构 2：国营企业 3：私营企业 4：外资企业 5：个体工商户 6：其它组织 7：事业单位 9：业主委员会；`use_head_info_flag=Y` 时不填 |
| mcc | 所属行业 | String | 7 | C | 参考 [汇付MCC编码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_hfmccbm)；`use_head_info_flag=Y` 时不填 |
| busi_type | 经营类型 | String | 1 | C | 1：实体，2：虚拟；`use_head_info_flag=Y` 时不填 |
| scene_type | 场景类型 | String | 8 | Y | `ONLINE`：线上；`OFFLINE`：线下；`ALL`：线上线下 |
| license_pic | 证照图片 | String | 64 | Y | 通过 [图片上传接口](https://paas.huifu.com/open/doc/api/#/shgl/shjj/api_shjj_shtpsc) 上传，文件类型：`F07`；**执照不能是过期的** |
| license_code | 证照编号 | String | 18 | Y | 工商营业执照编号；`use_head_info_flag=Y` 时不填；业主委员会填负责人身份证号 |
| license_type | 证照类型 | String | 32 | N | 参见 [机构证照类型](https://paas.huifu.com/open/doc/api/#/api_ggcsbm)；默认 `NATIONAL_LEGAL_MERGE` |
| license_validity_type | 证照有效期类型 | String | 1 | Y | 0：非长期有效；1：长期有效；`use_head_info_flag=Y` 时不填 |
| license_begin_date | 证照有效期开始 | String | 8 | Y | `yyyyMMdd`；`use_head_info_flag=Y` 时不填 |
| license_end_date | 证照有效期截止 | String | 8 | C | `license_validity_type=0` 时必填；为 1 时为空 |
| found_date | 成立时间 | String | 8 | Y | `yyyyMMdd` |
| reg_capital | 注册资本 | String | 16 | C | 保留两位小数；国营/私营/外资/事业单位/其他/集体经济必填，政府机构、个体工商户可为空 |
| business_scope | 经营范围 | String | 1000 | N | |
| reg_prov_id | 注册省 | String | 6 | N | 参考 [地区码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_dqbm) |
| reg_area_id | 注册市 | String | 6 | N | 同上 |
| reg_district_id | 注册区 | String | 6 | Y | 同上 |
| reg_detail | 注册详细地址 | String | 255 | Y | 商户注册地址或营业执照住所，汉字按 2 字符计 |
| prov_id | 经营省 | String | 6 | N | 参考 [地区编码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_dqbm) |
| area_id | 经营市 | String | 6 | N | 同上 |
| district_id | 经营区 | String | 6 | Y | 同上 |
| detail_addr | 经营详细地址 | String | 256 | C | `scene_type=OFFLINE/ALL` 时必填 |
| legal_name | 法人姓名 | String | 32 | Y | 最大 16 个汉字；名字含「·」时须与银行账号名一致 |
| legal_cert_type | 法人证件类型 | String | 2 | Y | 参考 [自然人证件类型](https://paas.huifu.com/open/doc/api/#/api_ggcsbm)；示例 `00`=身份证；开通全域资金管理仅支持身份证；04/11/14 需补 `F31`，13 需补 `F511`，15 需补 `F512`，其它补 `F32`（填写在 extended_material_list） |
| legal_cert_no | 法人证件号码 | String | 32 | Y | 年龄不能小于 18 岁且不能大于 80 岁 |
| legal_cert_validity_type | 法人证件有效期类型 | String | 1 | Y | 1：长期有效；0：非长期有效 |
| legal_cert_begin_date | 法人证件有效期开始 | String | 8 | Y | `yyyyMMdd` |
| legal_cert_end_date | 法人证件有效期截止 | String | 8 | C | `legal_cert_validity_type=0` 时必填 |
| legal_mobile_no | 法人手机号 | String | 11 | N | 全域资金业务必填 |
| legal_addr | 法人证件地址 | String | 256 | Y | |
| legal_cert_back_pic | 法人身份证国徽面 | String | 64 | Y | 图片上传，文件类型：`F03` |
| legal_cert_front_pic | 法人身份证人像面 | String | 64 | Y | 图片上传，文件类型：`F02` |
| beneficiary_info | 受益人列表 | String | - | N | jsonArray 字符串 |
| contact_name | 管理员姓名 | String | 128 | N | 默认法人姓名 |
| contact_mobile_no | 管理员手机号 | String | 11 | Y | 需为 11 位数字 |
| contact_email | 管理员电子邮箱 | String | 32 | Y | 需带 `@`，遵循邮箱格式 |
| sms_send_flag | 商户通知标识 | String | 1 | N | 进件成功后通知商户联系人：`M` 短信 / `E` 邮件 / `A` 都发 / 空 不通知 |
| login_name | 管理员账号 | String | 32 | Y | 用于商户平台登录，**全局唯一**，支持英文、数字、下划线，区分大小写 |
| service_phone | 客服电话 | String | 32 | N | 默认联系人手机号 |
| reg_acct_pic | 开户许可证 | String | 64 | C | 企业商户且结算账号为对公账户必填；文件类型：`F08` |
| card_info | 银行卡信息配置 | String | - | Y | 商户银行账户信息（jsonObject 字符串）；中信 E 管家结算配置在 `elec_acct_config` |
| settle_config | 结算业务配置 | String | - | N | 商户结算规则（jsonObject 字符串） |
| settle_card_front_pic | 银行卡卡号面 | String | 64 | C | 对私必填；文件类型：`F13` |
| settle_cert_back_pic | 持卡人身份证国徽面 | String | 64 | C | 对私必填；文件类型：`F56` |
| settle_cert_front_pic | 持卡人身份证人像面 | String | 64 | C | 对私必填；文件类型：`F55` |
| auth_entrust_pic | 授权委托书 | String | 64 | C | 对私非法人、对公非同名结算必填；文件类型：`F15`（E 管家需 `F520`） |
| cash_config | 取现业务配置 | String | - | N | 商户取现信息（jsonArray 字符串） |
| head_office_flag | 商户身份 | String | 1 | N | 1：总部商户，0：下级商户；不传默认普通商户。开通下级商户时上级需先调用 [开通下级商户权限配置接口](https://paas.huifu.com/open/doc/api/#/shgl/shywkt/api_shjj_shywkt_xjshpz)；`head_office_flag=1` 时 `head_type` 必填并补充对应材料（F718-F724 等）；为 0 时按客群类型补 F725-F727 |
| use_head_info_flag | 使用上级资料信息 | String | 1 | N | `Y` 则复用上级的执照、法人、注册地址、经营类型、mcc 等字段 |
| head_huifu_id | 上级汇付Id | String | 18 | C | `head_office_flag=0` 时必填；为 1 时不可传 |
| mer_url | 商户主页URL | String | 256 | N | |
| mer_icp | 商户ICP备案编号 | String | 50 | C | PC 网站且企业商户开通快捷/网银/大额转账/余额支付/分账（20%-100%），或个人商户分账（10%-100%）时必填 |
| store_header_pic | 店铺门头照 | String | 64 | C | `scene_type` 含线下场景时必填；文件类型：`F22`；微信/支付宝实名认证个人商户也用此字段 |
| store_indoor_pic | 店铺内景/工作区域照 | String | 64 | C | `scene_type` 含线下场景时必填；文件类型：`F24` |
| store_cashier_desk_pic | 店铺收银台/公司前台照 | String | 64 | C | `scene_type` 含线下场景时必填；文件类型：`F105` |
| ext_mer_id | 外部商户号 | String | 64 | N | **支持关联外部商户系统中的商户号**（本项目用于回填平台自有 merchantId） |
| remarks | 备注 | String | 300 | N | |
| async_return_url | 异步请求地址 | String | 120 | N | **审核结果消息接收地址**，为空时不推送消息 |
| elec_acct_config | 斗拱e账户功能配置 | String | - | N | jsonObject 字符串；用于下级商户配置银行电子账户功能 |
| share_holder_info_list | 股东信息 | String | - | N | jsonArray 字符串；全域资金业务新网银行必填 |
| extended_material_list | 扩展资料包 | String | - | N | jsonArray 字符串；按要求上传补充材料 |
| activated_products | 产品大类 | String | 32 | N | 01：一体化收款产品，02：账户与资金产品，03：业财数通产品；不传为空 |
| material_card_info | 对公卡信息 | String | - | Y | jsonObject 字符串；非个体工商户的企业商户选对私结算时，需补对公同名账户信息及证明图片（`F08`） |
| head_type | 总部客群 | String | 1 | C | `head_office_flag=1` 时必填：0 集团客户，1 大型商业综合体，2 品牌连锁客户 |

## 请求示例

```json
{
  "data": {
    "req_seq_id": "20220422267883697",
    "req_date": "20220422",
    "upper_huifu_id": "6666000003080000",
    "reg_name": "集成企业商户8664",
    "short_name": "企业商户3471",
    "ent_type": "1",
    "license_code": "20220422267883697",
    "license_validity_type": "1",
    "license_begin_date": "20200401",
    "license_end_date": "",
    "reg_prov_id": "350000",
    "reg_area_id": "350200",
    "reg_district_id": "350203",
    "reg_detail": "吉林省长春市思明区解放2路59096852",
    "legal_name": "陈立一",
    "legal_cert_type": "00",
    "legal_cert_no": "310112200001018888",
    "legal_cert_validity_type": "1",
    "legal_cert_begin_date": "20121201",
    "legal_cert_end_date": "20301201",
    "prov_id": "310000",
    "area_id": "310100",
    "district_id": "310104",
    "detail_addr": "吉林省长春市思明区解放1路49227677",
    "contact_name": "联系人",
    "contact_mobile_no": "13111112222",
    "contact_email": "jeff.peng@huifu.com",
    "service_phone": "021-121111221",
    "sms_send_flag": "Y",
    "login_name": "LG20220422267883697",
    "busi_type": "1",
    "receipt_name": "盈盈超市",
    "mcc": "5411",
    "async_return_url": "http://callback.example.com/sspm/testVirgo",
    "mer_en_name": "",
    "mer_url": "",
    "mer_icp": "",
    "license_type": "",
    "card_info": "{\"branch_code\":\"305290002096\",\"card_no\":\"98140008801800008888\",\"card_name\":\"上海以道数据服务中心\",\"card_type\":\"0\"}",
    "cash_config": "[{\"cash_type\":\"D0\",\"fix_amt\":\"1.00\",\"fee_rate\":\"\"},{\"cash_type\":\"D1\",\"fix_amt\":\"\",\"fee_rate\":\"10.00\"}]",
    "settle_config": "{\"settle_pattern\":\"\",\"is_priority_receipt\":\"\",\"settle_time\":\"\",\"settle_cycle\":\"D1\",\"min_amt\":\"1.00\",\"remained_amt\":\"2.00\",\"settle_abstract\":\"abstract\",\"out_settle_flag\":\"2\",\"out_settle_huifuid\":\"\",\"out_settle_acct_type\":\"\",\"fixed_ratio\":\"5.00\",\"settle_batch_no\":\"\"}"
  },
  "sys_id": "ssproxy_dev",
  "sign": "fTill2l5tg47MLUJ+wOg0wyEIcNtfVIBA9bGyLWZflkl/ZaT9pIYQiWUFJGgkbzWTfa+jI4bwnjHQg4u7lshAwQfxCzELyAkPC4rLm7jiGw0RbNTwgD6LWwzMDkbHkhMGxhaZfrVcK6XNPSHD1dqmKHj66A67+XeWFev50dEQE4=",
  "product_id": "ZDTEST"
}
```

## 返回参数

### 同步返回参数（data）

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| resp_code | 业务返回码 | String | 8 | Y | 参考 [业务返回码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_ywm)；示例：`00000000` |
| resp_desc | 业务返回描述 | String | 512 | Y | 示例：`处理成功` |
| huifu_id | 商户号 | String | 18 | N | 汇付商户号，示例：`6666000123123123` |
| apply_no | 申请单号 | String | 18 | N | 商户开户业务申请单号，示例：`2022012500073423` |
| token_no | 银行卡序列号 | String | 20 | N | 取现时使用，示例：`10004053462` |

### 异步返回参数（`async_return_url` 回调）

报文外层：

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| resp_code | 网关返回码 | String | 5 | Y | 参考 [网关返回码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_ywm)；示例：`10000` |
| resp_desc | 网关返回描述 | String | 512 | Y | 示例：`成功调用` |
| sign | 签名 | String | 512 | Y | 对报文整体签名，验签后处理 |
| data | 业务返回参数 | String | - | N | jsonObject 格式，见下表 |

data 内业务参数：

| 参数 | 中文名 | 定义 | 长度 | 必填 | 说明 |
|---|---|---|---|---|---|
| sub_resp_code | 业务返回码 | String | 8 | Y | 参考 [业务返回码](https://paas.huifu.com/open/doc/api/#/csfl/api_csfl_ywm)；示例：`00000000` |
| sub_resp_desc | 业务返回描述 | String | 512 | Y | 示例：`处理成功` |
| req_seq_id | 请求流水号 | String | 32 | Y | 原请求流水号（幂等关联） |
| req_date | 请求日期 | String | 8 | Y | 原请求日期 |
| audit_status | 审核结果 | String | 1 | Y | **`Y`：审核通过，`N`：审核拒绝，`F`：失败** |
| audit_desc | 审核描述 | String | 512 | N | 示例：`审核通过` |
| product_id | 产品号 | String | 32 | Y | 汇付分配的产品号 |
| huifu_id | 商户号 | String | 18 | N | 状态为审核中时返回 |
| apply_no | 申请单号 | String | 18 | N | 商户开户业务申请单号，状态为审核中时返回 |
| token_no | 银行卡序列号 | String | 20 | N | 取现时使用 |
| notify_type | 通知类型 | String | 1 | N | `Z`：电子账户 |
| elec_acct_result | 斗拱e账户开通结果 | String | - | N | jsonObject 格式；`notify_type=Z` 时返回 |

## 返回示例（成功）

```json
{
  "data": {
    "huifu_id": "6666000104778868",
    "resp_code": "00000000",
    "resp_desc": "成功",
    "token_no": "10000713406"
  },
  "sign": "o8bYN+DO5AaYl41idy04tZknaRnNORy8TgIztI6d8e3EtrSU9DbSjlN99DAgopNex6pEFFbQimxGxN8n9rADO4Xe7IZ9McPy2I9zJ0hccGpK9YBa2cqSMMzLCmxFiVqlz04RxrduBOHrfIsr4HM9Z3g6r8yOAL/FS1LqH7M3bCo="
}
```

## 关联接口（进件闭环用到的其余 API）

以下均在 [汇付文档中心](https://paas.huifu.com/open/doc/api/) 同一「进件管理」分组下，地址为站内锚点（前缀 `https://paas.huifu.com/open/doc/api/#`）：

| 接口 | 锚点 | 用途 |
|---|---|---|
| 图片上传 | `/shgl/shjj/api_shjj_shtpsc` | 进件前上传证照/身份证/银行卡等图片，换取 file_id |
| 申请单状态查询 | `/shgl/shjj/api_shjj_sqdztcx` | 进件后轮询审核状态（回调兜底） |
| 个人商户进件 | `/shgl/shjj/api_shjj_grshjbxxrz_kyc` | 无执照个人商户进件 |
| 商户统一进件（页面版） | `/shgl/shjj/api_shjj_shtyjjweb` | H5 页面自主进件（商户自己填资料） |
| 统一进件页面版查询 | `/shgl/shjj/api_shjj_shtyjjwebcx` | 页面版进件状态查询 |
| 商户基本信息修改 | `/shgl/shjj/api_shjj_shjbxxxg_kyc` | 进件后资料变更 |
| 商户业务开通 | `/shgl/shywkt/api_shjj_shywkt_kyc` | **进件通过后开通收款产品（否则无法收款）** |
| 商户业务开通修改 | `/shgl/shywkt/api_shjj_shywktxg_kyc` | 已开通业务的变更 |
| 商户详细信息查询 | `/shgl/shjj/api_shjj_shxxxxcx_kyc` | 查询商户在汇付的详细信息 |
| 商户状态变更 | `/shgl/api_shgl_shztbg` | 冻结/解冻等状态管理 |
| 商户费率信息查询 | `/shgl/shjj/api_merchant_conf_search_cx` | 查询商户费率 |
| 开通下级商户权限配置 | `/shgl/shywkt/api_shjj_shywkt_xjshpz` | 总部商户给下级商户开通权限 |
| 商户短信发送 | `/shgl/shjj/api_shjj_shdxfs` | 进件相关短信 |
| 接口加签验签说明 | `/open/doc/guide/#/api_v2jqyq`（独立文档） | 所有接口的签名/验签规范 |
| 业务返回码 | `/csfl/api_csfl_ywm` | resp_code / sub_resp_code 错误码表 |
| 汇付MCC编码 | `/csfl/api_csfl_hfmccbm` | mcc 行业码取值 |
| 地区码 | `/csfl/api_csfl_dqbm` | 省市区编码取值 |

## 本项目对接要点（多商户商城）

1. **关联锚点**：进件请求必传 `ext_mer_id` = 平台自有商户 ID，同步返回存 `huifu_id`、`apply_no` —— 三个字段构成 平台商户 ↔ 汇付商户 的双向映射。
2. **进件时序**：平台表单收资质 → 调图片上传接口逐张换 file_id → 调企业商户进件 → 拿到 `apply_no` 进入「审核中」→ 优先用 `async_return_url` 回调，另起定时任务调「申请单状态查询」兜底 → `audit_status=Y` 后调「商户业务开通」开通收款产品 → 商户方可收款。
3. **失败重试**：`audit_status=N/F` 记录 `audit_desc`，允许商户修改资料后重新进件（新 `req_seq_id`）。
4. **资金流**：买家付款直接进入商户汇付账户，平台不经手；结算/取现由商户在汇付侧配置（`settle_config`/`cash_config`），平台只读展示。

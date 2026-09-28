# P1 商户域实施计划（多商户商城一期）

> **前提**：本计划依据 `docs/superpowers/specs/2026-09-28-multi-merchant-mall-design.md`（状态：待评审）编写。
> 以 spec 评审通过为执行前提；执行中发现 spec 冲突时，先回改 spec 再改计划。

## 目标

新建 `yudao-module-mall/yudao-module-merchant`，完成 spec §12 P1 验收标准：

> 测试商户走完 **注册 → 填资质/传图 → 提交进件 → 汇付审核通过 → 业务开通 → 商户生效** 全链路（不含支付）。

## 硬约束

- Java 8 / Spring Boot 2.7.18：`javax.annotation.Resource`，不用 record / var / jakarta。
- 复用现有模式：mall 模块 DO `extends BaseDO`（表含 `tenant_id`，由租户拦截器维护）、`BaseMapperX`、`ErrorCodeConstants`、`BaseDbUnitTest`。
- 不引入新第三方依赖（HTTP 用 Spring 自带 `RestTemplate`，JSON 用 Jackson/hutool 已有栈）。
- 不抽佣、无分账、不碰资金（D1）；单店下单（D2，P3 范畴）；平台无人工预审（D5）。
- 每个任务以 `mvn test` 通过为完成标志；禁止占位符/TODO 实现。

## 一、文件结构

```
ruoyi-vue-pro/
├── pom.xml                                        # [改] 取消注释 <module>yudao-module-mall</module>
├── yudao-module-mall/pom.xml                      # [改] 增加 <module>yudao-module-merchant</module>
├── yudao-server/pom.xml                           # [改] 增加 yudao-module-merchant 依赖
├── yudao-server/src/main/resources/application.yaml  # [改] permit-all 增加回调路径 + merchant.huifu 配置
├── sql/mysql/2026-09-28-merchant-domain.sql       # [新] 三表 DDL + 菜单/角色/role_menu/定时任务
├── yudao-module-infra/.../api/file/FileApi.java   # [改] + getFileContent(fileId)
├── yudao-module-infra/.../api/file/FileApiImpl.java
├── yudao-module-system/.../api/permission/PermissionApi.java     # [改] + assignUserRole
├── yudao-module-system/.../api/permission/PermissionApiImpl.java
├── yudao-module-system/.../api/user/AdminUserApi.java            # [改] + updateUserStatus
├── yudao-module-system/.../api/user/AdminUserApiImpl.java
├── yudao-module-mall/yudao-module-merchant/
│   ├── pom.xml
│   └── src/main/java/cn/iocoder/yudao/module/merchant/
│       ├── enums/ErrorCodeConstants.java
│       ├── enums/MerchantStatusEnum.java            # 0草稿 1进件中 2生效 3拒绝 4停用
│       ├── enums/MerchantApplyStatusEnum.java       # 0进件中 1通过 2拒绝 3失败 4提交失败
│       ├── enums/MerchantImageBizTypeEnum.java      # F02/F03/F07/F08/F13/F15/F55/F56...
│       ├── dal/dataobject/merchant/MerchantDO.java
│       ├── dal/dataobject/apply/MerchantApplyDO.java
│       ├── dal/dataobject/image/MerchantImageDO.java
│       ├── dal/mysql/merchant/MerchantMapper.java
│       ├── dal/mysql/apply/MerchantApplyMapper.java
│       ├── dal/mysql/image/MerchantImageMapper.java
│       ├── framework/huifu/HuifuProperties.java      # @ConfigurationProperties("yudao.merchant.huifu")
│       ├── framework/huifu/HuifuConfiguration.java   # @EnableConfigurationProperties
│       ├── framework/huifu/HuifuSigner.java          # 加签/验签纯函数
│       ├── framework/huifu/HuifuClient.java          # 4 个接口封装（图片上传/KYC进件/状态查询/业务开通）
│       ├── service/merchant/MerchantService.java + Impl.java
│       ├── service/apply/MerchantApplyService.java + Impl.java
│       ├── controller/admin/merchant/MerchantController.java      # 运营商户列表/停用启用
│       ├── controller/admin/merchant/MerchantApplyController.java # 我的商户/提交进件/申请记录
│       ├── controller/admin/merchant/vo/...                        # VO 全套
│       ├── controller/admin/huifu/HuifuCallbackController.java    # @TenantIgnore 回调
│       └── job/HuifuApplyPollJob.java               # 轮询兜底 + 开通重试
│   └── src/test/java/...                            # 单测（见各任务）
│   └── src/test/resources/sql/{create_tables.sql,clean.sql}
├── yudao-ui-admin-vben/apps/web-antd/src/
│   ├── api/mall/merchant/index.ts                   # [新]
│   ├── views/mall/merchant/apply.vue                # [新] 入驻申请表单
│   ├── views/mall/merchant/merchant.vue             # [新] 运营商户列表
│   └── views/_core/authentication/register.vue      # [改] 注册成功后调 merchant/init
└── yudao-ui-admin-vben/apps/web-antd/src/.../.http  # [新] 接口调试脚本
```

## 二、任务

---

### Task 1：SQL 脚本（表 + 菜单 + 角色 + 定时任务）

**文件**：`sql/mysql/2026-09-28-merchant-domain.sql`（新，参照 `sql/mysql/2026-09-18-multilevel-brokerage.sql` 头注释格式）

**ID 分配（已验证不冲突）**：菜单 2030-2034（现 max 2026）、角色 170/171（现 max 162，AUTO_INCREMENT=164）、role_menu 8800-8813（现 max 8704）、infra_job 12904（AUTO_INCREMENT=12904）。

```sql
-- 多商户商城一期：商户域（P1）
-- 关联 spec：docs/superpowers/specs/2026-09-28-multi-merchant-mall-design.md

-- ========== 1. 三张新表 ==========

CREATE TABLE `merchant` (
  `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '商户编号',
  `user_id`                 bigint       NOT NULL DEFAULT '0' COMMENT '绑定的 admin 用户编号',
  `name`                    varchar(64)  NOT NULL DEFAULT '' COMMENT '商户名称',
  `short_name`              varchar(64)  NOT NULL DEFAULT '' COMMENT '商户简称',
  `logo`                    varchar(255) NOT NULL DEFAULT '' COMMENT '店铺 LOGO',
  `remark`                  varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `status`                  tinyint      NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1进件中 2生效 3拒绝 4停用',
  `huifu_id`                varchar(18)  NOT NULL DEFAULT '' COMMENT '汇付商户号',
  `ext_mer_id`              varchar(64)  NOT NULL DEFAULT '' COMMENT '外部商户号（=本表 id，进件时写入汇付）',
  `contact_name`            varchar(128) NOT NULL DEFAULT '' COMMENT '联系人姓名',
  `contact_mobile`          varchar(11)  NOT NULL DEFAULT '' COMMENT '联系人手机号',
  `contact_email`           varchar(32)  NOT NULL DEFAULT '' COMMENT '联系人邮箱',
  `settle_card_no_masked`   varchar(32)  NOT NULL DEFAULT '' COMMENT '结算卡号（脱敏）',
  `submit_time`             datetime              DEFAULT NULL COMMENT '最近进件提交时间',
  `effect_time`             datetime              DEFAULT NULL COMMENT '商户生效时间',
  `creator`                 varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                 varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`               bigint       NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_huifu_id` (`huifu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户主表';

CREATE TABLE `merchant_apply` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '申请单编号',
  `merchant_id`     bigint       NOT NULL DEFAULT '0' COMMENT '商户编号',
  `req_seq_id`      varchar(32)  NOT NULL COMMENT '汇付请求流水号（当日唯一）',
  `req_date`        varchar(8)   NOT NULL COMMENT '汇付请求日期（yyyyMMdd）',
  `apply_no`        varchar(18)  NOT NULL DEFAULT '' COMMENT '汇付申请单号',
  `status`          tinyint      NOT NULL DEFAULT '4' COMMENT '状态：0进件中 1通过 2拒绝 3失败 4提交失败',
  `audit_status`    varchar(1)   NOT NULL DEFAULT '' COMMENT '汇付审核结果：Y/N/F',
  `audit_desc`      varchar(512) NOT NULL DEFAULT '' COMMENT '汇付审核描述',
  `huifu_id`        varchar(18)  NOT NULL DEFAULT '' COMMENT '汇付商户号',
  `token_no`        varchar(20)  NOT NULL DEFAULT '' COMMENT '银行卡序列号',
  `submit_time`     datetime              DEFAULT NULL COMMENT '提交时间',
  `audit_time`      datetime              DEFAULT NULL COMMENT '审核时间',
  `raw_notify_json` text                  DEFAULT NULL COMMENT '回调/响应原始报文',
  `creator`         varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_seq_id` (`req_seq_id`),
  KEY `idx_merchant_status` (`merchant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户进件申请单';

CREATE TABLE `merchant_image` (
  `id`              bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
  `merchant_id`     bigint      NOT NULL DEFAULT '0' COMMENT '商户编号',
  `biz_type`        varchar(8)  NOT NULL COMMENT '图片类型（汇付 file_type）',
  `infra_file_id`   bigint      NOT NULL DEFAULT '0' COMMENT '平台 infra 文件编号',
  `huifu_file_id`   varchar(128) NOT NULL DEFAULT '' COMMENT '汇付图片文件 ID',
  `creator`         varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`       bigint      NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_biz` (`merchant_id`, `biz_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户资质图片映射';
```

**菜单**（列：id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted —— 无 tenant_id）：

```sql
-- 2030 商户中心（目录，挂在商城系统 449 下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2030, '商户中心', '', 1, 10, 449, 'merchant', 'ep:shop', NULL, NULL, 0, 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
-- 2031 入驻申请（商户自查自提交）
INSERT INTO `system_menu` VALUES (2031, '入驻申请', 'merchant:apply:query', 2, 1, 2030, 'apply', 'ep:document', 'mall/merchant/apply/index', 'MerchantApply', 0, 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
-- 2032 商户管理（平台运营）
INSERT INTO `system_menu` VALUES (2032, '商户管理', 'merchant:merchant:query', 2, 2, 2030, 'merchant', 'ep:shop', 'mall/merchant/merchant/index', 'Merchant', 0, 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
-- 按钮
INSERT INTO `system_menu` VALUES (2033, '进件提交', 'merchant:apply:submit', 3, 1, 2031, '', '', NULL, NULL, 0, 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
INSERT INTO `system_menu` VALUES (2034, '商户停用/启用', 'merchant:merchant:update', 3, 1, 2032, '', '', NULL, NULL, 0, 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
```

> 注：第 2/3 条用了全列 `INSERT INTO ... VALUES`，执行前按上面第 1 条的列清单核对列数（19 列）。

**角色 + 授权**（role 列：id,name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,create_time,updater,update_time,deleted,tenant_id）：

```sql
INSERT INTO `system_role` VALUES (170, '待生效商家', 'merchant_pending', 100, 1, '', 0, 2, '注册后未通过汇付审核的商家', '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role` VALUES (171, '商家', 'merchant', 99, 1, '', 0, 2, '已通过汇付审核生效的商家', '1', NOW(), '1', NOW(), b'0', 0);

-- role_menu（列：id,role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id）
-- 超管角色 1 → 全部新菜单
INSERT INTO `system_role_menu` VALUES (8800, 1, 2030, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8801, 1, 2031, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8802, 1, 2032, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8803, 1, 2033, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8804, 1, 2034, '1', NOW(), '1', NOW(), b'0', 0);
-- 待生效商家 170 → 仅入驻申请
INSERT INTO `system_role_menu` VALUES (8810, 170, 2030, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8811, 170, 2031, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8812, 170, 2033, '1', NOW(), '1', NOW(), b'0', 0);
-- 商家 171 → 入驻申请（P1 相同，P2 起扩展商品/订单菜单）
INSERT INTO `system_role_menu` VALUES (8813, 171, 2030, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8814, 171, 2031, '1', NOW(), '1', NOW(), b'0', 0);
INSERT INTO `system_role_menu` VALUES (8815, 171, 2033, '1', NOW(), '1', NOW(), b'0', 0);
```

**定时任务**（infra_job 列：id,name,status,handler_name,handler_param,cron_expression,retry_count,retry_interval,monitor_timeout,creator,create_time,updater,update_time,deleted）：

```sql
INSERT INTO `infra_job` VALUES (12904, '汇付进件状态轮询', 0, 'huifuApplyPollJob', '', '0 */5 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0');
```

**验证**：在本地库执行脚本 → `SELECT MAX(id) FROM system_menu` 应 ≥2034；`SELECT * FROM infra_job WHERE id=12904` 有记录；重复执行脚本不应因 UNIQUE 冲突半途失败（建议手工核对一次，无需幂等化）。

---

### Task 2：模块骨架 + 三处 pom 接线 + 错误码 + 状态枚举

**改动文件**：

1. `ruoyi-vue-pro/pom.xml`：`<module>yudao-module-mall</module>` 取消注释。
2. `yudao-module-mall/pom.xml`：`<modules>` 增加 `<module>yudao-module-merchant</module>`。
3. `yudao-server/pom.xml`：依赖列表增加：

```xml
<dependency>
    <groupId>cn.iocoder.boot</groupId>
    <artifactId>yudao-module-merchant</artifactId>
    <version>${revision}</version>
</dependency>
```

4. **新** `yudao-module-mall/yudao-module-merchant/pom.xml`（以 promotion pom 为模板，去掉 product/trade/member 依赖）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>cn.iocoder.boot</groupId>
        <artifactId>yudao-module-mall</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>yudao-module-merchant</artifactId>

    <name>${project.artifactId}</name>

    <description>
        merchant 模块，商户域：入驻申请、汇付进件、商户状态管理。
    </description>

    <dependencies>
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-module-system</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-module-infra</artifactId>
            <version>${revision}</version>
        </dependency>

        <!-- 业务组件 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-spring-boot-starter-biz-tenant</artifactId>
        </dependency>

        <!-- Web 相关 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-spring-boot-starter-security</artifactId>
        </dependency>

        <!-- DB 相关 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-spring-boot-starter-mybatis</artifactId>
        </dependency>

        <!-- Test 测试相关 -->
        <dependency>
            <groupId>cn.iocoder.boot</groupId>
            <artifactId>yudao-spring-boot-starter-test</artifactId>
        </dependency>
    </dependencies>

</project>
```

5. **新** `enums/ErrorCodeConstants.java`（错误码段 1-012，已验证全仓无占用）：

```java
package cn.iocoder.yudao.module.merchant.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Merchant 错误码枚举类
 *
 * merchant 系统，使用 1-012-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商户 1-012-001-000 ==========
    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_012_001_000, "商户不存在");
    ErrorCode MERCHANT_USER_NOT_BIND = new ErrorCode(1_012_001_001, "当前账号未绑定商户");
    ErrorCode MERCHANT_DISABLE = new ErrorCode(1_012_001_002, "商户已停用");
    ErrorCode MERCHANT_NAME_EXISTS = new ErrorCode(1_012_001_003, "商户名称({})已存在");

    // ========== 商户进件申请 1-012-002-000 ==========
    ErrorCode MERCHANT_APPLY_NOT_EXISTS = new ErrorCode(1_012_002_000, "进件申请单不存在");
    ErrorCode MERCHANT_APPLY_EXISTS_DOING = new ErrorCode(1_012_002_001, "存在进件中的申请单，请等待汇付审核结果");
    ErrorCode MERCHANT_APPLY_ILLEGAL_STATUS = new ErrorCode(1_012_002_002, "申请单状态({})不允许该操作");
    ErrorCode MERCHANT_APPLY_PARAM_INVALID = new ErrorCode(1_012_002_003, "进件参数不合法：{}");

    // ========== 汇付交互 1-012-003-000 ==========
    ErrorCode HUIFU_SIGN_ERROR = new ErrorCode(1_012_003_000, "汇付加签/验签失败：{}");
    ErrorCode HUIFU_REQUEST_ERROR = new ErrorCode(1_012_003_001, "汇付请求异常：{}");
    ErrorCode HUIFU_RESP_CODE_ERROR = new ErrorCode(1_012_003_002, "汇付业务返回码({})：{}");
    ErrorCode HUIFU_CALLBACK_VERIFY_ERROR = new ErrorCode(1_012_003_003, "汇付回调验签失败");

}
```

6. **新** `enums/MerchantStatusEnum.java`：

```java
package cn.iocoder.yudao.module.merchant.enums;

/**
 * 商户状态枚举
 */
public enum MerchantStatusEnum {

    DRAFT(0, "草稿"),
    APPLYING(1, "进件中"),
    EFFECTIVE(2, "生效"),
    REJECTED(3, "拒绝"),
    DISABLED(4, "停用");

    private final Integer status;
    private final String name;

    MerchantStatusEnum(Integer status, String name) {
        this.status = status;
        this.name = name;
    }

    public Integer getStatus() { return status; }
    public String getName() { return name; }
}
```

7. **新** `enums/MerchantApplyStatusEnum.java`（同款结构）：`DOING(0,"进件中")`、`APPROVED(1,"通过")`、`REJECTED(2,"拒绝")`、`FAILED(3,"失败")`、`SUBMIT_FAILED(4,"提交失败")`。

8. **新** `enums/MerchantImageBizTypeEnum.java`：`LEGAL_CERT_FRONT("F02","法人身份证人像面")`、`LEGAL_CERT_BACK("F03","法人身份证国徽面")`、`LICENSE("F07","营业执照")`、`REG_ACCT("F08","开户许可证")`、`SETTLE_CARD_FRONT("F13","结算银行卡卡号面")`、`AUTH_ENTRUST("F15","授权委托书")`、`SETTLE_CERT_FRONT("F55","持卡人身份证人像面")`、`SETTLE_CERT_BACK("F56","持卡人身份证国徽面")` —— 同款枚举结构（code + desc）。

**验证**：`mvn -q compile -pl yudao-module-mall/yudao-module-merchant -am` 通过（此时模块仅含 enums）。

---

### Task 3：三张 DO + Mapper + H2 测试建表 + Mapper 测试

DO 一律 mall 惯例：`extends BaseDO`（表含 `tenant_id`，由租户拦截器维护，DO 不带 tenantId 字段）+ `@TableName` + `@KeySequence("xxx_seq")` + `@TableId` + Lombok 五件套（`@Data @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true) @Builder @NoArgsConstructor @AllArgsConstructor`），参照 `ProductBrandDO`。

- `dal/dataobject/merchant/MerchantDO.java`（`@TableName("merchant")`）字段：`Long id; Long userId; String name; String shortName; String logo; String remark; Integer status; String huifuId; String extMerId; String contactName; String contactMobile; String contactEmail; String settleCardNoMasked; LocalDateTime submitTime; LocalDateTime effectTime;`
- `dal/dataobject/apply/MerchantApplyDO.java`（`@TableName("merchant_apply")`）字段：`Long id; Long merchantId; String reqSeqId; String reqDate; String applyNo; Integer status; String auditStatus; String auditDesc; String huifuId; String tokenNo; LocalDateTime submitTime; LocalDateTime auditTime; String rawNotifyJson;`
- `dal/dataobject/image/MerchantImageDO.java`（`@TableName("merchant_image")`）字段：`Long id; Long merchantId; String bizType; Long infraFileId; String huifuFileId;`

`dal/mysql/merchant/MerchantMapper.java`：

```java
@Mapper
public interface MerchantMapper extends BaseMapperX<MerchantDO> {

    default MerchantDO selectByUserId(Long userId) {
        return selectOne(MerchantDO::getUserId, userId);
    }

    default MerchantDO selectByHuifuId(String huifuId) {
        return selectOne(MerchantDO::getHuifuId, huifuId);
    }

    default PageResult<MerchantDO> selectPage(MerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantDO>()
                .likeIfPresent(MerchantDO::getName, reqVO.getName())
                .eqIfPresent(MerchantDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MerchantDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantDO::getId));
    }

}
```

`dal/mysql/apply/MerchantApplyMapper.java`（`BaseMapperX<MerchantApplyDO>`）：`selectByReqSeqId(reqSeqId)`、`selectDoingByMerchantId(merchantId)`（status=DOING）、`selectListByMerchantId(merchantId)`（id desc）、`selectDoingListBySubmitTimeLt(beforeTime)`（status=DOING 且 submit_time < beforeTime，轮询兜底用）、`selectApprovedList()`（status=APPROVED，开通重试用）。

`dal/mysql/image/MerchantImageMapper.java`：`selectListByMerchantId(merchantId)`。

`controller/admin/merchant/vo/MerchantPageReqVO.java`（Mapper 依赖，本任务建立）：`extends PageParam`，字段 `String name; Integer status; LocalDateTime[] createTime;`（`@Data @EqualsAndHashCode(callSuper = true)` + `@Schema`）。

**H2 测试建表** `src/test/resources/sql/create_tables.sql`（列名双引号 + `GENERATED BY DEFAULT AS IDENTITY` + `"tenant_id" bigint NOT NULL DEFAULT 0`，与 promotion 模块 test 资源同风格）：三张表列名与 Task 1 DDL 一致（`merchant` / `merchant_apply`（`req_seq_id varchar(32) NOT NULL`、`status tinyint NOT NULL DEFAULT 4`）/ `merchant_image`），`deleted` 用 `bit NOT NULL DEFAULT FALSE`。

`src/test/resources/sql/clean.sql`：

```sql
DELETE FROM "merchant";
DELETE FROM "merchant_apply";
DELETE FROM "merchant_image";
```

**测试** `src/test/java/.../dal/mysql/merchant/MerchantMapperTest.java`（Mapper 由 `YudaoMybatisAutoConfiguration` 的 `@MapperScan("${yudao.info.base-package}")` 自动扫描，无需 `@Import`）：

```java
public class MerchantMapperTest extends BaseDbUnitTest {

    @Resource
    private MerchantMapper merchantMapper;

    @Test
    public void testSelectByUserId() {
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> o.setStatus(MerchantStatusEnum.DRAFT.getStatus()));
        merchantMapper.insert(dbMerchant);
        assertPojoEquals(dbMerchant, merchantMapper.selectByUserId(dbMerchant.getUserId()));
    }

    @Test
    public void testSelectPage() {
        MerchantDO match = randomPojo(MerchantDO.class, o -> {
            o.setName("芋道商户");
            o.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
            o.setCreateTime(buildTime(2026, 9, 1));
        });
        merchantMapper.insert(match);
        merchantMapper.insert(cloneIgnoreId(match, o -> o.setName("别家")));
        merchantMapper.insert(cloneIgnoreId(match, o -> o.setStatus(MerchantStatusEnum.DRAFT.getStatus())));
        MerchantPageReqVO reqVO = new MerchantPageReqVO();
        reqVO.setName("芋道");
        reqVO.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
        reqVO.setCreateTime(new LocalDateTime[]{buildTime(2026, 8, 1), buildTime(2026, 9, 28)});
        PageResult<MerchantDO> result = merchantMapper.selectPage(reqVO);
        assertEquals(1, result.getTotal());
        assertPojoEquals(match, result.getList().get(0));
    }

}
```

**验证**：`mvn -q test -pl yudao-module-mall/yudao-module-merchant -am -Dtest=MerchantMapperTest`

---

### Task 4：HuifuSigner 加签/验签纯函数 + 单测

**文件** `framework/huifu/HuifuSigner.java`（新）。规则见 `docs/huifu/接口加签验签说明.md`：仅对 `body.data` 第一层按 key ASCII 字典序排序后序列化（嵌套值以 JSON 字符串传递、不再排序）；SHA256WithRSA + Base64；请求用平台私钥，响应/回调用汇付公钥；**同步返参排序、异步回调不排序**。

```java
package cn.iocoder.yudao.module.merchant.framework.huifu;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_SIGN_ERROR;

@Slf4j
public class HuifuSigner {

    /** 对 data 第一层按 key 字典序排序后序列化 */
    public static String sortJson(Map<String, Object> data) {
        return JsonUtils.toJsonString(new TreeMap<>(data));
    }

    /** 平台私钥加签（data 第一层排序后加签） */
    public static String sign(Map<String, Object> data, String privateKeyBase64) {
        return signText(sortJson(data), privateKeyBase64);
    }

    public static String signText(String text, String privateKeyBase64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(privateKeyBase64);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(privateKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw exception(HUIFU_SIGN_ERROR, e.getMessage());
        }
    }

    /** 汇付公钥验签（同步返参：data 第一层排序后验签） */
    public static boolean verify(Map<String, Object> data, String publicKeyBase64, String sign) {
        return verifyText(sortJson(data), publicKeyBase64, sign);
    }

    /** 汇付公钥验签（异步回调：data 原文不排序） */
    public static boolean verifyRaw(String dataJson, String publicKeyBase64, String sign) {
        return verifyText(dataJson, publicKeyBase64, sign);
    }

    private static boolean verifyText(String text, String publicKeyBase64, String sign) {
        if (StrUtil.isBlank(sign)) {
            return false;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initVerify(publicKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("[verifyText][验签异常]", e);
            return false;
        }
    }

    private HuifuSigner() {
    }

}
```

**测试** `src/test/java/.../framework/huifu/HuifuSignerTest.java`（纯 JUnit + 运行时生成 RSA 密钥对，无 Spring）：

```java
public class HuifuSignerTest {

    private static KeyPair KEY_PAIR;

    @BeforeAll
    public static void beforeAll() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KEY_PAIR = generator.generateKeyPair();
    }

    private static String privateKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    }

    private static String publicKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    @Test
    public void testSortJson_asciiOrder() {
        Map<String, Object> data = new HashMap<>();
        data.put("b", "2");
        data.put("a", "1");
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", HuifuSigner.sortJson(data));
    }

    @Test
    public void testSortJson_nestedStringNotSorted() {
        Map<String, Object> data = new HashMap<>();
        data.put("z", "1");
        data.put("nested", "{\"z\":1,\"a\":2}");
        // 嵌套 JSON 字符串原样保留（不二次排序）
        assertEquals("{\"nested\":\"{\\\"z\\\":1,\\\"a\\\":2}\",\"z\":\"1\"}", HuifuSigner.sortJson(data));
    }

    @Test
    public void testSignAndVerifySync_roundTrip() {
        Map<String, Object> data = new HashMap<>();
        data.put("req_seq_id", "M20260928000001-1024");
        data.put("req_date", "20260928");
        String sign = HuifuSigner.sign(data, privateKey());
        assertTrue(HuifuSigner.verify(data, publicKey(), sign));
        // 篡改后验签失败
        data.put("req_date", "20260929");
        assertFalse(HuifuSigner.verify(data, publicKey(), sign));
    }

    @Test
    public void testVerifyRaw_callbackNotSorted() {
        String raw = "{\"req_seq_id\":\"M1\",\"audit_status\":\"Y\"}";
        String sign = HuifuSigner.signText(raw, privateKey());
        assertTrue(HuifuSigner.verifyRaw(raw, publicKey(), sign));
        assertFalse(HuifuSigner.verifyRaw("{\"req_seq_id\":\"M2\",\"audit_status\":\"Y\"}", publicKey(), sign));
    }

}
```

**验证**：`mvn -q test -pl yudao-module-mall/yudao-module-merchant -am -Dtest=HuifuSignerTest`

---

### Task 5：HuifuProperties + HuifuClient（4 个接口）+ 单测

**文件 1** `framework/huifu/HuifuProperties.java`（模板参照 `yudao-module-pay/.../PayProperties`）：

```java
@ConfigurationProperties(prefix = "yudao.merchant.huifu")
@Data
public class HuifuProperties {

    /** 渠道商/平台 huifu_id（公共参数 sys_id） */
    private String sysId;
    /** 汇付产品号（公共参数 product_id） */
    private String productId;
    /** 平台私钥（Base64，PKCS8） */
    private String privateKey;
    /** 汇付公钥（Base64，X509） */
    private String huifuPublicKey;
    /** 网关地址 */
    private String gatewayUrl = "https://api.huifu.com";
    /** 进件审核结果回调地址（KYC async_return_url） */
    private String kycCallbackUrl;
    /** 业务开通结果回调地址（busi_async_return_url） */
    private String busiCallbackUrl;
    /** 微信配置对象 JSON 串（wx_conf_list，含费率） */
    private String wxConfList;
    /** 支付宝配置对象 JSON 串（ali_conf_list，含费率） */
    private String aliConfList;
    /** 线上业务类型编码（scene_type=ONLINE/ALL 时必填） */
    private String onlineBusiType;

}
```

**文件 2** `framework/huifu/HuifuConfiguration.java`：

```java
@Configuration
@EnableConfigurationProperties(HuifuProperties.class)
public class HuifuConfiguration {
}
```

**文件 3** `framework/huifu/HuifuClient.java`（`RestTemplate` 为 Spring 自带；`buildBody` / `parseResponse` 包级可见，供单测）：

```java
@Slf4j
@Component
public class HuifuClient {

    @Resource
    private HuifuProperties properties;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    /** 图片上传（multipart，无加签无验签） */
    public String uploadPicture(byte[] content, String fileName, String fileType,
                               String huifuId, String reqSeqId, String reqDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", reqSeqId);
        data.put("req_date", reqDate);
        data.put("file_type", fileType);
        if (StrUtil.isNotBlank(huifuId)) {
            data.put("huifu_id", huifuId);
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("sys_id", properties.getSysId());
        form.add("product_id", properties.getProductId());
        form.add("data", JsonUtils.toJsonString(data));
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getGatewayUrl() + "/v2/supplementary/picture",
                new HttpEntity<>(form, headers), String.class);
        Map<String, Object> respData = parseResponse(response.getBody(), false);
        assertRespCode(respData);
        return (String) respData.get("file_id");
    }

    /** 企业商户进件 KYC */
    public Map<String, Object> entOpen(Map<String, Object> data) {
        return post("/v2/merchant/basicdata/ent", data);
    }

    /** 申请单状态查询 */
    public Map<String, Object> queryApplyStatus(Map<String, Object> data) {
        return post("/v2/merchant/basicdata/status/query", data);
    }

    /** 商户业务开通 */
    public Map<String, Object> busiOpen(Map<String, Object> data) {
        return post("/v2/merchant/busi/open", data);
    }

    /** 组装并加签请求体（包级可见，供单测） */
    Map<String, Object> buildBody(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sys_id", properties.getSysId());
        body.put("product_id", properties.getProductId());
        body.put("data", new TreeMap<>(data));
        body.put("sign", HuifuSigner.sign(data, properties.getPrivateKey()));
        return body;
    }

    private Map<String, Object> post(String path, Map<String, Object> data) {
        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getGatewayUrl() + path, buildBody(data), String.class);
        return parseResponse(response.getBody(), true);
    }

    /** 解析响应 data；verifySign=true 时用汇付公钥验签（同步返参排序） */
    Map<String, Object> parseResponse(String raw, boolean verifySign) {
        Map<String, Object> body = JsonUtils.parseMap(raw);
        if (body == null) {
            throw exception(HUIFU_REQUEST_ERROR, "响应报文为空");
        }
        Object dataObj = body.get("data");
        Map<String, Object> data = dataObj instanceof String
                ? JsonUtils.parseMap((String) dataObj)
                : JsonUtils.convertObject(dataObj, new TypeReference<Map<String, Object>>() {});
        if (data == null) {
            throw exception(HUIFU_REQUEST_ERROR, "响应 data 为空");
        }
        if (verifySign && !HuifuSigner.verify(data, properties.getHuifuPublicKey(), (String) body.get("sign"))) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        return data;
    }

    private void assertRespCode(Map<String, Object> data) {
        String respCode = (String) data.get("resp_code");
        if (!"00000000".equals(respCode)) {
            throw exception(HUIFU_RESP_CODE_ERROR, respCode, data.get("resp_desc"));
        }
    }

}
```

> `HUIFU_REQUEST_ERROR` / `HUIFU_RESP_CODE_ERROR` / `HUIFU_CALLBACK_VERIFY_ERROR` 为 Task 2 已建错误码（`HUIFU_SIGN_ERROR` 同）。
> 单测中需要拿到 platform 私钥的验签能力以构造“响应”：`HuifuProperties#huifuPublicKey` 在测试里被设为平台公钥即可（见下）。

**测试** `src/test/java/.../framework/huifu/HuifuClientTest.java`（纯 JUnit，反射注入 properties，不发起 HTTP）：

```java
public class HuifuClientTest {

    private static KeyPair KEY_PAIR;
    private HuifuClient huifuClient;
    private HuifuProperties properties;

    @BeforeAll
    public static void beforeAll() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KEY_PAIR = generator.generateKeyPair();
    }

    @BeforeEach
    public void setUp() {
        properties = new HuifuProperties();
        properties.setSysId("sys_test");
        properties.setProductId("PROD_TEST");
        properties.setPrivateKey(Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded()));
        properties.setHuifuPublicKey(Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
        huifuClient = new HuifuClient();
        ReflectionTestUtils.setField(huifuClient, "properties", properties);
    }

    @Test
    public void testBuildBody_signed() {
        Map<String, Object> data = new HashMap<>();
        data.put("req_seq_id", "M1");
        data.put("req_date", "20260928");
        Map<String, Object> body = huifuClient.buildBody(data);
        assertEquals("sys_test", body.get("sys_id"));
        assertEquals("PROD_TEST", body.get("product_id"));
        assertNotNull(body.get("sign"));
        // 签名可用平台公钥验证（模拟汇付侧）
        assertTrue(HuifuSigner.verify(data, properties.getHuifuPublicKey(), (String) body.get("sign")));
    }

    @Test
    public void testParseResponse_verifySuccess() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resp_code", "00000000");
        data.put("huifu_id", "6666000000000001");
        String dataJson = JsonUtils.toJsonString(data);
        String sign = HuifuSigner.signText(HuifuSigner.sortJson(data), properties.getPrivateKey());
        String raw = "{\"data\":" + dataJson + ",\"sign\":\"" + sign + "\"}";
        Map<String, Object> result = huifuClient.parseResponse(raw, true);
        assertEquals("6666000000000001", result.get("huifu_id"));
    }

    @Test
    public void testParseResponse_verifyFail() {
        String raw = "{\"data\":{\"resp_code\":\"00000000\"},\"sign\":\"invalid\"}";
        assertServiceException(() -> huifuClient.parseResponse(raw, true), HUIFU_CALLBACK_VERIFY_ERROR);
    }

    @Test
    public void testAssertRespCode_error() {
        // resp_code 非 00000000 时由调用方（Service）判定，Client 仅提供 parseResponse
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resp_code", "10020001");
        data.put("resp_desc", "参数错误");
        String raw = "{\"data\":" + JsonUtils.toJsonString(data) + ",\"sign\":\"\"}";
        Map<String, Object> result = huifuClient.parseResponse(raw, false);
        assertEquals("10020001", result.get("resp_code"));
    }

}
```

**验证**：`mvn -q test -pl yudao-module-mall/yudao-module-merchant -am -Dtest=HuifuClientTest`
（图片上传 multipart 与真实 HTTP 联网验证放到 Task 13 端到端，不写假集成测试。）

---

### Task 6：跨模块 API 扩展（FileApi / PermissionApi / AdminUserApi）

merchant 模块依赖 system、infra 模块，通过现有 `*Api` 接口跨模块调用。三处新增方法（均为已存在底层能力的最小暴露）。

**6.1** `yudao-module-infra/.../api/file/FileApi.java` 增加：

```java
/**
 * 读取文件内容
 *
 * @param fileId 文件编号
 * @return 文件内容
 */
byte[] getFileContent(Long fileId) throws Exception;
```

`FileApiImpl` 增加：

```java
@Override
public byte[] getFileContent(Long fileId) throws Exception {
    FileDO file = fileService.getFile(fileId);
    if (file == null) {
        return null;
    }
    return fileService.getFileContent(file.getConfigId(), file.getPath());
}
```

（`FileDO` = `cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO`；`FileService#getFile(Long)`、`getFileContent(Long, String)` 已存在。）

**6.2** `yudao-module-system/.../api/permission/PermissionApi.java` 增加：

```java
/**
 * 赋予用户角色
 *
 * @param userId  用户编号
 * @param roleIds 角色编号集合
 */
void assignUserRole(Long userId, Set<Long> roleIds);
```

`PermissionApiImpl` 增加：`@Override public void assignUserRole(Long userId, Set<Long> roleIds) { permissionService.assignUserRole(userId, roleIds); }`（`PermissionService#assignUserRole(Long, Set<Long>)` 已存在，line 94）。

**6.3** `yudao-module-system/.../api/user/AdminUserApi.java` 增加：

```java
/**
 * 更新用户状态
 *
 * @param id     用户编号
 * @param status 状态
 */
void updateUserStatus(Long id, Integer status);
```

`AdminUserApiImpl` 增加：`@Override public void updateUserStatus(Long id, Integer status) { userService.updateUserStatus(id, status); }`（`AdminUserServiceImpl#updateUserStatus(Long, Integer)` 已存在，line 252，禁用时会清理 token）。

**验证**：`mvn -q compile -pl yudao-module-infra,yudao-module-system -am`

---

### Task 7：MerchantService（注册建草稿 / 查询 / 平台停用启用 / 生效）

**新增常量** `constant/MerchantConstants.java`：

```java
public interface MerchantConstants {

    /** 角色：待生效商家（sql/mysql/2026-09-28-merchant-domain.sql） */
    Long ROLE_ID_MERCHANT_PENDING = 170L;
    /** 角色：商家 */
    Long ROLE_ID_MERCHANT = 171L;

}
```

**补充错误码**（追加到 Task 2 的 `ErrorCodeConstants` 商户段）：

```java
ErrorCode MERCHANT_STATUS_ILLEGAL = new ErrorCode(1_012_001_004, "商户状态({})不允许该操作");
```

**接口** `service/merchant/MerchantService.java`：

```java
public interface MerchantService {

    /** 当前用户初始化商户草稿（幂等）；不存在则创建并赋予「待生效商家」角色 */
    Long initMerchant(Long userId);

    MerchantDO getMerchant(Long id);

    MerchantDO getMerchantByUserId(Long userId);

    PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO);

    /** 平台：停用(4)/启用(2) 商户，并联动 admin 账号状态 */
    void updateMerchantStatus(Long id, Integer status);

    /** 商户生效（进件通过 + 业务开通成功后调用）：状态置 2 + 赋「商家」角色 */
    void effectMerchant(Long id, String huifuId);

    void validateMerchantExists(Long id);

    /** 当前登录用户绑定的商户，未绑定抛 MERCHANT_USER_NOT_BIND */
    MerchantDO getLoginMerchant();

}
```

**实现** `service/merchant/MerchantServiceImpl.java` 要点（真实代码骨架）：

```java
@Service
@Validated
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long initMerchant(Long userId) {
        MerchantDO exist = merchantMapper.selectByUserId(userId);
        if (exist != null) {
            return exist.getId();
        }
        // 1. 建草稿
        MerchantDO merchant = MerchantDO.builder()
                .userId(userId).name("").shortName("").logo("").remark("")
                .status(MerchantStatusEnum.DRAFT.getStatus())
                .huifuId("").contactName("").contactMobile("").contactEmail("")
                .settleCardNoMasked("").build();
        merchantMapper.insert(merchant);
        // 2. ext_mer_id 恒等于 merchant.id，回写
        MerchantDO update = new MerchantDO();
        update.setId(merchant.getId());
        update.setExtMerId(String.valueOf(merchant.getId()));
        merchantMapper.updateById(update);
        // 3. 赋角色（待生效商家）
        permissionApi.assignUserRole(userId, Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT_PENDING));
        return merchant.getId();
    }

    @Override
    public MerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public MerchantDO getMerchantByUserId(Long userId) {
        return merchantMapper.selectByUserId(userId);
    }

    @Override
    public PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO) {
        return merchantMapper.selectPage(pageReqVO);
    }

    @Override
    public void updateMerchantStatus(Long id, Integer status) {
        MerchantDO merchant = validateMerchantExists(id);
        if (!Objects.equals(status, MerchantStatusEnum.DISABLED.getStatus())
                && !Objects.equals(status, MerchantStatusEnum.EFFECTIVE.getStatus())) {
            throw exception(MERCHANT_STATUS_ILLEGAL, status);
        }
        if (Objects.equals(merchant.getStatus(), MerchantStatusEnum.EFFECTIVE.getStatus())
                && Objects.equals(status, MerchantStatusEnum.EFFECTIVE.getStatus())) {
            return; // 已生效，幂等
        }
        // 1. 更新商户状态
        MerchantDO update = new MerchantDO();
        update.setId(id);
        update.setStatus(status);
        merchantMapper.updateById(update);
        // 2. 联动账号状态（停用=禁用，启用=开启）
        adminUserApi.updateUserStatus(merchant.getUserId(),
                Objects.equals(status, MerchantStatusEnum.DISABLED.getStatus())
                        ? CommonStatusEnum.DISABLE.getStatus() : CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public void effectMerchant(Long id, String huifuId) {
        MerchantDO merchant = validateMerchantExists(id);
        MerchantDO update = new MerchantDO();
        update.setId(id);
        update.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
        update.setHuifuId(huifuId);
        update.setEffectTime(LocalDateTime.now());
        merchantMapper.updateById(update);
        // 角色：待生效商家 → 商家
        permissionApi.assignUserRole(merchant.getUserId(), Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT));
    }

    @Override
    public MerchantDO validateMerchantExists(Long id) {
        MerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    @Override
    public MerchantDO getLoginMerchant() {
        MerchantDO merchant = merchantMapper.selectByUserId(SecurityFrameworkUtils.getLoginUserId());
        if (merchant == null) {
            throw exception(MERCHANT_USER_NOT_BIND);
        }
        return merchant;
    }

}
```

**测试** `service/merchant/MerchantServiceImplTest.java`（`BaseDbUnitTest` + `@Import({MerchantServiceImpl.class, MockConfig.class})`，`MockConfig` 为静态 `@TestConfiguration`，`@Bean` 返回 `Mockito.mock(PermissionApi.class)` / `mock(AdminUserApi.class)`）：

- `testInitMerchant_success`：调用返回 id 非空；`merchantMapper.selectById(id).getExtMerId()` 等于 id 字符串；`status=0`；`verify(permissionApi).assignUserRole(userId, Collections.singleton(170L))`。
- `testInitMerchant_exists`：先插一条该 user 的商户，再调 `initMerchant` 返回原 id，`verify(permissionApi, never()).assignUserRole(any(), any())`。
- `testUpdateMerchantStatus_disable`：插 EFFECTIVE 商户 → 调 disable → 状态 4 + `verify(adminUserApi).updateUserStatus(userId, CommonStatusEnum.DISABLE.getStatus())`。
- `testUpdateMerchantStatus_illegal`：调 status=1 → `assertServiceException(..., MERCHANT_STATUS_ILLEGAL)`。

**验证**：`mvn -q test -pl yudao-module-mall/yudao-module-merchant -am -Dtest=MerchantServiceImplTest`

---

### Task 8：MerchantApplyService 提交状态机 + 单测

**接口** `service/apply/MerchantApplyService.java`：

```java
public interface MerchantApplyService {

    /** 提交进件（图片推汇付 → KYC 进件）；返回申请单编号 */
    Long submitApply(Long merchantId, MerchantApplySubmitReqVO reqVO);

    /** 处理审核结果（回调 + 轮询共用，按 apply.status 幂等）；Y 时继续业务开通 */
    void handleAuditResult(String reqSeqId, String auditStatus, String auditDesc, String rawJson);

    /** 商户业务开通（审核通过后调用；已生效则幂等返回） */
    void openBusiness(MerchantApplyDO apply);

    /** 商户自查申请单列表 */
    List<MerchantApplyDO> getApplyListByMerchantId(Long merchantId);

    /** 轮询兜底 + 开通重试（定时任务调用） */
    void pollApplyStatus();

}
```

**实现** `service/apply/MerchantApplyServiceImpl.java` 关键逻辑（真实代码骨架）：

```java
@Service
@Validated
public class MerchantApplyServiceImpl implements MerchantApplyService {

    @Resource
    private MerchantApplyMapper merchantApplyMapper;
    @Resource
    private MerchantImageMapper merchantImageMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private MerchantService merchantService;
    @Resource
    private HuifuClient huifuClient;
    @Resource
    private HuifuProperties huifuProperties;
    @Resource
    private FileApi fileApi;

    @Override
    public Long submitApply(Long merchantId, MerchantApplySubmitReqVO reqVO) {
        // 1. 商户校验
        MerchantDO merchant = merchantService.validateMerchantExists(merchantId);
        if (Objects.equals(merchant.getStatus(), MerchantStatusEnum.DISABLED.getStatus())) {
            throw exception(MERCHANT_DISABLE);
        }
        if (merchantApplyMapper.selectDoingByMerchantId(merchantId) != null) {
            throw exception(MERCHANT_APPLY_EXISTS_DOING);
        }
        // 2. 基础信息落库（名称/联系人/结算卡脱敏）
        MerchantDO merchantUpdate = new MerchantDO();
        merchantUpdate.setId(merchantId);
        merchantUpdate.setName(reqVO.getRegName());
        merchantUpdate.setShortName(reqVO.getShortName());
        merchantUpdate.setContactName(reqVO.getContactName());
        merchantUpdate.setContactMobile(reqVO.getContactMobileNo());
        merchantUpdate.setContactEmail(reqVO.getContactEmail());
        merchantUpdate.setSettleCardNoMasked(maskCardNo(reqVO.getCardNo()));
        merchantMapper.updateById(merchantUpdate);
        // 3. 先落「提交失败」申请单（spec §10：超时/异常留痕、可重试）
        String reqSeqId = buildReqSeqId(merchantId);
        String reqDate = LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd");
        MerchantApplyDO apply = MerchantApplyDO.builder()
                .merchantId(merchantId).reqSeqId(reqSeqId).reqDate(reqDate).applyNo("")
                .status(MerchantApplyStatusEnum.SUBMIT_FAILED.getStatus())
                .auditStatus("").auditDesc("").huifuId("").tokenNo("")
                .submitTime(LocalDateTime.now()).build();
        merchantApplyMapper.insert(apply);
        // 4. 图片逐张推汇付（失败直接抛出，申请单停留「提交失败」）
        Map<String, String> huifuFileIds = uploadImages(merchantId, reqVO, reqSeqId, reqDate);
        // 5. KYC 进件
        Map<String, Object> data = buildKycData(merchant, reqVO, huifuFileIds, reqSeqId, reqDate);
        Map<String, Object> resp = huifuClient.entOpen(data);
        apply.setRawNotifyJson(JsonUtils.toJsonString(resp));
        if (!"00000000".equals(resp.get("resp_code"))) {
            apply.setAuditDesc(String.valueOf(resp.get("resp_desc")));
            merchantApplyMapper.updateById(apply);
            throw exception(HUIFU_RESP_CODE_ERROR, resp.get("resp_code"), resp.get("resp_desc"));
        }
        // 6. 进件中
        apply.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
        apply.setApplyNo((String) resp.get("apply_no"));
        apply.setHuifuId((String) resp.get("huifu_id"));
        merchantApplyMapper.updateById(apply);
        // 7. 商户状态 → 进件中，并回写 huifu_id
        MerchantDO statusUpdate = new MerchantDO();
        statusUpdate.setId(merchantId);
        statusUpdate.setStatus(MerchantStatusEnum.APPLYING.getStatus());
        statusUpdate.setHuifuId((String) resp.get("huifu_id"));
        statusUpdate.setSubmitTime(LocalDateTime.now());
        merchantMapper.updateById(statusUpdate);
        return apply.getId();
    }

    @Override
    public void handleAuditResult(String reqSeqId, String auditStatus, String auditDesc, String rawJson) {
        MerchantApplyDO apply = merchantApplyMapper.selectByReqSeqId(reqSeqId);
        if (apply == null) {
            log.warn("[handleAuditResult][reqSeqId({}) 无对应申请单，忽略]", reqSeqId);
            return;
        }
        // 幂等：非「进件中」直接返回
        if (!Objects.equals(apply.getStatus(), MerchantApplyStatusEnum.DOING.getStatus())) {
            return;
        }
        if ("P".equals(auditStatus)) {
            return; // 仍审核中
        }
        apply.setAuditStatus(auditStatus);
        apply.setAuditDesc(auditDesc);
        apply.setRawNotifyJson(rawJson);
        apply.setAuditTime(LocalDateTime.now());
        if ("Y".equals(auditStatus)) {
            apply.setStatus(MerchantApplyStatusEnum.APPROVED.getStatus());
            merchantApplyMapper.updateById(apply);
            // 审核通过 → 业务开通（失败由定时任务重试）
            openBusiness(apply);
            return;
        }
        // N 拒绝 / F 失败 → 商户可修改后重新提交
        apply.setStatus("N".equals(auditStatus)
                ? MerchantApplyStatusEnum.REJECTED.getStatus() : MerchantApplyStatusEnum.FAILED.getStatus());
        merchantApplyMapper.updateById(apply);
        MerchantDO merchantUpdate = new MerchantDO();
        merchantUpdate.setId(apply.getMerchantId());
        merchantUpdate.setStatus(MerchantStatusEnum.REJECTED.getStatus());
        merchantMapper.updateById(merchantUpdate);
    }

    @Override
    public void openBusiness(MerchantApplyDO apply) {
        MerchantDO merchant = merchantMapper.selectById(apply.getMerchantId());
        if (merchant == null || Objects.equals(merchant.getStatus(), MerchantStatusEnum.EFFECTIVE.getStatus())) {
            return; // 已生效，幂等
        }
        String huifuId = StrUtil.isNotBlank(merchant.getHuifuId()) ? merchant.getHuifuId() : apply.getHuifuId();
        if (StrUtil.isBlank(huifuId)) {
            log.warn("[openBusiness][apply({}) 无 huifu_id，等待下次重试]", apply.getId());
            throw exception(HUIFU_REQUEST_ERROR, "缺少汇付商户号 huifu_id");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", buildReqSeqId(merchant.getId()));
        data.put("req_date", LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd"));
        data.put("huifu_id", huifuId);
        data.put("short_name", merchant.getShortName());
        if (StrUtil.isNotBlank(huifuProperties.getWxConfList())) {
            data.put("wx_conf_list", huifuProperties.getWxConfList());
        }
        if (StrUtil.isNotBlank(huifuProperties.getAliConfList())) {
            data.put("ali_conf_list", huifuProperties.getAliConfList());
        }
        if (StrUtil.isNotBlank(huifuProperties.getOnlineBusiType())) {
            data.put("online_busi_type", huifuProperties.getOnlineBusiType());
        }
        data.put("async_return_url", huifuProperties.getBusiCallbackUrl());
        data.put("busi_async_return_url", huifuProperties.getBusiCallbackUrl());
        Map<String, Object> resp = huifuClient.busiOpen(data);
        if (!"00000000".equals(resp.get("resp_code"))) {
            throw exception(HUIFU_RESP_CODE_ERROR, resp.get("resp_code"), resp.get("resp_desc"));
        }
        // 开通申请已受理 → 商户生效 + 赋「商家」角色
        merchantService.effectMerchant(merchant.getId(), huifuId);
    }

}
```

> `uploadImages`：遍历 VO 中非空的图片 id → `fileApi.getFileContent(fileId)` → `huifuClient.uploadPicture(content, bizType + ".jpg", bizType.getCode(), merchant.getHuifuId(), reqSeqId + "-" + bizType.getCode(), reqDate)` → `merchantImageMapper.insert(new MerchantImageDO(...))`。
> `LocalDateTimeUtil` = `cn.hutool.core.date.LocalDateTimeUtil`（hutool，`yyyyMMdd` 格式化请求日期）。
> `buildKycData`：按 `docs/huifu/企业商户进件-KYC.md` 字段表逐项 put（`ext_mer_id=merchant.getId()`、`license_pic`/`legal_cert_front_pic`/`legal_cert_back_pic` 必填、`card_info` 用 `JsonUtils.toJsonString` 生成 jsonObject 字符串、`async_return_url=huifuProperties.getKycCallbackUrl()`；`license_validity_type=0` 时才放 `license_end_date`，法人同理）。条件图（F08/F13/F55/F56/F15）有值才 put。
> `maskCardNo`：保留前 6 后 4：`cardNo.substring(0, 6) + "****" + cardNo.substring(cardNo.length() - 4)`（长度 <10 时整体打码）。

**测试** `service/apply/MerchantApplyServiceImplTest.java`（`BaseDbUnitTest` + `@Import({MerchantApplyServiceImpl.class, MockConfig.class})`；MockConfig 提供 `mock(HuifuClient.class)`、`mock(FileApi.class)`、`mock(MerchantService.class)`、`mock(HuifuProperties.class)`）：

- `testSubmitApply_blockedByDoing`：插 apply(status=0) → `assertServiceException(..., MERCHANT_APPLY_EXISTS_DOING)`。
- `testSubmitApply_success`：`when(huifuClient.entOpen(any())).thenReturn(Map.of(...))`（Java 8 用 `CollectionUtils`/`new HashMap<>()`）返回 `{resp_code=00000000, apply_no=xxx, huifu_id=6666...}` → 调用后 `apply.status=0`、`applyNo` 已写、`merchant.status=1`。
- `testSubmitApply_huifuBusinessError`：`.thenReturn({resp_code=10020001, resp_desc=参数错误})` → `assertServiceException(..., HUIFU_RESP_CODE_ERROR)` 且 apply 仍为 `SUBMIT_FAILED`。
- `testHandleAuditResult_pass_openBusiness`：apply(status=0) → 调 `handleAuditResult(reqSeqId, "Y", "审核通过", "{}")` → apply=1 且 `verify(merchantService).effectMerchant(merchantId, huifuId)`。
- `testHandleAuditResult_reject`：`"N"` → apply=2、merchant=3、`verify(merchantService, never()).effectMerchant(any(), any())`。
- `testHandleAuditResult_idempotent`：apply 已是 1 → 再调不改变状态、不再开通。

**验证**：`mvn -q test -pl yudao-module-mall/yudao-module-merchant -am -Dtest=MerchantApplyServiceImplTest`

---

### Task 9：汇付回调控制器（验签 + 幂等）+ 配置放行

**文件** `controller/admin/huifu/HuifuCallbackController.java`（新）：

```java
@Tag(name = "管理后台 - 汇付回调")
@RestController
@RequestMapping("/merchant/huifu")
@Validated
@Slf4j
public class HuifuCallbackController {

    @Resource
    private HuifuProperties huifuProperties;
    @Resource
    private MerchantApplyService merchantApplyService;

    /**
     * 进件审核结果回调（async_return_url）
     *
     * 异步回调 data 原文不排序验签（docs/huifu/接口加签验签说明.md）。
     */
    @PostMapping("/kyc-callback")
    @PermitAll
    @TenantIgnore
    public String kycCallback(@RequestBody String rawBody) {
        log.info("[kycCallback][收到回调] raw={}", rawBody);
        Map<String, Object> body = JsonUtils.parseMap(rawBody);
        if (body == null) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        // 原文提取 data（字符串或对象），不重排字段
        Object dataObj = body.get("data");
        String dataJson = dataObj instanceof String
                ? (String) dataObj : JsonUtils.parseTree(rawBody).get("data").toString();
        if (!HuifuSigner.verifyRaw(dataJson, huifuProperties.getHuifuPublicKey(), (String) body.get("sign"))) {
            log.error("[kycCallback][验签失败] raw={}", rawBody);
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        Map<String, Object> data = JsonUtils.parseMap(dataJson);
        merchantApplyService.handleAuditResult((String) data.get("req_seq_id"),
                (String) data.get("audit_status"), (String) data.get("audit_desc"), dataJson);
        return "success";
    }

    /**
     * 业务开通结果回调（busi_async_return_url）
     *
     * P1 仅应答（审核通过后的开通结果以同步受理为准，见 Task 8 openBusiness）。
     */
    @PostMapping("/busi-callback")
    @PermitAll
    @TenantIgnore
    public String busiCallback(@RequestBody String rawBody) {
        log.info("[busiCallback][收到回调] raw={}", rawBody);
        Map<String, Object> body = JsonUtils.parseMap(rawBody);
        Object dataObj = body != null ? body.get("data") : null;
        String dataJson = dataObj instanceof String
                ? (String) dataObj : (body != null ? JsonUtils.parseTree(rawBody).get("data").toString() : "{}");
        if (body == null
                || !HuifuSigner.verifyRaw(dataJson, huifuProperties.getHuifuPublicKey(), (String) body.get("sign"))) {
            log.error("[busiCallback][验签失败] raw={}", rawBody);
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        Map<String, Object> data = JsonUtils.parseMap(dataJson);
        String ordId = data != null ? (String) data.get("ord_id") : null;
        // 汇付要求应答 RECV_ORD_ID_{ord_id}（商户业务开通文档）
        return StrUtil.isNotBlank(ordId) ? "RECV_ORD_ID_" + ordId : "success";
    }

}
```

> `@TenantIgnore`：回调无租户上下文，申请单按全局唯一 `req_seq_id` 定位（`uk_req_seq_id`），跨租户可写。
> `@PermitAll` + `security.permit-all_urls` 双保险（见下）。

**配置改动** `yudao-server/src/main/resources/application.yaml`：

1. `yudao.security.permit-all_urls` 增加一行（参照现有 `/admin-api/mp/open/**`）：

```yaml
      - /admin-api/merchant/huifu/** # 汇付进件/业务开通回调，无需登录
```

2. 文件末尾（或 `yudao:` 节点下）增加：

```yaml
yudao:
  merchant:
    huifu:
      sys-id: 替换为渠道商 huifu_id
      product-id: 替换为汇付产品号
      private-key: 替换为平台私钥（Base64, PKCS8）
      huifu-public-key: 替换为汇付公钥（Base64, X509）
      gateway-url: https://api.huifu.com
      kyc-callback-url: http://替换为公网域名/admin-api/merchant/huifu/kyc-callback
      busi-callback-url: http://替换为公网域名/admin-api/merchant/huifu/busi-callback
      wx-conf-list: '[{"pay_scene":"1","fee_rate":"0.38","mcc":"","pay_channel_id":"","fee_rule_id":"","service_codes":""}]'
      ali-conf-list: '[{"pay_scene":"1","fee_rate":"0.38","mcc":"","pay_channel_id":"","indirect_level":""}]'
      online-busi-type: 替换为线上业务类型编码
```

> 上述 5 个「替换为…」是**部署配置**（沙箱联调时注入），不是代码占位符；本地单测不读该配置（测试用 `new HuifuProperties()` 注入）。

**测试**：回调验签逻辑已在 `HuifuClientTest#testParseResponse_verifyFail` / `HuifuSignerTest#testVerifyRaw_callbackNotSorted` 覆盖；控制器为薄胶水层，纳入 Task 13 端到端验证。

**验证**：`mvn -q compile -pl yudao-module-mall/yudao-module-merchant`

---

### Task 10：轮询兜底 + 开通重试定时任务

**服务方法**（补进 `MerchantApplyServiceImpl`，接口 Task 8 已声明）：

```java
@Override
public void pollApplyStatus() {
    // 1. 进件中且提交超 15 分钟 → 调申请单状态查询
    List<MerchantApplyDO> doingList = merchantApplyMapper.selectDoingListBySubmitTimeLt(
            LocalDateTime.now().minusMinutes(15));
    for (MerchantApplyDO apply : doingList) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("req_seq_id", apply.getReqSeqId());
            data.put("req_date", apply.getReqDate());
            data.put("apply_no", apply.getApplyNo());
            data.put("huifu_id", apply.getHuifuId());
            Map<String, Object> resp = huifuClient.queryApplyStatus(data);
            String applyStatus = (String) resp.get("apply_status");
            if (StrUtil.isBlank(applyStatus)) {
                continue;
            }
            // Y→通过 / N→拒绝 / F→失败；P 表示仍审核中（handleAuditResult 内部已忽略 P）
            handleAuditResult(apply.getReqSeqId(), applyStatus,
                    (String) resp.get("apply_reason"), JsonUtils.toJsonString(resp));
        } catch (Exception e) {
            log.error("[pollApplyStatus][申请单({}) 查询失败]", apply.getId(), e);
        }
    }
    // 2. 审核通过但未生效 → 业务开通重试
    for (MerchantApplyDO apply : merchantApplyMapper.selectApprovedList()) {
        MerchantDO merchant = merchantMapper.selectById(apply.getMerchantId());
        if (merchant == null || Objects.equals(merchant.getStatus(), MerchantStatusEnum.EFFECTIVE.getStatus())) {
            continue;
        }
        try {
            openBusiness(apply);
        } catch (Exception e) {
            log.error("[pollApplyStatus][申请单({}) 业务开通重试失败]", apply.getId(), e);
        }
    }
}
```

**Job** `job/HuifuApplyPollJob.java`（模板 `CouponExpireJob`）：

```java
@Component
public class HuifuApplyPollJob implements JobHandler {

    @Resource
    private MerchantApplyService merchantApplyService;

    @Override
    @TenantJob
    public String execute(String param) {
        merchantApplyService.pollApplyStatus();
        return "汇付进件轮询完成";
    }

}
```

> 定时任务记录在 Task 1 SQL：`infra_job.id=12904`、`handler_name='huifuApplyPollJob'`（= bean 名）、`cron='0 */5 * * * ?'`。

**测试**：轮询分支已由 Task 8 的 `handleAuditResult` / `openBusiness` 单测覆盖；本任务只加胶水层（`pollApplyStatus` 可另加一条“无数据时静默返回”的冒烟测试，可选）。

**验证**：`mvn -q compile -pl yudao-module-mall/yudao-module-merchant`

---

### Task 11：Admin 控制器 + VO + 接口调试脚本

**VO**（`controller/admin/merchant/vo/`）：
- `MerchantPageReqVO`（Task 3 已建）
- `MerchantRespVO`：`extends MerchantDO` 常用字段 + `createTime`（`@Schema` 标注）
- `MerchantApplySubmitReqVO`：KYC 表单字段，`@NotNull` 校验必填项 —— `regName, shortName, receiptName, entType, busiType, mcc, sceneType, licenseCode, licenseValidityType, licenseBeginDate, licenseEndDate, foundDate, regDistrictId, regDetail, districtId, detailAddr, legalName, legalCertType, legalCertNo, legalCertValidityType, legalCertBeginDate, legalCertEndDate, legalAddr, contactName, contactMobileNo, contactEmail, loginName, cardBranchCode, cardNo, cardName, cardType` + 图片 id：`licensePicId(F07), legalCertFrontId(F02), legalCertBackId(F03), regAcctId(F08), settleCardFrontId(F13), settleCertFrontId(F55), settleCertBackId(F56), authEntrustId(F15)`
- `MerchantApplyRespVO`：`extends MerchantApplyDO`（`id, status, auditStatus, auditDesc, huifuId, submitTime, auditTime` 展示）

**控制器 1** `controller/admin/merchant/MerchantController.java`：

```java
@Tag(name = "管理后台 - 商户")
@RestController
@RequestMapping("/merchant/merchant")
@Validated
public class MerchantController {

    @Resource
    private MerchantService merchantService;
    @Resource
    private MerchantApplyService merchantApplyService;

    @PostMapping("/init")
    @Operation(summary = "初始化我的商户（注册后调用，幂等）")
    public CommonResult<Long> initMerchant() {
        return success(merchantService.initMerchant(SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my")
    @Operation(summary = "获得我的商户")
    public CommonResult<MerchantRespVO> getMyMerchant() {
        return success(BeanUtils.toBean(merchantService.getLoginMerchant(), MerchantRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商户分页")
    @PreAuthorize("@ss.hasPermission('merchant:merchant:query')")
    public CommonResult<PageResult<MerchantRespVO>> getMerchantPage(@Valid MerchantPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(merchantService.getMerchantPage(pageReqVO), MerchantRespVO.class));
    }

    @PutMapping("/update-status")
    @Operation(summary = "停用/启用商户")
    @Parameter(name = "id", description = "编号", required = true)
    @Parameter(name = "status", description = "状态：2生效 4停用", required = true)
    @PreAuthorize("@ss.hasPermission('merchant:merchant:update')")
    public CommonResult<Boolean> updateMerchantStatus(@RequestParam("id") Long id,
                                                     @RequestParam("status") Integer status) {
        merchantService.updateMerchantStatus(id, status);
        return success(true);
    }

}
```

**控制器 2** `controller/admin/merchant/MerchantApplyController.java`：

```java
@Tag(name = "管理后台 - 商户进件申请")
@RestController
@RequestMapping("/merchant/apply")
@Validated
public class MerchantApplyController {

    @Resource
    private MerchantApplyService merchantApplyService;
    @Resource
    private MerchantService merchantService;

    @PostMapping("/submit")
    @Operation(summary = "提交进件申请")
    @PreAuthorize("@ss.hasPermission('merchant:apply:submit')")
    public CommonResult<Long> submitApply(@Valid @RequestBody MerchantApplySubmitReqVO reqVO) {
        return success(merchantApplyService.submitApply(merchantService.getLoginMerchant().getId(), reqVO));
    }

    @GetMapping("/my-list")
    @Operation(summary = "获得我的进件申请列表")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<List<MerchantApplyRespVO>> getMyApplyList() {
        Long merchantId = merchantService.getLoginMerchant().getId();
        return success(BeanUtils.toBean(merchantApplyService.getApplyListByMerchantId(merchantId),
                MerchantApplyRespVO.class));
    }

}
```

> `BeanUtils` = `cn.iocoder.yudao.framework.common.util.object.BeanUtils`；`SecurityFrameworkUtils` = `cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils`。

**接口调试脚本** `src/test/resources/merchant-api.http`（参照同仓 `.http` 风格）：`init` → `my` → `submit`（表单 JSON，图片 id 用 `/admin-api/infra/file/upload` 返回）→ `my-list` → `page` → `update-status`。

**验证**：`mvn -q compile -pl yudao-module-mall/yudao-module-merchant`

---

### Task 12：vben 前端（商户注册绑定 + 入驻表单 + 运营商户列表）

工作目录：`E:\dm-project\yudao\yudao-ui-admin-vben\apps\web-antd\src`。

**12.1** 新增 `api/mall/merchant/index.ts`（namespace + `requestClient` 模式，参照 `api/mall/product/spu/index.ts`）：

```ts
import { requestClient } from '#/api/request';

export namespace MerchantApi {
  export interface Merchant {
    id: number;
    userId: number;
    name: string;
    shortName: string;
    status: number; // 0草稿 1进件中 2生效 3拒绝 4停用
    huifuId: string;
    contactName: string;
    contactMobile: string;
    contactEmail: string;
    createTime: string;
  }

  export interface MerchantPageParams {
    pageNo: number;
    pageSize: number;
    name?: string;
    status?: number;
  }

  export interface ApplySubmit {
    regName: string;
    shortName: string;
    receiptName: string;
    entType: string;
    busiType: string;
    mcc: string;
    sceneType: string;
    licenseCode: string;
    licenseValidityType: string;
    licenseBeginDate: string;
    licenseEndDate?: string;
    foundDate: string;
    regDistrictId: string;
    regDetail: string;
    districtId: string;
    detailAddr: string;
    legalName: string;
    legalCertType: string;
    legalCertNo: string;
    legalCertValidityType: string;
    legalCertBeginDate: string;
    legalCertEndDate?: string;
    legalAddr: string;
    contactName: string;
    contactMobileNo: string;
    contactEmail: string;
    loginName: string;
    cardBranchCode: string;
    cardNo: string;
    cardName: string;
    cardType: string;
    licensePicId: number;
    legalCertFrontId: number;
    legalCertBackId: number;
    regAcctId?: number;
    settleCardFrontId?: number;
    settleCertFrontId?: number;
    settleCertBackId?: number;
    authEntrustId?: number;
  }
}

/** 注册后初始化商户草稿（幂等） */
export function initMerchant() {
  return requestClient.post<number>('/merchant/merchant/init');
}

export function getMyMerchant() {
  return requestClient.get<MerchantApi.Merchant>('/merchant/merchant/my');
}

export function getMerchantPage(params: MerchantApi.MerchantPageParams) {
  return requestClient.get('/merchant/merchant/page', { params });
}

export function updateMerchantStatus(id: number, status: number) {
  return requestClient.put('/merchant/merchant/update-status', null, { params: { id, status } });
}

export function submitApply(data: MerchantApi.ApplySubmit) {
  return requestClient.post<number>('/merchant/apply/submit', data);
}

export function getMyApplyList() {
  return requestClient.get('/merchant/apply/my-list');
}
```

**12.2** 新增入驻申请页 `views/mall/merchant/apply/index.vue`（菜单 2031 `component=mall/merchant/apply/index`）：
- 顶部展示 `getMyMerchant().status` 状态标签（0草稿/1进件中/2生效/3拒绝/4停用）；
- `Form` 分四组：企业信息 / 法人信息 / 联系人 / 结算卡信息；
- 图片上传用 `@/api/infra/file` 的 `uploadFile(data, onUploadProgress)`，回填各 `xxxId`（营业执照 F07、法人正反面 F02/F03 必填，其余条件项可选）；
- 提交按钮 `@PreAuthorize` 对应权限 `merchant:apply:submit`（前端用 `v-access:code` 控制），调 `submitApply`；成功后刷新状态为「进件中」；
- 下方表格展示 `getMyApplyList()`（状态/审核意见/提交时间）。

**12.3** 新增运营商户列表 `views/mall/merchant/merchant/index.vue`（菜单 2032 `component=mall/merchant/merchant/index`）：
- 查询表单（名称 + 状态）；表格列（ID/名称/简称/状态/汇付号/联系人/创建时间）；
- 操作列「停用 / 启用」→ 二次确认 → `updateMerchantStatus(id, 4|2)`（`v-access:code="['merchant:merchant:update']"`）。

**12.4** 修改 `views/_core/authentication/register.vue`：注册成功后调用 `initMerchant()` 建草稿并绑定「待生效商家」角色（失败不阻断登录）。

```ts
import { initMerchant } from '#/api/mall/merchant';

async function handleRegister() {
  // ... 现有校验与 values 组装不变
  await authStore.authLogin('register', values, async () => {
    try {
      await initMerchant();
    } catch (e) {
      // 初始化失败不阻断登录（可稍后在「入驻申请」页重试）
      console.error('[register][initMerchant 失败]', e);
    }
    await router.push('/');
  });
}
```

> 现有实现若为 `await authStore.authLogin('register', values); router.push(...)`，改为传入 `onSuccess` 回调写法（`authLogin` 第三个参数已支持）。

**验证**：`pnpm install && pnpm dev`（在 `yudao-ui-admin-vben` 根）启动 web-antd，用「待生效商家」账号登录仅见「商户中心 → 入驻申请」。

---

### Task 13：端到端验收（汇付沙箱）

**前置**：`application.yaml` 注入真实 `sys-id/product-id/private-key/huifu-public-key/kyc-callback-url`（回调需公网可达，可用内网穿透）；执行 Task 1 SQL。

**验收步骤（对照 spec §12 P1 完成标准）**：

1. 启动 `yudao-server`，`admin-api` 可访问；`SELECT * FROM infra_job WHERE id=12904` 存在且启用。
2. 用真实手机号注册新账号 → 数据库 `merchant` 出现草稿行（`status=0`、`ext_mer_id=id`）；`system_user_role` 出现角色 170 绑定。
3. 登录 vben → 仅「商户中心 → 入驻申请」可见 → 填表（用汇付沙箱测试资质）并上传图片 → 提交。
4. 校验：`merchant_apply` 出现 `status=0`、`apply_no`/`huifu_id` 非空；`merchant.status=1`；`merchant_image` 每张图有 `huifu_file_id`；服务端日志无加签异常。
5. 在汇付沙箱控制台将申请单审核为通过（或等真实审核）→ 观察回调日志 `[kycCallback][收到回调]`：
   - 验签失败 → 立即排查公钥/报文（`resp_code` ≠ 00000000 时记录完整返回体）；
   - 验签成功 → `merchant_apply.status=1` → 自动调 `busi/open` → `merchant.status=2`、`effect_time` 非空、`huifu_id` 已写；账号角色由 170 变为 171。
6. 若回调未到：等待 5 分钟，`huifuApplyPollJob` 触发状态查询兜底，结果同上（日志 `[pollApplyStatus]`）。
7. 审核拒绝路径：沙箱提交一份会被拒的资质 → 回调/轮询后 `merchant_apply.status=2`、`audit_desc` 有拒绝原因、`merchant.status=3`；商户可修改后重新提交（生成新 `req_seq_id`，旧单留档）。
8. 平台运营：超管登录 → 商户管理分页可见 → 停用 → `merchant.status=4` 且该账号被禁用（登录被拒）；启用恢复。
9. 重复提交保护：`status=0` 期间再次点提交 → 返回「存在进件中的申请单」。
10. 回调幂等：手工重放同一回调报文 2 次 → 状态不再变化、无重复开通。

**回归**：`mvn test -pl yudao-module-mall/yudao-module-merchant -am` 全绿；`yudao-server` 本地启动无 bean 冲突。

---

## 三、Self-Review（计划自检）

| 检查项 | 结论 |
|---|---|
| 无占位符/TODO 代码 | 代码片段均为可直接落地的实现；配置中的「替换为…」是部署值，单测用 `new HuifuProperties()` 注入，不依赖 |
| 与 spec §5.1/§6 状态机一致 | `merchant.status` 0/1/2/3/4 与 `merchant_apply.status` 0/1/2/3/4 全量实现；双通道（回调 + 轮询）幂等；提交即推汇付、无人工预审（D5） |
| SQL id 无冲突 | 菜单 2030-2034（现 max 2026）、角色 170/171（现 max 162）、role_menu 8800-8815（现 max 8704）、infra_job 12904（AUTO_INCREMENT=12904） |
| Java 8 / SB 2.7 语法 | `javax.annotation.Resource`、无 record/var/Text Block；`RestTemplate`（非 RestClient）；`Map.of` 不可用处已注明用 `HashMap` |
| 模块接线完整 | 根 pom（取消注释）→ mall pom（加 module）→ server pom（加依赖）→ `@MapperScan` 覆盖 `cn.iocoder.yudao` 包 |
| 错误码无冲突 | 1-012 段全仓未占用（仅 fms `1_052_101_012` 含子串，非同段） |
| 跨模块依赖方向 | merchant → system/infra（已有 Api 接口扩展），无反向依赖；不触碰 product/trade/pay（P2/P3） |
| 数据隔离 | P1 仅商户自查（`getLoginMerchant` 按登录用户绑定）；平台侧统一 `@PreAuthorize` 权限；P3 再上 `@MerchantScope` |

**已知开放项（不阻塞 P1，Task 13 联调确认）**：
1. 回调应答体：进件回调返回 `"success"`、业务开通回调返回 `RECV_ORD_ID_{ordId}` —— 需以沙箱实际要求为准。
2. 回调 `data` 为 JSON 对象时按紧凑重序列化验签；若汇付原文含空白/非紧凑格式，需改为原文截取（联调首日验证）。
3. 商户业务开通的 `online_media_info_list`（scene=ONLINE/ALL 必填材料）与 `material_card_info` 必填组合：以沙箱返回的必填校验为准补齐（配置项 `online-busi-type` 已预留）。
4. 汇付审核为**异步**：`busi/open` 同步受理即置商户生效（P1 简化）；若沙箱要求等业务开通结果回调才算生效，则改为在 `busi-callback` 内调 `effectMerchant`。

## 四、Execution Handoff

计划落在 `docs/superpowers/plans/2026-09-28-p1-merchant-domain.md`，两种执行方式：

1. **逐任务执行（推荐，含评审点）**：每个 Task 完成后跑对应 `mvn test`，Task 间可暂停复核（尤其 Task 8 状态机与 Task 13 联调）。
2. **整体委派执行**：按 Task 顺序批量实施，仅在 Task 13 联调处停顿。

> 执行前提：spec（`docs/superpowers/specs/2026-09-28-multi-merchant-mall-design.md`）评审通过。执行中若发现 spec 与实现冲突，先回改 spec 再改计划。

-- ========================================================================
-- 多商户商城一期（P1）：商户域 —— 商户主表 / 进件申请单 / 资质图片映射
-- 关联：docs/superpowers/specs/2026-09-28-multi-merchant-mall-design.md
--       docs/superpowers/plans/2026-09-28-p1-merchant-domain.md (Task 1)
-- 兼容性：MySQL 5.7+
-- ID 占用（已核对全仓 sql/mysql/*.sql 无冲突）：
--   system_menu      2030-2034   （dump max 2026，AUTO_INCREMENT 2027）
--   system_role      170 / 171   （dump max 162，AUTO_INCREMENT 164）
--   system_role_menu 8800-8815   （dump max 8704，AUTO_INCREMENT 8710）
--   infra_job        12904       （dump max 12903，AUTO_INCREMENT 12904）
-- ========================================================================

-- ----------------------------
-- 1. 商户主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `merchant`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '商户编号',
    `user_id`               bigint       NOT NULL DEFAULT 0 COMMENT '绑定的 admin 用户编号',
    `name`                  varchar(64)  NOT NULL DEFAULT '' COMMENT '商户名称',
    `short_name`            varchar(64)  NOT NULL DEFAULT '' COMMENT '商户简称',
    `logo`                  varchar(255) NOT NULL DEFAULT '' COMMENT '店铺 LOGO',
    `remark`                varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1进件中 2生效 3拒绝 4停用',
    `merchant_type`         tinyint      NOT NULL DEFAULT 1 COMMENT '主体类型：1企业商户 2小微商户（个人）',
    `huifu_id`              varchar(18)  NOT NULL DEFAULT '' COMMENT '汇付商户号',
    `ext_mer_id`            varchar(64)  NOT NULL DEFAULT '' COMMENT '外部商户号（=本表 id，进件时写入汇付）',
    `contact_name`          varchar(128) NOT NULL DEFAULT '' COMMENT '联系人姓名',
    `contact_mobile`        varchar(11)  NOT NULL DEFAULT '' COMMENT '联系人手机号',
    `contact_email`         varchar(32)  NOT NULL DEFAULT '' COMMENT '联系人邮箱',
    `settle_card_no_masked` varchar(32)  NOT NULL DEFAULT '' COMMENT '结算卡号（脱敏）',
    `submit_time`           datetime              DEFAULT NULL COMMENT '最近进件提交时间',
    `effect_time`           datetime              DEFAULT NULL COMMENT '商户生效时间',
    `creator`               varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_huifu_id` (`huifu_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户主表';

-- ----------------------------
-- 2. 商户进件申请单（每次进件一条，保留历史）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `merchant_apply`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '申请单编号',
    `merchant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '商户编号',
    `req_seq_id`      varchar(32)  NOT NULL COMMENT '汇付请求流水号（当日唯一）',
    `req_date`        varchar(8)   NOT NULL COMMENT '汇付请求日期（yyyyMMdd）',
    `apply_no`        varchar(18)  NOT NULL DEFAULT '' COMMENT '汇付申请单号',
    `status`          tinyint      NOT NULL DEFAULT 4 COMMENT '状态：0进件中 1通过 2拒绝 3失败 4提交失败',
    `audit_status`    varchar(1)   NOT NULL DEFAULT '' COMMENT '汇付审核结果：Y通过 N拒绝 F失败',
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
    `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_req_seq_id` (`req_seq_id` ASC) USING BTREE,
    INDEX `idx_merchant_status` (`merchant_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户进件申请单';

-- ----------------------------
-- 3. 商户资质图片映射（平台文件 → 汇付 file_id）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `merchant_image`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `merchant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '商户编号',
    `biz_type`      varchar(8)   NOT NULL COMMENT '图片类型（汇付 file_type）',
    `infra_file_id` bigint       NOT NULL DEFAULT 0 COMMENT '平台 infra 文件编号',
    `huifu_file_id` varchar(128) NOT NULL DEFAULT '' COMMENT '汇付图片文件 ID',
    `creator`       varchar(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_merchant_biz` (`merchant_id` ASC, `biz_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户资质图片映射';

-- ----------------------------
-- 4. 菜单：商户中心（挂在「商城系统」449 下）
--    bit 类型列（visible/keep_alive/always_show）须用 b'1'
-- ----------------------------
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2030, '商户中心', '', 1, 10, 449, 'merchant', 'ep:shop', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2031, '入驻申请', 'merchant:apply:query', 2, 1, 2030, 'apply', 'ep:document', 'mall/merchant/apply/index', 'MerchantApply', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2032, '商户管理', 'merchant:merchant:query', 2, 2, 2030, 'merchant', 'ep:shop', 'mall/merchant/merchant/index', 'Merchant', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2033, '进件提交', 'merchant:apply:submit', 3, 1, 2031, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (2034, '商户停用/启用', 'merchant:merchant:update', 3, 1, 2032, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ----------------------------
-- 5. 角色：待生效商家 / 商家（tenant_id = 1 默认租户）
-- ----------------------------
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (170, '待生效商家', 'merchant_pending', 100, 1, '', 0, 2, '注册后未通过汇付审核的商家', '1', NOW(), '1', NOW(), b'0', 1);

INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (171, '商家', 'merchant', 99, 1, '', 0, 2, '已通过汇付审核生效的商家', '1', NOW(), '1', NOW(), b'0', 1);

-- ----------------------------
-- 6. 角色菜单授权
-- ----------------------------
-- 6.1 超级管理员（角色 1）：商户中心 + 入驻申请 + 商户管理 + 两个按钮
INSERT INTO `system_role_menu` (`id`, `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (8800, 1, 2030, '1', NOW(), '1', NOW(), b'0', 1),
       (8801, 1, 2031, '1', NOW(), '1', NOW(), b'0', 1),
       (8802, 1, 2032, '1', NOW(), '1', NOW(), b'0', 1),
       (8803, 1, 2033, '1', NOW(), '1', NOW(), b'0', 1),
       (8804, 1, 2034, '1', NOW(), '1', NOW(), b'0', 1);

-- 6.2 待生效商家（角色 170）：商户中心 + 入驻申请 + 进件提交
INSERT INTO `system_role_menu` (`id`, `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (8810, 170, 2030, '1', NOW(), '1', NOW(), b'0', 1),
       (8811, 170, 2031, '1', NOW(), '1', NOW(), b'0', 1),
       (8812, 170, 2033, '1', NOW(), '1', NOW(), b'0', 1);

-- 6.3 商家（角色 171）：商户中心 + 入驻申请 + 进件提交（P1 相同；P2 起追加商品/订单菜单）
INSERT INTO `system_role_menu` (`id`, `role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (8813, 171, 2030, '1', NOW(), '1', NOW(), b'0', 1),
       (8814, 171, 2031, '1', NOW(), '1', NOW(), b'0', 1),
       (8815, 171, 2033, '1', NOW(), '1', NOW(), b'0', 1);

-- ----------------------------
-- 7. 定时任务：汇付进件状态轮询（status=1 开启）
-- ----------------------------
INSERT INTO `infra_job` (`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (12904, '汇付进件状态轮询', 1, 'huifuApplyPollJob', NULL, '0 */5 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0');

-- ----------------------------
-- 8. 配置管理：汇付回调地址（后台「基础设施 → 配置管理」可改，实时生效；留空则回退 application.yaml）
-- ----------------------------
INSERT INTO `infra_config` (`id`, `category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (14, 'biz', 1, '汇付进件审核回调地址', 'merchant.huifu.kyc-callback-url', '', b'1', '汇付 KYC 进件审核结果异步通知地址（公网可达），留空回退 application.yaml 的 yudao.merchant.huifu.kyc-callback-url', '1', NOW(), '1', NOW(), b'0');
INSERT INTO `infra_config` (`id`, `category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (15, 'biz', 1, '汇付业务开通回调地址', 'merchant.huifu.busi-callback-url', '', b'1', '汇付业务开通结果异步通知地址（公网可达），留空回退 application.yaml 的 yudao.merchant.huifu.busi-callback-url', '1', NOW(), '1', NOW(), b'0');

-- ----------------------------
-- 9. 增量变更：商户表增加主体类型列（已执行过旧版脚本的库执行以下语句）
-- ----------------------------
-- ALTER TABLE `merchant` ADD COLUMN `merchant_type` tinyint NOT NULL DEFAULT 1 COMMENT '主体类型：1企业商户 2小微商户（个人）' AFTER `status`;



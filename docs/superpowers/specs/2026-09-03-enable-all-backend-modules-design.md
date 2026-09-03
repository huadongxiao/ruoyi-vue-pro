# 后端全部功能模块启用设计

## 背景

当前仓库是芋道源码的精简配置。根 `pom.xml` 只聚合 `system`、`infra` 和 `server`，`yudao-server/pom.xml` 也只引入系统与基础设施模块。会员、工作流、报表、公众号、支付、商城、CRM、ERP、AI、IoT、MES、WMS、HRM、FMS、IM 等源码已经存在，但被 Maven 注释排除。

运行环境使用远程 MySQL 和远程 Redis，不部署本地数据库或本地 Redis。用户确认需要字面意义上启用全部模块，包括 AI 与 IoT。

## 目标

- 根 Maven 聚合工程包含仓库中的全部后端功能模块。
- `yudao-server` 装载全部可嵌入主服务的业务模块。
- AI 模块完整编译并注册接口；模型调用在提供有效密钥后工作。
- IoT 业务模块在主服务中启用，并继续使用 Redis 消息总线。
- 独立 IoT 网关参与完整工程构建，但不强制主服务连接本机 Kafka、RocketMQ 或 MQTT。
- 完整工程能够构建，主后端能够使用现有远程 MySQL、Redis 启动。

## 非目标

- 不创建本地 MySQL 或 Redis。
- 不伪造 AI 服务密钥。
- 不自动部署 Kafka、RocketMQ、MQTT Broker、Qdrant、Milvus 或 TDengine。
- 不在没有确认数据内容的情况下覆盖或重建远程数据库。
- 不修改前端项目；前端是否展示菜单仍取决于远程数据库中的菜单和权限数据。

## 方案

### Maven 聚合

解除根 `pom.xml` 中下列模块的注释：

- `yudao-module-member`
- `yudao-module-bpm`
- `yudao-module-report`
- `yudao-module-mp`
- `yudao-module-pay`
- `yudao-module-mall`
- `yudao-module-crm`
- `yudao-module-erp`
- `yudao-module-iot`
- `yudao-module-mes`
- `yudao-module-wms`
- `yudao-module-hrm`
- `yudao-module-fms`
- `yudao-module-im`
- `yudao-module-ai`

`yudao-module-iot` 自身继续聚合 `iot-biz`、`iot-core` 和独立的 `iot-gateway`；`yudao-module-mall` 继续聚合其产品、营销、交易、统计等子模块。

### 主服务依赖

解除 `yudao-server/pom.xml` 中全部业务模块依赖的注释，包括商城的四个主服务子模块以及 `yudao-module-iot-biz`。独立 IoT 网关不嵌入 `yudao-server`，避免两个启动入口和端口生命周期耦合；它由根聚合工程单独构建。

### 外部服务策略

- MySQL、Redis：沿用当前远程连接配置。
- IoT 主服务：保留 `yudao.iot.message-bus.type=redis`，不要求本地 MQ。
- AI：保留现有按业务调用创建模型客户端的机制；没有有效密钥时，后端仍应启动，实际模型调用会返回配置或供应商错误。
- 向量存储：沿用 local profile 对 Qdrant、Milvus 自动配置的排除；当前代码默认使用内存 `SimpleVectorStore`，因此不把外部向量库作为启动前置条件。
- IoT 网关：参与构建，但只有在提供对应 Broker/服务端配置后才单独启动并验证协议链路。

## 数据库边界

仓库当前 `sql/mysql/ruoyi-vue-pro.sql` 只有 48 张基础表，主要覆盖 `system` 和 `infra`。模块编译成功不代表远程数据库已经具备全部业务表、菜单和权限数据。

实施时先通过只读方式验证远程库表结构及启动日志。若缺少模块表：

1. 不自动覆盖远程库；
2. 汇总缺失模块和首个缺表错误；
3. 优先使用与当前源码版本匹配的官方完整 SQL；
4. 任何远程数据库结构写入都需要单独确认。

## 验证

1. 用 JDK 8 执行完整 Maven 构建，确认所有聚合模块成功。
2. 检查生成的 `yudao-server.jar` 是否包含全部模块代码及依赖。
3. 使用 local profile 和远程 MySQL、Redis 重启主后端。
4. 从启动日志确认 Spring 上下文完成，并检查各模块 Controller 的请求映射。
5. 请求 Swagger/OpenAPI，确认各模块接口分组或路径已经注册。
6. 检查启动日志中的缺表、Bean 冲突、端口冲突和外部服务连接错误。
7. 独立验证 `iot-gateway` 可构建；没有 Broker 配置时不宣称其协议链路可运行。

## 失败处理

- 编译失败：定位到首个模块错误，只做与完整模块集兼容所必需的最小改动。
- 主服务启动失败：先区分代码依赖、数据库结构和外部服务三类原因，不通过关闭业务模块规避。
- 外部 AI/IoT 服务缺失：保持模块与接口装载，明确指出缺少的运行时配置。
- 远程数据库缺表：停止结构写入，向用户报告所需的版本匹配 SQL。

## 验收标准

- 根工程和 `yudao-server` 不再注释任何已有功能模块。
- 完整 Maven reactor 构建通过。
- 主服务使用远程 MySQL、Redis 运行，不启动本地数据库或 Redis。
- Swagger/OpenAPI 能看到已装载模块的接口；若数据库结构阻碍启动，报告必须精确到模块和表。
- AI 与 IoT 源码、依赖和接口全部启用，外部供应商或协议服务的可用性按实际已提供配置说明。

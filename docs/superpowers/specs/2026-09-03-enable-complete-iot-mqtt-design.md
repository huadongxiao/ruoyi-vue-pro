# 完整 IoT MQTT 功能启用设计

## 背景

当前后端仓库使用 `master`（JDK 8）分支，`yudao-module-iot` 与
`yudao-server` 中的 IoT 依赖仍被注释。服务器已有 MySQL、Redis、TDengine
和 `yudao-server` 容器，但尚未部署独立的 `iot-gateway`。目标是启用完整的
IoT 管理功能，并允许真实设备通过 MQTT 安全接入。

TDengine 位于 `117.88.101.77:6041`，数据库为 `ruoyi_vue_pro`。只读验证已确认
端口、账号和数据库可用。MQTT 使用域名 `mqtt.yddtm.cn`，目标服务器为
`117.88.101.77`。

## 目标

- 在 `yudao-server` 中启用 IoT 产品、物模型、设备、场景联动、告警、OTA、
  数据流转和 TDengine 时序数据能力。
- 导入官方 IoT MySQL 脚本和仓库内的 Quartz MySQL 脚本。
- 独立部署 `iot-gateway`，启用源码内置的 MQTT Server。
- 真实设备通过 MQTT over TLS 接入，上下行消息通过 Redis 在网关和主服务间流转。
- 验证设备认证、上线/离线、属性上报、TDengine 落库和平台下行消息。

## 非目标

- 不部署 EMQX、Mosquitto 或其他外部 MQTT Broker。
- 不启用 HTTP、TCP、UDP、WebSocket、CoAP 或 Modbus 设备协议。
- 不将 `iot-gateway` 嵌入 `yudao-server` 进程。
- 不改造现有 IoT 业务代码或引入新的依赖。

## 方案选择

采用源码内置 MQTT Server + Redis 消息总线方案。它直接复用现有 Redis，组件最少，
并完整支持当前源码的设备认证、动态注册、上下行消息和子设备能力。

备选方案一是 EMQX + `emqx` 协议适配，适合以后需要 Broker 集群、可视化运维或更大
连接规模时使用；当前会增加无必要的服务和配置。备选方案二是明文 MQTT `1883`，
仅适合临时内网测试，不用于公网真实设备。

## 架构与数据流

1. 设备通过 `mqtts://mqtt.yddtm.cn:8883` 连接 `iot-gateway`。
2. 网关使用设备的产品 Key、设备名和设备密钥调用 `yudao-server` 的 RPC API 完成认证。
3. 上行设备消息由网关发布到 Redis 消息总线。
4. `iot-biz` 消费消息，将产品和设备元数据写入 MySQL，将消息和属性时序数据写入
   TDengine。
5. 平台下行指令通过 Redis 返回网关，再由网关发送给已连接的 MQTT 设备。

`iot-gateway` 与 `yudao-server`、Redis 使用同一个 Docker 内部网络通信；公网只暴露
TLS MQTT 端口 `8883`。

## 代码与配置变更

- 根 `pom.xml`：取消 `yudao-module-iot` 聚合模块的注释。
- `yudao-server/pom.xml`：取消 `yudao-module-iot-biz` 依赖的注释。
- `application-local.yaml`：启用 `tdengine` 数据源，使用 WebSocket JDBC 驱动连接
  `117.88.101.77:6041/ruoyi_vue_pro`。
- `iot-gateway/application.yaml`：保留 Redis 消息总线，仅启用 `mqtt-json`，监听
  `8883` 并启用 PEM 证书和私钥；其他协议保持关闭。
- 网关 RPC 地址和 Redis 地址使用 Docker 内部服务名，不通过公网回环。
- 敏感连接参数通过容器环境变量或只在服务器保存的配置覆盖，不写入新的公开源码。

当前使用的 Vben 管理前端已包含完整 `iot` API 与页面目录。实现阶段只验证菜单和页面，
不做无必要的前端改造；若服务器实际部署的是官方 Vue3 管理端，则使用其现成 `iot`
目录，处理方式相同。

## 数据库处理

从用户已授权访问的官方附件取得与当前版本匹配的 IoT SQL，并导入现有
`ruoyi-vue-pro` MySQL 数据库。导入前先检查 `iot_` 表，避免重复执行导致冲突。
场景联动所需 Quartz 表使用仓库的 `sql/mysql/quartz.sql`，同样先检查后导入。

TDengine 数据库已存在。主服务启动时 `TDengineTableInitRunner` 会检查并创建
`device_message` 超级表；产品属性超级表按物模型同步操作创建。

## 部署

1. 使用 JDK 8 构建 `yudao-server` 和 `yudao-module-iot-gateway` 可执行 JAR。
2. 备份服务器当前主服务 JAR 与容器配置，再替换并重启 `yudao-server`。
3. 在 1Panel 创建独立 `iot-gateway` 容器，挂载网关 JAR、配置和 TLS 证书。
4. 将网关加入现有应用 Docker 网络，并映射宿主机 `8883/TCP`。
5. 为 `mqtt.yddtm.cn` 签发或复用可信 CA 证书，证书只挂载给网关读取。

## 安全

- 公网只开放 `8883/TCP`；不开放明文 `1883`。
- 禁止匿名 MQTT，沿用源码的逐设备认证。
- Redis、主服务 RPC 和 MySQL 仅通过 Docker 内网供网关访问。
- TLS 私钥和数据库密码不提交到 Git。
- 当前权威 DNS 仍把 `mqtt.yddtm.cn` 解析为 `28.0.0.200`；部署 TLS 前必须改为
  `117.88.101.77`。

## 验证

- Maven 构建成功，主服务 JAR 包含 `iot-biz`，网关 JAR 可独立启动。
- `yudao-server` 启动日志无 TDengine 初始化错误，`device_message` 超级表存在。
- 管理后台显示 IoT 菜单，产品、物模型和设备接口可正常调用。
- TLS 证书链与域名匹配，`8883` 接受 MQTT TLS 连接，`1883` 不提供服务。
- 使用测试产品和设备密钥完成一次真实 MQTT 认证。
- 设备上线、属性上报和下线状态进入平台；上报数据可从 TDengine 查询。
- 从管理后台发送一次下行消息，设备端收到正确主题与载荷。

## 失败处理与回滚

数据库导入和容器替换前保留备份。若主服务启动失败，恢复原 JAR 和容器配置；若网关
失败，停止新网关容器即可，不影响原主服务。任何验证失败都保留日志并定位根因，不以
端口可连通代替端到端设备消息验证。

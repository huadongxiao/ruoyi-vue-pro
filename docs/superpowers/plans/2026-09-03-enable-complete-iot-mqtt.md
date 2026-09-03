# Complete IoT MQTT Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the complete IoT backend, connect it to the existing TDengine database, and deploy a TLS-only MQTT gateway for real devices at `mqtt.yddtm.cn:8883`.

**Architecture:** `yudao-server` loads `iot-biz` for management, rules, persistence, and device-message processing. A separate `iot-gateway` process runs the built-in Vert.x MQTT server, authenticates devices through the main server, and exchanges upstream/downstream messages with `iot-biz` over the existing Redis instance. MySQL stores metadata, while TDengine stores time-series messages and properties.

**Tech Stack:** Java 8, Maven, Spring Boot 2.7, MyBatis Plus dynamic datasource, TDengine WebSocket JDBC, Redis Streams, Vert.x MQTT Server, Docker/1Panel, TLS PEM certificates.

**Spec:** `docs/superpowers/specs/2026-09-03-enable-complete-iot-mqtt-design.md`

## Global Constraints

- Keep the repository on the current `master` JDK 8 source line.
- Use the existing TDengine database `ruoyi_vue_pro` at `117.88.101.77:6041`.
- Use the built-in MQTT server; do not deploy EMQX or Mosquitto.
- Expose MQTT only as TLS on `8883/TCP`; do not serve plaintext MQTT on `1883`.
- Keep `iot-gateway` as a separate process from `yudao-server`.
- Reuse the existing Redis service and Docker network `1panel-network`.
- Do not commit TLS private keys, database passwords, device secrets, or server `.env` files.
- Back up the current server artifact and database before a state-changing deployment step.

---

### Task 1: Enable IoT in the Maven reactor and main server

**Files:**
- Modify: `pom.xml:26`
- Modify: `yudao-server/pom.xml:111-116`

**Interfaces:**
- Consumes: existing Maven parent and module structure.
- Produces: a reactor containing `yudao-module-iot` and a `yudao-server` runtime dependency on `yudao-module-iot-biz`.

- [ ] **Step 1: Run the pre-change assertion**

```powershell
$rootPom = Get-Content -LiteralPath 'pom.xml' -Raw
$serverPom = Get-Content -LiteralPath 'yudao-server/pom.xml' -Raw
[xml]$rootXml = $rootPom
[xml]$serverXml = $serverPom
if ('yudao-module-iot' -notin @($rootXml.project.modules.module)) {
    throw 'IoT reactor module is not active'
}
if ('yudao-module-iot-biz' -notin @($serverXml.project.dependencies.dependency.artifactId)) {
    throw 'IoT server dependency is not active'
}
```

Expected before implementation: exit code is non-zero because the IoT entries are still commented out.

- [ ] **Step 2: Uncomment only the existing IoT module and dependency blocks**

Root POM result:

```xml
<module>yudao-module-iot</module>
```

Server POM result:

```xml
<!-- IoT 物联网相关模块。默认注释，保证编译速度 -->
<dependency>
    <groupId>cn.iocoder.boot</groupId>
    <artifactId>yudao-module-iot-biz</artifactId>
    <version>${revision}</version>
</dependency>
```

- [ ] **Step 3: Verify Maven sees both entries as active XML**

```powershell
[xml]$rootPom = Get-Content -LiteralPath 'pom.xml' -Raw
[xml]$serverPom = Get-Content -LiteralPath 'yudao-server/pom.xml' -Raw
$rootModules = @($rootPom.project.modules.module)
$serverArtifacts = @($serverPom.project.dependencies.dependency.artifactId)
if ('yudao-module-iot' -notin $rootModules) { throw 'IoT reactor module is not active' }
if ('yudao-module-iot-biz' -notin $serverArtifacts) { throw 'IoT server dependency is not active' }
```

Expected: exit code `0`.

- [ ] **Step 4: Commit the Maven enablement**

```powershell
git add -- pom.xml yudao-server/pom.xml
git commit -m "feat: enable iot backend module"
```

---

### Task 2: Configure the TDengine datasource and TLS MQTT gateway

**Files:**
- Modify: `yudao-server/src/main/resources/application-local.yaml:75-82`
- Modify: `yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml:7-11`
- Modify: `yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml:37-40`
- Modify: `yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml:117-127`

**Interfaces:**
- Consumes: Spring Boot relaxed environment binding, existing `tdengine` dynamic datasource name, existing Redis message-bus implementation, PEM certificate/key files.
- Produces: `@TDengineDS` routes to the remote TDengine database and `mqtt-json` listens with TLS on port `8883`.

- [ ] **Step 1: Run the pre-change configuration assertion**

```powershell
$serverYaml = Get-Content -LiteralPath 'yudao-server/src/main/resources/application-local.yaml' -Raw
$gatewayYaml = Get-Content -LiteralPath 'yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml' -Raw
if ($serverYaml -notmatch '(?m)^\s+tdengine:\s*# IoT 时序数据库\s*$') { throw 'Active TDengine datasource is missing' }
if ($gatewayYaml -notmatch '(?ms)^\s+- id:\s*mqtt-json\s*$\r?\n\s+enabled:\s*true\s*$') { throw 'MQTT listener is not enabled' }
if ($gatewayYaml -notmatch 'port:\s*\$\{IOT_MQTT_PORT:8883\}') { throw 'MQTT TLS port is not configured' }
```

Expected before implementation: exit code is non-zero because the datasource and listener are disabled.

- [ ] **Step 2: Enable the TDengine datasource without committing its password**

Use this active datasource block under `spring.datasource.dynamic.datasource`:

```yaml
        tdengine: # IoT 时序数据库
          lazy: true
          url: jdbc:TAOS-WS://${TDENGINE_HOST:117.88.101.77}:${TDENGINE_PORT:6041}/${TDENGINE_DATABASE:ruoyi_vue_pro}?varcharAsString=true&enableAutoReconnect=true
          driver-class-name: com.taosdata.jdbc.ws.WebSocketDriver
          username: ${TDENGINE_USERNAME:root}
          password: ${TDENGINE_PASSWORD}
          druid:
            validation-query: SELECT SERVER_STATUS()
```

- [ ] **Step 3: Make gateway-to-service addresses deployable through environment variables**

Set the existing values to:

```yaml
  redis:
    host: ${REDIS_HOST:127.0.0.1}
    port: ${REDIS_PORT:6379}
    database: ${REDIS_DATABASE:0}
    password: ${REDIS_PASSWORD:}

yudao:
  iot:
    message-bus:
      type: redis
    gateway:
      rpc:
        url: ${IOT_SERVER_URL:http://127.0.0.1:48080}
```

Keep all existing timeout and token settings unchanged.

- [ ] **Step 4: Enable only the built-in MQTT listener with TLS**

Replace the existing `mqtt-json` entry with:

```yaml
        - id: mqtt-json
          enabled: true
          protocol: mqtt
          port: ${IOT_MQTT_PORT:8883}
          serialize: json
          ssl:
            ssl: true
            ssl-cert-path: ${IOT_MQTT_CERT_PATH:/app/certs/fullchain.pem}
            ssl-key-path: ${IOT_MQTT_KEY_PATH:/app/certs/privkey.pem}
          mqtt:
            max-message-size: 8192
            connect-timeout-seconds: 60
```

Keep every other protocol entry `enabled: false`.

- [ ] **Step 5: Verify the effective text and YAML indentation**

```powershell
$serverYaml = Get-Content -LiteralPath 'yudao-server/src/main/resources/application-local.yaml' -Raw
$gatewayYaml = Get-Content -LiteralPath 'yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml' -Raw
if ($serverYaml -notmatch 'jdbc:TAOS-WS://\$\{TDENGINE_HOST:117\.88\.101\.77\}') { throw 'TDengine URL missing' }
if ($serverYaml -match '(?m)^\s+password:\s*(?!\$\{TDENGINE_PASSWORD\}\s*$)\S+') { throw 'TDengine password must remain an environment placeholder' }
if ($gatewayYaml -notmatch '(?s)id:\s*mqtt-json.*?enabled:\s*true.*?port:\s*\$\{IOT_MQTT_PORT:8883\}.*?ssl:\s*true') { throw 'TLS MQTT configuration missing' }
$disabledProtocolIds = 'http-json','tcp-json','udp-json','websocket-json','coap-json','emqx-1','modbus-tcp-client-1','modbus-tcp-server-1'
foreach ($id in $disabledProtocolIds) {
    $pattern = '(?ms)^\s+- id:\s*' + [regex]::Escape($id) + '\s*$\r?\n\s+enabled:\s*false\s*$'
    if ($gatewayYaml -notmatch $pattern) { throw "Protocol $id must remain disabled" }
}
```

Expected: exit code `0`.

- [ ] **Step 6: Commit the runtime configuration**

```powershell
git add -- yudao-server/src/main/resources/application-local.yaml yudao-module-iot/yudao-module-iot-gateway/src/main/resources/application.yaml
git commit -m "feat: configure tdengine and tls mqtt gateway"
```

---

### Task 3: Build and test both Java applications

**Files:**
- Verify: `yudao-server/target/yudao-server.jar`
- Verify: `yudao-module-iot/yudao-module-iot-gateway/target/yudao-module-iot-gateway.jar`

**Interfaces:**
- Consumes: Maven configuration from Tasks 1-2 and the bundled JDK at `E:/dm-project/yudao/.runtime/temurin8-20260903/jdk8u504-b01`.
- Produces: deployable main-server and gateway JARs.

- [ ] **Step 1: Select the JDK 8 toolchain for this shell**

```powershell
$iotJdk = 'E:\dm-project\yudao\.runtime\temurin8-20260903\jdk8u504-b01'
$env:JAVA_HOME = $iotJdk
$env:Path = "$iotJdk\bin;$env:Path"
java -version
```

Expected: Temurin/OpenJDK `1.8.0_504`.

- [ ] **Step 2: Run the IoT module tests**

```powershell
mvn -pl yudao-module-iot/yudao-module-iot-core,yudao-module-iot/yudao-module-iot-biz -am test
```

Expected: `BUILD SUCCESS` with zero test failures.

- [ ] **Step 3: Package the main server and gateway**

```powershell
mvn -pl yudao-server,yudao-module-iot/yudao-module-iot-gateway -am clean package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify both executable JARs**

```powershell
$serverJar = 'yudao-server/target/yudao-server.jar'
$gatewayJar = 'yudao-module-iot/yudao-module-iot-gateway/target/yudao-module-iot-gateway.jar'
if (-not (Test-Path -LiteralPath $serverJar)) { throw 'Server JAR missing' }
if (-not (Test-Path -LiteralPath $gatewayJar)) { throw 'Gateway JAR missing' }
jar tf $serverJar | Select-String 'cn/iocoder/yudao/module/iot/' | Select-Object -First 1
jar tf $gatewayJar | Select-String 'IotGatewayServerApplication.class'
```

Expected: both searches return a matching class.

---

### Task 4: Import the licensed IoT and Quartz MySQL schemas

**Files:**
- Source: user-authorized official attachment `iot-2026-02-10.sql.zip` from `https://t.zsxq.com/dQTQ2`
- Source: `sql/mysql/quartz.sql`
- Server staging: `/data/yudao-new-project/sql/iot-2026-02-10.sql`
- Server staging: `/data/yudao-new-project/sql/quartz.sql`

**Interfaces:**
- Consumes: existing MySQL container `1Panel-mysql-QBSo` and database `ruoyi-vue-pro`.
- Produces: all `iot_` metadata tables and Quartz scheduler tables required by scene rules.

- [ ] **Step 1: Download and inspect the official IoT archive**

Use the signed-in Chrome session to open `https://t.zsxq.com/dQTQ2`, download
`iot-2026-02-10.sql.zip`, extract it into a temporary local directory, and verify the SQL contains
`CREATE TABLE` statements whose table names begin with `iot_`. Do not add the licensed SQL to Git.

- [ ] **Step 2: Check the current remote schema before importing**

Run in the 1Panel host terminal:

```bash
docker exec 1Panel-mysql-QBSo sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''ruoyi-vue-pro'\'' AND table_name LIKE '\''iot\\_%'\''; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''ruoyi-vue-pro'\'' AND table_name LIKE '\''QRTZ\\_%'\'';"'
```

Expected: two numeric counts. If either family already exists, inspect it before importing and do not blindly replay `CREATE` or seed statements.

- [ ] **Step 3: Back up the target database in 1Panel**

Create a database backup for `ruoyi-vue-pro` from **Database → MySQL → Backup List** and verify the new backup entry has a non-zero size before continuing.

- [ ] **Step 4: Upload and import both SQL files**

Upload the extracted IoT SQL and `sql/mysql/quartz.sql` to `/data/yudao-new-project/sql/`. Then run:

```bash
docker exec -i 1Panel-mysql-QBSo sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "ruoyi-vue-pro"' < /data/yudao-new-project/sql/iot-2026-02-10.sql
docker exec -i 1Panel-mysql-QBSo sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "ruoyi-vue-pro"' < /data/yudao-new-project/sql/quartz.sql
```

Expected: both commands exit `0`.

- [ ] **Step 5: Verify schema families exist**

```bash
docker exec 1Panel-mysql-QBSo sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''ruoyi-vue-pro'\'' AND table_name LIKE '\''iot\\_%'\''; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''ruoyi-vue-pro'\'' AND table_name LIKE '\''QRTZ\\_%'\'';"'
```

Expected: both counts are greater than zero.

---

### Task 5: Issue the MQTT TLS certificate

**Files:**
- Server create: `/data/yudao-new-project/iot-gateway/certs/fullchain.pem`
- Server create: `/data/yudao-new-project/iot-gateway/certs/privkey.pem`

**Interfaces:**
- Consumes: public DNS `mqtt.yddtm.cn → 117.88.101.77`, reachable ports `80/443`, and 1Panel certificate management.
- Produces: a trusted PEM certificate/key pair mounted read-only by the gateway.

- [ ] **Step 1: Verify authoritative DNS before requesting a certificate**

```powershell
$authoritativeServers = 'ns11.xincache.com','ns12.xincache.com'
foreach ($server in $authoritativeServers) {
    $ips = Resolve-DnsName -Name 'mqtt.yddtm.cn' -Type A -Server $server -DnsOnly |
        Where-Object IPAddress | Select-Object -ExpandProperty IPAddress
    if ('117.88.101.77' -notin $ips) { throw "DNS is not ready on $server: $($ips -join ',')" }
}
```

Expected: exit code `0`. Do not request a certificate while this fails.

- [ ] **Step 2: Issue a Let's Encrypt certificate in 1Panel**

In **Website → Certificates**, request a certificate for exactly `mqtt.yddtm.cn` using HTTP validation. Do not include unrelated domains.

- [ ] **Step 3: Place the certificate at the gateway paths**

Export/copy the issued full chain and private key to:

```text
/data/yudao-new-project/iot-gateway/certs/fullchain.pem
/data/yudao-new-project/iot-gateway/certs/privkey.pem
```

Set the directory to `0750`, the certificate to `0644`, and the private key to `0600`.

- [ ] **Step 4: Verify certificate identity and expiry**

```bash
openssl x509 -in /data/yudao-new-project/iot-gateway/certs/fullchain.pem -noout -subject -issuer -dates -ext subjectAltName
openssl pkey -in /data/yudao-new-project/iot-gateway/certs/privkey.pem -noout -check
```

Expected: SAN contains `DNS:mqtt.yddtm.cn`, expiry is in the future, and the key check succeeds.

---

### Task 6: Deploy the IoT-enabled main server

**Files:**
- Replace on server: `/data/yudao-new-project/server/yudao-server.jar`
- Modify outside Git: `/opt/1panel/runtime/java/yudao-server/.env`
- Backup on server: `/data/yudao-new-project/server/backups/yudao-server-before-iot.jar`

**Interfaces:**
- Consumes: server JAR from Task 3, imported MySQL schema from Task 4, remote TDengine, and the existing `yudao-server` container.
- Produces: a healthy main server exposing IoT admin and RPC APIs.

- [ ] **Step 1: Record the existing artifact hash and create a recoverable backup**

Run in the 1Panel host terminal:

```bash
mkdir -p /data/yudao-new-project/server/backups
sha256sum /data/yudao-new-project/server/yudao-server.jar
cp -p /data/yudao-new-project/server/yudao-server.jar /data/yudao-new-project/server/backups/yudao-server-before-iot.jar
test -s /data/yudao-new-project/server/backups/yudao-server-before-iot.jar
```

Expected: the backup exists and has non-zero size.

- [ ] **Step 2: Upload and atomically replace the JAR**

Upload the Task 3 artifact as `/data/yudao-new-project/server/yudao-server.jar.next`, then run:

```bash
test -s /data/yudao-new-project/server/yudao-server.jar.next
mv /data/yudao-new-project/server/yudao-server.jar.next /data/yudao-new-project/server/yudao-server.jar
```

- [ ] **Step 3: Add the TDengine password to the server-only environment**

Add `TDENGINE_PASSWORD` to `/opt/1panel/runtime/java/yudao-server/.env` using the credential supplied by the user. Preserve all existing lines and do not display or copy the value into logs or Git.

- [ ] **Step 4: Restart and verify the main server**

Restart only the `yudao-server` container in 1Panel. Then run:

```bash
docker logs --since 5m yudao-server 2>&1 | grep -E 'Started YudaoServerApplication|TDengine|ERROR' | tail -n 100
curl -fsS http://127.0.0.1:48080/actuator/health
```

Expected: the application reports started, health returns success, and no TDengine initialization error appears.

- [ ] **Step 5: Verify TDengine created the base stable**

Run a read-only REST SQL request to `117.88.101.77:6041/rest/sql/ruoyi_vue_pro` and execute:

```sql
SHOW STABLES LIKE 'device_message';
```

Expected: `device_message` is returned.

---

### Task 7: Deploy the independent TLS MQTT gateway

**Files:**
- Server create: `/data/yudao-new-project/iot-gateway/yudao-module-iot-gateway.jar`
- Server create: `/data/yudao-new-project/iot-gateway/run.sh`
- Reuse: `/data/yudao-new-project/iot-gateway/certs/fullchain.pem`
- Reuse: `/data/yudao-new-project/iot-gateway/certs/privkey.pem`

**Interfaces:**
- Consumes: gateway JAR from Task 3, `yudao-server:48080`, `1Panel-redis-Kw39:6379`, and the TLS files from Task 5.
- Produces: Docker container `iot-gateway` on `1panel-network`, publicly listening only on `8883/TCP`.

- [ ] **Step 1: Upload the gateway JAR and create its start script**

Upload the JAR to `/data/yudao-new-project/iot-gateway/yudao-module-iot-gateway.jar`. Create `/data/yudao-new-project/iot-gateway/run.sh` with:

```bash
#!/usr/bin/env bash
set -euo pipefail
exec java ${JAVA_OPTS:--Xms256m -Xmx512m} \
  -jar /app/yudao-module-iot-gateway.jar \
  --spring.redis.host=1Panel-redis-Kw39 \
  --spring.redis.port=6379 \
  --yudao.iot.gateway.rpc.url=http://yudao-server:48080
```

If the existing Redis instance requires authentication, add `SPRING_REDIS_PASSWORD` as a container environment variable using the same server-only secret already used by `yudao-server`; do not place it in `run.sh`.

- [ ] **Step 2: Validate files before creating the container**

```bash
chmod 0750 /data/yudao-new-project/iot-gateway/run.sh
test -s /data/yudao-new-project/iot-gateway/yudao-module-iot-gateway.jar
test -s /data/yudao-new-project/iot-gateway/certs/fullchain.pem
test -s /data/yudao-new-project/iot-gateway/certs/privkey.pem
```

Expected: exit code `0`.

- [ ] **Step 3: Create the gateway container in 1Panel**

Create a container with these exact settings:

```text
Name: iot-gateway
Image: 1panel/java:25
Network: 1panel-network
Host port: 0.0.0.0:8883
Container port: 8883/tcp
Mount: /data/yudao-new-project/iot-gateway -> /app (read/write)
Command: bash /app/run.sh
Restart policy: unless-stopped
```

Do not enable privileged mode and do not map port `1883`.

- [ ] **Step 4: Verify gateway startup and TLS**

```bash
docker logs --since 5m iot-gateway 2>&1 | grep -E 'MQTT|启动成功|ERROR' | tail -n 100
openssl s_client -connect mqtt.yddtm.cn:8883 -servername mqtt.yddtm.cn -verify_return_error </dev/null
```

Expected: the gateway reports MQTT startup on `8883`; TLS verification returns code `0` with the certificate for `mqtt.yddtm.cn`.

- [ ] **Step 5: Verify plaintext MQTT is not served**

```powershell
$client = [Net.Sockets.TcpClient]::new()
try {
    $connected = $client.ConnectAsync('117.88.101.77', 1883).Wait(3000) -and $client.Connected
    if ($connected) { throw 'Plaintext MQTT port 1883 is still reachable' }
} finally {
    $client.Dispose()
}
```

Expected: exit code `0` because no service accepts connections on `1883`.

---

### Task 8: Run end-to-end device acceptance

**Files:**
- Verify only: no committed files.

**Interfaces:**
- Consumes: IoT admin UI, a test product/device from the imported IoT data or one created in the UI, and `mqtts://mqtt.yddtm.cn:8883`.
- Produces: evidence that authentication, upstream persistence, status changes, and downstream delivery all work.

- [ ] **Step 1: Verify the admin surface**

Open the active admin frontend, refresh its menu, and verify the IoT home, product, thing-model, device, scene-rule, alert, OTA, and data-rule pages load without `404` or permission errors.

- [ ] **Step 2: Prepare a dedicated test product and device**

Use the built-in test product if present; otherwise create one direct-connected MQTT product and one device in the admin UI. Record its product key, device name, and device secret only in a temporary local session; do not commit them.

- [ ] **Step 3: Synchronize the product thing model**

Use the UI's TDengine synchronization action for the test product. Verify its `product_property_<productId>` stable appears in `ruoyi_vue_pro`.

- [ ] **Step 4: Connect a TLS MQTT client with platform-generated authentication**

Generate `clientId`, `username`, and signed `password` with the existing `IotDeviceAuthUtils.getAuthInfo(productKey, deviceName, deviceSecret)` logic. Connect to:

```text
mqtts://mqtt.yddtm.cn:8883
```

Use the system CA trust store and reject hostname or certificate errors.

- [ ] **Step 5: Publish one property report and wait for its reply**

Publish JSON to:

```text
/sys/<productKey>/<deviceName>/thing/event/property/post
```

Subscribe first to:

```text
/sys/<productKey>/<deviceName>/thing/event/property/post_reply
```

The payload must use `IotDeviceMessage.requestOf("thing.event.property.post", properties)` serialization from the repository. Expected: a successful reply is received.

- [ ] **Step 6: Verify platform state and TDengine persistence**

Verify the device becomes online in the admin UI, the reported property value is visible, and both `device_message_<deviceId>` and `product_property_<productId>` contain the new timestamped row.

- [ ] **Step 7: Verify one downstream command**

Keep the client connected and subscribed to its downstream system topic. Send one property-set or service-call command from the admin UI. Expected: the device receives the command and its reply is visible in the platform message list.

- [ ] **Step 8: Run the final repository and deployment checks**

```powershell
git status --short --branch
git diff --check HEAD~2..HEAD
```

```bash
docker ps --filter name=yudao-server --filter name=iot-gateway --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker logs --since 10m yudao-server 2>&1 | grep -E 'ERROR|TDengine初始化' || true
docker logs --since 10m iot-gateway 2>&1 | grep -E 'ERROR|启动失败' || true
```

Expected: the Git worktree contains no unexpected changes, both containers are running, only `8883` is mapped for MQTT, and the recent logs contain no IoT startup failure.

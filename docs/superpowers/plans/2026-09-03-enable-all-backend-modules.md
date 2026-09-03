# Enable All Backend Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable every backend feature module present in the repository, build the complete Maven reactor, and run the main backend against the user-provided remote MySQL and Redis.

**Architecture:** The root Maven reactor will aggregate every existing module, while `yudao-server` will depend on every business module intended to run inside the main Spring Boot process. The independent IoT gateway remains a separate executable produced by the reactor. External AI providers and IoT brokers stay runtime integrations rather than main-server startup prerequisites.

**Tech Stack:** Maven, Java 8 branch (`2026.07-jdk8-SNAPSHOT`), Spring Boot 2.7.18, PowerShell, remote MySQL, remote Redis.

**Spec:** `docs/superpowers/specs/2026-09-03-enable-all-backend-modules-design.md`

## Global Constraints

- Do not start a local MySQL or Redis.
- Do not persist the remote database or Redis passwords in tracked files.
- Enable AI and IoT modules as well as all ordinary business modules.
- Keep the independent IoT gateway separate from `yudao-server`.
- Use JDK 8 first because the current branch declares `java.version=1.8`; only change JDK after a concrete compiler or bytecode-version error proves it necessary.
- Do not write to or rebuild the remote database without separate user approval.
- Keep changes limited to Maven module/dependency activation unless verification exposes a specific incompatibility requiring a supplemental plan.

---

### Task 1: Activate the Complete Maven Module Set

**Files:**
- Modify: `pom.xml:10-34`
- Modify: `yudao-server/pom.xml:23-151`

**Interfaces:**
- Consumes: Existing module directories and their current `${revision}` Maven coordinates.
- Produces: A root reactor containing all 15 currently commented feature modules and a main server depending on all 18 commented runtime artifacts, including the four mall artifacts and `yudao-module-iot-biz`.

- [ ] **Step 1: Run the structural assertion and verify RED**

Run this PowerShell assertion before editing:

```powershell
$rootPom = [xml](Get-Content -Raw -LiteralPath 'pom.xml')
$serverPom = [xml](Get-Content -Raw -LiteralPath 'yudao-server/pom.xml')
$requiredModules = @(
  'yudao-module-member','yudao-module-bpm','yudao-module-report','yudao-module-mp',
  'yudao-module-pay','yudao-module-mall','yudao-module-crm','yudao-module-erp',
  'yudao-module-iot','yudao-module-mes','yudao-module-wms','yudao-module-hrm',
  'yudao-module-fms','yudao-module-im','yudao-module-ai'
)
$requiredDependencies = @(
  'yudao-module-member','yudao-module-report','yudao-module-bpm','yudao-module-pay',
  'yudao-module-mp','yudao-module-product','yudao-module-promotion','yudao-module-trade',
  'yudao-module-statistics','yudao-module-crm','yudao-module-erp','yudao-module-ai',
  'yudao-module-iot-biz','yudao-module-mes','yudao-module-wms','yudao-module-hrm',
  'yudao-module-fms','yudao-module-im'
)
$activeModules = @($rootPom.project.modules.module | ForEach-Object { [string]$_ })
$activeDependencies = @($serverPom.project.dependencies.dependency.artifactId | ForEach-Object { [string]$_ })
$missingModules = @($requiredModules | Where-Object { $_ -notin $activeModules })
$missingDependencies = @($requiredDependencies | Where-Object { $_ -notin $activeDependencies })
if ($missingModules.Count -or $missingDependencies.Count) {
  throw "Missing modules: $($missingModules -join ', '); missing server dependencies: $($missingDependencies -join ', ')"
}
```

Expected: FAIL and list all currently commented modules and dependencies.

- [ ] **Step 2: Enable all root modules**

In `pom.xml`, replace the commented module declarations with active `<module>` elements, preserving this order:

```xml
        <module>yudao-module-member</module>
        <module>yudao-module-bpm</module>
        <module>yudao-module-report</module>
        <module>yudao-module-mp</module>
        <module>yudao-module-pay</module>
        <module>yudao-module-mall</module>
        <module>yudao-module-crm</module>
        <module>yudao-module-erp</module>
        <module>yudao-module-iot</module>
        <module>yudao-module-mes</module>
        <module>yudao-module-wms</module>
        <module>yudao-module-hrm</module>
        <module>yudao-module-fms</module>
        <module>yudao-module-im</module>
        <module>yudao-module-ai</module>
```

Remove the obsolete AI activation warning comment because the module is no longer disabled.

- [ ] **Step 3: Enable all main-server dependencies**

In `yudao-server/pom.xml`, remove only the XML comment markers around the 18 existing dependency blocks. Preserve their group IDs, artifact IDs, versions, descriptive comments, and order. Do not add `yudao-module-iot-gateway` to the server.

- [ ] **Step 4: Re-run the structural assertion and verify GREEN**

Run the exact PowerShell assertion from Step 1.

Expected: exit code 0 with no missing-module exception.

- [ ] **Step 5: Validate XML and whitespace**

Run:

```powershell
[xml](Get-Content -Raw -LiteralPath 'pom.xml') | Out-Null
[xml](Get-Content -Raw -LiteralPath 'yudao-server/pom.xml') | Out-Null
git diff --check
```

Expected: all commands exit 0 and `git diff --check` prints nothing.

- [ ] **Step 6: Commit module activation**

```powershell
git add -- pom.xml yudao-server/pom.xml
git commit -m "feat: enable all backend modules"
```

Expected: one commit containing only the two POM changes.

### Task 2: Build the Complete Reactor on the Branch-Declared JDK

**Files:**
- Verify: `pom.xml`
- Verify: every Maven module reached from the root reactor
- Output: ignored Maven `target/` directories

**Interfaces:**
- Consumes: The active module graph from Task 1 and the repository's dependency management.
- Produces: A complete `yudao-server/target/yudao-server.jar` plus the independent IoT gateway artifact.

- [ ] **Step 1: Verify the selected Java and Maven runtimes**

```powershell
$env:JAVA_HOME = 'E:\dm-project\yudao\.runtime\temurin8-20260903\jdk8u504-b01'
$env:Path = "$env:JAVA_HOME\bin;C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin;$env:Path"
java -version
mvn.cmd -version
```

Expected: Java reports `1.8.0_504`; Maven reports that same Java home.

- [ ] **Step 2: Build every module**

```powershell
mvn.cmd -DskipTests clean package
```

Expected: `BUILD SUCCESS`, all reactor modules show `SUCCESS`, and the command exits 0. A class-version or compiler-source error is the only evidence that justifies revisiting JDK selection; ordinary dependency or source failures must be diagnosed on JDK 8.

- [ ] **Step 3: Verify both executable artifacts exist**

```powershell
$serverJar = 'yudao-server/target/yudao-server.jar'
$gatewayJar = 'yudao-module-iot/yudao-module-iot-gateway/target/yudao-module-iot-gateway.jar'
if (!(Test-Path -LiteralPath $serverJar)) { throw "Missing $serverJar" }
if (!(Test-Path -LiteralPath $gatewayJar)) { throw "Missing $gatewayJar" }
Get-Item -LiteralPath $serverJar, $gatewayJar | Select-Object FullName, Length, LastWriteTime
```

Expected: both files exist and have fresh timestamps from Step 2.

### Task 3: Restart and Verify the Full Main Backend

**Files:**
- Execute: `yudao-server/target/yudao-server.jar`
- Inspect: `yudao-server/target/yudao-server.stdout.log`
- Inspect: `yudao-server/target/yudao-server.stderr.log`

**Interfaces:**
- Consumes: The complete server JAR and the user-provided remote MySQL/Redis connection values.
- Produces: A Spring Boot process listening on `127.0.0.1:48080` with all module request mappings registered.

- [ ] **Step 1: Verify and stop only the existing backend**

```powershell
$listener = Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction Stop
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
if ($process.Name -ne 'java.exe' -or $process.CommandLine -notlike '*yudao-server.jar*') {
  throw "Port 48080 is not owned by the expected yudao-server process"
}
Stop-Process -Id $listener.OwningProcess
Wait-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
```

Expected: port 48080 no longer has a listener; frontend processes remain untouched.

- [ ] **Step 2: Start with process-scoped remote connection overrides**

Set the user-provided values in the current process for these exact properties, without writing the values into a tracked file:

```text
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD
SPRING_REDIS_HOST
SPRING_REDIS_PORT
SPRING_REDIS_DATABASE
SPRING_REDIS_PASSWORD
```

Then run:

```powershell
$java = 'E:\dm-project\yudao\.runtime\temurin8-20260903\jdk8u504-b01\bin\java.exe'
$jar = (Resolve-Path 'yudao-server/target/yudao-server.jar').Path
$stdout = (Resolve-Path 'yudao-server/target').Path + '\yudao-server.stdout.log'
$stderr = (Resolve-Path 'yudao-server/target').Path + '\yudao-server.stderr.log'
$backend = Start-Process -FilePath $java -ArgumentList @('-jar', $jar, '--spring.profiles.active=local') -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
$backend.Id
```

Expected: a new PID is returned and secrets exist only in the launched process environment.

- [ ] **Step 3: Verify startup from port and logs**

```powershell
$deadline = (Get-Date).AddSeconds(60)
do {
  Start-Sleep -Seconds 2
  $ready = Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction SilentlyContinue
} until ($ready -or (Get-Date) -ge $deadline)
if (!$ready) {
  Get-Content -LiteralPath 'yudao-server/target/yudao-server.stdout.log' -Tail 200
  Get-Content -LiteralPath 'yudao-server/target/yudao-server.stderr.log' -Tail 200
  throw 'Backend did not listen on port 48080 within 60 seconds'
}
rg -n -i '(APPLICATION FAILED|Table .*doesn.t exist|BeanDefinitionOverrideException|Connection refused|ClassNotFoundException|NoSuchMethodError)' yudao-server/target/yudao-server.*.log
```

Expected: port 48080 is listening. Any scan match is treated as a concrete blocker, not hidden by disabling a module or writing to the remote database.

- [ ] **Step 4: Verify Swagger and module routes**

```powershell
$swagger = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:48080/swagger-ui/index.html' -TimeoutSec 15
$openApi = Invoke-RestMethod -Uri 'http://127.0.0.1:48080/v3/api-docs' -TimeoutSec 30
if ($swagger.StatusCode -ne 200) { throw "Swagger status $($swagger.StatusCode)" }
$requiredFragments = @('member','bpm','report','mp','pay','product','promotion','trade','statistics','crm','erp','ai','iot','mes','wms','hrm','fms','im')
$paths = @($openApi.paths.PSObject.Properties.Name)
$missingFragments = @($requiredFragments | Where-Object {
  $fragment = "/$_/"
  -not ($paths | Where-Object { $_.Contains($fragment) })
})
if ($missingFragments.Count) { throw "Missing OpenAPI module fragments: $($missingFragments -join ', ')" }
"Swagger status: $($swagger.StatusCode); paths: $($paths.Count)"
```

Expected: Swagger returns 200 and OpenAPI contains at least one route for every enabled module fragment.

- [ ] **Step 5: Record final evidence**

```powershell
git status --short --branch
git log -3 --oneline
Get-NetTCPConnection -LocalPort 48080 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
```

Expected: only intended tracked changes/commits are present and the new backend PID owns port 48080. Do not claim AI provider calls or IoT broker traffic work without credentials and broker endpoints.

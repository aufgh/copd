# COPD 呼吸趋势演示（Android + 本机 FastAPI）

手机录 20 秒平静呼吸 + 3 声咳嗽 → 上传到你自己电脑上的服务 → 返回可重复的虚拟趋势结果 → 展示单次结果与七天趋势。全程只走局域网，不依赖互联网。

当前推理引擎是 `demo`（虚拟结果，可重复）。后续接入真模型时**只替换服务端 `app/inference/` 下的引擎实现**，Android 页面和接口结构不需要改动。

---

## 一、服务端（Windows 电脑）

需要 Python 3.10 及以上（推荐 3.12）。在 `server` 目录下执行：

```bat
cd server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python run_server.py
```

`run_server.py` 启动后会把本机所有局域网地址打印出来，形如：

```
可用地址（把其中一个填到手机 App 的设置页）：
  http://192.168.1.100:8000
```

把打印出来的地址填进手机 App 的设置页即可。**不要填 127.0.0.1 或 localhost**，那是电脑自己的地址，手机连不上。

第一次运行需要放行防火墙。Windows 弹窗时选"允许专用网络访问"；如果没弹或误点了拒绝，用管理员权限的命令行执行一次：

```bat
netsh advfirewall firewall add rule name="COPD Demo 8000" dir=in action=allow protocol=TCP localport=8000
```

### 可选环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `COPD_PORT` | `8000` | 端口 |
| `COPD_ENGINE` | `demo` | `demo` / `real`，换真模型时改这里 |
| `COPD_DEMO_SCENARIO` | `attention` | `stable` / `attention` / `alert` |
| `COPD_DEMO_MIN_SECONDS` / `COPD_DEMO_MAX_SECONDS` | `2.0` / `4.0` | 模拟分析耗时区间 |
| `COPD_SEED_HISTORY` | `1` | 是否自动铺 7 天演示历史，`0` 关闭 |

### 自测

```bat
cd server
.venv\Scripts\activate
pip install pytest httpx
pytest
```

`tests/test_flow.py` 覆盖了整条链路：上传 → 轮询 → 结果，同日同用户结果可重复、幂等重传不产生第二条记录、七天历史齐全、质检失败返回正确的 error code，以及"结果文案不出现确诊类字样"的合规检查。

---

## 二、Android 端

### 关于缺失的 gradlew

仓库里只有 `android/gradle/wrapper/gradle-wrapper.properties`，没有 `gradlew` / `gradlew.bat` / `gradle-wrapper.jar`（二进制文件生成不了，也不适合手写）。

- **用 Android Studio 打开 `android` 目录：不受影响。** Studio 会读 properties 文件，自己下载对应版本的 Gradle 发行包。
- **想在命令行 `gradlew assembleDebug`：** 在 `android` 目录里跑一次 `gradle wrapper --gradle-version 8.14.3`（需要机器上已装 Gradle；也可以在 Studio 的 Run Anything 里执行这条命令，`wrapper` 是 Gradle 内置任务，不需要 gradlew 就能跑）。注意跑完检查 `gradle-wrapper.properties` 的 `distributionUrl` 有没有被改回官方地址，被覆盖了就改回腾讯云镜像并补上 `validateDistributionUrl=false`。生成的三个文件建议提交上去，根 `.gitignore` 已经写了 `!android/gradle/wrapper/gradle-wrapper.jar` 的例外。

### 国内网络：把 Gradle 下载地址换成镜像

`services.gradle.org` 在国内经常连不上（报 `Connection refused` 或卡住）。打开 `android/gradle/wrapper/gradle-wrapper.properties`，把 `distributionUrl` 换成腾讯云镜像，顺手把校验关掉：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.14.3-bin.zip
validateDistributionUrl=false
```

注意 `https` 后面那个反斜杠是 properties 文件的转义写法，必须保留。改完在 Studio 里 `File → Sync Project with Gradle Files`。

如果接下来卡在下载依赖库（`dl.google.com` / `repo.maven.apache.org`），`android/settings.gradle.kts` 里已经把阿里云镜像放在最前面了，正常不需要动。想改回官方源就把两个 `repositories` 块里 `maven("https://maven.aliyun.com/...")` 那几行注释掉，后面的 `google()` / `mavenCentral()` / `gradlePluginPortal()` 就是官方源。

报错是 `Connection refused` 而不是超时，还有一种可能：Studio 里配了一个已经关掉的代理。检查 `File → Settings → Appearance & Behavior → System Settings → HTTP Proxy`，要么改成 `No proxy`，要么把你的代理软件打开。

### 构建

1. Android Studio 里 `Open` → 选 `android` 目录（不是仓库根目录）。
2. 等 Gradle Sync 完成。第一次会下载 Gradle 发行包和依赖，需要联网，几分钟。`local.properties`（SDK 路径）由 Studio 自动生成，不用手写。
3. 手机开启 USB 调试并连上电脑，点 Run。

如果 Sync 报 AGP / Kotlin 版本与你的 Android Studio 不匹配，只改 `android/gradle/libs.versions.toml` 里最上面两行：

```toml
agp = "8.11.1"
kotlin = "2.1.21"
```

首次运行 App 会申请麦克风权限，必须允许。

### SDK Platform 不用额外下载

`compileSdk = 36` / `buildToolsVersion = "35.0.0"`，都是本机 SDK 里已经装好的（`D:\Android\SDK`）。这样 Gradle Sync 不会去 `dl.google.com` 下 Platform 35 —— 那个域名在国内经常卡住，而且它不走 maven 镜像，配了阿里云也没用。

`targetSdk` 故意停在 35：36 会强制启用 Android 16 的 edge-to-edge 等行为变更。`targetSdk` 只是清单里的数字，不需要装对应 platform。

换到别的机器上构建，如果报找不到 `android-36`，把 `compileSdk` 改成那台机器已装的最高版本（34 及以上都能编过），`buildToolsVersion` 那行直接删掉让 AGP 用默认值。

### 明文 HTTP 说明

Android 9 以后默认禁止明文 HTTP。项目里已经通过 `res/xml/network_security_config.xml` 放开，因为演示服务在局域网内跑 HTTP。这是演示用配置，正式产品应改为 HTTPS 并收紧到指定域名。

---

## 三、演示前检查清单

按顺序过一遍，大约两分钟：

1. 电脑和手机连在**同一个 Wi-Fi**（手机没有偷偷切到 5G 流量）。
2. 电脑上 `python run_server.py` 已启动，命令行没有报错。
3. 浏览器打开电脑上的 `http://<局域网IP>:8000/api/v1/health`，能看到 JSON。
4. 手机 App 设置页地址填的是上面那个 IP，返回首页顶部显示"已连接服务器"。
5. 首页七天趋势图有数据（演示历史会在第一次连接时自动生成）。
6. 完整走一遍：呼吸 → 咳嗽 → 分析 → 结果 → 七天趋势。
7. 手机音量键调一下、把手机静音关掉（试听功能要出声）。
8. 关掉电脑的休眠，演示中途别让电脑睡过去。

**兜底方案**：如果现场 Wi-Fi 有客户端隔离（手机能上网但连不到电脑），用手机开热点、让电脑连手机热点，然后重新执行 `python run_server.py`，用新打印出来的地址。

---

## 四、合规红线（已在代码里落实）

- 任何页面都不出现"确诊 COPD""已经发生急性加重"这类表述，结果文案只描述趋势变化和建议动作。
- 每个页面底部固定显示：本结果仅用于健康趋势观察，不构成医学诊断。
- 上传文件用随机检测编号命名（`{assessment_id}_{breathing|cough}.wav`），不含姓名；设置页明确提示匿名编号不要填真实姓名。
- 结果页和首页在引擎为 `demo` 时显示"演示引擎"标记，避免截图被误当成真实模型结论。
- 上传失败只要不是录音本身的问题，就只提示重传，不要求用户重新录音（由服务端 `needs_rerecord` 字段驱动）。

---

## 五、接真模型时要改的地方

只有一处：在 `server/app/inference/real.py` 里实现 `InferenceEngine`，然后把 `COPD_ENGINE` 设成 `real`。

引擎拿到的是 `InferenceRequest`（已解码成 16 kHz 单声道 float32 的样本、服务端算好的个人基线、录音时间），返回 `InferenceResult`；`app/inference/mapper.py` 负责把结果映射成对外的 JSON 字段。也就是说：推理内部结构可以随便变，接口不用变，Android 不用改。

---

## 六、换一台电脑构建

仓库里**不包含**这些机器相关的东西（`.gitignore` 已排除），克隆下来后需要各自生成：

| 缺什么 | 怎么来 |
| --- | --- |
| `android/local.properties` | Android Studio 打开 `android` 目录时自动生成，写的是那台机器的 SDK 路径 |
| `server/.venv/` | 见下面第 2 步，重新建虚拟环境 |
| `server/data/`（数据库、上传的 wav） | 服务端首次启动时自动创建，演示数据自动补齐 |
| `gradlew` / `gradle-wrapper.jar` | 见上面"关于缺失的 gradlew"；用 Studio 构建不需要 |

克隆之后：

```bat
git clone <仓库地址> copd
cd copd\server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python run_server.py
```

Android 端：Studio `Open` → 选 `copd\android` 目录 → 等 Sync → Run。

两处按新机器情况检查：`android/app/build.gradle.kts` 的 `compileSdk = 36`（改成那台机器已装的最高 Platform，34 及以上都能编过；`buildToolsVersion` 那行删掉让 AGP 用默认值），以及 App 设置页里的服务器地址（换电脑后局域网 IP 会变，代码里的默认值 `http://192.168.1.100:8000` 未必还对）。

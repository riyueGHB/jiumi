<div align="center">

<img src="docs/images/jiumi-icon.png" alt="揪咪图标" width="80" />

# 揪咪 Jiumi

看清应用在前台和后台做了什么，再决定留下、限制、停用还是卸载。
<br>无需 Root，也无需电脑，记录保存在本机。

**[下载 APK](https://github.com/ybblab/Jiumi/releases/latest)** · [使用指南](https://jiumi.ybblab.com/download/) · [官网](https://jiumi.ybblab.com)

<sub>Android 11 及以上 · 64 位 ARM · 安装包约 4 MB · <a href="LICENSE">Apache 2.0</a></sub>

**简体中文** · [English](docs/readme/README.en.md) · [日本語](docs/readme/README.ja.md) · [한국어](docs/readme/README.ko.md) · [Español](docs/readme/README.es.md) · [Deutsch](docs/readme/README.de.md) · [Français](docs/readme/README.fr.md) · [हिन्दी](docs/readme/README.hi.md) · [Bahasa Indonesia](docs/readme/README.id.md) · [Português (Brasil)](docs/readme/README.pt-BR.md) · [Русский](docs/readme/README.ru.md)

</div>

## 界面速览

<p align="center">
  <a href="docs/images/monitor.zh.png"><img src="docs/images/monitor.zh.png" width="196" alt="30 天活动概览与关注热力图" /></a>&nbsp;
  <a href="docs/images/monitor-week.zh.png"><img src="docs/images/monitor-week.zh.png" width="196" alt="7 天访问走势与关注热力图" /></a>&nbsp;
  <a href="docs/images/by-app.zh.png"><img src="docs/images/by-app.zh.png" width="196" alt="按应用排行，比较访问次数和后台行为" /></a>&nbsp;
  <a href="docs/images/app-detail.zh.png"><img src="docs/images/app-detail.zh.png" width="196" alt="应用详情里的 30 天行为曲线" /></a>
</p>

<p align="center">
  <a href="docs/images/timeline.zh.png"><img src="docs/images/timeline.zh.png" width="196" alt="时间线，按时间查看应用行为和状态" /></a>&nbsp;
  <a href="docs/images/by-type.zh.png"><img src="docs/images/by-type.zh.png" width="196" alt="按类型，查看各类行为涉及的应用和次数" /></a>&nbsp;
  <a href="docs/images/apps.zh.png"><img src="docs/images/apps.zh.png" width="196" alt="应用，按来源查看及筛选应用" /></a>&nbsp;
  <a href="docs/images/connection.zh.png"><img src="docs/images/connection.zh.png" width="196" alt="四种启动方式：无线调试、网络调试、Shizuku 和 Root" /></a>
</p>

<p align="center"><sub>以上界面均为示例数据，应用和地址均为虚构。点按图片查看大图。</sub></p>

## 功能

### 看清行为

| 功能 | 可以看到或做到什么 |
| --- | --- |
| 活动概览 | 按**本次、今天、7 天或 30 天**查看访问总数、走势、均值、峰值和后台次数。点按或滑动曲线可查看某个时段，没有监测也没有记录的时段标为「无记录」。 |
| 需要关注 | 用**热力图**看出问题集中在哪天、哪个时段，并集中查看在后台上传数据、后台敏感行为、短时间内反复读取和被拒绝后仍反复尝试。 |
| 按应用排行 | 按全部或后台次数找出最活跃的应用，对比各自读取的数据。系统例行工作单独折叠。 |
| 应用详情 | 用同样的曲线查看单个应用的走势。点按某类数据，曲线和时间轴只显示这一类。 |
| 行为记录 | 按时间、应用或类型查看位置、剪贴板、设备标识等 **12 类数据、56 种访问**，也可以筛选后台记录。 |
| 网络活动 | 按应用查看连接地址和可用的流量统计。开启域名记录后，可显示关联到的域名。 |
| 系统历史 | 在应用详情查看 Android 保留的权限访问明细和累计次数，包括开始监测前的部分行为。 |

### 监测与记录

| 功能 | 可以看到或做到什么 |
| --- | --- |
| 监测白名单 | 在「设置 › 监测 › 监测白名单」选择不想监测的应用。揪咪不再为它们生成新记录，已有记录暂时隐藏。移出后恢复显示，期间的活动不会补录。 |
| 通知 | 监测时显示已监测时长和停止按钮，可在「设置 › 通知」关闭。开启「显示监测摘要」后显示这次监测的**访问次数和后台次数**，不显示应用名。开启「需要关注提醒」后，应用新出现需要关注的行为时会通知你，同一次监测里同一应用的同一项只提醒一次。 |
| 日志导出 | 在「设置 › 日志 › 导出日志」将选定时段的记录导出为包含 CSV 的 ZIP，供保存和分析。 |

## 管理应用

「应用」页按来源整理应用。在应用详情里查看行为记录，再决定要不要改动。所有管理操作都由你发起，揪咪改动后会读回系统状态确认是否生效，系统拒绝时显示原因。

<p align="center">
  <a href="docs/images/app-disable.zh.png"><img src="docs/images/app-disable.zh.png" width="250" alt="官网演示：停用应用的确认弹窗" /></a>&nbsp;
  <a href="docs/images/app-delete.zh.png"><img src="docs/images/app-delete.zh.png" width="250" alt="官网演示：卸载应用的确认弹窗与备份说明" /></a>&nbsp;
  <a href="docs/images/app-restore.zh.png"><img src="docs/images/app-restore.zh.png" width="250" alt="官网演示：已卸载应用的恢复和清除备份按钮" /></a>
</p>

| 操作 | 会发生什么 |
| --- | --- |
| 权限管理 | 在应用详情的「权限管理」里逐项关闭或开启权限。「可控权限总开关」一次关闭全部，再打开时只恢复揪咪关闭过的权限。系统锁定的权限标为只读。 |
| 联网管理 | 在支持的设备上，通过 Android 自带的防火墙为单个应用关闭或恢复联网，不占用 VPN。应用与其他应用共用 UID、规则由其他工具设置，或无法确认只影响这一个应用时，开关会锁定并说明原因。 |
| 停用与启用 | 仅为当前用户停用，保留应用和数据。可在「应用 › 已停用」里重新启用。已停用的应用图标带雪花标记。 |
| 已重新启用 | 揪咪发现自己停用过的应用又被启用时，列表和详情会标为「已重新启用」，并在时间线记下发现时间。开启「设置 › 应用管理 › 重新启用时自动停用」后，揪咪会再次停用。 |
| 卸载自行安装的应用 | 输入完整包名确认后，揪咪先备份安装包，再卸载应用。之后可在「应用 › 已卸载」尝试重新安装。**卸载会清除应用数据，备份仅包含安装文件。** |
| 卸载预装应用 | 在「设置 › 应用管理」开启「允许卸载预装应用」后，可以仅为当前用户卸载受支持的预装应用。出厂安装包和应用数据会保留，可在「应用 › 已卸载」尝试恢复。未开启时点「卸载」，揪咪会提示并带你前往应用管理。 |

「设置 › 应用管理」的「执行身份」显示这些操作以谁的身份执行。使用 Root，或以 Root 启动的 Shizuku 时，停用、启用应用，以及卸载、恢复预装应用以 Root 身份执行，能更改手机不允许 shell 更改的系统应用。使用无线调试或网络调试时，应用管理以 shell 身份执行。部分系统应用会受手机限制，揪咪会说明原因。自行安装的应用由 shell 卸载和恢复。卸载仅影响当前用户，工作资料或分身里的同一应用会保留。

核心系统组件默认受保护。改动手机依赖的组件可能影响系统功能，揪咪会在操作前提示风险。

## 不用 Root，怎么看到后台？

Android 在应用运行时会产生权限访问记录、连接表、系统跟踪和事件日志。揪咪探针以 **shell 身份**读取可用的系统信号，App 再按规则整理成记录，存入手机上的数据库。监测过程不会向其他应用注入代码。停用、卸载、关闭权限或断网等操作需要你在应用管理中主动发起。

<p align="center"><img src="docs/images/how-it-works.zh.png" alt="应用行为产生 Android 系统信号，揪咪探针以 shell 身份读取，揪咪 App 整理并保存在本机。可选的域名记录通过本机 VPN 接收 DNS 查询。" width="900" /></p>

| 信号来源 | 揪咪能看到什么 |
| --- | --- |
| 权限访问记录（AppOps） | 哪个应用在什么时候使用了哪项权限，以及系统保留的历史。 |
| 连接表 | 默认每 5 秒读取一次，查看应用连接了哪些地址。 |
| 系统跟踪（Perfetto） | 可识别的系统服务调用，以及系统支持时的按连接流量。 |
| 事件日志 | 进程何时被拉起，以及系统记录的启动原因。 |

### 四种连接方式

| 方式 | 适合 | 首次设置 | 手机重启后 |
| --- | --- | --- | --- |
| **无线调试**（推荐） | 大多数手机，无需 Root 和电脑 | 连接 Wi-Fi，在手机上输入一次配对码 | 配对仍有效。需要连上 Wi-Fi 并开启无线调试 |
| **网络调试** | 已开启 ADB 端口的手机 | 自动查找或手动填写端口，按需在系统弹窗里授权，无需配对码 | 需要手机重新开启端口 |
| **Shizuku** | 已在使用 Shizuku | 启动 Shizuku，并允许揪咪使用 | 需要 Shizuku 已运行 |
| **Root** | 已 Root 的手机 | 在 Root 管理器里允许揪咪 | 授权有效时即可使用 |

四种方式都以 shell 身份运行探针。只有使用 Root，或以 Root 启动的 Shizuku 时，上文列出的应用管理操作会以 Root 身份执行。

> **网络调试安全提醒：** 揪咪只连接本机地址，也不会替你开启端口。但用 `adb tcpip` 等方式开启的端口，通常也能被同一网络里的其他设备访问。请只在可信网络使用，不要允许来历不明的调试授权，不用时关闭端口。

**域名记录（可选）**使用本机 VPN 接收 DNS 查询，并原样转发给当前网络的 DNS 服务器。其他流量照常直连。开启时会占用系统的 VPN 位置，应用自带的加密 DNS 或指定服务商的私人 DNS 可能绕过记录。

## 下载与开始监测

1. 在运行 Android 11 或更高版本的 64 位 ARM 手机上，从 [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest) 下载并安装 APK。
2. 首次打开时选择启动方式。推荐使用无线调试：连接 Wi-Fi，按引导允许通知、开启开发者选项，并在揪咪的通知里输入系统显示的 6 位配对码。通知栏输不了时，在第 3 步点「通知栏输不了配对码？」，改用分屏、小窗或快捷设置磁贴输入。手机已开启 ADB 端口时，也可以选择网络调试。
3. 到「监测」页点「开始监测」。监测期间通知栏会显示常驻通知，可以从通知或 App 停止监测。

<p align="center">
  <a href="docs/images/pairing-split.zh.png"><img src="docs/images/pairing-split.zh.png" width="196" alt="其他配对方式：分屏或小窗" /></a>&nbsp;
  <a href="docs/images/pairing-tile.zh.png"><img src="docs/images/pairing-tile.zh.png" width="196" alt="其他配对方式：快捷设置磁贴" /></a>
</p>

重启手机、更新揪咪或调试服务中断后，揪咪会在所选连接可用时尝试续上同一次监测，回到揪咪时也会再试一次。如果卡片显示「监测已中断」，请恢复所选连接后点「恢复」。中断的时间不计入监测时长。

使用网络调试时，探针停止后揪咪会尝试用之前连接过的端口或重新查找本机端口来续上监测。找不到端口或需要授权时，会在监测卡片上提示。

使用无线调试时，建议同时开启 USB 调试。仅开启无线调试时，断开 Wi-Fi 可能让系统关闭调试服务并结束监测。「设置 › 连接 › 备用连接」默认关闭，只适用于系统已配置本机专用 ADB 端口的手机，揪咪验证后复用这个端口，不会开启新端口。详细操作见[使用指南](https://jiumi.ybblab.com/download/)。

## 隐私与使用边界

- **记录留在本机。** 揪咪没有账号，也不上传监测记录。App 可从 GitHub 检查更新，选择更新后会下载安装包并校验大小、SHA-256 和签名。更新检查可关闭。
- **记录行为和连接信息。** 揪咪可以显示某项权限被使用或访问被拒绝，无法看到聊天内容、密码或网络传输正文。
- **能看到的范围取决于系统。** 短连接可能在两次读取之间结束，直接读取文件等行为可能没有可用信号。按连接统计流量需要 Android 14 或更高版本及系统支持。为减少误报，揪咪会略去部分无法确认的行为。
- **概览和排行的口径不同。** 概览会用系统保留的分时段计数补充，可能包含开始监测前的一段时间。排行只计算揪咪的逐条记录。一次监测超过 31 天时，「本次」概览和通知摘要只统计前 31 天，请改看「30 天」。
- **监测时会增加耗电。** 耗电量取决于应用活动，不需要时可从通知停止监测。

## 从源码构建

需要 JDK 17 和 Android SDK。

```sh
cd code
./gradlew :app:assembleDebug
```

监测分类、提醒和受保护应用的规则位于 [rules 目录](code/app/src/main/assets/rules/)。代码结构与贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 贡献与许可

欢迎提交 Issue 和 Pull Request。安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

项目采用 [Apache License 2.0](LICENSE)，第三方组件及其许可见 [NOTICE](NOTICE)。请在你拥有或已获授权的设备上使用。

## Star 历史

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="揪咪的 GitHub Star 趋势图" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**如果揪咪帮你看清了手机里的应用在做什么，欢迎点个 ⭐ 支持我们！**

</div>

# 打卡提醒 (DakaCheck)

一个 Android 考勤辅助 App —— **上班忘打卡 09:15 提醒，下班忘打卡 02:30 提醒**，并自动同步公司 IPSA 考勤日历。

> 全程后台运行，不占屏幕、不弹页面、没有常驻通知栏。

---

## 功能

### 1. 打卡核对

- **09:15** 检查当天上班卡，没打就提醒
- **02:00** 检查前一天下班卡（支持加班到 0 点后的情况），没打就提醒
- 考勤日按 **04:00** 划分：0–4 点页面显示的是前一天的考勤
- 非工作日自动跳过，不打扰
- 月末最后一个周一，如有考勤异常会全屏响铃提醒

### 2. IPSA 考勤日历

- 自动读取公司 IPSA 平台当月考勤
- 按平台**颜色标记**判断状态，不依赖关键字：
  - 🔴 红色三角 → 考勤/TM 工时异常
  - 🟢 绿色三角 → 请假 / 外出公干（不算异常）
  - 有打卡记录 → 正常
  - 空白格子 → 不标记
- 每天 **14:30** 后台静默同步一次，无感刷新
- 登录失效时弹窗提示「需重新登录」

### 3. 其他

- 工时自动累计（月累计 / 已统计天数）
- 工时补录
- 桌面小组件（4×2 / 2×2）
- 节假日自动识别（含调休）

---

## 安装

下载 [`apk/dakacheck.apk`](apk/dakacheck.apk) 安装。

首次使用需要在 App 内「权限设置与测试」中开启：

| 权限 | 用途 | 必要性 |
|---|---|---|
| 无障碍服务 | 后台读取页面、点击打卡 | **必需** |
| 电池优化白名单 | 防止后台被系统杀掉 | **必需** |
| 精确闹钟 | 09:15 / 02:00 准时提醒 | **必需** |
| 通知权限 | 提醒通知 | **必需** |
| 全屏提醒权限 | 锁屏时弹出闹钟 | 可选 |
| 使用情况访问 | 防止误判（打开过打卡 App 就不提醒） | 可选 |

> 不需要 root，不需要改系统设置。

---

## 技术实现

### 打卡状态读取

通过**无障碍服务**读取「软通打卡」App 的页面文本，识别上班/下班卡签到时间。

### IPSA 考勤抓取

Android 15 禁止后台启动 Activity，所以采用 **1×1 透明 WebView 挂在无障碍服务的图层上**：

```
AccessibilityService
    └── TYPE_ACCESSIBILITY_OVERLAY WebView (1×1, alpha=0, 屏幕外)
            └── 加载考勤页 → 读取 DOM → 解析 → 销毁
```

整个过程**前台毫无变化**，用户完全无感。

- 必须使用**桌面 UA**，否则门户页渲染异常
- 导航链路：`/portal` → `/portal/Special/hwkq` → `/apsa/Attendance/Home/HomeView/Index`

### 考勤状态判定（`ColorParser`）

不穷举异常关键字，直接读平台给的 CSS 类：

```javascript
// 每个日期格子的 td
<td class="error-red">   → 异常
<td class="holiday_td">  → 节假日
<td>                     → 正常（若真有打卡记录）
```

颜色判定的好处：**平台新增任何异常类型都自动覆盖**。

---

## 源码结构

```
src/com/dsh/dakacheck/
├── MainActivity.java          主页
├── HoursActivity.java         工时日历
├── SettingsActivity.java      权限设置与测试
├── KaoqinLoginActivity.java   IPSA 登录页（含遮罩）
├── BackgroundGrab.java        后台静默抓取（无障碍层 WebView）
├── ColorParser.java           按颜色解析考勤状态 ★核心
├── KaoqinParser.java          页面文字解析
├── Warden.java                考勤同步调度与异常判定
├── AttendanceService.java     无障碍服务
├── Scheduler.java             闹钟调度
├── Decide.java                打卡判定逻辑
├── Notify.java                通知与响铃
├── Store.java                 本地存储
├── Config.java                配置项
├── Holidays.java              节假日（含联网更新）
├── Archive.java               历史归档
├── Work.java                  工作时长计算
├── Widgets.java               桌面小组件
└── ...
```

### 核心逻辑速览

| 文件 | 职责 |
|---|---|
| `ColorParser` | 从页面 DOM 提取每个日期的 CSS 类，判定红/绿/正常 |
| `BackgroundGrab` | 隐形 WebView 加载考勤页，生命周期管理 |
| `Warden` | 接收数据 → 写入存储 → 判断是否要提醒 |
| `Decide` | 09:15 / 02:00 的打卡判定 |
| `AttendanceService` | 无障碍服务，读打卡 App 页面 |

---

## 构建

### 依赖

- JDK 8+
- Android SDK（build-tools 34、platform 34）

### 命令

```bash
python build.py
```

脚本流程：

```
javac → d8 → aapt2 → zipalign → apksigner
```

产物：`build/dakacheck.apk`

> `debug.keystore` 仅用于本地调试签名，正式发布请自行生成签名文件。

---

## 已知限制

- **仅支持当月考勤同步**：手机竖屏下平台页面没有月份切换控件
- 依赖公司考勤系统的页面结构，如平台改版可能失效
- 考勤系统域名相关的逻辑需要自行适配

---

## 隐私

- 账号密码**只保存在手机本地**（SharedPreferences），不上传任何服务器
- 无网络请求上报、无统计埋点
- 源码中不含任何硬编码凭据

---

## 免责声明

本项目仅供个人学习与效率提升使用，请遵守所在公司的考勤制度。

因使用本工具产生的考勤问题，作者不承担责任。

---

design by Karl

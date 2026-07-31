# CountStat 原生 Android App

[![GitHub](https://img.shields.io/badge/GitHub-lilibushilicc%2Fcountstat--android-181717?logo=github&style=flat-square)](https://github.com/lilibushilicc/countstat-android)

根据上传的 4 个设计页改写的原生 Android 工程，不使用 WebView。

## 技术栈

- Java
- Android SDK 原生 View
- SQLite（SQLiteOpenHelper）本地数据库
- 原生 HttpURLConnection 实现 WebDAV 同步（无第三方依赖）
- SharedPreferences 用于 WebDAV 配置

## 数据存储

| 数据 | 存储方式 |
|------|---------|
| 计件记录（日期 + 机器明细） | SQLite `countstat.db` |
| WebDAV 配置、自动同步开关 | SharedPreferences |

数据库表 `records`：
- `date` TEXT PRIMARY KEY — 记录日期
- `machines` TEXT — 机器明细 JSON 数组 `[{name, qty, price}]`
- `updated_at` INTEGER — 更新时间戳

每台机器独立存储名称、数量、单价，支持任意数量机器。

## 工程结构

```
app/src/main/java/com/countstat/app/
├── MainActivity.java       # 界面与交互
├── DbHelper.java            # SQLite 数据库
├── Record.java              # 记录/机器数据模型
├── WebDavClient.java        # WebDAV 上传/下载/列目录
└── AutoSyncManager.java     # 自动同步调度 + JSON 导出
countstat-web/
└── index.html               # WebDAV 端数据看板（零依赖单文件）
```

## WebDAV 端看板

`countstat-web/index.html` 是与备份放在同一目录的只读数据看板：

- App 每次备份时自动上传 `countstat-export.json`（记录全量数据）和 `countstat-latest.json`（最新备份指针）
- 把 `index.html` 上传到 WebDAV 备份目录，通过支持静态托管的 WebDAV 服务（或任意静态服务器）访问即可
- 同目录原则：页面用相对路径读取 JSON，无跨域问题
- 功能：周期（本周/本月/全部）切换、折线/柱状趋势图、机器产量分布、每日记录搜索/筛选/展开明细、60s 自动刷新

## 已实现功能

### 首页录入
- 自定义机器数量（动态添加/删除行）
- 每台机器独立名称、数量、单价
- 实时计算总件数和收入

### 历史记录
- 按日期筛选
- 回填修改、删除记录

### 汇总统计
- 本周/本月/全部切换
- 收入、件数、日均、机器产量分布

### WebDAV 自动同步
- 打开应用时自动从 WebDAV 拉取最新备份
- 修改/删除记录后 3 分钟（可配置）自动上传
- 手动备份、恢复、测试连接
- 从 WebDAV 列出并恢复历史备份

## 打开方式

1. Android Studio 打开本目录
2. 等待 Gradle 同步（使用 Gradle 8.9，已配置代理）
3. 运行 `app`

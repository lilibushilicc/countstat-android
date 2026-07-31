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

`countstat-web/index.html` 是零依赖单文件只读数据看板，App 每次备份时自动上传两个数据文件：

- `countstat-export.json`：全量记录 + 机器配置 + 数据库时间戳
- `countstat-latest.json`：最新备份文件名指针

### 用法一：同目录部署（推荐，任何 WebDAV 都行）

把 `index.html` 和两个 JSON 文件放到**同一个目录**（页面用相对路径读取，不涉及跨域）：

1. 在手机 App「备份」页填写 WebDAV 账号并「立即备份」，自动生成两个 JSON
2. 把 `index.html` 上传到 WebDAV 备份目录（或在 PC 上直接打开 `countstat-web/index.html` 本地文件）
3. 通过支持静态托管/WebDAV 预览的站点访问该目录下的 `index.html` 即可

无需任何 CORS 配置，坚果云等不支持跨域的网盘也适用。

### 用法二：跨区部署（WebDAV 支持 CORS 时）

如果 WebDAV 服务返回 `Access-Control-Allow-Origin` 头（例如自建 Nextcloud/nginx 配置），看板可部署在**任意位置**，通过 URL 参数指向远程数据：

```
https://你的看板地址/countstat-web/index.html?data=https://dav.example.com/remote.php/webdav/countstat/backup/countstat-export.json
```

- `data` 填远程 `countstat-export.json` 的完整地址，`countstat-latest.json` 会自动按同目录推导
- 同目录部署时忽略该参数即可
- 跨区部署时若 WebDAV 未配置 CORS，浏览器会拦截读取，页面会提示原因

### 页面功能

- 周期切换：本周 / 本月 / 全部
- 趋势图：折线 / 柱状两种模式
- 机器产量分布条
- 每日记录：搜索、日期过滤、展开看各机器明细
- 60 秒自动刷新（`?data=` 跨区模式同样生效）

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

package com.countstat.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动同步调度器。
 *
 * 行为：
 * - 打开时拉取（pullOnOpen）：仅当远程备份比本地新时下载并覆盖，避免本地数据被旧备份覆盖
 * - 修改后延时上传（scheduleUpload）：每次保存记录后重置计时器，
 *   到点（默认 3 分钟）把本地 db 上传到 WebDAV，并附带 JSON 导出与最新备份清单
 *
 * 所有网络操作在后台线程执行，结果回调到主线程。
 */
public class AutoSyncManager {

    public static final long DEFAULT_DELAY_MS = 3 * 60 * 1000L; // 3 分钟

    /** Web 端展示页固定读取的文件名。 */
    public static final String EXPORT_NAME = "countstat-export.json";
    public static final String LATEST_NAME = "countstat-latest.json";

    public interface Callback {
        void onMessage(String msg);
    }

    private final Context context;
    private final DbHelper dbHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler delayHandler = new Handler(Looper.getMainLooper());

    private String url;
    private String user;
    private String password;
    private String basePath;
    private boolean autoSync = false;
    private long delayMs = DEFAULT_DELAY_MS;
    private String machinesJson = "";
    private Callback callback;

    private final Runnable uploadTask = this::doUpload;

    public AutoSyncManager(Context context, DbHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void configure(String url, String user, String password, String basePath,
                          boolean autoSync, long delayMinutes) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.basePath = basePath;
        this.autoSync = autoSync;
        this.delayMs = Math.max(60_000L, delayMinutes * 60_000L);
    }

    public boolean isAutoSyncEnabled() {
        return autoSync && url != null && !url.isEmpty();
    }

    /** 设置机器配置 JSON（透传给 Web 端导出文件）。 */
    public void setMachinesJson(String json) {
        this.machinesJson = json == null ? "" : json;
    }

    private WebDavClient buildClient() {
        return new WebDavClient(url, user, password, basePath);
    }

    /** 打开时拉取远程最新数据库（仅当远程比本地新）。 */
    public void pullOnOpen() {
        if (!isAutoSyncEnabled()) return;
        io.execute(this::doPull);
    }

    private void doPull() {
        WebDavClient client = buildClient();
        List<String> backups = client.listBackups();
        if (backups.isEmpty()) {
            post("自动同步：远程暂无备份");
            return;
        }
        Collections.sort(backups, Collections.reverseOrder());
        String latest = backups.get(0);
        // 备份文件名含上传时间戳；本地文件比它新说明本地数据更新，跳过拉取
        long remoteTime = backupTime(latest);
        long localTime = dbHelper.getDbFile().lastModified();
        if (remoteTime > 0 && localTime > remoteTime) {
            post("自动同步：本地已是最新");
            return;
        }
        File tmp = new File(context.getCacheDir(), "remote_countstat.db");
        if (client.download(latest, tmp)) {
            // 关闭本地库后替换文件，再重新打开
            dbHelper.close();
            File local = dbHelper.getDbFile();
            local.getParentFile().mkdirs();
            copyFile(tmp, local);
            // 触发重新打开（下次访问即可）
            dbHelper.getReadableDatabase();
            post("自动同步：已从远程恢复最新备份");
        } else {
            post("自动同步：下载失败");
        }
    }

    /** 解析备份文件名中的时间戳，解析失败返回 0（表示无法判断）。 */
    private long backupTime(String name) {
        try {
            String prefix = "countstat_";
            int start = name.indexOf(prefix);
            int end = name.lastIndexOf(".db");
            if (start < 0 || end < 0 || end <= start) return 0;
            String time = name.substring(start + prefix.length(), end);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.CHINA);
            return format.parse(time).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 修改后延时上传。每次调用会重置计时器。 */
    public void scheduleUpload() {
        if (!isAutoSyncEnabled()) return;
        delayHandler.removeCallbacks(uploadTask);
        delayHandler.postDelayed(uploadTask, delayMs);
    }

    /** 立即上传（手动备份用）。 */
    public void uploadNow() {
        io.execute(this::doUpload);
    }

    private void doUpload() {
        WebDavClient client = buildClient();
        String name = WebDavClient.backupName();
        String result = client.upload(dbHelper.getDbFile(), name);
        if (result == null) {
            post("WebDAV 备份失败");
            return;
        }
        // 同时上传 JSON 导出与最新备份清单，供 WebDAV 端的展示页读取
        String export = buildExportJson(name);
        if (!export.isEmpty()) client.uploadText(export, EXPORT_NAME);
        client.uploadText(buildLatestManifest(name), LATEST_NAME);
        post("已备份到 WebDAV：" + name);
    }

    /** 组装 Web 端展示页所需的 JSON 导出。 */
    private String buildExportJson(String dbName) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "CountStat");
            root.put("db", dbName);
            long updatedAt = System.currentTimeMillis();
            JSONArray records = new JSONArray();
            for (Record record : dbHelper.getAllRecords()) {
                JSONObject r = new JSONObject();
                r.put("date", record.date);
                r.put("updatedAt", record.updatedAt);
                updatedAt = Math.max(updatedAt, record.updatedAt);
                JSONArray machines = new JSONArray();
                for (Record.Machine m : record.machines) {
                    JSONObject mm = new JSONObject();
                    mm.put("name", m.name);
                    mm.put("qty", m.quantity);
                    mm.put("price", m.unitPrice);
                    machines.put(mm);
                }
                r.put("machines", machines);
                records.put(r);
            }
            try {
                if (!machinesJson.trim().isEmpty()) {
                    root.put("machines", new JSONArray(machinesJson));
                }
            } catch (Exception ignored) {
            }
            root.put("updatedAt", updatedAt);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("records", records);
            return root.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 最新备份清单：文件名 + 时间。 */
    private String buildLatestManifest(String dbName) {
        try {
            JSONObject o = new JSONObject();
            o.put("db", dbName);
            o.put("updatedAt", System.currentTimeMillis());
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void post(final String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onMessage(msg);
        });
    }

    private boolean copyFile(File src, File dst) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

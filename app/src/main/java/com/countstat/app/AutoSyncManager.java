package com.countstat.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自动同步调度器。
 *
 * 行为：
 * - 打开时拉取（pullOnOpen）：下载远程最新 db，覆盖本地后重载数据库
 * - 修改后延时上传（scheduleUpload）：每次保存记录后重置计时器，
 *   到点（默认 3 分钟）把本地 db 上传到 WebDAV
 *
 * 所有网络操作在后台线程执行，结果回调到主线程。
 */
public class AutoSyncManager {

    public static final long DEFAULT_DELAY_MS = 3 * 60 * 1000L; // 3 分钟

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

    private WebDavClient buildClient() {
        return new WebDavClient(url, user, password, basePath);
    }

    /** 打开时拉取远程最新数据库。 */
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
        post(result != null ? "已备份到 WebDAV：" + name : "WebDAV 备份失败");
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

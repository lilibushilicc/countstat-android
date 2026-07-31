package com.countstat.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * WebDAV 客户端，基于 HttpURLConnection，无第三方依赖。
 * 支持上传(PUT)、下载(GET)、测试连接(PROPFIND)和列目录。
 */
public class WebDavClient {

    private String url;
    private String user;
    private String password;
    private String basePath;

    public WebDavClient(String url, String user, String password, String basePath) {
        setUrl(url);
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        setBasePath(basePath);
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url.trim();
        while (this.url.endsWith("/")) this.url = this.url.substring(0, this.url.length() - 1);
    }

    public void setBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            this.basePath = "";
        } else {
            String p = basePath.trim();
            if (!p.startsWith("/")) p = "/" + p;
            while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
            this.basePath = p;
        }
    }

    public boolean isConfigured() {
        return !url.isEmpty();
    }

    /** 远程资源的完整 URL。 */
    private String remoteUrl(String remoteName) {
        String base = url + basePath + "/";
        return base + remoteName;
    }

    private void applyAuth(HttpURLConnection conn) {
        if (user.isEmpty() && password.isEmpty()) return;
        String auth = user + ":" + password;
        String encoded;
        try {
            encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            encoded = android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
        }
        conn.setRequestProperty("Authorization", "Basic " + encoded);
    }

    private HttpURLConnection open(String fullUrl, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
        if (conn instanceof HttpsURLConnection) {
            // 允许自签名证书的简易信任；原型阶段不强制校验
            HttpsURLConnection https = (HttpsURLConnection) conn;
            try {
                javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                ctx.init(null, new javax.net.ssl.TrustManager[]{trustAll()}, new java.security.SecureRandom());
                https.setSSLSocketFactory(ctx.getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            } catch (Exception ignored) {
            }
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        applyAuth(conn);
        return conn;
    }

    private javax.net.ssl.TrustManager trustAll() {
        return new javax.net.ssl.X509TrustManager() {
            @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
            @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        };
    }

    /** 测试连接：尝试 PROPFIND 根目录。 */
    public boolean testConnection() {
        if (!isConfigured()) return false;
        HttpURLConnection conn = null;
        try {
            conn = open(url + basePath + "/", "PROPFIND");
            conn.setRequestProperty("Depth", "0");
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 确保备份目录存在（MKCOL，失败忽略）。 */
    public void ensureDir() {
        if (basePath.isEmpty()) return;
        String[] parts = basePath.substring(1).split("/");
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            cur.append("/").append(part);
            HttpURLConnection conn = null;
            try {
                conn = open(url + cur + "/", "MKCOL");
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    /** 上传本地文件到远程，文件名带时间戳。返回远程文件名，失败返回 null。 */
    public String upload(File localFile, String remoteName) {
        if (!isConfigured()) return null;
        ensureDir();
        HttpURLConnection conn = null;
        try (FileInputStream in = new FileInputStream(localFile)) {
            conn = open(remoteUrl(remoteName), "PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            try (OutputStream out = conn.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            int code = conn.getResponseCode();
            return (code >= 200 && code < 300) ? remoteName : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 下载远程文件到本地。成功返回 true。 */
    public boolean download(String remoteName, File localDest) {
        if (!isConfigured()) return false;
        HttpURLConnection conn = null;
        try {
            conn = open(remoteUrl(remoteName), "GET");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return false;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(localDest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 列出备份目录下的 .db 文件名（带时间戳）。 */
    public List<String> listBackups() {
        List<String> result = new ArrayList<>();
        if (!isConfigured()) return result;
        HttpURLConnection conn = null;
        try {
            conn = open(url + basePath + "/", "PROPFIND");
            conn.setRequestProperty("Depth", "1");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return result;
            String body = readAll(conn.getInputStream());
            Pattern p = Pattern.compile("<D?:?href>([^<]*\\.db)</D?:?href>", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(body);
            while (m.find()) {
                String href = m.group(1);
                int slash = href.lastIndexOf('/');
                String name = slash >= 0 ? href.substring(slash + 1) : href;
                try {
                    name = java.net.URLDecoder.decode(name, StandardCharsets.UTF_8.name());
                } catch (Exception ignored) {
                }
                if (name.endsWith(".db")) result.add(name);
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    /** 生成带时间戳的备份文件名。 */
    public static String backupName() {
        return "countstat_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.CHINA)
                .format(new java.util.Date()) + ".db";
    }
}

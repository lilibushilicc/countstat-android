package com.countstat.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * WebDAV 客户端，基于原生 Socket 手写 HTTP/1.1，无第三方依赖。
 * 不经过 HttpURLConnection，不受 Android 方法白名单限制，
 * 支持 PROPFIND / MKCOL / PUT / GET。
 */
public class WebDavClient {

    /** 是否信任任意 TLS 证书。原型阶段默认 true；生产部署应改为 false。 */
    public static final boolean TRUST_ALL_TLS = true;

    private String url;
    private String user;
    private String password;
    private String basePath;
    private String lastError = "";

    public String getLastError() {
        return lastError;
    }

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

    /** 对路径做百分号编码（保留 / 和 :）。 */
    private String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        return android.net.Uri.encode(path, "/:");
    }

    /** 远程资源的完整 URL。 */
    private String remoteUrl(String remoteName) {
        String base = url + encodePath(basePath) + "/";
        return base + remoteName;
    }

    /** 一次 HTTP 响应。 */
    private static class Response {
        int code;
        byte[] bytes = new byte[0];
        Map<String, String> headers = new HashMap<>();

        String body() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** 发送原始 HTTP 请求（每请求一个连接，Connection: close）。 */
    private Response request(String fullUrl, String method, String depth,
                             byte[] body, String contentType) throws Exception {
        URL u = new URL(fullUrl);
        boolean https = "https".equalsIgnoreCase(u.getProtocol());
        int defaultPort = https ? 443 : 80;
        int port = u.getPort() > 0 ? u.getPort() : defaultPort;
        String host = u.getHost();
        String path = u.getPath();
        if (u.getQuery() != null) path += "?" + u.getQuery();
        if (path.isEmpty()) path = "/";

        Socket socket = null;
        try {
            if (https) {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{trustAll()}, new SecureRandom());
                SSLSocketFactory factory = ctx.getSocketFactory();
                SSLSocket ssl = (SSLSocket) factory.createSocket();
                ssl.connect(new InetSocketAddress(host, port), 15000);
                ssl.setSoTimeout(30000);
                ssl.startHandshake();
                socket = ssl;
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 15000);
                socket.setSoTimeout(30000);
            }

            StringBuilder head = new StringBuilder();
            head.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            head.append("Host: ").append(host);
            if (port != defaultPort) head.append(":").append(port);
            head.append("\r\n");
            if (!user.isEmpty() && !password.isEmpty()) {
                String auth = user + ":" + password;
                String encoded = android.util.Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP);
                head.append("Authorization: Basic ").append(encoded).append("\r\n");
            }
            if (depth != null) head.append("Depth: ").append(depth).append("\r\n");
            if (contentType != null) head.append("Content-Type: ").append(contentType).append("\r\n");
            head.append("User-Agent: CountStat/1.0\r\n");
            head.append("Connection: close\r\n");
            head.append("Content-Length: ").append(body == null ? 0 : body.length).append("\r\n");
            head.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) out.write(body);
            out.flush();

            return parse(socket.getInputStream());
        } finally {
            if (socket != null) socket.close();
        }
    }

    private Response parse(InputStream in) throws Exception {
        Response r = new Response();
        String statusLine = readLine(in);
        if (statusLine == null) throw new Exception("服务器无响应");
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) throw new Exception("响应状态行异常: " + statusLine);
        try {
            r.code = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new Exception("响应状态行异常: " + statusLine);
        }
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                r.headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                        line.substring(idx + 1).trim());
            }
        }
        if (r.code == 204 || r.code == 304) return r;
        String te = r.headers.get("transfer-encoding");
        if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
            r.bytes = readChunked(in);
        } else {
            String cl = r.headers.get("content-length");
            if (cl != null) {
                r.bytes = readN(in, Integer.parseInt(cl.trim()));
            } else {
                r.bytes = readN(in, -1);
            }
        }
        return r;
    }

    private String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            bos.write(c);
        }
        if (bos.size() == 0 && c == -1) return null;
        String s = bos.toString(StandardCharsets.ISO_8859_1.name());
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private byte[] readChunked(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) break;
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
            int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (size == 0) break;
            bos.write(readN(in, size));
            in.read();
            in.read();
        }
        return bos.toByteArray();
    }

    private byte[] readN(InputStream in, int n) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int len;
        while ((len = in.read(buf)) != -1) {
            bos.write(buf, 0, len);
            total += len;
            if (n >= 0 && total >= n) break;
        }
        return bos.toByteArray();
    }

    private X509TrustManager trustAll() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    /** 测试连接：先 PROPFIND 根目录验证凭据，若备份目录不存在则创建后重测。 */
    public boolean testConnection() {
        lastError = "";
        if (!isConfigured()) {
            lastError = "地址为空";
            return false;
        }
        try {
            Response r = request(url + "/", "PROPFIND", "0", null, null);
            if (r.code < 200 || r.code >= 400) {
                lastError = "根目录 HTTP " + r.code + (r.code == 401 ? "（账号或密码错误）" : "");
                return false;
            }
            String target = url + encodePath(basePath) + "/";
            r = request(target, "PROPFIND", "0", null, null);
            if (r.code == 404) {
                ensureDir();
                r = request(target, "PROPFIND", "0", null, null);
            }
            if (r.code >= 200 && r.code < 400) return true;
            lastError = "备份目录 HTTP " + r.code;
            return false;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    /** 确保备份目录存在（MKCOL，失败忽略）。 */
    public void ensureDir() {
        if (basePath.isEmpty()) return;
        String[] parts = basePath.substring(1).split("/");
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            cur.append("/").append(part);
            try {
                request(url + encodePath(cur.toString()) + "/", "MKCOL", null, null, null);
            } catch (Exception ignored) {
            }
        }
    }

    /** 上传本地文件到远程，文件名带时间戳。返回远程文件名，失败返回 null。 */
    public String upload(File localFile, String remoteName) {
        if (!isConfigured()) return null;
        ensureDir();
        try {
            byte[] data = readFile(localFile);
            Response r = request(remoteUrl(remoteName), "PUT", null, data, "application/octet-stream");
            return (r.code >= 200 && r.code < 300) ? remoteName : null;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return null;
        }
    }

    /** 下载远程文件到本地。成功返回 true。 */
    public boolean download(String remoteName, File localDest) {
        if (!isConfigured()) return false;
        try {
            Response r = request(remoteUrl(remoteName), "GET", null, null, null);
            if (r.code < 200 || r.code >= 300) return false;
            try (FileOutputStream out = new FileOutputStream(localDest)) {
                out.write(r.bytes);
            }
            return true;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    /** 上传一段文本内容（JSON 导出、最新备份清单等）。成功返回 true。 */
    public boolean uploadText(String content, String remoteName) {
        if (!isConfigured()) return false;
        ensureDir();
        try {
            Response r = request(remoteUrl(remoteName), "PUT", null,
                    content.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
            return r.code >= 200 && r.code < 300;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return false;
        }
    }

    /** 列出备份目录下的 .db 文件名（带时间戳）。 */
    public List<String> listBackups() {
        List<String> result = new ArrayList<>();
        if (!isConfigured()) return result;
        try {
            Response r = request(url + encodePath(basePath) + "/", "PROPFIND", "1", null, null);
            if (r.code < 200 || r.code >= 400) return result;
            String body = r.body();
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
        }
        return result;
    }

    private byte[] readFile(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** 生成带时间戳的备份文件名。 */
    public static String backupName() {
        return "countstat_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.CHINA)
                .format(new java.util.Date()) + ".db";
    }
}

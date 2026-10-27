package com.muge.cumtsilent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * CUMT（中国矿业大学）ePortal 登录客户端（Dr.COM 风格）。
 * 该门户是内网私有地址，只有连上校园 Wi-Fi 时才可达；认证靠 DDDDD(账号+后缀)+upass(密码)。
 * 直接冷访问登录 URL 会拿到门户的 HTML 登录页；真正的 JSONP 结果需要像门户页面里的 AJAX 那样
 * 带上会话 cookie + Referer + X-Requested-With 才会返回，因此先访问首页拿 cookie 再发登录请求。
 */
public class PortalClient {

    public enum Status {
        SUCCESS,
        ALREADY_ONLINE,
        BAD_CREDENTIALS,
        NOT_CONNECTED,   // 门户不可达：多半是没连上校园 Wi-Fi
        TIMEOUT,
        SERVER_ERROR,
        UNKNOWN
    }

    public static class Result {
        public final Status status;
        public final String message;
        public final String rawBody;
        public final int httpCode;
        public final String contentType;

        public Result(Status status, String message, String rawBody) {
            this(status, message, rawBody, -1, null);
        }

        public Result(Status status, String message, String rawBody, int httpCode, String contentType) {
            this.status = status;
            this.message = message;
            this.rawBody = rawBody;
            this.httpCode = httpCode;
            this.contentType = contentType;
        }
    }

    private static final String EPORTAL_LOGIN_URL_BASE = "http://10.2.5.251:801/eportal/";

    // 运营商 -> 账号后缀；登录地址不变，只是账号尾部拼不同后缀
    public static final Map<String, String> ISP_SUFFIX = new LinkedHashMap<>();
    static {
        ISP_SUFFIX.put("校园网", "");
        ISP_SUFFIX.put("中国移动", "@cmcc");
        ISP_SUFFIX.put("中国联通", "@unicom");
        ISP_SUFFIX.put("中国电信", "@telecom");
        // 让 HttpURLConnection 自动保存/回传门户的会话 cookie
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Mobile Safari/537.36";

    /** 一次 HTTP GET 的结果。 */
    private static final class Resp {
        int code;
        String contentType;
        String location;
        String body;
    }

    /** 登录并判定结果：先拿会话 cookie，再以 AJAX 方式请求登录接口，解析 JSONP 返回码。 */
    public Result login(String username, String password, String isp) {
        String suffix = ISP_SUFFIX.containsKey(isp) ? ISP_SUFFIX.get(isp) : "";
        long ts = System.currentTimeMillis();
        String callback = "dr" + ts + (100 + new Random().nextInt(900));

        String account = username.trim() + suffix;
        String url = EPORTAL_LOGIN_URL_BASE
                + "?c=ACSetting"
                + "&a=Login"
                + "&DDDDD=" + enc(account)
                + "&upass=" + enc(password)
                + "&callback=" + enc(callback)
                + "&login_method=1"
                + "&wlan_user_ip=" + enc(localIp())
                + "&wlan_user_mac=" + enc(mac())
                + "&wlan_ac_ip="
                + "&wlan_ac_name="
                + "&jsVersion=3.0"
                + "&_=" + ts;

        Resp resp;
        try {
            // 先访问门户首页，拿到会话 cookie（多数 ePortal 的 AJAX 登录需要 cookie + Referer 才回 JSONP）
            try {
                fetch(EPORTAL_LOGIN_URL_BASE, false);
            } catch (Exception ignore) {
                // 首页访问失败不影响继续尝试登录
            }
            resp = fetch(url, true);
        } catch (SocketTimeoutException e) {
            return new Result(Status.TIMEOUT, "登录请求超时。", null);
        } catch (Exception e) {
            // 私有门户不可达 -> 通常是手机没有连上校园 Wi-Fi（或流量关闭）
            return new Result(Status.NOT_CONNECTED, "无法连接校园网门户（10.2.5.251），请确认已连接到校园 Wi-Fi。", null);
        }

        // 只信门户返回：result:1=登录成功；result:0&ret_code:2=已在线；result:0&其它=认证未通过。
        return interpret(resp);
    }

    private Resp fetch(String url, boolean ajax) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(false);
        if (ajax) {
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Referer", EPORTAL_LOGIN_URL_BASE);
        }
        Resp r = new Resp();
        r.code = conn.getResponseCode();
        r.contentType = conn.getContentType();
        r.location = conn.getHeaderField("Location");
        InputStream in = r.code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        r.body = readAll(in);
        conn.disconnect();
        return r;
    }

    /** 判定登录结果：主要看 302 跳转目标页（3.htm=成功，2.htm?RetCode=2=已在线），并兼容直接返回 JSONP 的部署。 */
    private Result interpret(Resp r) {
        String body = r.body == null ? "" : r.body;

        // 门户把结果编码在 302 跳转的目标页里（实测）：
        //   3.htm            -> 登录成功（新登录）
        //   2.htm?RetCode=2  -> 终端 IP 已在线（无需重复登录）
        //   2.htm 其它码/1.htm -> 认证未通过，ErrorMsg 带原因
        // 页面上看到的 {"result":...} 文本是 JS 现渲染的，原始响应体里没有，所以只能读跳转目标。
        if (r.code >= 300 && r.code < 400 && r.location != null) {
            String loc = r.location;
            String ret = queryParam(loc, "RetCode");
            String err = queryParam(loc, "ErrorMsg");
            boolean hasErr = err != null && !err.isEmpty();
            String page = resultPage(loc);
            String tag = " [HTTP " + r.code + " → " + (page.isEmpty() ? "?" : page)
                    + (ret != null ? (" RetCode=" + ret) : "") + "]";
            if ("2.htm".equals(page) && "2".equals(ret)) {
                return new Result(Status.ALREADY_ONLINE, "终端 IP 已在线，无需重复登录。" + tag, body, r.code, r.contentType);
            }
            if ("3.htm".equals(page) || "1".equals(ret)) {
                return new Result(Status.SUCCESS, (hasErr ? err : "登录成功。") + tag, body, r.code, r.contentType);
            }
            String m = hasErr ? err : ("登录未成功（" + (page.isEmpty() ? "未知结果页" : page)
                    + (ret != null ? (" RetCode=" + ret) : "") + "）。");
            return new Result(Status.BAD_CREDENTIALS, m + tag, body, r.code, r.contentType);
        }

        // 少数部署会直接返回 JSONP 正文：尽力从中抽出 {"result":...} 对象。
        String json = findResultJson(body);
        if (json != null) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(json);
                String result = obj.optString("result", "").trim();
                String retCode = obj.optString("ret_code", "").trim();
                String msg = obj.optString("msg", "").trim();
                if ("1".equals(result)) {
                    return new Result(Status.SUCCESS, msg.isEmpty() ? "登录成功。" : msg, body, r.code, r.contentType);
                }
                if ("0".equals(result)) {
                    if ("2".equals(retCode)) {
                        return new Result(Status.ALREADY_ONLINE, "终端 IP 已在线，无需重复登录。", body, r.code, r.contentType);
                    }
                    return new Result(Status.BAD_CREDENTIALS,
                            msg.isEmpty() ? ("认证未通过（ret_code=" + retCode + "）。") : msg, body, r.code, r.contentType);
                }
                if ("ok".equalsIgnoreCase(result) || "success".equalsIgnoreCase(result)) {
                    return new Result(Status.SUCCESS, "登录成功。", body, r.code, r.contentType);
                }
            } catch (Exception ignore) {
                // 抽出的片段不是合法 JSON：继续走“未识别”分支
            }
        }

        // 既不是可识别跳转、也不是 JSONP：多半是登录页(HTML)，说明这次没被当作 AJAX 登录。
        boolean html = body.trim().startsWith("<") || (r.contentType != null && r.contentType.toLowerCase().contains("html"));
        String msg = (html ? "门户返回的是登录页(未取到结果)。" : "无法识别门户返回。")
                + " HTTP " + r.code + " CT=" + r.contentType
                + (r.location != null ? " LOC=" + r.location : "");
        return new Result(Status.UNKNOWN, msg, body, r.code, r.contentType);
    }

    /** 从跳转 URL 里取出结果页文件名（如 3.htm / 2.htm / 1.htm）。 */
    private static String resultPage(String loc) {
        int q = loc.indexOf('?');
        String path = q >= 0 ? loc.substring(0, q) : loc;
        int s = path.lastIndexOf('/');
        return s >= 0 ? path.substring(s + 1) : path;
    }

    /** 从 URL 查询串里取某个参数值（大小写不敏感，URL 解码）。 */
    private static String queryParam(String url, String key) {
        int q = url.indexOf('?');
        String qs = q >= 0 ? url.substring(q + 1) : url;
        for (String kv : qs.split("&")) {
            int e = kv.indexOf('=');
            String k = e < 0 ? kv : kv.substring(0, e);
            if (k.equalsIgnoreCase(key)) {
                String v = e < 0 ? "" : kv.substring(e + 1);
                try {
                    return java.net.URLDecoder.decode(v, "UTF-8");
                } catch (Exception ex) {
                    return v;
                }
            }
        }
        return null;
    }

    /** 在任意文本(JSONP 或 HTML 页面)里定位包含 "result" 的最外层 {...} 对象；找不到返回 null。 */
    private static String findResultJson(String body) {
        int key = body.indexOf("\"result\"");
        if (key < 0) {
            return null;
        }
        int start = body.lastIndexOf('{', key);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inStr = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** 取本机 site-local IPv4，优先 Wi‑Fi(wlan*) 接口；失败退回 10.0.0.1。 */
    static String localIp() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                boolean wifi = ni.getName() != null && ni.getName().startsWith("wlan");
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        if (wifi) return addr.getHostAddress();
                        if (fallback == null) fallback = addr.getHostAddress();
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Exception ignore) {
        }
        return "10.0.0.1";
    }

    static String mac() {
        return "000000000000"; // Android 无法读取真实 MAC，门户认证不依赖此字段
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

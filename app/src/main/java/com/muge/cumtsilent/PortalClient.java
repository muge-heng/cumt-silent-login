package com.muge.cumtsilent;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * CUMT（中国矿业大学）ePortal 登录客户端（Dr.COM 风格 JSONP）。
 * 该门户是内网私有地址，只有连上校园 Wi-Fi 时才可达；认证靠 DDDDD(账号+后缀)+upass(密码)，
 * wlan_user_ip / mac 仅为接入侧信息位，不参与校验。
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

        public Result(Status status, String message, String rawBody) {
            this.status = status;
            this.message = message;
            this.rawBody = rawBody;
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
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    /** 登录并判定结果：先看登录前是否已在线，再发 ePortal 请求解析返回码。 */
    public Result login(Context ctx, String username, String password, String isp) {
        // 登录前系统就把当前校园 Wi-Fi 判为“可上网”，说明这个终端 IP 早已在线——与门户返回码无关。
        boolean onlineBefore = isCampusOnline(ctx);

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

        String body = null;
        int httpCode = -1;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            httpCode = conn.getResponseCode();
            InputStream in = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            body = readAll(in);
            conn.disconnect();
        } catch (SocketTimeoutException e) {
            return new Result(Status.TIMEOUT, "登录请求超时。", null);
        } catch (Exception e) {
            // 私有门户不可达 -> 通常是手机没有连上校园 Wi-Fi（或流量关闭）
            return new Result(Status.NOT_CONNECTED, "无法连接校园网门户（10.2.5.251），请确认已连接到校园 Wi-Fi。", null);
        }

        // 登录前就已在线 -> 直接判为“已在线”，不再看返回码。
        if (onlineBefore) {
            return new Result(Status.ALREADY_ONLINE, "终端 IP 已在线，无需重复登录。", body);
        }

        Result parsed = interpret(httpCode, body);
        // 未在线时以门户返回为准：只有明确 result:1 才算“登录成功”，ret_code:2 算“已在线”。
        if (parsed.status == Status.SUCCESS || parsed.status == Status.ALREADY_ONLINE) {
            return parsed;
        }
        // 返回无法确认，但此刻外网已连通：说明这次登录生效了 -> 登录成功。
        if (httpCode == 200 && isOnline()) {
            return new Result(Status.SUCCESS, "登录请求已生效，外网已连通。", body);
        }
        return parsed;
    }

    /** 当前活动网络是否为“已放行的校园 Wi-Fi”（系统已判定可上网 = 之前就已在线）。 */
    static boolean isCampusOnline(Context ctx) {
        try {
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            if (cm == null) return false;
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) return false;
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 CUMT ePortal 的 JSONP 返回（result 为字符串码）：
     *   {"result":"1","msg":"认证成功"}           -> 登录成功
     *   {"result":"0","msg":"","ret_code":"2"}    -> 终端 IP 已在线（已登录）
     *   {"result":"0", ...ret_code != 2...}       -> 认证未通过（账号/密码等），尽量带上 msg
     */
    private Result interpret(int httpCode, String body) {
        if (httpCode >= 500) {
            return new Result(Status.SERVER_ERROR, "门户服务器返回 " + httpCode + "。", body);
        }
        if (body == null || body.trim().isEmpty()) {
            return new Result(Status.UNKNOWN, "门户返回了空响应。", body);
        }
        String json = stripJsonp(body);
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String result = obj.optString("result", "").trim();
            String retCode = obj.optString("ret_code", "").trim();
            String msg = obj.optString("msg", "").trim();

            if ("1".equals(result)) {
                return new Result(Status.SUCCESS, msg.isEmpty() ? "登录成功。" : msg, body);
            }
            if ("0".equals(result)) {
                if ("2".equals(retCode)) {
                    return new Result(Status.ALREADY_ONLINE, "终端 IP 已在线，无需重复登录。", body);
                }
                return new Result(Status.BAD_CREDENTIALS,
                        msg.isEmpty() ? ("认证未通过（ret_code=" + retCode + "）。") : msg, body);
            }
            // 兼容其它门户写法
            if ("ok".equalsIgnoreCase(result) || "success".equalsIgnoreCase(result)) {
                return new Result(Status.SUCCESS, "登录成功。", body);
            }
        } catch (Exception ignore) {
            // 非 JSON 正文：落到外层外网探测兜底
        }
        return new Result(Status.UNKNOWN, "无法识别门户返回。", body);
    }

    private static String stripJsonp(String body) {
        int open = body.indexOf('(');
        int close = body.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return body.substring(open + 1, close);
        }
        return body;
    }

    /** 探一次真实外网连通（HEAD baidu / gstatic 204），作为返回含义不明确时的兜底判据。 */
    private boolean isOnline() {
        String[] probes = {
                "https://www.baidu.com",
                "https://connectivitycheck.gstatic.com/generate_204",
        };
        for (String p : probes) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(p).openConnection();
                c.setRequestMethod("HEAD");
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setConnectTimeout(3000);
                c.setReadTimeout(3000);
                c.setInstanceFollowRedirects(false);
                int code = c.getResponseCode();
                c.disconnect();
                if (code >= 200 && code < 400) {
                    return true;
                }
            } catch (Exception e) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(p).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    c.setRequestProperty("User-Agent", USER_AGENT);
                    c.getResponseCode();
                    c.disconnect();
                    return true;
                } catch (Exception ignore) {
                    // 继续下一个探针
                }
            }
        }
        return false;
    }

    /** 取本机 site-local IPv4；失败退回 10.0.0.1。 */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
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

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

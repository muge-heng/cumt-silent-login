package com.muge.cumtsilent;

import android.content.Context;
import android.content.SharedPreferences;

/** 账号/密码/运营商只存一处；首次填好后，后续启动即无感登录。用户要改就清应用缓存。 */
public final class Prefs {

    private static final String FILE = "cumt_login_prefs";
    public static final String KEY_CONFIGURED = "configured";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_ISP = "isp";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured(Context c) {
        SharedPreferences s = sp(c);
        return s.getBoolean(KEY_CONFIGURED, false)
                && !s.getString(KEY_USERNAME, "").trim().isEmpty()
                && !s.getString(KEY_PASSWORD, "").isEmpty();
    }

    public static String username(Context c) { return sp(c).getString(KEY_USERNAME, ""); }
    public static String password(Context c) { return sp(c).getString(KEY_PASSWORD, ""); }
    public static String isp(Context c) {
        String v = sp(c).getString(KEY_ISP, "");
        return PortalClient.ISP_SUFFIX.containsKey(v) ? v : "校园网";
    }

    public static void save(Context c, String username, String password, String isp) {
        sp(c).edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_ISP, isp)
                .putBoolean(KEY_CONFIGURED, true)
                .apply();
    }
}

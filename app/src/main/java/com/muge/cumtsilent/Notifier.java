package com.muge.cumtsilent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/** 登录结果只弹一则安静、明确的中文通知。 */
public final class Notifier {

    private static final String CHANNEL_ID = "cumt_login_result";
    private static final int NOTIF_ID = 1001;

    private Notifier() {}

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "校园网登录结果", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("校园网自动登录完成后的一次性提示");
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    public static void showResult(Context c, PortalClient.Result r) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        String title;
        String body;
        switch (r.status) {
            case SUCCESS:
                title = "校园网已登录";
                body = "登录成功，网络已连通。";
                break;
            case ALREADY_ONLINE:
                title = "校园网已在线";
                body = "之前就已经在线，无需重复登录。";
                break;
            case BAD_CREDENTIALS:
                title = "登录失败";
                body = r.message == null ? "账号或密码不正确。" : r.message;
                break;
            case NOT_CONNECTED:
                title = "未连到校园网";
                body = "请先连上校园 Wi-Fi，再打开本应用。";
                break;
            case TIMEOUT:
                title = "登录超时";
                body = "门户没有及时响应，稍后再试。";
                break;
            case SERVER_ERROR:
                title = "门户异常";
                body = r.message == null ? "门户服务器返回错误。" : r.message;
                break;
            default:
                title = "登录结果未知";
                body = r.message == null ? "已发送登录请求，但无法确认结果。" : r.message;
        }

        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_wifi)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        nm.notify(NOTIF_ID, n);
    }
}

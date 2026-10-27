package com.muge.cumtsilent;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

/**
 * 无感登录执行体：在后台线程跑一次门户登录，只弹一则结果通知，然后自行结束（应用退出）。
 * IntentService 自带工作线程且处理完即 stopSelf，天然契合“发一次请求就退”的需求。
 */
public class LoginService extends IntentService {

    public LoginService() {
        super("LoginService");
        // 处理完请求后不保留：进程随后回收，对用户不可见。
        setIntentRedelivery(false);
    }

    public static void start(Context c) {
        c.startService(new Intent(c, LoginService.class));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String username = Prefs.username(this);
        String password = Prefs.password(this);
        String isp = Prefs.isp(this);

        PortalClient client = new PortalClient();
        PortalClient.Result result = client.login(username, password, isp);
        Notifier.showResult(this, result);
    }
}

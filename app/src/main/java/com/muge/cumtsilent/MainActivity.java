package com.muge.cumtsilent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 首次打开（未配置）显示一个简洁的设置页；填过一次账号密码并登录后，
 * 之后每次打开都直接起后台服务、立即 finish，整个过程没有任何前台页面。
 */
public class MainActivity extends Activity {

    private EditText usernameEdit;
    private EditText passwordEdit;
    private Spinner ispSpinner;
    private Button loginButton;
    private TextView statusText;
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Notifier.ensureChannel(this);

        if (Prefs.isConfigured(this)) {
            // 已配置：无感登录，不显示任何页面。
            LoginService.start(this);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        usernameEdit = findViewById(R.id.username);
        passwordEdit = findViewById(R.id.password);
        ispSpinner = findViewById(R.id.isp);
        loginButton = findViewById(R.id.login_button);
        statusText = findViewById(R.id.status);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"校园网", "中国移动", "中国联通", "中国电信"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ispSpinner.setAdapter(adapter);

        maybeRequestNotificationPermission();

        loginButton.setOnClickListener(v -> doFirstLogin());
    }

    private void doFirstLogin() {
        if (running) return;

        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        String isp = ispSpinner.getSelectedItem().toString();

        if (username.isEmpty() || password.isEmpty()) {
            statusText.setText("请填写账号和密码。");
            return;
        }

        // 记住配置，之后即无感
        Prefs.save(this, username, password, isp);

        running = true;
        loginButton.setEnabled(false);
        statusText.setText("正在登录校园网…");

        new Thread(() -> {
            PortalClient.Result r = new PortalClient().login(MainActivity.this, username, password, isp);
            Notifier.showResult(this, r);
            runOnUiThread(() -> {
                running = false;
                loginButton.setEnabled(true);
                boolean ok = r.status == PortalClient.Status.SUCCESS
                        || r.status == PortalClient.Status.ALREADY_ONLINE;
                statusText.setText(describe(r));
                if (!ok) {
                    Toast.makeText(this, "登录未成功，可重试或清除应用数据修改账号。", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String describe(PortalClient.Result r) {
        switch (r.status) {
            case SUCCESS: return "登录成功，校园网已连通。";
            case ALREADY_ONLINE: return "已在线，校园网正常工作。";
            case BAD_CREDENTIALS: return "登录失败：" + r.message;
            case NOT_CONNECTED: return "未连到校园网：" + r.message;
            case TIMEOUT: return "登录超时，请稍后重试。";
            case SERVER_ERROR: return "门户异常：" + r.message;
            default: return "登录结果未知：" + (r.message == null ? "已发送请求。" : r.message);
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 通知权限被拒也不影响无感登录本身；结果仅改为在设置页内可见。
    }
}

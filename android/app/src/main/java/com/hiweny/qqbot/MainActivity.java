package com.hiweny.qqbot;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * QQBot AI —— 纯 WebView 外壳。
 * 加载线上页面 https://hiweny.github.io/QQbot/ ，不修改网页本身逻辑。
 * 特性：edge-to-edge 全屏沉浸、深浅色跟随系统、前台常驻通知联动运行状态、后台保活。
 */
public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "https://hiweny.github.io/QQbot/";
    private static final String HOME_HOST = "hiweny.github.io";
    private static final int REQ_NOTIF = 1001;

    private WebView webView;
    private ProgressBar progress;
    private boolean injected = false;
    private boolean errorShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(savedInstanceState);

        // 全屏沉浸：内容绘制到系统栏后面
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.webview);

        setupWebView();
        applyInsets(root);
        applySystemBarIcons();

        startKeepAliveService();
        requestNotificationPermission();
        maybeRequestBatteryExemption();

        webView.loadUrl(START_URL);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        try { s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); } catch (Throwable ignored) {}

        // 网页自带双模式，禁止算法加深，避免二次变暗
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                s.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
        } catch (Throwable ignored) {}

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // 关键保活：即使不可见也保持渲染进程高优先级，避免后台被冻结/回收
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
            }
        } catch (Throwable ignored) {}

        webView.addJavascriptInterface(new StatusBridge(getApplicationContext()), "QQBotNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if (("http".equals(scheme) || "https".equals(scheme))
                        && HOME_HOST.equalsIgnoreCase(u.getHost())) {
                    return false; // 站内继续用 WebView
                }
                openExternally(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injected = false;
                errorShown = false;
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progress != null) progress.setVisibility(View.GONE);
                injectBridge();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    if (progress != null) progress.setVisibility(View.GONE);
                    showLoadError();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                      final JsPromptResult result) {
                final EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });
    }

    private void injectBridge() {
        if (injected) return;
        injected = true;
        String js = readAsset("inject.js");
        if (js != null && !js.isEmpty()) {
            webView.evaluateJavascript(js, null);
        }
    }

    private String readAsset(String name) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void applyInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            if (webView != null) {
                // 键盘弹起时把网页顶上去，其余方向保持铺满全屏（无缝）
                webView.setPadding(0, 0, 0, ime.bottom);
            }
            return insets;
        });
    }

    private boolean isSystemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBarIcons() {
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean dark = isSystemDark();
        c.setAppearanceLightStatusBars(!dark);
        c.setAppearanceLightNavigationBars(!dark);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBarIcons();
        boolean dark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.__QQBOT_APPLY_SCHEME__&&window.__QQBOT_APPLY_SCHEME__(" + dark + ");", null);
        }
    }

    private void startKeepAliveService() {
        try {
            ContextCompat.startForegroundService(this, new Intent(this, BotService.class));
        } catch (Throwable t) {
            try { startService(new Intent(this, BotService.class)); } catch (Throwable ignored) {}
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            } catch (Throwable ignored) {}
        }
    }

    private void maybeRequestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        SharedPreferences sp = getSharedPreferences("qqbot", MODE_PRIVATE);
        if (sp.getBoolean("battery_asked", false)) return;
        sp.edit().putBoolean("battery_asked", true).apply();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("后台保活设置")
                        .setMessage("为了让机器人在后台稳定在线、不被系统清理，建议关闭对本应用的电池优化限制。")
                        .setPositiveButton("去设置", (d, w) -> {
                            try {
                                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                i.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            } catch (Throwable ignored) {
                                openBatterySettingsFallback();
                            }
                        })
                        .setNegativeButton("以后", null)
                        .show();
            }
        } catch (Throwable ignored) {}
    }

    private void openBatterySettingsFallback() {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void openExternally(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoadError() {
        if (errorShown || isFinishing()) return;
        errorShown = true;
        new AlertDialog.Builder(this)
                .setTitle("加载失败")
                .setMessage("无法连接服务器，请检查网络后重试。")
                .setPositiveButton("重试", (d, w) -> webView.reload())
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // 不结束 Activity，转后台以保持网页与机器人持续运行
        moveTaskToBack(true);
    }
}

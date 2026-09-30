package com.hiweny.qqbot;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/**
 * 注入到网页的 JS 桥。仅做「读取状态 / 上报状态」，不修改网页逻辑。
 */
public class StatusBridge {

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());

    public StatusBridge(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** 当前系统是否是深色模式（供网页侧同步主题）。 */
    @JavascriptInterface
    public boolean isDark() {
        int mode = appContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 网页运行状态变化时回调，联动前台常驻通知。 */
    @JavascriptInterface
    public void onStatus(String json) {
        final String payload = json;
        main.post(() -> {
            try { BotService.pushStatus(appContext, payload); } catch (Throwable ignored) {}
        });
    }

    /** 网页发起的新回复通知（Notification API 桥接）。 */
    @JavascriptInterface
    public void notify(String title, String body) {
        final String t = title;
        final String b = body;
        main.post(() -> {
            try { BotService.postReply(appContext, t, b); } catch (Throwable ignored) {}
        });
    }
}

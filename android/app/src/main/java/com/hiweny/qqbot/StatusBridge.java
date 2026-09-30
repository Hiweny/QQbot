package com.hiweny.qqbot;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 注入到网页的 JS 桥。
 * 1) 读取/上报机器人运行状态（联动前台通知）
 * 2) 原生 HTTP 通道：网页的跨域请求改由 App 发起，彻底绕过浏览器 CORS 限制
 */
public class StatusBridge {

    private final Context appContext;
    private final WeakReference<WebView> webRef;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public StatusBridge(Context context, WebView webView) {
        this.appContext = context.getApplicationContext();
        this.webRef = new WeakReference<>(webView);
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

    /**
     * 原生 HTTP 请求。JS 传 {url, method, headers, bodyBase64}，异步完成后再回调网页。
     * 不限制跨域，网页请求什么就发什么，响应原样回传。
     */
    @JavascriptInterface
    public void httpRequest(final String id, final String payloadJson) {
        pool.execute(() -> {
            String result;
            try {
                JSONObject p = new JSONObject(payloadJson);
                String url = p.optString("url", "");
                String method = p.optString("method", "GET").toUpperCase(Locale.US);
                JSONObject headers = p.optJSONObject("headers");
                String bodyB64 = p.isNull("bodyBase64") ? null : p.optString("bodyBase64", null);
                result = perform(url, method, headers, bodyB64);
            } catch (Throwable t) {
                result = errorJson(t);
            }
            final String r = result;
            main.post(() -> {
                WebView w = webRef.get();
                if (w == null) return;
                try {
                    w.evaluateJavascript(
                            "window.__QQBOT_HTTP_DONE__&&window.__QQBOT_HTTP_DONE__("
                                    + JSONObject.quote(id) + "," + JSONObject.quote(r) + ");", null);
                } catch (Throwable ignored) {}
            });
        });
    }

    private static String perform(String url, String method, JSONObject headers, String bodyB64) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            String proto = u.getProtocol();
            if (!"http".equals(proto) && !"https".equals(proto)) return errorJson("scheme not allowed");

            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setInstanceFollowRedirects(true);
            conn.setUseCaches(false);

            if (headers != null) {
                for (Iterator<String> it = headers.keys(); it.hasNext(); ) {
                    String k = it.next();
                    String v = headers.optString(k, null);
                    if (k == null || v == null) continue;
                    try { conn.setRequestProperty(k, v); } catch (Throwable ignored) {}
                }
            }

            byte[] payload = null;
            if (bodyB64 != null && !bodyB64.isEmpty()) {
                payload = Base64.decode(bodyB64, Base64.DEFAULT);
            }
            if (payload != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int status = conn.getResponseCode();
            String msg = conn.getResponseMessage();

            JSONObject outHeaders = new JSONObject();
            boolean hasAcao = false;
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                String k = e.getKey();
                if (k == null) continue;
                String lk = k.toLowerCase(Locale.US);
                if ("access-control-allow-origin".equals(lk)) hasAcao = true;
                outHeaders.put(lk, TextUtils.join(", ", e.getValue()));
            }
            if (!hasAcao) outHeaders.put("access-control-allow-origin", "*");

            byte[] body = new byte[0];
            InputStream is = null;
            try { is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream(); } catch (Throwable ignored) {}
            if (is != null) body = readAll(is);

            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("status", status);
            out.put("statusText", sanitize(msg));
            out.put("headers", outHeaders);
            out.put("body", Base64.encodeToString(body, Base64.NO_WRAP));
            return out.toString();
        } catch (Throwable t) {
            return errorJson(t);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    private static byte[] readAll(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").trim();
    }

    private static String errorJson(Throwable t) {
        String m;
        if (t == null) m = "error";
        else if (t.getMessage() != null) m = t.getMessage();
        else m = t.getClass().getSimpleName();
        return errorJson(m);
    }

    private static String errorJson(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", message == null ? "error" : message);
            return o.toString();
        } catch (Throwable e) {
            return "{\"ok\":false,\"error\":\"error\"}";
        }
    }
}

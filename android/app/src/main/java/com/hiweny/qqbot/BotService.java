package com.hiweny.qqbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/**
 * 前台常驻服务：维持进程存活（保证 WebView 与网页里的机器人连接不被打断），
 * 并通过常驻通知实时展示 QQBot 的运行状态。
 */
public class BotService extends Service {

    public static final String CH_STATUS = "qqbot_status";
    public static final String CH_REPLY = "qqbot_reply";
    private static final int ID_STATUS = 1001;
    private static final int ID_REPLY = 1002;
    private static final String EXTRA_JSON = "json";

    private static volatile BotService instance;

    private PowerManager.WakeLock wakeLock;
    private String curLine = "未启动";

    // ---------- 供外部调用的静态入口 ----------

    public static void pushStatus(Context ctx, String json) {
        BotService s = instance;
        if (s != null) {
            s.apply(json);
        } else {
            try {
                Intent i = new Intent(ctx, BotService.class).putExtra(EXTRA_JSON, json);
                ContextCompat.startForegroundService(ctx, i);
            } catch (Throwable ignored) {}
        }
    }

    public static void postReply(Context ctx, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            ensureChannels(ctx, nm);
            Notification n = new NotificationCompat.Builder(ctx, CH_REPLY)
                    .setSmallIcon(R.drawable.ic_stat_bot)
                    .setColor(0xFF12B7F5)
                    .setContentTitle(title == null || title.isEmpty() ? "QQBot AI" : title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(ctx))
                    .build();
            try { NotificationManagerCompat.from(ctx).notify(ID_REPLY, n); } catch (SecurityException ignored) {}
        } catch (Throwable ignored) {}
    }

    // ---------- 生命周期 ----------

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) ensureChannels(this, nm);

        // 部分唤醒锁：屏幕关闭时也让 CPU/网络保持工作
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QQBot::KeepAlive");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable ignored) {}

        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, ID_STATUS, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(ID_STATUS, n);
            }
        } catch (Throwable t) {
            try { startForeground(ID_STATUS, n); } catch (Throwable ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_JSON)) {
            apply(intent.getStringExtra(EXTRA_JSON));
        } else {
            refresh();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 任务被划掉 → 承载网页的 Activity 已销毁，机器人无法继续，结束服务
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        instance = null;
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        wakeLock = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 状态解析与通知刷新 ----------

    private void apply(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String state = o.optString("state", "off");
            String text = o.optString("text", "");
            String bot = o.optString("bot", "");
            String count = o.optString("count", "0");
            if ("on".equals(state)) {
                String who = (bot == null || bot.isEmpty() || "-".equals(bot)) ? "" : ("：" + bot);
                curLine = "在线" + who + " · 已处理 " + (count.isEmpty() ? "0" : count) + " 条";
            } else if ("connecting".equals(state)) {
                curLine = "连接中…";
            } else {
                curLine = (text == null || text.isEmpty()) ? "已停止" : text;
            }
        } catch (Throwable ignored) {}
        refresh();
    }

    private void refresh() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID_STATUS, buildNotification());
        } catch (Throwable ignored) {}
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_bot)
                .setColor(0xFF12B7F5)
                .setContentTitle("QQBot AI")
                .setContentText(curLine)
                .setSubText("后台常驻运行中")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent(this))
                .build();
    }

    private static PendingIntent contentIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(ctx, 0, i, flags);
    }

    private static void ensureChannels(Context ctx, NotificationManager nm) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel status = new NotificationChannel(
                CH_STATUS, ctx.getString(R.string.notif_channel_status), NotificationManager.IMPORTANCE_LOW);
        status.setDescription(ctx.getString(R.string.notif_channel_status_desc));
        status.setShowBadge(false);
        nm.createNotificationChannel(status);

        NotificationChannel reply = new NotificationChannel(
                CH_REPLY, ctx.getString(R.string.notif_channel_reply), NotificationManager.IMPORTANCE_DEFAULT);
        reply.setDescription(ctx.getString(R.string.notif_channel_reply_desc));
        nm.createNotificationChannel(reply);
    }
}

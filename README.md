# QQBot AI 🤖

> 纯前端 · 零服务器 · 打开即在线的 QQ 智能 AI 机器人

**[🌐 在线使用](https://hiweny.github.io/QQbot/)** · **[📦 下载 Android APK](https://github.com/hiweny/QQbot/releases)**

一个单文件 HTML 实现的完整 QQ 机器人：浏览器直接连接 QQ 开放平台 WebSocket，无需服务器、无需 Node/Python 环境，配好 AppID 就能让 AI 机器人在你的 QQ 群/私聊/频道里在线。

![QQBot AI](https://hiweny.github.io/QQbot/app-icon.png)

---

## ✨ 核心特性

### 🤖 机器人核心
- **纯前端运行**：单 HTML 文件，浏览器打开即用，WebSocket 直连 QQ 开放平台
- **多场景回复**：QQ 群 @ 消息、C2C 私聊、频道消息全支持
- **多 AI 供应商**：内置免费公益站开箱即用，支持任意 OpenAI 兼容接口（DeepSeek / 智谱 / Kimi / OpenAI…），多槽位独立配置一键切换
- **Token 自动管理**：自动获取/续期 AccessToken，支持手动 Token 兜底，CORS 失败自动走公共代理

### 💬 多条消息（真人感）
- **AI 智能分条**：AI 用 `\` 分隔回复，像真人一样分条发送，独立开关
- **msg_seq 递增**：遵守官方"同一消息最多回复 5 次"限制，自动处理去重/过期/超长错误码
- **连发消息合并**：用户短时间连发多条自动合并为一次回复，不打断不漏回

### 🧠 长期记忆系统
- **检索式注入**：按「相关性 + 重要度 + 时近性」综合评分筛选记忆注入上下文
- **自动摘要归档**：超过滑窗自动 AI 摘要入库，历史消息完整保留不删除
- **去重合并**：相似记忆自动合并，记忆库不膨胀；"记住 XX"即时入库
- **记忆管理**：置顶 / 删除 / 清空 / 命中统计，全在会话详情页

### 🌐 联网搜索（Tavily）
- AI 自动判断是否需要搜索实时信息
- OpenAI 兼容供应商走**原生 Function Calling**；免费公益站走 **SEARCH 指令协议**
- 用户明确说"搜一下"强制触发

### 📱 Android 客户端
- WebView 高度适配封装，[点此查看 APK 发行说明](RELEASE.md)
- **前台服务保活**：后台持续运行，屏幕关闭不断线
- **状态联动通知**：常驻通知实时显示机器人在线状态与心跳
- **新消息推送**：收到的回复以原生通知推送到通知栏
- 毛玻璃自适应图标 + 开机自启 + 电池优化白名单引导

### 🎨 界面
- 毛玻璃设计系统（Glassmorphism）：卡片、悬浮胶囊导航、输入控件全套
- 动态背景：随机图 API 自动轮换 + 渐变光斑兜底
- 深色模式完整适配，`prefers-reduced-motion` 无障碍支持

---

## 🚀 快速开始

### 网页版
1. 打开 [https://hiweny.github.io/QQbot/](https://hiweny.github.io/QQbot/)
2. 在 [QQ 机器人开放平台](https://q.qq.com/) 创建机器人，拿到 AppID / AppSecret
3. 「设置」页填入凭据（新机器人记得开沙箱模式）
4. 回到「状态」页点 **🚀 启动机器人**
5. 在测试群 @ 机器人 或私聊，开聊！

### Android 版
到 [Releases 页面](https://github.com/hiweny/QQbot/releases) 下载最新 APK 安装，详见 [APK 发行说明](RELEASE.md)。

> 💡 建议搭配 [QQ 机器人调试工具](https://bot.q.qq.com/sandbox) 获取手动 Token。

---

## ⚙️ 配置指南

| 配置项 | 说明 |
|---|---|
| AppID / AppSecret | QQ 开放平台机器人凭据 |
| AI 供应商 | 免费公益站（默认）/ OpenAI 兼容接口（Base URL + Key + 模型名） |
| Tavily Key | 联网搜索，[tavily.com](https://tavily.com) 免费注册，每月 1000 次 |
| 系统提示词 | AI 人设 |
| 上下文窗口 | 送入模型的最近消息轮数（默认 15） |
| 分条/合并/搜索 | 各功能独立开关 |

所有配置保存在浏览器本地（localStorage + IndexedDB），不上传任何服务器。

---

## 🏗️ 技术架构

```
浏览器 / Android WebView
├── WebSocket ──► wss://api.sgroup.qq.com（QQ 开放平台网关）
├── HTTPS ──────► api.sgroup.qq.com（发消息，CORS 失败自动切公共代理）
├── AI 调用 ────► OpenAI 兼容接口 / 免费公益站（工具调用 / SEARCH 协议）
├── 联网搜索 ──► api.tavily.com（浏览器 CORS 直连）
└── 本地存储 ──► localStorage（配置）+ IndexedDB（会话/消息/记忆）
```

Android 壳：`MainActivity`（WebView + JS 桥）+ `BotService`（前台服务 + WakeLock + 状态通知）+ `BootReceiver`（开机自启）。

---

## 📂 项目结构

```
QQbot/
├── index.html          # 全部前端逻辑（单文件）
├── app-icon.png        # 应用图标源文件
├── qqbot.keystore      # APK 签名密钥库（升级版本必须使用！）
├── QQBot-AI-v1.0.0.apk # Android 客户端
├── README.md           # 本文件
└── RELEASE.md          # APK 发行说明
```

---

## 📄 许可与声明

- 本项目仅供学习交流，请遵守 [QQ 机器人开放平台](https://q.qq.com/) 相关规范
- 免费公益站与随机图 API 来自第三方公益服务，稳定性不作保证
- AI 回复内容由模型生成，请注意甄别

---

Made with 💙 by [hiweny](https://github.com/hiweny) · [在线体验](https://hiweny.github.io/QQbot/) · [问题反馈](https://github.com/hiweny/QQbot/issues)

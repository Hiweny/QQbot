# QQBot AI 🤖

一个纯前端、零服务器的 QQ AI 机器人。

**在线使用：** https://hiweny.github.io/QQbot/  
**Android APK：** https://github.com/Hiweny/QQbot/releases

## 当前功能

- 浏览器直接运行，核心页面为单文件 `index.html`
- 连接 QQ 开放平台 WebSocket
- 支持 QQ 群 @、C2C 私聊和频道消息
- 支持 OpenAI 兼容 API 与多模型配置
- 支持消息分条、连续消息合并
- 支持长期记忆、摘要归档和记忆管理
- 支持 Tavily 联网搜索
- 支持 Android WebView 版本与后台保活能力
- 配置和会话数据主要保存在浏览器本地

## 当前结构

```text
QQbot/
├── index.html
└── README.md
```

Android APK 发行文件通过 GitHub Releases 提供，不作为源码文件放在当前仓库根目录。

## 使用

1. 打开在线页面。
2. 在 QQ 开放平台创建机器人并准备 AppID / AppSecret。
3. 在设置中填写 QQ 与 AI 服务配置。
4. 启动机器人并在测试群或私聊中使用。

## AI 服务

支持 OpenAI 兼容接口，可自行填写 Base URL、API Key 和模型名称。项目也保留了第三方公益服务的接入方式，但其稳定性不由本项目保证。

## 数据

浏览器版尽量采用本地存储保存配置、会话和记忆，不需要为机器人另外部署自己的后端。

## 说明

本项目仅供学习交流，请遵守 QQ 开放平台及相关服务的使用规范。

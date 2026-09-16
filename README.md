# Cloudflare Workers & Pages Dashboard

Android 客户端，界面排版参考官方 Dashboard 截图，支持 **API Token 登录**。

## 功能

### 登录
- 输入 Cloudflare API Token 登录
- Token 本地安全保存

### 列表页（对应截图1）
- 账单仪表板
- 周期统计（请求 / CPU / 可观测性事件 / 构建时间）
- 搜索应用程序
- Workers + Pages 真实列表

### 详情页（对应截图2）
- 标签栏：概述 / 指标 / 部署 / 绑定 / Observability / 域 / Access / 设置
- 域名信息
- 绑定关系示意图
- 调用次数 / CPU 时间 / 错误 指标卡片 + 折线图

## 使用方法

1. 打开 App，粘贴 API Token
2. 权限建议：
   - Account → Workers Scripts → Read
   - Account → Account Settings → Read
3. 登录后查看真实数据

## 生成 Token

1. https://dash.cloudflare.com
2. 头像 → My Profile → API Tokens → Create Token

## 构建 APK

```bash
./gradlew assembleDebug
```

或使用 GitHub Actions 自动构建。

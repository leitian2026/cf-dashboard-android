# Cloudflare Workers & Pages Dashboard

Android 客户端，界面排版参考官方 Dashboard，支持 **Global API Key** 登录。

## 登录方式

使用 **邮箱 + Global API Key**：

1. 打开 https://dash.cloudflare.com
2. 头像 → My Profile → API Tokens
3. 页面最下方 **API Keys** → Global API Key → View
4. 输入密码后复制 Key
5. 在 App 里填入邮箱和 Key 即可登录

## 功能

- 列表页：账单、统计、搜索、Workers/Pages 列表
- 详情页：概述、指标、绑定关系、折线图

## 构建

GitHub Actions 自动构建，或本地：

```bash
./gradlew assembleDebug
```

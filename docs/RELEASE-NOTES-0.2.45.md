# DiPlay v0.2.45 — 旧版车机安全更新连接修复

## 更新内容

- 修复部分 Android 4.4/5.0 定制车机检查更新时提示 `No enabled protocols; SSLv3 is no longer supported` 的问题。使用兼容 API 19 的独立 HTTPS 实现，仅协商 TLS 1.2。
- 其他 Android 版本仅在遇到相同的 SSLv3 协议错误时切换到安全 TLS 1.2 客户端；不对超时、证书验证或其他连接错误放宽安全要求。
- 维持严格的 HTTPS 主机名与证书验证、GitHub 更新源及跳转白名单、APK SHA-256 和 Android 签名校验。
- 中英文错误提示说明如何从官方 Release 手动下载安装包。
- 已在 Android 4.4 (API 19) 和 Android 11 (API 30) 模拟器上完成真实 GitHub 更新检查，均通过；增加协议选择与降级测试。
- 同步最新上游 NCM TCP 诊断预算与 SYN 可观测性修复。

## 验证与限制

通过项目规定的单元测试、Lint 和 Debug 构建，并使用项目既有原始签名证书构建正式 APK。不同车机厂商定制 ROM 的表现仍需实机确认。已有版本如无法检查更新，需要先手动安装此包，Android 系统安装仍需用户授权。

## 发布附件

- `DiPlay-v0.2.45-legacy-release.apk`
- `DiPlay-v0.2.45-legacy-release.apk.sha256`

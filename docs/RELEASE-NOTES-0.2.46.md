# DiPlay v0.2.46 — USB/VPN、AirPlay 和车机兼容优化

## 更新内容

- 有线 AirPlay 监听绑定明确的接口 scope，并完善内核 LISTEN 状态诊断。
- USB/VPN 使用实际 Android API 等级判断，改善部分虚报系统版本的车机兼容性。
- 蓝牙诊断使用 Hybrid 适配器，适配 HSAE 外置蓝牙设备。
- 无线配对等待提示更明确，引导用户在 iPhone 上选择 DiPlay。
- 保留 v0.2.45 中对 Android 4.4/5.0 车机 HTTPS TLS 1.2 更新检查的修复。

## 验证说明

本次由最新远端 main 构建，运行项目单元测试、Lint、Debug 和正式签名构建；实际车机表现仍需在对应 ROM 上测试。APK 保持原有签名以支持从先前版本升级。

## 发布文件

- `DiPlay-v0.2.46-legacy-release.apk`
- `DiPlay-v0.2.46-legacy-release.apk.sha256`

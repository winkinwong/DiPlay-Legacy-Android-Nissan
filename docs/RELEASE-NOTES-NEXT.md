# DiPlay next release notes

Changes through DiPlay 0.2.14 are documented in [0.2.14 release notes](RELEASE-NOTES-0.2.14.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

No unreleased changes are recorded yet. Add future unreleased changes here.

## Unreleased (post 0.2.46-t32.1)

- **触摸滑动延迟优化（P5a）**：连续的触摸 MOVE 报告在控制器发送队列中合并为最新一个点，DOWN/UP 等状态变化严格保序；事件通道写阻塞时不再重放整段过期滑动轨迹（实车基线：touch2frame 平均 24–126ms、最差 478ms，touchSendMax 最高 330ms）。
- **更新检查改指本 fork**：应用内更新检查从原作者仓库改查 `winkinwong/DiPlay-Legacy-Android-T32` 的 Release；版本比较支持 `vX.Y.Z-t32.N` 标签（远端与已装两侧都解析 `-t32.N` 限定符）。
- **退出 CarPlay 后进程崩溃修复（实车 FATAL 取证）**：退出会话后 activity 恢复时，`syncAirPlayDarkMode` 会向已终止的 `airPlayCommandExecutor` 提交任务并抛出 `RejectedExecutionException`，导致整个应用进程崩溃；进程 abrupt 死亡后内核 VPN/TUN 状态残留，下一次 USB 连接的首个 TCP SYN 被静默丢弃（表现为「卡在控制通道 30 秒、必须重插数据线」）。现在该同步在关闭中直接跳过、executor 终止时记录诊断而非崩溃。
- **视频启动黑屏修复**：流启动后解码器配置耗时数百毫秒，期间堆积的帧超过 250ms 帧龄阈值曾被误判为「积压」并销毁健康解码器、等待关键帧（实车：启动 2 次恢复、首个 5 秒窗口最大帧间隙 1859ms）。现在解码器（重新）配置完成后有 2 秒宽限期，宽限期内超龄帧正常送入（演示时间仍按帧率节奏播放）而不触发恢复。
- **AirPlay 监听 IPv6 scope 解析加固**：监听 socket 的 link-local scope 解析现在优先读 `/proc/net/if_inet6`（内核真值），TUN 销毁重建后 Java `NetworkInterface` 缓存陈旧时也能绑定到正确接口（issue #133 静默丢包家族）；解析失败仍回退原行为。
- 诊断增强：USB 绑定日志新增 `wired airplay bind=… scopeId=N mode=scoped|scopeless-fallback`；NCM 入站 TCP 诊断新增 `dstHost=<布尔>`（iPhone 的 SYN 目标是否为本端 TUN 地址）；出站 TCP（如 SYN-ACK）现在也进入导出报告预算（此前只进 logcat）。
- 诊断修正：`TouchLatencyProbe` 的 `touchSendMax` 统计改用原子累计，多线程下数字可信。

Changes that were previously listed here shipped in [0.2.46-t32.1 release notes](RELEASE-NOTES-0.2.46-t32.1.md).

## Unreleased (v0.2.46-t32.2+)
- `diagnostics`: 连接诊断新增 `Vendor BT services anwPhoneLinkResolved=N hsaeBluetoothService=bool` 行——
  探测航盛/日产 MCU 私有蓝牙服务（com.anwsdk.service 可绑定数 / com.hsae.bluetoothservice 存在性），
  用于判断无线 CarPlay 的 MCU 蓝牙回落路径在车机上是否可行（纯布尔，不含地址或身份信息）。
- `connection`: 新增实验性设置「USB 连接时自动停止 iPod 服务」——在发现 iPhone 时
  自动停止车机 iPod 服务（com.hsae.ipod），解决航盛车机（2020 奇骏 NTDA3 等）上
  iPod 服务抢占 USB 接口、必须手动强制停止才能连接 CarPlay 的问题。root 车机使用
  `am force-stop`（可停前台服务），其他车机回退 killBackgroundProcesses；
  默认关闭，仅在需要的车机上开启。下次 USB 连接生效。
- `connection`: 「选择 iPhone」在隐藏框架 BluetoothAdapter 的车机
  （日产航盛 MCU 等）上不再直接落入手动输入 MAC——现在先查询
  厂商 AnwPhoneLink 服务记录的 MCU 配对设备列表（只读
  Binder 事务，5 秒超时），列表非空时直接点选（与既有列表框一致，
  仍保留手动输入入口）；服务不可达或列表为空时回退手动输入。
  查询为只读，不改变车机蓝牙状态。
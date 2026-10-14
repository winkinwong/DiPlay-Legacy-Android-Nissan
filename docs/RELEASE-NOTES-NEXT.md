# DiPlay next release notes

Changes through DiPlay 0.2.14 are documented in [0.2.14 release notes](RELEASE-NOTES-0.2.14.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

No unreleased changes are recorded yet. Add future unreleased changes here.

## Unreleased (post 0.2.46-t32.1)

- **触摸滑动延迟优化（P5a）**：连续的触摸 MOVE 报告在控制器发送队列中合并为最新一个点，DOWN/UP 等状态变化严格保序；事件通道写阻塞时不再重放整段过期滑动轨迹（实车基线：touch2frame 平均 24–126ms、最差 478ms，touchSendMax 最高 330ms）。
- **更新检查改指本 fork**：应用内更新检查从原作者仓库改查 `winkinwong/DiPlay-Legacy-Android-T32` 的 Release；版本比较支持 `vX.Y.Z-t32.N` 标签（远端与已装两侧都解析 `-t32.N` 限定符）。
- 诊断修正：`TouchLatencyProbe` 的 `touchSendMax` 统计改用原子累计，多线程下数字可信。

Changes that were previously listed here shipped in [0.2.46-t32.1 release notes](RELEASE-NOTES-0.2.46-t32.1.md).

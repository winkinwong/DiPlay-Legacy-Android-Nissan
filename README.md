# DiPlay Legacy Android — X-Trail (T32) head-unit optimization

[English](README.md) · [简体中文](README.zh-CN.md)

> **Third-party listing:** This build is listed in the third-party versions directory on the [DiPlay official website](https://diplay.org).

> **This repository is the X-Trail (chassis code T32) head-unit optimization build.**
> Its verification and optimization target is the **Nissan X-Trail (奇骏, chassis T32)**: tested on a **2020 X-Trail** whose head unit is a
> **Hangsheng HSAE `NTDA3_BOARD_S02` / Android 4.4.3 (API 19) / 32-bit freescale sabresd (`NTDA3_P350`)** with an iPhone
> (tested: iPhone 14 Pro / iOS 27.0) connected over wired USB CarPlay. X-Trail-specific changes shipped in **v0.2.46-t32.2**;
> item-by-item notes are in [release notes](docs/RELEASE-NOTES-NEXT.md) and the [CHANGELOG](CHANGELOG.md).
>
> This project is modified from [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) with deep compatibility refactoring.
>
> **Scope difference with upstream**:
> - Upstream DiPlay supports **Android 7.0+ (API 24)** in latest releases, primarily targeting modern 64-bit platforms.
> - This project focuses on **Android 4.4 KitKat (API 19) through Android 6.0**, as well as legacy 32-bit automotive hardware architectures (`armeabi-v7a`, `x86`, Allwinner T3, MediaTek, AC8227L), low-level Linux usbfs driver hardening, and proprietary Bluetooth stacks (e.g., Hangsheng HSAE for Dongfeng Nissan, **including the X-Trail**).
>
> Upstream project: https://github.com/shihabal3amri/DiPlay

> **Trademark notice / 商标声明:** This is an independent third-party open-source project, not affiliated with, endorsed by, or authorized by Nissan Motor Co., Ltd., Dongfeng Nissan (东风日产) or their affiliates. NISSAN, X-Trail and 奇骏 are trademarks of their respective owners, referenced here only to describe vehicle and head-unit compatibility. CarPlay and its icon belong to Apple Inc.

**CarPlay for the Nissan X-Trail and other compatible legacy Android head units.** Wired and wireless, with the familiar DiAuto interface. Independent app: `com.shihab.diplay`. The upstream's BYD support stays in the code, but **this repository is verified and optimized for the X-Trail**.

> **Support scope:** This repository focuses on the **Nissan X-Trail** head unit; the upstream's BYD support remains in the code but is not the target of its verification or optimization. Other brands may work, but they are unsupported here and there are no plans to add support or fix brand-specific incompatibilities.

[Upstream website](https://shihabal3amri.github.io/DiPlay/) · [This fork's release v0.2.46-t32.2](https://github.com/winkinwong/DiPlay-Legacy-Android-T32/releases/tag/v0.2.46-t32.2) · [Report a problem](https://github.com/winkinwong/DiPlay-Legacy-Android-T32/issues/new/choose)

**Latest release:** **v0.2.46-t32.2** (versionCode 69, published 2026-10-15) — APK 16,179,722 bytes, SHA-256 `ccdef79e1138950830cbedfebeff37e2a5cc1cc08ef17f994c3bfa7a12327fb1`, v1+v2 signed with this fork's certificate (`t32-key0`). It overinstalls on v0.2.46-t32.1 (same certificate); uninstall any older fork or the original author's build first (different certificates).

**On-car verification (2026-10, 2020 X-Trail / HSAE NTDA3 / Android 4.4.3 / iPhone 14 Pro / iOS 27):** wired USB is daily usable; frame decryption runs on the bundled Conscrypt native implementation (1.2–5.9 ms/frame, 3.5–23.7 MB/s); the exit-session process crash and startup black screen are fixed; the iAP2 peer-silence fix was ported into the official upstream v0.2.63 by the upstream author (`port(winkinwong)`). **Known limits:** wireless (Bluetooth) is **not yet car-tested**; touch latency is a hardware floor (iPhone rendering + i.MX6 decode pipeline — lowering the display resolution is the only lever); the iPod auto-stop is experimental and off by default.

![DiPlay home](site/assets/home.png)

## Optimizations for the X-Trail (T32) head unit (shipped in v0.2.46-t32.2)

> These changes shipped in **v0.2.46-t32.2**; item-by-item notes are in [release notes](docs/RELEASE-NOTES-NEXT.md); the report behind them is [issue #134](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/issues/134).
> They are verified **on the car** (wired USB daily usable) **and offline** — 2,137 unit tests, log replay, micro-benchmarks and the full CI command. Wireless is not yet car-tested.

| Problem | What it looked like on the X-Trail | What this repository changes |
| :--- | :--- | :--- |
| **Stuttering video** | 9–91 ms of decryption per frame (`decryptAvgUs` in the logs), dropped frames | Android 4.4 has no system ChaCha20-Poly1305, so every frame fell back to BouncyCastle's **pure-Java** decryption. The cipher now prefers the platform implementation and falls back to the **bundled Conscrypt native** one (9.1× faster for the same code path in an offline benchmark). |
| **Lost crash evidence** | A crashed run exported a report with no `FATAL` line, so no stack could ever be obtained | A multi-line stack was handed to the redactor as one line, and the redactor drops any line containing a newline. Lines are now redacted individually, a durable crash record is kept on disk (**survives a restart**), and the report gained a `Durable crash record` section. |
| **Settings overlay crash** | DiPlay died after opening and closing the settings overlay a few times | Rebuilding the settings menu cast the previous menu's parent to a `ViewGroup`; when it had been detached that threw out of the gesture handler. The rebuilt menu is now re-attached safely. |
| **Control channel stalls** | Needed a cable replug to recover | That guard watched iAP2 control frames only. On the X-Trail the iPhone sends none for minutes while AirPlay keeps streaming, so the 90 s reset tore down a **healthy** session. The reset now also requires AirPlay media to have gone quiet, and it logs that it kept the link. |
| **Broken diagnostics** | `decryptMBps` always read 0 | Integer division truncated every throughput below 1 MB/s. It now reports two decimals, and the report gained a `Frame cipher:` line naming the implementation actually in use. |
| **Manual MAC entry for iPhone choice** | On head units that hide the framework Bluetooth adapter (HSAE MCU), "Choose iPhone" fell back to typing the Bluetooth MAC by hand | The vendor AnwPhoneLink service is queried for the MCU's paired devices (read-only Binder transaction, 5 s timeout); a non-empty list is shown for picking, with manual entry kept as the fallback |
| **iPod service grabs the USB interface** | com.hsae.ipod had to be force-stopped by hand before every USB CarPlay connect | Experimental setting "Stop iPod service on USB": the service is stopped automatically when an iPhone is found (`am force-stop` on rooted units, `killBackgroundProcesses` otherwise); off by default, applies at the next USB connection |

**Checked, no code change needed:** the "no sound" report was reconciled against the full log — the app's data path was healthy (a continuous 105 s 48 kHz media stream, zero write errors, zero underruns), so it is a head-unit audio-source routing issue, matching the upstream author's conclusion in the issue.

## Inherited from upstream 0.2.14

Install on the **car**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is required for use. Core CarPlay does not require ADB; optional dashboard, battery, wheel-speed and parked-video features do. Your head unit must permit APK installation. **This repository builds with minSdk 19 (Android 4.4)**, so legacy head units such as the X-Trail's can install it; individual wireless features still need a recent enough Android version (car hotspot Android 9+, Wi-Fi Direct Android 10+), and some use a firmware-dependent legacy path — see [Android 9 Wi-Fi Direct](docs/ANDROID9_WIFI_DIRECT.md). Android 10+ verifies its negotiated group frequency.

- Wired USB and wireless CarPlay with local authentication.
- BYD HUD navigation with arrows, distance and street names on verified firmware.
- Car hotspot support, improved audio buffering and saved receive diagnostics.
- Automatic address discovery, fixed-channel Wi-Fi fallbacks and successful-configuration memory.
- Icon/text size, resolution and frame rate; applying a display change reconnects CarPlay.
- Local diagnostic export. Reports are sent only if you choose to share them.
- Separate installation alongside DiAuto. Run one projection app at a time.
- Legacy Android fallbacks for media keys, notification/services, USB configuration and requests, audio/video codecs, multidex and Java library APIs.

This is **not an Apple-certified product**. The APK bundles an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity for DiPlay. A bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved. This release invites community testing; it is not a guarantee of universal compatibility.

Earlier releases were tested on the development DiLink5.1 car: live windshield guidance and street names work, Car hotspot now starts CarPlay, and Wi-Fi Direct performance is substantially improved. Occasional audio cutouts remain and are deferred to a later update. The floating-map test build was installed on the development DiLink 5.1 car; feedback led to the pinch corrections in 0.2.9. Earlier wheel-speed and video contributions were tested on a BYD Tang with DiLink 5.0 and an iPhone 15 Pro on iOS 27; wheel-speed dead reckoning in tunnels remains unverified. Broader head-unit and iOS compatibility is not guaranteed. The HUD firmware scope and cleanup limits are documented in [BYD navigation](docs/BYD_NAVIGATION.md).

## What’s new in v0.2.46-t32.2 (this fork)

- **Choose-iPhone list via the vendor AnwPhoneLink service**: on head units that hide the framework Bluetooth adapter (HSAE NTDA3), "Choose iPhone" queries the MCU's paired devices over a read-only Binder transaction and shows them for picking; manual MAC entry stays as the fallback.
- **Stop the iPod service on USB (experimental)**: stops `com.hsae.ipod` when an iPhone is found, so it no longer grabs the USB interface before CarPlay connects; `am force-stop` on rooted units, `killBackgroundProcesses` otherwise. Off by default; applies at the next USB connection.
- **Issue #134 fixes**: Conscrypt-native frame decryption (1.2–5.9 ms/frame), durable crash records that survive a restart, the settings-overlay crash fix, and the iAP2 peer-silence reset that now also requires AirPlay media to have gone quiet — the upstream author ported this fix into the official v0.2.63 (`port(winkinwong)`).
- **Exit-session crash fix**: leaving CarPlay no longer kills the whole app process (a `RejectedExecutionException` in the dark-mode sync tore down the VPN/TUN state and dropped the next USB connect); startup black-screen grace period for slow decoder configuration; AirPlay listener IPv6 scope hardening (`/proc/net/if_inet6`); touch MOVE coalescing; the in-app update check now targets this fork's releases.

See [release notes](docs/RELEASE-NOTES-NEXT.md), [validation](docs/VALIDATION.md) and the [v0.2.46-t32.1 notes](docs/RELEASE-NOTES-0.2.46-t32.1.md) for details; general stutter, calls/Siri, decoder and model-specific reports still need current-device evidence.

If a problem remains, reproduce it on **v0.2.46-t32.2**, then use **Settings → Diagnostics → Save diagnostic report**. Android 10+ normally saves to **Downloads/DiPlay**; Android 9 uses the document picker. If unavailable, use **View report** or **Share** from the confirmation, which identifies external/private fallback storage. Review the `.txt` and add it to a matching [existing issue](https://github.com/winkinwong/DiPlay-Legacy-Android-T32/issues), or [create one](https://github.com/winkinwong/DiPlay-Legacy-Android-T32/issues/new/choose). Include vehicle/head-unit model, exact firmware and Android, phone/iOS, connection backend, relevant settings, steps and failure time. Reports are shared only when you choose; never post your hotspot password.

## Documentation

[Existing Wi-Fi / Same LAN](docs/EXISTING_WIFI.md) keeps the iPhone and head unit
on an external router. See the guide for setup, build requirements and the
BYD DiLink 4.0 / Android 10 clean-install validation result.

- [Install and connect](docs/INSTALL.md)
- [Compatibility and troubleshooting](docs/COMPATIBILITY.md)
- [Smooth wireless CarPlay](docs/SMOOTH_WIRELESS.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Build from source](docs/BUILD.md) — select `mobile` for the main DiPlay app; `maphost` is a map sample.
- [Validation](docs/VALIDATION.md)
- [Release notes](CHANGELOG.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

The app and release website are available in English, Arabic, Russian, Ukrainian, Spanish, Simplified Chinese and Traditional Chinese (Taiwan). Traditional Chinese uses Taiwan wording; the app also recognizes Hong Kong/Macao and explicit Hant selections without claiming separate regional translations. Choose the app language in Settings; on Android 13+, it stays synchronized with Android’s per-app language setting.

## Source and credits

Based on [xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The home/settings UI and website adapt [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; that license is included in `docs/licenses`. Preserve those notices when distributing modifications. CarPlay and its icon belong to Apple Inc.; NISSAN, X-Trail and 奇骏 are trademarks of Nissan Motor Co., Ltd.; no Apple, BYD or Nissan affiliation or endorsement is implied.

This repository starts with a clean public source snapshot. Local research, tester reports and release-signing secrets are excluded. The complete source corresponding to the APK is provided with every release; experimental runtime identity assets are described separately in the build instructions and notices.

## Community & Feedback

- **Issues & Discussions**: Feel free to discuss questions and issues in [this repository's GitHub Issues](https://github.com/winkinwong/DiPlay-Legacy-Android-T32/issues). We will reply whenever time permits. Upstream questions: [upstream Issues](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/issues).
- **Check Real Android OS Version**: If installation fails (e.g. "There was a problem parsing the package"), your aftermarket head unit may have a spoofed system version (such as fake Android 10/11/12 on top of real Android 4.4/5.1). You can use this head unit inspection tool provided by [@493330227-wq](https://github.com/493330227-wq) to check your genuine Android version and API level:  
  👉 **[headunit-version-inspector](https://github.com/493330227-wq/headunit-version-inspector)**
- **Attach Diagnostic Logs**: When reporting a specific problem, attaching detailed diagnostic logs (via **Settings → Diagnostics → Save diagnostic report**, automatically exported to USB/SD cards since v0.2.27) and environment details (head-unit model, Android version, iPhone/iOS version) is strongly recommended.
- **Contributions Welcome**: Pull Requests (PRs) from experienced developers are warmly welcomed to help improve compatibility across various legacy automotive hardware!
- ⭐ **Support**: If this project is helpful to you, please give it a **Star**!


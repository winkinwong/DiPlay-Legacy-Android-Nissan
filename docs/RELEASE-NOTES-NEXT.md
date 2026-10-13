# DiPlay next release notes

Changes through DiPlay 0.2.14 are documented in [0.2.14 release notes](RELEASE-NOTES-0.2.14.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

## Unreleased

Addressed from [upstream issue #134](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/issues/134) (Nissan X-Trail 2020 / HSAE NTDA3 / Android 4.4.3 / wired USB CarPlay):

- **Stuttering video (#134-2):** frames were decrypted with BouncyCastle's Java ChaCha20-Poly1305 because Android 4.4 installs no ChaCha20-Poly1305 provider and the bundled Conscrypt was only ever handed to `SSLContext`, never installed. The frame cipher now falls back to the bundled Conscrypt provider (native BoringSSL AEAD), keeping the platform's own implementation whenever it is available. A 33,512-byte frame benchmark on a desktop JVM measured 17 µs against 154 µs for the Java path (9.1×); the absolute device figure still needs an on-device report.
- **Diagnostic reports lost every crash (#134-4):** a multi-line crash stack was handed to the redactor as one line, which drops lines containing newlines, so `FATAL` never reached the log. Session logs now redact line by line, a durable `diplay_crash.log` is kept under the app's log directory, and the exported report carries a `Durable crash record (survives a restart)` section.
- **Settings overlay could kill the process (#134-4):** rebuilding the settings menu cast the previous menu's parent to a `ViewGroup`; when it had been detached, that cast threw out of the gesture handler. The menu is now re-attached to the content root instead.
- **Wired link reset while video was streaming (#134-3):** the iAP2 peer-silence guard only watched control frames, and the NTDA3's iPhone sends none for minutes while AirPlay keeps delivering, so the 90 s reset tore down a healthy session (video was normal one second before the reset, then stalled for about a minute). The reset now also requires AirPlay media to have gone quiet, and logs `AirPlay media is still arriving; keeping the link` when it deliberately keeps the link.
- **`decryptMBps` reported 0 for slow streams:** integer division truncated every throughput under 1 MB/s, hiding exactly the value that shows decryption is the bottleneck. It now reports two decimals.
- **Added diagnostics:** the exported report states which ChaCha20-Poly1305 implementation the device resolved (`Frame cipher: …`).
- **Tests:** 27 new tests cover the cipher fallback, the crash record surviving a restart, the settings-menu lifecycle, and the media-activity probe. A pre-existing timing-sensitive wait in `SmoothVideoHostLifecycleTest` was widened from 5 s to 30 s (it failed on the unmodified tree too).

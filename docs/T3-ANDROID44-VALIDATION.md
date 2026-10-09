# T3 / Android 4.4 Validation

## Target and Provenance

- User-reported hardware: T3 head unit, Android 4.4; iPhone 15 Pro, iOS 26.6.
- Local release: `0.2.18-t3.1`, version code `38`, package `com.shihab.diplay`.
- Upstream base: DiPlay `v0.2.14`, commit `7887bb7bf2b52258e663a2a4ea1332382ed80ad8`, already merged into this fork.
- Android installation floor: API 19. Native APK includes ARMv7 for the 32-bit T3.
- Local verification device: Android 4.4.4 / API 19 x86 emulator, 1024 x 600. It is not the T3 kernel or hardware.

## Compatibility Work

- Media3 pinned to 1.4.1, without a manifest override of incompatible library minimum SDKs.
- KitKat AudioTrack, legacy audio focus and media-button handling, plus guarded newer codec/UI APIs.
- KitKat station Wi-Fi and hotspot interface discovery without Android 5/6 network APIs.
- USB configuration values and alternate settings read from raw descriptors on the authorized connection; do not assume configuration 1 or alternate 0.
- Native USB alternate selection uses the real descriptor value; USBMUX and NCM retain their shared authorized connection.
- USB re-enumeration actively polls when old vendor firmware omits ATTACHED, with bounded failure reporting.
- KitKat VPN uses a native blocking TUN fd. Only IPv6 link-local traffic is routed; API 19 cannot scope a VPN by application.
- Release builds require production signing and standalone runtime authentication assets; no debug-signing fallback.
- Standalone builds validate key/certificate consistency and repeated signatures before packaging.
- Application initialization classes are explicitly retained in the main DEX. A release-only KitKat cold-start failure was reproduced and fixed, then checked in the signed production APK.
- USB reads on Android below 9 are capped at 16 KiB, with bounded smaller-queue fallback for old vendor USB implementations.

## Automated Checks

`T3LegacyRuntimeTest` runs on the actual API 19 emulator, rather than an unsupported Robolectric API 19 sandbox. Its checks cover:

1. Opening each settings category.
2. Opening and closing manual-hotspot, Wi-Fi Direct and USB connection screens. This is an entry-point test, not a transport connection test.
3. Loading the native TUN fd helper.
4. Preserving USB configuration and alternate-setting values through KitKat's hidden descriptor constructors.
5. Starting AudioTrack and observing a successful PCM write with legacy audio focus.
6. Starting the local AirPlay listener, answering `/info`, recovering from an occupied port and restarting it.
7. X25519, Ed25519 and ChaCha20-Poly1305 round trips on Dalvik.
8. Loading the bundled local accessory identity and signing a challenge. This proves local key consistency only.
9. Encoding a synthetic H.264 stream with the platform encoder, feeding CarPlay-format access units to the real decoder, observing a Surface frame and releasing the decoder.

## Authentication Input Repair

The previous local authentication input failed PKCS#8 parsing on both desktop Java and
API 19. Changing the Android crypto provider alone did not fix it. The original
`DiPlay-v0.2.17-legacy-release.apk` is preserved; its rejected input was extracted
into a private temporary directory for a negative preflight test.

The replacement is the experimental identity from the existing downloaded upstream
`DiPlay-0.2.14.apk`. Its whole-APK SHA-256 is
`62b31f79db32bc7c85013ae830460b697a5952fad571ed0030b97341dde0b2e3`, matching
[the official release](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.14).
Its key/certificate pair passed two independent loads and six locally verified
challenge signatures with changed-challenge rejection. No new identity was generated.
Runtime private inputs remain ignored by Git. An iPhone's trust decision is not
established by these local checks.

The negative test against the previous APK's nonempty but malformed key failed at
`verifyStandaloneAuthentication` with an EC key-parse error, as intended. The valid
upstream pair passes the same task. A file-existence check alone is insufficient.

## Execution Results (2026-10-09)

The required CI command from AGENTS.md passed, including all three application debug
lint/build tasks. The shared suite passed 1,046 tests and the common suite passed
892 tests: 1,938 total, zero failures, errors or skipped tests. Home has no unit-test
sources. Mobile release lint, standalone release and release instrumentation APK
builds also passed.

The custom instrumentation targets `com.shihab.diplay`, not the debug application:

| Emulator | Signed production runtime checks | Result |
| --- | --- | --- |
| Android 4.4.4, API 19, x86 | All nine checks listed above | 9 run, 0 failures |
| Android 7.0, API 24, x86 | Eight applicable checks; KitKat-only descriptor check returns without running | 9 reported, 0 failures |
| Android 8.1, API 27, x86 | Eight applicable checks; KitKat-only descriptor check returns without running | 9 reported, 0 failures |

AGP 9.3 initially packaged a separately trimmed compatibility library in the release
test APK. On native multidex Android it shadowed target-app collection methods.
The test-only L8 task now retains that library in full; production bytes are not
changed by this workaround. All three final runs above use the corrected test APK.
Standalone production cold starts were also checked outside instrumentation.

API 19 fresh installation and same-key upgrade from version code 37 to 38 passed.
The upgrade retained the application UID and a private-files marker, and the updated
application opened successfully. There were no AndroidRuntime fatal exceptions in
the checked cold-start/runtime logs. The 1024 x 600 production home screen rendered.

### Deliverable

- File: `DiPlay-v0.2.18-T3-Android4.4-release.apk` in the project root.
- Version: `0.2.18-t3.1`, code `38`; minimum API `19`; not debuggable.
- Size: 11,582,675 bytes.
- SHA-256: `7a827b334104894ebc0a23a6c6f0a019625d6202e22d61427ae385e8eba45c38`.
- Production signing certificate SHA-256: `bca015c0cb43469fee55539b4054ff862671e7b41e6612eee0cb8566d327b7ae`, matching the previous release.
- APK v1 and v2 signatures and 16 KiB zip alignment passed verification.
- ABIs: ARMv7, ARM64, x86 and x86_64. The ARMv7 USB helper records native API 19.
- Bundled authentication assets match the validated upstream inputs byte-for-byte.
- The Application companion is defined in `classes.dex`, not just referenced there.

The frozen build started from `0668e1b` with the local transport/build patch applied.
Those source edits were subsequently committed in the primary checkout as
`2890996` by another task; this validation does not claim to have made that commit.
Build and instrumentation logs are retained outside the repository in the host's
temporary directory, using the `diplay-t3-` prefix.

## Physical Acceptance Still Required

No T3 or iPhone is connected to this build host. A complete successful CarPlay session on this exact combination has **not** been demonstrated. Existing normal Bluetooth pairing, hotspot networking, USB storage installation and iPhone trust prompts do not establish RFCOMM, USBMUX/NCM, accessory authentication or projection compatibility.

All three modes need parked-vehicle acceptance: fresh/upgrade installation, iPhone authorization, first picture, touch, music, navigation voice, Siri microphone and reconnect after unplug/restart. A simulated USB descriptor or a local `/info` response cannot replace these checks.

This independent receiver is not an Apple-certified accessory. The experimental local identity may be rejected by an iPhone. It is not possible to promise one-time connection success without the physical acceptance run.

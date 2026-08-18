# PS-BLOCK for Android

Android port of the PS-BLOCK content filter & screen-time guard. It keeps **all**
features of the Windows build, re-implemented with native Android mechanisms.

## Feature parity

| Windows (Electron) | Android equivalent |
|---|---|
| Domain blocking via `hosts` file | `FilterVpnService` — local DNS filter (no root) |
| PowerShell browser-title monitor | `MonitorAccessibilityService` |
| Redirect tab → `blocked.html` (SendKeys) | global BACK + `BlockedActivity` |
| Kill browsers when limit reached | full-screen `LockoutActivity` re-raised by the service |
| Screen-time counting + idle/lock pause | `ScreenTimeService` (pauses when screen off) |
| Tray + notifications | foreground-service notifications + alerts |
| Password protection (SHA-256 `pw+cb_v1`) | identical hashing in `Config` |
| Add time (+15 min, max 120) | `ScreenTimeActivity` / `LockoutActivity` |
| Auto-start (scheduled task / Run key) | `BootReceiver` on `BOOT_COMPLETED` |
| Anti-uninstall (registry, watchdog) | Device **owner**/admin (`AdminReceiver` + `DevicePolicy`) + sticky FGS |
| `nativeTheme` light/dark | AppCompat DayNight (auto + manual) |
| Hungarian / English | `values/` + `values-hu/` string resources |
| Custom blocklist | `Config.customBlocked`, applied to DNS + titles |
| Built-in domains / allow-list / keywords | ported verbatim in `Blocklist` / `TitleEvaluator` |

## Build

Requires Android SDK (platform 34, build-tools 34) and JDK 17.

```bash
cd Android
./build.sh        # or build.bat on Windows → produces release/PS-BLOCK.apk (signed)
```

Manual:
```bash
gradle wrapper --gradle-version 8.7   # first time only
./gradlew assembleRelease             # signed APK, app/build/outputs/apk/release
./gradlew assembleDebug               # debug APK
```

`build.sh`/`build.bat` sign the APK with the self-signed key in `psblock-release.keystore`
and write it to `release/PS-BLOCK.apk` (gitignored — build it locally rather than committing
a binary). Or open `Android/` in Android Studio.

## Device owner (strong anti-tamper, optional)

On a fresh device with no accounts, provision PS-BLOCK as **device owner**:

```bash
adb shell dpm set-device-owner com.psblock.app/.AdminReceiver
```

As device owner the app blocks its own uninstall while a password is set, forces the filter
VPN always-on, pins Private DNS, and stops the user from reconfiguring VPNs. Without device
owner, it falls back to a normal **device admin** (offered when you set a password), which still
requires deactivating the admin before the app can be uninstalled.

## Required permissions (granted on first run via the **Enable protection** button)

1. **VPN consent** — for the local DNS content filter.
2. **Accessibility service** — to read browser titles/URLs for keyword blocking.
3. **Display over other apps** — for the lockout and blocked screens.
4. **Notifications** (Android 13+) and, optionally, **Device admin** for anti-uninstall.

## DoH / DoT (encrypted DNS) handling

Encrypted DNS is actively countered, not just plain DNS:

1. **Bootstrap hostnames blocked** — `dns.google`, `cloudflare-dns.com`, `dns.quad9.net`,
   `dns.adguard.com`, NextDNS, Mullvad, ControlD, etc. resolve to `0.0.0.0`.
2. **Resolver IPs black-holed** — the VPN routes ~40 known DoH/DoT anycast IPs (IPv4 + IPv6)
   into the tunnel, where their TCP 443 / 853 / QUIC traffic is dropped, so hard-coded
   encrypted-DNS clients fail and fall back to plain DNS (which is filtered).
3. **Private DNS pinned** (device owner only) — system DNS-over-TLS is forced to opportunistic
   and the user can't point it at a custom DoH provider.

See `Blocklist.DOH_DOT_HOSTS / DOH_DOT_IPS / DOH_DOT_IPS6`.

## Notes / limitations

- Browser monitoring covers ~70 Android browsers (`Browsers.kt` /
  `accessibility_service_config.xml`); add more package names there if needed.
- A determined user could still find an obscure resolver IP not on the black-hole list; device
  owner mode (Private DNS pinning) closes that gap.
- Everything runs on-device; no data leaves the phone.

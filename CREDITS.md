# Credits & third-party licenses

Keystone — Copyright (C) 2026 Tylerlikesps4 — GPL-3.0-or-later (see LICENSE).

Keystone is an original, independent app. It reproduces the *kind* of functionality found in
life-OS Notion templates (PARA, life hubs, trackers) but contains none of their text, branding,
layouts or images. "Notion" and "Limitless OS" are trademarks of their respective owners; this
project is not affiliated with them.

## Libraries bundled in the APK

| Component | Version | License | Source |
|---|---|---|---|
| Kotlin standard library | 2.0.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.serialization (json) | 1.7.3 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| kotlinx.coroutines (transitive) | 1.7+ | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Jetpack Compose (ui, foundation, material3) via BOM | 2024.12.01 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| Compose Material Icons Extended (Material Symbols/Icons) | BOM 2024.12.01 | Apache-2.0 | https://github.com/google/material-design-icons |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 | same |
| AndroidX Navigation Compose | 2.8.5 | Apache-2.0 | same |
| AndroidX Lifecycle (runtime/viewmodel compose) | 2.8.7 | Apache-2.0 | same |
| AndroidX WorkManager | 2.10.0 | Apache-2.0 | same |
| Coil (image loading) | 2.7.0 | Apache-2.0 | https://github.com/coil-kt/coil |
| OkHttp / Okio (transitive via Coil) | 4.x / 3.x | Apache-2.0 | https://github.com/square/okhttp |
| SQLite (Android framework, android.database.sqlite) | platform | Public domain | https://sqlite.org |

## Build & test tools (not shipped)

| Tool | License |
|---|---|
| Gradle 8.11.1 | Apache-2.0 |
| Android Gradle Plugin 8.7.3 | Apache-2.0 |
| JUnit 4.13.2 | EPL-1.0 |
| kotlin-test | Apache-2.0 |
| Eclipse Temurin JDK 17 | GPL-2.0 with Classpath Exception |
| aapt2 arm64 build (lzhiyong/android-sdk-tools, used only to build on ARM Linux) | Apache-2.0 |

Apache-2.0 and EPL-1.0 (test-only) are compatible with distributing Keystone under GPL-3.0-or-later.

## Icons, fonts, artwork

- UI icons: Material Symbols / Material Icons by Google — Apache-2.0.
- Font: Roboto (Android system font, not bundled) — Apache-2.0 / OFL-1.1.
- App icon (arch with keystone): original artwork for this project — GPL-3.0-or-later.
- Charts (bar, column, line, radar/Wheel of Life): drawn with Compose Canvas in this project; no chart library.

## Ideas & inspiration (no code copied)

- The PARA method — Tiago Forte (a public organizing method).
- Wheel of Life — a widely used public coaching exercise.
- Open-source apps studied for ideas only: AppFlowy (AGPL-3.0), Anytype (Any Source Available License), Logseq (AGPL-3.0),
  Loop Habit Tracker (GPL-3.0). No source code from these projects is included.

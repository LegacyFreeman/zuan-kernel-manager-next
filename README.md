<div align="center">

# ZUAN KERNEL MANAGER NEXT

### Advanced Android Kernel & System Management

A modern, root-powered Android utility focused on **kernel control, performance tuning, system monitoring, and customization** — with a clean Material 3 interface.

<br>

![Platform](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Root](https://img.shields.io/badge/Root-Required-EF5350?style=for-the-badge&logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-GPL--3.0-0050C6?style=for-the-badge&logo=gnu&logoColor=white)

<br><br>

**Performance • Control • Monitoring • Customization**

</div>

---

## 📖 Overview

**Zuan Kernel Manager Next (ZKM Next)** is an Android system-management application designed for rooted devices.

The project brings frequently used kernel and system controls into one interface, so users can inspect their device, tune supported parameters, manage system components, and monitor performance without constantly switching between shell commands and separate tools.

> [!WARNING]
> ZKM Next performs privileged operations and may modify kernel or system parameters. Incorrect values can cause instability, boot issues, overheating, data loss, or other device-specific problems. **Use advanced features at your own risk.**

---

## ✨ Highlights

<table>
<tr>
<td width="50%">

### ⚡ Performance

- CPU frequency & governor controls
- GPU tuning where supported
- Performance-oriented system tweaks
- Refresh-rate controls
- Memory and VM tuning
- ZRAM configuration

</td>
<td width="50%">

### 📊 Monitoring

- CPU & GPU information
- Frequency monitoring
- RAM / swap information
- Thermal information
- Process & system logs
- Real-time system status

</td>
</tr>
<tr>
<td>

### 🛠️ System Tools

- Root terminal
- Kernel flashing utilities
- Activity launcher
- Build.prop / system property tools
- Application management
- Backup & restore utilities

</td>
<td>

### 🎨 Customization

- App-controlled Light / Dark theme
- Custom application colors
- Custom background support
- Material 3 UI
- Adjustable navigation & visual styles
- Editable language packs

</td>
</tr>
</table>

---

## 🧩 Home-screen Widgets & App Shortcuts

Long-press the ZKM icon (or open the launcher's widget picker) to add:

| Widget | What it does |
| --- | --- |
| **ZKM Performance** | One-tap Balance / Dynamic / Performance CPU mode |
| **ZKM Quick Actions** | Refresh-rate cycle, Clear RAM, Do Not Disturb, Game Space |
| **ZKM Menu** | 8 shortcuts: Game Space, SoC, Battery, Thermal, Display, Process, Terminal, Storage |
| **ZKM Game Launcher** | Your Game Space games, most recently played first |
| **ZKM Live Stats** | Battery, power draw, RAM and temperature (tap to refresh) |

App shortcuts (long-press the icon): **Game Space**, **Performance**, **Clear RAM**, **Thermal**.

Actions that change the system need Root or Shizuku/Axeron, same as inside the app.
Code lives in `feature/widget/`; widgets use plain `RemoteViews` (no extra dependency) and follow
light/dark and Material You colours on Android 12+.

---

## 🎨 Theme System

ZKM Next is designed so the **application itself owns its appearance settings**.

The app provides its own theme configuration instead of relying on the phone's Light/Dark setting. This makes the interface predictable across different ROMs, OEM skins, and system configurations.

### Theme controls include

- ☀️ **Light Mode**
- 🌙 **Dark Mode**
- 🎨 **Custom Colors**
- 🖼️ **Custom Background**
- 🌈 App-controlled color palette
- 🚫 No forced dependency on Android system Dynamic Color

The goal is simple: **what you select inside ZKM Next is what the application uses.**

---

## 🌍 Language Packs

ZKM Next includes an editable language-pack system for customization and community translations.

### Export Language Pack

Creates a JSON file containing the application's translatable strings.

### Import Language Pack

Loads an edited JSON language pack and applies the translated values to the application.

Example structure:

```json
{
  "format": "ZKM_LANGUAGE_PACK",
  "version": 1,
  "locale": "en",
  "strings": {
    "app_name": "Zuan Kernel Manager",
    "settings": "Settings"
  }
}
```

> Keep Android format placeholders such as `%1$s`, `%1$d`, etc. unchanged when translating strings.

---

## 🔐 Root Compatibility

ZKM Next is intended for rooted Android devices and may work with different root implementations depending on the feature and device configuration.

Common environments include:

- **Magisk**
- **KernelSU**
- **APatch**
- Other compatible root environments exposing a usable `su` interface

Some features depend on the kernel, vendor implementation, SELinux policy, available sysfs/proc nodes, and device architecture.

---

## 📱 Supported Architecture

| Architecture | Status |
|---|---|
| `arm64-v8a` | ✅ Primary |
| `armeabi-v7a` | ✅ Supported where available |
| `x86_64` | ⚠️ Device / build dependent |

Feature availability is **device-dependent**. ZKM Next does not assume that every CPU policy, GPU node, thermal node, or kernel interface exists on every Android device.

---

## 🧩 Main Modules

```text
Zuan Kernel Manager Next
│
├── app
│   ├── Dashboard / Overall
│   ├── CPU & GPU controls
│   ├── Memory / ZRAM
│   ├── Thermal controls
│   ├── System tools
│   ├── Settings & Themes
│   └── Language Pack system
│
├── kernel-flasher
│   └── Kernel / boot image flashing utilities
│
├── terminal-emulator
│   └── Integrated terminal engine
│
└── terminal-view
    └── Terminal rendering & interaction
```

---

## 🏗️ Building From Source

### Requirements

- Android Studio or a compatible Android IDE
- Android SDK
- JDK compatible with the project's Gradle configuration
- Android NDK for native components

### Build

Clone the repository and open the project in your Android IDE.

```bash
git clone https://github.com/zuanvfx02/zuan-kernel-manager-next.git
cd zuan-kernel-manager-next
```

Then build the debug APK:

```bash
./gradlew :app:assembleDebug
```

The generated APK will be located under the app's Gradle build output directory.

> Native components may require additional Android NDK configuration depending on the build environment.

---

## 🧪 Development Notes

ZKM Next interacts with low-level Android interfaces such as:

- `/sys`
- `/proc`
- CPUFreq / DevFreq
- KGSL GPU interfaces
- Thermal zones
- Power supply information
- ZRAM
- Kernel scheduler interfaces
- Android system services

Because these interfaces vary significantly between Qualcomm, MediaTek, Samsung, Google, Xiaomi, and other platforms, **a feature being present in the UI does not guarantee that the underlying node exists on every device**.

The application therefore uses device/path detection where appropriate and should gracefully hide or disable unsupported controls.

---

## 🛡️ Safety Notes

Before changing advanced kernel parameters:

1. Know what the parameter controls.
2. Keep a working recovery / boot image backup.
3. Change one group of settings at a time.
4. Avoid blindly copying values from another device.
5. Reboot and verify stability after major changes.

Kernel and thermal tuning can have real hardware consequences. **There is no universal safe value for every device.**

---

## 🤝 Credits & Open Source

ZKM Next builds on ideas, libraries, components, and open-source work from the Android and kernel-management communities.

Special thanks to the developers and projects that contributed inspiration, libraries, tools, or implementation ideas, including:

- [Rve27](https://github.com/Rve27)
- [libxzr](https://github.com/libxzr)
- [5ec1cff](https://github.com/5ec1cff)
- [Rem01Gaming](https://github.com/Rem01Gaming)
- [helloklf](https://github.com/helloklf)
- [capntrips](https://github.com/capntrips)
- [Termux](https://github.com/termux)
- [Kyant0](https://github.com/Kyant0)
- Jetpack Compose / AndroidX
- Material 3
- libsu
- Coil
- Kotlin Coroutines
- KernelSU
- Magisk

Please refer to the source repositories and included license files for the exact licensing and attribution requirements of each component.

---

## 📜 License

Zuan Kernel Manager Next is distributed under the **GNU General Public License v3.0 (GPL-3.0)** unless otherwise stated by individual third-party components.

See [`LICENSE`](LICENSE) for the full license text.

---

## 👨‍💻 Maintainer

<div align="center">

### Zuanvfx01 · Zuanvfx02

Android • Kernel • System Tools • UI/UX

[![GitHub](https://img.shields.io/badge/GitHub-Zuanvfx01-181717?style=for-the-badge&logo=github)](https://github.com/zuanvfx01)
[![GitHub](https://img.shields.io/badge/GitHub-Zuanvfx02-181717?style=for-the-badge&logo=github)](https://github.com/zuanvfx02)
[![Telegram](https://img.shields.io/badge/Telegram-Official%20Channel-26A5E4?style=for-the-badge&logo=telegram)](https://t.me/zuankernelmanagernext)

📦 Releases: https://github.com/zuanvfx02/zuan-kernel-manager-next/releases

</div>

---

<div align="center">

**Built for users who want deeper control over their Android device.**

⭐ Star the repository if you find ZKM Next useful.

</div>

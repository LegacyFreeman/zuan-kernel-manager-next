# Working modes (Basic / ADB / ROOT)

## Intro
Final step after the Privacy page: "Select working mode". Badges: Basic = Noroot, ADB = Noroot, ROOT = Experience.
The choice is stored by `WorkingModeManager` (`settings_prefs` -> `working_mode`). Installs that already finished the old intro migrate to ROOT.

## Feature tiers (`core/mode/WorkingMode.kt` -> `FeatureAccess`)
- Basic: Dashboard, SoC (view only), Storage, Screen Time, Battery Usage, Charge Stats, Activity Launcher, Settings, About, Help.
- ADB (adds): Terminal, SetEdit, FPS, Process Manager, App Manager, Debloat, Dex2oat, Doze, Display, Power menu.
- ROOT: everything else (Kernel Flasher, KSU WebUI, GPU, Thermal, MIUI Thermal, Battery Controller, kernel node writes).
Unlisted routes default to ROOT. Locked screens are produced by `gatedComposable` in `ui/ModeUi.kt`.

## Execution
`KernelRootRuntime` is the single gate: Basic never runs a shell, ADB runs through `ShizukuShell`, ROOT uses libsu + the ZKM daemon.
Kernel node writes need ROOT. The su prompt only appears in ROOT mode. Mode can be changed in Settings > Working mode.

## Language
`values-in/` was removed and the remaining hard-coded Indonesian strings were moved to English resources, so language pack export/import always starts from one English base.
`docs/ZKM-Help-Pack-ID.json` is kept as an optional importable pack.

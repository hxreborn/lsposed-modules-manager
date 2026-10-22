# Modules Manager 📦

An Android app for finding, installing and updating Xposed/Lsposed modules.

- Package: `com.rushi.modulesmanager`
- Android 8.1 (API 26) and newer
- ⬇️ [Latest release](https://github.com/rushiranpise/lsposed-modules-manager/releases)

## Features

- Browse over 2,000 module repositories from the daily [xposed-modules index](https://github.com/rushiranpise/xposed-modules).
- Search, sort by name or last update, and filter by updates, installed or not installed.
- See the installed version next to an available update.
- Open a module to read its README, latest release and target apps.
- Install with the system installer, a PackageInstaller session, or Shizuku for silent installs.
- Long press an installed module to uninstall it.
- The Installed tab lists every installed module, including ones missing from the index.
- Light, dark and system themes, Material You colors, AMOLED black and GitHub-style identicons.

## Build

Requires JDK 21 and Android SDK 37.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Signed release APKs are built by the [release workflow](.github/workflows/release.yml) when a `v*` tag is pushed.

## Data

The app reads the daily index from:

`https://raw.githubusercontent.com/rushiranpise/xposed-modules/main/data/repos.json`

Each entry provides the package, summary, target scope and latest APK release. Installed modules are checked against GitHub releases when the app starts, then at most once every six hours, so releases published after the daily snapshot still appear. The index has no README or full release history, so the app fetches the README when a module is opened.

The Installed tab counts every installed module. The Installed filter on the Modules screen only counts modules that are in the index.

## License

GPL-3.0. See [LICENSE](LICENSE).

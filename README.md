# Modules Manager

An Android client for LSPosed module repositories. It helps you find, install and update
Xposed modules without browsing GitHub releases by hand. It is a companion to LSPosed, not a
replacement for the LSPosed framework: installing a module here only puts the APK on the device,
enabling it still happens in LSPosed Manager.

- Package: `com.rushi.modulesmanager`
- App name: Modules Manager
- Min Android: 8.1 (API 26), target Android 16 (API 36)

## Features

- Browses the official LSPosed repository (`modules.lsposed.org`) with its backup mirrors.
- Custom repositories: any base URL that serves the same `modules.json` format, for mirrors or
  self-hosted module collections.
- Search, sort by name or last update, filter to updates or installed modules.
- Update badges for installed modules, matched by package name and version code.
- Module page with rendered README, release history, download counts and per-APK install buttons.
- Downloads in the background with a progress banner, then verifies that the APK is for the
  expected package, is not older than the installed version, and is signed with the same key
  before handing it to the system installer.
- Channel selection: stable, beta and snapshot releases.
- Installed screen detects Xposed modules through their package metadata and shows which have
  updates. It can open LSPosed Manager or the module's own settings screen.
- Material 3 UI with dynamic color, dark mode and a generated monogram/GitHub avatar per module.

## Repository format

The app uses the format served by `https://modules.lsposed.org/`:

- `<base>/modules.json` - array of modules. Each entry has `name` (the package name),
  `description` (display name), `summary`, `latestRelease`, `latestBetaRelease`,
  `latestSnapshotRelease`, release timestamps and optionally `readme`, `readmeHTML`, `scope`.
- `<base>/module/<package>.json` - one module with its full `releases` array, including
  `releaseAssets` (`name`, `downloadUrl`, `size`, `downloadCount`).

Version strings are `versionCode-versionName`, for example `133-5.0.1`. Updates are detected by
comparing the version code and then the version name, the same rule the LSPosed manager uses.

To add a repository, open the source chip on the Modules screen (or Settings > Repository) and
choose **Add repository**. The URL is validated before it is saved. The app expects the files at
the base URL:

```text
https://example.com/modules/
  modules.json
  module/
    com.example.module.json
```

Local HTTP is allowed only for loopback hosts (`127.0.0.1`, `localhost`, `10.0.2.2`), which is
useful with `adb reverse` while developing.

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 21 and Android SDK 37 (compileSdk 37). Dependencies come from Google Maven and Maven
Central; no other setup is needed.

## Structure

```text
app/src/main/java/com/rushi/modulesmanager/
  AppViewModel.kt            Shared state: source, updates, downloads, navigation detail
  data/Models.kt             Repository models, JSON parsing, channels and version matching
  data/RepoApi.kt            modules.json/module JSON fetching and streaming downloads
  data/RepoRepository.kt     In-memory list plus per-source on-disk cache and failover
  data/Settings.kt           Channel, source and sort preferences
  data/InstalledModules.kt   Xposed module detection through PackageManager metadata
  data/ApkInstaller.kt       PackageInstaller session and result handling
  data/ApkInspector.kt       Package/signature verification of downloaded APKs
  ui/                        Compose screens and components
```

## Notes

- The official repository can be unreachable from some networks. When that happens the app shows
  the error, keeps showing cached data if it has any, and lets you switch to a mirror or custom
  source.
- Installing and updating requires the system "install unknown apps" permission. Updates signed
  with a different key than the installed app are rejected before the installer opens.
- Modules Manager does not check module safety beyond package identity and signing keys. Treat
  modules from third-party repositories like any other sideloaded APK.

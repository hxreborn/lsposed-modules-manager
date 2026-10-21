# Modules Manager

An Android client for the Xposed module ecosystem. It helps you find, install and update modules
without browsing GitHub releases by hand. It is a companion to LSPosed, not a replacement for the
LSPosed framework: installing a module here only puts the APK on the device, enabling it still
happens in LSPosed.

- Package: `com.rushi.modulesmanager`
- App name: Modules Manager
- Min Android: 8.1 (API 26), target Android 16 (API 36)

## Features

- Uses one daily module index instead of scraping the LSPosed website:
  `https://raw.githubusercontent.com/rushiranpise/xposed-modules/main/data/repos.json`.
  It currently contains about 1273 modules, each with a package name, summary, target scopes and
  its latest release.
- Installed modules are checked against GitHub releases on refresh and at most once every six
  hours, so a release published after the daily snapshot still shows up. The checks use ETags and
  stop quietly if GitHub rate-limits or is unavailable.
- Search, sort by name or last update, and filter to updates, installed, or not installed modules.
- Update badges for installed modules, matched by package name and version.
- Module page with the summary, latest release, APK install button and information links.
- Downloads in the background with a progress banner, then verifies that the APK is for the
  expected package, is not older than the installed version, and is signed with the same key
  before handing it to the system installer.
- Installed screen lists modules from the index that are installed, including packages that do not
  declare Xposed metadata, and shows which have updates. Its count matches the Installed filter on
  the Modules screen.
- Material 3 UI with dynamic color, dark mode, centered filters, a draggable scrollbar and
  scroll-to-top control.

## Data format

The index is an array of GitHub repositories. Each module entry has:

- `package` (or an `org_repo` under `Xposed-Modules-Repo/` to derive it from),
- `full_name`, `summary`, `description`, `homepage`, `source_url`, `scope`,
- `release` with `tag`, `name`, `published_at`, `html_url`, `apk_url` and `prerelease`.

The app builds one module from each entry that has a package and an APK. Tags are normally
`versionCode-versionName`, such as `520-3.8.3.r520.9605640`. Plain tags such as `v1.3.0` are also
accepted and compared by version name.

The daily index does not include READMEs or release history. The Overview tab shows the module
summary, and the Releases tab shows the latest release from the index. Live checks replace that
release when GitHub has a newer one.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires JDK 21 and Android SDK 37 (compileSdk 37). Dependencies come from Google Maven and Maven
Central; no other setup is needed. The unit tests use a local `MockWebServer` and cover the daily
index schema, tag/version comparison, live GitHub release parsing and the asset download fallback.

## Structure

```text
app/src/main/java/com/rushi/modulesmanager/
  AppViewModel.kt            Shared state: modules, updates, live checks, downloads
  data/Models.kt             Models, repos.json parsing, channels and version matching
  data/RepoApi.kt            Daily index fetching and APK downloads
  data/RepoRepository.kt     In-memory list, disk cache and live-release merging
  data/LiveReleaseApi.kt     GitHub latest-release checks for installed modules
  data/Settings.kt           Channel, sort and live-check preferences
  data/InstalledModules.kt   Installed package and Xposed module detection
  data/ApkInstaller.kt       PackageInstaller session and result handling
  data/ApkInspector.kt       Package/signature verification of downloaded APKs
  ui/                        Compose screens and components
```

## Notes

- Custom repositories and the repository picker were removed. The app uses the daily index only.
- The Installed tab is scoped to the index. Modules installed from other sources are not listed
  there, which keeps its count aligned with the repository filters.
- The index has one APK URL per module, usually the release asset picked by the data collector.
  Live checks expose all APK assets from the latest GitHub release when an update is found.
- Installing and updating requires the system "install unknown apps" permission. Updates signed
  with a different key than the installed app are rejected before the installer opens.
- Modules Manager does not check module safety beyond package identity and signing keys. Treat
  modules like any other sideloaded APK.

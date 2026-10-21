# Modules Manager

An Android client for LSPosed module repositories. It helps you find, install and update
Xposed modules without browsing GitHub releases by hand. It is a companion to LSPosed, not a
replacement for the LSPosed framework: installing a module here only puts the APK on the device,
enabling it still happens in LSPosed.

- Package: `com.rushi.modulesmanager`
- App name: Modules Manager
- Min Android: 8.1 (API 26), target Android 16 (API 36)

## Features

- Browses the official LSPosed repository (`modules.lsposed.org`) using its current paginated
  API: `/module-list/{page}.json` for the module list and `/module/<package>.json` for details.
  Every page is fetched in parallel and merged into one cached list.
- Tries every known host automatically. Failures on HTTP status, TLS, timeouts and invalid JSON
  all move to the next endpoint, and the endpoint that answered is reused for later refreshes
  and module pages.
- Custom repositories: any base URL serving the modern paginated API, or the older single
  `modules.json` document, for mirrors or self-hosted module collections.
- Search, sort by name or last update, and filter to updates, installed, or not installed
  modules.
- Update badges for installed modules, matched by package name and version code.
- Module page with rendered README, release history, download counts and per-APK install buttons.
- Downloads in the background with a progress banner, then verifies that the APK is for the
  expected package, is not older than the installed version, and is signed with the same key
  before handing it to the system installer.
- Channel selection: stable, beta and snapshot releases.
- Installed screen lists modules from the selected repository that are installed, including
  packages that do not declare Xposed metadata, and shows which have updates. Its count matches
  the Installed filter on the Modules screen.
- Material 3 UI with dynamic color, dark mode and a generated monogram/GitHub avatar per module.

## Repository format

The app uses the API served by `https://modules.lsposed.org/`:

- `<base>/module-list/<page>.json` - one page of modules, starting at page 1. The response is
  `{ "modules": [...], "page": 1, "pageCount": 41, "total": 1201 }` and each module has
  `name` (the package name), `description` (display name), `summary`, `latestRelease`,
  `latestBetaRelease`, `latestSnapshotRelease`, timestamps and contributor data.
- `<base>/module/<package>.json` - one module with its full `releases` array, including
  `readmeHTML` and `releaseAssets` (`name`, `downloadUrl`, `originalDownloadUrl`, `size`,
  `downloadCount`).

Version strings are `versionCode-versionName`, for example `133-5.0.1`. Updates are detected by
comparing the version code and then the version name, the same rule the LSPosed manager uses.

The official source ships the same three hosts as the LSPosed manager
(`modules.lsposed.org`, `modules-blogcdn.lsposed.org`, `modules-cloudflare.lsposed.org`). The
first one that answers is remembered for the session, so a blocked primary endpoint is not
retried on every refresh or module page.

Older custom repositories that expose a single `<base>/modules.json` array are still supported;
`RepoFormat.AUTO` tries the paginated API first and falls back to that document. To add a
repository, open the source chip on the Modules screen (or Settings > Repository) and choose
**Add repository**. The URL is validated before it is saved. A modern repository looks like:

```text
https://example.com/modules/
  module-list/
    1.json
    2.json
  module/
    com.example.module.json
```

Local HTTP is allowed only for loopback hosts (`127.0.0.1`, `localhost`, `10.0.2.2`), which is
useful with `adb reverse` while developing.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires JDK 21 and Android SDK 37 (compileSdk 37). Dependencies come from Google Maven and Maven
Central; no other setup is needed. The unit tests use a local `MockWebServer` and cover
pagination, host failover, endpoint stickiness, the legacy `modules.json` fallback and the
signed-URL to GitHub asset download fallback.

## Structure

```text
app/src/main/java/com/rushi/modulesmanager/
  AppViewModel.kt            Shared state: source, updates, downloads, navigation detail
  data/Models.kt             Repository models, JSON parsing, channels and version matching
  data/RepoApi.kt            Paginated module-list fetching, host failover and downloads
  data/RepoRepository.kt     In-memory list plus per-source on-disk cache and failover
  data/Settings.kt           Channel, source and sort preferences
  data/InstalledModules.kt   Xposed module detection through PackageManager metadata
  data/ApkInstaller.kt       PackageInstaller session and result handling
  data/ApkInspector.kt       Package/signature verification of downloaded APKs
  ui/                        Compose screens and components
```

## Notes

- The official list currently has 41 pages of 30 modules. A refresh fetches them in parallel and
  caches the merged result, so a cold start is the only time the full list is downloaded.
- The official repository can be unreachable from some networks. When that happens the app shows
  the error, keeps showing cached data if it has any, and lets you switch to a mirror or custom
  source.
- Module APK links are signed `assets.lsposed.org` URLs. If such a link stops working, the
  downloader retries the `originalDownloadUrl` from GitHub.
- Installing and updating requires the system "install unknown apps" permission. Updates signed
  with a different key than the installed app are rejected before the installer opens.
- Modules Manager does not check module safety beyond package identity and signing keys. Treat
  modules from third-party repositories like any other sideloaded APK.
- The Installed tab is scoped to the selected repository. Modules installed from other sources
  are not listed there, which keeps its count aligned with the repository filters.

package com.rushi.modulesmanager.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class UpdateChannel(val key: String, val label: String, val description: String) {
    STABLE("stable", "Stable", "Released versions only"),
    BETA("beta", "Beta", "Pre-releases included, snapshots skipped"),
    SNAPSHOT("snapshot", "Snapshot", "Newest builds, including snapshots");

    companion object {
        fun fromKey(key: String?): UpdateChannel = entries.firstOrNull { it.key == key } ?: STABLE
    }
}

enum class SortMode(val key: String, val label: String) {
    NAME("name", "Name"),
    UPDATED("updated", "Last updated");

    companion object {
        fun fromKey(key: String?): SortMode = entries.firstOrNull { it.key == key } ?: NAME
    }
}

data class RepoAsset(
    val name: String,
    val contentType: String?,
    val downloadUrl: String,
    val downloadCount: Int,
    val size: Long,
    /** GitHub URL used when the signed assets.lsposed.org URL stops working. */
    val originalDownloadUrl: String? = null,
)

data class RepoRelease(
    val name: String?,
    val tagName: String?,
    val url: String?,
    val descriptionHtml: String?,
    val publishedAt: String?,
    val isPrerelease: Boolean,
    val assets: List<RepoAsset>,
) {
    val apkAssets: List<RepoAsset>
        get() = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

    val isSnapshot: Boolean
        get() {
            val value = (name ?: tagName ?: "").lowercase(Locale.ROOT)
            return value.startsWith("snapshot") || value.startsWith("nightly")
        }

    fun supports(channel: UpdateChannel): Boolean = when (channel) {
        UpdateChannel.STABLE -> !isPrerelease && !isSnapshot
        UpdateChannel.BETA -> !isSnapshot
        UpdateChannel.SNAPSHOT -> true
    }
}

data class RepoCollaborator(val login: String, val name: String?)

data class RepoModule(
    val packageName: String,
    val displayName: String,
    val summary: String?,
    val url: String?,
    val homepageUrl: String?,
    val sourceUrl: String?,
    val latestRelease: String?,
    val latestReleaseTime: String?,
    val latestBetaRelease: String?,
    val latestBetaReleaseTime: String?,
    val latestSnapshotRelease: String?,
    val latestSnapshotReleaseTime: String?,
    val releases: List<RepoRelease>,
    val betaReleases: List<RepoRelease>,
    val snapshotReleases: List<RepoRelease>,
    val readme: String?,
    val readmeHtml: String?,
    val collaborators: List<RepoCollaborator>,
    val scope: List<String>,
    val hide: Boolean,
    val updatedAt: String?,
    val stargazerCount: Int,
) {
    val hasReleases: Boolean
        get() = releases.isNotEmpty() || betaReleases.isNotEmpty() || snapshotReleases.isNotEmpty()

    fun latest(channel: UpdateChannel): ModuleVersion? = when (channel) {
        UpdateChannel.STABLE -> ModuleVersion.parse(latestRelease)
        UpdateChannel.BETA -> ModuleVersion.parse(latestBetaRelease) ?: ModuleVersion.parse(latestRelease)
        UpdateChannel.SNAPSHOT -> ModuleVersion.parse(latestSnapshotRelease)
            ?: ModuleVersion.parse(latestBetaRelease)
            ?: ModuleVersion.parse(latestRelease)
    }

    fun latestTime(channel: UpdateChannel): String? = when (channel) {
        UpdateChannel.STABLE -> latestReleaseTime
        UpdateChannel.BETA -> latestBetaReleaseTime ?: latestReleaseTime
        UpdateChannel.SNAPSHOT -> latestSnapshotReleaseTime ?: latestBetaReleaseTime ?: latestReleaseTime
    }

    fun releasesFor(channel: UpdateChannel): List<RepoRelease> {
        val pool = when (channel) {
            UpdateChannel.STABLE -> releases
            UpdateChannel.BETA -> betaReleases.ifEmpty { releases }
            UpdateChannel.SNAPSHOT -> snapshotReleases.ifEmpty { betaReleases.ifEmpty { releases } }
        }
        val filtered = pool.filter { it.supports(channel) }
        return filtered.ifEmpty { pool }
    }

    fun releaseFor(channel: UpdateChannel): RepoRelease? {
        val pool = releasesFor(channel)
        return pool.firstOrNull { it.apkAssets.isNotEmpty() } ?: pool.firstOrNull()
    }

    /** GitHub owner of the module, used for its avatar. */
    fun avatarUrl(): String? {
        val candidate = sourceUrl ?: url ?: homepageUrl ?: return null
        val owner = Regex("github\\.com/([^/?#]+)").find(candidate)?.groupValues?.getOrNull(1) ?: return null
        // These accounts host many unrelated modules, so their logo would repeat on every row.
        if (owner.lowercase() in setOf("releases", "apps", "xposed-modules-repo", "lsposed")) return null
        return "https://github.com/$owner.png?size=192"
    }
}

data class ModuleVersion(val code: Long, val name: String) {
    fun isNewerThan(installedCode: Long, installedName: String?): Boolean =
        code > installedCode || (code == installedCode && installedName?.replace(' ', '_') != name)

    companion object {
        /** Repository versions are formatted as "versionCode-versionName". */
        fun parse(value: String?): ModuleVersion? {
            if (value.isNullOrBlank()) return null
            val parts = value.split('-', limit = 2)
            if (parts.size < 2) return null
            val code = parts[0].toLongOrNull() ?: return null
            return ModuleVersion(code, parts[1])
        }
    }
}

object RepoJson {
    fun parseModules(json: String): List<RepoModule> = parseModules(JSONArray(json))

    fun parseModules(array: JSONArray): List<RepoModule> {
        val modules = ArrayList<RepoModule>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            runCatching { parseModule(obj) }.getOrNull()?.let(modules::add)
        }
        return modules
    }

    fun parseModule(json: String): RepoModule = parseModule(JSONObject(json))

    private fun parseModule(obj: JSONObject): RepoModule {
        val packageName = obj.optString("name").takeIf { it.isNotBlank() } ?: error("module has no name")
        val description = obj.optString("description").takeIf { it.isNotBlank() && it != "null" }
        return RepoModule(
            packageName = packageName,
            displayName = description ?: packageName,
            summary = obj.optString("summary").takeIf { it.isNotBlank() && it != "null" },
            url = obj.optString("url").takeIf { it.isNotBlank() && it != "null" },
            homepageUrl = obj.optString("homepageUrl").takeIf { it.isNotBlank() && it != "null" },
            sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" },
            latestRelease = obj.optString("latestRelease").takeIf { it.isNotBlank() && it != "null" },
            latestReleaseTime = obj.optString("latestReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            latestBetaRelease = obj.optString("latestBetaRelease").takeIf { it.isNotBlank() && it != "null" },
            latestBetaReleaseTime = obj.optString("latestBetaReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            latestSnapshotRelease = obj.optString("latestSnapshotRelease").takeIf { it.isNotBlank() && it != "null" },
            latestSnapshotReleaseTime = obj.optString("latestSnapshotReleaseTime").takeIf { it.isNotBlank() && it != "null" },
            releases = parseReleases(obj.optJSONArray("releases")),
            betaReleases = parseReleases(obj.optJSONArray("betaReleases")),
            snapshotReleases = parseReleases(obj.optJSONArray("snapshotReleases")),
            readme = obj.optString("readme").takeIf { it.isNotBlank() && it != "null" },
            readmeHtml = obj.optString("readmeHTML").takeIf { it.isNotBlank() && it != "null" },
            collaborators = parseCollaborators(obj.optJSONArray("collaborators")),
            scope = parseStrings(obj.optJSONArray("scope")),
            hide = obj.optBoolean("hide", false),
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" },
            stargazerCount = obj.optInt("stargazerCount", 0),
        )
    }

    private fun parseReleases(array: JSONArray?): List<RepoRelease> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val assets = obj.optJSONArray("releaseAssets")?.let { items ->
                    buildList(items.length()) {
                        for (i in 0 until items.length()) {
                            val asset = items.optJSONObject(i) ?: continue
                            val url = asset.optString("downloadUrl")
                            if (url.isBlank()) continue
                            add(
                                RepoAsset(
                                    name = asset.optString("name").ifBlank { "download" },
                                    contentType = asset.optString("contentType").takeIf { it.isNotBlank() },
                                    downloadUrl = url,
                                    downloadCount = asset.optInt("downloadCount", 0),
                                    size = asset.optLong("size", 0L),
                                    originalDownloadUrl = asset.optString("originalDownloadUrl")
                                        .takeIf { it.isNotBlank() && it != "null" },
                                ),
                            )
                        }
                    }
                } ?: emptyList()

                add(
                    RepoRelease(
                        name = obj.optString("name").takeIf { it.isNotBlank() && it != "null" },
                        tagName = obj.optString("tagName").takeIf { it.isNotBlank() && it != "null" },
                        url = obj.optString("url").takeIf { it.isNotBlank() && it != "null" },
                        descriptionHtml = obj.optString("descriptionHTML").takeIf { it.isNotBlank() && it != "null" },
                        publishedAt = (obj.optString("publishedAt").takeIf { it.isNotBlank() && it != "null" })
                            ?: (obj.optString("createdAt").takeIf { it.isNotBlank() && it != "null" }),
                        isPrerelease = obj.optBoolean("isPrerelease", false),
                        assets = assets,
                    ),
                )
            }
        }.sortedByDescending { parseInstant(it.publishedAt) ?: Instant.EPOCH }
    }

    private fun parseCollaborators(array: JSONArray?): List<RepoCollaborator> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val login = obj.optString("login").takeIf { it.isNotBlank() } ?: continue
                add(RepoCollaborator(login, obj.optString("name").takeIf { it.isNotBlank() && it != "null" }))
            }
        }
    }

    private fun parseStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
}

fun formatDate(value: String?): String? {
    val instant = parseInstant(value) ?: return null
    return runCatching {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrNull()
}

fun formatRelativeDate(value: String?): String? {
    val instant = parseInstant(value) ?: return null
    val seconds = (System.currentTimeMillis() - instant.toEpochMilli()) / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        seconds < 86_400 * 30 -> "${seconds / 86_400} d ago"
        else -> formatDate(value)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "unknown size"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toInt()} ${units[unit]}" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun stripHtml(html: String?): String? {
    if (html.isNullOrBlank()) return null
    return html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
        }
        .replace(Regex("&#([0-9]+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        .replace("&nbsp;", " ")
        .replace("&mdash;", "-")
        .replace("&ndash;", "-")
        .replace("&hellip;", "...")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&ldquo;", "\"")
        .replace("&rdquo;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("\r", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

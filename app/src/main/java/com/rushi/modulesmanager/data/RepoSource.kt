package com.rushi.modulesmanager.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class RepoSource(
    val id: String,
    val name: String,
    val bases: List<String>,
    val custom: Boolean = false,
)

object DefaultSources {
    /**
     * The same repository the LSPosed manager uses, with its documented fallbacks.
     * They all serve the same modules.json, so the cache is shared per source.
     */
    val official = RepoSource(
        id = "official",
        name = "LSPosed Repository",
        bases = listOf(
            "https://modules.lsposed.org/",
            "https://modules-blogcdn.lsposed.org/",
        ),
    )
}

/** Turns user input into a repository base URL ending in a slash, or null when it is not a URL. */
fun normalizeRepoBase(input: String): String? {
    var value = input.trim()
    if (value.isEmpty()) return null
    if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) {
        value = "https://$value"
    }
    val url = value.toHttpUrlOrNull() ?: return null
    var path = url.encodedPath.trimEnd('/')
    if (path.endsWith("/modules.json")) path = path.removeSuffix("/modules.json")
    val normalizedPath = if (path.isEmpty()) "/" else "$path/"
    val builder = url.newBuilder()
        .encodedPath(normalizedPath)
        .query(null)
        .fragment(null)
    return builder.build().toString()
}

fun sourceFromBase(base: String): RepoSource {
    val url = base.toHttpUrlOrNull()
    val host = url?.host ?: base
    val defaultPort = if (url?.isHttps == true) 443 else 80
    val port = url?.port?.takeIf { it != defaultPort }
    val authority = if (port != null) "$host:$port" else host
    val path = url?.encodedPath?.trim('/').orEmpty()
    val name = if (path.isEmpty()) authority else "$authority/$path"
    return RepoSource(
        id = "custom:$base",
        name = name,
        bases = listOf(base),
        custom = true,
    )
}

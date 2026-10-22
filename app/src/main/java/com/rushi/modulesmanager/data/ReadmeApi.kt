package com.rushi.modulesmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The daily index carries only the summary. GitHub can render the README to HTML for us, so the
 * Overview tab does not have to reimplement Markdown.
 */
class ReadmeApi(private val client: OkHttpClient) {
    suspend fun fetchHtml(repoFullName: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoFullName/readme")
            .header("User-Agent", RepoApi.USER_AGENT)
            .header("Accept", "application/vnd.github.html+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun fetchUiStrings(repoFullName: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/$repoFullName/HEAD/app/src/main/res/values/strings.xml")
            .header("User-Agent", RepoApi.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            Regex("<string[^>]*>([^<]{3,})</string>")
                .findAll(response.body?.string().orEmpty())
                .joinToString(" | ") { it.groupValues[1] }
                .takeIf { it.isNotBlank() }
        }
    }
}

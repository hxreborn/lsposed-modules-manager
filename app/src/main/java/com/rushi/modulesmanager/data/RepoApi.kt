package com.rushi.modulesmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RepoApi(private val client: OkHttpClient) {

    data class FetchedModules(val rawJson: String, val modules: List<RepoModule>, val baseUrl: String)

    suspend fun fetchModules(bases: List<String>): FetchedModules {
        val (raw, base) = fetchFirst(bases, "modules.json")
        return FetchedModules(raw, RepoJson.parseModules(raw), base)
    }

    suspend fun fetchModule(bases: List<String>, packageName: String): RepoModule {
        val (raw, _) = fetchFirst(bases, "module/$packageName.json")
        return RepoJson.parseModule(raw)
    }

    private suspend fun fetchFirst(bases: List<String>, path: String): Pair<String, String> {
        var lastError: Exception? = null
        for (base in bases) {
            val url = base.trimEnd('/') + "/" + path
            try {
                return fetch(url) to base
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No repository URL configured")
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    /**
     * Streams [url] into [destination], reporting bytes written. The file only appears at its
     * final path once the download completed, so a cancelled download never looks installable.
     */
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".part")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Download failed: empty response")
            val total = body.contentLength()
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport >= 128 * 1024 || (total > 0 && downloaded == total)) {
                            lastReport = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    onProgress(downloaded, if (total > 0) total else downloaded)
                }
            }
        }

        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        destination
    }

    companion object {
        const val USER_AGENT = "ModulesManager/1.0 (Android)"

        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }
}

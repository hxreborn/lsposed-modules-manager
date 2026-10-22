package com.rushi.modulesmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

enum class AiProvider(
    val key: String,
    val label: String,
    val endpoint: String,
    val model: String,
    val keyHint: String,
) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-flash", "sk-..."),
    GEMINI(
        "gemini",
        "Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "gemini-flash-lite-latest",
        "AIza...",
    );

    companion object {
        fun fromKey(key: String?): AiProvider = entries.firstOrNull { it.key == key } ?: DEEPSEEK
    }
}

class TldrApi(
    private val client: OkHttpClient,
    private val endpointFor: (AiProvider) -> String = AiProvider::endpoint,
) {
    suspend fun summarize(
        provider: AiProvider,
        apiKey: String,
        locale: Locale,
        prompt: String,
        module: RepoModule,
        readmeHtml: String?,
        uiStrings: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val facts = JSONObject()
            .put("name", module.displayName)
            .put("package", module.packageName)
            .put("description", module.summary.orEmpty())
            .put("declared_scope", JSONArray(module.scope))
            .put("readme", readmeText(readmeHtml))
            .apply { if (!uiStrings.isNullOrBlank()) put("ui_strings", uiStrings.take(MAX_FIELD_CHARS)) }
        val language = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        val body = JSONObject()
            .put("model", provider.model)
            .put("max_tokens", MAX_TOKENS)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", prompt.replace(LANGUAGE_TOKEN, language)))
                    .put(JSONObject().put("role", "user").put("content", facts.toString())),
            )
        val request = Request.Builder()
            .url(endpointFor(provider))
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> throw IOException("${provider.label} rejected the API key")
                response.code == 402 -> throw IOException("The ${provider.label} account has no balance")
                response.code == 429 -> throw IOException("${provider.label} rate limit reached, try again later")
                !response.isSuccessful -> throw IOException("${provider.label} returned HTTP ${response.code}")
            }
            val content = JSONObject(response.body?.string().orEmpty())
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .lines()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
            when {
                content.isNullOrEmpty() -> throw IOException("${provider.label} returned an empty summary")
                content.trimEnd('.').equals(NONE_REPLY, ignoreCase = true) -> NO_DETAILS
                else -> content
            }
        }
    }

    private fun readmeText(html: String?): String =
        stripHtml(
            html.orEmpty()
                .replace(Regex("<pre[\\s\\S]*?</pre>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<(img|svg)[\\s\\S]*?>", RegexOption.IGNORE_CASE), " "),
        ).orEmpty()
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_FIELD_CHARS)

    companion object {
        const val LANGUAGE_TOKEN = "{language}"

        const val NO_DETAILS = "No details published for this module"

        val DEFAULT_PROMPT = """
            You summarize an Android Xposed/LSPosed module for a list row in a module browser.
            The input is JSON: name, package, description, declared_scope, readme and sometimes ui_strings (labels from the module's settings screen). The readme is the most informative field; the description is often only the module name.
            Output exactly one sentence in {language} saying which app the module modifies and the two or three main things it adds or changes.
            - Name apps by their common name (Douyin, WeChat, MIUI Security), never by package name.
            - Ignore version numbers, changelogs, test results, signing, build and installation notes.
            - At most 100 characters. Plain text only: no markdown, emoji, quotes or preamble.
            - Never comment on the input or say information is missing. If the facts truly do not say what the module does, output only the word NONE.
            Examples:
            Adds text-to-speech, auto-reply and comment tools to Douyin.
            Tunes MIUI Security charging and power policies with night and fast-charge modes.
        """.trimIndent()

        private const val NONE_REPLY = "NONE"
        private const val MAX_TOKENS = 4096
        private const val MAX_FIELD_CHARS = 2500
    }
}

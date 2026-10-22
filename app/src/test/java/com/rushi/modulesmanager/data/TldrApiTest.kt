package com.rushi.modulesmanager.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

class TldrApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = TldrApi(RepoApi.createClient()) { server.url("/chat/completions").toString() }

    private val module = RepoModule(
        packageName = "io.github.superhide.viptry",
        displayName = "Viptry",
        summary = "黄金超隐：隐私保护工具",
        url = null,
        homepageUrl = null,
        sourceUrl = null,
        latestRelease = "v20261022",
        latestReleaseTime = null,
        latestBetaRelease = null,
        latestBetaReleaseTime = null,
        latestSnapshotRelease = null,
        latestSnapshotReleaseTime = null,
        releases = emptyList(),
        betaReleases = emptyList(),
        snapshotReleases = emptyList(),
        readme = null,
        readmeHtml = null,
        collaborators = emptyList(),
        scope = listOf("android"),
        hide = false,
        updatedAt = null,
        stargazerCount = 0,
    )

    @Test
    fun `sends the facts and returns the first line of the answer`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"\nSpoofs device info and GPS for privacy.\nextra"}}]}""",
            ),
        )

        val text = api().summarize(AiProvider.DEEPSEEK, "sk-test", Locale.ENGLISH, TldrApi.DEFAULT_PROMPT, module, "<p>readme <img src=\"badge.svg\"> body</p><pre>code</pre> https://x.y/z")

        assertEquals("Spoofs device info and GPS for privacy.", text)
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        assertEquals("deepseek-flash", JSONObject(request.body.clone().readUtf8()).getString("model"))
        val body = JSONObject(request.body.readUtf8())
        val messages = body.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("sentence in English saying"))
        val facts = JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals("io.github.superhide.viptry", facts.getString("package"))
        assertEquals("readme body", facts.getString("readme"))
    }

    @Test
    fun `asks for the device language`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"隐私保护模块"}}]}"""))

        api().summarize(AiProvider.DEEPSEEK, "sk-test", Locale.SIMPLIFIED_CHINESE, TldrApi.DEFAULT_PROMPT, module, null)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("sentence in Chinese saying"))
    }

    @Test
    fun `reports a rejected key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            api().summarize(AiProvider.DEEPSEEK, "bad", Locale.ENGLISH, TldrApi.DEFAULT_PROMPT, module, null)
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("DeepSeek rejected the API key", e.message)
        }
    }

    @Test
    fun `maps a NONE reply to the no details text and sends ui strings`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"None."}}]}"""))

        val text = api().summarize(AiProvider.DEEPSEEK, "sk-test", Locale.ENGLISH, TldrApi.DEFAULT_PROMPT, module, null, "Hide ads | Remove splash")

        assertEquals(TldrApi.NO_DETAILS, text)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val facts = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals("Hide ads | Remove splash", facts.getString("ui_strings"))
    }

    @Test
    fun `uses the selected provider model and label`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))

        try {
            api().summarize(AiProvider.GEMINI, "AIza-test", Locale.ENGLISH, TldrApi.DEFAULT_PROMPT, module, null)
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("Gemini returned HTTP 400", e.message)
        }
        assertEquals("gemini-flash-lite-latest", JSONObject(server.takeRequest().body.readUtf8()).getString("model"))
    }
}

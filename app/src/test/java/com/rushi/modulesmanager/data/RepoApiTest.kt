package com.rushi.modulesmanager.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RepoApiTest {

    private lateinit var primary: MockWebServer
    private lateinit var backup: MockWebServer

    @Before
    fun setUp() {
        primary = MockWebServer().apply { start() }
        backup = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        primary.shutdown()
        backup.shutdown()
    }

    private fun source(
        format: RepoFormat = RepoFormat.MODERN,
        bases: List<String> = listOf(primary.url("/").toString(), backup.url("/").toString()),
    ) = RepoSource(
        id = "test-source",
        name = "Test Repository",
        bases = bases,
        custom = true,
        format = format,
    )

    private fun api() = RepoApi(RepoApi.createClient())

    @Test
    fun `loads every page of the current API`() = runBlocking {
        primary.enqueue(MockResponse().setBody(PAGE_1))
        primary.enqueue(MockResponse().setBody(PAGE_2))

        val result = api().fetchModules(source())

        assertEquals(3, result.modules.size)
        assertEquals(
            listOf("com.example.one", "com.example.two", "com.example.three"),
            result.modules.map { it.packageName },
        )
        assertEquals(2, primary.requestCount)
        assertEquals("/module-list/1.json", primary.takeRequest().path)
        assertEquals("/module-list/2.json", primary.takeRequest().path)
    }

    @Test
    fun `falls back to the backup host when the first endpoint returns an error`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(503))
        backup.enqueue(MockResponse().setBody(PAGE_1_SINGLE))

        val result = api().fetchModules(source())

        assertEquals(backup.url("/").toString(), result.baseUrl)
        assertEquals("com.example.one", result.modules.single().packageName)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun `remembers the endpoint that worked`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        backup.enqueue(MockResponse().setBody(PAGE_1_SINGLE))
        val api = api()
        api.fetchModules(source())

        // Both endpoints are healthy now, but the remembered one should be asked first.
        backup.enqueue(MockResponse().setBody(PAGE_1_SINGLE))
        val second = api.fetchModules(source())

        assertEquals(backup.url("/").toString(), second.baseUrl)
        assertEquals(1, primary.requestCount)
        assertEquals(2, backup.requestCount)
    }

    @Test
    fun `uses legacy modules json when the paginated API is missing`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(404))
        primary.enqueue(MockResponse().setBody(LEGACY_JSON))

        val result = api().fetchModules(
            source(format = RepoFormat.AUTO, bases = listOf(primary.url("/").toString())),
        )

        assertEquals("com.example.legacy", result.modules.single().packageName)
        assertEquals(2, primary.requestCount)
        assertEquals("/module-list/1.json", primary.takeRequest().path)
        assertEquals("/modules.json", primary.takeRequest().path)
    }

    @Test
    fun `fetches module details from the backup endpoint`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        backup.enqueue(MockResponse().setBody(MODULE_JSON))

        val module = api().fetchModule(source(), "com.example.module")

        assertEquals("com.example.module", module.packageName)
        assertEquals("42-4.2", module.latestRelease)
        assertEquals(1, module.releases.single().apkAssets.size)
        assertEquals(
            "https://github.com/example/module/releases/download/v4.2/example.apk",
            module.releases.single().apkAssets.single().originalDownloadUrl,
        )
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun `reports every failed endpoint`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        backup.enqueue(MockResponse().setResponseCode(502))

        val error = try {
            api().fetchModules(source())
            null
        } catch (e: RepositoryUnavailableException) {
            e
        }

        assertNotNull(error)
        assertEquals(2, error!!.failures.size)
        assertTrue(error.userMessage().contains("2 endpoints"))
        assertTrue(error.userMessage().contains("HTTP 500"))
    }

    @Test
    fun `download falls back to the original asset URL`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(403))
        primary.enqueue(MockResponse().setBody("apk-bytes"))
        val directory = Files.createTempDirectory("modules-manager-download").toFile()
        val destination = File(directory, "example.apk")
        val signed = primary.url("/signed.apk").toString()
        val original = primary.url("/original.apk").toString()

        val file = api().download(listOf(signed, original), destination) { _, _ -> }

        assertEquals("apk-bytes", file.readText())
        assertEquals(2, primary.requestCount)
        assertEquals("/signed.apk", primary.takeRequest().path)
        assertEquals("/original.apk", primary.takeRequest().path)
        directory.deleteRecursively()
        Unit
    }

    private companion object {
        val PAGE_1_SINGLE = """
            {
              "modules": [
                {
                  "name": "com.example.one",
                  "description": "Example One",
                  "summary": "The first example",
                  "latestRelease": "1-1.0",
                  "latestReleaseTime": "2026-01-02T03:04:05Z"
                }
              ],
              "page": 1,
              "pageCount": 1,
              "total": 1
            }
        """.trimIndent()

        val PAGE_1 = """
            {
              "modules": [
                {
                  "name": "com.example.one",
                  "description": "Example One",
                  "summary": "The first example",
                  "latestRelease": "1-1.0",
                  "latestReleaseTime": "2026-01-02T03:04:05Z"
                },
                {
                  "name": "com.example.two",
                  "description": "Example Two",
                  "latestRelease": "2-2.0",
                  "latestReleaseTime": "2026-01-03T03:04:05Z"
                }
              ],
              "page": 1,
              "pageCount": 2,
              "total": 3
            }
        """.trimIndent()

        val PAGE_2 = """
            {
              "modules": [
                {
                  "name": "com.example.three",
                  "description": "Example Three",
                  "latestRelease": "3-3.0",
                  "latestReleaseTime": "2026-01-04T03:04:05Z"
                }
              ],
              "page": 2,
              "pageCount": 2,
              "total": 3
            }
        """.trimIndent()

        val LEGACY_JSON = """
            [
              {
                "name": "com.example.legacy",
                "description": "Legacy Repository",
                "latestRelease": "7-7.0"
              }
            ]
        """.trimIndent()

        val MODULE_JSON = """
            {
              "name": "com.example.module",
              "description": "Example Module",
              "summary": "An example",
              "latestRelease": "42-4.2",
              "readme": "Hello",
              "releases": [
                {
                  "name": "4.2",
                  "tagName": "v4.2",
                  "publishedAt": "2026-01-02T03:04:05Z",
                  "releaseAssets": [
                    {
                      "name": "example.apk",
                      "downloadUrl": "https://assets.example.com/example.apk?sign=abc",
                      "originalDownloadUrl": "https://github.com/example/module/releases/download/v4.2/example.apk",
                      "size": 1234
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}

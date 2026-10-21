package com.rushi.modulesmanager.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LiveReleaseApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses the latest release and its apk asset`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "tag_name": "521-3.8.4",
                  "name": "3.8.4",
                  "published_at": "2026-02-03T04:05:06Z",
                  "html_url": "https://github.com/example/module/releases/tag/521-3.8.4",
                  "prerelease": false,
                  "body": "Changes",
                  "assets": [
                    {
                      "name": "example.apk",
                      "content_type": "application/vnd.android.package-archive",
                      "browser_download_url": "https://github.com/example/module/releases/download/521-3.8.4/example.apk",
                      "download_count": 9,
                      "size": 1234
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val base = server.url("/").toString().trimEnd('/')
        val liveApi = LiveReleaseApi(RepoApi.createClient(), base)

        val release = liveApi.fetchLatest("example/module")

        assertEquals("521-3.8.4", release?.tag)
        assertEquals("3.8.4", release?.name)
        assertEquals(1, release?.assets?.size)
        assertEquals("example.apk", release?.assets?.single()?.name)
        assertEquals(9, release?.assets?.single()?.downloadCount)
        assertEquals("/repos/example/module/releases/latest", server.takeRequest().path)
    }
}

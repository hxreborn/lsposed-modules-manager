package com.rushi.modulesmanager.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoJsonTest {

    @Test
    fun `finds packages that have no release in the daily index`() {
        val packages = RepoJson.packagesWithoutReleases(INDEX)

        assertTrue(packages.contains("com.example.missing"))
        assertFalse(packages.contains("com.example.ready"))
    }

    private companion object {
        val INDEX = """
            [
              {
                "full_name": "example/ready",
                "package": "com.example.ready",
                "release": {
                  "tag": "1-1.0",
                  "apk_url": "https://github.com/example/ready/releases/download/1-1.0/ready.apk"
                }
              },
              {
                "full_name": "example/missing",
                "org_repo": "Xposed-Modules-Repo/com.example.missing",
                "release": null
              }
            ]
        """.trimIndent()
    }
}

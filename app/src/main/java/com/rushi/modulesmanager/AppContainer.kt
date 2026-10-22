package com.rushi.modulesmanager

import android.app.Application
import android.content.Context
import com.rushi.modulesmanager.data.DownloadCenter
import com.rushi.modulesmanager.data.InstalledModuleScanner
import com.rushi.modulesmanager.data.LiveReleaseApi
import com.rushi.modulesmanager.data.ReadmeApi
import com.rushi.modulesmanager.data.RepoApi
import com.rushi.modulesmanager.data.RepoRepository
import com.rushi.modulesmanager.data.Settings
import com.rushi.modulesmanager.data.TldrApi

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = Settings(appContext)
    val api = RepoApi(RepoApi.createClient())
    private val githubClient = RepoApi.createClient().newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val token = settings.githubToken.value
            if (token.isBlank() || request.url.host != "api.github.com") {
                chain.proceed(request)
            } else {
                val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
                if (response.code == 401) {
                    response.close()
                    chain.proceed(request)
                } else {
                    response
                }
            }
        }
        .build()
    val liveApi = LiveReleaseApi(githubClient)
    val readmeApi = ReadmeApi(githubClient)
    val tldrApi = TldrApi(RepoApi.createClient())
    val repo = RepoRepository(appContext, api)
    val scanner = InstalledModuleScanner(appContext)
    val downloads = DownloadCenter()
}

class ModulesManagerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

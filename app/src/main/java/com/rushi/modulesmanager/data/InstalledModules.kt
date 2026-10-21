package com.rushi.modulesmanager.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle

data class InstalledModule(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val description: String?,
    val scope: List<String>,
    val icon: Drawable?,
    val hasLauncher: Boolean,
)

class InstalledModuleScanner(private val context: Context) {

    fun scan(): List<InstalledModule> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val result = ArrayList<InstalledModule>()
        for (info in packages) {
            if (info.packageName == context.packageName) continue
            val app = info.applicationInfo ?: continue
            val meta = app.metaData ?: continue
            val declared = meta.getBoolean("xposedmodule", false) || meta.containsKey("xposedminversion")
            if (!declared) continue
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            result += InstalledModule(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName),
                versionName = info.versionName,
                versionCode = versionCode,
                description = meta.getString("xposeddescription"),
                scope = readScope(meta),
                icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                hasLauncher = pm.getLaunchIntentForPackage(info.packageName) != null,
            )
        }
        return result.sortedBy { it.label.lowercase() }
    }

    private fun readScope(meta: Bundle): List<String> = when (val value = meta.get("xposedscope")) {
        is Array<*> -> value.filterIsInstance<String>()
        is String -> value.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        is ArrayList<*> -> value.filterIsInstance<String>()
        else -> emptyList()
    }

    fun openManager(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openModule(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        private const val MANAGER_PACKAGE = "org.lsposed.manager"
    }
}

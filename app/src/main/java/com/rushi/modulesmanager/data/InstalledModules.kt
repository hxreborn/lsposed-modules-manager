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

/** Version data for every installed package, used to match repository modules. */
data class InstalledPackageVersion(
    val versionName: String?,
    val versionCode: Long,
)

data class InstalledModulesResult(
    val modules: List<InstalledModule>,
    val versions: Map<String, InstalledPackageVersion>,
)

class InstalledModuleScanner(private val context: Context) {

    fun scan(): InstalledModulesResult {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val modules = ArrayList<InstalledModule>()
        val versions = HashMap<String, InstalledPackageVersion>(packages.size)
        for (info in packages) {
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            versions[info.packageName] = InstalledPackageVersion(info.versionName, versionCode)

            if (info.packageName == context.packageName) continue
            val app = info.applicationInfo ?: continue
            val meta = app.metaData ?: continue
            val declared = meta.getBoolean("xposedmodule", false) || meta.containsKey("xposedminversion")
            if (!declared) continue
            modules += InstalledModule(
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
        return InstalledModulesResult(
            modules = modules.sortedBy { it.label.lowercase() },
            versions = versions,
        )
    }

    private fun readScope(meta: Bundle): List<String> = when (val value = meta.get("xposedscope")) {
        is Array<*> -> value.filterIsInstance<String>()
        is String -> value.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        is ArrayList<*> -> value.filterIsInstance<String>()
        else -> emptyList()
    }

    fun openModule(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }


}

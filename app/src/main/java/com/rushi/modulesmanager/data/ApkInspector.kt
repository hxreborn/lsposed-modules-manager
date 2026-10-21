package com.rushi.modulesmanager.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkInspector {

    data class ApkInfo(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val label: String?,
        val signatureDigest: String?,
    )

    fun inspect(context: Context, file: File): ApkInfo? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.absolutePath, archiveFlags()) ?: return null
        val app = info.applicationInfo
        if (app != null) {
            app.sourceDir = file.absolutePath
            app.publicSourceDir = file.absolutePath
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return ApkInfo(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() },
            signatureDigest = signatureDigest(info),
        )
    }

    /** Digest of the signing certificate of an installed package, or null when it is not installed. */
    fun installedSignatureDigest(context: Context, packageName: String): String? {
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, archiveFlags())
            signatureDigest(info)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun archiveFlags(): Int {
        @Suppress("DEPRECATION")
        return PackageManager.GET_META_DATA or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
    }

    private fun signatureDigest(info: PackageInfo): String? {
        val signature: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        }
        val bytes = signature?.toByteArray() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

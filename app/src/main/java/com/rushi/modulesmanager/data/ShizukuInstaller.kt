package com.rushi.modulesmanager.data

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object ShizukuInstaller {
    private const val PERMISSION_REQUEST_CODE = 0x5348
    private const val PERMISSION_TIMEOUT_MS = 60_000L
    private const val INSTALL_TIMEOUT_SECONDS = 120L

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isGranted(): Boolean = isRunning() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun ensurePermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return withTimeoutOrNull(PERMISSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: Shizuku.OnRequestPermissionResultListener
                listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                    if (requestCode == PERMISSION_REQUEST_CODE) {
                        Shizuku.removeRequestPermissionResultListener(listener)
                        continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                continuation.invokeOnCancellation {
                    Shizuku.removeRequestPermissionResultListener(listener)
                }
                try {
                    Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                } catch (_: Throwable) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(false)
                }
            }
        } ?: false
    }

    /** Streams the APK into `pm install -S`, so the shell user never needs access to app files. */
    suspend fun install(apk: File): String? = withContext(Dispatchers.IO) {
        val size = apk.length()
        if (size <= 0L) return@withContext "APK is empty"

        val process = try {
            val binder = Shizuku.getBinder() ?: return@withContext "Shizuku is not running"
            RemoteProcess(
                IShizukuService.Stub.asInterface(binder).newProcess(
                    arrayOf("pm", "install", "-r", "-t", "-S", size.toString()),
                    null,
                    null,
                ),
            )
        } catch (t: Throwable) {
            return@withContext t.message ?: "Could not start the Shizuku install"
        }

        try {
            apk.inputStream().use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            if (!process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return@withContext "The Shizuku install timed out"
            }
            val output = (stdout + "\n" + stderr).trim()
            val exitCode = process.exitValue()
            if (exitCode == 0 && output.contains("Success", ignoreCase = true)) {
                null
            } else {
                output.ifBlank { "Shizuku install failed (exit $exitCode)" }
            }
        } catch (t: Throwable) {
            t.message ?: "Shizuku install failed"
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
            remote.waitForTimeout(timeout, unit.toString())

        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }
}

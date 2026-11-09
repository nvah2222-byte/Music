package com.example.music.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Информация о новом релизе на GitHub. */
data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String
)

sealed interface UpdateCheck {
    data class Available(val info: UpdateInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * Проверка обновлений через GitHub Releases.
 * APK должен быть прикреплён к последнему релизу, а версия — в теге (v1.2.3).
 */
object UpdateChecker {

    suspend fun check(context: Context, repo: String): UpdateCheck = withContext(Dispatchers.IO) {
        if (repo.isBlank() || !repo.contains('/')) return@withContext UpdateCheck.Failed
        try {
            val connection = (URL("https://api.github.com/repos/$repo/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Music-Android")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateCheck.Failed
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name")
                val version = tag.trim().removePrefix("v")
                val assets = json.optJSONArray("assets") ?: return@withContext UpdateCheck.Failed
                var apkUrl: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val url = apkUrl?.takeIf { it.isNotBlank() } ?: return@withContext UpdateCheck.Failed
                if (!isNewer(version, currentVersion(context))) return@withContext UpdateCheck.UpToDate
                UpdateCheck.Available(
                    UpdateInfo(
                        tag = tag,
                        version = version,
                        notes = json.optString("body"),
                        apkUrl = url
                    )
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            UpdateCheck.Failed
        }
    }

    /** Скачивает APK обновления в кэш приложения. */
    suspend fun download(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "update-${info.version}.apk")
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Music-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Не удалось скачать обновление")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (file.length() == 0L) error("Пустой файл обновления")
        file
    }

    fun currentVersion(context: Context): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
    }

    /** true, если [latest] новее [current]; поддерживает 1.2 / v1.2.3 / 1.2.3-beta. */
    fun isNewer(latest: String, current: String): Boolean {
        val new = parseVersion(latest) ?: return false
        val old = parseVersion(current) ?: return false
        val size = maxOf(new.size, old.size)
        for (index in 0 until size) {
            val left = new.getOrElse(index) { 0 }
            val right = old.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parseVersion(value: String): List<Int>? {
        val cleaned = value.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (cleaned.isBlank()) return null
        return cleaned.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
    }
}

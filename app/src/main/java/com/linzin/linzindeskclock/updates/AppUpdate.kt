package com.linzin.linzindeskclock.updates

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseUrl: String = "https://github.com/linzin86/LinzinDeskClock/releases"
)

/**
 * 比較兩組版本號字串 (如 1.0.1 vs 1.0.0)
 */
fun isVersionNewer(latest: String, current: String): Boolean {
    val lParts = latest.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    val cParts = current.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(lParts.size, cParts.size)
    for (i in 0 until maxLen) {
        val l = lParts.getOrElse(i) { 0 }
        val c = cParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

/**
 * 查詢 GitHub 最新 Release 版本資訊
 */
suspend fun checkForAppUpdate(currentVersionName: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val url = URL("https://api.github.com/repos/linzin86/LinzinDeskClock/releases/latest")
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            // GitHub API 規定必須提供 User-Agent，否則會回傳 403 拒絕請求
            setRequestProperty("User-Agent", "LinzinDeskClock-Android")
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val tagName = root.optString("tag_name", "")
            val htmlUrl = root.optString("html_url", "https://github.com/linzin86/LinzinDeskClock/releases")

            val cleanLatest = tagName.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")

            val isNewer = isVersionNewer(cleanLatest, cleanCurrent)

            AppUpdateInfo(
                hasUpdate = isNewer,
                latestVersion = tagName,
                releaseUrl = if (htmlUrl.isNotBlank()) htmlUrl else "https://github.com/linzin86/LinzinDeskClock/releases"
            )
        } else {
            null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.e("DeskClockUpdate", "Check update failed", e)
        null
    } finally {
        conn?.disconnect()
    }
}

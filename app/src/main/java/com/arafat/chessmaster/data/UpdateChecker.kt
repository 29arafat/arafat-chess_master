package com.arafat.chessmaster.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val tag: String, val notes: String, val url: String, val build: Int)

/** Looks for a newer GitHub Release of this app (tag format: v2.<buildNumber>). */
object UpdateChecker {
    const val REPO = "29arafat/arafat-chess_master"

    fun fetchLatest(): UpdateInfo? {
        return try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(txt)
            val tag = o.getString("tag_name")
            val build = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
            UpdateInfo(tag, o.optString("body", ""), o.optString("html_url", "https://github.com/$REPO/releases"), build)
        } catch (e: Exception) {
            null
        }
    }
}

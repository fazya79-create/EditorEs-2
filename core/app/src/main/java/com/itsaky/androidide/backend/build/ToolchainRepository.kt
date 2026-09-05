package com.itsaky.androidide.backend.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object ToolchainRepository {

    suspend fun fetchReleases(kind: ToolchainKind): List<ToolchainRelease> =
        withContext(Dispatchers.IO) {
            val api = when (kind) {
                ToolchainKind.Ndk -> ToolchainPaths.NdkReleasesApi
                ToolchainKind.CMake -> ToolchainPaths.CMakeReleasesApi
            }
            val body = readBody(URL(api))
            parse(body)
        }

    private fun parse(body: String): List<ToolchainRelease> {
        val releases = JSONArray(body)
        val result = mutableListOf<ToolchainRelease>()
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            val tag = release.optString("tag_name").takeIf { it.isNotEmpty() } ?: continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(ToolchainPaths.AssetSuffix)) continue
                val url = asset.optString("browser_download_url").takeIf { it.isNotEmpty() } ?: continue
                result += ToolchainRelease(
                    tag = tag,
                    assetName = name,
                    downloadUrl = url,
                    sizeBytes = asset.optLong("size", 0L)
                )
                break
            }
        }
        return result
    }

    private fun readBody(url: URL): String {
        var current = url
        repeat(5) {
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "AndroidIDE/1.0")
            val status = connection.responseCode
            if (status in 301..303 || status == 307 || status == 308) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location == null) throw IllegalStateException("Redirect without location")
                current = URL(current, location)
                return@repeat
            }
            if (status != 200) {
                connection.disconnect()
                throw IllegalStateException("GitHub API returned HTTP $status")
            }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        throw IllegalStateException("Too many redirects")
    }
}

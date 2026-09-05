package com.itsaky.androidide.backend.net

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class DownloadCancelledException : Exception("Download cancelled")

object ResumableDownload {

    private const val MaxAttempts = 24
    private const val BufferSize = 128 * 1024
    private const val ConnectTimeoutMs = 20000
    private const val ReadTimeoutMs = 30000

    fun partFile(target: File): File = File(target.parentFile, target.name + ".part")

    fun fetch(
        url: String,
        target: File,
        fallbackTotal: Long,
        cancelled: AtomicBoolean,
        onProgress: (Long, Long) -> Unit,
        onRetry: (Int, String) -> Unit
    ) {
        target.parentFile?.mkdirs()
        val part = partFile(target)
        var total = fallbackTotal
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MaxAttempts) {
            if (cancelled.get()) throw DownloadCancelledException()

            val existing = if (part.isFile) part.length() else 0L
            if (total > 0 && existing >= total) break

            try {
                val connection = open(url, existing)
                val status = connection.responseCode
                val resumed = status == 206

                if (existing > 0 && !resumed) part.delete()

                val declared = connection.contentLengthLong
                total = when {
                    resumed -> parseTotalFromRange(connection.getHeaderField("Content-Range"))
                        ?: (existing + declared.coerceAtLeast(0L))
                    declared > 0 -> declared
                    else -> total
                }

                val startAt = if (resumed) existing else 0L
                var received = startAt
                onProgress(received, total)

                FileOutputStream(part, resumed && existing > 0L).use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(BufferSize)
                        while (true) {
                            if (cancelled.get()) throw DownloadCancelledException()
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                    out.fd.sync()
                }
                connection.disconnect()

                if (total <= 0L || part.length() >= total) break

                attempt++
                if (attempt >= MaxAttempts) break
                onRetry(attempt, "connection closed early")
                backoff(attempt, cancelled)
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt >= MaxAttempts) break
                onRetry(attempt, e.message ?: "network error")
                backoff(attempt, cancelled)
            }
        }

        if (cancelled.get()) throw DownloadCancelledException()
        if (!part.isFile || (total > 0L && part.length() < total)) {
            throw lastError ?: IOException("Download incomplete, please retry")
        }

        target.delete()
        if (!part.renameTo(target)) throw IOException("Unable to finalize download")
    }

    private fun open(url: String, offset: Long): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = ConnectTimeoutMs
            connection.readTimeout = ReadTimeoutMs
            connection.setRequestProperty("User-Agent", "AndroidIDE/1.0")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")

            val status = connection.responseCode
            if (status in 301..303 || status == 307 || status == 308) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrEmpty()) throw IOException("Redirect without location")
                current = URL(current, location)
                return@repeat
            }
            if (status == 416) {
                connection.disconnect()
                throw IOException("Range not satisfiable")
            }
            if (status != 200 && status != 206) {
                connection.disconnect()
                throw IOException("Server returned HTTP $status")
            }
            return connection
        }
        throw IOException("Too many redirects")
    }

    private fun parseTotalFromRange(header: String?): Long? {
        val value = header ?: return null
        val slash = value.lastIndexOf('/')
        if (slash < 0) return null
        return value.substring(slash + 1).trim().toLongOrNull()
    }

    private fun backoff(attempt: Int, cancelled: AtomicBoolean) {
        val seconds = when {
            attempt <= 2 -> 1L
            attempt <= 4 -> 3L
            attempt <= 8 -> 6L
            else -> 12L
        }
        val deadline = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.get()) throw DownloadCancelledException()
            Thread.sleep(200L)
        }
    }
}

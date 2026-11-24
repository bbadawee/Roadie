package io.roadie.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Lightweight zero-dependency local Wi-Fi HTTP companion server.
 * Lets passengers on the same car hotspot send YouTube URLs straight to Roadie's queue
 * without Google Cast or Google Play Services.
 */
class CarCompanionServer(
    private val port: Int = 8765,
    private val onQueueVideoId: (String) -> Unit
) {
    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    fun start(): String? {
        if (running) return currentUrl()
        val socket = runCatching { ServerSocket(port) }.getOrNull() ?: return null
        serverSocket = socket
        running = true
        thread(isDaemon = true, name = "RoadieCompanionHttp") {
            while (running) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching { handleClient(client) }
                }
            }
        }
        return currentUrl()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun currentUrl(): String? {
        val ip = findLocalIpv4() ?: return null
        return "http://$ip:$port"
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.use { s ->
            s.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            val responseBody: String
            val statusLine: String
            if (method == "POST" && path.startsWith("/queue")) {
                val bodyChars = CharArray(contentLength.coerceIn(0, 4096))
                var read = 0
                while (read < bodyChars.size) {
                    val n = reader.read(bodyChars, read, bodyChars.size - read)
                    if (n <= 0) break
                    read += n
                }
                val rawBody = String(bodyChars, 0, read)
                val urlParam = rawBody.split("&")
                    .firstOrNull { it.startsWith("url=") }
                    ?.substringAfter("url=")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                    ?: rawBody
                val videoId = extractYouTubeVideoId(urlParam)
                if (videoId != null) {
                    onQueueVideoId(videoId)
                    statusLine = "HTTP/1.1 200 OK"
                    responseBody = htmlPage("Queued video: $videoId")
                } else {
                    statusLine = "HTTP/1.1 400 Bad Request"
                    responseBody = htmlPage("Could not find a valid YouTube video ID in that link.")
                }
            } else {
                statusLine = "HTTP/1.1 200 OK"
                responseBody = htmlPage(null)
            }

            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            val headers = "$statusLine\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            s.getOutputStream().write(headers.toByteArray(StandardCharsets.UTF_8))
            s.getOutputStream().write(bytes)
            s.getOutputStream().flush()
        }
    }

    private fun htmlPage(notice: String?): String {
        val banner = if (notice != null) """<p class="notice">$notice</p>""" else ""
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>Send to Roadie</title>
              <style>
                body{background:#0f0f0f;color:#f1f1f1;font-family:system-ui,sans-serif;padding:24px;max-width:480px;margin:auto}
                h2{margin:0 0 8px} p{color:#aaa;font-size:14px}
                input{width:100%;box-sizing:border-box;padding:14px;border-radius:10px;border:1px solid #303030;background:#181818;color:#fff;font-size:16px;margin:12px 0}
                button{width:100%;padding:14px;border:0;border-radius:999px;background:#ff0000;color:#fff;font-size:16px;font-weight:600}
                .notice{background:#222;color:#4ade80;padding:10px 14px;border-radius:8px}
              </style>
            </head>
            <body>
              <h2>Send to Roadie</h2>
              <p>Paste any YouTube link or video ID to add it to the car queue.</p>
              $banner
              <form method="POST" action="/queue">
                <input name="url" placeholder="https://youtu.be/..." autofocus required />
                <button type="submit">Add to Car Queue</button>
              </form>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private val rawIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
        private val watchRegex = Regex("[?&]v=([A-Za-z0-9_-]{11})")
        private val pathRegex = Regex("""(?:youtu\.be/|youtube\.com/(?:shorts|embed|live)/)([A-Za-z0-9_-]{11})""")

        fun extractYouTubeVideoId(input: String): String? {
            val trimmed = input.trim()
            if (rawIdRegex.matches(trimmed)) return trimmed
            watchRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            pathRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }

        fun findLocalIpv4(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }
}

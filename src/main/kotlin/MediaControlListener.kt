/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.*
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object MediaControlListener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var selectorManager: SelectorManager? = null
    private var listeningJob: Job? = null
    private var metadataJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    private var lastClientIp: String? = null

    private var lastSentTitle = ""
    private var lastSentArtist = ""
    private var lastSentThumb = ""

    fun start(port: Int = 9095) {
        if (isRunning) return

        selectorManager = SelectorManager(Dispatchers.IO)
        isRunning = true

        listeningJob = scope.launch {
            try {
                val selector = selectorManager ?: return@launch
                val serverSocket = aSocket(selector)
                    .udp()
                    .bind(InetSocketAddress("0.0.0.0", port))

                AppDebug.log("MediaControlListener: Listening for remote media control on UDP port $port")
                startMetadataLoop()

                while (isActive && isRunning) {
                    val datagram = serverSocket.receive()
                    val payload = datagram.packet.readBytes().decodeToString().trim()

                    val clientHost = datagram.address.toString()
                        .removePrefix("/")
                        .substringBefore(":")
                    if (clientHost.isNotBlank()) {
                        lastClientIp = clientHost
                    }

                    AppDebug.log("MediaControlListener: Processing command '$payload' from $clientHost")
                    handleCommand(payload)
                }
            } catch (e: CancellationException) {
                AppDebug.log("MediaControlListener: Listener job cancelled.")
            } catch (e: Exception) {
                AppDebug.log("MediaControlListener error: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    private var lastSentStatus = ""
    private var lastSentPos = -1L

    private fun startMetadataLoop() {
        metadataJob?.cancel()
        metadataJob = scope.launch {
            while (isActive && isRunning) {
                delay(1000)
                val targetIp = lastClientIp ?: continue
                try {
                    val meta = fetchWindowsMediaMeta() ?: continue
                    if (meta.title != lastSentTitle || meta.artist != lastSentArtist || meta.thumbB64 != lastSentThumb || meta.status != lastSentStatus || Math.abs(meta.posSec - lastSentPos) >= 1) {
                        lastSentTitle = meta.title
                        lastSentArtist = meta.artist
                        lastSentThumb = meta.thumbB64
                        lastSentStatus = meta.status
                        lastSentPos = meta.posSec

                        val payload = "CMD:META|${meta.title}|${meta.artist}|${meta.thumbB64}|${meta.status}|${meta.posSec}|${meta.durSec}"
                        val bytes = payload.toByteArray(Charsets.UTF_8)
                        val socket = DatagramSocket()
                        val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(targetIp), 9096)
                        socket.send(packet)
                        socket.close()
                    }
                } catch (e: Exception) {
                    // Ignore transient metadata errors
                }
            }
        }
    }

    private data class MediaMeta(
        val title: String,
        val artist: String,
        val thumbB64: String,
        val status: String,
        val posSec: Long,
        val durSec: Long
    )

    @Volatile
    private var cachedMetaFetcherPath: String? = null

    private fun extractExeFromResources(resourceName: String): File? {
        return try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "wfas_native")
            tempDir.mkdirs()
            val outFile = File(tempDir, resourceName)
            val stream = this::class.java.getResourceAsStream("/native/windows/x86_64/$resourceName")
            if (stream != null) {
                stream.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
            }
            if (outFile.exists() && outFile.length() > 0) outFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getMetaFetcherExe(): File? {
        cachedMetaFetcherPath?.let { path ->
            val f = File(path)
            if (f.exists()) return f
        }
        val exe = extractExeFromResources("MediaMetaFetcher.exe") ?: return null
        cachedMetaFetcherPath = exe.absolutePath
        return exe
    }

    private fun fetchWindowsMediaMeta(): MediaMeta? {
        val exeFile = getMetaFetcherExe() ?: return null

        return try {
            val process = ProcessBuilder(exeFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            var title = ""
            var artist = ""
            var thumb = ""
            var status = "PAUSED"
            var posSec = 0L
            var durSec = 0L

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("TITLE:") -> title = line.removePrefix("TITLE:").trim()
                        line.startsWith("ARTIST:") -> artist = line.removePrefix("ARTIST:").trim()
                        line.startsWith("THUMB:") -> thumb = line.removePrefix("THUMB:").trim()
                        line.startsWith("STATUS:") -> status = line.removePrefix("STATUS:").trim()
                        line.startsWith("POS:") -> posSec = line.removePrefix("POS:").trim().toLongOrNull() ?: 0L
                        line.startsWith("DUR:") -> durSec = line.removePrefix("DUR:").trim().toLongOrNull() ?: 0L
                    }
                }
            }
            process.waitFor()

            if (title.isBlank() && artist.isBlank()) null
            else MediaMeta(title.ifBlank { "WiFi Audio Streaming" }, artist.ifBlank { "Desktop PC" }, thumb, status, posSec, durSec)
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        isRunning = false
        listeningJob?.cancel()
        listeningJob = null
        metadataJob?.cancel()
        metadataJob = null
        try {
            selectorManager?.close()
        } catch (_: Exception) {}
        selectorManager = null
        AppDebug.log("MediaControlListener: Stopped listener.")
    }

    private fun handleCommand(payload: String) {
        scope.launch {
            val upperPayload = payload.uppercase()
            when {
                upperPayload.startsWith("CMD:SEEK|") -> {
                    val sec = payload.substringAfter("|").toLongOrNull()
                    if (sec != null) {
                        MediaKeySimulator.triggerSeek(sec)
                    }
                }
                upperPayload in listOf("CMD:MEDIA_TOGGLE", "PAUSE_TOGGLE", "CMD:MEDIA_PLAY", "CMD:MEDIA_PAUSE", "MEDIA_PLAY_PAUSE") -> {
                    MediaKeySimulator.triggerAction(MediaKeySimulator.MediaAction.PLAY_PAUSE)
                }
                upperPayload in listOf("CMD:MEDIA_NEXT", "MEDIA_NEXT") -> {
                    MediaKeySimulator.triggerAction(MediaKeySimulator.MediaAction.NEXT)
                }
                upperPayload in listOf("CMD:MEDIA_PREV", "MEDIA_PREV", "CMD:MEDIA_PREVIOUS") -> {
                    MediaKeySimulator.triggerAction(MediaKeySimulator.MediaAction.PREVIOUS)
                }
                else -> {
                    AppDebug.log("MediaControlListener: Unknown payload '$payload'")
                }
            }
        }
    }
}

package com.marcomorosi.wifiaudiostreaming.client

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Lightweight, non-blocking asynchronous socket client to dispatch
 * bi-directional media playback control commands to the WFAS Desktop host.
 */
object MediaControlClient {

    private const val TAG = "MediaControlClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sendMediaCommand(serverIp: String, port: Int = 9095, command: String) {
        if (serverIp.isBlank()) return

        scope.launch {
            try {
                val address = InetAddress.getByName(serverIp)
                val bytes = command.toByteArray(Charsets.UTF_8)
                val socket = DatagramSocket()
                socket.soTimeout = 1000

                val packet = DatagramPacket(bytes, bytes.size, address, port)
                socket.send(packet)
                socket.close()

                Log.d(TAG, "Sent media control payload '$command' to $serverIp:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending media control payload to $serverIp:$port", e)
            }
        }
    }

    fun togglePlayPause(serverIp: String, port: Int = 9095) {
        sendMediaCommand(serverIp, port, "CMD:MEDIA_TOGGLE")
    }

    fun skipToNext(serverIp: String, port: Int = 9095) {
        sendMediaCommand(serverIp, port, "CMD:MEDIA_NEXT")
    }

    fun skipToPrevious(serverIp: String, port: Int = 9095) {
        sendMediaCommand(serverIp, port, "CMD:MEDIA_PREV")
    }
}

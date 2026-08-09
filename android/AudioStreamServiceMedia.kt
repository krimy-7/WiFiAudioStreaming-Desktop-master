package com.marcomorosi.wifiaudiostreaming.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.marcomorosi.wifiaudiostreaming.R
import com.marcomorosi.wifiaudiostreaming.client.MediaControlClient

/**
 * Handles Android system-native MediaSession and Foreground Notification with MediaStyle.
 * Integrates with Lock Screen, Quick Settings Media Player, and Android 15 Status Bar Chips.
 */
class AudioStreamMediaManager(
    private val context: Context,
    private var serverIpProvider: () -> String,
    private var mediaPortProvider: () -> Int = { 9095 }
) {

    private var mediaSession: MediaSessionCompat? = null

    companion object {
        const val CHANNEL_ID = "wfas_media_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PREVIOUS = "com.marcomorosi.wifiaudiostreaming.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.marcomorosi.wifiaudiostreaming.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.marcomorosi.wifiaudiostreaming.ACTION_NEXT"
    }

    fun initializeMediaSession(): MediaSessionCompat {
        createNotificationChannel()

        val session = MediaSessionCompat(context, "WFAS_MediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MediaControlClient.togglePlayPause(serverIpProvider(), mediaPortProvider())
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onPause() {
                    MediaControlClient.togglePlayPause(serverIpProvider(), mediaPortProvider())
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onSkipToNext() {
                    MediaControlClient.skipToNext(serverIpProvider(), mediaPortProvider())
                }

                override fun onSkipToPrevious() {
                    MediaControlClient.skipToPrevious(serverIpProvider(), mediaPortProvider())
                }
            })

            isActive = true
        }

        this.mediaSession = session
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMediaMetadata("WiFi Audio Streaming", "Streaming from Desktop PC")

        return session
    }

    fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    fun updateMediaMetadata(title: String, artist: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .build()

        mediaSession?.setMetadata(metadata)
    }

    fun buildMediaStyleNotification(isStreaming: Boolean): Notification {
        val sessionToken = mediaSession?.sessionToken

        val prevPendingIntent = PendingIntent.getService(
            context, 0, Intent(context, Service::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            context, 1, Intent(context, Service::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            context, 2, Intent(context, Service::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isStreaming) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("WiFi Audio Streaming")
            .setContentText("Connected to PC: ${serverIpProvider()}")
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isStreaming) "Pause" else "Play", playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(sessionToken)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        return builder.build()
    }

    fun release() {
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Audio Streaming Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for active audio streaming and remote media controls."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

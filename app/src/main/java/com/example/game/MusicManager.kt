package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.IOException

/**
 * Manages background music playback using the bundled music/music.ogg asset.
 * Mirrors the web game's single looping background track.
 *
 * Usage:
 *   val music = MusicManager(context)
 *   music.start()   // play / resume
 *   music.pause()   // pause (e.g., game paused)
 *   music.stop()    // full stop and release
 *   music.release() // call from onDestroy()
 */
class MusicManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var shouldPlay = false

    init {
        prepare()
    }

    private fun prepare() {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
                )
                val afd = context.assets.openFd("music/music.ogg")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.55f, 0.55f) // 55% volume – comfortable during gameplay
                setOnPreparedListener {
                    isPrepared = true
                    if (shouldPlay) it.start()
                }
                setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    false
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: IOException) {
            // Asset not found – silently skip music
        } catch (e: Exception) {
            // Any other error – silently skip music
        }
    }

    /** Start or resume playback. */
    fun start() {
        shouldPlay = true
        val mp = mediaPlayer ?: return
        if (isPrepared && !mp.isPlaying) {
            mp.start()
        }
    }

    /** Pause playback (preserves position). */
    fun pause() {
        shouldPlay = false
        try {
            val mp = mediaPlayer ?: return
            if (isPrepared && mp.isPlaying) mp.pause()
        } catch (_: Exception) {}
    }

    /** Stop playback and reset to beginning. */
    fun stop() {
        shouldPlay = false
        try {
            val mp = mediaPlayer ?: return
            if (isPrepared && mp.isPlaying) {
                mp.stop()
                isPrepared = false
            }
        } catch (_: Exception) {}
    }

    /** Release all resources. Call from Activity.onDestroy(). */
    fun release() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPrepared = false
    }

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
}

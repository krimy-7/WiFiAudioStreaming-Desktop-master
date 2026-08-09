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

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaKeySimulator {

    private var nativeAvailable = false

    init {
        try {
            if (AudioEngine.loadLibrary()) {
                nativeAvailable = true
            }
        } catch (e: Throwable) {
            nativeAvailable = false
        }
    }

    @JvmStatic
    private external fun simulateMediaKeyNative(keyCode: Int)

    enum class MediaAction {
        PLAY_PAUSE,
        NEXT,
        PREVIOUS
    }

    suspend fun triggerAction(action: MediaAction) = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name").lowercase()
        AppDebug.log("MediaKeySimulator: Triggering action $action (OS: $os)")

        when {
            os.contains("win") -> triggerWindows(action)
            os.contains("mac") -> triggerMac(action)
            else -> triggerLinux(action)
        }
    }

    suspend fun triggerSeek(targetSec: Long) = withContext(Dispatchers.IO) {
        val exeFile = getWinKeyInjectorExe()
        if (exeFile != null) {
            try {
                ProcessBuilder(exeFile.absolutePath, "seek", targetSec.toString()).start()
                AppDebug.log("MediaKeySimulator (WinKeyInjector): Triggered seek to $targetSec s.")
            } catch (e: Exception) {
                AppDebug.log("MediaKeySimulator seek error: ${e.message}")
            }
        }
    }

    @Volatile
    private var cachedInjectorPath: String? = null

    private fun getWinKeyInjectorExe(): File? {
        cachedInjectorPath?.let { path ->
            val f = File(path)
            if (f.exists()) return f
        }
        return try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "wfas_native")
            tempDir.mkdirs()
            val outFile = File(tempDir, "WinKeyInjector.exe")
            val stream = this::class.java.getResourceAsStream("/native/windows/x86_64/WinKeyInjector.exe")
            if (stream != null) {
                stream.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
            }
            if (outFile.exists() && outFile.length() > 0) {
                cachedInjectorPath = outFile.absolutePath
                outFile
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun triggerWindows(action: MediaAction) {
        val arg = when (action) {
            MediaAction.PLAY_PAUSE -> "play"
            MediaAction.NEXT -> "next"
            MediaAction.PREVIOUS -> "prev"
        }

        val exeFile = getWinKeyInjectorExe()
        if (exeFile != null) {
            try {
                ProcessBuilder(exeFile.absolutePath, arg).start()
                AppDebug.log("MediaKeySimulator (WinKeyInjector): Triggered $action instantly.")
                return
            } catch (e: Exception) {
                AppDebug.log("MediaKeySimulator (WinKeyInjector error): ${e.message}")
            }
        }

        var nativeOk = false
        try {
            val keyCode = when (action) {
                MediaAction.PLAY_PAUSE -> 0
                MediaAction.NEXT -> 1
                MediaAction.PREVIOUS -> 2
            }
            simulateMediaKeyNative(keyCode)
            nativeOk = true
            AppDebug.log("MediaKeySimulator (Win JNI): Triggered $action successfully.")
        } catch (_: Throwable) {}

        if (nativeOk) return

        val charCode = when (action) {
            MediaAction.PLAY_PAUSE -> 179
            MediaAction.NEXT -> 176
            MediaAction.PREVIOUS -> 177
        }
        val psScript = "\$w = New-Object -ComObject WScript.Shell; \$w.SendKeys([char]$charCode)"
        try {
            ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psScript).start()
        } catch (_: Exception) {}
    }

    private fun triggerMac(action: MediaAction) {
        val script = when (action) {
            MediaAction.PLAY_PAUSE -> "tell application \"System Events\" to key code 16"
            MediaAction.NEXT -> "tell application \"System Events\" to key code 19"
            MediaAction.PREVIOUS -> "tell application \"System Events\" to key code 20"
        }

        try {
            ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            val altScript = when (action) {
                MediaAction.PLAY_PAUSE -> "tell application \"Music\" to playpause"
                MediaAction.NEXT -> "tell application \"Music\" to next track"
                MediaAction.PREVIOUS -> "tell application \"Music\" to previous track"
            }
            runCatching { ProcessBuilder("osascript", "-e", altScript).start() }
        }
    }

    private fun triggerLinux(action: MediaAction) {
        val playerctlCmd = when (action) {
            MediaAction.PLAY_PAUSE -> "play-pause"
            MediaAction.NEXT -> "next"
            MediaAction.PREVIOUS -> "previous"
        }

        val xdotoolKey = when (action) {
            MediaAction.PLAY_PAUSE -> "XF86AudioPlay"
            MediaAction.NEXT -> "XF86AudioNext"
            MediaAction.PREVIOUS -> "XF86AudioLowerVolume"
        }

        try {
            val p = ProcessBuilder("playerctl", playerctlCmd).redirectErrorStream(true).start()
            if (p.waitFor() == 0) return
        } catch (_: Exception) {}

        try {
            ProcessBuilder("xdotool", "key", xdotoolKey).redirectErrorStream(true).start()
        } catch (e: Exception) {
            AppDebug.log("MediaKeySimulator (Linux error): ${e.message}")
        }
    }
}

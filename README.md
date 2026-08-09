# WiFi Audio Streaming (Desktop PC)

A high-performance, low-latency application to stream system audio over Wi-Fi to your Android device, enhanced with **bi-directional remote media playback controls**, **real-time metadata & thumbnail streaming**, and **interactive seeking**.

> **Original App by:** Marco Morosi  
> **Modified & Enhanced by:** [Krishna Nishad](https://github.com/krimy-7)

---

## 📥 Direct Downloads & Pre-Built Executables

Skip building from source! Download pre-compiled executables directly:

- 💻 **[Download Desktop PC App (Executable .jar)](https://drive.google.com/file/d/1V3SC2f05FNPdgIVboLt7AMs7YVoeOCg8/view?usp=sharing)** *(Windows 64-bit)*
- 📱 **[Download Android Mobile App (.apk)](https://drive.google.com/file/d/14ctjEleHYK2pU0oqiFNJoLVimvuAX0T-/view?usp=sharing)** *(Android 8.0+)*



---

## ✨ Features & Enhancements

### 🎵 Real-Time Audio Streaming
- Stream PC system audio to Android over Wi-Fi with ultra-low latency.
- High-efficiency PCM, RTP, and HTTP audio streaming support.

### 🎮 Bi-Directional Remote Media Control (UDP Port 9095)
- Control PC media playback directly from your Android phone's notification bar or lock screen.
- **Supported Controls:** Play / Pause, Next Track, Previous Track.
- **Ultra-Fast Response (~15ms):** Powered by custom C# Win32/WinRT hotkey injection (`WinKeyInjector.exe`).
- **Universal Compatibility:** Works seamlessly with YouTube (Chrome/Edge), Spotify, VLC, Netflix, and Windows Media Player.

### 🖼️ Live Track Metadata & Artwork Streaming (UDP Port 9096)
- **Live Metadata:** Streams Track Title, Artist, and Album/Video Thumbnail directly from Windows System Media Transport Controls (GSMTC).
- **High-Efficiency Thumbnail Compression:** Resizes and compresses artwork into lightweight 160x160 JPEG streams (~4KB) to avoid network congestion.
- **Real-Time Playback Status:** Live `PLAYING` vs `PAUSED` state synchronization.
- **Timestamps & Progress Bar:** Real-time playback position and total track duration sync (e.g., `2:14 / 5:05`).

### ⏩ Interactive Touch Seeking
- Touch or scrub the progress bar on your Android phone to jump to any point in a YouTube video or audio track on your PC.

---

## 🛠️ System Requirements & Architecture

- **Operating System:** Windows 10 / Windows 11 (64-bit)
- **Java Runtime:** JDK 17+ or Java JRE (Run by double-clicking the `.jar` file)
- **Ports Used:**
  - `9090`: Audio RTP Stream
  - `9095`: Media Control Commands (UDP)
  - `9096`: Track Metadata & Thumbnail Stream (UDP)

---

## 🚀 How to Build from Source

### 1. Prerequisites
Ensure Java JDK is installed (or use Android Studio's bundled JBR):
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
```

### 2. Compile Desktop Standalone Executable
```powershell
.\gradlew packageReleaseUberJarForCurrentOS
```
The compiled standalone executable JAR will be created at:  
`build/compose/jars/WiFi Audio Streaming-windows-x64-5.1.0-release.jar`

---

## 👨‍💻 Developer Credits & Licensing

- **Original Base Project:** Developed by **Marco Morosi** under the European Union Public Licence (EUPL v1.2).
- **Remote Controls, Metadata, Seeking & UI Enhancements:** Added by **Krishna Nishad** ([@krimy-7](https://github.com/krimy-7)).

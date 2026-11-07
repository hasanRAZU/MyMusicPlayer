# MyMusicPlayer 🎵

**MyMusicPlayer** is a simple, offline Android music player developed using Android Studio and Java. It scans music files stored on the device, organizes them by folders, and provides essential music playback features with background playback support.

The application does not require an internet connection or a backend server.

## ✨ Features

- **Folder-wise Music Library** – Automatically scans and organizes music files by their folders.
- **Music Playback** – Play audio files directly from the device's storage.
- **Playback Controls** – Play, pause, previous, and next.
- **Repeat Modes** – Repeat Off, Repeat All, and Repeat One.
- **Shuffle Mode** – Randomize music playback.
- **Seek Controls** – Move backward or forward by 15 or 30 seconds.
- **Playback Progress** – View the current playback position and total duration.
- **Background Playback** – Continue listening while using other applications.
- **Notification Controls** – Control music playback from the notification panel.
- **Lock Screen Controls** – Access playback controls from the lock screen.
- **Folder Management** – Hide unwanted music folders from the library.
- **Offline Operation** – No internet connection or online music service required.

## 🛠️ Technologies Used

| Technology | Purpose |
|---|---|
| Java | Application programming language |
| XML | User interface layouts |
| Android Studio | Development environment |
| Android SDK | Android application development |
| Media3 ExoPlayer | Audio playback engine |
| Media3 MediaSession | Background playback and media controls |
| MediaStore | Accessing audio files stored on the device |
| SharedPreferences | Saving user preferences |
| Gradle Kotlin DSL | Project build configuration |

## 📱 Requirements

- Android 14 (API 34) or higher.
- A device containing locally stored audio files.
- Storage permission to access audio files.

**Project Configuration**

| Property | Value |
|---|---|
| Project Name | MyMusicPlayer |
| Package Name | `com.example.mymusicplayer` |
| Language | Java |
| UI | XML |
| Minimum SDK | API 34 |
| Compile SDK | API 37 |
| Target SDK | API 37 |
| Backend | None |
| Internet Required | No |

## 🔐 Permissions

The application uses the following Android permissions:

- `READ_MEDIA_AUDIO` – Allows the application to read audio files stored on the device.
- `FOREGROUND_SERVICE` – Allows foreground services.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` – Allows the application to run a media playback foreground service.

The audio permission is requested at runtime when needed.

## 📂 Project Structure

The main application components include:

```text
MyMusicPlayer/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/mymusicplayer/
│           │       ├── MainActivity.java
│           │       └── MusicPlaybackService.java
│           │
│           ├── res/
│           │   ├── layout/
│           │   │   └── activity_main.xml
│           │   └── values/
│           │
│           └── AndroidManifest.xml
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🚀 Getting Started

### 1. Clone the Repository

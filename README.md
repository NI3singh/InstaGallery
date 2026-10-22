<!-- InstaGallery README -->

<p align="center">
  <img src="instagallery-logo.svg" alt="InstaGallery app logo" width="144" />
</p>

<h1 align="center">InstaGallery</h1>

<p align="center">
  <strong>Your camera roll, reimagined as a private Instagram-style memory network.</strong><br />
  Browse photos, watch local videos as Reels, and organize familiar faces into personal profiles — right on your Android device.
</p>

<p align="center">
  <a href="https://github.com/NI3singh/InstaGallery/releases/latest/download/InstaGallery-v1.1.5.apk">
    <img src="https://img.shields.io/badge/Download-APK-0095F6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://github.com/NI3singh/InstaGallery/releases">
    <img src="https://img.shields.io/badge/Releases-v1.1.5-833AB4?style=for-the-badge&logo=github&logoColor=white" alt="View releases" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0 or newer" />
  <img src="https://img.shields.io/badge/Network-No%20INTERNET%20permission-222222?style=for-the-badge" alt="No INTERNET permission declared in the app manifest" />
</p>

<p align="center">
  <a href="#features">Features</a> ·
  <a href="#privacy--data-handling">Privacy</a> ·
  <a href="#download-and-install">Install</a> ·
  <a href="#build-from-source">Build from source</a>
</p>

---

## What is InstaGallery?

**InstaGallery** turns your device's local photos and videos into a personal, dark-mode social feed for the people and moments that matter to you. It indexes media with Android's `MediaStore`, detects faces on-device, and groups recurring faces into editable profiles such as `@Papa`, `@Maa`, `@Nitin`, and `@Rishi`.

The result is a familiar social-style experience — posts, Stories, Reels, profile grids, likes, bookmarks, and multi-person tags — without requiring an account or remote gallery service for its core experience.

> **Built for personal memories:** InstaGallery organizes local media and its own metadata. Tagging or merging profiles is not intended to move or delete the original camera-roll files.

## Features

### 🏠 Home feed, Stories, and posts

- **Local media feed:** browse photos and videos discovered through Android media storage while indexing continues in the background.
- **People Stories tray:** face-crop avatars with gradient rings, plus an `@Other` group for media without a person assignment.
- **Interactive posts:** double-tap to like, with an animated heart response.
- **On-image person tags:** reveal linked `@Person` profiles from a post or its person badge.
- **Multiple people per memory:** overlapping avatars represent photos associated with more than one person.
- **Liked and bookmarked filters:** return to favorite memories quickly.
- **Live indexing progress:** see processed-versus-total media counts while background face indexing runs.
- **Stable feed ordering:** the randomized order is seeded for the current session, so ordinary state updates do not intentionally reshuffle the feed; the app-title action can trigger a reshuffle and media rescan.
- **Manual tagging and media details:** add or remove person associations and inspect available file details such as dimensions, MIME type, and capture date.

### 🎬 Full-screen vertical Reels

- Swipe vertically through local videos in a Reels-style player.
- Tap to pause or resume; double-tap to like.
- Use the action rail for likes, tagged people, bookmarks, Android's native share sheet, and mute controls.
- Open Reel details to manage tags and inspect available metadata.
- A managed Media3 `ExoPlayer` is bound to the settled page to reduce unnecessary player instances and prevent adjacent videos from playing together.

### 👥 People directory and face grouping

- Detect faces locally using Google ML Kit.
- Deskew detected face crops using the estimated head-roll angle before feature extraction.
- Generate on-device facial descriptors using the TensorFlow Lite model when it is available, with a built-in 128-dimensional facial-geometry, HOG, and color-feature fallback.
- Group detections into person profiles and maintain local links between people and media.
- Review possible duplicate profiles, then merge profiles or dismiss the suggestion.
- Sort the people directory by post count, name, or recency.

> Automatic face grouping is approximate and can make mistakes. Review suggested matches, correct tags, and merge profiles only when appropriate.

### 👤 Editable person profiles

- Rename automatically generated profiles, such as changing `Person 1` to `Papa`.
- Choose a detected face crop as a profile avatar.
- Browse separate photo and Reel grids with post, Reel, and total counts.
- Remove a memory from a person's profile without deleting the original media file from the device.

## Privacy & data handling

InstaGallery is designed for local use. The current app manifest **does not declare** `android.permission.INTERNET`. Its media workflow uses Android media-access permissions, while Room/SQLite stores the local relationships used by feeds and person profiles.

- **No app-operated upload flow:** the core gallery experience does not require an account or remote gallery service.
- **Local organization:** tagging, renaming, and merging update app metadata and relationships rather than intentionally modifying original media files.
- **Media permissions:** the app declares media-reading permissions appropriate to the supported Android versions.
- **On-device face processing:** face detections, crops, and descriptors are used by local indexing and profile grouping.

**Important Android backup note:** the manifest currently sets `android:allowBackup="true"`. Android system backup or device-transfer behavior is separate from the app's declared internet permission and may depend on device configuration. Do not treat the missing `INTERNET` permission as proof that Android-level backup is disabled.

## Download and install

The latest published release is **v1.1.5**.

<p align="center">
  <a href="https://github.com/NI3singh/InstaGallery/releases/latest/download/InstaGallery-v1.1.5.apk">
    <img src="https://img.shields.io/badge/Download-InstaGallery--v1.1.5.apk-0095F6?style=for-the-badge&logo=android&logoColor=white" alt="Download InstaGallery APK" />
  </a>
  <br />
  <a href="https://github.com/NI3singh/InstaGallery/releases">View release notes and assets</a>
</p>

### Install on Android

1. Open the APK link above on your Android phone.
2. Open the downloaded `InstaGallery-v1.1.5.apk`. If Android prompts you, allow your browser or file manager to install unknown apps for this installation.
3. Launch InstaGallery and grant media access when prompted.
4. Browse your local media. Face indexing may continue in the background after the initial scan begins.

**Requirements:** Android 8.0 Oreo (API 26) or newer.

### Verify the APK

The published release asset reports this SHA-256 digest:

```text
1f52f1a2d3e4d7df1f0944028031db83e3ef5fdb69644339c6ec50f26686889b
```

After downloading, calculate the file's SHA-256 hash and compare it with the value above before installing. Verify the hash even when downloading through a mirror.

## Build from source

### Prerequisites

- JDK 21 (Temurin 21+ recommended)
- Android SDK with API 36
- Gradle installed and available on your PATH

### Build the phone-focused debug APK

From the repository root, run:

```bash
gradle assembleDebug -PtargetPhonesOnly=true
```

The phone-focused build configures the `arm64-v8a` and `armeabi-v7a` Android ABIs and excludes desktop-emulator `x86` and `x86_64` binaries.

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Main technologies

| Area | Implementation |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 |
| Local media discovery | Android `MediaStore` |
| Local persistence | Room / SQLite |
| Face detection | Google ML Kit |
| Face grouping | On-device descriptors and similarity-based clustering |
| Background indexing | Android `WorkManager` |
| Video playback | AndroidX Media3 / `ExoPlayer` |

## Repository layout

```text
app/src/main/
├── java/com/example/
│   ├── data/          # Room entities, DAOs, repositories, media scanning
│   ├── ml/            # Face detection, descriptors, clustering, indexing worker
│   └── ui/            # Feed, people directory, profiles, Reels, components
└── res/
    ├── drawable/      # Adaptive launcher icon vectors
    └── mipmap-*/      # Launcher icon assets by density

docs/assets/
└── instagallery-logo.svg
```

## Notes

- Face clustering is a convenience, not a guarantee of identity. Check automatically assigned people and correct mistakes.
- Media access permission is required to display local photos and videos.
- The `docs/assets/instagallery-logo.svg` image is a reusable SVG version based on the app's launcher icon vector resources in `app/src/main/res/drawable/`.
- APK size can change between releases. Check the release page for the current artifact and verification details.

---

<p align="center">
  <sub>InstaGallery · Private memories, organized on your device.</sub>
</p>

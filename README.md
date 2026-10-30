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
  <a href="https://github.com/NI3singh/InstaGallery/releases/latest/download/InstaGallery-v2.0.5.apk">
    <img src="https://img.shields.io/badge/Download-APK-0095F6?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://github.com/NI3singh/InstaGallery/releases">
    <img src="https://img.shields.io/badge/Releases-v2.0.5-833AB4?style=for-the-badge&logo=github&logoColor=white" alt="View releases" />
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

The result is a familiar social-style experience — posts, Stories, Reels, Saved Collection boards, primary "My Profile" hub, likes, bookmarks, and multi-person tags — without requiring an account or remote gallery service for its core experience.

> **Built for personal memories:** InstaGallery organizes local media and its own metadata. Tagging or merging profiles is not intended to move or delete the original camera-roll files.

## Features

### 🏠 Home feed, Stories, and posts

- **Sub-second local media sync:** surgical URI `ContentObserver` and zero-I/O `HashSet` ID diffing reflect new photos and deletions in under 50ms while fast-lane face indexing processes newly captured photos first.
- **People Stories tray & interactive Story Player:** face-crop avatars with gradient rings open a curated 12-slide Story Player (*On This Day*, *Favorites*, *Recent*) with auto-advancing segmented bars, tap navigation, press-and-hold pause, and 1080p InstaCard export.
- **Interactive posts:** double-tap to like with customizable 6-theme heart burst physics, add personal captions, and inspect EXIF metadata.
- **On-image person tags & Collab headers:** reveal linked `@Person` profiles or tap overlapping Collab avatars (`@PersonA & @PersonB`) to jump to any co-star's profile.
- **Navbar re-tap & pull-to-refresh shuffle:** re-tap the active bottom navigation icon or pull down to sync and reshuffle your feed while keeping photos taken in the last 30 minutes pinned at the top.

### 🎬 Full-screen vertical Reels & video bookmarks

- Swipe vertically through local videos in a Reels-style player with pull-to-refresh shuffle at the top page.
- Bookmark Reel videos into SQLite and organize them into custom Saved Collection boards.
- Add personal captions to Reel videos (replacing raw camera filenames) and manage person tags from the `⋮` options sheet.
- A managed Media3 `ExoPlayer` is bound to the settled page to prevent background audio bleed.

### 🔖 Saved / Bookmarks boards & built-in Reel player

- Organize bookmarked photos and Reels into custom local boards with edge-to-edge `2x2` mosaic covers and dynamic *"X posts • Y reels"* counters.
- Filter any board by *All*, *Photos*, or *Reels*, and toggle between a `3x3` Grid and a scrollable vertical Post Feed.
- Tap any saved video to launch the built-in full-screen Saved Reel ExoPlayer modal.

### 👤 Primary "My Profile", People directory & Settings Studio

- Set your own detected profile as **My Profile** (displaying your face avatar in the bottom navigation bar), quick-switch via `@handle ⌄`, or expand the *People in Your Gallery* discovery tray.
- Filter any profile grid by *All*, *Solo* portraits, or *Together With @Friend* co-occurrence filters.
- Full **Settings & Activity Hub** with 7 dedicated interactive sub-screens: *Edit Profile*, *Switch 'Me' Face*, *Gallery Sync & Face Scanner Console*, *Hidden & @Other Profiles Manager*, *Merge Duplicate Profiles Studio* (with live 192-D cosine similarity lookalike detection), *Feed, Aspect Ratio & Story Studio* (with interactive live preview), and *Database & Storage Inspector*.

> Automatic face grouping is approximate and can make mistakes. Review suggested matches, correct tags, and merge profiles only when appropriate.

## Privacy & data handling

InstaGallery is designed for local use. The current app manifest **does not declare** `android.permission.INTERNET`. Its media workflow uses Android media-access permissions, while Room/SQLite stores the local relationships used by feeds and person profiles.

- **No app-operated upload flow:** the core gallery experience does not require an account or remote gallery service.
- **Local organization:** tagging, renaming, and merging update app metadata and relationships rather than intentionally modifying original media files.
- **Media permissions:** the app declares media-reading permissions appropriate to the supported Android versions.
- **On-device face processing:** face detections, crops, and descriptors are used by local indexing and profile grouping.

**Important Android backup note:** the manifest currently sets `android:allowBackup="true"`. Android system backup or device-transfer behavior is separate from the app's declared internet permission and may depend on device configuration. Do not treat the missing `INTERNET` permission as proof that Android-level backup is disabled.

## Download and install

The latest published release is **v2.0.5**.

<p align="center">
  <a href="https://github.com/NI3singh/InstaGallery/releases/latest/download/InstaGallery-v2.0.5.apk">
    <img src="https://img.shields.io/badge/Download-InstaGallery--v2.0.5.apk-0095F6?style=for-the-badge&logo=android&logoColor=white" alt="Download InstaGallery APK" />
  </a>
  <br />
  <a href="https://github.com/NI3singh/InstaGallery/releases">View release notes and assets</a>
</p>

### Install on Android

1. Open the APK link above on your Android phone.
2. Open the downloaded `InstaGallery-v2.0.5.apk`. If Android prompts you, allow your browser or file manager to install unknown apps for this installation.
3. Launch InstaGallery and grant media access when prompted.
4. Browse your local media. Face indexing may continue in the background after the initial scan begins.

**Requirements:** Android 8.0 Oreo (API 26) or newer.

### Verify the APK

Each GitHub Release publishes the exact SHA-256 digest of `InstaGallery-v2.0.5.apk` in the release notes. After downloading, calculate the file's SHA-256 hash and compare it with the published digest on the release page before installing.

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

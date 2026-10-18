# 📸 InstaGallery — Private Offline-First Local Instagram Clone & AI Face Network

<p align="center">
  <a href="../../releases/latest/download/InstaGallery-v1.0.apk">
    <img src="https://img.shields.io/badge/⬇_Download_APK-GitHub_Releases_(v1.0.0)-0095F6?style=for-the-badge&logo=android&logoColor=white" alt="Download InstaGallery APK from GitHub Releases" />
  </a>
  <a href="./releases/InstaGallery-v1.0.apk">
    <img src="https://img.shields.io/badge/📦_Mirror_APK-/releases_Folder-262626?style=for-the-badge&logo=github&logoColor=white" alt="Download APK from repo folder" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B_(API_26%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Privacy-100%25_Offline_(Zero_Internet)-FF3040?style=for-the-badge" alt="100% Offline" />
</p>

**InstaGallery** transforms your phone's local camera roll and video folders into a sleek, dark-mode **Instagram-style social network of your friends and family** — **100% offline** on your device.

Using native Android `MediaStore` indexing, **ML Kit Face Detection**, and on-device **TensorFlow Lite / Facial Geometry & HOG Embeddings**, InstaGallery automatically detects recurring faces across your photos and videos, groups them into dedicated `@Person` profiles (like `@Papa`, `@Maa`, `@Nitin`, `@Rishi`), and lets you browse your personal memories as an interactive social feed and vertical Reels stream without uploading a single byte to the cloud.

---

## 📲 Direct APK Download (No Play Store Needed)

You can download and install the signed **`InstaGallery-v1.0.apk`** directly from **GitHub Releases**:

1. **Direct 1-Click APK Download (GitHub Releases)**:
   👉 **[Download `InstaGallery-v1.0.apk` (Latest Release)](../../releases/latest/download/InstaGallery-v1.0.apk)**
2. **GitHub Releases Page**:
   👉 **[View All Releases (`v1.0.0`)](../../releases)** *(Listed under the **Releases** section on the right sidebar of this GitHub repo)*
3. **Repository Mirror Path**:
   👉 **[`releases/InstaGallery-v1.0.apk`](./releases/InstaGallery-v1.0.apk)**

### Installation Steps on Android:
1. Tap the download link above on your Android phone (8.0 Oreo / API 26 or newer).
2. Open the downloaded **`InstaGallery-v1.0.apk`** file (if prompted, allow *"Install unknown apps"* for your browser or file manager).
3. Launch **InstaGallery** and tap **Grant Media Access** to index your local photos and videos.

---

## ✨ Key Features

### 1. 🏠 Instagram-Style Home Feed & Stories Tray
- **Instant Media Indexing**: Scans your device's `MediaStore` photos and videos in seconds so your feed is immediately browsable while face clustering runs smoothly in the background via `WorkManager`.
- **Stories People Tray**: Top horizontal tray featuring circular face-crop avatars wrapped in the signature Instagram gradient ring (`#FEDA77` → `#F58529` → `#DD2A7B` → `#8134AF`), plus an `@Other` bucket for scenic/untagged media.
- **Interactive Post Cards**:
  - **Double-Tap to Like** with a bouncy spring-animated heart burst overlay.
  - **Single-Tap Floating Tag Bubbles**: Tap any photo or the bottom-left person badge to reveal interactive `@Person` tag bubbles right on the image.
  - **Overlapping Co-Author Avatars**: Photos featuring multiple family members automatically show overlapping avatars (`@Papa and @Maa`).
  - **Favorites & Saved Bookmarks Filters**: Filter your Home feed by Liked (`♥`) or Bookmarked (`🔖`) memories from the top bar.
  - **Manual Tagging & Metadata Sheet**: Add or remove person tags on any photo and inspect file resolution, MIME type, and capture date.

### 2. 🎬 Full-Screen Vertical Reels
- **Single-ExoPlayer Lifecycle Architecture**: Smooth vertical swipe pager (`VerticalPager`) backed by a single managed Media3 `ExoPlayer` instance bound strictly to the settled page for zero audio bleed and low memory usage.
- **Interactive Gestures & Controls**:
  - **Single-Tap** to Pause/Play with an animated center indicator.
  - **Double-Tap** to Like with a full-screen heart burst.
  - **Right-Side Action Rail**: Like counter, Tagged People badge (`2 People`), Bookmark toggle, Native Share sheet, Mute/Unmute speaker toggle, and Reel Info & Tag Management bottom sheet.
  - **Animated Audio Marquee**: Scrolling audio ticker pill at the bottom-left with a `"View Profiles"` quick action.

### 3. 👥 Smart People Directory & 1-Tap Duplicate Merging
- **On-Device Face Clustering**: Automatically groups detected faces into profiles sorted by post count, name (`A–Z`), or recency.
- **Possible Duplicate Detection Banner**: Automatically surfaces pairs of profiles with high facial similarity (`Possible Duplicate Found: @Papa and @Person 4 look very similar`) with one-tap **Review** or **Dismiss**.
- **Multi-Profile Merge Flow**: Select 2 or more profiles (or long-press any profile card) to merge all their photos, reels, and facial embeddings into a single unified profile.

### 4. 👤 Dedicated Person Profiles
- **Complete Memory Breakdown**: Displays `Posts`, `Reels`, and `Total` memory counts, verified badge, and separate **Photos Grid** and **Reels Grid** tabs.
- **Custom Profile Management**:
  - Rename any person (`Person 1` → `Papa`).
  - Tap the profile avatar (or use the `•••` bottom sheet) to pick your favorite detected face crop as their profile picture.
  - Long-press any grid item to remove a photo from that person's profile without deleting the file from your phone.

---

## 🔒 Strict Privacy & Non-Destructive Architecture

- **Zero Internet Permission**: `android.permission.INTERNET` is completely omitted from `AndroidManifest.xml`. It is physically impossible for the app to transmit photos, face embeddings, or analytics off your device.
- **Read-Only Media Access**: Requests only `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (or `READ_EXTERNAL_STORAGE` on older Android versions).
- **Non-Destructive Organization**: Untagging a photo or merging profiles only updates SQLite cross-reference rows in Room (`media_person_cross_ref`) — your original camera roll files are never modified, moved, or deleted.

---

## 🏗️ Technical Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              InstaGallery UI (Jetpack Compose)              │
├─────────────────┬─────────────────┬─────────────────────────┤
│  HomeFeedScreen │   ReelsScreen   │  PeopleDirectoryScreen  │
│  (Stories Tray, │  (Media3 Player,│  (Duplicate Banner,     │
│   Post Cards)   │   Audio Marquee)│   Merge Modal & Grid)   │
└────────┬────────┴────────┬────────┴────────────┬────────────┘
         │                 │                     │
         ▼                 ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                       ViewModel Layer                       │
│   (HomeViewModel, ReelsViewModel, PeopleViewModel, etc.)    │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      GalleryRepository                      │
├──────────────────────────────┬──────────────────────────────┤
│      MediaStoreScanner       │      FaceIndexingWorker      │
│  (Reads Images & Videos)     │ (ML Kit + TFLite / HOG Emb)  │
└──────────────┬───────────────┴──────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Room SQLite Database                     │
├──────────────┬───────────────┬──────────────┬───────────────┤
│ media_items  │    people     │detected_faces│media_person_  │
│              │               │              │   cross_ref   │
└──────────────┴───────────────┴──────────────┴───────────────┘
```

### Tech Stack
- **UI**: Kotlin, Jetpack Compose, Material Design 3 (True OLED `#000000` Dark Theme), Navigation Compose
- **Media & Video**: AndroidX Media3 (`ExoPlayer`), Coil (`coil-compose` + `coil-video`), AndroidX `ExifInterface`
- **On-Device ML**: Google ML Kit Face Detection (`com.google.mlkit:face-detection`), TensorFlow Lite (`org.tensorflow:tensorflow-lite`) with deterministic Landmark + Sobel HOG + YCbCr fallback
- **Persistence & Background Work**: AndroidX Room (`ksp`), AndroidX WorkManager (`CoroutineWorker`), Kotlin Coroutines & `StateFlow`

---

## 🛠️ Building from Source

### Prerequisites
- JDK 21 (Temurin 21+)
- Android SDK (API 36)

### Build Signed Debug APK
```bash
gradle assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
releases/InstaGallery-v1.0.apk
```

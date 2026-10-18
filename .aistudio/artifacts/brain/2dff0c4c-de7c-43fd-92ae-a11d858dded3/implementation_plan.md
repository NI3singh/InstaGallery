# InstaGallery: Offline-First Local Media Feed & Face-Clustered People Network

InstaGallery transforms a user's on-device camera roll and video gallery into a private, offline, Instagram-style social experience. Using native Android MediaStore indexing, on-device face detection, and embedding-based clustering, it organizes photos and reels into an intelligent social graph of friends and family without uploading a single byte to the cloud.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during the interactive clarification turn:

- **Media Population Strategy**: Scan device MediaStore storage only. The app indexes all local pictures and videos via `MediaStore.Files`, reacting to additions, updates, and deletions in real-time with zero cloud uploads.
- **Face Recognition Pipeline**: Strict ML Kit face detection (`FaceDetector`) combined with on-device face embeddings (`mobilefacenet.tflite` with dynamic input/output tensor inspection and a deterministic color-spatial fallback) and online cosine clustering.
- **Reels Experience**: Auto-looping full-screen Reels using a strictly managed single `ExoPlayer` instance, with volume controls, audio ticker marquee, reaction buttons, and tagged people counters.

---

## 1. Overview & Core Concept

- **What It Does**: Reads local photos and videos from device storage, analyzes faces locally with ML Kit, clusters recurring individuals into people profiles (e.g., `@Papa`, `@Maa`, `@Nitin`), and generates an authentic Instagram-style dark mode feed, vertical reels stream, and people directory.
- **Strict Privacy Guarantee**: 100% offline. Zero internet permission (`android.permission.INTERNET` omitted from manifest). Read-only access to media; original device files are never renamed, relocated, or modified.
- **Target Audience**: Users seeking a beautiful, private alternative to social media that lets them view personal memories as a curated social feed with automated face tagging and zero cloud surveillance.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Permission Gate & Rapid Onboarding**: Checks storage/media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` on Android 13+, `READ_EXTERNAL_STORAGE` on older versions). Once granted, immediate transition to Home feed.
2. **Instant Home Feed & Background Scanning**: Photos display instantly via fast metadata scan. A compact top status pill pulses with live progress (`Scanning faces: X/Y`) powered by `WorkManager`.
3. **People Tray & Stories**: Top horizontal tray displays circular avatars with the signature Instagram gradient ring (`#FEDA77` -> `#F58529` -> `#DD2A7B` -> `#8134AF`). Tapping any person opens their dedicated profile.
4. **Interactive Post Cards**: Full-bleed 4:5 portrait media, overlapping author avatars (`-10.dp` overlap), like toggle (animated red heart), bookmark toggle, native Android share sheet (`Intent.ACTION_SEND`), and tagged person chips.
5. **Full-Screen Reels**: Vertical pager with video auto-looping, interactive action rail (Heart, Tagged People pill, Share, Mute toggle, 3-dots, and audio thumbnail), floating metadata, and animated scrolling audio marquee.
6. **People Directory & Smart Deduplication**: 3-column grid displaying detected faces and post counts. Displays a prominent "Possible Duplicate Found" banner when two face clusters share high similarity, offering a one-tap "Review" & "Merge" flow.
7. **Person Profile & Memory Management**: Displays Posts, Reels, and Total memories counts, editable name dialog, photo-grid vs reels tabs, and a long-press Instagram-style Modal Bottom Sheet to change avatar or remove photos from a profile.

### Visual Identity & Theme Tokens
- **Canvas Base**: `#000000` (True OLED black canvas)
- **Elevated Surfaces**: `#121212` (Cards and placeholders)
- **Modal & Sheet Surfaces**: `#2A2A2A` (Bottom sheets, pills, search inputs)
- **Hairline Dividers**: `#1F1F1F` and `#262626`
- **Accent Brand Blue**: `#0095F6` (Checkmarks, Merge CTA, badges)
- **Accent Red**: `#FF3040` (Liked heart state, destructive actions)
- **Gradient Story Ring**: `Brush.linearGradient(listOf(#FEDA77, #F58529, #DD2A7B, #8134AF))`
- **Typography**: Clean modern type scale using Inter font family, cursive Instagram script for the brand header.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Fast Metadata Indexing vs. Blocking AI Initialization**
  - *Chosen Approach*: Decouple media discovery from face analysis. Room inserts all files in under 2 seconds; `FaceIndexingWorker` processes detection in background batches of 25 items.
  - *Why*: Eliminates startup freeze and blank screens. Users enjoy browsing photos immediately while tags populate progressively.
- **Decision 2: Strict Single-ExoPlayer Lifecycle in Reels**
  - *Chosen Approach*: Only one `ExoPlayer` instance is retained in `ReelsScreen`, bound exclusively to `pagerState.settledPage`. Non-settled pages render low-overhead still frame thumbnails.
  - *Why*: Prevents native memory exhaustion, decoder lockups, and audio bleed across swipe transitions on resource-constrained Android devices.
- **Decision 3: Non-Destructive Association Management**
  - *Chosen Approach*: Actions like "Remove photo from @Papa" delete records in the `MediaPersonCrossRef` table only.
  - *Why*: Upholds the core architectural contract that user media in external storage is never touched or deleted.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    InstaGallery App UI                      │
├─────────────────┬─────────────────┬─────────────────────────┤
│  HomeFeedScreen │   ReelsScreen   │  PeopleDirectoryScreen  │
│  (Stories Tray, │  (ExoPlayer,    │  (Duplicate Banner,     │
│   Post Cards)   │   Audio Marquee)│   Merge Modal & Grid)   │
└────────┬────────┴────────┬────────┴────────────┬────────────┘
         │                 │                     │
         ▼                 ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                       ViewModel Layer                       │
│    (HomeViewModel, ReelsViewModel, PeopleViewModel, etc.)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      GalleryRepository                      │
├──────────────────────────────┬──────────────────────────────┤
│      MediaStoreScanner       │      FaceIndexingWorker      │
│  (Reads Images & Videos)     │ (ML Kit + TFLite Embedding)  │
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

### Data Layer Specifications
- `MediaEntity`: `mediaId`, `uri`, `displayName`, `mimeType`, `isVideo`, `dateAddedSeconds`, `width`, `height`, `durationMs`, `isProcessedForFaces`, `hasFaces`, `isFavorite`, `isBookmarked`.
- `PersonEntity`: `personId`, `name`, `avatarCropPath`, `centroidEmbeddingBlob`, `faceCount`, `createdAt`.
- `FaceEntity`: `faceId`, `mediaId`, `personId`, `cropPath`, `embeddingBlob`, bounding coordinates (`left`, `top`, `right`, `bottom`).
- `MediaPersonCrossRef`: Composite primary key `(mediaId, personId)` with foreign keys cascade.
- `MediaWithAuthors`: Embedded relation resolving co-tagged people for post headers and reels badges.

### Automated GitHub Actions CI Workflow
- `.github/workflows/android-apk-build.yml` configured to build on `ubuntu-latest`, setup Java 17 and Android SDK, pre-fetch/cache `mobilefacenet.tflite`, compile via `gradle assembleDebug`, and archive the signed debug APK artifact.

---

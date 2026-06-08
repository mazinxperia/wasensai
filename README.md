<div align="center">

<img src="./app/src/main/res/drawable/wa_sensai_logo_3d.png" alt="WA Sensai logo" width="128" />

# WA Sensai

<strong>Rooted Android extractor and offline `.waview` viewer for WhatsApp data.</strong>

Built for high-integrity local extraction, packaging, and offline viewing of real WhatsApp Business history on rooted Android devices.

<br>

<a href="#quick-glance"><img alt="Quick Glance" src="https://img.shields.io/badge/Quick_Glance-052E2B?style=for-the-badge"></a>
<a href="#screenshots"><img alt="Screenshots" src="https://img.shields.io/badge/Screenshots-0F766E?style=for-the-badge"></a>
<a href="#standalone-web-viewer-beta"><img alt="Web Viewer" src="https://img.shields.io/badge/Web_Viewer-312E81?style=for-the-badge"></a>
<a href="#privacy-and-data-handling"><img alt="Privacy" src="https://img.shields.io/badge/Privacy_Local-0B1220?style=for-the-badge"></a>

<br><br>

<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
<img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
<img alt="Android" src="https://img.shields.io/badge/Rooted_Android-3DDC84?style=flat-square&logo=android&logoColor=white">
<img alt="Archive format" src="https://img.shields.io/badge/Archive-.waview-0F766E?style=flat-square">

</div>

<br>

<p align="center">
  <img src="./screenshots/home-accent-1.png" alt="WA Sensai home screen" width="245" />
  <img src="./screenshots/chat-list-edited.png" alt="WA Sensai edited chat list" width="245" />
  <img src="./screenshots/chat-window-edited.png" alt="WA Sensai edited chat window" width="245" />
</p>

<p align="center">
  <sub>Private rooted extraction, packaged archive integrity, and offline local viewing for large WhatsApp Business history.</sub>
</p>

---

## Table Of Contents

- [Quick Glance](#quick-glance)
- [Scope And Intent](#scope-and-intent)
- [Tested Environment](#tested-environment)
- [Compatibility Notes](#compatibility-notes)
- [App Specification](#app-specification)
- [What The App Does](#what-the-app-does)
- [High-Level Extraction Flow](#high-level-extraction-flow)
- [How `.waview` Storage Works](#how-waview-storage-works)
- [Example Extracted Data Structure](#example-extracted-data-structure)
- [How The Viewer Reads Data](#how-the-viewer-reads-data)
- [Standalone Web Viewer Beta](#standalone-web-viewer-beta)
- [Architecture Overview](#architecture-overview)
- [Current App Architecture In Practice](#current-app-architecture-in-practice)
- [Major Development Setbacks And Fixes](#major-development-setbacks-and-fixes)
- [Handoff Summary](#handoff-summary)
- [Performance Notes](#performance-notes)
- [Privacy And Data Handling](#privacy-and-data-handling)
- [Build And Run Notes](#build-and-run-notes)
- [Screenshots](#screenshots)
- [Web Viewer Screenshot](#web-viewer-screenshot)
- [Project Structure](#project-structure)
- [Repository Notes](#repository-notes)
- [Internal Use Disclaimer](#internal-use-disclaimer)

> [!TIP]
> Use the links above to jump to a section. Then click the expand row under each heading to open the full content.

## Quick Glance

| Item | Details |
| --- | --- |
| Primary purpose | Rooted WhatsApp data extraction + offline local viewer |
| Main validated target | WhatsApp Business |
| Regular WhatsApp support | Package/path fallback for `com.whatsapp` is included |
| Archive format | `.waview` |
| App stack | Kotlin, Compose, Hilt, Media3, Zip4j, libsu |
| Additional viewer | Standalone web viewer beta in `web_viewer/` |
| Main validated source device | Samsung Galaxy A13 `SM-A137F/DS` |
| Extra viewer validation | Samsung Galaxy S22 Ultra, Pixel 4 Emulator |
| Data realism | Real-world WhatsApp Business history, roughly `6-7 months` |

WA Sensai is a rooted Android app built for extracting WhatsApp data from a real device and packaging it into a private `.waview` archive that can later be opened in the app's built-in viewer.

The repository also now includes a separate beta web viewer under `web_viewer/`. That web viewer is intended for urgent standalone PC viewing of existing `.waview` files in a browser. It is not a replacement for the Android app and should not be treated as production-level software yet.

This project was created for internal personal use, with the main focus on data integrity, media recovery, export reliability, and smooth offline viewing of large real-world chat history. The primary validated target was WhatsApp Business, not public multi-device compatibility across every WhatsApp variant.

The overall app experience, especially the viewer side, was built with the intention of mirroring the current WhatsApp Business feel and flow as closely as practical within this offline archive-viewing model.

> [!IMPORTANT]
> WA Sensai was built and tested mainly for an internal rooted WhatsApp Business workflow. Regular WhatsApp fallback support exists, but it was not the main validated target during development.

## Scope And Intent

<details>
<summary><strong>Open section</strong></summary>

<br>


WA Sensai was built to:

- extract WhatsApp data from a rooted Android device
- package the extracted data into a local `.waview` archive
- preserve as much message and media integrity as possible
- open that archive inside an offline in-app viewer
- provide a separate beta browser viewer for opening `.waview` files on a PC when urgently needed
- support large real chat history, not only tiny sample datasets
- handle very large message databases without requiring all messages or media to be held in memory at once

WA Sensai was not originally built as a public consumer product. It was developed for internal use and validated mainly against one real WhatsApp Business environment. Regular WhatsApp fallback logic exists, but regular WhatsApp was not formally validated end-to-end during the main development cycle.

The standalone web viewer was added later as a practical viewer-only tool. It is useful when a `.waview` file needs to be opened from a desktop browser, but it is still considered beta and should be evaluated carefully before relying on it as a production viewer.

</details>

## Tested Environment

<details>
<summary><strong>Open section</strong></summary>

<br>


Primary extraction and viewer validation was done on:

- Device: Samsung Galaxy A13 `SM-A137F/DS`
- One UI: `6`
- Android version: `14`
- Security patch: `5 February 2026`
- Root: `Magisk 30.7 (30700)`
- Magisk app version: `30.7 (30700)`
- Main tested app target: `WhatsApp Business 2.26.9.72`

Additional viewer validation was also done on:

- Samsung Galaxy S22 Ultra
- Pixel 4 Android Emulator

Important testing note:

- the `.waview` files used during validation were created from real WhatsApp Business data on a real device
- testing was done against roughly `6-7 months` of actual message history
- this was used specifically to validate data integrity and viewer behavior against realistic volume, not only synthetic test data
- extraction and viewer testing were also performed on the same real source phone in the main workflow

</details>

## Compatibility Notes

<details>
<summary><strong>Open section</strong></summary>

<br>


- Fully validated target: WhatsApp Business
- Partially prepared fallback target: regular WhatsApp
- Regular WhatsApp was not deeply validated during the main production cycle
- If regular WhatsApp extraction does not work on a specific build, additional adjustments may be needed

</details>

## App Specification

<details>
<summary><strong>Open section</strong></summary>

<br>


- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Dependency Injection: Hilt
- Serialization: Kotlinx Serialization
- Image loading: Coil 3
- Media playback: AndroidX Media3 / ExoPlayer
- Archive handling: Zip4j
- Root access: libsu
- Android model: single-app extractor + local archive viewer
- Main format: `.waview`
- Current archive format generation: format version 3

</details>

## What The App Does

<details>
<summary><strong>Open section</strong></summary>

<br>


WA Sensai has two major responsibilities:

1. Extraction
2. Viewing

### 1. Extraction

The extractor runs on a rooted Android device and copies the required WhatsApp databases and media-related files from protected app storage into WA Sensai's own controlled flow.

It then processes and packages:

- contacts
- chats
- messages
- reactions
- polls
- groups
- call logs
- labels
- mentions
- vcards
- statuses
- message edit history
- avatar/media references and indexed media metadata

The result is a self-contained `.waview` archive intended for offline viewing later.

### 2. Viewing

The viewer opens an existing `.waview` archive, reads the exported structured data, rebuilds chat lists and timelines, resolves media from the archive, and renders the data in a WhatsApp-style local viewer flow.

The viewer was refactored heavily to handle larger exports more safely and more smoothly.

### 3. Standalone Web Viewing Beta

The repository now includes an additional standalone web viewer in `web_viewer/`.

This web viewer:

- opens existing `.waview` files from a desktop browser
- reads the archive locally without uploading it to a backend
- provides a PC-side viewer for urgent archive inspection
- supports large archive handling with browser-side ZIP indexing and lazy message loading
- is separate from the Android app and does not perform extraction

The web viewer is currently beta. It was added for urgent practical viewing needs, not as a fully production-hardened replacement for the Android viewer.

</details>

## High-Level Extraction Flow

<details>
<summary><strong>Open section</strong></summary>

<br>


At a high level, extraction works like this:

1. Detect and verify rooted access.
2. Detect WhatsApp / WhatsApp Business installation.
3. Copy the required source databases and supporting files into the app's working area.
4. Read chats, contacts, groups, and metadata from the copied databases.
5. Stream messages into `data.json` instead of building one giant in-memory message list.
6. Build media index information and media availability state.
7. Add media to the archive directly from source paths one file at a time.
8. Package everything into a `.waview` archive.

Important design goals during extraction:

- avoid touching live source data more than necessary
- prefer packaged archival integrity over quick-and-dirty copying
- preserve message structure and metadata
- preserve media references even when some files are missing
- distinguish available, missing, and deleted/unrecoverable media states
- avoid staging the full media tree in app cache before zipping
- write the final archive through a `.partial` file in Downloads, then rename it in place after the archive is complete

</details>

## How `.waview` Storage Works

<details>
<summary><strong>Open section</strong></summary>

<br>


The `.waview` file is a private archive container used by WA Sensai.

At a high level, it stores:

- export metadata
- serialized structured datasets for viewer reconstruction
- media index information
- extracted avatar/media payloads where available
- archive-level information needed by the viewer to open and resolve content safely

Privacy note:

- this README intentionally avoids exposing your private internal sample names or personal dataset identifiers
- the format description here stays conceptual and implementation-oriented

Implementation direction used in this project:

- `.waview` is handled as a zip-based packaged archive
- archive creation uses `STORE` compression strategy in the current generation path
- media is indexed instead of treated as an uncontrolled loose-file dump
- export writes keep the same archive layout while streaming large data and media safely
- viewer loading relies on archive indexing, media resolution rules, and cache extraction when needed

</details>

## Example Extracted Data Structure

<details>
<summary><strong>Open section</strong></summary>

<br>


The real extracted data used during development followed a structure broadly like this before packaging. The example below is privacy-safe and intentionally generalized, but it reflects the actual shape of the extracted working data:

```text
Extracted/
├── meta/
│   └── version.json              # Export metadata / format version information
├── data.json                     # Main serialized structured export payload
├── msgstore.db                   # Copied WhatsApp message database used for extraction
├── wa.db                         # Copied WhatsApp contacts/profile database used for extraction
├── avatars/
│   ├── me.j                      # Self avatar reference/output
│   ├── <contact>.j               # Per-contact avatar output
│   ├── <group>.j                 # Group avatar output
│   └── <newsletter>.j            # Newsletter/channel avatar output when present
└── media/
    └── WhatsApp Business/
        ├── Databases/            # Source encrypted backup databases found on device
        ├── Media.zip             # Packed media payload snapshot used during processing
        ├── .Shared/              # App/internal temporary media-related files from source tree
        ├── .StickerThumbs/       # Sticker thumbnail cache area
        ├── .trash/               # Source-side deleted/trash remnants when present
        ├── Backups/              # Backup-related files such as stickers/settings/db backups
        └── Media/
            ├── WhatsApp Business Images/                  # Image media
            ├── WhatsApp Video/                            # Video media
            ├── WhatsApp Voice Notes/                      # Voice note / PTT media
            ├── WhatsApp Documents/                        # Document media
            ├── WhatsApp Calls/                            # Call-related media if present
            ├── WhatsApp Business Animated Gifs/           # GIF media
            ├── WhatsApp Business Sticker Packs/           # Sticker pack assets
            ├── WhatsApp Business Premium Message Media/   # Business-specific media area
            ├── WhatsApp Business Quick Reply Attachments/ # Quick reply attachment media
            ├── WhatsApp Business Bug Report Attachments/  # Bug-report related attachments
            └── WallPaper/                                 # Wallpaper-related media from source tree
```

Notes about this structure:

- this is based on a real extraction layout used during development
- names and contents have been generalized to protect private data
- the final `.waview` archive is a packaged viewer-oriented format, not just a raw folder dump
- WA Sensai does not rely on exposing private real contact names in documentation

</details>

## How The Viewer Reads Data

<details>
<summary><strong>Open section</strong></summary>

<br>


The viewer flow is not just "open zip and show messages". The current app uses a more structured loading pipeline:

1. Open the `.waview` archive.
2. Index the archive contents.
3. Stream `data.json` through `JsonReader`.
4. Import messages into a local SQLite-backed viewer store.
5. Build viewer state for chats, contacts, calls, labels, and related entities.
6. Sync archive metadata and media availability state.
7. Load recent chat windows and additional messages on demand.
8. Resolve media lazily and safely when the UI needs it.

Important viewer characteristics in the current codebase:

- zip indexing before heavy viewer usage
- SQLite-backed message import for large archives
- guarded media extraction and media validity checks
- archive read synchronization around zip access
- chat-level media loading safeguards
- recent-message window loading instead of full-chat memory loading by default
- deleted/missing media handling instead of silent crashes
- large archives skip full chat preload and load chats on demand
- render-model-based chat and chat-list drawing for smoother UI

Important loading note:

- the first archive open can take noticeably longer, because messages are streamed into the local viewer store
- this is by design: the app pays the import cost once instead of repeatedly parsing a huge `data.json`
- for very large archives, chat browsing is intentionally on-demand to protect memory and keep the viewer stable

</details>

## Standalone Web Viewer Beta

<details>
<summary><strong>Open section</strong></summary>

<br>

WA Sensai now includes a separate standalone browser viewer in:

```text
web_viewer/
```

This web viewer was added for urgent `.waview` viewing from a PC. It is useful when an exported `.waview` archive needs to be opened outside the Android app, directly from a desktop browser.

Important status:

- the web viewer is currently beta
- it is viewer-only
- it is not production-level ready
- it is not a replacement for the Android extractor or Android in-app viewer
- it was created mainly to make `.waview` files readable on PC as a standalone local tool

### What The Web Viewer Does

The web viewer can:

- open a local `.waview` file from the browser file picker
- read the archive without uploading it to a backend
- index ZIP / ZIP64 archive metadata in the browser
- validate the WA Sensai archive format
- display chats, groups, calls, media references, reactions, polls, and archive metadata
- load avatars and media only when needed
- handle large archives with a lazy message-loading path
- run through Docker as a static web app served by nginx

### What The Web Viewer Does Not Do

The web viewer does not:

- extract WhatsApp data
- require root
- create `.waview` files
- modify `.waview` files
- upload archives to Docker, nginx, or a server
- guarantee production-level compatibility across every archive shape yet

### Web Viewer Technical Notes

The web viewer is built with:

- React
- TypeScript
- Vite
- Tailwind CSS
- Web Worker archive parsing
- custom ZIP / ZIP64 central-directory reading
- browser `File.slice()` media access
- lazy large-archive message parsing
- Docker + nginx static serving

The browser receives a local file handle when the user selects a `.waview` file. The selected archive stays on the local machine. The web app reads the ZIP central directory, `meta/version.json`, `data.json`, avatars, and media slices directly through browser file APIs.

For large archives, the web viewer avoids parsing the full message list during startup. It first opens the archive with chat/media/call/contact metadata, then parses messages when a chat is opened. This was added because very large `.waview` files can contain hundreds of thousands of messages and very large `data.json` payloads.

### Running The Web Viewer

From the `web_viewer/` folder:

```bash
npm install
npm run dev
```

Or through Docker:

```bash
docker compose up --build
```

Then open:

```text
http://localhost:8088
```

For full web-viewer-specific documentation, see:

```text
web_viewer/README.md
```

</details>

## Architecture Overview

<details>
<summary><strong>Open section</strong></summary>

<br>


The app is organized into clear layers:

### Root / Device Access Layer

- verifies root access
- locates WhatsApp / WhatsApp Business data
- copies protected files safely for extraction

### Extraction Layer

- parses copied source data
- transforms raw records into app export models
- prepares archive-ready structures

### Archive / Export Layer

- writes the final `.waview` package
- records export metadata
- packages media/index data with the structured export

### Repository Layer

- coordinates extraction flow
- coordinates viewer loading flow
- owns archive read, sync, and media-resolution logic

### ViewModel Layer

- manages UI-facing state for home, extraction, and viewer flows
- coordinates navigation-level screen state and screen actions

### UI Layer

- Compose screens for extraction, settings, home, and viewer
- dedicated viewer render models for chat list and chat timeline
- themed offline chat-style browsing experience

</details>

## Current App Architecture In Practice

<details>
<summary><strong>Open section</strong></summary>

<br>


The current app combines two major phases of work:

### Handoff Phase 1: Core `v12` extraction + viewer stabilization

This phase established the core product:

- rooted extraction flow
- `.waview` packaging
- structured export model
- media indexing
- avatar handling
- deleted / unavailable media safety
- viewer repository and archive load pipeline
- stable offline viewer foundation

### Handoff Phase 2: post-`v12` viewer performance and loading redesign

This phase focused on the heavy viewer problems seen with real data:

- chat open/reopen jank reduction
- chat-list flattening
- route-first navigation behavior
- preload strategy changes
- loading-state redesign
- wallpaper draw-cost cleanup
- render-model based chat UI preparation
- memory pressure tuning
- profiling-backed narrowing of fixed screen mount cost

### Handoff Phase 3: large archive hardening

This phase focused on very large exports and viewer files:

- streaming `data.json` export
- direct media-to-archive zipping
- direct Downloads `.partial` output before final rename
- Android viewer SQLite message store
- lazy chat message loading for high-volume archives
- regular WhatsApp package/path fallback hardening

The current codebase reflects all three phases together.

</details>

## Major Development Setbacks And Fixes

<details>
<summary><strong>Open section</strong></summary>

<br>


The project went through several practical problems during production. The most important categories were:

### 1. Media reliability and missing-file handling

Problem:

- real exports contain mixed media states
- some media can be missing, deleted, or only partially recoverable
- unsafe media assumptions can crash the viewer or create false "working" states

Fix direction:

- indexed media state tracking
- guarded media resolution pipeline
- validity checks before playback/open
- explicit deleted/missing placeholders
- synchronization around archive reads

### 2. Archive read safety

Problem:

- large archive access plus repeated reads can become fragile if handled loosely

Fix direction:

- archive indexing
- coordinated zip access
- guarded extraction-to-cache behavior
- repository-owned resolution logic instead of scattered screen-level file handling

### 3. Viewer performance on real chat history

Problem:

- large real chats caused slow opens, jank, and heavy work at the wrong time
- very large archives could exceed Android heap limits if every message was decoded and cached in memory

Fix direction:

- route-first chat navigation
- render-model preparation
- chat-list row model flattening
- timeline prebuild work
- preload strategy redesign
- SQLite-backed message storage
- recent-message window loading
- large-archive on-demand chat loading
- lighter wallpaper/background drawing path

### 4. Very large export stability

Problem:

- very large `msgstore.db` files can contain hundreds of thousands of messages
- serializing all messages as one in-memory object graph can hit heap limits
- copying the full media tree into app cache before zipping can exceed emulator/device storage
- moving a completed huge archive at the end can fail if storage is tight

Fix direction:

- count and stream messages instead of retaining all messages in memory
- write `data.json` incrementally
- scan and filter media without staging the full media tree
- add selected media into the archive one file at a time
- write the output archive directly to Downloads as `.waview.partial`, then rename after success

### 5. Loading-state quality

Problem:

- slow transitions felt broken when heavy work happened invisibly

Fix direction:

- explicit loading states
- current-route chat readiness state
- staged preparation before full chat rendering

### 6. Theme and system bar handling

Problem:

- newer Android SDK targets deprecated older direct system bar coloring patterns

Fix direction:

- edge-to-edge behavior retained
- icon appearance handling kept
- deprecated direct viewer-theme system bar color writes removed

</details>

## Handoff Summary

<details>
<summary><strong>Open section</strong></summary>

<br>


This repository is the result of multiple handoff-style development sessions.

### Handoff 1: Foundation and stability

The first major handoff produced the main extractor/viewer base:

- root access workflow
- export pipeline
- archive structure
- broad model coverage
- media-safety groundwork
- initial viewer architecture

### Handoff 2: Performance-focused viewer iteration

The later handoff focused on real-world viewer behavior under heavier data:

- loading redesign
- preloading changes
- route-level viewer state work
- render model introduction
- wallpaper/render-cost cleanup
- smoother chat list and chat open behavior

### Handoff 3: large export and huge viewer hardening

The latest hardening focused on archives large enough to expose memory and storage limits:

- streaming export data
- direct media zipping
- direct `.partial` archive output
- SQLite-backed Android viewer message import
- on-demand chat windows for huge archives

### Current State

The current app keeps the stable extractor/archive design from the first major handoff, the smoother viewer behavior introduced in the later performance session, and the large-archive safety work added after high-volume testing.

</details>

## Performance Notes

<details>
<summary><strong>Open section</strong></summary>

<br>


Internal testing showed the app working well on:

- the main Samsung Galaxy A13 source device
- Samsung Galaxy S22 Ultra
- Pixel 4 Android Emulator

Exact profiled CPU and RAM numbers for the Galaxy S22 Ultra were not preserved as formal public metrics in the repository documentation, so this README does not claim specific resource figures that were not recorded. The practical outcome from internal testing was that the viewer behavior was acceptable and working correctly on that device.

</details>

## Privacy And Data Handling

<details>
<summary><strong>Open section</strong></summary>

<br>


- WA Sensai is intended for local use
- the project handles exported private messaging data
- no public sample dataset is included in this repository
- this README intentionally avoids exposing personal dataset names or internal private identifiers
- viewer archives should be handled carefully because they may contain sensitive personal history

</details>

## Build And Run Notes

<details>
<summary><strong>Open section</strong></summary>

<br>


### Requirements

- Android Studio
- Android SDK compatible with `compileSdk 35`
- rooted Android device for extraction use cases
- WhatsApp Business for the main validated path

### Important Notes

- extraction functionality depends on root access
- viewer functionality depends on valid `.waview` exports
- Android Studio or Gradle builds will regenerate local folders such as `.gradle`, `.idea`, `.kotlin`, and `build`
- `local.properties` is machine-specific and should not be committed publicly

</details>

## Screenshots

Main app screens:

<p align="center">
  <img src="./screenshots/home-accent-1.png" alt="Home screen with accent color 1" width="260" />
  <img src="./screenshots/home-accent-2.png" alt="Home screen with accent color 2" width="260" />
  <img src="./screenshots/extract-options.png" alt="Extract options screen" width="260" />
</p>

<p align="center">
  <img src="./screenshots/sync-log.png" alt="Sync log screen" width="260" />
  <img src="./screenshots/chat-list-edited.png" alt="Edited chat list screen for privacy" width="260" />
  <img src="./screenshots/chat-window-edited.png" alt="Edited chat window screen for privacy" width="260" />
</p>

Important note:

- two screenshots were intentionally edited for privacy protection
- those edited screenshots were also visually pushed more toward a GTA San Andreas style vibe
- the edited screenshots are the chat list screen and the chat window screen

## Web Viewer Screenshot

Standalone web viewer beta:

<p align="center">
  <img src="./screenshots/web_viewer.png" alt="WA Sensai standalone web viewer beta" width="780" />
</p>

Important note:

- this screenshot shows the separate browser-based web viewer
- the web viewer is intended for local PC viewing of existing `.waview` files
- it is currently beta and should not be treated as the production Android viewer

## Project Structure

<details>
<summary><strong>Open section</strong></summary>

<br>


This section describes the current repository layout and the purpose of the main Kotlin files.

```text
WASensai/
├── app/                              # Android application module
│   ├── src/main/java/com/mazin/wasensai/
│   │   ├── MainActivity.kt           # Activity entry point, edge-to-edge host
│   │   ├── MainActivityContent.kt    # Top-level Compose content and app theme host
│   │   ├── WaSensaiApp.kt            # Application class and Hilt entry point
│   │   ├── data/
│   │   │   ├── datastore/
│   │   │   │   └── SettingsDataStore.kt      # Theme/accent preference storage
│   │   │   ├── model/                         # Export and viewer data models
│   │   │   │   ├── CallLog.kt                # Call history model
│   │   │   │   ├── Chat.kt                   # Chat/session metadata model
│   │   │   │   ├── Contact.kt                # Contact model
│   │   │   │   ├── ExportInfo.kt             # Export metadata model
│   │   │   │   ├── Group.kt                  # Group and participant-related model data
│   │   │   │   ├── Label.kt                  # Label and message-label mapping models
│   │   │   │   ├── MediaEntry.kt             # Media index / media archive entry model
│   │   │   │   ├── Mention.kt                # Mention model
│   │   │   │   ├── Message.kt                # Message model
│   │   │   │   ├── MessageEdit.kt            # Message edit history model
│   │   │   │   ├── Poll.kt                   # Poll and vote models
│   │   │   │   ├── Reaction.kt               # Message reaction model
│   │   │   │   ├── StatusUpdate.kt           # Status model
│   │   │   │   └── VCard.kt                  # Shared contact card model
│   │   │   └── repository/
│   │   │       ├── ExtractRepository.kt      # Extraction orchestration and data assembly
│   │   │       ├── ViewerMessageStore.kt     # SQLite-backed Android viewer message store
│   │   │       └── ViewerRepository.kt       # Archive open/load/sync/media resolution logic
│   │   ├── export/
│   │   │   └── ExportManager.kt              # `.waview` archive packaging writer
│   │   ├── root/
│   │   │   └── RootFileAccess.kt             # Root-driven file discovery/copy access
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   └── ChatWallpaper.kt          # Chat wallpaper surface for viewer chats
│   │   │   ├── navigation/
│   │   │   │   └── NavGraph.kt               # Compose navigation graph
│   │   │   ├── screens/
│   │   │   │   ├── about/
│   │   │   │   │   └── AboutScreen.kt                 # About page
│   │   │   │   ├── extract/
│   │   │   │   │   ├── ExtractDesignUtils.kt          # Shared extraction UI helpers
│   │   │   │   │   ├── ExtractFlowScreen.kt           # Main extraction flow container
│   │   │   │   │   ├── ExtractForceStopScreen.kt      # Stop/force-stop guidance screen
│   │   │   │   │   ├── ExtractOptionsScreen.kt        # Extraction options UI
│   │   │   │   │   ├── ExtractProgressScreen.kt       # Live extraction progress UI
│   │   │   │   │   ├── ExtractRootScreen.kt           # Root status / prerequisite screen
│   │   │   │   │   └── ExtractScanScreen.kt           # Scan / detection screen
│   │   │   │   ├── home/
│   │   │   │   │   └── HomeScreen.kt                  # Main launcher/home screen
│   │   │   │   ├── settings/
│   │   │   │   │   ├── AccentColorPickerScreen.kt     # Accent color picker
│   │   │   │   │   └── SettingsScreen.kt              # App settings screen
│   │   │   │   └── viewer/
│   │   │   │       ├── CallsScreen.kt                 # Exported call log viewer screen
│   │   │   │       ├── ChatListRenderModels.kt        # Prebuilt chat-list UI row models
│   │   │   │       ├── ChatListScreen.kt              # Chat list and sync status UI
│   │   │   │       ├── ChatRenderModels.kt            # Prebuilt chat timeline render models
│   │   │   │       ├── ChatScreen.kt                  # Main conversation viewer screen
│   │   │   │       ├── ContactInfoScreen.kt           # Contact/group info viewer
│   │   │   │       ├── FullScreenMediaScreen.kt       # Full-screen image/video viewer
│   │   │   │       ├── MediaGalleryScreen.kt          # Shared media/documents gallery
│   │   │   │       ├── SettingsScreen.kt              # Viewer-specific settings UI
│   │   │   │       ├── StarredScreen.kt               # Starred messages screen
│   │   │   │       ├── ViewerScreen.kt                # Viewer root and section navigation
│   │   │   │       └── ViewerUiState.kt               # Viewer UI state models
│   │   │   └── theme/
│   │   │       ├── Color.kt                  # Base app palette values
│   │   │       ├── Theme.kt                  # Main app theme
│   │   │       ├── Type.kt                   # Typography setup
│   │   │       └── ViewerTheme.kt            # Dedicated viewer theme
│   │   ├── utils/
│   │   │   ├── DateUtils.kt                  # Date/time formatting helpers
│   │   │   └── FileUtils.kt                  # File/path helper utilities
│   │   └── viewmodel/
│   │       ├── ExtractViewModel.kt           # Extraction UI state and actions
│   │       ├── HomeViewModel.kt              # Home/settings state bridge
│   │       └── ViewerViewModel.kt            # Viewer state, preload, and navigation logic
│   ├── src/main/res/                 # Drawables, fonts, launcher assets, XML config
│   └── src/main/AndroidManifest.xml  # App manifest, permissions, intents, provider
├── gradle/                           # Gradle version catalog and wrapper support files
├── web_viewer/                        # Standalone beta browser viewer for `.waview` archives
│   ├── Dockerfile                     # Web viewer production container build
│   ├── docker-compose.yml             # Local nginx web viewer service on port 8088
│   ├── nginx.conf                     # Static web viewer serving config
│   ├── package.json                   # Web viewer scripts and dependencies
│   ├── public/                        # Web viewer public assets and chat wallpapers
│   └── src/
│       ├── components/                # Web viewer React UI components
│       ├── context/                   # Web viewer theme context
│       ├── hooks/                     # Worker controller hook
│       ├── lib/                       # ZIP reader and formatting helpers
│       ├── types/                     # `.waview` and worker TypeScript contracts
│       └── workers/                   # Browser worker archive parser
├── screenshots/                       # README screenshots, including web viewer screenshot
├── build.gradle.kts                  # Root Gradle config
├── settings.gradle.kts               # Module inclusion and repository setup
├── gradle.properties                 # Gradle runtime options
├── gradlew / gradlew.bat             # Gradle wrapper scripts
└── WASensai_FULL_v12.md              # Internal development handoff documentation
```

</details>

## Repository Notes

<details>
<summary><strong>Open section</strong></summary>

<br>


- The repository currently includes internal handoff documentation used during development.
- The public-facing README is intentionally privacy-safe.
- The `web_viewer/` folder is a separate beta standalone browser viewer, not part of the rooted Android extraction runtime.
- If this project is uploaded publicly, review every document and asset before publishing.
- Consider whether internal handoff files should remain in the public repository.
- Review the web viewer separately before treating it as production-ready.

</details>

## Internal Use Disclaimer

<details>
<summary><strong>Open section</strong></summary>

<br>


WA Sensai was built around a real personal workflow and a real rooted Android device environment. The app worked well in that validated internal use case, but broad public compatibility across all devices, all Android variants, and all WhatsApp package variations should not be assumed without further testing.

The standalone web viewer was added later as a practical beta tool for opening `.waview` files on PC. It is useful for local urgent viewing, but it should not be described as production-level ready yet.

</details>

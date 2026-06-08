<div align="center">

# WA Sensai Web Viewer

Standalone browser-based `.waview` archive viewer for WA Sensai exports.

Built for local, offline inspection of WA Sensai backup archives without uploading the archive to any server.

</div>

---

## Table Of Contents

- [Scope And Intent](#scope-and-intent)
- [Quick Glance](#quick-glance)
- [What This Web Viewer Does](#what-this-web-viewer-does)
- [What This Web Viewer Does Not Do](#what-this-web-viewer-does-not-do)
- [How `.waview` Loading Works](#how-waview-loading-works)
- [Large Archive Loading Design](#large-archive-loading-design)
- [ZIP And Byte-Range Reader](#zip-and-byte-range-reader)
- [Expected `.waview` Archive Shape](#expected-waview-archive-shape)
- [Data Model Coverage](#data-model-coverage)
- [Media Resolution Logic](#media-resolution-logic)
- [UI Architecture](#ui-architecture)
- [Worker Architecture](#worker-architecture)
- [Runtime Flow](#runtime-flow)
- [Performance Notes](#performance-notes)
- [Privacy And Data Handling](#privacy-and-data-handling)
- [Local Development](#local-development)
- [Docker Usage](#docker-usage)
- [Cleaning Generated Files](#cleaning-generated-files)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Known Limits](#known-limits)
- [Repository Notes](#repository-notes)
- [Internal Use Disclaimer](#internal-use-disclaimer)

> [!TIP]
> This README is only for the standalone `web_viewer` app. It does not document the full Android extractor application.

## Scope And Intent

<details>
<summary><strong>Open section</strong></summary>

<br>

The WA Sensai Web Viewer is a separate web application inside the main WA Sensai repository.

Its purpose is to open an existing WA Sensai `.waview` archive in a desktop browser and display the exported WhatsApp-style data as a local viewer.

It was created as a viewer-only experience:

- select a `.waview` file from local disk
- read archive metadata in the browser
- display chats, groups, calls, media references, reactions, polls, and related archive metadata
- preview avatars and media when the bytes are embedded in the archive
- keep very large archives practical by using direct file slicing and lazy message loading

The web viewer is intentionally separate from the Android app. It does not need root, Android APIs, or the Android extractor runtime. It only needs a valid `.waview` file already produced by WA Sensai.

</details>

## Quick Glance

| Item | Details |
| --- | --- |
| App type | Standalone web viewer |
| Main input | WA Sensai `.waview` archive |
| Upload model | No server upload; browser reads the local file handle |
| Frontend stack | React, TypeScript, Vite, Tailwind CSS |
| Worker stack | Browser Web Worker, custom ZIP central-directory reader |
| UI style | AssetFlow-inspired glass/dark/accent interface |
| Runtime target | Modern Chromium-based desktop browser |
| Docker target | Static nginx container on port `8088` |
| Large archive strategy | Lazy metadata-first loading and per-chat message parsing |
| Media strategy | Resolve archive entries first, then load media bytes on demand |

## What This Web Viewer Does

<details>
<summary><strong>Open section</strong></summary>

<br>

The web viewer opens a `.waview` file and reconstructs a read-only archive browsing experience.

Current responsibilities:

1. Open a local `.waview` file through the browser file picker.
2. Read the ZIP end records and central directory from the file.
3. Validate WA Sensai archive metadata from `meta/version.json`.
4. Read `data.json` from the archive.
5. Build chat rows, call rows, media indexes, contact names, avatar aliases, reactions, polls, and summary metadata.
6. Render the archive in a polished local viewer UI.
7. Load chat messages when needed.
8. Load avatars and media only when the UI asks for them.
9. Show unavailable media as unavailable instead of pretending it exists.
10. Keep all archive bytes local to the browser session.

Main viewer sections:

- Chats
- Groups
- Media-filtered chats
- Starred
- Calls
- Settings / archive metadata

</details>

## What This Web Viewer Does Not Do

<details>
<summary><strong>Open section</strong></summary>

<br>

This web viewer does not:

- extract WhatsApp data from a phone
- require root
- connect to WhatsApp
- create `.waview` files
- modify `.waview` files
- upload archives to a backend
- store the selected archive in Docker or nginx
- run server-side archive parsing
- provide a public hosted multi-user service

The Docker container only serves the static web app files. The selected `.waview` remains on the user's machine and is read by browser APIs.

</details>

## How `.waview` Loading Works

<details>
<summary><strong>Open section</strong></summary>

<br>

The loading flow is designed around browser-local file access.

High-level flow:

1. User selects a `.waview` file.
2. React sends the `File` object to a Web Worker.
3. The worker reads the ZIP end record from the end of the file.
4. The worker locates ZIP64 records when needed.
5. The worker reads the central directory.
6. The worker validates `meta/version.json`.
7. The worker reads `data.json`.
8. The worker builds archive indexes.
9. The UI switches from upload state to viewer state.
10. Messages, avatars, and media are loaded lazily after the archive is open.

Important behavior:

- The browser does not upload the file.
- The worker receives the local `File` handle.
- Media preview uses `File.slice()` and object URLs.
- Object URLs are revoked when the archive closes or when replaced.
- Reloading the browser page loses the selected local file handle, so the archive must be selected again after reload.

</details>

## Large Archive Loading Design

<details>
<summary><strong>Open section</strong></summary>

<br>

Large `.waview` files can contain hundreds of thousands of messages and a very large `data.json`.

The large-archive path exists because fully parsing a huge `data.json` during startup can freeze the worker for a long time.

Current large archive behavior:

- if `data.json` is at least `80 MB`, the worker enters large archive mode
- the worker reads `data.json` as text
- the worker scans top-level JSON keys manually
- the worker extracts metadata arrays needed for the first viewer screen
- the huge `messages` array is skipped during startup
- the archive opens after chats, contacts, calls, media refs, reactions, polls, and summary metadata are prepared
- messages are parsed later when a chat is selected

This is the main reason the largest tested archive can move past the loading screen:

- `data.json` can be roughly `230 MB`
- message count can be above `500,000`
- media index rows can be above `70,000`
- ZIP entry count can be above `58,000`

The viewer avoids doing all message work at startup.

Large archive progress phases:

| Progress | Meaning |
| --- | --- |
| `8%` | ZIP directory read starts |
| `24%` | archive metadata validation |
| `38%` | `data.json` read starts |
| `48%` | top-level metadata scan starts |
| `72%` | lazy metadata indexing starts |
| `74%` | contacts and avatars |
| `78%` | media references |
| `84%` | reactions and polls |
| `88%` | calls |
| `92%` | chats |
| `100%` | archive is ready |

Tradeoff:

- startup becomes practical for huge archives
- first open of a large chat may still take time, because that chat's messages are parsed on demand

</details>

## ZIP And Byte-Range Reader

<details>
<summary><strong>Open section</strong></summary>

<br>

The web viewer uses a custom ZIP reader in `src/lib/zip-reader.ts`.

It exists because generic ZIP libraries can be too expensive for multi-gigabyte archives if they enumerate or inflate too much data.

The custom reader handles:

- normal ZIP end of central directory record
- ZIP64 locator
- ZIP64 end of central directory record
- central directory parsing
- local file header parsing
- data offset resolution
- stored entry slicing
- deflate entry decompression through browser `DecompressionStream`

Important implementation details:

- central directory is read from the end of the file
- each central-directory entry is indexed by normalized path
- media bytes are not copied during startup
- stored media entries can be represented as slices of the original file
- compressed metadata entries can be decompressed directly without scanning the full ZIP again

Supported compression modes:

- `0` / STORE: read with `File.slice()`
- `8` / DEFLATE: decompressed with browser `DecompressionStream("deflate-raw")`

Unsupported compression modes throw a clear error.

</details>

## Expected `.waview` Archive Shape

<details>
<summary><strong>Open section</strong></summary>

<br>

The web viewer expects a WA Sensai archive with this broad structure:

```text
archive.waview
├── meta/
│   └── version.json
├── data.json
├── avatars/
│   ├── me.j
│   ├── <contact>_s.whatsapp.net.j
│   ├── <group>_g.us.j
│   └── <newsletter>_newsletter.j
└── media/
    └── ...
```

`meta/version.json` is used to validate the archive format.

`data.json` contains structured exported data such as:

- export info
- chats
- contacts
- messages
- media index
- call logs
- reactions
- polls
- labels
- mentions
- vcards
- statuses
- message edit history
- starred message references

`avatars/` contains contact/group avatar files when available.

`media/` contains embedded media bytes when the archive includes them.

The web viewer does not assume all referenced media exists. Real archives may contain media rows whose bytes are missing, deleted, or not embedded.

</details>

## Data Model Coverage

<details>
<summary><strong>Open section</strong></summary>

<br>

TypeScript data models live in `src/types/waview.ts`.

The current viewer understands these archive-level entities:

- `ExportInfo`
- `Chat`
- `Contact`
- `Group`
- `GroupParticipant`
- `Message`
- `MediaEntry`
- `Reaction`
- `Poll`
- `PollOptionEntry`
- `PollVote`
- `CallLog`
- `CallParticipant`
- `Label`
- `LabeledMessage`
- `Mention`
- `VCard`
- `StatusUpdate`
- `MessageEdit`

Viewer-facing models:

- `ArchiveSummary`
- `ChatRow`
- `ViewerMessage`
- `ViewerCall`
- `MediaBlobResult`
- `AvatarBlobResult`

Rendered directly:

- chats
- groups
- messages
- images
- videos
- audio
- documents
- stickers
- locations
- polls
- reactions
- deleted-message placeholders
- expired view-once placeholders
- calls
- archive summary metadata

Surfaced as metadata counts:

- labels
- labeled messages
- mentions
- vcards
- statuses
- message edits

</details>

## Media Resolution Logic

<details>
<summary><strong>Open section</strong></summary>

<br>

Media resolution is handled in the worker.

The web viewer does not trust `data.json` blindly. A media row is considered previewable only when an actual ZIP entry can be resolved.

Resolution inputs:

- `media_index[].relative_path`
- `media_index[].file_name`
- `media_index[].mime_type`
- ZIP central-directory entries

Resolution behavior:

- remove duplicate `media/` prefixes
- normalize slash direction and casing
- try exact archive path candidates
- infer file extension from MIME type when needed
- handle `Sent/` path variants
- map WhatsApp Business path shapes to regular WhatsApp archive paths when needed
- use filename indexes for fallback matching

Important path variant supported:

```text
data.json: media/WhatsApp Business/Media/...
zip entry: media/WhatsApp/Media/...
```

This matters because some archives reference `WhatsApp Business` paths while the embedded media tree uses `WhatsApp` paths.

Media states:

- `downloaded`: bytes are resolvable in the archive
- `not_embedded`: referenced by metadata but bytes are not available
- `unavailable`: attempted read failed or browser cannot read that entry

The UI shows unavailable media clearly instead of looping forever.

</details>

## UI Architecture

<details>
<summary><strong>Open section</strong></summary>

<br>

The UI is a React app styled with Tailwind CSS.

Design direction:

- standalone app shell
- left navigation rail/sidebar
- top header
- glass surfaces
- dark mode
- accent colors
- WhatsApp-style chat wallpaper
- dense archive viewer layout
- AssetFlow-inspired interaction and visual language

Main UI files:

```text
src/App.tsx
src/components/viewer/UploadDropzone.tsx
src/components/viewer/ViewerShell.tsx
src/components/viewer/ChatSidebar.tsx
src/components/viewer/ChatTimeline.tsx
src/components/viewer/MessageBubble.tsx
src/components/viewer/MediaPreviewDialog.tsx
src/components/viewer/CallsView.tsx
src/components/viewer/InfoPanel.tsx
src/components/viewer/ThemeControls.tsx
```

Reusable UI primitives:

```text
src/components/ui/button.tsx
src/components/ui/dialog.tsx
src/components/ui/loader.tsx
src/components/ui/progress.tsx
src/components/ui/scroll-area.tsx
src/components/ui/tabs.tsx
src/components/ui/tooltip.tsx
```

Theme files:

```text
src/context/theme.tsx
src/context/theme-core.ts
src/index.css
tailwind.config.js
```

The theme system stores local preferences in `localStorage`:

- dark/light mode
- glass mode
- accent color

</details>

## Worker Architecture

<details>
<summary><strong>Open section</strong></summary>

<br>

Archive parsing runs inside `src/workers/waview.worker.ts`.

The worker owns:

- archive opening
- ZIP directory reading
- metadata validation
- data parsing
- large archive lazy mode
- contact-name maps
- avatar alias maps
- media indexes
- call list preparation
- chat row preparation
- message parsing
- media blob reads
- avatar blob reads

Worker requests are typed in `src/types/worker.ts`.

Request types:

- `OPEN_ARCHIVE`
- `GET_MESSAGES`
- `GET_MEDIA`
- `GET_AVATAR`
- `CLOSE_ARCHIVE`

Response types:

- `PROGRESS`
- `ARCHIVE_READY`
- `MESSAGES_READY`
- `MEDIA_READY`
- `MEDIA_ERROR`
- `AVATAR_READY`
- `AVATAR_ERROR`
- `CLOSED`
- `ERROR`

The React hook `src/hooks/useWaviewArchive.ts` wraps the worker and exposes a UI-friendly controller object.

</details>

## Runtime Flow

<details>
<summary><strong>Open section</strong></summary>

<br>

Runtime flow from the UI:

1. `UploadDropzone` receives the selected file.
2. `useWaviewArchive.openArchive(file)` posts `OPEN_ARCHIVE`.
3. Worker reports progress.
4. Worker returns `ARCHIVE_READY`.
5. `App.tsx` switches from upload UI to `ViewerShell`.
6. `ViewerShell` selects the first chat by default.
7. Selecting a chat posts `GET_MESSAGES`.
8. Message rows render in `ChatTimeline`.
9. `ChatAvatar` asks for avatars when visible.
10. `MessageBubble` asks for inline media when previewable.
11. `MediaPreviewDialog` asks for clicked media when opened.
12. Object URLs are created for loaded blobs and revoked later.

The app intentionally avoids reading all media bytes at startup.

</details>

## Performance Notes

<details>
<summary><strong>Open section</strong></summary>

<br>

Performance strategy:

- parse ZIP directory once
- use path maps instead of repeated ZIP scans
- avoid loading media bytes until needed
- use Web Worker for heavy parsing
- use `@tanstack/react-virtual` for long chat lists and message timelines
- use lazy large-archive mode for huge `data.json`
- use object URLs for media previews
- revoke object URLs to reduce memory leaks

Large archive issues fixed during development:

1. Full ZIP library enumeration was too slow on huge archives.
2. Full `JSON.parse` of huge `data.json` blocked opening.
3. Media resolution had an accidental `media rows x ZIP entries` fallback.
4. Missing media could cause repeated load attempts.
5. Avatar misses could retry repeatedly.

Current design avoids those known bottlenecks.

Important remaining tradeoff:

- a very large individual chat can still take time the first time it is opened, because messages are parsed on demand in large archive mode

</details>

## Privacy And Data Handling

<details>
<summary><strong>Open section</strong></summary>

<br>

The web viewer is local-first.

Privacy behavior:

- the `.waview` file is selected with the browser file picker
- the archive is not uploaded to nginx
- the archive is not uploaded to Docker
- the archive is not sent to a backend
- parsing happens in the browser
- media previews are created from local file slices
- selected file access exists only for the current browser session

Important privacy note:

- `.waview` archives can contain private message history and media
- do not commit real `.waview` files
- do not put private archive samples inside `public/`, `src/`, or the repository

</details>

## Local Development

<details>
<summary><strong>Open section</strong></summary>

<br>

Install dependencies:

```bash
npm install
```

Start development server:

```bash
npm run dev
```

Build production files:

```bash
npm run build
```

Run lint:

```bash
npm run lint
```

Preview production build locally:

```bash
npm run preview
```

Generated local folders:

- `node_modules/`
- `dist/`

These are ignored by Git.

</details>

## Docker Usage

<details>
<summary><strong>Open section</strong></summary>

<br>

Build and run:

```bash
docker compose up --build
```

Detached mode:

```bash
docker compose up -d --build
```

Open:

```text
http://localhost:8088
```

Stop container:

```bash
docker compose down
```

Remove local image and compose runtime artifacts:

```bash
docker compose down --rmi local --volumes --remove-orphans
```

Docker design:

- build stage uses Node
- runtime stage uses nginx
- nginx serves static files only
- archive parsing still happens in the browser
- `.waview` files are not copied into the container

</details>

## Cleaning Generated Files

<details>
<summary><strong>Open section</strong></summary>

<br>

Before committing or pushing, generated folders can be removed:

```powershell
Remove-Item -LiteralPath .\dist -Recurse -Force
Remove-Item -LiteralPath .\node_modules -Recurse -Force
```

Docker cleanup:

```bash
docker compose down --rmi local --volumes --remove-orphans
```

Files that should stay committed:

- `package.json`
- `package-lock.json`
- `Dockerfile`
- `docker-compose.yml`
- `nginx.conf`
- source files
- public UI assets

Files/folders that should not be committed:

- `node_modules/`
- `dist/`
- local `.waview` archives
- private backup files
- machine-specific logs

</details>

## Project Structure

<details>
<summary><strong>Open section</strong></summary>

<br>

```text
web_viewer/
├── Dockerfile                         # Multi-stage production build
├── docker-compose.yml                 # Local nginx container on port 8088
├── nginx.conf                         # Static app serving config
├── package.json                       # Scripts and dependencies
├── package-lock.json                  # Reproducible npm install lockfile
├── index.html                         # Vite HTML entry
├── public/
│   ├── favicon.svg                    # App icon
│   ├── icons.svg                      # UI icon asset
│   ├── chat_wallpaper_dark.png        # Dark chat wallpaper
│   └── chat_wallpaper_light.png       # Light chat wallpaper
├── src/
│   ├── App.tsx                        # Top-level app switch: upload vs viewer
│   ├── main.tsx                       # React mount
│   ├── index.css                      # Base theme, glass mode, wallpaper CSS
│   ├── components/
│   │   ├── ui/                        # Reusable UI primitives
│   │   └── viewer/                    # Viewer-specific UI components
│   ├── context/
│   │   ├── theme.tsx                  # Theme provider and localStorage sync
│   │   └── theme-core.ts              # Theme context hook
│   ├── hooks/
│   │   └── useWaviewArchive.ts        # Worker controller hook
│   ├── lib/
│   │   ├── format.ts                  # Formatting and message type helpers
│   │   ├── utils.ts                   # Class merge helper
│   │   └── zip-reader.ts              # Custom ZIP/ZIP64 byte-range reader
│   ├── types/
│   │   ├── waview.ts                  # Archive and viewer data models
│   │   └── worker.ts                  # Worker request/response contracts
│   └── workers/
│       └── waview.worker.ts           # Archive parser and lazy media/message loader
├── tailwind.config.js                 # Tailwind theme and animation config
├── tsconfig*.json                     # TypeScript configs
└── vite.config.ts                     # Vite config
```

</details>

## Troubleshooting

<details>
<summary><strong>Open section</strong></summary>

<br>

### The app stays on the upload screen after selecting a file

Possible causes:

- selected file is not a `.waview`
- archive is missing `meta/version.json`
- archive format version is unsupported
- browser lost file access after page reload

### The app appears stuck around `38%`

This means the worker is reading `data.json`.

For very large archives, this can still take time because the browser has to read a large text entry from local disk.

### The app appears stuck around `72%`

This means metadata indexing is running.

Current worker builds more detailed progress after this point:

- `74%` contacts/avatars
- `78%` media references
- `84%` reactions/polls
- `88%` calls
- `92%` chats

If it never moves past one of those, the current phase is the bottleneck to inspect.

### Media does not open

Possible causes:

- media is referenced in `data.json` but not embedded in the archive
- media path cannot be resolved to a ZIP entry
- browser cannot decode the media type
- media entry uses an unsupported compression method

The UI should show unavailable media instead of repeatedly loading forever.

### Avatar does not show

Possible causes:

- avatar reference exists but no avatar bytes are embedded
- avatar alias cannot be matched to the chat JID
- avatar file is present but unreadable

The UI falls back to initials or group icon.

</details>

## Known Limits

<details>
<summary><strong>Open section</strong></summary>

<br>

Known limits:

- browser file handle is lost after reload
- large `data.json` still has to be read from disk
- large archive mode scans stored `data.json` text when opening a chat
- very large individual chats may take time on first open
- metadata-only entities such as labels and vcards are counted but do not yet have dedicated detail pages
- the app is intended for modern Chromium-based browsers
- unsupported ZIP compression methods cannot be read
- this viewer is read-only

</details>

## Repository Notes

<details>
<summary><strong>Open section</strong></summary>

<br>

This folder is a standalone web app inside the larger WA Sensai repository.

Commit this folder as source:

- app source
- Docker files
- config files
- lockfile
- public UI assets

Do not commit generated folders or private archives.

Current ignored generated folders:

- `node_modules/`
- `dist/`

</details>

## Internal Use Disclaimer

<details>
<summary><strong>Open section</strong></summary>

<br>

The WA Sensai Web Viewer was built for a real local WA Sensai archive-viewing workflow.

It is designed to open private `.waview` files locally and should be treated carefully because archives may contain sensitive message history and media.

Broad compatibility across every possible `.waview` variant, browser, and archive shape should not be assumed without testing.

</details>

## New Features

- **Multi-Format Offline Reader:** View PDF, Word (`.doc`, `.docx`, `.rtf`), Excel (`.xls`, `.xlsx`, `.xlsm`, `.xltx`), PowerPoint (`.ppt`, `.pptx`), and Text/Data (`.txt`, `.md`, `.csv`, `.json`, `.xml`, `.log`, `.ini`, `.cfg`) documents offline with **Original** and **PDF View** modes.
- **Separate Compressed ABI Builds:** Publishes dedicated compressed **ARMv7** (`armeabi-v7a`), **ARMv8** (`arm64-v8a`), and **Universal** APKs with R8 shrinking, resource compression, and consistent signing for smooth updates.
- **Folder-Based Settings:** Reorganized all settings into clean folders for **PDF**, **DOC**, **EXCEL**, **PPT**, **TEXT**, **Home Screen Documents**, **Appearance**, **Storage**, and **Cache**.
- **Per-Format Reading Controls & State Saving:** Saves view mode (`Single Page` / `Continuous`), orientation (`Auto`, `Portrait`, `Landscape`), zoom preferences, settings, and open menu state automatically before exit.
- **Full Offline Storage Support:** Scans internal/external storage, optional hidden `(.)` dot folders, and custom SAF folders, plus batch share, print, rename, and delete actions.

## Improvements

- **Low-Storage Lossless Fast Cache:** Uses `WEBP_LOSSLESS` (Android 11+) / `PNG` fallback (Android 9–10) for landing previews, GZIP-compressed parsed caches, and an adaptive 32–96 MB RAM LRU cache for fast home and reader loading without lowering reader quality.
- **Original Reader Quality:** Preserves source document rendering quality in the reader without downscaling or artificial compression.
- **Compact Chrome & Touch Sizing:** Refined the Home and Settings top bars to `42.dp` height with `42.dp` icon touch targets and narrowed the document category pill bar to `20.dp` row height.
- **Smart Default Pills & Always-On Format Support:** Hides the **TEXT** pill on Home by default (`PDF`, `DOC`, `EXCEL`, `PPT` active) while keeping all document scanners, Settings, and external file-opening features enabled even when any pill is hidden.
- **Low-Power Background Lifecycle:** Switches to an ultra-low-resource foreground/background footprint when minimizing to another app, and shuts down completely with zero background services when closed from the Recent Apps menu.
- **Theme-Matched System Bars & Loading State:** Matches status bar and navigation bar colors to the active System/Dark/Light theme and displays a loading indicator for up to 5 seconds before showing empty-state artwork.

## Fixes

- **Edge-to-Edge System Bar Insets:** Fixed status bar and navigation bar padding across all screens so documents, search fields, and bottom/action menus never hide behind system bars.
- **Zoom, Pan & Auto-Rotation Cropping:** Fixed image cropping on orientation changes and enabled smooth pinch-to-zoom, pan, and double-tap zoom in both Single Page and Continuous reader modes.
- **External File & PDF Opening:** Fixed opening PDFs and documents from external file managers and apps (`ACTION_VIEW`), including non-seekable `content://` streams and hidden-pill document categories.

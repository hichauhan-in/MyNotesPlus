<div align="center">

# MyNotes+

### Private. Encrypted. Yours.

An offline‑first, privacy‑first, **encrypted** note‑taking app for Android. Every note is
encrypted at rest with AES-256-GCM using a key held in the Android Keystore.
Core note-taking needs no sign-in. Metadata, temporary capture files, exports,
optional Google services and SDK diagnostics are described in the privacy policy.

</div>

---

## ✨ Features

- **On-device encryption** - note titles and bodies, saved image/audio attachments, template names/content and reminder text use AES-256-GCM. Organization metadata is stored separately in app-private storage. Android Keystore protects local keys.
- **Offline-first** - create, edit and read notes without signing in or connecting to the internet. Drive operations and optional model downloads need connectivity; Google SDKs can also transmit diagnostics.
- **Premium, neumorphic UI** — a calm indigo→violet design system built on **Material 3**, with light / dark / system themes and optional **dynamic color**.
- **Tablet & large‑screen ready** — the phone layout is untouched, while on tablets (and split‑screen) the note grid flows into **more columns** and long‑form screens (the editor, expenses, settings and reminders) **centre their content at a comfortable reading width** instead of stretching edge‑to‑edge. It reacts live to window size, so rotating or resizing stays fluid.
- **Fast home dashboard** — staggered note grid with search and filter chips (All, Recent, Favorites, Pinned, Archived, Trash) plus a morphing “create” FAB. Optionally **swipe left/right** to move between tabs (toggle in Settings).
- **Home‑screen widgets** — a **Quick Create** widget (one tap to start a note, checklist, expense or board), a **New note** capture button, a **Stats** widget (note count + backup status, no content shown), and a scrollable **Reminders** widget that lists your upcoming reminders and jumps straight to a reminder's note or the add screen. All widgets **resize** on both axes.
- **Reminders** — set a one‑off or repeating (daily / weekly / monthly) reminder from the **+** menu, or straight from a note’s **⋮ → Remind me** (for the whole note or a specific thing). Reminders fire as notifications, survive reboots, and open the linked note on tap. Reminder text is **encrypted on‑device** like everything else; manage alerts and precise timing from **Settings → Notifications**.
- **Multi‑select** — long‑press to select multiple notes and pin / favorite / archive / trash them in bulk. A per‑note `⋮` menu handles single‑note actions.
- **Distraction‑free editor** — debounced auto‑save, a Markdown formatting toolbar with **live styling** (bold / italic / headings render as you type), note color labels, and live word‑count / reading‑time stats.
- **Rich blocks** — interactive **checklists**, **tables** (labelled A/B/C columns and 1/2/3 rows, add / remove, drag to resize), **callouts** (a highlighted tip / warning box), inline **sketch boxes** (a resizable freehand pad), a heading button with **selectable sizes** (long‑press for H1 / H2 / H3), a **list button with real bullet styles** (long‑press for •, ◦, ▪, ➤, ★, numbered or checklist), a **divider button with separator styles** (long‑press for solid / dashed / dotted / stars / wave) and a quote button that remember your last choice, all interleaved inline with text and media.
- **Draw in the note body** - finger/stylus ink is clipped to the writing area. The title, metadata, margins and interactive non-text blocks are protected; back and toolbar controls remain outside the canvas. A stroke stops at the boundary and two-finger movement scrolls without adding ink. Drawing over existing text leaves that text in place. The default **Protect ink** mode makes earlier text read-only and continues new typing below the strokes; writing positions survive reopening and undo/redo. **Settings > Additional configurations > Drawing & text > Free overlay** allows both text and ink to overlap freely. Choose pen colour/width, undo, clear or convert handwriting locally.
- **Board notes** — a pan‑and‑zoom infinite canvas where freehand drawing is the default; choose pen thickness (long‑press the pen) and colour, draw **shapes** (line, arrow, rectangle, ellipse — long‑press the shape tool to pick), drop **images** (move, resize keeping their shape, and **crop** them right on the board) and draggable **colour‑coded sticky text notes** that zoom with the board, **turn your handwriting into typed text** with a tap (on‑device), and **zoom in/out** — pinch zooms in on exactly where your fingers are — across a wide range while everything stays smooth.
- **Reusable expense actions** - a manual, multi-account ledger with exact paise arithmetic. Set opening balances, then save salary, expense, savings, investment, transfer or budget-only actions. Tap **Record** once to apply an action to the current recorded balance, without a confirmation dialog. The same action remains ready for next time; its edit button changes future amounts without rewriting history. Internal transfers update both accounts together. Activity retains every execution, correction and reversal. Trackers open with their actions available; recording is blocked during a save or after a save failure. No bank connection or real payment is initiated. Existing balances and history are preserved when migrating to expense schema 5.
- **Receipt capture** - add a receipt from the camera or gallery in an expense section. On-device OCR suggests a labelled total, merchant and date for review. Saving creates or updates a reusable action without changing the balance. Its next execution retains the encrypted receipt in Activity; later executions do not reuse the old receipt.
- **Daily capture and search** - share text, selected text, images or recordings into MyNotes+ from another app, then save a new note or append to an existing text note after unlocking. Import UTF-8 TXT/Markdown documents. Search includes readable table/callout content, board text and expense records, with note-type filters, matching snippets and book-name matches. In the editor, **Search** sits immediately above **Export** in the three-dot menu and opens an inline top search bar with highlighted next/previous matches. Closing search returns to the same editor position.
- **Editing recovery** - undo/redo for text, checklist and board edits appear only in edit mode, immediately before the edit/check button. Board undo is available in the top bar only. **Recent versions** is in the three-dot menu; preview and recover any of up to 20 encrypted saved versions as an independent copy. Duplicate note makes separate media copies. Media referenced by another note, template or saved version is retained; abandoned files are eligible for cleanup after 48 hours.
- **Full encrypted backup** - Settings can create a passphrase-protected `.mynotesbackup` containing notes, books, saved attachments, templates, reminders and recent versions. Export verifies the encrypted archive before copying it to the selected destination. Restore validates the complete archive, previews its contents and adds independent copies with fresh IDs and media names. Existing notes, Drive credentials and security settings are not replaced. Limits: 32 MB per archive entry, 512 MB total payload, one million characters per note, and 20,000 media files. Keep the passphrase separately; it cannot be recovered.
- **Checklists** — turn a note into an interactive, checkable to‑do list with a live **progress bar**; press Enter to add the next item, backspace on an empty line to remove it, and use the ⋮ menu to check all, uncheck all, move done to the bottom, or clear completed.
- **Image attachments** — insert photos from the **gallery** or capture live with the **camera**, placed **inline at your cursor** so text and images can be interleaved freely. Drag a corner to **resize** (smoothly) or **crop** an image. Each note keeps its own private, **encrypted‑at‑rest** on‑device copy (removed with the note); captures never leave the app unless you export them.
- **On‑device text recognition (OCR)** — tap the **scan** icon on any image to pull its text out: a photo of a document, whiteboard or receipt becomes **editable text right below the picture**. It runs **100% on your device** (Google ML Kit, a model bundled in the app) — the image and the recognised text **never leave the phone** and it works fully offline.
- **Smart suggestions (on‑device)** — as you type, MyNotes quietly spots **dates, links, phone numbers, emails and addresses** in the note and offers one‑tap chips: turn a detected date into a **reminder** (pre‑filled with the time), **call** a number, **email** an address, **open** a link or **map** an address. Your text is analysed **on your device** (Google ML Kit) — nothing is uploaded. Turn it off anytime in **Settings → Additional configurations → Smart suggestions**.
- **AI note summary (on‑device)** — on supported phones (those with **Gemini Nano** via Android AICore), the editor’s **⋮ → Summarize (AI)** condenses a long note into a few bullet points and drops them in a callout at the top. It runs **entirely on your device** — the note is **never uploaded**, and it’s **free**. The option only appears where the device supports it.
- **Voice notes** - record audio straight into a note, pause/resume playback, seek and choose playback speed. Playback pauses when the app leaves the foreground and respects audio focus. Saved recordings are encrypted at rest.
- **Books** — organise notes into nestable folders ("books"). Create a book from the + menu, open it to browse or add notes inside, and move single or multiple notes between books. **Long‑press a book to select several at once** and move or delete them together (its **⋮ menu** still gives per‑book actions). Deleting a book (after a confirmation) moves it and **everything nested inside it to Trash together, keeping its structure**, so you can restore the whole book or delete it for good.
- **Tags** — label notes and search any tag to pull up every note that carries it. Tagging, pin, favorite and colour sit together in one row right under the title on **every** note type (text, checklist, board and expenses).
- **Reusable templates** — kept on a dedicated **templates button in the bottom-left corner**; “Manage templates” lists them, and **New template** opens a full note editor where the title becomes the template name, you pick an icon, and a **Save as template** button stores your draft. Deleted templates go to **Trash** and can be recovered within the retention window.
- **App lock** — optional unlock with **fingerprint, face, or device PIN** (biometric / device‑credential).
- **Read-only by default** - text, checklist and board notes reopen read-only unless changed in Settings; tap the pencil to edit. Expense trackers open with reusable actions ready. Every note also has Share, Export and Trash actions.
- **Organization** — pin, favorite, archive, color labels, and a recoverable Trash.
- **Share** - use the Android share sheet for readable text/PDF/Markdown copies. Public Drive links require an explicit warning and confirmation; they upload a readable snapshot, not a live or encrypted document. Expense trackers cannot be made public through this feature. Deleting the Drive copy revokes its link but cannot recall downloaded copies.
- **Share encrypted** - package a note and its attachments in a passphrase-locked `.mynote` file. New shares require 12 characters; existing packages remain importable with their original passphrases. Imports are limited to 16 MB, 6 MB per attachment and 8 MB of total attachment bytes, validate the package before writing, and assign fresh attachment names. The receiving app may upload the encrypted package. Share the passphrase separately.
- **Export** — save any note as **plain text, Markdown, HTML or PDF** (notes with images or voice notes come out as a **ZIP** with their attachments, and any **drawings/whiteboards are rendered into the file** as images), or export a whole **book as a ZIP** that keeps its folder structure and attachments. Exports go straight to your **Downloads/MyNotes** folder by default (no permission needed on Android 10+); pick a different **default export folder** in Settings, or on older devices you'll get the system "Save to…" picker.
- **Optional encrypted Google Drive sync** - note content, custom templates and reminders sync while foregrounded. Note conflicts use per-note sync checkpoints and preserve a conflict copy; existing remote updates use conditional writes, and incomplete transfers remain retryable. Template/reminder merges still use last-write-wins per item. **Attachment binaries, recent versions and book/folder structure do not yet sync.** Use a full encrypted backup for complete device transfer. Conflict copies do not merge expense balances; review conflicting trackers before recording more actions. Update all devices before editing schema-5 trackers.
- **Drive recovery** - deleting the visible MyNotes folder leaves its recovery key in hidden Drive app data. Settings > Backup & Sync > Repair Drive sync can check a restored folder or, after explicit **Start over** confirmation, create a new sync with a new passphrase or **4-10 digit recovery PIN**. Local notes stay intact; only supported records on this device are uploaded again, and cloud-only content is not recovered. Older hidden recovery data is retained separately. Update and reconnect other devices with the new credential. A long passphrase is recommended: short PINs are substantially easier to guess offline. This PIN is separate from Android's app-lock credential.
- **Actionable reminders** - Today, Overdue, Upcoming, Done and Off filters, explicit completion, 15-minute Snooze, recurrence anchors and optional checklist-item targets. An alert firing does not complete a one-shot task. Notifications offer Done/Snooze when app lock is off; locked reminders require opening the app. Exact timing and delivery depend on Android permissions and device policy.

Google ML Kit processes recognition/inference content on-device, but can send SDK diagnostics,
identifiers, device/app information, performance events and feature metadata to Google. Do not
interpret "on-device" as "no network traffic". The in-app privacy policy covers these distinctions.

> Screenshots and the full feature roadmap live in the app itself and in [`plan.txt`](plan.txt).

---

## 🔐 Security & privacy at a glance

| Area | Approach |
| --- | --- |
| Encryption | AES‑256‑GCM, random IV per operation |
| Key storage | Android Keystore (hardware‑backed where available) |
| At rest | Titles/bodies and saved attachments encrypted; some organization metadata and temporary files are not |
| Accounts/SDKs | No MyNotes account or advertising; optional Google SDKs can send diagnostics |
| App lock | Fails closed; session unlock is not restored after process death; protects screenshots, previews and reminder text |
| Cloud | Optional encrypted sync; separately confirmed public links are readable |
| Writes | Serialized note saves, retryable failures and atomic encrypted attachment writes |
| Updates/backups | Explicit Room migrations; no destructive fallback; OS backup disabled |
| Data controls | Offline privacy policy and local/Drive data-management guidance in Settings |

Full policy: **[Privacy Policy](docs/index.html)** (also published via GitHub Pages — see below).

---

## 🧱 Tech stack & architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 (Compose BOM)
- **Architecture:** MVVM + Repository pattern, unidirectional state with `StateFlow`
- **Persistence:** Room (encrypted payloads) + Jetpack DataStore (non‑sensitive settings)
- **Async:** Coroutines & Flow
- **Security:** Android Keystore, `androidx.biometric`
- **Navigation:** Navigation‑Compose with shared‑axis transitions
- **DI:** lightweight manual container (`di/AppContainer`)

### Project structure

```
app/src/main/java/com/example/
├─ MainActivity.kt            # Entry point, theme + app‑lock gate
├─ VaultNotesApp.kt           # Application, initialises AppContainer
├─ data/
│  ├─ local/                  # Room database, entities, DAOs
│  ├─ repository/             # NoteRepository (encrypt/decrypt boundary)
│  ├─ security/               # EncryptionManager (AES‑256‑GCM + Keystore)
│  └─ settings/               # SettingsRepository (DataStore)
├─ di/                        # AppContainer
├─ domain/model/             # Domain models
└─ ui/
   ├─ components/             # Reusable neumorphic components
   ├─ editor/                 # Note editor screen + view model
   ├─ home/                   # Home screen + view model
   ├─ lock/                   # Biometric app‑lock gate
   ├─ navigation/             # NavHost
   ├─ settings/               # Settings screen + view model
   └─ theme/                  # Colors, type, shapes, neumorphism
```

---

## 🚀 Build & run

**Prerequisites:** [Android Studio](https://developer.android.com/studio), its bundled Gradle JDK
(17 or newer), Android SDK 36 with minor API level 1, and a Gradle version compatible with AGP 9.1.1.
Java source compatibility remains 11. This checkout does not include a Gradle wrapper; use the
configured Android Studio/host build environment or generate a matching wrapper there.

1. Open Android Studio → **Open** → select this project directory.
2. Let Gradle sync and resolve dependencies.
3. Run on an emulator or device (min SDK 24 / Android 7.0).

> Debug signing uses the project's supplied debug keystore when present, otherwise Android's default
> debug signing configuration. Register the actual debug certificate with Google OAuth before testing
> Drive locally. Production signing is unchanged.

### Validation

On Windows, the Android-independent regression suite and Kotlin syntax/configuration checks can run
without an Android SDK:

```powershell
.\tools\verify-core.ps1 -InstallToolchain
```

The optional install switch downloads a temporary Java/Kotlin/JUnit toolchain outside the repository.
Subsequent runs can omit it. This executes the money, migration, export and import-policy tests and
checks Kotlin syntax, **not Android types or runtime behavior**. Compose tests are also supplied for
the drawing boundary and expense confirmation flow and must run in the Android build environment.

See [Release Readiness](docs/RELEASE_READINESS.md) for the required build/device and Play Console gates.
Apply the entire change set, including file deletions and the docs directory used for the offline policy.

### Release signing

Release builds are signed via environment variables (no secrets in the repo):

| Variable | Purpose |
| --- | --- |
| `KEYSTORE_PATH` | Path to your upload keystore (defaults to `my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password (alias: `upload`) |

Keystores (`*.jks`, `*.keystore`), `.env`, and `google-services.json` are all git‑ignored — **never commit signing keys or secrets.**

---

## 🌐 Privacy policy hosting (GitHub Pages)

A ready‑to‑publish privacy policy lives at [`docs/index.html`](docs/index.html). To publish it for your Play Store listing:

1. Push this repo to GitHub.
2. Go to **Settings → Pages**.
3. Set **Source** to `Deploy from a branch`, branch `main`, folder **`/docs`**.
4. Your policy will be live at `https://<your-username>.github.io/<repo-name>/`.
5. Paste that URL into the Play Console **Privacy Policy** field.

The same policy is bundled into the Android app from the docs directory. Verify the developer website's
Contact option works, publish the updated page at a stable HTTPS URL, and use that URL in Play Console.
An offline in-app policy alone does not satisfy the public policy URL requirement.

---

## 🗺️ Roadmap

Remaining work includes attachment and book sync, scheduled backups, wider device
coverage for the beta on-device AI stack, and signed-release/device validation. External contribution buttons
are enabled through `EXTERNAL_SUPPORT_ENABLED`, with the existing voluntary-support disclaimer and no
paid benefits. Review payment routes against Google Play's direct-tip requirements before publishing.

See [Daily-use update](docs/DAILY_USE_UPDATE.md) for this change set, verification commands and remaining release gates.

---

## 📄 License

Copyright © 2026. All rights reserved. (Add your preferred license here before open‑sourcing.)


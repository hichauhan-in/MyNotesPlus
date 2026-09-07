# Daily-use Update

## Reusable Expenses

- Save salary, groceries, savings, investments and HDFC-to-SBI transfers once. Record applies the saved action to the current recorded balance without an execution confirmation dialog.
- A recorded action remains available. Edit changes the next execution's amount, name or destination, not historical records. Every execution receives an independent ID and timestamp.
- Insufficient funds, invalid amounts, unavailable transfer accounts, same-account transfers and overflow are rejected. Record is unavailable while saving or after a save error; Retry save retries the existing state rather than executing again.
- Reversal remains a separately confirmed correction with an audit record. Reversing an earlier execution does not reset the last-use timestamp of later executions.
- Expenses open ready for daily use; other note types keep the existing read-only preference. This app records money movements and never initiates bank transactions.
- Receipt capture uses camera/gallery and local OCR. Suggested values require review. Save action does not affect balances; Record does. Receipt evidence belongs to the next execution only and can be opened from Activity.
- Expense schema 5 reads schemas 1-4 without reapplying legacy credit. Older apps reject schema 5; update every device before editing.

## Notes And Capture

- Create actions have 12 dp spacing and a bounded scrollable menu. Template menus are also scrollable on short windows. Existing theme/colors are unchanged.
- Text/checklist/board edits have bounded undo/redo. Up to 20 encrypted saved versions can be previewed and recovered as independent copies.
- Duplicate note uses independent attachment names. Shared media remains available while referenced by notes, templates or saved versions; abandoned files have a 48-hour cleanup grace period.
- Android Share and selected-text actions support a new note or append to an existing text note, behind the app-lock gate. Media is limited to 10 images/recordings per capture and 32 MB per item.
- TXT/Markdown import accepts bounded, valid UTF-8 input (2 MB / one million characters). External private-attachment tokens and encoded block markers are neutralized.
- Search reads table cells, callouts, board text and expense content, not encoded tokens. Type filters, matching snippets, book-name matches and Find in note with highlighted next/previous results are included.
- Standalone and inline checklists support drag/menu reorder and accessible move actions. Voice playback supports pause/resume, seeking and speed controls and pauses when backgrounded.

## Backup And Sync

- Manual encrypted full backups include notes, books, attachments, templates, reminders and recent versions. Archive records are authenticated and names/content encrypted; tampered, incomplete, over-limit or invalid backups are rejected.
- Export verifies a temporary encrypted archive before writing to the selected Android document destination. The destination provider may be a cloud service.
- Restore previews contents and creates independent copies. Existing notes, security settings and Drive credentials are not replaced. Database inserts reject collisions and use a transaction; templates use DataStore with compensating rollback on ordinary database failures. Cross-store power-loss atomicity is not guaranteed.
- Limits: 12-character minimum new passphrase; 32 MB per archive entry, including the manifest; 512 MB total payload; 20,000 media entries; one million characters per note; up to 20 versions per note. Passphrases cannot be recovered.
- Note sync uses per-note checkpoints, conflict copies and conditional remote writes. Unknown first-upgrade merge bases preserve differing copies rather than guessing. Missing conflict media blocks replacement and leaves sync incomplete.
- Template/reminder merges remain last-write-wins per item. Failed downloads are not treated as empty collections. Metadata upload failures do not advance the successful-sync timestamp.
- Drive still does not sync media binaries, book structure or recent versions. Full-device transfer uses the encrypted backup. Scheduled backups and distributed expense-ledger merging are not implemented.

## Reminders

- Delivery, completion and snooze are separate. A one-shot alert remains overdue until explicitly completed or disabled.
- Today, Overdue, Upcoming, Done and Off filters; Done and 15-minute Snooze controls; anchored daily/weekly/monthly recurrence.
- Notifications expose Done/Snooze only when app lock is disabled. Receivers are non-exported, PendingIntents are immutable, and stale occurrence actions are rejected. With app lock enabled, open/unlock the app to act.
- A reminder can target a unique checklist row. Completion changes that row and the reminder in one database transaction. Renamed/duplicate/missing rows are not guessed.
- Notification denial is visible in the Reminders screen. Exact delivery still depends on Android permissions, battery policy and device behavior.

## Verification

Google's Digital Ink Recognition SDK was updated from 18.1.0 to 19.0.0 and its adapter migrated to the
new recognition package. This removes the release lint warning for the unaligned `libdigitalink.so`;
the SDK's [release notes](https://developers.google.com/ml-kit/release-notes) identify 19.0.0 as the
16 KB page-size update. Notification settings use a tested API-24/25 fallback, and Smart Suggestions
return without initializing the API-26-only entity-extraction SDK on Android 7/7.1. These checks do
not replace hardware/device testing of the bundled SDKs.

The matching temporary toolchain is outside this checkout: Gradle 9.3.1, JDK 21, Android platform 36.1,
build-tools 36.0.0. No release keys, Git commits or remote pushes are part of this change.

Run with the configured Android environment:

```text
:app:testDebugUnitTest
:app:lintDebug
:app:lintRelease
:app:assembleDebug
```

The Windows `tools/verify-core.ps1` suite also supports `-TestClass <fully.qualified.TestClass>`.
Kotlin syntax validation alone does not validate Android types; Android compilation was run separately.

Focused checks cover reusable ledger executions/reversals, schema migration, receipt parsing, encrypted
archive authentication, full backup/restore through a test content provider, cancellation cleanup, media
isolation, version retention, rich-content search, text-import validation, recurrence, menu interactions
and Room upgrades from schema 8. Test-only keystore providers do not certify hardware-backed key behavior.

Full debug unit tests, debug/release lint and debug APK assembly have passed. Lint has zero errors;
remaining warnings include dependency updates, unused resources and layout/style suggestions. The
specific 16 KB alignment and unsupported notification-settings API warnings were resolved. The
standalone suite passes 69 tests and is a subset of the Android test suite, not additional coverage
to be added to its count.

Android-rendered captures are generated under `app/build/reports/daily-use/` by
`DailyUseScreenshotTest` with the Gradle property `-Proborazzi.test.record=true`. Phone and tablet/large-text
expense captures and the phone create menu were inspected for spacing, readable amounts and clipping.

## Remaining Release Gates

- Run signed-device tests for real Keystore/biometric behavior, app-lock return from camera/photo pickers,
  process death, storage exhaustion, large media, audio focus and notification actions.
- Test Google Drive on two authorized devices: ETag/conditional-write responses, conflict copies,
  cancellation/offline retries and account reconnection. Do not use simultaneous expense edits as accounting reconciliation.
- Exercise Android 24/25 ML entry points and SDK initialization, current target-API behavior and 16 KB native-library compatibility.
- Run full TalkBack, enlarged-font, landscape/foldable, camera/recording, reboot and time-zone tests on devices.
- Publish the updated privacy policy, review OAuth and Data safety/Financial features declarations, increment versionCode,
  and run signed release lint/bundle and Play pre-launch testing with the existing signing identity.
- Passing tests and lint do not certify the absence of bugs/security issues or guarantee Google Play approval.
# Release Readiness

Prepared 7 September 2026. This is a release checklist, not a compliance certificate or a guarantee of Google Play approval.

## Implemented

- Expenses use integer paise and explicit pending/completed transactions. Credits add to the source balance; expenses, savings and investments subtract; internal transfers debit and credit together. Budget-only records do not change balances.
- A confirmation previews the affected recorded balances. Duplicate completion, invalid amounts, insufficient funds and same-account transfers are rejected. Repeat creates a fresh pending row; reversal leaves a linked audit record.
- Legacy monthly credit is folded into the balance once. Existing allocations are pending, not automatically spent. Saved transfers move into their source account's sections. A missing legacy source becomes a zero-balance recovered account rather than losing its rows.
- Text, Markdown, HTML and PDF expense output includes status, history, reversals and both sides of transfers. These are manual records, not actual bank operations.
- Page ink is clipped to the writing body. Titles, margins and interactive non-text blocks do not accept or display page ink. Back/navigation/toolbars stay outside the canvas. Existing stroke coordinates are preserved.
- Saves are ordered, failed saves remain retryable, and Trash waits for pending saves. Attachment writes are atomic. Unexpected database upgrades no longer silently wipe the database.
- App lock fails closed, does not restore an unlocked state after process death, and protects screenshots, recents and reminder text. Authentication uses a supported authenticator combination on older Android versions.
- Encrypted imports validate size and attachment references, use fresh filenames, and wait for persistence before reporting success. New shares require a 12-character passphrase; old shares remain readable.
- Public links require confirmation and are unavailable for expense trackers. Unused Firebase AI/App Check dependencies are removed. External contribution UI defaults off.
- Settings contains the bundled privacy policy and data controls. The hosted and bundled policy use the same HTML source and disclose Google SDK diagnostics, local metadata, temporary files, sync limits and deletion paths.

## Migration Precautions

1. Export important trackers before deploying a new build.
2. Update all devices together. Old app versions do not understand expense schema 4 and must not edit a migrated tracker.
3. Opening a tracker read-only does not rewrite it. The first change persists the migrated representation.
4. A legacy credit is part of the starting balance, not a newly posted salary. Previously executed transfers have no historical execution log in the old format; that history cannot be reconstructed.
5. Keep the complete change set, including removal of the legacy expense editor, new model/codec files, tests, and the docs directory used by the asset source set.

## Verification Available Here

Run from the repository root on Windows:

```powershell
.\tools\verify-core.ps1 -InstallToolchain
```

The install switch is only needed for initial toolchain setup. It downloads Java/Kotlin and test dependencies to a temporary directory, with archive checksum verification. No Android SDK is installed and no machine-wide environment variables are changed.

The suite executes 28 Kotlin/JUnit tests covering arithmetic, completion, reversals, migrations, export escaping, import isolation and size limits. It also parses Kotlin sources/build scripts and checks manifest/migration/privacy guards. Kotlin syntax parsing is not Android compilation. Editor diagnostics are not a substitute for a Gradle build.

## Required Android Gates

Run the matching tasks through the configured Android Studio or host build environment. This checkout has no Gradle wrapper, Android SDK, emulator or signing secrets configured for VS Code execution.

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:lintRelease
:app:bundleRelease
```

- Use a Gradle/JDK combination supported by AGP 9.1.1 and the configured API 36.1 platform. Do not independently upgrade the built-in Kotlin/Compose compiler pairing.
- Run the new PageInkLayerTest and ExpenseLedgerEditorTest Compose/Robolectric tests, not only the standalone JVM subset.
- Verify the final bundle contains assets/index.html and that Settings opens the privacy policy offline with working internal navigation and external contact links.
- Validate API 24/25 specifically. The existing manifest overrides the minimum SDK of several ML Kit libraries; these overrides are not proof of runtime compatibility. Do not release on those API levels unless all entry points and SDK initialization are safe. Remove unsafe overrides or raise the supported minimum if device tests fail.
- Verify 16 KB page-size compatibility for all bundled native SDK libraries on a 16 KB emulator/device. A modern Android Gradle Plugin alone does not certify third-party native binaries.
- Increment versionCode before uploading a new release and keep the existing application ID and production signing identity.

## Device Test Matrix

- Expenses: create HDFC with INR 50,000; complete an INR 10,000 credit; transfer INR 10,000 to SBI; record an external investment. Check both balances, pending totals, Activity, reopen persistence, export and reversal.
- Exercise zero, blank, malformed, very large and insufficient amounts. Double-tap completion/confirmation. Repeat a completed row and verify it is pending without changing the balance.
- Migrate each old schema, including credit, tracked-only allocations and a saved transfer whose source was removed. Reopen and ensure credit is not applied twice.
- Drawing: start inside the body and drag toward the title, back button, toolbar and screen edge. Tap checkboxes and other protected controls. Two-finger scroll must not add ink. Test read-only mode, undo/clear, handwriting conversion and writing below ink.
- Test narrow phones, a tablet, rotation, split screen, light/dark themes, TalkBack and enlarged fonts. Long account names and large amounts must remain readable without obscuring controls.
- Save, immediately leave, background, rotate and relaunch. Test an I/O failure where feasible and confirm Retry save works without overwriting recoverable content. Trash must retain the latest committed transaction.
- App lock: success, cancellation, hardware unavailable, missing enrollment, process death, rotation and return from photo/credential pickers. No authentication error should reveal content. Confirm notification/widget masking and screenshot protection.
- Imports: repeat import of the same file, wrong passphrase, an old short-passphrase share, missing/corrupt attachments, board images, oversized input and interrupted navigation. Existing notes' attachments must remain unchanged.
- Permissions: deny microphone, deny notifications, and leave exact alarms off. Gallery and external camera flows should not request broad media/storage/camera permission. Reboot, time-zone changes and exact-alarm permission changes need device verification.
- Drive: use two signed test devices, verify connect/recovery, manual/automatic sync and failures. Public-link cancellation must not upload anything; expense public links must not be available. Remove a public copy in Drive and verify its link stops working.

## Play Console Gates

1. Publish the updated privacy page at a stable, public, non-geofenced HTTPS URL. Confirm the developer Contact route accepts privacy inquiries; use a monitored email or form if necessary. Enter the live policy URL in Play Console.
2. Complete Data safety using the actual release dependency graph and network behavior. ML Kit declares diagnostics, identifiers, device/app information, performance events, configuration and input/output sizes. Do not select "no collection" solely because recognition runs locally or notes are encrypted. Apply any encryption/user-initiated-transfer exceptions only when their exact conditions are met.
3. Complete the Financial features declaration accurately. Describe a manual budgeting/expense-record tool, not bank account access, lending, investment trading or money transmission. Ask Play support if the available categories do not clearly fit.
4. Complete audience, content-rating, app-access and data-deletion questions. The app has no separately hosted MyNotes account; Drive is authorization to an existing Google account. Review Google's account definition against the actual sign-in experience rather than assuming an exemption.
5. Review the merged manifest, not just the source manifest. Explain microphone, notifications and optional exact alarms. Check current target-API and native page-size requirements for the submission date.
6. Finish the Google OAuth consent/production setup and register the actual installed signing certificates. Restrict authorization to the required drive.file and drive.appdata scopes and complete any verification requested by Google.
7. Keep EXTERNAL_SUPPORT_ENABLED false unless the exact contribution flow and distribution regions have been reviewed against the current Payments policy and any required program enrollment. A "voluntary" label is not a substitute for that review.
8. Run Play's pre-launch report and internal testing with the signed release artifact. Resolve crashes, ANRs, accessibility findings, SDK warnings and privacy/disclosure mismatches before requesting production approval.

## Known Limits

- Drive sync does not transfer attachment binaries or book structure. Use encrypted share or an export that includes attachments for those files.
- Conflict resolution remains last-write-wins. This is not a distributed accounting ledger; concurrent edits to the same tracker on different devices can replace one another.
- Not all organization metadata is app-encrypted. Private camera/recording files exist temporarily before encryption; readable exports and ordinary share-cache files are intentional exceptions.
- Imported packages are capped at 16 MB, 6 MB per attachment, 8 MB total attachment data and 100 attachments. Note content is capped at 1,000,000 characters for encrypted sharing.
- On-device AI remains dependent on SDK/device availability, initial model downloads and the beta APIs already used by the project. The complete Android build and runtime behavior have not been verified in this VS Code environment.

## Official References

- [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
- [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271)
- [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738)
- [Android biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth)
- [16 KB page-size support](https://developer.android.com/guide/practices/page-sizes)
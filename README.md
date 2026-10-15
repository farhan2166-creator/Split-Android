# Split — Android expense sharing

An offline-first Android app for trips, flatmates, dinners, and shared budgets. No registration or server required.

## Working features

- Multiple groups, each with its own fixed currency (PKR, USD, EUR, GBP, AED, INR)
- Participants, duplicate-name protection, group rename
- Create, edit and delete expenses; choose payer, category, note and eligible participants
- Four split methods: equal, custom shares/weights, percentage and exact amounts
- Integer minor-unit money math with deterministic remainder allocation (no floating-point rounding drift)
- Per-person net balances, suggested repayments and manual repayment records
- Category spending insights, CSV transaction export, text balance sharing
- Persistent on-device SQLite ledger; all multi-table writes are transactional
- Unit tests for rounding, weights, validation, settlements, repayment and zero balances
- Automated debug APK and unit test GitHub Actions workflow

## Build

Open this repository in Android Studio (JDK 17, Android SDK 35). It uses Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21 and Jetpack Compose. If Gradle 8.9 is installed, run:

```sh
gradle testDebugUnitTest assembleDebug
```

In GitHub open **Actions → Android Debug APK** to download a debug artifact from pushes to main, pull requests or manual runs. A separate **Android Release APK** workflow builds a production-key-signed release when dispatched manually. Both workflows derive a build number from the commit count plus the workflow run number. Each new run gets a distinct build code.

## Data & feature boundaries

- Data is **only on the current device**. Export via the Android share sheet to keep a copy. There is no cloud sync, invitation link or shared editing yet.
- Each group operates in **one currency**. Other currencies are available when creating a different group; there is **no currency conversion** across transactions.
- Every expense currently has one payer, but can be shared across any participants in several ways.
- CSV export contains transactions (not the complete participant allocation ledger); it is useful for review, **not a restorable backup**. Avoid uninstalling or clearing app data until a real backup/import feature exists.
- No receipts, banking integration, payment processing or notification service is implemented.

## Financial behavior

For each expense, the payer is credited with the amount they paid and every beneficiary is debited their allocated share. A repayment credits its sender and debits its receiver. Positive balances mean the person should receive money. Settlements are suggestions only; they are recorded when the user explicitly chooses **Record paid**. The zero-sum accounting invariant is checked in tests.

## Next meaningful releases

Complete ledger backup/restore with migration tests, multi-payer expenses, attachment handling, explicit-rate multi-currency transactions, optional end-to-end encrypted group synchronization, invitations/access control and conflict handling. Those require separate implementations, not just interface buttons.

## Versioning and identity

The installed Android app is named **Split** and has a distinct application ID, `com.mythos.split`. Its Kotlin source namespace remains `com.splitapp`; these are independent properties. Control the release version using `version.properties` (MAJOR, MINOR, PATCH). GitHub Actions adds its run number to the Android `versionCode` and `versionName`, without rewriting the repository. Each successful run uploads a version-labelled debug APK and SHA256 checksum. The home screen **About** option displays the installed version, build code and package ID.

Version codes reserve 10,000 build numbers per patch; before exceeding that limit, increment the patch version and reset the CI counter strategy. App versions are calculated deterministically so parallel Gradle tasks cannot corrupt a version file. Do not change the application ID again if you want future APKs to upgrade the same installed app.

**Existing installs:** previous experimental builds with `com.splitapp` are a separate application. They will not upgrade into `com.mythos.split`, nor automatically transfer their private SQLite data. Keep the old installation if it contains records you need. Debug APKs are test-signed, not production releases.

## Debug and signed release APKs

- **Debug APK:** Actions → Android Debug APK → successful run → artifact `Split-Android-debug-...`. On main pushes, pull requests or manual dispatch. Runs unit tests, verifies debug signature, and archives a version-labelled APK and checksum.
- **Signed release APK:** Configure four GitHub Actions repository secrets: `ANDROID_KEYSTORE_BASE64` (production .jks file encoded as base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. When all four secrets are configured, a signed release APK builds automatically on pushes to `main`, and also on manual runs. Without those secrets, automatic release runs skip the signing job and explain why; manual release runs fail clearly. An unsigned APK is never published as a release.
- **Optional GitHub Release:** In the manual run select `publish_release` to upload the signed APK and SHA256 checksum as downloadable Release assets. Publishing is opt-in; the Actions artifact is uploaded whether publishing is chosen or not.
- **Signing compatibility:** Both variants have application ID `com.mythos.split`, but APKs signed with different keys cannot update one another. Do not uninstall an app containing local expenses just to change signatures; uninstalling deletes its private data. Maintain the same private release keystore for all production updates.
- **Versions:** Both workflows calculate `splitCiRunNumber` as 1000 + full Git commit count + that workflow’s run number + (run attempt - 1). This ensures that a manual rebuild of the same commit receives a newer version than its previous run. Update `version.properties` for MAJOR/MINOR/PATCH. Increase the version or revise the scheme before this build sequence reaches 9999; each release and debug workflow maintains its own run counter and signing identity.
- **Local signing:** Copy `signing.properties.example` to ignored `signing.properties`, and store the keystore outside version control. Debug builds never read signing secrets.

Never commit signing passwords, .jks files, or signing.properties.

### Troubleshooting APK rebuilding

A successful **Android Debug APK** workflow produces a downloadable GitHub Actions *artifact*; GitHub wraps the APK and checksum in a ZIP for download, so extract it to install the APK. If you want a new debug build of the same code, choose **Actions → Android Debug APK → Run workflow**. Every new run includes a newer build number in the APK version. A fresh push to `main` also triggers the debug build, including README-only changes. The signed release workflow builds automatically only when all four signing secrets have been configured. If the installed app has a different signing certificate, Android will block an in-place update even when its version is newer; do not uninstall without preserving your private expense data first.

## PDF ledgers

Open a group and select **Generate PDF Ledger** (also available in **Insights → Records & sharing**).

- **Group ledger:** all participants, with individual sections and an activity appendix.
- **Single-member ledger:** one person's credited payments, allocated shares, repayments, running balance and net position.
- **Multi-member ledger:** any selected participants, with independent opening and closing balances; common transactions are counted once in the activity summary.
- **Periods:** All time, this month, last month, or a custom inclusive start/end date.
- **Accounting:** Each member's opening balance includes all activity before the start date. Their closing balance includes only relevant activity through the end date. Positive means they should receive money; negative means they owe.
- **PDF:** Offline A4 pages with a summary, chronological member ledger tables, balance columns, and the related transaction appendix. Uses Android's built-in PDF renderer and Save As document picker; no photo capture, permissions or external PDF service.

The ledger is a read-only report. Exporting does not alter any expense, settlement or group balance. Transactions are grouped within a single group's currency; there is no cross-currency conversion.

## Full offline backup and restore (v1.2)

The home dashboard provides **Export full backup** and **Import or restore backup**. These operations use Android's system file picker, work offline, and require no storage permission.

- Full backups preserve groups (including archived groups), participants, expenses, multiple payer contributions, expense allocations, repayments, notes, currencies and dates, with stable internal identifiers and exact integer monetary values.
- Backups use a **versioned JSON format** with a SHA-256 checksum for detection of accidental corruption. The format is **not encrypted**; handle backup files privately.
- Import validates its checksum, record identifiers, relationships, balanced splits, contribution totals, and member balances before database changes.
- **Import missing groups** leaves existing groups unchanged and skips groups already present. Identifier conflicts with unrelated local records are rejected.
- **Replace all local groups** requires an explicit acknowledgment. Changes are transactional: if SQLite rejects a record, the previous database is preserved by rollback. This is destructive, so keep a second copy of your backup before replacing.
- The entire backup remains on the device unless you manually save or share the selected file. SQLite migrations continue to work; backup format and database version are separate.

PDF and CSV exports are reports and **cannot** be used as restoration sources. Use **Export full backup** for recovery or transfer to another phone. A checksum is not a security signature: never import a backup from an untrusted source.

## Design refresh (v1.3)

Split uses an accessible ocean-and-sage visual system with warm amber accents, subtle outlined cards, comfortable spacing, and consistent typography. Both light and dark modes have carefully chosen surface and text contrast.

- The dashboard prioritizes groups and everyday actions, with a concise overview, search, active/archived navigation and data-recovery shortcuts.
- Group details present spending in a focused summary with clear Add Expense, Repayment and PDF Ledger actions.
- Expense, repayment and PDF flows now have consistent instructions, section hierarchy and generous touch targets.
- In **About → Appearance**, choose **System**, **Light** or **Dark**. The choice is stored only on this device.
- The app launcher has a dedicated adaptive Split icon, while the application ID remains `com.mythos.split`.
- Core balance calculations, SQLite records, import/export formats and signing configuration are unchanged.

The interface intentionally avoids neon colors and excessive animation so it remains calm and readable during longer use.

## Direct APK downloads (without extracting a ZIP)

After each successful `main` debug build, GitHub Actions also publishes an **individual `.apk` file** and its SHA-256 checksum as a **GitHub pre-release**. Open [Split-Android Releases](https://github.com/farhan2166-creator/Split-Android/releases), choose the newest `Split Android Debug APK` entry, and tap the `.apk` asset under **Assets**. It downloads directly, without having to extract an Actions artifact ZIP.

GitHub Actions' **Artifacts** section itself always wraps uploaded files in a ZIP; the pre-release is the direct APK download location. Each successful debug run links the APK in the `Publish direct debug APK` job summary. Debug builds are test-signed and cannot automatically replace a production-key-signed installation; keep a backup before changing signing identities.

## Monthly group budgets (v1.4)

Within any group, open **Insights → Plan your spending** to set an optional monthly limit. Navigate between current and previous calendar months to see expense totals, remaining allowance, category totals and progress toward the limit. The panel warns when 80% or more is used and highlights overspending without blocking legitimate transactions.

- Budgets belong to each group and use that group's currency; no currency conversion or shared global limits.
- Spending uses the local calendar month of each expense date, including backdated entries. Repayments and settlements never count as expenses.
- Budgets may be edited or removed. They do not alter any money owed, split allocation, settlement or financial ledger.
- Database schema v4 migrates existing groups with no budget, preserving every existing expense.
- Full offline JSON backups now include configured limits. Older backup files lacking a budget are still readable.
- JVM tests cover month boundaries, budget thresholds, no-budget behavior and the full backup round-trip.

The budget is a voluntary planning tool and does not automatically send notifications.

## Screen-first navigation (v1.5)

The home screen now has a permanent **Groups | Archived | Settings** bottom bar. Group screens use a permanent **Activity | Balances | Insights** bar, while the total-spending summary and primary Expense, Repay and PDF actions remain visible at the top. Home search and the selected tabs remain intact when opening a group, editing an expense, generating a report or navigating back.

Large forms are separated into deliberately short screens:
- **Expense entry:** Details / Split bottom tabs with a persistent Save button. If validation fails, the app switches to the relevant tab.
- **PDF ledger:** People / Dates / Options bottom tabs with a persistent Save PDF button and validation directing the user to the relevant step.
- **Repayments:** A single compact page with a fixed save action.

The purpose is to avoid scrolling through entire *screens*. Long group transaction histories, unusually large member lists, keyboard-covered content and enlarged accessibility text may still require scrolling; removing that scrolling would hide essential information.

This release does not add Gmail permissions, cloud sync, an account system or invitation links. The app remains offline-first: group membership exists only in the current on-device ledger and is transferable via the existing validated full backup/restore process.

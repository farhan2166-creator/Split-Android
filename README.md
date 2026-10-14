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

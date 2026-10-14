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

In GitHub open **Actions → Android CI and APK**. After a successful workflow run, download the artifact **split-debug-apk** and install the APK. The workflow automatically builds on pushes to `main` and supports manual runs. **Debug APKs are not Play Store release-signed.**

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

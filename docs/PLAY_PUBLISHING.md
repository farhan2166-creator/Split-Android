# Split Android — Google Play publication checklist

> This checklist is a release gate, **not** an assertion that Play publication is complete. Keep the app ID `com.mythos.split` and the original signing keystore unchanged. Do not erase testers' local expense data to work around signing problems.

## Automated prerequisites (configured; verify in CI)
- [ ] Compile and target Android 16 / API 36 (Google Play requirement for new phone apps from August 31, 2026).
- [ ] Java 17 + AGP 8.10.1 + Gradle 8.11.1; Kotlin Compose plugin 2.2.21.
- [ ] Produce signed APK **and** signed Play `.aab` from the same permanent private signing configuration.
- [ ] Run unit tests on CI and build a test AAB on PRs. Verify APK signatures, AAB JAR signature, package ID and SHA-256 checksums on signed builds.
- [x] Keep offline operation and original SQLite/backup formats unchanged.
- [x] Add in-app privacy disclosure, editable project-owned policy HTML in `docs/privacy.html`, and draft Play listing text.
- [x] Keep credentials out of source control; release signing uses existing GitHub Actions secrets.

## Human-operated Google Play Console steps (not yet completed)
- [ ] Confirm all CI build and test jobs succeed; run the APK on real Android 15 and 16 phones. Check edge-to-edge insets, gesture navigation, keyboard, small screens, landscape/tablets, dark mode and large fonts.
- [ ] Inspect the Android App Bundle in Play Console. Confirm no native-library 16 KB page-size issues and no unexpected permissions.
- [ ] Enable GitHub Pages for `main` → `/docs` (or deploy `docs/privacy.html` on another reliable HTTPS host). Test the live policy URL from a signed-out browser, then use it in Play Console. Expected GitHub Pages address is `https://farhan2166-creator.github.io/Split-Android/privacy.html` **only after Pages is enabled**.
- [ ] Ensure the Play developer display name and privacy contact reflect the actual publisher. The GitHub issue link is a support option, not a substitute for required developer contact information.
- [ ] Enroll in **Play App Signing**. Decide whether the existing production key will be the app signing key or only the upload key. Google Play-installed APKs may have a different signing certificate from old GitHub-sideloaded APKs; back up all local data before changing installation source.
- [ ] Upload the **AAB**, not the APK, to an internal testing track. Keep `com.mythos.split` stable after publication.
- [ ] Provide accurate Data safety declarations, including all third-party SDKs. Current code has no internet permission, advertising SDK, analytics, cloud server or account; manually exported data is user-initiated. Re-evaluate when dependencies/features change.
- [ ] Complete Play Console content rating, target audience, app access, ads declaration, country distribution, category, and contact/support fields.
- [ ] Provide a Play icon (512×512 PNG), feature graphic (1024×500), and **real app screenshots** captured from a tested build; do not present imagined screens as actual UI.
- [ ] Complete all required developer identity/account verification and production access prerequisites. New personal Play Console accounts created after November 13, 2023 generally require at least 12 opted-in closed testers for 14 consecutive days.
- [ ] Test full backup/restore (merge + replace), SQLite upgrades from previous versions, offline startup, creating/editing expenses, custom shares, advances, repayments, CSV/PDF export, huge values, and rotation/process restart. Do not use private financial data in screenshots.
- [ ] Test updates **without uninstalling** from previous production-signed APKs; ensure `versionCode` increases.
- [ ] Resolve critical crashes and accessibility defects in closed testing; approve listing metadata and then publish.

## Distribution and security
- GitHub Release APK is for direct installation; AAB is for Google Play upload.
- An AAB cannot be installed directly like an APK.
- Never expose signing credentials or private tour ledger data in issues or releases.
- Building a signed file does not complete Play review, signing enrollment, listing assets or on-device tests.

Official references:
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [App testing](https://support.google.com/googleplay/android-developer/answer/14151465)

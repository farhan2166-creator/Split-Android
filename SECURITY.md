# Split-Android security policy

Split is an **offline-first expense ledger**. It does not require internet
access, accounts, Gmail permissions, or a backend. The app holds financial
records on the owner's device. A manually exported JSON/PDF/CSV file is
**not encrypted** and must be treated as private financial information.

## Report vulnerabilities privately

Please do **not** publish private expense files, keys, passwords, or
exploit details in public issues. Use GitHub's private vulnerability
reporting feature if it is enabled in the repository's **Security** tab,
or contact the repository maintainer privately before sharing evidence.
Ordinary non-sensitive bugs can be filed as public issues.

## For contributors

- Never commit signing keystores, API tokens, passwords, financial exports,
  personal databases, `.env` files, or real participant data.
- Do not put credentials in workflow logs, pull requests, screenshots, or
  GitHub Actions artifacts. Use GitHub Actions encrypted repository secrets.
- External pull requests run only the read-only **debug** workflow. Do not
  add `pull_request_target` or expose release signing secrets to PR code.
- Signed releases are built only from `main`, with credentials scoped to the
  signing jobs and a write-capable GitHub token limited to the publication
  job. Treat any workflow change as security sensitive.
- The CI tracked-file scan is a *guardrail*, not proof that history and
  dependencies are free of vulnerabilities.
- If a credential or user record is committed, immediately revoke/rotate the
  compromised secret or remove the affected public data, review history and
  caches, and consider GitHub's sensitive-data removal procedure. Deleting
  it in a new commit does **not** erase earlier public history.

## Release signing

The permanent Android release keystore is **never** stored in Git.
Keep an offline backup of its private key and password; future updates
of the package `com.mythos.split` must use the **same signing certificate**.
Changing the signing certificate will generally prevent existing installs
from updating without an app data migration/backup.

If the signing *password* needs changing, update the stored keystore
password and matching Actions secrets while preserving the original
private signing key and certificate. Do not regenerate a new certificate
just to change a password.

## Repository owners: recommended public-repository controls

In GitHub Settings, configure:

1. **Code security and analysis:** enable Dependabot alerts, GitHub secret
   scanning and push protection where available; enable private
   vulnerability reporting if desired.
2. **Actions > General:** keep the default `GITHUB_TOKEN` permissions
   read-only; permit workflows to request `contents: write` only for
   the trusted APK publication jobs.
3. **Rules > Rulesets:** protect `main` against force pushes and deletion;
   optionally require a successful Android Debug APK check before merging
   a pull request. Do not unintentionally block your intended direct
   development commits.
4. **Account security:** require 2FA, review access granted to users/apps,
   and store the release keystore separately from GitHub.

This policy is documentation and does not automatically change the
repository's GitHub Settings or access controls.

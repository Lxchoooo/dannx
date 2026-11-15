# Security hardening

Status: implemented in the repository. The GitHub settings below were checked
against the API on 2026-11-15; only the secret scanning extras are still
pending.

## In the repository

- **CodeQL** analyses `java-kotlin` with a real build (`assembleDebug`) on
  `main`, on pull requests that touch the app or Gradle files, and weekly.
- **Dependency review** blocks pull requests that introduce high-severity
  vulnerabilities and posts a summary in the PR.
- **Gradle wrapper validation** checks `gradle-wrapper.jar` in every job that
  runs Gradle.
- **SHA pinning**: every action is pinned to a 40-character commit SHA.
- **Least privilege**: the Android workflow starts with `contents: read`; the
  release job requests only the scopes it uses.
- **Pull requests never see the signing key**: the release keystore is only
  restored for `main` and tag builds, so a PR checks out code that cannot read
  it. Debug APKs from PRs use the standard debug signature.
- **Release integrity**: every tag and rolling release publishes
  `DannX.apk.sha256` and generates a build provenance attestation.
- **Private reporting**: `SECURITY.md` points to GitHub Security Advisories.
- **No secrets in the repository**: signing material lives in GitHub secrets;
  session tokens are excluded from Android backups.

## GitHub settings

These are repository settings, not files, so they are applied once from the
GitHub UI or API. Verified with the API on 2026-11-15:

- **Secret scanning** and **push protection**: enabled, with no open alerts.
- **Private vulnerability reporting**: enabled.
- **Dependabot alerts** and **Dependabot security updates**: enabled, with no
  open alerts.
- **SHA pinning**: required for every action; Actions are enabled.
- **Allowed actions**: only actions owned by GitHub or by a verified creator,
  plus two explicit patterns for the only third parties in use:
  `gradle/actions/*@*` and `reactivecircus/android-emulator-runner@*`.
- **Release immutability**: enabled; a published tag cannot be deleted or have
  its assets replaced.
- **Default `GITHUB_TOKEN`**: read-only, and workflows cannot approve pull
  requests.
- **Ruleset for `main`**: requires `checks` and `debug`, blocks force-pushes and
  deletions, with an `always` bypass for the Admin role.

Still pending:

- **Secret scanning validity checks** and **non-provider patterns**: both
  disabled. The API accepts the request and returns the repository unchanged,
  so they look plan-gated rather than misconfigured; retry from the UI when the
  account has GitHub Secret Protection.

## Verification

```bash
# APK integrity
sha256sum -c DannX.apk.sha256
gh attestation verify DannX.apk --repo Lxchoooo/dannx

# Security alerts
gh api repos/Lxchoooo/dannx/code-scanning/alerts
gh api repos/Lxchoooo/dannx/secret-scanning/alerts
gh api repos/Lxchoooo/dannx/dependabot/alerts
```

## Notes

- CodeQL needs a real Kotlin build; `build-mode: none` does not analyse Kotlin.
- Dependency review only runs on pull requests, so it does not add time to
  `main` releases.
- The attestation proves which repository, commit and workflow produced the APK;
  the checksum proves the downloaded file matches the published one. They cover
  different problems and are both kept.

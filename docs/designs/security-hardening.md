# Security hardening

Status: implemented in the repository; a few GitHub settings still have to be
enabled by the maintainer.

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
- **Release integrity**: every tag and rolling release publishes
  `DannX.apk.sha256` and generates a build provenance attestation.
- **Private reporting**: `SECURITY.md` points to GitHub Security Advisories.
- **No secrets in the repository**: signing material lives in GitHub secrets;
  session tokens are excluded from Android backups.

## GitHub settings to enable

These are repository settings, not files, so they are applied once from the
GitHub UI or API:

- **Secret scanning** and **push protection**.
- **Private vulnerability reporting** (Security Advisories).
- **Dependabot alerts** and **Dependabot security updates**.
- **Actions permissions**: allow only selected actions, require SHA pinning, and
  set the default `GITHUB_TOKEN` to read-only.
- **Ruleset for `main`**: require `checks` and `debug`, block force-pushes and
  deletions. This is already configured.
- **Release immutability** for published tags, if available.

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

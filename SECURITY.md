# Security

English | [Español](SECURITY.es.md)

## Reporting a vulnerability

If you find a security issue (data leaks, unauthorized access, etc.), **do not
open a public issue**. Report it privately through
[GitHub Security Advisories](https://github.com/Lxchoooo/dannx/security/advisories/new)
and include:

- The app version and phone model.
- The steps to reproduce it.
- The impact: which data or features are exposed.

You will get a response as soon as possible while the report is investigated and
a fix is prepared.

## Scope

- The DannX app and the builds published in Releases.

Out of scope: issues in third-party services, the Android system, or other apps.

## Practices already in place

- The backend service key is never used in the app or in the repository.
- Database writes are restricted by server rules to a single administrator
  account.
- The release signing key lives as a GitHub secret, not in the repository.
- Public Supabase registration is disabled: the app only signs in with the
  administrator account.
- The schema and policies live in `docs/migraciones/`; `supabase/setup.sql` is
  kept as historical reference. The migration workflow verifies that
  `collections.is_public` exists, that RLS is enabled on collections and items,
  and that the policies use that column.
- `main` rejects deletions and force-pushes; pull requests require the `checks`
  and `debug` checks (with a bypass for the admin role). `debug` runs only on
  pull requests. Actions are pinned to SHA and the repository enforces SHA
  pinning.
- There are no write deploy keys; pushes use GitHub tokens.
- Session tokens live in private preferences excluded from device backups.
- The release APK does not include debug logs.
- CI runs CodeQL, dependency review and Gradle wrapper validation. Releases
  publish a SHA-256 checksum and a build provenance attestation.

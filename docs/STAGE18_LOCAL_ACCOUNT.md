# Stage 18 — Local / Offline Accounts

This patch adds Pojav-style local profiles while keeping them clearly separate from Microsoft-authenticated accounts.

## What it does

- Lets the user create, select, and delete local profile names.
- Persists only profile names and selected local profile in Android private SharedPreferences.
- Generates the conventional deterministic `OfflinePlayer:<name>` UUID.
- Builds a `legacy` profile with access token `0`, empty properties, and no Microsoft client/Xbox IDs.
- Restores a previously selected local account after app process recreation, but never overrides an already active session.
- Allows local/offline profile launch through the same game host after account/profile validation.

## Limitations

- Local profiles do not authenticate account ownership and cannot impersonate an official Microsoft profile.
- Online-mode servers typically reject local profiles. Only use servers that explicitly permit offline-mode identities.
- This patch does not implement server-side authentication bypass, skin entitlement, Microsoft services, or token generation.
- Microsoft access tokens remain in memory only, unchanged from Stage 17.

## Apply

Extract the changed-file ZIP at the Sally Launcher project root and overwrite files at their relative paths. Rebuild using the existing GitHub Actions workflow.

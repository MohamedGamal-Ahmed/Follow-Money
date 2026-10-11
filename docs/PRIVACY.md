# Privacy and data handling

## Local storage

Transactions, plans, account identifiers and preferences are stored on the device. The SQLite ledger is **not encrypted**. Stored original financial source text is encrypted using Android Keystore. Device security and access to the app's storage remain relevant.

The app requests no Internet permission and has no account, cloud sync or telemetry service. App-managed Android backup is disabled in the manifest and backup rules; this does not guarantee protection from a rooted device or third-party copying.

## Optional permissions

| Access | Purpose |
| --- | --- |
| Read / receive SMS | Preview historical SMS and capture new supported financial messages |
| Notification access | Read payment notifications from apps selected by the user |
| Post notifications | Transaction alerts and reminders |
| Exact alarms | Optional precise daily refresh scheduling |
| Boot events | Restore scheduling after restart |

Notification access is granted through Android settings. Users choose which payment apps to capture. Unsupported messages may be ignored; captured history is not a guarantee of complete financial coverage.

## Sharing and export

CSV export is user-initiated through Android's document picker and contains financial transaction data. It does not include stored original SMS text. Treat exported files as private.

The GitHub support preview contains app version/build, device manufacturer/model, Android version/API and locale. Opening the form passes these fields to GitHub in a browser URL. The app does not include transactions, amounts, account numbers, SMS, device serial numbers or Android IDs in that preview. The browser and GitHub have their own data handling.

The user must sign in, review the form and submit it. Reports go to the public `Follow-Money-Support` repository; users receive no access to the private app source. Reports and attachments are public. Redact screenshots and use invented reproductions; do not attach database backups or financial exports.

## Repository hygiene

Private device audits, backups, local SDK configuration, signing material and build outputs are ignored. Only curated documentation and synthetic test code should be shared. `tools/check-repo.py` provides a preflight check, not a guarantee that every file is free from sensitive content.

For a suspected security issue, follow [SECURITY.md](../SECURITY.md).

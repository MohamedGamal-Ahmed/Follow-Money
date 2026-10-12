# Privacy and data handling

The end-user policy is bundled in [privacy-policy.txt](../app/src/main/assets/privacy-policy.txt) and shown in the app's Help and Data/Privacy sections. [Public HTML](https://mohamedgamal-ahmed.github.io/Follow-Money-Support/privacy.html) and the [local publishing source](../support/privacy.html) are generated from that exact text by `python tools/render-privacy.py`. [Google Play preparation](GOOGLE_PLAY_RELEASE.md) tracks submission gates and draft declarations.

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

Optional voice entry opens the system speech recognition provider after an in-app disclosure. Its network behavior depends on the provider; no offline guarantee is made. The app receives text for review and does not save audio.

CSV export is user-initiated through Android's document picker and contains financial transaction data. It does not include stored original SMS text. Treat exported files as private.

Problem and suggestion dialogs show the exact metadata automatically added: app version/build, device manufacturer/model, Android version/API and locale. The app reads no financial records when composing support requests. The description is user-written; do not enter account/card numbers or paste bank messages. Opening the draft hands that description and metadata to the email app for delivery to mgamal.ahmed@outlook.com. A configured email account is required, and the user reviews and sends the draft themselves. If no email app handles the intent, the address and request are copied to the clipboard. Email providers have their own data handling.

The optional developer GitHub route opens the public `Follow-Money-Support` repository. GitHub sign-in is required to submit reports; reports and attachments are public. Users receive no access to the private app source. Redact screenshots and use invented reproductions; do not attach database backups or financial exports. The browser and GitHub have their own data handling.

## Repository hygiene

Private device audits, backups, local SDK configuration, signing material and build outputs are ignored. Only curated documentation and synthetic test code should be shared. `tools/check-repo.py` provides a preflight check, not a guarantee that every file is free from sensitive content.

For a suspected security issue, follow [SECURITY.md](../SECURITY.md).

## Retention and deletion

Local records remain until deleted in-app, cleared through Android, or removed by uninstalling. Financial clear-data removes ledger/evidence/archive and associated financial data; it does not remove original phone SMS, exported files, external support correspondence, every UI preference or the optional profile photo. Photo deletion is available under Appearance. Android clear-app-data also clears local preferences/files. Revoke SMS and Notification Access separately to stop new capture.

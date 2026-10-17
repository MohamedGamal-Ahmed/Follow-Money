# Contributing and reporting

This is a private project maintained by [Mohamed Gamal](https://github.com/MohamedGamal-Ahmed). Only authorized developers receive source access. Users report issues in the separate [public support repository](https://github.com/MohamedGamal-Ahmed/Follow-Money-Support), without an invitation to the source repository.

## Bug reports

Use the bug form in the public support repository or the app's **المساعدة وعن التطبيق** settings. Reports and attachments are public. Describe:

1. What you did, including the relevant screen and filter/currency.
2. What happened and what should have happened.
3. App version, device and Android version.
4. How often it happens, and a redacted screenshot if useful.

For a parsing or calculation problem, supply invented data that reproduces it. Replace names, phone numbers, account/card identifiers and amounts consistently so the arithmetic still works. Never attach real SMS, OTPs, database files, exports or private device logs. The issue form's privacy checkbox is a reminder; always review attachments yourself.

Use the feature form for suggestions. Explain the user problem and desired behavior before proposing implementation.

## Code changes

- Use Java 17 and the existing native Android architecture. Discuss new dependencies first.
- Keep changes focused. Preserve source messages, user corrections and financial migration behavior.
- Add meaningful cases for calculation/parsing fixes, including false-positive and duplicate scenarios.
- Use synthetic data on a disposable emulator. Do not run write-oriented instrumentation on a user's phone.
- Run `python tools/check-repo.py` and `./tools/run-checks.ps1 -Online`; use `-Android` when changing Android storage, receivers or widgets.
- A pull request touching `ExpenseDb.java`, `SmsReceiver.java`, `PaymentNotificationListener.java`, `SmsImportActivity.java` or `MainActivity.java` must have the emulator CI job green before merge.
- Open a pull request describing the resulting behavior, validation and known limits. Avoid unrelated formatting and generated artifacts.

Commit messages should be clear English. Do not commit or push on someone else's behalf without their authorization.

## What belongs in Git

Source, curated docs, build configuration, synthetic test code and asset attribution belong here. Local SDK paths, signing keys, APKs, database copies, financial audits, screenshots containing personal data and `.tmp/` do not. Ignore rules reduce accidental inclusion; they cannot sanitize files already tracked or force-added.

For sensitive security findings, follow [SECURITY.md](SECURITY.md) instead of uploading exploitable details or personal data to an issue.

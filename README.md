# Follow Money

An Arabic Android app for understanding everyday spending, transfers and recurring payments. Follow Money builds a local financial timeline from supported bank SMS and selected payment notifications, with manual entry and review whenever the source is unclear.

**تتبّع صرفك وتحويلاتك والتزاماتك من مكان واحد، ببيانات محفوظة على جهازك.** [دليل الاستخدام بالعربي](docs/USAGE_AR.md)

| | |
| --- | --- |
| Status | Private project, under active development |
| App version | 0.2.0 — development build |
| Platform | Android 8.0+ / API 26+ |
| Implementation | Java 17, native Android views, SQLite |
| Services | No backend, account registration or analytics service |

## What it does

- **Capture:** supported banking SMS, selected payment app notifications, manual transactions and text sharing. Historical SMS import includes a preview before saving.
- **Review:** transactions grouped by date, filters, editable categories, original source details and account/card identity when available.
- **Understand:** spending categories, money flow, recurring payments and insights with links to the underlying transactions. Currencies stay separate.
- **Plan:** daily limits, category budgets and confirmed installment/subscription/bill schedules.
- **Keep it visible:** three resizable home screen widgets, optional hidden amounts, daily reminders and payment alerts.
- **Get help:** GitHub bug and feature forms, with a review step before sharing basic device information.

The parser runs locally and uses rules. It does not guarantee recognition of every bank or notification format. Review ambiguous transfers, unclassified expenses and proposed commitments before relying on totals.

<img src="docs/screenshots/settings.png" alt="Follow Money Settings: six focused categories in the Arabic interface" width="300">

Actual emulator capture of the Settings screen; no financial data is shown.

## Reading the numbers

| Number | Meaning |
| --- | --- |
| Everyday spending | Expenses after the applicable refund/reimbursement adjustments; identified commitments are shown separately |
| Commitments | Recorded payments identified as installments, bills or subscriptions; a future schedule alone does not create an expense |
| Transfers to others | Outgoing transfers that are not identified as transfers between your own accounts or as commitments |
| Internal transfers | Movement between your confirmed accounts; not income or consumption |
| Income | Recorded incoming money after applicable refund adjustments; not necessarily salary |
| Daily remaining | Your configured daily limit minus eligible spending today; income does not increase the limit |
| Account balance | Latest explicit bank balance, or an estimate from that anchor and later transactions, labeled accordingly |

These are records captured by the app, not a complete bank statement. Different currencies are never silently added together. [Architecture and calculation boundaries](docs/ARCHITECTURE.md)

## Run locally

Install Android Studio, Java 17 and Android SDK platform 34 / build tools 34.0.0. Use the Gradle wrapper included in this repository.

```powershell
git clone https://github.com/MohamedGamal-Ahmed/Follow-Money.git
Set-Location Follow-Money
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

Open the project in Android Studio and select your local SDK. Alternatively, create an ignored `local.properties` containing your own `sdk.dir`. Do not copy another developer's machine paths.

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. This is a development build; production signing and distribution are not configured. Repository access is required to clone this private project.

## Checks

```powershell
python tools/check-repo.py
# First run / dependencies not yet cached:
.\tools\run-checks.ps1 -Online
# Subsequent runs with dependencies cached:
.\tools\run-checks.ps1
# Additional database/widget/feature checks on a disposable emulator:
.\tools\run-checks.ps1 -Android -Device emulator-5554
```

The script runs the standalone JVM suites, assembles the app and instrumentation APK, and runs Android lint. `-Android` also installs both APKs and runs the three instrumentation groups. Use an emulator with synthetic data: these checks exercise writes and repairs. They are deliberately blocked on physical device IDs.

GitHub Actions uses the same checks for pushes and pull requests to `main`; emulator instrumentation remains a local step. No CI pass or production readiness is implied by the presence of the workflow.

## Project map

```text
app/src/main/java/com/mohamedgamal/masareef/  App, parsing, storage and calculations
app/src/main/res/                           Native layouts, widgets and local assets
app/src/androidTest/                       Android database and feature checks
tools/                                    JVM checks and repository validation
docs/                                     Curated usage, architecture and privacy docs
.github/                                  Issue forms, PR template and CI
```

## Report a problem

Use **الإعدادات ← المساعدة وعن التطبيق ← الإبلاغ عن مشكلة**, or [open a bug report](https://github.com/MohamedGamal-Ahmed/Follow-Money-Support/issues/new?template=bug_report.yml). Include the app version, device, Android version, steps and expected behavior. Use invented sample transactions and redact screenshots; never attach real SMS, databases, account details or exports.

**Application source stays private.** User reports go to the separate [public support repository](https://github.com/MohamedGamal-Ahmed/Follow-Money-Support), which requires GitHub sign-in to submit but no source access or invitation. Reports and attachments are public. The app opens a form in the browser; it does not create an issue automatically. [Reporting and contribution guide](CONTRIBUTING.md)

The `support/` folder is the reviewed, code-free publishing source for the support repository. Its README, forms and guides are copied into a separate checkout; application source and private development docs are never copied there.

## Privacy and ownership

Financial data stays in app-local storage. Stored source text is encrypted with Android Keystore; the SQLite ledger itself is not encrypted. Android backup is disabled in the app configuration. CSV exports are created only when requested and contain financial data. GitHub support details contain app/device/Android/locale information, not transactions. [Privacy details](docs/PRIVACY.md)

No open-source license has been granted for this private project's original code. Third-party fonts and brand assets retain their own terms; see [asset attribution](BRAND_ASSETS.md). Commercial redistribution needs an asset-rights review.

[Change log](CHANGELOG.md) · [Security reporting](SECURITY.md) · [GitHub setup](docs/GITHUB_SETUP.md)

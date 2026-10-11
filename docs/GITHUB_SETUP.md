# GitHub setup and first publication

Source: `MohamedGamal-Ahmed/Follow-Money` — private. Support: `MohamedGamal-Ahmed/Follow-Money-Support` — public. Both default branches: `main`.

## Prepared in source

- Public bug and feature issue forms in `support/.github/ISSUE_TEMPLATE`, with reproduction fields and privacy guidance.
- Pull request template and `CODEOWNERS` naming the maintainer.
- Read-only CI permissions; pinned checkout, Java and artifact actions.
- Java/JVM checks, debug build, instrumentation APK compilation and Android lint on pushes/PRs to `main`.
- Seven-day debug-build/lint/check-log artifacts. No device exports or database files are included.
- Monthly Dependabot proposals for Gradle and GitHub Actions; no automatic merging. AGP and Gradle wrapper proposals are patch-only; coordinate larger toolchain upgrades manually.
- Ignore rules for SDK paths, keys, builds, local financial audits and backups.

## First publication

Review `git diff --cached` and run `python tools/check-repo.py`. Commit and push only after the owner authorizes them. A remote URL alone is not push authorization.

```powershell
git remote -v
git status --short
python tools/check-repo.py
.\tools\run-checks.ps1 -Online
# After authorization and review:
git commit -m "Prepare Follow Money project and GitHub support"
git push -u origin main
```

The issue forms and workflow become active after publication to the repository's default branch. Check the first Actions run; locally passing Windows checks do not prove the hosted Ubuntu run has passed.

## Owner settings after publication

1. Confirm `main` is the default branch on both repos. Enable Issues on Support; the source repo uses Support as its user-facing issue tracker.
2. Confirm Actions is enabled and the `Android checks` workflow completes.
3. Invite developers to the source repo only when source access is intended. Customers and testers use Support without a source invitation.
4. Keep `bug` and `enhancement` labels used by the forms; add triage labels only as needed.
5. Consider a rule requiring pull requests and the build check before merging. Availability/enforcement for private repositories depends on the GitHub plan; do not assume this file enables protection.
6. Enable private vulnerability reporting if supported and suitable for the repository.
7. Review dependencies and asset rights before signing/distributing a production APK. Signing secrets belong outside Git, or in dedicated GitHub secrets for a future release workflow.

`GitHubSupport.REPOSITORY` points to the public support repo. The `support/` folder contains the exact publishing source for its README, forms, usage, reporting, privacy and security docs. Publish only those reviewed files to the separate Support checkout; never copy the app, build outputs, development docs or device evidence.

No license, signed release, automatic deployment or auto-merge policy is selected by this setup.

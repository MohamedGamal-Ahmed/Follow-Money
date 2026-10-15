"""Stdlib preflight: shareable files, local doc links and GitHub support wiring.

This is not a full YAML schema validator or a substitute for reviewing staged files.
"""
from pathlib import Path
from urllib.parse import unquote
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []


def require(condition, message):
    if not condition:
        errors.append(message)


def git_files():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT, capture_output=True, check=True,
    )
    return sorted(set(result.stdout.decode("utf-8").split("\0")) - {""})


files = git_files()
allowed_docs = {"docs/ARCHITECTURE.md", "docs/USAGE_AR.md", "docs/PRIVACY.md", "docs/GITHUB_SETUP.md", "docs/GOOGLE_PLAY_RELEASE.md", "docs/CLOSED_TESTING_PLAN.md", "docs/NEXT_STEPS_2026-10-13.md", "docs/BRAND_CATALOG_CANDIDATES_2026-10-10.csv", "docs/screenshots/settings.png"}
allowed_docs.add("docs/RELEASE_FREEZE_2026-10-14.md")
allowed_docs.add("docs/CLAUDE_AUDIT_TRIAGE_2026-10-15.md")
private_paths = re.compile(r"(^|/)(\.tmp|\.gradle|\.idea|build|captures|__pycache__)(/|$)|(^|/)local\.properties$|\.(apk|aab|jks|keystore|p12|pfx|dpapi\.xml|db|sqlite\w*|tar|log|hprof)(-|$)|(^|/)\.env($|\.)", re.I)
credential = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b|\bAIza[A-Za-z0-9_-]{35}\b")
for name in files:
    require(not private_paths.search(name) or name.endswith("/.env.example") or name == ".env.example", f"Private/generated file included: {name}")
    require(not name.startswith("docs/") or name in allowed_docs or name.startswith("docs/brand-catalog-2026-10-10/"), f"Unreviewed historical document included: {name}")
    path = ROOT / name
    if not path.is_file():
        continue
    raw = path.read_bytes()
    if b"\0" in raw:
        continue
    content = raw.decode("utf-8", errors="replace")
    require(not credential.search(content), f"Possible credential in file (review locally): {name}")
    if name.endswith(".md"):
        for target in re.findall(r"\]\(([^)]+)\)", content):
            target = target.strip().split(' "', 1)[0].strip("<>")
            if re.match(r"^[a-z]+:", target, re.I) or target.startswith("#"):
                continue
            local = (path.parent / unquote(target.split("#", 1)[0])).resolve()
            require(local.exists(), f"Broken local link in {name}: {target}")
            require(local.is_relative_to(ROOT), f"Link outside checkout in {name}: {target}")
            if local.is_relative_to(ROOT) and local.is_file():
                require(local.relative_to(ROOT).as_posix() in files, f"Link to ignored/unshared file in {name}: {target}")

forms = ROOT / "support/.github/ISSUE_TEMPLATE"
form_ids = {}
for filename in ("bug_report.yml", "feature_request.yml"):
    content = (forms / filename).read_text(encoding="utf-8")
    ids = re.findall(r"^\s+id: ([a-zA-Z0-9_-]+)$", content, re.M)
    require(len(ids) == len(set(ids)), f"Duplicate issue field IDs: {filename}")
    require("privacy" in ids, f"Missing privacy review field: {filename}")
    require(all(re.search(rf"^{key}:", content, re.M) for key in ("name", "description", "body")), f"Missing issue form header: {filename}")
    form_ids[filename] = set(ids)

support = (ROOT / "app/src/main/java/com/mohamedgamal/masareef/GitHubSupport.java").read_text(encoding="utf-8")
keys = set(re.findall(r'appendQueryParameter\("([^"]+)"', support)) - {"template"}
require(keys <= form_ids["bug_report.yml"], "App support query fields do not match bug form IDs")
require('"bug_report.yml"' in support and '"feature_request.yml"' in support, "App issue template wiring missing")
require('"https://github.com/MohamedGamal-Ahmed/Follow-Money-Support"' in support, "App support must point to the public support repo")
for forbidden in ("ExpenseDb", "ANDROID_ID", "getSerial", "READ_SMS", "SharedPreferences"):
    require(forbidden not in support, f"Support metadata accesses sensitive source: {forbidden}")

workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
for action in re.findall(r"uses: (\S+)", workflow):
    require(bool(re.fullmatch(r"actions/[a-z-]+@[0-9a-f]{40}", action)), f"CI action is not pinned: {action}")
require("contents: read" in workflow and "persist-credentials: false" in workflow, "CI permissions/credential isolation missing")
require("retention-days: 7" in workflow, "CI artifact retention must remain bounded")
require(".tmp/ci/checks.txt" in workflow and not re.search(r"^\s+\.tmp/\*|^\s+\.tmp/$", workflow, re.M), "CI must not upload private temp folders")
require("-Online" in workflow, "Hosted build must allow dependency downloads")

if errors:
    print("Repository preflight FAILED:")
    for error in errors:
        print("-", error)
    sys.exit(1)
print(f"Repository preflight passed: {len(files)} shareable files, doc links, issue fields and pinned CI actions.")

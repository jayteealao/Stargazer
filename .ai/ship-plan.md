---
schema: sdlc/v1
type: ship-plan
slug: stargazer
plan-version: 2
created-at: "2026-10-28T21:34:19Z"
updated-at: "2026-10-29T07:11:10Z"
project-name: "Stargazer"
template-hint: none

# === Required core — read by /wf ship ===

# Block A — what ship means
ship-meaning: publish
ship-environments:
  - name: "github-releases"
    auto-promote: false
ship-cadence: on-demand

# Block B — versioning contract
version-scheme: semver
version-source-of-truth:
  - { path: "app/build.gradle.kts", field: "android.defaultConfig.versionName" }
  - { path: "app/build.gradle.kts", field: "android.defaultConfig.versionCode" }
version-bump-rule: manual
version-bump-cmd: ""
prerelease-suffix: none
post-release-version: none
post-release-version-cmd: ""

# Block C — CI/CD contract
ci-pipeline:
  pre-merge-checks: [debug-build, unit-tests, pr-title, commit-messages, secret-scan]
  release-trigger: tag-on-main
  release-workflow-file: ".github/workflows/release.yml"
  release-jobs: [build-and-release]
  publish-dry-run-cmd: "./gradlew --no-daemon clean assembleRelease bundleRelease"
  publish-cmd: "git tag v$VERSION <merge-sha> && git push origin v$VERSION"
  # plan v2: no signing secrets are required; releases publish an unsigned APK and AAB.
  required-secrets: []
  secrets-staleness-threshold-days: 90
  ci-ergonomics:
    dep-cache: true
    matrix: { os: ["ubuntu-latest"], versions: ["21"] }
    release-concurrency: true
    path-filters: false

# Block D — post-publish verification contract
post-publish-checks:
  - { kind: github-release, cmd: "gh release view v$VERSION --json tagName,isDraft --jq '[.tagName, .isDraft] | @tsv'", expect: "v$VERSION\tfalse" }
propagation-window-min-minutes: 5
propagation-window-max-minutes: 30
poll-interval-seconds: 60

# Block E — rollout + rollback contract
rollout-strategy: immediate
rollback-mechanism: fix-forward
rollback-time-estimate-min: 30
rollback-cmd: ""
rollback-verify-cmd: "gh release view v$VERSION --json tagName,isDraft --jq '[.tagName, .isDraft] | @tsv'"
prior-artifact-retention: "Every GitHub Release stays published; no release is deleted."
irreversible-steps:
  - "An APK that a person downloaded and installed stays installed; a later release cannot remove it."
  - "Room database migrations run on the device and are forward-only; an older APK cannot open a database that a newer one migrated."
db-migrations-reversible: false

# Block F — recovery playbooks
recovery-playbooks:
  # plan v2: signing-missing and signing-incomplete are removed; releases are unsigned by design.
  - id: client-id-invalid
    triggers: ["GITHUB_APP_CLIENT_ID is blank", "GITHUB_APP_CLIENT_ID has characters"]
    steps:
      - "Find where GITHUB_APP_CLIENT_ID is set for the release build (a Gradle property or an environment variable in release.yml)."
      - "Remove the empty or wrong value, or set the real client ID (see docs/github-app-setup.md)."
      - "Re-run the failed release run: gh run rerun <run-id>."

# Block G — stakeholder + announcement contract
announcement:
  channels: []
  template-path: ".ai/release-announcement-template.md"

# === Inbound half — read by /wf project ship-plan build (and the local gate in the PR phase of /wf ship) ===

# Block H — code-quality gates
code-quality:
  format-check: { tool: "none", cmd: "" }
  lint:         { tool: "none", cmd: "" }
  type-check:   { tool: "n/a", cmd: "" }
  build:        { tool: "gradle", cmd: "./gradlew --no-daemon assembleDebug" }
  test-coverage: { min-percent: null, cmd: "./gradlew --no-daemon testDebugUnitTest" }
  commit-convention:   { spec: conventional, config-path: "lefthook.yml", enforce: [local, ci] }
  pr-title-convention: { spec: conventional, enforce: [ci] }

# Block I — local developer experience
local-dx:
  git-hooks:
    framework: lefthook
    hooks:
      pre-commit: ["gitleaks git --staged --redact --no-banner"]
      commit-msg: ["check the subject against the conventional-commit pattern ^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\(.+\\))?!?: .+"]
      pre-push:   ["./gradlew --no-daemon testDebugUnitTest"]
  editorconfig: true
  runtime-version-files: []
  task-runner: { kind: none, targets: {} }
  bootstrap-cmd: "lefthook install && ./gradlew assembleDebug"
  contributing-doc: true

# Block J — repo governance
governance:
  branch-protection:
    base-branch: "main"
    mechanism: ruleset
    required-checks: ["debug-build", "unit-tests", "pr-title", "commit-messages", "secret-scan"]
    required-approvals: 0
    dismiss-stale-reviews: false
    require-up-to-date: false
    enforce-admins: false
    require-code-owner-reviews: false
    require-conversation-resolution: true
    require-linear-history: false
    allow-force-pushes: false
    allow-deletions: false
    apply-via: gh-api
    deletion-rule-scope: ["refs/heads/main", "refs/tags/v*"]
    delete-branch-on-merge: true
  codeowners: []
  pr-template: true
  issue-templates: true
  dependency-automation: { tool: none, ecosystems: [], schedule: "" }
  merge: { method: rebase, auto-merge: false, merge-queue: false }
pr-shape: single

# Block K — security & supply-chain gates
security:
  sast:             { tool: none, cmd: "", schedule: "" }
  dependency-audit: { tool: "none", cmd: "", fail-on: high }
  secret-scanning:  { tool: gitleaks, cmd: "gitleaks git --redact --no-banner", pre-commit: true }
  sbom:             { tool: none, format: cyclonedx, publish-with-release: false }
  license-check:    { tool: "none", allow: [], deny: [] }

# === Extensions — open schema, not read by /wf ship unless a consumer opts in by id ===

additional-contracts: []
---

# Ship Plan — Stargazer

## What "ship" means here
Ship publishes a GitHub Release of the Android app. After the PR merges into `main`, ship tags the merge commit `v<version>`. The tag push starts `.github/workflows/release.yml`. That workflow builds the release APK and AAB and creates the GitHub Release with the APK, the AAB, the R8 mapping file and a changelog file. There is no Play Store upload. Releases happen on demand, one environment (`github-releases`), with no automatic promotion. Evidence: `release.yml` runs `on: push: tags: v*` and calls `softprops/action-gh-release@v2`.

## Versioning
The scheme is semver, as `CHANGELOG.md` states. The version lives in `app/build.gradle.kts`, in two fields that change together on every release: `versionName` (the semver string, today `1.1`) and `versionCode` (an integer that increases by 1 on each release, today `2`). The bump is manual: ship proposes patch, minor or major from the commits since the last tag, the person confirms, and ship writes both fields in one `build: bump version to <version>` commit. The hand-written `[Unreleased]` section of `CHANGELOG.md` becomes the `[<version>] - <date>` entry; ship does not regenerate the file, because the repo has no `cliff.toml` and a default git-cliff run would replace the curated text. No prerelease suffix and no post-release bump. The repo has no tags yet, so the first ship creates the first tag.

## CI/CD pipeline
Pre-merge checks, all on `ubuntu-latest` with JDK 21 and the Gradle cache:
- `debug-build` — `./gradlew assembleDebug` (runs today in `pr_check.yml`).
- `unit-tests` — `./gradlew testDebugUnitTest`.
- `pr-title` and `commit-messages` — conventional-commit form; with rebase merges every commit lands on `main`, so every commit of the PR is checked.
- `secret-scan` — gitleaks over the PR.

The release trigger is a `v*` tag on `main`. `release.yml` has one job, `build-and-release`. Its dry run is `./gradlew clean assembleRelease bundleRelease`. No repository secret is required (plan v2): with no signing secrets, the release publishes an unsigned APK and AAB, and an unsigned APK cannot be installed on a device. The four `SIGNING_*` secrets stay optional; when all four exist, `release.yml` signs the build (see `docs/release-signing.md`). `GOOGLE_SERVICES_JSON_B64` is referenced by the workflows but the build does not use it, so it is not required. `manual-release.yml` (manual dispatch, draft release, JDK 17) stays as a fallback outside this plan.

## Post-publish verification
One check: `gh release view v$VERSION` must show the tag and `isDraft: false`. Ship polls every 60 seconds, from 5 minutes up to 30 minutes after the tag push.

## Rollout strategy
Immediate: a GitHub Release is available to everyone at once. There is no staged rollout.

## Rollback playbook
Fix forward. A bad release is not deleted; ship publishes a patch release that corrects it, in about 30 minutes. Detection: a failed release run, a missing or draft release, or a defect report. Two steps cannot be undone: an APK that a person installed stays installed, and Room migrations on the device are forward-only, so an older APK cannot be offered as a downgrade.

## Recovery playbooks
- `client-id-invalid` — the build rejected `GITHUB_APP_CLIENT_ID`: fix or remove the value, re-run. Seeded from `docs/github-app-setup.md` and `app/build.gradle.kts`.

## Stakeholder + announcement
No announcement channel. The GitHub Release notes are the announcement, and ship skips its announce step.

## Code-quality gates
The gates are the debug build and the unit tests (`./gradlew testDebugUnitTest`), with no coverage threshold. There is no formatter, no linter and no separate type check; the Kotlin compiler in the debug build covers types. Commit messages and PR titles follow Conventional Commits, checked locally by a lefthook `commit-msg` hook and in CI by a PR-title job and a commit-message job. These gates, plus the gitleaks scan, are the pre-merge checks above.

## Local developer experience
Lefthook runs three hooks: `pre-commit` scans staged changes with gitleaks, `commit-msg` checks the conventional-commit pattern, and `pre-push` runs the unit tests. A new contributor runs `lefthook install && ./gradlew assembleDebug`. The repo ships a `.editorconfig` (Kotlin style: 4 spaces, LF, final newline) and a `CONTRIBUTING.md`. There is no task runner beyond the Gradle wrapper and no runtime version file.

## Repo governance
A ruleset on `main` requires the five pre-merge checks and resolved conversations, needs 0 approvals (one maintainer), and blocks force-pushes and deletion. The existing `default` ruleset blocks deletion on every branch; it is narrowed to `main` and `v*` tags so merged feature branches can be deleted, and the repo setting "delete branch on merge" is turned on. Its Copilot review rule stays. PRs merge by rebase, with no auto-merge and no merge queue. The repo ships a PR template and issue templates, no CODEOWNERS file and no dependency bot. `/wf project ship-plan build` applies these settings through `gh api`, behind its own confirm step.

## Security & supply-chain gates
gitleaks only: in CI on every PR (a pre-merge check) and as the lefthook `pre-commit` hook. No SAST, no dependency audit, no SBOM and no license policy.

## Additional contracts
None.

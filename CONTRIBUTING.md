# Contributing to Stargazer

## Set up a clone

You need JDK 21, the Android SDK (platform 36), [lefthook](https://lefthook.dev) and [gitleaks](https://github.com/gitleaks/gitleaks).

```bash
lefthook install && ./gradlew assembleDebug
```

`lefthook install` turns on the git hooks in `lefthook.yml`:

- **pre-commit** scans the staged changes for secrets with gitleaks.
- **commit-msg** checks that the commit subject follows Conventional Commits.
- **pre-push** runs the unit tests.

## Run the checks on your machine

Every pull request must pass the same checks in CI:

| Check | Command |
|---|---|
| debug-build | `./gradlew assembleDebug` |
| unit-tests | `./gradlew testDebugUnitTest` |
| secret-scan | `gitleaks git --redact` |

CI also builds the release APK and AAB without signing (`./gradlew assembleRelease bundleRelease`) to prove that the release build compiles.

## Commit messages and pull request titles

Write each commit subject and each pull request title in the [Conventional Commits](https://www.conventionalcommits.org) form: `<type>(<optional scope>): <description>`. The types are `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore` and `revert`. Examples:

- `feat: name the selected star list in the folded drawer header`
- `fix: keep the star after a failed unstar`

Pull requests merge by rebase, so every commit lands on `main` as it is. Squash `fixup!` commits before you merge.

## Pull requests

1. Branch from `main`.
2. Open a pull request against `main` and fill in the template.
3. Wait for the checks to pass and resolve every review conversation.
4. Merge with "Rebase and merge". The branch is deleted after the merge.

## Signing and secrets

Never commit a keystore, a signing password or a GitHub key. Release signing reads its settings from an ignored `signing.properties` or from the environment; see `docs/release-signing.md`.

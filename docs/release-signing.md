# How to sign a release build of Stargazer

A release build is signed with the upload key from four settings. The build reads them from a
`signing.properties` file at the repository root, which git ignores, or from environment variables
of the same names, which is how CI provides them. Nothing about signing is kept in the repository.

## The four settings

| Name | What it holds |
|---|---|
| `SIGNING_STORE_FILE` | Path of the keystore file, relative to the repository root, or absolute |
| `SIGNING_STORE_PASSWORD` | Password of the keystore |
| `SIGNING_KEY_ALIAS` | Alias of the upload key inside the keystore |
| `SIGNING_KEY_PASSWORD` | Password of that key |

## Sign on your own machine

1. Copy `signing.properties.example` to `signing.properties` at the repository root.
2. Fill in the four values. The file is a Java properties file: write paths with forward slashes
   (`C:/keys/upload.jks`, not `C:\keys\upload.jks`), because a backslash starts an escape there.
   A relative path is read from the repository root, not from `app/`.
3. Run `./gradlew :app:assembleRelease` (or `:app:bundleRelease`).

Keep the keystore itself outside the repository, or in a git-ignored path. Never commit
`signing.properties`.

## Sign in CI

Set the four names as secrets and pass them to the build as environment variables. When
`SIGNING_STORE_FILE` is set in the environment, CI's debug builds are signed with the same key, so
they install over a release build. A `signing.properties` file never changes how a debug build on
your own machine is signed.

## When something is missing

- No `SIGNING_STORE_FILE` anywhere: the release build is produced unsigned.
- `SIGNING_STORE_FILE` set but another value missing: the build stops at configuration and names the
  missing setting.

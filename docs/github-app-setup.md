# How to register the GitHub App for Stargazer

Stargazer signs people in to GitHub with the device flow of a GitHub App. The app shows a short code, the
person enters it on github.com, and GitHub hands the app an access key. The device flow needs only the
app's **client ID**, which is public. There is no client secret and no private key, and no server is
involved.

Follow this guide when you register the app for the first time, when you check an existing registration,
or when you build a fork that should sign people in to your own GitHub App.

## Before you start

- A GitHub account that will own the app. The upstream app is owned by a personal account.
- A clone of this repository and a working Android build (`./gradlew :app:assembleDebug`).

## 1. Create the GitHub App

1. Open <https://github.com/settings/apps/new> (for an organization:
   *Organization settings → Developer settings → GitHub Apps → New GitHub App*).
2. Fill in the basics:
   - **GitHub App name**: a unique name, for example `Stargazer` or `Stargazer (my fork)`.
     People see this name on GitHub's approval screen.
   - **Homepage URL**: the repository URL.
   - **Callback URL**: leave empty. The device flow does not use it.
3. Under **Identifying and authorizing users**:
   - Clear **Expire user authorization tokens**. Stargazer has no refresh step; when GitHub rejects a key,
     the app asks the person to sign in again.
   - Clear **Request user authorization (OAuth) during installation**.
   - Select **Enable Device Flow**.
4. Under **Webhook**, clear **Active**. The app receives no webhooks.
5. Under **Permissions**:
   - **Account permissions → Starring**: **Read and write**.
   - Leave every **Repository permission** and every **Organization permission** at **No access**.
   - Leave every other account permission at **No access**.
6. Under **Where can this GitHub App be installed?**, select **Any account**. With **Only on this account**,
   nobody but the owner can sign in.
7. Select **Create GitHub App**.

If the form has no **Expire user authorization tokens** checkbox, open the new app's settings, go to
**Optional features**, and opt out of **User-to-server token expiration**.

## 2. Do not create secrets

On the app's **General** page, do **not** select **Generate a new client secret**, and do **not** generate a
private key. Stargazer needs neither. A secret placed in an Android app can be extracted from the APK by
anyone, so the app is built never to need one.

If a secret or private key was created by mistake, delete it on the same page.

## 3. Copy the client ID

On the app's **General** page, copy the **Client ID**. It starts with `Iv` and is safe to commit: GitHub
treats it as public, and anyone can read it from a released APK.

## 4. Put the client ID in the build

The build reads the client ID in this order and writes the first value it finds to
`BuildConfig.GITHUB_APP_CLIENT_ID`:

1. The Gradle property `GITHUB_APP_CLIENT_ID`: `-PGITHUB_APP_CLIENT_ID=…` on the command line, an entry in
   `~/.gradle/gradle.properties`, or the environment variable `ORG_GRADLE_PROJECT_GITHUB_APP_CLIENT_ID`.
2. The environment variable `GITHUB_APP_CLIENT_ID`.
3. The default in `app/build.gradle.kts` (`defaultConfig`), which is the upstream app's client ID.

Choose the option that fits:

- **Upstream maintainers** who register a new app: replace the default in `app/build.gradle.kts`.
- **Forks**: set the Gradle property or the environment variable. Without it, a fork signs its users in to
  the upstream app.

The build fails with a message naming `GITHUB_APP_CLIENT_ID` if the value is empty.

Check the result:

```sh
./gradlew :app:generateDebugBuildConfig
grep GITHUB_APP_CLIENT_ID app/build/generated/source/buildConfig/debug/uk/adedamola/stargazer/BuildConfig.java
```

## 5. Star lists need no extra permission

Stargazer reads GitHub star lists with the GraphQL query `viewer { lists { … } }`. A key from an app with
only the Starring permission reads them: this was checked on 2026-10-25, and the query returned the
account's lists with their item counts. Do not add any other permission for lists.

The same check showed that the per-repository star endpoint (`/user/starred/{owner}/{repo}`) works for
public repositories with this key; GitHub grants read access to public repository metadata automatically.

## 6. Check the approval screen

Install a build with your client ID, select **Sign in with GitHub**, and enter the code on github.com.
GitHub's approval screen must list only **Starring** and no access to repositories or code.

Device codes can be used for phishing: an attacker can show a code that, once entered, approves *their*
session. Enter only codes that the Stargazer app shows you, and check that the approval screen names your
app.

## 7. Revoke the app

A person who wants to remove Stargazer's access opens
**GitHub Settings → Applications → Authorized GitHub Apps**, selects the app, and selects **Revoke**.
The next request from Stargazer is then rejected, and the app asks the person to sign in again.
Signing out inside Stargazer deletes the key on the phone but does not revoke the app on GitHub.

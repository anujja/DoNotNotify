# Play Store publishing

Releases to Google Play run through [fastlane supply](https://docs.fastlane.tools/actions/supply/),
driven by `fastlane/Fastfile`. The `publish-to-play` job in
`.github/workflows/release.yml` runs it on every version bump.

Until the four secrets below exist, that job is a **green no-op** — it logs a
notice and skips. Nothing about the existing GitHub-release flow changes.

## One-time setup

### 1. Service account

Most of this happens in the *Google Cloud* Console, not the Play Console. The
old "Play Console → Setup → API access" page is gone — a developer account no
longer needs to be linked to a Cloud project.

1. Create (or pick) a project at
   [console.cloud.google.com/projectcreate](https://console.cloud.google.com/projectcreate).
2. Enable the **Google Play Android Developer API** for it:
   [console.developers.google.com/apis/api/androidpublisher.googleapis.com](https://console.developers.google.com/apis/api/androidpublisher.googleapis.com/)
   → **Enable**.
3. [IAM & Admin → Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts)
   → **Create service account**. Name it anything; no Cloud IAM roles are needed.
4. Open the new account → **Keys** → **Add key → Create new key → JSON**. That
   downloaded file is the `PLAY_SERVICE_ACCOUNT_JSON` secret.
5. Back in Play Console →
   [Users and permissions](https://play.google.com/console/users-and-permissions)
   → **Invite new users** → paste the service account's email address (it ends
   in `.iam.gserviceaccount.com`) → grant **Release manager** on
   `com.donotnotify.donotnotify` → **Invite user**.

Step 5 is the only Play Console step, and it needs account-owner or admin
rights. Permissions can take a few hours to propagate;
`bundle exec fastlane android validate` is the cheapest way to check whether
they have.

### 2. Upload keystore

Play App Signing means the key CI uses is the *upload* key, not the app signing
key. Use the same one already used for manual uploads from Android Studio — the
one whose credentials go in `local.properties` as `KEYSTORE_FILE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Base64-encode it for GitHub:

```bash
base64 -w0 /path/to/upload.jks
```

### 3. Repository secrets

Add under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the service account JSON |
| `PLAY_UPLOAD_KEYSTORE_BASE64` | `base64 -w0` of the upload keystore |
| `PLAY_UPLOAD_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_UPLOAD_KEY_ALIAS` | key alias |
| `PLAY_UPLOAD_KEY_PASSWORD` | key password |

The API cannot create a Play listing from nothing: a **first build must be
uploaded manually** in the Console before any of this works.

## How a release flows

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts`, add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, push to `main`.
2. `check-version` skips everything if a GitHub release for that `versionName`
   already exists.
3. `build-and-release` builds the APK signed with the public `github` key and
   attaches it to a GitHub release (unchanged).
4. `publish-to-play` builds an AAB signed with the *upload* key and sends it to
   the **internal** track, along with that version's changelog.

Promotion to production stays a deliberate act — either in the Console, or:

```bash
bundle exec fastlane android promote version_code:58 to:production
bundle exec fastlane android promote version_code:58 to:production rollout:0.1
```

`workflow_dispatch` accepts a `play_track` input, but only for a version that
has not been released yet — `check-version` gates the whole workflow on that.

## Local use

Needs Ruby (the repo has no Gemfile.lock yet; `bundle lock` will create one):

```bash
bundle install
cp /path/to/service-account.json fastlane/play-service-account.json  # gitignored

bundle exec fastlane android validate                 # dry run, uploads nothing
bundle exec fastlane android internal                 # build + upload to internal
bundle exec fastlane android metadata                 # push store listing text
bundle exec fastlane android metadata with_images:true  # ...and screenshots/icon
```

## What the release lanes deliberately don't touch

Release uploads set `skip_upload_metadata`, `skip_upload_images` and
`skip_upload_screenshots`. Store listing text in `fastlane/metadata/` is only
pushed by the `metadata` lane, run on purpose.

This matters if you ever run a **store listing experiment**: applying a winning
variant edits the listing in the Console, and a release that also synced
metadata would silently revert it to whatever the repo last held. Changelogs are
different — they are per-version and live in the repo, so they upload every time.

The R8 mapping needs no separate upload: AGP embeds it in the AAB at
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` and Play
extracts it automatically.

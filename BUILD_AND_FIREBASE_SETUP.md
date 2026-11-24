# Mousomi AI — Firebase Google Sign-In & Signed Release APK Build Guide

## 1. Application Identity
- **Application ID (Package Name):** `com.aistudio.mousomiai.xkmzpq`
- **Kotlin Namespace:** `com.example`
- **Compile SDK / Target SDK / Min SDK:** `36.1` / `36` / `24` (Android 12+ fully supported)
- **AGP / Gradle:** `9.1.1` / `9.3.1` (JDK 21 required for Robolectric SDK 36 unit tests; bytecode targets Java 11)

## 2. Firebase & Google Cloud Setup (Required for Google Sign-In, Firestore Sync, and Firebase AI)
1. Open the **Firebase Console** (for the project matching your `firebase_applet_config.xml` / Firestore database) and register an Android app with exact package name:
   `com.aistudio.mousomiai.xkmzpq`
2. In **Authentication → Sign-in method**, enable the **Google** provider.
3. Add **both** your Debug and Release signing certificate **SHA-1** and **SHA-256** fingerprints under **Project Settings → Your apps → Android app**:
   - **Debug keystore (`debug.keystore`)** included via `debug.keystore.base64` (password `android`, alias `androiddebugkey`):
     ```bash
     keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     - SHA-1: `24:0A:7C:05:2B:0E:EA:A0:E7:60:E4:4D:49:82:CC:A2:5C:DC:D7:4E`
     - SHA-256: `86:E5:AB:78:9B:1B:D1:6D:A2:FE:98:D6:C2:CB:26:52:9B:C2:3D:EB:06:E9:55:C5:D9:66:CD:54:7D:6B:E1:3F`
   - **Release keystore (`my-upload-key.jks` / `release.keystore`)**:
     ```bash
     keytool -list -v -keystore my-upload-key.jks -alias upload
     ```
     Copy the printed `SHA1` and `SHA256` into Firebase Console **before** downloading `google-services.json`.
4. Ensure an **OAuth 2.0 Web Client ID** (`oauth_client` with `client_type = 3`) is configured for the Firebase project (created automatically when Google Sign-In is enabled in Firebase Authentication).
5. Download the updated `google-services.json` from Firebase Console.
   - For local builds: place it at `app/google-services.json` (gitignored).
   - For GitHub Actions: encode it to Base64 and store it in `GOOGLE_SERVICES_JSON_BASE64`.

## 3. Generate a Stable Release Keystore (Once)
Keep this keystore safe and private — **every future Release APK update must be signed with the exact same key**:
```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```
Encode files for GitHub Secrets (single-line Base64):
```bash
base64 -w 0 app/google-services.json
base64 -w 0 my-upload-key.jks
base64 -w 0 debug.keystore
```
*(On macOS, omit `-w 0` and pipe through `tr -d '\n'`.)*

## 4. GitHub Actions Secrets (`Settings → Secrets and variables → Actions`)
| Secret | Required For | Description |
|---|---|---|
| `GOOGLE_SERVICES_JSON_BASE64` | Google Sign-In, Firestore, AI Logic & Release APK | Base64-encoded `app/google-services.json` for `com.aistudio.mousomiai.xkmzpq` |
| `RELEASE_KEYSTORE_BASE64` | Signed Release APK | Base64-encoded `.jks` / `.keystore` release signing file |
| `STORE_PASSWORD` | Signed Release APK | Release keystore store password |
| `KEY_PASSWORD` | Signed Release APK | Release key password |
| `KEY_ALIAS` | Signed Release APK (optional, defaults to `upload`) | Key alias inside the release keystore |
| `DEBUG_KEYSTORE_BASE64` | Debug APK (optional) | Overrides `debug.keystore.base64` if desired |

## 5. GitHub Actions Workflow (`.github/workflows/android-build.yml`)
- **Job 1 (`validate-and-test`)**: Validates Gradle Wrapper & `.env.example`, compiles Kotlin (`:app:compileDebugKotlin`), runs `:app:testDebugUnitTest` on JDK 21, and always uploads `unit-test-reports`.
- **Job 2 (`build-debug-apk`)**: Runs independently (not blocked by test failures), restores `debug.keystore` from `DEBUG_KEYSTORE_BASE64` or `debug.keystore.base64` (or generates a standard Android `debug.keystore` via `keytool` if neither is present), builds `:app:assembleDebug`, verifies signature & package ID, and uploads **`Mousomi-AI-debug-apk`** (`Mousomi-AI-debug.apk`).
- **Job 3 (`build-signed-release-apk`)**: Requires `validate-and-test`, valid `google-services.json`, and release signing secrets. Builds `:app:assembleRelease`, verifies signature, package ID, and generated `default_web_client_id`, and uploads **`Mousomi-AI-release-apk`** (`Mousomi-AI-release.apk`). Skips cleanly with instructions if secrets are not configured.
- **Job 4 (`publish-release`)**: Runs only after a verified signed Release APK build, publishing an immutable tagged release (`v<versionName>-b<versionCode>`) and safely updating `latest` with `--clobber`.

## 6. Downloading & Installing APKs (e.g. Samsung Galaxy M51)
- **Debug APK (testing):** Open **GitHub → Actions → Android CI & Release Build → latest run → Artifacts → `Mousomi-AI-debug-apk`**, unzip, and install `Mousomi-AI-debug.apk`.
- **Signed Release APK (production & in-place updates):** Open **GitHub → Releases → `latest`** (or the `Mousomi-AI-release-apk` workflow artifact) and download `Mousomi-AI-release.apk`. Future builds triggered with a higher `versionCode` (automatic via `github.run_number` or manual `workflow_dispatch` input) will update the installed app cleanly when signed with the same release keystore.

# UtsavPost - Festival, Birthday & Status Maker 🇮🇳
**Package**: `com.utsavpost.maker`

A native Android Java app designed for creating personalized festival greetings, birthday posters, political & social media statuses in 1 click.

---

## 🌟 Key Features
- **Multilingual Festival Catalog**: 22 authentic templates across Hindi, Bhojpuri, and Marathi for Diwali, Chhath Puja, Ganeshotsav, Shiv Jayanti, Makar Sankranti, Birthdays, and Suvichar.
- **Dynamic Poster Canvas**:
  - 4:5 aspect ratio with rich gradients and image backdrops.
  - Draggable & editable quote typography.
  - Toggleable decorative gold border frame.
- **Live Branding Footer Strips**:
  - Automatically displays user's profile photo, name, and designation.
  - 3 distinct visual styles: *Classic Ribbon*, *Modern Floating Pill*, and *VIP/Political Banner*.
- **Monetization (Google AdMob)**:
  - Banner ads on Main & Editor screens.
  - Frequency-capped Interstitial ads on save.
  - Rewarded video ad to unlock watermark removal.
- **Viral Sharing**: 1-tap direct sharing to WhatsApp with promotional download links.

---

## 🚀 Cloud Build via GitHub Actions (No Android Studio Needed)

This repository includes a production-ready CI/CD pipeline at `.github/workflows/build_android.yml`.

### How to Build on GitHub:
1. Initialize your GitHub repository and push code:
   ```bash
   git add .
   git commit -m "Initial commit of UtsavPost"
   git remote add origin https://github.com/<your-username>/utsavpost.git
   git push -u origin main
   ```
2. Navigate to the **Actions** tab in your GitHub repository.
3. The build will run automatically and produce:
   - **`UtsavPost-Debug-APK`**: Direct installable `.apk` for testing on your phone.
   - **`UtsavPost-Release-APK`**: Signed release `.apk`.
   - **`UtsavPost-Release-Bundle-AAB`**: Android App Bundle ready to upload to the Google Play Console.
4. Download the artifacts directly from the workflow summary page!

---

## 🔑 Generating Your Play Store Release Keystore

To generate your permanent `release-key.jks` using standard Java `keytool`, run in PowerShell:

```powershell
keytool -genkeypair -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias utsavpost -dname "CN=UtsavPost, OU=Mobile, O=UtsavPost, L=NewDelhi, S=Delhi, C=IN"
```

### Local Signing Setup:
1. Copy `keystore.properties.example` to `keystore.properties`.
2. Enter your keystore password and alias.
3. `keystore.properties` and `*.jks` are automatically ignored by `.gitignore`.

### GitHub Secrets Setup (Optional):
Copy your base64 keystore into your clipboard:
```powershell
[Convert]::ToBase64String([System.IO.File]::ReadAllBytes("release-key.jks")) | Set-Clipboard
```
Add it to **Settings > Secrets and variables > Actions**:
- `KEYSTORE_BASE64`: (paste the copied string)
- `KEYSTORE_PASSWORD`: your keystore password
- `KEY_ALIAS`: `utsavpost`
- `KEY_PASSWORD`: your key password

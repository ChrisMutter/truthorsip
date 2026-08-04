# Truth or Sip — Android build

Standalone 18+ party game. Capacitor project, same workflow as Mbogi.
Package: com.chrismutter.truthorsip · API 36 · icons/splash/haptics configured.
Questions live in www/content.js (easy to expand). Game logic in www/index.html.

## Get an APK (cloud build — no local Android SDK needed)
1. Create a GitHub repo (e.g. ChrisMutter/truthorsip), private is fine.
2. From this unzipped folder:
   git init && git add . && git commit -m "Truth or Sip v0.1"
   git branch -M main
   git remote add origin https://github.com/ChrisMutter/truthorsip.git
   git push -u origin main
3. Repo → Actions → "Build Truth or Sip APK" runs automatically (~5 min).
4. Open the green run on your phone → Artifacts → truthorsip-debug-apk → install.

## Iterate
Edit www/content.js (add questions) or www/index.html, then push. Fresh APK builds itself.

## Adding questions
In www/content.js each category has a `q: [ ... ]` array — just add strings.
To add a whole new category, copy a category block and give it a unique key.

## Publishing
Signed AAB via Android Studio (Build → Generate Signed App Bundle) — back up the keystore.
Fill listing from store-listing.md. Expect a Mature 17+ rating (correct for this app).

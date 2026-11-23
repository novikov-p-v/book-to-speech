# Publishing on Google Play

Application ID: **`io.github.novikovpv.booktts`** (permanent — never change it after the first upload).

## 1. Create the upload key (once)

Keep the keystore and passwords **outside the repository** and back them up. If you lose the upload key,
Google can reset it, but it takes days.

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/booktts-upload.jks -alias booktts \
        -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=/home/<you>/keys/booktts-upload.jks
storePassword=...
keyAlias=booktts
keyPassword=...
```

Alternatively set `BOOKTTS_STORE_FILE`, `BOOKTTS_STORE_PASSWORD`, `BOOKTTS_KEY_ALIAS`, `BOOKTTS_KEY_PASSWORD`.
Without either, release builds are signed with the debug key (OK for your own phone, rejected by Play) and
Gradle prints a warning.

## 2. Build

```bash
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab  -> upload to Play
./gradlew assembleRelease   # app/build/outputs/apk/release/app-<abi>-release.apk -> sideloading
```

Run these as **separate** commands: per-ABI APK splits are switched off whenever a bundle task is requested.

Before each upload, increase `versionCode` (and usually `versionName`) in `app/build.gradle.kts` — Play rejects
a `versionCode` it has already seen. Rebuild after changing it, then verify the bundle:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep SHA1   # = upload key
grep -rho 'android:version\(Code\|Name\)="[^"]*"' app/build/intermediates/merged_manifest/release/
```

Release notes in Play Console use language tags (≤ 500 chars per language):

```
<ru-RU>
• ...
</ru-RU>
<en-US>
• ...
</en-US>
```

## 3. Play Console checklist

- [ ] Developer account (one-time $25, identity verification).
- [ ] Create app: name "Book to Speech", default language, *App*, *Free*.
- [ ] **App signing:** let Google manage the app signing key; upload the `.aab` signed with your upload key.
- [ ] **Store listing:** texts below, 512×512 icon, 1024×500 feature graphic, ≥ 2 phone screenshots (`docs/store/screenshots/`).
- [ ] **Privacy policy URL:** `https://github.com/novikov-p-v/book-to-speech/blob/main/PRIVACY.md`
- [ ] **Data safety:** no data collected, no data shared. (Downloads go directly to GitHub.)
- [ ] **Ads:** no. **Content rating:** questionnaire (reference/education, no user content shared).
- [ ] **Target audience:** 13+ (simplest; avoids the Families program requirements).
- [ ] **Foreground service declaration** (Android 14+):
  - *Media playback* — "Reads the user's book aloud with the screen off; controlled from the media notification."
  - *Data sync* — "Downloads a voice model the user requested and exports a book to audio files the user requested."
  Google may ask for a short screen recording of each.
- [ ] **Closed testing** (new personal accounts): ≥ 12 testers opted in for 14 consecutive days, then apply for production.

## 4. Licensing notes

- **Licences:** the project's own source code is **MIT** (`LICENSE`). The built app also contains espeak-ng
  (GPL-3.0, inside sherpa-onnx 1.13.8), so the app package as distributed is covered by **GPL-3.0**: users must be
  able to get the complete corresponding source. The public GitHub repository satisfies this; keep it in sync
  with released versions (tag each release, e.g. `v0.1.0`). MIT is GPL-compatible, so nothing else changes.
- Voices are **downloaded by the user** from their original hosting, not bundled. The catalog
  (`app/src/main/assets/voices.json`) only lists voices whose licences allow redistribution and any use
  (CC0, public domain, CC BY / CC BY-SA with credit, Apache-2.0, OpenRAIL-M). Credits are shown in the app's
  *About* dialog. Removed for licence reasons: Piper *ru_RU-irina* (unknown), *ru_RU-ruslan* and *en_US-ryan*
  (non-commercial), *en_US-lessac* (research only) — they can still be imported manually from a `.tar.bz2`.
- Supertonic 3 is under **OpenRAIL-M** (use-based restrictions, e.g. no deceptive impersonation); the app's use
  (reading the user's own books) is compatible.
- The bundled RUAccent ё dictionary is MIT (`app/src/main/assets/ru/LICENSE-ruaccent.txt`).

## 5. Store listing draft

**Title:**
- EN (en-US listing): Book to Speech: offline TTS  (≤ 30 chars; or just "Book to Speech")
- RU (default listing): Книга вслух

**Short description (≤ 80):**
- EN: Listen to your TXT, FB2 and PDF books. Natural voices, fully offline.
- RU: Слушайте свои книги TXT, FB2 и PDF. Естественные голоса, без интернета.

**Full description (EN):**
Book to Speech turns your own e-books into audiobooks right on your phone, with no internet connection and no account.

• Opens TXT (any encoding), FB2 / FB2.ZIP and PDF books
• Natural neural voices that run entirely on your device: Supertonic (31 languages), Kokoro (English) and Piper
• English, Russian, Ukrainian, German, French, Spanish, Italian, Polish and more
• Remembers your place in every book; tap any sentence to start reading from it, and the current sentence is highlighted
• Keeps reading with the screen off; control playback from the notification, lock screen or headphones
• Adjustable speed and chapter navigation
• Export a whole book to audio files (one per chapter) to listen in any player
• Choose a voice per language or per book, and preview voices before downloading
• Russian: restores «ё» and improves stress for correct pronunciation
• No ads, no tracking, open source

Each voice is downloaded once (20–350 MB). After that, everything works offline, even in airplane mode.

**Full description (RU):**
«Книга вслух» превращает ваши электронные книги в аудиокнигу прямо на телефоне — без интернета и без регистрации.

• Открывает TXT (любая кодировка), FB2 / FB2.ZIP и PDF
• Естественные нейросетевые голоса работают полностью на устройстве: Supertonic (31 язык), Kokoro (английский), Piper
• Русский, английский, украинский, немецкий, французский, испанский, итальянский, польский и другие
• Запоминает место; нажмите на любое предложение, чтобы читать с него; текущее предложение подсвечивается
• Читает с выключенным экраном; управление из уведомления, с экрана блокировки и наушников
• Экспорт книги в аудиофайлы (по файлу на главу)
• Для русского восстанавливается «ё» для правильного произношения
• Без рекламы и слежки, открытый исходный код

Голоса скачиваются один раз (20–350 МБ); после этого всё работает офлайн.

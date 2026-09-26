# Samjho

Android app for the iQOO hackathon. The patient records a doctor consultation (with the doctor's
permission) and the phone explains it back in Hindi or English. Everything runs on the device;
the app requests no network permission (`INTERNET` is removed from the merged manifest).

## Rules the code enforces

- Samjho never adds medical advice. It only restates the doctor's words.
- Every extracted item stores the transcript line it came from and can show that line.
- A deterministic rule extractor runs first and finishes in well under a second. An on-device
  language model may only ADD items, never overwrite them.
- Anything the doctor did not say is shown as "not mentioned", never guessed.
- A medicine name that was inferred (sounds like, an alternative hearing, an AI match) shows the
  raw word that was heard, is marked UNCONFIRMED, and only the patient's answer confirms it.

## Run the demo

Judges get this repository plus an APK they build locally. Neither the speech models nor the
language model are in git, and no APK is committed.

Prerequisites: Windows, Android Studio's SDK (or `ANDROID_HOME`), a JDK 17+, and an Android phone
(Android 8.0+) with USB debugging on.

1. Download the models (next section) somewhere on this computer.
2. Plug the phone in, accept the USB debugging prompt, then from the repo root:

```powershell
.\scripts\setup-phone.ps1 `
  -ModelsPath  C:\path\to\vosk-downloads `
  -GemmaPath   C:\path\to\Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task
```

The script copies the Vosk models into `app/src/main/assets/` if they are missing, builds the APK,
installs it, and pushes Gemma to `/data/local/tmp/llm/` on the phone. It is safe to re-run: models
already in place are not copied again, and a Gemma file already on the phone is not pushed again.
Add `-Serial <id>` if several devices are connected.

Both models are optional for the baked-in demos:

| Missing | What still works | What does not |
|---|---|---|
| Vosk models | Android recogniser (English), all three baked-in demos | Vosk recording, and any Hindi recording |
| Gemma | Everything. The language-model pass is simply off and the result says RULES ONLY | The AI name-repair layer |

## Downloads and licences

These are third-party models, used unmodified. Samjho's own code does not include them.

**Vosk speech models** (Alpha Cephei), from https://alphacephei.com/vosk/models

| Language | File | Unzips to |
|---|---|---|
| Hindi | `vosk-model-small-hi-0.22.zip` | `vosk-model-small-hi-0.22/` |
| English (India) | `vosk-model-small-en-in-0.4.zip` | `vosk-model-small-en-in-0.4/` |

Licence: the one stated for each model on the Vosk models page (Apache 2.0 at the time of writing);
the Vosk library itself is Apache 2.0. Put the zips, or their unzipped folders, in one folder and
pass that folder as `-ModelsPath`. `am/`, `conf/` and `graph/` must end up directly inside
`app/src/main/assets/model-hi/` and `model-en-in/`.

**Gemma 3 1B IT** (Google), the LiteRT-LM file `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task`
(about 530 MB), from https://huggingface.co/litert-community/Gemma3-1B-IT

You must be logged in to Hugging Face and accept the Gemma licence on that page before the download
works. Use is governed by the Gemma Terms of Use (https://ai.google.dev/gemma/terms) and its
Prohibited Use Policy. That repository holds several similarly named files for different runtimes;
this exact `.task` file is the one the app is built against. Pass its path as `-GemmaPath`.

## Speech engines

The engine is a setting on the home screen, defaulting to Android.

- **Android** uses the phone's own recogniser, biased toward medicine names, and asks for 5
  hypotheses per sentence. On the iQOO I2501 the strict on-device recogniser has no models, so it
  falls back to the system recogniser, and for English from `en-IN` to `en-US`, which Google does
  have offline there. Every switch is logged, and the active mode is shown on the recording screen.
  The system recogniser only *prefers* offline; do not claim it can never use a server. To verify
  offline behaviour, turn on airplane mode **and** switch Wi-Fi and mobile data off, then confirm
  `adb shell ping -c 1 8.8.8.8` reports "Network is unreachable".
- **Vosk** is fully offline on any phone but its small models cannot spell most drug names.
  Hindi recording needs Vosk, because Android has no offline `hi-IN` model on this phone.

Each transcript line is logged as `SamjhoSpeech`, and how each medicine was identified and what
the patient decided is logged as `SamjhoNames` (`adb logcat -s SamjhoSpeech SamjhoNames`).

## How medicine names are recovered

1. **Rules**: exact and near-exact lexicon matches, then consonant-skeleton sound matching, only
   beside dosing words.
2. **N-best**: if the top hearing named no known medicine, a lower-ranked hearing with a confident
   match beside dosing words may be used. On the offline recogniser measured so far the lists were
   the same sentence each time, so this layer rarely fires.
3. **Language model**: a dosing line no item cites is offered up to 5 sound-alike candidates and
   must answer exactly one of them or `UNKNOWN`; anything else is discarded.
4. **Patient confirm**: anything from steps 1 to 3 that is not an exact match waits for Yes / No /
   Choose another.

A dosing line whose medicine could not be named at all is still shown, as the doctor's own words.

## Merging the language model

The rest of the app depends only on this interface, in
`app/src/main/java/com/packetloss/samjho/llm/LlmEngine.kt`:

```kotlin
interface LlmEngine {
    /** Null when the model can be used right now; otherwise why not. */
    fun unavailableReason(): String?

    /** Blocking. Call off the main thread. Returns the raw reply, or null on any failure or timeout. */
    fun complete(prompt: String): String?
}
```

Adapt the `:llm`-process repository to it and return that adapter from `LlmEngines.create` in
`llm/LlmEngines.kt`, which currently returns a "no model" stub. `complete` must return null rather
than throw, must enforce its own timeout, and its reply is treated as untrusted text. After adding
the LiteRT-LM dependency, re-check the packaged permissions: the APK must still request only
`RECORD_AUDIO`.

## Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

The suite includes real transcripts captured on the iQOO I2501 (`RealVoskTranscriptTest`,
`RealAndroidTranscriptTest`, `RealNBestTest`) as regressions, and invariants such as "no extraction
ever creates a confirmed medicine".

## Known limits

- The medicine lexicon is small (about 25 common outpatient drugs); unknown brands are still caught
  by "X tablet" phrasing but are shown as heard, not identified.
- Recognisers mishear drug names. That is why inferred names are confirmed by the patient.
- Reminders and read-aloud are not built yet.

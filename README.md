# Samjho

Android app for the iQOO hackathon. The patient records a doctor consultation (with the
doctor's permission) and the phone explains it back in Hindi or English. Everything runs on the
device; the app requests no network permission.

## Rules the code enforces

- Samjho never adds medical advice. It only restates the doctor's words.
- Every extracted item stores the transcript line index it came from and can show that line.
- A deterministic rule extractor runs first and finishes in well under a second. An on-device
  model may later only add items, never overwrite them.
- Anything the doctor did not say is shown as "not mentioned", never guessed.

## Third-party models (not our code)

The speech models are open-source Vosk models by Alpha Cephei, used unmodified and kept out of
git (see `.gitignore`). Place them in `app/src/main/assets/`:

| Language | Model | Folder |
|---|---|---|
| Hindi | `vosk-model-small-hi-0.22` | `model-hi/` |
| English (India) | `vosk-model-small-en-in-0.4` | `model-en-in/` |

Source: https://alphacephei.com/vosk/models (Apache 2.0). `am/`, `conf/` and `graph/` must sit
directly inside each folder.

The on-device language model is Gemma 3 1B IT (int4, LiteRT-LM `.task` file), released by Google
under the Gemma terms of use. It is pushed to the phone at `/data/local/tmp/llm/` rather than
bundled in the APK.

## Run

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

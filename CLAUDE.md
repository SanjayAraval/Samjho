# Samjho

Android app for the iQOO hackathon. A patient records a doctor's consultation (with permission) and the
phone explains it back in Hindi or English, on the device. Code here is written fresh for the event.

## Non-negotiables

These are the product. Several are enforced by tests; do not weaken a test to get around one.

1. **Never add medical advice.** The app only restates the doctor's words. No dosing suggestions, no
   "you should", no drug information from our own knowledge. Read-aloud and the shared summary follow it too.
2. **Every item cites a transcript line.** Each medicine, note, warning and follow-up carries the line
   indices it came from, and the UI can show them ("Show the doctor's words"). Anything not said is
   shown as "not mentioned", never guessed.
3. **Nothing is confirmed without the patient.** An inferred medicine name (sounds like, alternative
   hearing, AI match, or an unknown word beside "tablet") is created `UNCONFIRMED`, shows the raw heard
   word, and only the patient's Yes / Choose another confirms it. No code path may create a `CONFIRMED`
   medicine. A guess is never spoken or shared as a fact.
4. **No INTERNET permission, ever.** The manifest strips it (`tools:node="remove"`). After adding or
   upgrading any dependency, check the packaged APK:
   `aapt2 dump permissions app-debug.apk` must list only `RECORD_AUDIO`.
5. **Rules first, then the LLM.** `RuleExtractor` is deterministic and must return in 1 to 2 seconds; the
   result screen shows it before any model runs. The LLM runs afterwards, in its own process, off the main
   thread, behind the small `LlmEngine` interface.
6. **The LLM may only add, and must be grounded.** It is asked about a dosing line with no medicine, is
   offered up to 5 sound-alike lexicon candidates, and must answer exactly one of them or `UNKNOWN`.
   Anything else is discarded. Results are appended with `Extraction.withAdded`, never merged over a rules
   item, and are marked "AI matched" and unconfirmed. If the model is missing or fails, the rules result
   stands.
7. **Never touch airplane mode, Wi-Fi or mobile data on the test phone.** It reaches the laptop through
   Office Kit and airplane mode drops that link. Reading state (`settings get`, `ping`) is fine; changing
   it is not. Ask the user instead. Offline verification happens only when the user asks and sets the
   phone up themselves. Do not call a run offline unless they did and a ping confirms it.

## How we work

- **Pushing:** a fix for a user-visible bug goes straight to `origin/main` without asking, so main stays
  demo-ready. Features, releases and anything else: ask first. Never force-push. Run the tests first.
- **Verify on the phone.** Tests do not prove a UI change works. If the phone is locked, stop and ask;
  never try to get past a lock screen. Say if you change any phone setting (auto-rotate is currently off).
- **Real captures become fixtures.** Raw recogniser output goes into a `Real*Test` unedited. Rules are
  tuned against real data, and every loosening gets negative tests (`doctor`, `please`, `practice`, ...).
- **Models and Gemma stay out of git.** No LFS, no committed APKs. `scripts/setup-phone.ps1` copies the
  Vosk models, builds, installs and pushes Gemma. The app must work fully without Gemma.
- **Be honest about verification.** Say what was run and what was not. The Android system recogniser only
  *prefers* offline, so a run with the network on proves nothing about offline behaviour.

## Commands

- Tests: `.\gradlew.bat :app:testDebugUnitTest` (needs `ANDROID_HOME`, or a `local.properties` with `sdk.dir`).
- Demo build, install, Gemma: `.\scripts\setup-phone.ps1 -ModelsPath <dir> -GemmaPath <.task>`.
- Test phone: iQOO I2501, adb serial `10BFBK0TN8001GJ`, Android 16.
- Logs: `adb logcat -s SamjhoSpeech SamjhoNames SamjhoVoice SamjhoShare SamjhoLlm` (engine and mode per line,
  how each medicine was identified and what the patient decided, read-aloud, share, model backend and speed).

## Map (`app/src/main/java/com/packetloss/samjho`)

- `extract/` rules: `RuleExtractor`, `Lexicon`, `Phonetic`, `NameRepair` (LLM layer, pure and tested).
- `model/` data: `Extraction`, `Medicine` (basis, confirmation, candidates), `Utterance`, `Hypothesis`.
- `speech/` engines behind `SpeechEngine`: `AndroidSpeechEngine` (default), `VoskEngine`; `Hypotheses`.
- `llm/` the `LlmEngine` seam, implemented by `AidlLlmEngine` (app side: 90 s limit, restarts, falls back to
  rules) talking over AIDL to `LlmService` in the `:llm` process, which runs `LiteRtRunner` (LiteRT-LM Gemma:
  tries NPU, GPU, CPU, keeps the fastest, caches the result). `BackendPlan` is the pure, tested part.
  `voice/` offline read-aloud. `share/` image and PDF summary for the share sheet.
- `ui/` Compose screens and bilingual `Strings`; `SamjhoViewModel` holds the state.

## Device facts that bite

- Google's strict on-device recogniser has no models on the test phone; the system recogniser has offline
  en-US only (no en-IN, no hi-IN). Hindi recording needs Vosk, which is weak on drug names.
- The recogniser's N-best lists are near-duplicates and its confidences are all 0.00.
- There is no offline Hindi TTS voice; Hindi read-aloud says so instead of speaking.
- The phone locks, rotates and gets backgrounded by Office Kit; a live recording ends when Samjho is not
  in the foreground.
- Wi-Fi has come back on inside airplane mode before, so the airplane setting alone proves nothing.
- The `:llm` process is frozen by Android whenever the screen is off or Samjho is not in front, so the model
  only loads and answers while the phone is awake and unlocked with Samjho open. Tests need that.
- Gemma is read from `/data/local/tmp/llm/` (or the app's external files dir `llm/`). On this phone CPU
  (about 69 tok/s on the probe) beats GPU (about 40); the NPU is not usable, since no vendor NPU runtime is
  packaged and the model is not NPU-compiled. The app only tries the NPU when that library is present,
  because the runtime otherwise falls back to CPU silently and the label would lie.
- A backend is only written off after two interrupted starts, so swiping the app away cannot disable the AI.
- To exercise the timeout path, `adb shell run-as com.packetloss.samjho kill -STOP <pid of :llm>` and run a
  demo: the rules result must stay, and after 90 s the app kills and restarts the model process itself.

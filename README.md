# Yaad

An Android app that turns spoken notes into a to-do list, entirely on your phone. Tap a mic, including from the lock screen, say what's on your mind, and it becomes tasks with deadlines and ideas. *Yaad* is Hindi for memory.

Speech recognition and sorting both run on the device. The app has no internet permission, so nothing can leave the phone.

## What it does

- **One-tap capture** from a home-screen widget, a lock-screen notification or a Quick Settings tile, without unlocking.
- **Sorts what you said.** "Call the bank tomorrow, and I have an idea, we could add a dark mode" becomes a task due tomorrow and a separate idea.
- **Buckets** for tasks and ideas: To do, In progress, Completed and Discarded.
- **Removes duplicates.** Say the same thing twice and one item stays on your list, keeping any deadline either mention gave. The copy goes to Discarded, where you can restore it.
- **Reminders** two days before a deadline, one day before and that morning, plus one hour before if you gave a time.
- **A plain-text log** of every note at `Documents/Yaad/voice-log.md`. Recordings stay playable for 30 days.

## How it works

A note goes through three separate steps: recording, analysis and duplicate removal. Analysing one note never gets in the way of recording the next, and a delay in removing duplicates never holds up analysis.

```
Main process                              :analysis process
────────────                              ─────────────────
CaptureService                            AnalysisService
  records audio to disk                     Whisper: speech to text
  queues the note, then stops               Qwen3: text to tasks and ideas
                                            rules: dates, and anything the model missed
AnalysisWorker (WorkManager)
  keeps the queue, starts analysis  ─────►
  retries if analysis dies          ◄─────
  starts duplicate removal when done

DuplicateWorker (WorkManager)
  compares new items with the open list
  merges repeats, moves the copies to Discarded
```

- A crash or memory kill in `:analysis` can't touch a recording in progress. Audio reaches disk as you speak, so a crash in the main process loses a moment at most.
- Waiting notes survive crashes and reboots, and finish without the app being opened.
- The model decides what the items are and what kind they are. Dates are worked out by tested rules from words it has to quote from the note, items it invents are dropped, and items it misses that the rules caught are added back. If it fails, the rules sort the note instead. If it crashes the app, AI sorting turns itself off.
- Duplicate removal uses plain rules and no model, and it leaves anything uncertain on the list, because a leftover duplicate costs far less than a lost task. It ignores small words, word order and word endings, and knows a few everyday equivalents such as "ring" and "call" or "mom" and "mother". Items with different deadlines stay separate, since "pay rent" for two different dates is two tasks. Completed and discarded items are never compared, and each item is checked once, so one you restore stays on the list.

## Building

Needs a JDK 21 and the Android SDK; Android Studio includes both. Outside Android Studio, set `JAVA_HOME` and `ANDROID_HOME` first. Gradle downloads the NDK, CMake and the two models (435 MB, each pinned to a revision and checked against a SHA-256).

```bash
git clone --recurse-submodules --shallow-submodules https://github.com/SameerRanjanJha/yaad.git
cd yaad
./gradlew :app:assembleRelease
```

The APK, about 460 MB, is at `app/build/outputs/apk/release/app-release.apk`. Copy it to your phone and open it. Then go through the setup screen, because battery settings decide whether background analysis and reminders run on time.

It needs Android 12 or newer and an ARM chip from about 2019 or later. To support older chips, remove the `GGML_CPU_ARM_ARCH` lines from the two `CMakeLists.txt` files, at the cost of speed.

### Signing

Without any setup, builds use the standard debug key. To keep your own key, so updates install over your data, add a `keystore.properties` file. It's ignored by git.

```properties
storeFile=keystore/yaad.jks
storePassword=...
keyAlias=yaad
keyPassword=...
```

## Checking the privacy claim

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

`INTERNET` isn't in the list, and neither is `ACCESS_NETWORK_STATE`. Android won't let an app without `INTERNET` open a network socket. The only network access is at build time, to download the tools and models.

Android's cloud backup is turned off for the app, so if you lose your phone, you lose your buckets. Copy `voice-log.md` somewhere safe now and then.

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

The unit tests cover the note parser, date reading, duplicate removal, reminder timing, audio files and how the app handles model replies.

For the emulator, build with `-Pyaad.abis=x86_64`. Debug builds read `test-audio.wav` (16 kHz mono) from the app's external files folder instead of the mic when it exists, so the whole flow can run with known speech.

`tools/score_sorting.py` and the debug-only `AiEvalActivity` score a model on a set of sample notes. The current model got 56 of 57 expected items right across 40 notes. That set is small and hand-written, so treat it as a regression check, not a benchmark.

## Known limits

- Recording and transcription have run on a OnePlus Nord 5. AI sorting, crash recovery, reboot recovery and duplicate removal have been tested on an Android 15 emulator.
- Sorting is built for English. The Hindi + English mode is untested.
- Duplicate removal only catches repeats worded alike, so "prepare the client slides" and "make the client deck" stay as two items. It's also built for English.
- Phones can delay background work to save battery, and a delayed note delays its reminders.
- Android doesn't offer third-party lock-screen widgets on most phones, so the lock-screen mic is a permanent notification.

## Project layout

```
app/    the app: capture/, parse/, dedupe/, remind/, data/, log/, ui/
llm/    Kotlin and JNI wrapper around llama.cpp
tools/  scores sorting quality
third_party/  whisper.cpp and llama.cpp, as git submodules
```

## Credits

| Component | License |
|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) and the Whisper base model | MIT |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT |
| [Qwen3 0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), [quantized by Unsloth](https://huggingface.co/unsloth/Qwen3-0.6B-GGUF) | Apache 2.0 |
| AndroidX, Jetpack Compose, Room, WorkManager | Apache 2.0 |

Yaad itself is under the [MIT License](LICENSE).

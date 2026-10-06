# OpenMausBot Android companion

The Android counterpart to the iOS companion app: pair a phone with a computer
running OpenMausBot, then read and answer from the phone.

- `applicationId` — `com.openmausbot.companion`
- `minSdk` 26 (Android 8.0), `targetSdk` / `compileSdk` 37
- Deep-link scheme — `openmausbot`
- Two modules: `:core` (protocol, ported from `ios/Sources/CompanionCore`) and
  `:app` (Compose UI, Android platform)

## Build and test

Requires **JDK 17** and an Android SDK. Gradle arrives through the wrapper, so
there is nothing to install for it.

```sh
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

CI also builds the preview APK described below (`.github/workflows/ci.yml`).
Gradle caches aggressively — a suspiciously fast `BUILD SUCCESSFUL` usually means nothing ran.
Prefix `cleanTest` when a test count matters:

```sh
./gradlew cleanTest :core:test :app:testDebugUnitTest :app:assembleDebug
```

### Conversation motion

Live replies are paced on display frames rather than network batches, revealing
whole grapheme clusters with a breathing caret and a short rendered-ink tail.
Partial bold/code markers are temporarily closed for rendering. Tables reveal
whole rows: the header waits for its complete delimiter, and streaming body rows
wait for their newline, including when the text pacer has caught up. A reply already
visible as a stream swaps into its settled row without another entrance; answers
that were only shown in Hidden's plain-text status line reveal in the transcript
in at most about 0.9 seconds. Initial history and paging never replay that reveal.
Remove animations shows text immediately; TalkBack touch exploration also skips
the settled-answer reveal. Following stays at the growing reply's end only until
the reader drags away.

`StreamingTextTest` covers the pure pacing, grapheme, Markdown, table-row and status rules;
`ConversationDesignTest` covers streamed handover. The Robolectric
`StreamingMotionScreenshotTest` drives the real screen with an explicitly frozen
Compose clock at xxhdpi. Capture its Full and Hidden sequences with:

```sh
COMPANION_MOTION_CAPTURE_DIR="$PWD/.impeccable/review/motion" \
  ./gradlew :app:testDebugUnitTest --tests '*StreamingMotionScreenshotTest'
ffmpeg -framerate 20 -i .impeccable/review/motion/stream/frame-%03d.png \
  -vf 'pad=ceil(iw/2)*2:ceil(ih/2)*2' -pix_fmt yuv420p \
  ../.impeccable/review/motion/android-stream.mp4
ffmpeg -framerate 20 -i .impeccable/review/motion/hidden-reveal/frame-%03d.png \
  -vf 'pad=ceil(iw/2)*2:ceil(ih/2)*2' -pix_fmt yuv420p \
  ../.impeccable/review/motion/android-hidden-reveal.mp4
```

These are isolated native Robolectric captures, not physical-device refresh-rate
or touch-performance evidence. The normal suite keeps capture output disabled.
The one-pixel padding accommodates odd native viewport dimensions for H.264
without rescaling the xxhdpi captures.

### Responsiveness sweep (2026-10-06)

Measured on the arm64 macOS host with JDK 17; these are JVM/Robolectric results,
not physical-phone frame times or keyboard-latency claims.

**Real-chat recompositions.** `ChatPerformanceTest` mounts the actual `ChatScreen`
and `Session` against a synthetic fleet/loopback server, with six visible settled
messages. A debug-only, inline `SideEffect` probe counts both settled row
presentation and `MessageRow` bodies; its callback and lazy key expression are
absent from the constant-false release branch. Each typed character/network
frame/caption update is rendered before the next one. Motion is disabled to
isolate event-driven recomposition from intentional live animation.

| Scenario | Settled rows before → after | Message bodies before → after | Live item before → after |
| --- | ---: | ---: | ---: |
| Type 40 composer characters | 0 → 0 | 0 → 0 | 0 → 0 |
| 200 open-thread `content.delta` frames | 1,200 → 0 | 1,200 → 0 | 200 → 200 |
| 200 frames across eight other bots/threads | 1,200 → 0 | 1,200 → 0 | 200 → 0 |
| 200 Live call output-caption deltas | 0 → 0 | 0 → 0 | 0 → 0 |

Typing and captions were already isolated in this fixture. The measured fix is
eliminating transcript-wide fan-out from both the open stream and a busy fleet.
The regression test requires zero settled-row/body recompositions in all four
scenarios and requires the live item to consume the open stream.

**Fold CPU medians.** The fixed workload has 20 turns / 200 messages, including
120 tool inputs with 4,096 ASCII padding characters plus JSON framing; Updates
uses 24 such threads. Both before and after use 100 warmups and the median of
15 samples × 50 operations, consume results through a volatile sink, and assert
functional results. Values below are microseconds per operation. The preview
fixture deliberately ends in a relevant text message, so its tail scan is very
short; that speedup is not a promise for every possible transcript shape.

| Fold / activity detail | Before (µs) | After (µs) |
| --- | ---: | ---: |
| `transcriptRows` / full | 102.235 | 52.467 |
| `transcriptRows` / reduced | 108.755 | 42.875 |
| `transcriptRows` / hidden | 94.097 | 43.605 |
| `liveNarration` / hidden | 4.262 | 1.495 |
| `rosterPreview` / full | 68.494 | 0.081 |
| `rosterPreview` / reduced | 61.403 | 0.035 |
| `rosterPreview` / hidden | 54.236 | 0.051 |
| Updates aggregation / full | 939.378 | 15.565 |
| Updates aggregation / reduced | 693.535 | 14.922 |
| Updates aggregation / hidden | 649.234 | 12.518 |

**Changes and evidence.**

- [ChatScreen.kt](app/src/main/kotlin/com/openmausbot/companion/ui/ChatScreen.kt)
  retains resolved chat identity, keys transcript derivation on the selected
  thread's messages/leaf/edit, derives jump visibility, and gives settled rows
  their own skippable composition boundary.
  [MessageRow.kt](app/src/main/kotlin/com/openmausbot/companion/ui/MessageRow.kt)
  observes distinct thread-version/pending-edit/run-reference projections,
  rather than every session frame.
- [ChatPreferences.kt](core/src/main/kotlin/com/openmausbot/companion/core/ChatPreferences.kt)
  reverse-scans previews and scans only the latest narration turn.
  [Models.kt](core/src/main/kotlin/com/openmausbot/companion/core/Models.kt) and
  [DigestPresentation.kt](core/src/main/kotlin/com/openmausbot/companion/core/DigestPresentation.kt)
  memoize digest visibility without changing message serialization/equality.
  [Chat.kt](core/src/main/kotlin/com/openmausbot/companion/core/Chat.kt),
  [RosterScreen.kt](app/src/main/kotlin/com/openmausbot/companion/ui/RosterScreen.kt),
  [Updates.kt](app/src/main/kotlin/com/openmausbot/companion/ui/Updates.kt), and
  [UpdatesSheet.kt](app/src/main/kotlin/com/openmausbot/companion/ui/UpdatesSheet.kt)
  reuse screen-owned per-thread previews, pending cards, branches, and faces.
  Counting-list regressions prove 200 busy-fleet frames perform zero additional
  unchanged-transcript reads. The navigator already composes only its current
  screen, so offscreen roster screens do not collect or animate.
- [Markdown.kt](core/src/main/kotlin/com/openmausbot/companion/core/Markdown.kt)
  retains settled parsed blocks and reparses only the unresolved tail, using
  the same parser as full rendering. Every-prefix equivalence covers 17
  fixtures and 400 adjacent syntax pairs, plus rewind/replacement/reset and
  partial-marker mode changes.
  [MarkdownText.kt](app/src/main/kotlin/com/openmausbot/companion/ui/MarkdownText.kt)
  reads caret opacity in a glyph `graphicsLayer`; opacity changes preserve
  accessible text and body layout.
  [StreamingMotion.kt](app/src/main/kotlin/com/openmausbot/companion/ui/StreamingMotion.kt),
  [ThinkingView.kt](app/src/main/kotlin/com/openmausbot/companion/ui/ThinkingView.kt),
  and [PlanViews.kt](app/src/main/kotlin/com/openmausbot/companion/ui/PlanViews.kt)
  narrow frame-state reads. Thinking/plan infinite animations stop when inactive,
  motion-disabled, or clipped offscreen; mascot/working arcs already had visibility
  gating. Thinking's paused timer still captures the correct
  finished duration and preserves reopened disclosure state.
- [LiveCallManager.kt](app/src/main/kotlin/com/openmausbot/companion/audio/LiveCallManager.kt)
  owns/cancels queued and suspended media jobs, guards late attempts/computer
  changes, releases media on owner cancellation, and cancels bounded server
  cleanup on confirmation or an eight-second timeout. Twelve added lifecycle
  regressions exercise concrete job cancellation, exactly-once audio/transport
  release, and stale callbacks/responses.
  [LiveCallBar.kt](app/src/main/kotlin/com/openmausbot/companion/ui/LiveCallBar.kt)
  observes only the server call and ticks only a visible running bar.
  App-scoped server/link collectors intentionally remain between calls; an
  already-posted, bounded start request retains its late-response cleanup path.

Compose compiler metrics/reports were temporarily enabled through
`companion.composeReports`. The debug report marked `MessageRow`, `TextBubble`,
`PlanCard`, `ChatHeader`, roster `ChatRow`, and `StepBadge` **skippable**.
Protocol `Chat`/`Message`/plan/summary parameters remain cross-module unstable;
retaining their identities and remembered collections avoids falsely declaring
ordinary Kotlin lists immutable. `StepBadge` inputs were already stable;
`PacedText` now exposes only observable snapshot state and private cursor fields.
The temporary metrics configuration was removed.

Reproduce the measurements (numeric output is in Gradle test-result XML
`system-out`):

```sh
./gradlew --no-daemon :core:test --tests '*FoldMicrobenchmarkTest' \
  :app:testDebugUnitTest --tests '*UpdatesMicrobenchmarkTest' --tests '*ChatPerformanceTest'
```

Final acceptance: `./gradlew --no-daemon :core:test :app:testDebugUnitTest
:app:assembleDebug` succeeded, with **890 core tests + 1,195 app tests**, zero
failures/skips, and the debug APK assembled. Existing behavior, tags, and
semantics remain regression-covered. Not exercised: physical-device refresh
rates/input latency, actual microphone/WebRTC hardware, or a production Live
call; the call/recomposition evidence uses isolated fake media and loopback.

## Installable threads preview

```sh
cd android
./gradlew :app:assemblePreview
```

Install `app/build/outputs/apk/preview/app-preview.apk`. Its launcher name is
**OpenMausBot Preview**, its application ID is `com.openmausbot.companion.preview`,
and its version ends in `-threads-preview`. Gradle signs it with the local debug
key, so no release signing material is needed. It installs beside the released
app with separate pairing, preferences and messages; it does not update that app.

Open Preview and pair using its QR scanner or manual address form. Preview does
not register the release's `openmausbot://` pairing links or system share targets.
Its file-sharing provider uses the preview application ID too.

Pull-request CI runs the core and debug UI unit tests, builds both APK variants,
and uploads `android-threads-preview-<tested-commit>-<attempt>` for 14 days. Download
and extract that run's artifact to get the same installable preview APK. Debug
keys can differ between local builds and CI runners; Android requires the same
key to update an existing preview installation. A fresh installation after
removing a differently signed preview loses only that preview's local data.

Verify conversation changes against an isolated fixture as described in
[`docs/verification/README.md`](../docs/verification/README.md), using a disposable
emulator. These builds do not exercise live pairing or a physical phone by
themselves.

## Building a release APK

```sh
cd android
./gradlew :app:assembleRelease
```

With no signing material configured this writes an **unsigned** APK:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

That is a supported outcome, not a degraded one — it is the artifact to hand to
whoever holds the release key. The filename says which of the two happened: a
signed build writes `app-release.apk` instead.

The APK is already zip-aligned by the Android Gradle Plugin, and `apksigner`
preserves that alignment. **Do not run `zipalign` or `jarsigner`.**

## Signing it

The signing key belongs to the maintainer and never enters this repository.
`.gitignore` refuses `*.jks`, `*.keystore` and `*.p12` repo-wide, and
`android/keystore.properties` alongside them.

Create a key once, outside any clone of this repository:

```sh
keytool -genkeypair -v -keystore ~/openmausbot-release.jks \
  -storetype PKCS12 -alias openmausbot -keyalg RSA -keysize 4096 -validity 10000
```

Then, for each release:

```sh
# whichever build-tools version is installed; any recent one works
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)/apksigner"

"$APKSIGNER" sign --ks ~/openmausbot-release.jks --ks-key-alias openmausbot \
  --out app-release.apk app-release-unsigned.apk

"$APKSIGNER" verify --verbose --print-certs app-release.apk
```

Without `--ks-pass`, `apksigner` prompts for the password, which keeps it out of
the shell history. `verify` should report `Verifies` along with v2 and v3
signature scheme lines.

Keep that keystore file and its passwords backed up. Google Play ties an app to
the key that first signed it; losing it means the listing cannot be updated.

### Signing from Gradle instead

To have `assembleRelease` produce a signed APK directly, supply the key material
one of two ways. The environment wins over the file, so a CI runner cannot
silently inherit a stale `keystore.properties` from a cached workspace.

`android/keystore.properties` (gitignored):

```properties
storeFile=/absolute/path/to/openmausbot-release.jks
storePassword=…
keyAlias=openmausbot
keyPassword=…
```

or the environment, for CI secrets:

```
OPENMAUSBOT_KEYSTORE_FILE
OPENMAUSBOT_KEYSTORE_PASSWORD
OPENMAUSBOT_KEY_ALIAS
OPENMAUSBOT_KEY_PASSWORD   # optional; PKCS12 reuses the store password
```

Supply all of it or none of it. A build handed only part of the material stops
and names what is missing, rather than quietly producing an unsigned APK that
looks finished and cannot be published.

Both paths declare v2 and v3 signature schemes. v3 is what makes the key
rotatable later: without a v3 block there is no signing lineage for a new key to
prove it descends from the old one. v1 stays off — it is JAR signing, for
Android 6 and below, which `minSdk 26` already excludes.

## Versioning

There is one line to edit per release, in `app/build.gradle.kts`:

```kotlin
private val appVersionName = "1.0.0"
```

`versionCode` is derived from it as `MAJOR * 10000 + MINOR * 100 + PATCH`, so
`1.0.0` is `10000`, `1.0.1` is `10001`, `2.0.0` is `20000`. Each part must be in
`0..99`; a name outside `X.Y.Z` fails the build rather than resolving to a number
nobody chose. There is no second number to remember to bump.

Play orders uploads by `versionCode` alone and refuses one it has already
accepted, so the code has to rise whenever the name does.

## Why R8 is off

`isMinifyEnabled = false` is a decision, not an oversight, and
`app/build.gradle.kts` carries the full reasoning.

The short version: `:core` has 66 `@Serializable` classes, and
kotlinx.serialization reaches their generated serializers reflectively, by name,
at the moment a frame is decoded. R8 sees no call site for those companions and
strips or renames them. The result builds, installs and opens — then fails the
first time the phone talks to the computer, in release only, as a
`SerializationException` with a stack trace made of one-letter class names.

Turning it on means writing keep rules *and testing them* against a real pairing
and a real session, because no unit test reaches that failure mode and a
minified APK that launches proves nothing. Until someone does that work, `false`
is the tested state, and roughly 34 MB is its price.

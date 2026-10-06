# iOS transcript presentation

Run the core checks with `cd ios && swift test`. `TranscriptPresentationTests`
pins completed-turn folds, terminal patches, legacy/unfinished replies and
webhook trust boundaries. The conversation redesign adds:

- `ConversationWireTests`: optional input/summary/item ID, structured digests,
  failure markers, malformed-field tolerance and cache round trips.
- `TodoPlanTests` and `PlanTranscriptTests`: snapshot shapes, status aliases,
  omitted items, Claude Tasks folding, thread-local state and first-position /
  latest-state cards at Full, Reduced and Hidden.
- `DigestPresentationTests`: summaries default off independently of activity,
  automatic problem receipts, empty suppression, reply lookup, structured and
  legacy evidence, durations and singular/plural counts.
- `ToolCategoryTests` and `MarkdownStylingParserTests`: glyph classification
  precedence and mixed blocks / incomplete streaming Markdown.

For the native UI, generate the project with `xcodegen generate` inside `ios/`,
then use a disposable simulator. SwiftPM's `netrc` authorization provider is
required so resolving WebRTC never waits on an interactive Keychain prompt:

```sh
xcodebuild -project OpenMausCompanion.xcodeproj -scheme OpenMausCompanion \
  -sdk iphonesimulator -destination "id=$SIMULATOR_ID" \
  -derivedDataPath /tmp/omb-dd-ioschat -packageAuthorizationProvider netrc \
  CODE_SIGNING_ALLOWED=NO build

xcodebuild -project OpenMausCompanion.xcodeproj -scheme OpenMausCompanion \
  -destination "id=$SIMULATOR_ID" \
  -derivedDataPath /tmp/omb-dd-ioschat -packageAuthorizationProvider netrc \
  -resultBundlePath /tmp/omb-ios-transcript.xcresult \
  -parallel-testing-enabled NO \
  -only-testing:OpenMausCompanionUITests/TranscriptPresentationUITests \
  -only-testing:OpenMausCompanionUITests/ChatShowcaseUITests \
  -only-testing:OpenMausCompanionUITests/ThreadNavigationUITests \
  -only-testing:OpenMausCompanionUITests/StopButtonUITests \
  -only-testing:OpenMausCompanionUITests/ComposerReturnUITests \
  -only-testing:OpenMausCompanionUITests/VoiceNoteUITests \
  -only-testing:OpenMausCompanionUITests/GeneratedImageUITests \
  -only-testing:OpenMausCompanionUITests/MentionPickerUITests \
  CODE_SIGNING_ALLOWED=NO test
```

The tests launch `-store-preview -chat-presentation-preview`, using
`App/ChatPresentationPreview.json` without an API client or paired computer.
`-chat-reasoning-preview` adds the runtime frame in
`App/ChatReasoningPreview.json`. These launch flags are Debug-only.

The same suite uses `-chat-update-preview` and `App/ChatUpdatePreview.json`
to check that pasted-text wrapper lines disappear while the notes remain,
and that the Claude update card offers its manual command and Copy action.
This scene has no API client and cannot run an updater on any computer.

Check that completed narration starts inside a “Worked for 4s” fold and can
be expanded and collapsed while the final answer stays visible. Webhooks
show the task without envelope metadata; their payload opens on demand.
Hidden activity suppresses live reasoning while retaining the working
indicator. Full shows the “Thinking” disclosure, and the answer-wins case
proves streaming answers keep retained reasoning available behind “Thought
for …”, never mixed into the reply.

## Contact-poster conversation showcase

`-store-preview -chat-showcase-preview -open-first` loads
`App/ChatShowcasePreview.json`, without an API client. Pepper's first chat
contains an ask, completed narration, three real TodoWrite input snapshots,
read/shell/edit/web/failed steps with command summaries, a mixed Markdown
reply (lists, tasks, quote, inline code, fence and aligned table), structured
problem and successful digests, a webhook task, compaction and an approval.
`-chat-showcase-busy-preview` adds an in-progress plan and live narration;
`-chat-reasoning-preview` keeps the existing retained reasoning fixture.
`-chat-answer-preview` adds a synthetic answer stream to prove answer priority.
`-chat-showcase-component question|credential|routine|diff|report` selects
an alternate native-card branch for action, security, report and composer
evidence. Credential previews deliberately retain the secure-pairing guard.
All existing preview flags remain available.

`ChatShowcaseUITests` exercises the poster, in-place plan, reduced timeline,
table/code reply, problem-on/default-off summaries, reply detail link, digest
sheet, thinking panel, live-strip scroll action, approval controls and the
Settings toggle, structured question selection, credential guard, routine
report expansion, diff disclosure, teammate report and command HUD. Its
named attachments provide repeatable native review
frames. Debug-only `-chat-showcase-target <row-id>` (plus
`-chat-showcase-bottom`) selects a scroll position for these scenes without
changing production navigation.

Work summaries use `companion.prefs.showWorkSummaries` (off by default).
Turning them on reveals slim receipts even when Activity is Hidden. Failures
always reveal nonempty receipts. The last bot reply of a turn also exposes
“What I did” through its tap-revealed time line, context menu and expanded
“Worked for …” narration. Compaction, failed turns, notices, thread links,
teammate reports and approval recovery retain their existing actions.

Capture both appearances with a 9:41 status bar:

```sh
xcrun simctl status_bar "$SIMULATOR_ID" override --time 9:41
xcrun simctl ui "$SIMULATOR_ID" appearance light
# Run ChatShowcaseUITests, export its attachments, then repeat with dark.
xcrun simctl ui "$SIMULATOR_ID" appearance dark
xcrun simctl io "$SIMULATOR_ID" screenshot /absolute/path/to/frame.png
```

Review the poster, plan, collapsed/expanded steps, code/table, both digest
variants, sheet, thinking row/panel, live strip, approval and summaries setting
in light/dark. Semantic type, VoiceOver labels, logical alignment and static
Reduce Motion fallbacks are part of the component contract; physical-device
haptic timing and real pairing are not proved by synthetic simulator scenes.

## Live turn and streaming motion

`-store-preview -chat-showcase-preview -open-first -chat-stream-preview`
plays one whole live turn into the showcase chat through the real store folds
(`Session.startStreamPreview`): a send, typing, reasoning deltas, a narration
stream, a TodoWrite plan ticking off, two steps, a Markdown reply streamed in
uneven batches (2–22 characters at 35–140 ms, like network batches), the
settled reply and its digest. Leave out `-chat-showcase-target` so the chat
follows the end the way a reader at the bottom sees it.

What it proves, by Activity level:

- **Full / Reduced** — the reply reveals at an even pace (`StreamPacer`, a
  display link; `RevealPacing` absorbs a burst on a 0.3 s time constant), its
  newest characters ink in, the caret breathes, half-typed bold/code render
  styled (`MarkdownPartial.closingOpenSpans`) and tables arrive a whole row
  at a time (`MarkdownPartial.revealedPrefix`). When the reply settles, its
  bubble takes over the stream's pacer: no flush, no second arrival.
- **Hidden** — the status line reads plain words (`MarkdownPlain.line`), then
  the settled reply types itself into the chat in under a second
  (`ReplyArrival.revealed`) while the chat keeps its end in view.
- Following: a reader who drags away is never pulled back by the stream; one
  whose scroll comes to rest at the end follows again (iOS 18+ scroll
  phases). Reduce Motion and VoiceOver show replies whole.

Record it with `xcrun simctl io "$SIMULATOR_ID" recordVideo --codec=h264 out.mp4`
around the launch (stop with Ctrl-C after ~28 s) at `-companion.prefs.activityDetail
full` and `hidden`; review frames at the reply's start, mid-table and settle.

## Redesign verification — 2026-10-06

- Integrated CompanionCore suite: **928 tests passed, 0 failures**, including
  direct decoding and hydration of `App/ChatShowcasePreview.json`.
- Simulator application build: **BUILD SUCCEEDED**, with
  `-packageAuthorizationProvider netrc`, DerivedData `/tmp/omb-dd-ioschat`,
  and iPhone 18 Pro simulator `361A305B-81FB-41B9-A279-5F13233366A3`.
- The first core run exposed an incorrect new malformed-preview assertion:
  `true` is invalid preview text but valid `turnSucceeded`. The corrected
  assertion checks both independently; no production failure was suppressed.
- The first simulator run passed 30 of 32 cases. The table regression exposed
  glyph-sized TextKit bounds despite fixed-width columns. Table text now
  returns the column's proposed width; ordinary prose keeps intrinsic sizing.
  An initial unpaired launch and two later single-method launches remain in
  failed result bundles. The subsequent full showcase class passed all eight
  cases without a startup retry or a production fallback.
- The remaining Settings test failure was diagnosed from its native recording:
  a pending approval correctly opens Home's attention island, whose backdrop
  consumes a tap to dismiss it. The test now explicitly dismisses that modal
  before tapping Settings, rather than retrying a swallowed navigation tap.
  Decorative header-overlay hygiene is isolated in prerequisite commit
  `a49c5697` and is also present in the home redesign.
  Explicit dismissal also exposed the closing overlay intercepting toolbar
  taps after its controls disappeared; prerequisite `e76cbec5` gates hit
  testing on its visible state. Settings navigation then reached the toggle.
- The Settings case uses `-reset-work-summaries` and no launch-argument
  override for that preference: an argument-domain `NO` would pin the value
  and prevent a legitimate UI toggle from changing it. The case proves the
  actual off default and persisted switch, not a pinned preview value.
- Native screenshot review caught inline reasoning expanding below the
  viewport. Explicitly opening reasoning resumes following; later growth
  cannot pull away a reader who has since scrolled. The UI test requires an onscreen panel,
  not merely an accessibility element existing outside the viewport.
- The complete dark regression selection ran **40 cases: 39 passed**, with
  its one failure being the initial showcase process opening unpaired before
  `message-input` existed. All 12 thread-navigation, 9 transcript, 6 roster,
  and composer-return, mention, voice, image, and stop cases passed.
- After the viewport correction, the full dark showcase class passed
  **8 cases, 0 failures**, including the onscreen reasoning-panel assertion,
  mutable Work summaries switch, and both diff states. Result bundle:
  `/tmp/omb-ioschat-dark-verified.xcresult`.
- Final light showcase: **8 cases, 0 failures**, with the same viewport and
  disclosure assertions. Result bundle:
  `/tmp/omb-ioschat-light-captured.xcresult`.
- Native 9:41 screenshots are retained in this worktree at
  `.impeccable/review/ios-chat/light/` and `dark/`; each appearance covers 43
  frames across the poster, transcript/cards, composer, and preserved actions.
  `screenshots.json` in that evidence directory lists all 86 absolute paths.
  Both appearances were visually reviewed, including the corrected inline
  reasoning and settled collapsed diff captures.
- Not exercised here: physical haptics, VoiceOver, large-type/RTL/Reduce
  Motion device runs, real pairing, or a real credential submission. Native
  fixture presentation and preserved synthetic interaction suites are the
  scope of this evidence; no live user data or provider was used.
- Owner review batch: webhook tasks are now one blue user bubble with white
  content/disclosure and a subtle payload inset, not a nested bot card.
  Counted labels use catalog plural forms; the sheet explicitly asserts
  `1 memory change` and rejects `1 memory changes`. Badge fill is 20%/26%,
  symbols are 12pt semibold, and the full-opacity timeline connector spans
  from the 22pt badge's bottom to the next badge's top.
- Clean baseline comparison: detached `c2fc677f` and the revised app both
  passed `TranscriptPresentationUITests/testFullKeepsLiveReasoningAvailable`
  after uninstalling the synthetic app and regenerating their projects.
  Bundles: `/tmp/omb-ioschat-base-c2fc677f.xcresult` and
  `/tmp/omb-ioschat-current-common-startup.xcresult`. The temporary baseline
  worktree was removed. No unpaired launch reproduced in this common case;
  the new showcase suite/fixture did not exist on the baseline, so these
  observations do not establish the intermittent failure's origin.
- Review-batch verification: **929 core tests passed, 0 failures**, including
  singular copied-summary counts; **17 native UI cases passed in light and
  17 in dark**, including the singular sheet label and webhook disclosure.
  Bundles: `/tmp/omb-ioschat-review2-light.xcresult` and
  `/tmp/omb-ioschat-review2-dark.xcresult`. Projects were freshly regenerated
  with `xcodegen generate`; the revised application built successfully.
- The omitted `App/StepBadge.swift` source is included in the review fix
  commit. Updated native frames replace the affected light/dark paths in
  `screenshots.json`; webhook, digest and timeline states were visually
  inspected in both appearances.

The existing `scripts/verify-ios-thread-navigation-ci.sh` also runs this suite
on disposable iPhone and iPad simulators. Keep the result bundles, then shut
down and delete only the simulator IDs created for verification.

These checks prove native presentation with synthetic data. They do not
exercise a physical iPhone, pairing, or actual webhook delivery.

## Local evidence — 2026-09-18

- Before the fix, the new core regressions failed on unfolded narration and
  raw webhook previews. After the fix, all 465 core tests passed.
- All three UI cases passed on a disposable iPhone 17 Pro / iOS 26.5 simulator,
  including expansion and collapse, Hidden reasoning, and Full reasoning.
- Result bundle: `/tmp/omb-chat-presentation-ui.xcresult`. Exported screenshots
  in `/tmp/omb-chat-presentation-screenshots/` were visually inspected.
- The disposable simulator was shut down and deleted. No user data was used.

## Review regressions — 2026-09-18

- Task markers inside untrusted webhook data cannot override the configured or
  default task. Missing, blank and malformed trusted prefixes keep the raw
  message. The regression failed before the change in Swift, Kotlin and the
  desktop parser; all three pass after the fix.
- All 466 Swift core tests and all three simulator presentation tests passed.
  Expanded narration now tails only its last bubble. The expansion case was
  rerun to capture the screenshot below.
- Evidence: `/tmp/moca204-review-ui.xcresult`,
  `/tmp/moca204-expanded-ui.xcresult`, and
  `/tmp/moca204-expanded-screenshots/`. The disposable simulator was deleted.
- The desktop renderer suite passed all 1,792 tests, including its parser
  regression. This is not a claim that the entire server/desktop suite passed.

![Expanded narration](assets/ios-transcript/narration-expanded.png)

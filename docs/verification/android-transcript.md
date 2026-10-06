# Android transcript presentation

With JDK 17 and the Android SDK configured, run from `android/`:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

For just the transcript regressions:

```sh
./gradlew :core:test \
  :app:testDebugUnitTest --tests '*TranscriptPresentationTest*' \
  --tests '*ChatShowcaseScreenshotTest*' --tests '*TableReadingOrderTest*' \
  --tests '*LiveTailTest*' --tests '*ConversationDesignTest*'
```

The core suite verifies completed-turn folds at every activity level, wire
serialization, terminal patches, legacy/unfinished replies, interleaved turns,
elapsed time, and webhook parsing without changing the stored model prompt.

The core conversation suite also covers tolerant optional tool/digest decoding,
todo snapshots and incremental Claude Tasks, stable per-turn plans at every
activity detail, independent digest visibility/problem detection, digest
presentation, and ordered tool categories. Markdown tables and task lists share
one parser with whole-message table cards. `MarkdownTableTest` ports the iOS
contract: optional outer pipes, alignments, escaped/code-span pipes, welded
single-line tables, ragged padding and widening, list-context exclusions, and
every streaming prefix. CSV quoting remains unchanged.

The app suite mounts the actual ChatScreen and Session in Robolectric using a
synthetic fleet and an isolated loopback server. It checks:

- Completed narration starts collapsed and can be expanded and collapsed.
- The final answer and webhook task remain visible while transport metadata
  stays out of the conversation. Event payloads open only on request.
- Webhook Copy selects the task; text-only Edit and retry is unavailable.
- Switching Activity to Hidden hides live reasoning but retains the typing
  indicator and live narration. Full/Reduced show a reversible thinking row and
  flowing retained-reasoning panel; answer tokens win over the reasoning preview.
- A search hit reveals its intermediate reply inside the completed-turn fold.
- Pasted-text wrappers disappear while their content stays visible. The Claude
  update card displays a loopback server refusal, then its successful retry's
  version and resend guidance; it never runs a real provider updater.
- Work summaries start off, persist separately from activity detail, and show
  automatically when a step failed. Replies reveal a time/meta line on tap,
  including a direct "What I did" action; the same action is available in the
  context menu and expanded completed-turn fold. The digest is a native bottom
  sheet with files, tools, memory, available usage/cost, Copy and Done.
- Plans stay at their first activity's position while later snapshots update the
  card. The live strip above the composer scrolls to its plan card and preserves
  `live-status-line` on narration. Completed cards and active items use the
  contact's contrast-safe ink.
- `TableReadingOrderTest` keeps row-major TalkBack semantics, column measurements,
  horizontal scrolling and CSV copy. Code blocks add Copy/Copied confirmation.
- `ConversationDesignTest` checks contact/action contrast across all eight skins
  and all mascot colours, and ensures only appended rows qualify for arrival
  motion (not opening a transcript or paging older messages).
- A real transcript drag freezes follow-to-latest; incoming reasoning leaves the
  viewport fixed and adds the contact's small face to the 48dp jump button. The
  button restores follow, as does an own send. Search and live-plan navigation
  respect the reader's position.
- `MarkdownConversationTest` verifies that linked inline-code capsules still open
  their target and completed task code retains monospacing, bold, reduced type
  size, and strike-through.

## Contact-poster showcase

`ChatShowcaseScreenshotTest` uses native Robolectric graphics, the real
ChatScreen and Session, an isolated synthetic fleet, and a loopback server. It
includes a user ask, narration, three TodoWrite snapshots, shell/read/edit/web
steps (including a failure and command summaries), a rich Markdown reply with
tasks/code/table, structured problematic and successful digests, a webhook task,
an approval, a structured question, a routine, compaction, a Claude update card,
synthetic screenshots/images/documents/voice notes, live reasoning, and a busy
active plan. Both light and dark captures cover poster, message details, plan,
steps and their reduced timeline, Markdown, table, thinking panel, mention picker,
the additional card/media families, busy approval/live strip, hidden-detail
narration and digest sheet. Clipboard export and summary-off meta access are
exercised through the production sheet. Animations use `MotionDurationScale = 0`;
this also exercises the static fallback. `ConversationComposerScreenshotTest`
captures pending attachments, held sends, and authored quick-reply glyphs in
light/dark plus a 1.3× font-scale RTL layout. The complete conversation showcase
also runs at 1.3× in RTL, including a frozen incoming-stream jump capture.
`ConversationSettingsScreenshotTest` captures Work summaries beside Activity
detail in both themes and proves that the default-off switch changes state.

Files are written to `android/app/build/outputs/transcript-screenshots/` with
`showcase-<component>-light.png` / `showcase-<component>-dark.png` /
`showcase-<component>-large-type-rtl.png` names. The review copies live in
`.impeccable/review/android-chat/` in the verification worktree.
These fixtures never pair with a real computer or read `~/.openmausbot`.

The native graphics fixture writes screenshots under
`android/app/build/outputs/transcript-screenshots/`. The checked-in
[before](assets/android-transcript/before.png),
[collapsed](assets/android-transcript/collapsed.png), and
[expanded payload](assets/android-transcript/payload-expanded.png) images
show the same synthetic conversation.

The preview APK is debug-signed with its own application ID and is written to
`android/app/build/outputs/apk/preview/app-preview.apk`. No real computer is
paired during these tests. Robolectric coverage and a successful APK build do
not establish physical-device pairing, live webhook delivery, or HTTPS behavior.

## Local evidence — 2026-09-18

- New core and Compose regressions reproduced unfolded narration, raw webhook
  metadata, and live reasoning remaining visible after selecting Hidden.
- After the fixes, all 548 core tests and 919 app tests passed, including a
  search hit in the 12th intermediate reply of a turn spanning several screens.
- The preview APK assembled successfully; its signature and separate preview
  application ID were verified with the Android SDK tools.
- Before/after screenshots from the native graphics fixture were visually
  inspected. All conversation state and HTTP responses were synthetic.

## Contact-poster verification evidence

- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` passes:
  841 core tests and 1,163 app tests, with no failures or skips; the debug APK
  assembles successfully.
- Native light/dark captures cover every redesigned conversation component,
  including diffs and webhook tasks. The complete conversation and composer
  also have 1.3× font-scale RTL captures. Work summaries has light/dark setting
  captures using the real root's selected-skin and Surface wrapper.
- Digest captures include the native dialog window, visible usage/cost pills,
  and a fixed Copy/Done toolbar; clipboard contents are asserted.
- Reader-position regression: a real drag freezes the transcript, an incoming
  reasoning delta adds the small mascot to Jump to latest without moving the
  viewport, and the button restores follow-to-latest.
- All data, media, transport responses, and preferences are isolated synthetic
  fixtures. These checks do not establish physical-device pairing, real-engine
  delivery, or live audio playback.

## Review-round separation and framing

- Conversation surfaces derive from the actual transcript `background` role,
  not the skin's card role. A minimum 1.20:1 light / 1.25:1 dark surface contrast
  is enforced before choosing 4.5:1 contact ink and filled-action labels; the
  existing eight-skin/mascot-colour matrix covers both rules.
- The poster wash is confined to the top 260dp. A neutral backdrop remains
  opaque through the measured header controls, then fades over the following
  24dp; the wash is layered above this scrim and below the controls.
- Native review captures use xxhdpi (1233 × 2673 for the 411 × 891dp viewport),
  retain 1.3× RTL variants, and chain fixture parent IDs so ordinary user sends
  do not falsely expose sibling-branch pagers.
- Tables show only their data, subtle header fill, continuous row hairlines,
  and a 48dp Copy table as CSV icon/custom action; there is no table kicker or
  row-count chip.
- Explicit review-state stems are `poster`, `plan`, `live-plan`, `step-run`,
  `step-timeline`, `markdown`, `table`, `thinking-collapsed`, `thinking`,
  `digest-line`, `digest-line-success`, `digest`, `message-details`, `approval`,
  and `settings`; each uses the existing `showcase-<stem>-<variant>.png` naming.
  Modal captures assert the intended density, font scale and RTL direction.
- The review batch's full core/app suites and debug APK pass. All 93 native
  review PNGs are regenerated at 1233 × 2673, including actual 1.3× font-scale
  dialog captures. Representative light/dark/RTL captures were inspected for
  surface edges, header occlusion, table copy/separators, and retained reasoning.
- Table headings retain the Markdown's written case and semibold weight. The
  header fill covers the complete table viewport; Copy overlays that row, with
  its target space inside the final cell column rather than a separate rail.
  For a table-only evidence refresh, set `COMPANION_CAPTURE_COMPONENT=table`
  while running the showcase tests; their behaviour assertions still all run.
  The final table-only run passed all 20 tests across `TableReadingOrderTest`,
  `ParagraphDirectionTest`, and `ChatShowcaseScreenshotTest`, plus debug APK
  assembly; only the light, dark, and large-type RTL table PNGs were refreshed.

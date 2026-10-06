---
name: OpenMausBot Phone Companions
description: A native messenger whose mascot, soft contact tint, and live plan make each bot feel personal.
colors:
  mine: "#2E6FDB"
  mascot-green: "#009957"
  mascot-blue: "#377FE6"
  mascot-red: "#D94B52"
  mascot-orange: "#E78531"
  mascot-purple: "#8057C8"
  mascot-cyan: "#0EA5C6"
  mascot-pink: "#D84F8B"
  mascot-yellow: "#D8A729"
  mascot-teal: "#01A492"
  mascot-coral: "#E5634E"
  mascot-fallback: "#8E8E93"
  incoming-neutral-ios-light: "#F0F0F3"
  incoming-neutral-ios-dark: "#1F1F22"
  black: "#000000"
  white: "#FFFFFF"
  inset-ios-light: "rgba(0, 0, 0, 0.06)"
  inset-ios-dark: "rgba(255, 255, 255, 0.07)"
  warning-light: "#B35400"
  warning-dark-android: "#FF9F0A"
typography:
  title:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 700
  headline:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 600
  body:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 400
  label:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 400
  label-emphasis:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 600
  label-medium:
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif"
    fontWeight: 500
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, monospace"
    fontWeight: 400
  composer-android:
    fontFamily: "Roboto, sans-serif"
    fontSize: "17px"
rounded:
  inline-code: "5px"
  inset-block: "12px"
  conversation-surface: "20px"
  composer-ios: "22px"
  composer-android: "24px"
  floating-sheet: "28px"
  circle: "50%"
spacing:
  run-join: "2px"
  heading-after: "4px"
  group: "8px"
  bubble-block: "9px"
  run-break: "10px"
  transcript: "12px"
  bubble-inline: "14px"
  sheet: "20px"
  sheet-section-ios: "24px"
components:
  bubble-user:
    backgroundColor: "{colors.mine}"
    textColor: "{colors.white}"
    typography: "{typography.body}"
    rounded: "{rounded.conversation-surface}"
    padding: "9px 14px"
  button-send:
    backgroundColor: "{colors.mine}"
    textColor: "{colors.white}"
    rounded: "{rounded.circle}"
    size: "32px"
  button-approval-primary:
    typography: "{typography.label-emphasis}"
  button-approval-secondary:
    typography: "{typography.label-emphasis}"
  composer-ios:
    typography: "{typography.body}"
    rounded: "{rounded.composer-ios}"
  composer-android:
    typography: "{typography.composer-android}"
    rounded: "{rounded.composer-android}"
  conversation-navigation:
    typography: "{typography.label-emphasis}"
  plan-card:
    rounded: "{rounded.conversation-surface}"
    padding: "14px"
  markdown-inset:
    rounded: "{rounded.inset-block}"
  step-badge:
    rounded: "{rounded.circle}"
    size: "22px"
  work-stat-pill:
    typography: "{typography.label-medium}"
    padding: "7px 10px"
  digest-disclosure:
    typography: "{typography.label}"
---

# Design System: OpenMausBot Phone Companions

## Overview

**Creative North Star: "The Bot's Contact Poster"**

**Scope:** The built conversation visual system and surrounding roster/Updates experience of the native iOS SwiftUI and Android Jetpack Compose phone companions. The desktop/web app (`src/`) is out of scope; this is not a specification for every other companion screen.

A conversation belongs to the contact, not to an agent console. The mascot and its colour establish recognition; familiar speech bubbles and native system type carry the words. Deep Maus blue distinguishes the person's messages. The bot replies on a soft contact-coloured surface, with a contrast-resolved ink for links, checks, and progress. The result is personal, cozy, and familiar without dressing the transcript as a tool log.

Density is intentionally uneven: messages are closely grouped, separate turns have air, and work details live in quieter rows or deliberate disclosures. The live plan is the signature object: it stays in the reading lane while its progress changes. Depth belongs to floating chrome, not to every piece of conversation. iOS keeps system glass; Android uses opaque elevated Material surfaces rather than imitating that glass.

**Key Characteristics:**
- Contact identity through the existing mascot and per-bot colour, including the actual speaker in rooms.
- Solid, rounded speech bubbles with one soft tail at the end of a run.
- Semantic native type and surfaces, with distinct iOS and Android material implementations.
- A shared conversation-card family, restrained work receipts, and progress with readable numbers.
- State-driven motion with reduced-motion alternatives, rather than entrances on loaded history.

**Evidence:** Extracted from `ios/App/BotTint.swift`, `SpeechBubble.swift`, `MessageArrival.swift`, `StepReceipt.swift`, `StepBadge.swift`, `MarkdownText.swift`, `ChatView.swift`, `CompanionLayout.swift`, `Glass.swift`, `RosterFace.swift`, `ChatListView.swift`, and the sampled plan, thinking, and digest cards; Android sources are `ConversationTint.kt`, `SpeechBubble.kt`, `ConversationMotion.kt`, `StepViews.kt`, `PlanViews.kt`, `ThinkingView.kt`, `DigestViews.kt`, `ChatPoster.kt`, `MarkdownText.kt`, `TranscriptCardViews.kt`, `CompanionTheme.kt`, `AppearanceSkin.kt`, `MausPalette.kt`, `Chrome.kt`, `ChatScreen.kt`, `MessageRow.kt`, and `RosterFace.kt` under `android/app/src/main/kotlin/com/openmausbot/companion/ui/`. The surface brief names the world; implementation, not the provisional build spec, supplies the values below.

**Token interpretation:** The frontmatter is a portable CSS-shaped export of native tokens. Its `px` lengths represent logical iOS points or Android dp; the Android composer size represents sp, not device pixels. Native semantic text roles remain authoritative at the user's text size. Omitted dynamic colours and font sizes are deliberate: a single swatch cannot replace a trait-, skin-, or contact-dependent computation. Sidecar HTML/CSS is an illustrative browser translation of the sampled native states, not a replacement control library; synthesized tonal ramps are panel aids, not additional app colours.

## Colors

The palette puts saturated identity inside quiet, opaque conversation surfaces; readable contact ink, rather than raw mascot colour, carries small interactive detail.

### Primary
- **Deep Maus Blue** (`mine`): the person's bubble, enabled send disc, composer caret, and roster unread dot. This is distinct from the lighter mascot blue.
- **Contact Blue** (`mascot-blue`): one of the bot identities, and the room's chrome identity. A room's individual messages still resolve from their speaker.

### Secondary
- **Mascot identities** (`mascot-green`, `mascot-red`, `mascot-orange`, `mascot-purple`, `mascot-cyan`, `mascot-pink`, `mascot-yellow`, `mascot-teal`, `mascot-coral`): established identity assets, not interchangeable status colours. Unknown names use `mascot-fallback`.
- **Problem Orange** (`warning-light`, `warning-dark-android`): the digest problem glyph and leading failure text. iOS uses its deeper light-mode warning token and semantic system orange in dark mode. Failure glyphs/cards use iOS semantic red or the active Android skin's Material error role; success, changed-file, and deletion colours retain their native semantic assignments.

### Neutral
- **Incoming iOS grounds** (`incoming-neutral-ios-light`, `incoming-neutral-ios-dark`): bases mixed with the mascot to make an incoming surface. They are not the page background.
- **Mixing and label endpoints** (`black`, `white`): contrast targets and the foreground on filled actions/checks, not a universal replacement for semantic text.
- **Inset overlays** (`inset-ios-light`, `inset-ios-dark`): code, table containers, and progress tracks over the existing bubble. These overlays do not create a separate palette.
- **Platform surfaces:** iOS uses system background, primary/secondary/tertiary labels, and separator. Android resolves background, surface, on-surface, on-surface-variant, outline, and error from `AppearanceSkin` through `CompanionTheme`. Preserve all eight skins: Midnight (default), Atelier, Foundry, Lagoon, Graphite, Linen, Dusk, and Daylight. `surfaceContainerHigh` remains a Material role for chrome; it is not a hard-coded conversation neutral.

### Named Rules

**The Contact Owns the Ink Rule.** Resolve the bubble and its ink from the same speaker colour. Raw mascot colour identifies a contact; contrast-resolved ink carries links, step glyphs, checks, and progress.

**The Native Neutral Rule.** Preserve the platform's actual neutral and contrast policy instead of forcing identical light/dark swatches across iOS and Android.

The implemented colour computation is linear mixing in sRGB:
- **iOS incoming surface:** mix the light incoming neutral with the mascot at (11%), or the dark neutral at (17%). **Ink:** mix toward black starting at (20%) in light, toward white starting at (25%) in dark; advance in (5%) steps until contrast against the incoming surface is at least (4.5:1), with a (70%) cap. Glyph fill is that ink at (20% light / 26% dark). Poster wash is the mascot at (14% light / 22% dark). Insets use the frontmatter's overlays.
- **Android incoming surface:** start from the active skin's **background**, not an incoming-neutral constant, and the same initial (11% / 17%) mascot mix. Increase the mix by (2.5%) until separation from that background reaches (1.20:1 light / 1.25:1 dark), or the mix reaches (100%). The light separation target mixes the mascot (5%) toward black. **Ink:** choose the black/white endpoint with greater contrast on the resolved bubble, start at (20% light / 25% dark), then advance in (5%) steps up to (100%) until at least (4.5:1). Glyph fill is ink at (14% light / 20% dark). Wash uses the same opacity pair as iOS; inset uses the selected endpoint at (6% light / 7% dark).
- **Filled action labels:** iOS `actionLabel` and Android `actionText` select white only when it reaches (4.5:1) against ink, otherwise black. Keep the contrast-selected check foreground too: iOS plan/Markdown checks use `actionLabel`; Android plan checks choose white/black at the non-text glyph threshold (3:1). Do not restore unconditional white checks in dark mode.

## Typography

**Body and UI Font:** San Francisco through native system text styles on iOS; the default Material 3 type scale/Roboto on Android. No separate decorative display face is introduced. **Code Font:** native system monospace / `FontFamily.Monospace` for code and shell commands, not for ordinary narration.

**Character:** Familiar messenger reading rather than technical-console typography. Weight distinguishes the current task and contact; small secondary type keeps timing and receipts subordinate.

### Hierarchy

| Role | iOS | Android | Use |
| --- | --- | --- | --- |
| Contact title | `.title2.bold()` | `headlineSmall`, bold | Poster name; not an ornamental display heading |
| Markdown headline | h1 `.title3.semibold`, h2 `.headline.semibold`, h3+ `.subheadline.semibold` | `titleMedium`, `titleSmall`, `labelLarge`, semibold | Hierarchy inside a reply |
| Conversation body | `.body` | `bodyLarge` | Message text; user text is literal, bot text renders Markdown |
| Plan item | `.callout`, active semibold | `bodyLarge`, active semibold | Current task versus completed/pending tasks |
| Step label / supporting line | `.subheadline` | `bodyMedium` | Step names and compact live work |
| Receipt / code body | `.footnote`; monospace for code/commands | `bodySmall`; monospace for code/commands | Digest lines, command summaries, reasoning previews |
| Meta | `.caption` / `.caption2` | `labelSmall` / `labelMedium` | Time, counts, speaker name, secondary detail |
| Composer | `.body` | existing scalable (17sp) field | A growing one-to-five-line draft |

Native roles scale with Dynamic Type/font scale; the omitted frontmatter sizes are not permission to freeze them. Markdown paragraphs separate by (8pt/dp); headings use (12pt/dp) before and (4pt/dp) after. Table headers are semibold `.subheadline` / `titleSmall`; body cells use `.subheadline` / `bodyMedium`. Source-selected numeral features keep progress, duration, counts, and table cells stable.

### Named Rules

**The Numbers Stay Still Rule.** Use monospaced digits/tabular-number features for progress and measured data, while keeping prose in the system proportional face.

## Layout

A single transcript lane fills the available space above a bottom-docked composer. Header furniture respects native insets; keyboard growth does not turn the composer into a floating mid-screen object. The transcript uses side insets (12pt/dp), bubble padding (14pt/dp horizontal, 9pt/dp vertical), close joins within a run (2pt/dp), speaker/run breaks (10pt/dp), and separation around non-text rows (8pt/dp).

Incoming ordinary bubbles leave a trailing gutter (20%); user bubbles leave a leading gutter (22%). iOS plan cards leave an (8%) trailing gutter. Android table/diff bubbles and plan/approval cards can occupy (92%) of their lane; ordinary Android text remains at (80%), with a bubble cap (640dp). iOS text bubbles retain the ordinary (80%) rule even when they contain a table: horizontal scrolling, not an invented wider iOS table lane, solves overflow. iOS caps the chat/composer column (760pt), roster column (680pt), and header column (900pt) in wider windows; compact widths naturally remain below those caps. These are width constraints, not CSS breakpoints.

Card padding is generally (14pt/dp) for plans and iOS conversation cards; sampled Android approval cards use (16dp). Digest sheets use side padding (20pt/dp), with section spacing (24pt) on iOS and (20dp) on Android. Roster compact/comfortable densities remain a user choice: sampled iOS attention faces start at (32/40pt), comfortable bot faces at (44pt), and accessibility sizes can grow within the existing caps rather than truncating every row to one line.

Layout follows leading/trailing, including bubble tails and table overflow fades. Android paragraph ordering uses content-directed text with an LTR fallback for neutral-only data; this does not stop the surrounding chrome from mirroring with the locale. Tables grow with their contents/text size and scroll horizontally, rather than reducing text to fit.

## Elevation & Depth

Transcript content is flat: solid bubble fills, tonal inset layers, and small separators establish depth without per-message shadows. The poster wash fades vertically over the header area (260pt/dp). Table overflow edges and thinking preview masks are functional fades, not a general gradient or blur treatment.

Floating chrome intentionally differs by platform. iOS `GlassSurface` uses interactive regular Liquid Glass on iOS 26; earlier systems use ultra-thin material with a semantic-primary hairline (0.5pt at 10% opacity). Android `Chrome` is opaque `surfaceContainerHigh`, elevated (3dp) for capsules/buttons and (6dp) for rounded sheets/composer. Native sheets keep their own platform depth.

### Shadow Vocabulary
- **iOS jump-to-latest:** black (12% opacity), blur radius (8pt), vertical offset (2pt), under its regular-material capsule.
- **Android chrome capsule:** native shape-aware elevation (3dp), not a portable CSS box-shadow constant.
- **Android chrome sheet:** native shape-aware elevation (6dp), not a translucent glass imitation.

### Named Rules

**The Float the Furniture Rule.** Reserve glass or elevation for floating navigation, composer, and transient controls; conversation bubbles and plan cards remain solid and flat.

## Shapes

Speech bubbles and the shared conversation-card family use the gently rounded surface token. The tail is the same authored four-cubic contour on both platforms, scaled from its reference cap radius, not a triangular pointer. Only the last text bubble of a same-speaker run receives it; leave its proportional tail drop below the shape. Room speaker changes, cards, and tool rows break a run. RTL mirrors the tail.

Inside that family, code/table/image containers use the smaller inset-block radius; inline code uses the inline-code radius. Step badges, plan markers, and send discs are circular. Small stat and action pills are fully rounded; multiline fields are not: the composer holds its iOS/Android corner radius as it grows rather than rounding to half of its new height. iOS untailed shapes use continuous corners; Android uses circular arcs because Compose has no identical continuous-corner primitive. Preserve this native difference rather than replacing the shared tail artwork.

## Components

### Buttons

Small native targets with a clear division between speaking, approving, and secondary actions.
- **Send:** deep-blue circular disc (32pt/dp) within the native touch target (44pt iOS / 48dp Android). Disabled state uses muted semantic foreground/fill instead of an enabled blue control. iOS uses the up-arrow; Android uses the auto-mirrored Material send icon.
- **Primary approval/action:** contact ink fill and contrast-selected label; fully rounded native action geometry. The sampled iOS approval options share available width and a minimum height (44pt); Android retains Material button layout.
- **Secondary/refusal:** tonal inset fill, with iOS primary text / Android contact ink. Refusal remains visually quieter without changing the option's meaning.
- **States:** answering/loading/transport conditions disable the existing actions; native pressed/focus treatment and haptics remain. Phone behaviour does not depend on hover. The sidecar supplies hover/focus-visible/active styles for its browser previews only.

### Chips

Small pieces of context, not containers around every tool event. Digest stat pills use contact ink over glyph fill, tabular numerals, and padding (10pt/dp horizontal, 7pt/dp vertical). Needs-you roster/Updates badges use a warning-tonal surface; unread remains a blue dot. Existing quick replies, mentions, and queued-send controls retain their native actions.

### Cards / Containers

Conversation objects share the incoming surface and rounded-card family: plans, approvals, questions, credentials, routines, attachments, and screenshots. Use contact ink for primary actions and inset tone for secondary actions. Failed-turn cards use semantic error at (10%) opacity with a warning glyph and readable primary text, not a thick coloured border. Do not force the card padding or secondary foreground of one platform onto the other.

### Inputs / Fields

The composer is a native growing field, not a new form aesthetic. Its main corners stay fixed while the draft grows to five lines; the caret is deep Maus blue. Floating chrome contains the attachment/command/voice/send controls and the live plan/status furniture. Stop remains accessible while work is running. iOS and Android keep their established native keyboard, focus, and disabled/read-only behaviour, including Return as a newline on the software keyboard.

### Navigation

A floating back/unread control, contact face/name/thread access, and available call/computer controls sit over the faint contact wash. The larger poster at a thread's beginning uses the existing mascot (88pt/dp), contact name, role, and available model in decreasing native type roles. It is a real route to profile/thread options, not an illustration to invent. Roster and Updates reuse identity-plus-corner-status: the working arc surrounds the face, waiting uses a hand badge, queued uses a clock, and unread uses blue. Native Back, menus, sheets, and roster disclosure actions remain the interaction vocabulary.

### Live Plan

A flat incoming-colour card with padding (14pt/dp), header/count, rounded progress track (4pt/dp), and item spacing (10pt/dp). Item circles (20pt/dp) distinguish pending, active, completed, and cancelled work; completion adds a contrast-selected drawn check and muted strikethrough, not colour alone. Active text is semibold. More than six items uses the existing six-item window with a reversible show-all control. A compact strip above the composer has a progress ring (18pt/dp), tabular count, current item, and a direct jump to the card; Android gives the strip a tinted rounded surface, while iOS keeps it as plain composer furniture.

Plan progress uses the conversation spring: iOS response (0.38s), damping fraction (0.82); Android damping ratio (0.82), stiffness (380). The drawn check takes (280ms); the active quarter-arc takes (1s) per revolution. Reduced motion uses the existing iOS short easing/opacity treatment or Android instant state change; active arcs become static. Completion haptics occur on changes, not as a replay of already-completed history.

### Steps, Thinking, and Receipts

- **Steps:** a circular badge (22pt/dp), two-line native label, optional one-line middle-truncated summary, and spinner/error state. Command summaries alone are monospaced. Reduced runs show up to three overlapping badges, then disclose a thin connected trail. Android reveal fades use (30ms) stagger increments, capped at index seven; these are details, not a log terminal in the reading path.
- **Thinking:** a small ink dot (8pt/dp), quiet label, elapsed number, and a roughly two-line tail preview outside the reply bubble. The purposeful ink shimmer cycles (1.6s); reduced motion makes it static. Disclosure opens a retained-reasoning panel with the incoming fill, card radius, padding (14pt/dp), and maximum height (280pt/dp). When the answer begins, the row becomes a smaller “Thought for…” disclosure. Detail visibility remains a preference, not a colour distinction.
- **Digest:** an unboxed ink glyph, secondary receipt text, and chevron within a native minimum touch height. Only the failure portion uses the corrected problem orange. It opens a native digest sheet with mascot, heading/time, stat pills, and relevant file/tool/memory sections. iOS uses medium/large detents; Android uses a fully expanded Material bottom sheet. Work summaries are off by default, problem receipts are shown automatically, and the reply's tap-revealed meta line/context menu keeps its summary findable.
- **Other receipts:** compaction and notices remain slim, quiet native rows. Do not restore a capsule to every activity or turn summary.

### Markdown and Message Arrival

Markdown belongs inside the bot bubble: hanging list markers, contact-ink links/checks, selectable text, inset inline code, scrollable code blocks with copy feedback, and a neutral quote bar (2pt/dp). Tables share the inset-block frame, stronger tonal header, semibold header type, tabular digits, row-major accessible cells, and thin separators. Their trailing fade (24pt/dp) appears only while more content is off-screen and mirrors in RTL. iOS cells use padding (12pt horizontal / 9pt vertical); Android uses (6dp / 8dp), content-measured columns, and a reserved trailing copy-CSV target. Preserve these implemented policies rather than lifting the provisional table measurements.

Newly appended rows rise and settle: opacity from zero, vertical offset (10pt/dp incoming / 18pt/dp outgoing), scale (0.97 to 1), anchored at the tail corner, using the conversation spring. First load and prepended history do not receive this arrival. Streaming replies reuse settled bubble geometry so their handover does not jump. Reduce Motion on iOS removes translation/scale and uses opacity (200ms); Android remove-animations snaps arrivals to their final state. Roster working arcs use the slower cycle (2.4s), with static rings when animation is disabled.

## Do's and Don'ts

### Do:
- **Do** resolve each reply's surface and ink from its speaker, keeping room chrome distinct from room speakers.
- **Do** preserve the platform-specific tint computation, semantic text roles, all Android skins, and light/dark label selection.
- **Do** keep one authored tail at the end of a text run and retain its drop, mirrored layout, and shared card family.
- **Do** use tabular numerals for progress and measured data, and reserve monospace for actual code/commands.
- **Do** keep work disclosures tap-accessible and retain state labels/icons alongside colour.
- **Do** preserve native touch targets, text scaling, Back, selection, screen-reader labels, and reduced-motion alternatives.
- **Do** animate live state changes and appended messages without replaying loaded or paginated history.

### Don't:
- **Don't** use raw mascot orange for small problem text on a light transcript or assume white labels/checks work on every dark-mode ink fill.
- **Don't** flatten iOS glass and Android opaque elevated chrome into one faux-glass implementation.
- **Don't** give every bubble/card a shadow, a glow halo, a thick coloured side border, or terminal-style log chrome.
- **Don't** substitute generic avatars, emoji/glyph icons, or triangular pointers for the existing mascot, native icon system, and authored bubble tail.
- **Don't** turn a growing composer into a tall capsule or shrink table text to avoid horizontal scrolling.
- **Don't** promote illustrative browser previews, synthesized tonal ramps, or values from the provisional build spec into new native tokens.

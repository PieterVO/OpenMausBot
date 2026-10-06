# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

One person who runs OpenMausBot on their own computer and keeps a roster of AI bots there. On the phone they are away from that computer: on the couch, commuting, between meetings. They open the companion app to talk to their bots the way they talk to contacts in a messenger: catch up on what happened, reply, approve or deny what a bot wants to do, steer a running turn, and glance at work in progress.

## Product Purpose

OpenMausBot is an open-source, local-first chat app where every contact is a real agent (Claude, Codex, Grok and other engines running on the user's machine) with its own personality, model, computer and connected apps. The phone companions (iOS and Android) pair with that computer and carry the conversation away from the desk. Success on the phone: the person can stay in an ongoing, personal conversation with each bot, and is never surprised by what a bot did or is waiting on.

## Positioning

AI as a messaging app, not one assistant in one box: a roster of agents you chat with like friends, running on your own machine, on models and logins you already have, asking before they act. Open source (Apache 2.0), not affiliated with xAI, Meta, OpenAI or Manus.

## Operating Context

- The desktop app (Electron + React) and its local harness server own every agent process; transcripts, keys and events live in `~/.openmausbot`.
- The phone pairs with that server (QR, typed code, LAN or hosted route) and streams transcripts, activity, approvals and questions live.
- Bots work in turns: they narrate, call tools, write todo lists, think, produce digests ("What I did") and compaction receipts, ask questions, request approvals and secrets, run routines on schedules, and use cloud desktops, Local VMs, browsers and phones.
- Phone surfaces: roster/home (with Updates, routine calendar, density settings), chat transcript and composer (mentions, attachments, voice notes, queued and steering messages, Stop), threads, agent profile, computer/browser control, Live calls and walkie, tasks and routines, settings, widgets, Live Activities and the Dynamic Island, share extension.
- Native codebases: iOS in SwiftUI (`ios/`, shared logic in `ios/Sources/CompanionCore`), Android in Jetpack Compose (`android/app`, protocol in `android/core`, ported from CompanionCore).

## Capabilities and Constraints

- Every existing function must survive any redesign; behavior is pinned by unit and UI tests on both platforms.
- Activity detail is a user setting (full, reduced, hidden); hidden is the default and a working bot's narration shows as one status line.
- Verification uses isolated fixtures and disposable simulators/emulators, never the user's live app or data (`docs/verification/README.md`).
- Localized UI (iOS `Localizable.xcstrings`, Android `LocalizedCopy.kt`); right-to-left text must hold.

## Brand Commitments

- Name: OpenMausBot (also MausBot). Bots have generated mascot faces and per-bot colours; these are recognisable identity assets.
- Voice: plain, warm, specific. Bots are addressed as contacts.
- The phone should feel personal, cozy and familiar, like talking to a friend (owner's direction, October 2026).

## Evidence on Hand

- Screenshots: `docs/screenshots/`, `ios/AppStore/screenshots/`, `android/PlayStore/assets/`.
- Preview fixtures in the iOS app (`ios/App/*Preview*.json`) and Android previews.
- No customer testimonials, usage numbers or benchmarks are on hand; none may be invented.

## Product Principles

1. The conversation is the product: what the bot said and what it needs from you come first; machinery comes second and on request.
2. Never surprise the person: what a bot did, is doing or waits on is always one glance or one tap away.
3. Talk to bots like contacts: familiar messenger conventions over novel controls.
4. Nothing removed: new presentation, same capabilities.

## Accessibility & Inclusion

Dynamic Type / font scale, VoiceOver / TalkBack labels, sufficient contrast in light and dark, reduce-motion, and right-to-left layout must all hold on both platforms.

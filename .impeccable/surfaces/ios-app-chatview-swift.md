---
version: 1
slug: "ios-app-chatview-swift"
primary_target: "ios/App/ChatView.swift"
related_targets: ["android/app/src/main/kotlin/com/openmausbot/companion/ui/ChatScreen.kt","android/app/src/main/kotlin/com/openmausbot/companion/ui/MessageRow.kt","ios/App/ChatListView.swift","android/app/src/main/kotlin/com/openmausbot/companion/ui/RosterScreen.kt"]
---

# Phone conversation (iOS + Android companions)

Mode: Operate. The visitor is inside an ongoing chat with one bot (or a room of bots) on their phone, away from the computer: reading replies, answering approvals and questions, steering a running turn, glancing at progress.

Scope: the chat transcript and its components (bubbles, Markdown incl. tables, steps/tool activity, thinking, todo plans, digests, receipts, typing/live status, composer furniture), plus the home roster and Updates sheet as the surrounding experience. Native controls stay: HIG on iOS, Material 3 on Android.

Constraints: nothing removed; accessibility identifiers / test tags kept; Activity detail (full/reduced/hidden, hidden default) keeps its meaning; Dynamic Type / font scale, VoiceOver / TalkBack, Reduce Motion / remove animations, RTL, light and dark all hold.

Owner decisions (Oct 2026): mix of contact poster and classic messenger, with inventive UI/UX and smooth animation details. Digest off by default, slim line when switched on, shown automatically on problems, one tap away (never only behind long-press).

## Direction contract

THESIS: Each chat belongs to the bot you are talking to. Classic messenger grammar (bubbles, runs, tails, a familiar composer) carries the conversation; the bot's own colour and mascot quietly own its chat the way a contact poster owns a call. Refuses the agent-console default: no log lines, no grey machinery, no developer chrome in the reading path.

OWN-WORLD: System surfaces and system type. You speak in a deep Maus blue; each bot answers in a soft wash of its own colour, with an "ink" variant of that colour for its checkmarks, step glyphs, links and progress. One faint poster wash of the bot's colour behind the header. Rounded speech bubbles with one tail per run; cards share the bubble's lane, radius family and tint.

STORY: Open a chat and it is obviously that bot's: its face, its colour, its words first. Work happens in its margin: a plan you can watch tick off, a steps trail that opens when you care, thinking that whispers instead of shouting. What a turn did is one tap on the reply away.

FIRST VIEWPORT: Floating header (back, call, face, Threads, computer) over a poster wash; transcript of tinted bot bubbles and blue user bubbles; at the very start of a thread, a contact-poster intro (large mascot, name, role, model); composer docked at the bottom with the live plan strip / status line above it.

FORM: Native messenger, bot-tinted. Familiar on the first glance, specific on the second.

SIGNATURE: The live plan. While a bot works from a todo list, its plan card ticks off item by item (check strokes drawing in, progress springing in the bot's colour) and a one-line strip above the composer reads "2 of 5 · Writing the tests"; tap it to jump to the card. Supporting motion: messages rise and settle on arrival, the typing bubble grows into the reply, thinking shimmers in the bot's ink.

seed: 00e65a8d → re-rolled 8c749131 (assigned candidates declined on the pinned brief: "slick and clean", "familiar"); resolved by the owner as poster × classic messenger.

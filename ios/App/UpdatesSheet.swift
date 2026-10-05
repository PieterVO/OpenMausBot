// What the Updates pill opens: the active bots, grouped by what they need.
//
// Needs you first, with the answer right there — the phone exists so that
// a stopped bot on the laptop can be un-stopped from wherever you are.
// Then what is working, then what finished while you were not looking.
import SwiftUI
import CompanionCore

struct UpdatesSheet: View {
    let open: (Chat) -> Void
    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @AppStorage(PrefKey.activityDetail) private var activityDetail = ActivityDetail.phoneDefault.rawValue

    private var updates: [ChatUpdate] {
        session.state.updates(detail: ActivityDetail(rawValue: activityDetail) ?? .phoneDefault)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Updates")
                            .font(.title2.bold())
                        Text(updates.isEmpty ? "All quiet" : "\(updates.count) active")
                            .font(.caption)
                            .monospacedDigit()
                            .foregroundStyle(Color.secondary)
                    }
                    Spacer()
                    Button("Done") { dismiss() }
                        .font(.body.weight(.semibold))
                        .frame(minWidth: 44, minHeight: 44)
                }
                .padding(.horizontal, 20)
                .padding(.top, 22)
                .padding(.bottom, 6)

                if updates.isEmpty {
                    EmptyStateView(
                        "Nothing needs you",
                        systemImage: "checkmark.circle",
                        description: Text("When a bot stops for an answer, is mid-task, or finishes something, it shows up here.")
                    )
                    .padding(.top, 24)
                } else {
                    section("Needs you", kind: .needsYou)
                    section("Working", kind: .working)
                    section("To review", kind: .toReview)
                }
            }
            .padding(.bottom, 24)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .accessibilityIdentifier("updates-sheet")
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .sheetChromeCompat()
    }

    @ViewBuilder
    private func section(_ title: LocalizedStringKey, kind: ChatUpdate.Kind) -> some View {
        let items = updates.filter { $0.kind == kind }
        if !items.isEmpty {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.secondary)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 8)

            ForEach(items) { update in
                UpdateRow(update: update) { open(update.chat) }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
            }
        }
    }
}

private struct UpdateRow: View {
    let update: ChatUpdate
    let open: () -> Void
    @EnvironmentObject private var session: Session
    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .body) private var scaledFace: CGFloat = 40
    @State private var answering = false

    private var queued: Bool {
        session.state.pendingQueued[update.chat.threadId]?.isEmpty == false
    }

    private var waitingOnTeammate: Bool {
        if case let .bot(bot) = update.chat { return bot.waitingOnTeammate == true }
        return false
    }

    private var badge: RosterFaceBadge? {
        switch update.kind {
        case .needsYou: .waiting
        case .working: queued || waitingOnTeammate ? .queued : nil
        case .toReview: .unread
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: open) {
                HStack(alignment: .top, spacing: 12) {
                    RosterFace(
                        color: update.chat.color, size: min(scaledFace, 48),
                        working: update.kind == .working && !queued && !waitingOnTeammate, badge: badge
                    ) {
                        ChatAvatarView(
                            chat: update.chat, size: min(scaledFace, 48),
                            state: MausState.forChat(update.chat, in: session.state)
                        )
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(update.chat.name)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Color.primary)
                        Text(update.chat.threadTitle)
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                            .lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
                        Text(update.line.isEmpty ? " " : update.line)
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                            .lineLimit(update.kind == .needsYou || typeSize.isAccessibilitySize ? 3 : 2)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Image(systemName: "chevron.forward")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.secondary)
                        .padding(.top, 12)
                }
                .padding(14)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("update-\(update.chat.threadId)")
            .accessibilityValue(update.kind == .needsYou ? "Waiting on you" : queued ? "Queued" : waitingOnTeammate ? "Waiting on teammate" : update.kind == .working ? "Working" : "Unread")

            if update.kind == .needsYou, let card = update.card, card.isPending {
                if card.skillRequest != nil {
                    Button(action: open) {
                        Label("Open the chat to review SKILL.md", systemImage: "doc.text.magnifyingglass")
                            .font(.footnote.weight(.medium))
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.secondary)
                    .padding(.horizontal, 14)
                    .padding(.bottom, 10)
                } else {
                    // Answers are siblings of the open-chat button, so a tap
                    // can never both answer and navigate. Adaptive columns
                    // keep every choice readable at large text sizes.
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 180 : 110))], spacing: 8) {
                        ForEach(update.answerOptions, id: \.self) { option in
                            Button {
                                Haptics.selection()
                                answering = true
                                Task {
                                    await session.answer(chat: update.chat, card: card, choice: option)
                                    answering = false
                                }
                            } label: {
                                Text(option)
                                    .font(.subheadline.weight(.semibold))
                                    .multilineTextAlignment(.center)
                                    .foregroundStyle(CardStyle.isRefusal(option) ? Color.primary : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .background(
                                        Capsule().fill(
                                            CardStyle.isRefusal(option)
                                                ? Color.secondary.opacity(0.14)
                                                : RosterStyle.unread
                                        )
                                    )
                            }
                            .buttonStyle(.plain)
                            .disabled(answering)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 14)
                }
            }
        }
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(uiColor: .secondarySystemGroupedBackground)))
    }
}

/// One definition of "the refusal", shared by every place a card's options
/// are drawn as buttons, so the tints cannot drift apart.
enum CardStyle {
    static func isRefusal(_ option: String) -> Bool {
        OptionCard.isRefusal(option)
    }
}

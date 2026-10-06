import SwiftUI
import CompanionCore

/// The same reversible narration fold as desktop. Activity preferences apply
/// to tool receipts independently, so Hidden still offers this compact row.
struct AssistantTurnChip: View {
    let turn: AssistantTurnFold
    let chat: Chat
    let openLink: (URL, Message) -> OpenURLAction.Result
    var openThread: ((ThreadRef) -> Void)? = nil
    var revealedMessageId: String? = nil
    var scrollToMessage: ((String) -> Void)? = nil
    var digest: Message? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.botTintColor) private var color
    @State private var showingDigest = false
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) { expanded.toggle() }
                Haptics.selection()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark").foregroundStyle(BotTint.ink(color))
                    Text(turn.label)
                    Image(systemName: "chevron.right")
                        .rotationEffect(.degrees(expanded ? 90 : 0))
                }
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(turn.label)
            .accessibilityHint(expanded ? "Hides intermediate replies" : "Shows intermediate replies")
            .accessibilityIdentifier("assistant-turn.\(turn.turnId)")

            if expanded {
                ForEach(Array(turn.messages.enumerated()), id: \.element.id) { index, message in
                    MessageRow(
                        chat: chat, message: message, endsRun: index == turn.messages.count - 1,
                        startsRun: index == 0, openLink: openLink, openThread: openThread
                    )
                    .equatable()
                    .id(message.id)
                    .onAppear {
                        if revealedMessageId == message.id { scrollToMessage?(message.id) }
                    }
                }
                if let digest, !DigestPresentation(message: digest).isEmpty {
                    Button {
                        Haptics.selection()
                        showingDigest = true
                    } label: {
                        Label("What I did", systemImage: "checklist")
                            .font(.footnote)
                            .foregroundStyle(BotTint.ink(digest.from?.color ?? color))
                            .frame(minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("assistant-turn-digest.\(turn.turnId)")
                }
            }
        }
        .onValueChange(of: revealedMessageId, initial: true) { id in
            if turn.messages.contains(where: { $0.id == id }) { expanded = true }
        }
        .sheet(isPresented: $showingDigest) {
            if let digest {
                DigestSheet(message: digest, botName: digest.from?.name ?? chat.name, color: digest.from?.color ?? color)
            }
        }
    }
}

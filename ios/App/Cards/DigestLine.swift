import CompanionCore
import SwiftUI

/// The summary is available without exposing the machinery of every step.
struct DigestLine: View {
    let message: Message
    @Environment(\.botTintColor) private var color
    var botName = "Bot"
    @State private var showingSheet = false

    var body: some View {
        let presentation = DigestPresentation(message: message)
        let problem = digestHasProblem(message)
        if !presentation.isEmpty {
            Button {
                Haptics.selection()
                showingSheet = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: problem ? "exclamationmark.triangle.fill" : "checklist")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(problem ? BotTint.warning : BotTint.ink(message.from?.color ?? color))
                    line(presentation.slimLine, problem: problem)
                        .font(.footnote).monospacedDigit()
                        .multilineTextAlignment(.leading)
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                }
                .frame(minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(DigestSummary(text: message.text ?? "").chipLabel)
            .accessibilityValue(presentation.slimLine)
            .accessibilityHint("Shows what this reply did")
            .accessibilityIdentifier("digest-line-\(message.id)")
            .sheet(isPresented: $showingSheet) {
                DigestSheet(message: message, botName: message.from?.name ?? botName, color: message.from?.color ?? color)
            }
        }
    }

    private func line(_ value: String, problem: Bool) -> Text {
        guard problem else { return Text(verbatim: value).foregroundColor(.secondary) }
        let parts = value.components(separatedBy: " · ")
        let first = Text(verbatim: parts.first ?? value).foregroundColor(BotTint.warning)
        guard parts.count > 1 else { return first }
        return first + Text(verbatim: " · " + parts.dropFirst().joined(separator: " · ")).foregroundColor(.secondary)
    }
}

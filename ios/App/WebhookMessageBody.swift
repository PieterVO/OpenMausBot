import SwiftUI
import CompanionCore

struct WebhookMessageBody: View {
    let content: WebhookMessageContent
    var color: String? = nil
    @Environment(\.botTintColor) private var botTintColor
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Webhook task", systemImage: "bolt.horizontal.circle")
                .font(.caption.weight(.semibold))
                .foregroundStyle(BotTint.ink(color ?? botTintColor))
            Text(content.task)
                .font(.body)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
            if let payload = content.payload {
                DisclosureGroup(isExpanded: $expanded) {
                    ScrollView {
                        Text(payload)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("webhook-payload")
                    }
                    .padding(12)
                    .frame(maxHeight: 180)
                    .background(BotTint.inset, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                } label: {
                    Text("Event payload")
                        .frame(minHeight: 44)
                }
                .font(.caption)
                .tint(BotTint.ink(color ?? botTintColor))
            }
        }
        .foregroundStyle(Color.primary)
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(BotTint.theirs(color ?? botTintColor), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

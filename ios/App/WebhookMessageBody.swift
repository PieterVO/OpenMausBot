import SwiftUI
import CompanionCore

struct WebhookMessageBody: View {
    let content: WebhookMessageContent
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Webhook task", systemImage: "bolt.horizontal.circle")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.8))
            Text(content.task)
                .font(.body)
                .textSelection(.enabled)
                .fixedSize(horizontal: false, vertical: true)
            if let payload = content.payload {
                Rectangle().fill(Color.white.opacity(0.25)).frame(height: 1)
                    .accessibilityHidden(true)
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
                    .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                } label: {
                    Text("Event payload")
                        .frame(minHeight: 44)
                }
                .font(.caption)
                .tint(.white)
            }
        }
        .foregroundStyle(.white)
    }
}

import SwiftUI
import CompanionCore

/// Only reports expand; command output remains on the computer. Notices and
/// failed turns retain their special meaning at every activity preference.
struct StepReceipt: View {
    let tool: ToolActivity
    let busy: Bool
    let output: String?
    let outputIsProse: Bool
    @Environment(\.botTintColor) private var color

    var body: some View {
        if tool.name.hasPrefix("notice:") {
            Label(String(tool.name.dropFirst("notice:".count)).trimmingCharacters(in: .whitespaces), systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        } else if let cause = failedTurnCause(tool.name) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
                Text(cause)
                    .font(.body)
                    .foregroundStyle(.primary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else if let output {
            SkillExecutionReceiptView(skillName: tool.label,
                                      status: tool.ok.map { $0 ? "success" : "error" } ?? (busy ? "running" : "success"),
                                      output: output, outputIsProse: outputIsProse)
        } else {
            HStack(alignment: .top, spacing: 10) {
                StepBadge(name: tool.name, failed: tool.ok == false, color: color)
                    .padding(.top, 1)
                VStack(alignment: .leading, spacing: 3) {
                    Text(tool.label)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    if let summary = tool.summary, !summary.isEmpty {
                        Text(summary)
                            .font(toolCategory(tool.name) == .shell ? .footnote.monospaced() : .footnote)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if tool.ok == false {
                    Image(systemName: "xmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                } else if tool.ok == nil, busy {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(BotTint.ink(color))
                }
            }
            .padding(.vertical, 5)
            .accessibilityElement(children: .combine)
        }
    }
}

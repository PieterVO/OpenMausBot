import SwiftUI

public struct SkillExecutionReceiptView: View {
    public let skillName: String
    public let status: String // "running", "success", "error"
    public let durationMs: Int
    public let parameters: String
    public let output: String
    /// A teammate's report is prose, not a tool log: keep every word selectable.
    public let outputIsProse: Bool

    @Environment(\.botTintColor) private var color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isExpanded = false

    public init(
        skillName: String,
        status: String = "success",
        durationMs: Int = 0,
        parameters: String = "",
        output: String = "",
        outputIsProse: Bool = false
    ) {
        self.skillName = skillName
        self.status = status
        self.durationMs = durationMs
        self.parameters = parameters
        self.output = output
        self.outputIsProse = outputIsProse
    }

    public var body: some View {
        let hasDetails = !parameters.isEmpty || !output.isEmpty
        VStack(alignment: .leading, spacing: 8) {
            Button {
                guard hasDetails else { return }
                Haptics.selection()
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(alignment: .top, spacing: 8) {
                    StepBadge(name: skillName, failed: status == "error")
                    VStack(alignment: .leading, spacing: 3) {
                        Text(skillName).font(.subheadline).foregroundStyle(.primary).lineLimit(2)
                        if durationMs > 0 {
                            Text("\(durationMs)ms").font(.caption).monospacedDigit().foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 0)
                    statusIcon
                    if hasDetails {
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    }
                }
                .frame(minHeight: 44, alignment: .center)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!hasDetails)
            .accessibilityLabel("\(skillName), \(status)")
            .accessibilityHint(hasDetails ? (isExpanded ? String(localized: "Hides the report") : String(localized: "Shows the report")) : "")

            if isExpanded && hasDetails {
                VStack(alignment: .leading, spacing: 12) {
                    if !parameters.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Input").font(.caption.weight(.semibold)).foregroundStyle(BotTint.ink(color))
                            Text(parameters).font(.footnote.monospaced()).foregroundStyle(.primary)
                                .textSelection(.enabled)
                        }
                    }
                    if !output.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(outputIsProse ? "Report" : "Output")
                                .font(.caption.weight(.semibold)).foregroundStyle(BotTint.ink(color))
                            if outputIsProse {
                                Text(verbatim: output).font(.body).foregroundStyle(.primary)
                                    .textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
                            } else {
                                Text(output).font(.footnote.monospaced()).foregroundStyle(.primary).lineLimit(6)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(BotTint.inset, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .transition(.opacity)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(BotTint.theirs(color), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    @ViewBuilder private var statusIcon: some View {
        switch status {
        case "success":
            Image(systemName: "checkmark").font(.caption.weight(.semibold)).foregroundStyle(BotTint.ink(color))
                .accessibilityHidden(true)
        case "error":
            Image(systemName: "xmark.circle.fill").font(.subheadline).foregroundStyle(.red)
                .accessibilityHidden(true)
        case "running":
            ProgressView().controlSize(.mini).tint(BotTint.ink(color))
                .accessibilityLabel("Running")
        default:
            Text(status.capitalized).font(.caption).foregroundStyle(.secondary)
        }
    }
}

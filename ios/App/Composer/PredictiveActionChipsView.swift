import SwiftUI

public struct ActionChipItem: Identifiable {
    /// Stable ids keep stored chips in place when the composer renders again.
    public let id: String
    public let title: String
    public let icon: String
    public let prompt: String

    public init(id: String = UUID().uuidString, title: String, icon: String, prompt: String) {
        self.id = id
        self.title = title
        self.icon = icon
        self.prompt = prompt
    }
}

public struct PredictiveActionChipsView: View {
    public let chips: [ActionChipItem]
    public let accentColor: Color?
    public let color: String?
    public let onSelectChip: (ActionChipItem) -> Void

    @Environment(\.botTintColor) private var botTintColor

    public static let defaultChips: [ActionChipItem] = [
        ActionChipItem(title: "Show diff", icon: "arrow.triangle.pull", prompt: "Show latest git diff"),
        ActionChipItem(title: "Run tests", icon: "checkmark.seal", prompt: "Run all automated tests"),
        ActionChipItem(title: "Explain steps", icon: "text.bubble", prompt: "Explain the changes in detail"),
        ActionChipItem(title: "What's next?", icon: "sparkles", prompt: "What should we do next?")
    ]

    public init(
        chips: [ActionChipItem] = PredictiveActionChipsView.defaultChips,
        accentColor: Color? = nil,
        color: String? = nil,
        onSelectChip: @escaping (ActionChipItem) -> Void
    ) {
        self.chips = chips
        self.accentColor = accentColor
        self.color = color
        self.onSelectChip = onSelectChip
    }

    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(chips) { chip in
                    Button {
                        onSelectChip(chip)
                        Haptics.selection()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: chip.icon)
                                .foregroundStyle(accentColor ?? BotTint.ink(color ?? botTintColor))
                                .accessibilityHidden(true)
                            Text(chip.title)
                                .foregroundStyle(.primary)
                        }
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .frame(minHeight: 44)
                        .background(BotTint.theirs(color ?? botTintColor), in: Capsule())
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 3)
        }
    }
}

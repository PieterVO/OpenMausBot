import SwiftUI

public struct CommandSkillItem: Identifiable {
    public let id: String
    public let title: String
    public let description: String
    public let iconName: String
    public let brandColor: Color
    public let command: String

    public init(id: String, title: String, description: String, iconName: String, brandColor: Color, command: String) {
        self.id = id
        self.title = title
        self.description = description
        self.iconName = iconName
        self.brandColor = brandColor
        self.command = command
    }
}

public struct CommandSkillHUDView: View {
    @Binding public var text: String
    @Binding public var isVisible: Bool
    public let commands: [CommandSkillItem]
    public let accentColor: Color?
    public let color: String?
    public let onSelectCommand: (CommandSkillItem) -> Void

    @Environment(\.botTintColor) private var botTintColor
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public static let defaultCommands: [CommandSkillItem] = [
        CommandSkillItem(
            id: "computer",
            title: "/computer",
            description: "Open live screen & desktop controls",
            iconName: "desktopcomputer",
            brandColor: Color(hex: "#38BDF8"),
            command: "/computer"
        ),
        CommandSkillItem(
            id: "tasks",
            title: "/threads",
            description: "View and manage threads",
            iconName: "square.stack.fill",
            brandColor: Color(hex: "#A855F7"),
            command: "/threads"
        ),
        CommandSkillItem(
            id: "diff",
            title: "/diff",
            description: "Inspect latest git changes and patches",
            iconName: "arrow.triangle.pull",
            brandColor: Color(hex: "#22C55E"),
            command: "Show git diff and list modified files"
        ),
        CommandSkillItem(
            id: "retry",
            title: "/retry",
            description: "Retry the last turn with fresh context",
            iconName: "arrow.clockwise",
            brandColor: Color(hex: "#EAB308"),
            command: "Please retry the last turn"
        ),
        CommandSkillItem(
            id: "steer",
            title: "/steer",
            description: "Steer and redirect active execution",
            iconName: "steeringwheel",
            brandColor: Color(hex: "#F97316"),
            command: "Pause and explain your current plan"
        )
    ]

    public init(
        text: Binding<String>,
        isVisible: Binding<Bool>,
        commands: [CommandSkillItem] = CommandSkillHUDView.defaultCommands,
        accentColor: Color? = nil,
        color: String? = nil,
        onSelectCommand: @escaping (CommandSkillItem) -> Void
    ) {
        self._text = text
        self._isVisible = isVisible
        self.commands = commands
        self.accentColor = accentColor
        self.color = color
        self.onSelectCommand = onSelectCommand
    }

    private var ink: Color { accentColor ?? BotTint.ink(color ?? botTintColor) }

    private var filteredCommands: [CommandSkillItem] {
        if text.hasPrefix("/") && text.count > 1 {
            let query = String(text.dropFirst()).lowercased()
            return commands.filter {
                $0.title.lowercased().contains(query) || $0.description.lowercased().contains(query)
            }
        }
        return commands
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            headerBar
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(filteredCommands) { command in
                        CommandCardView(command: command, ink: ink) {
                            onSelectCommand(command)
                            withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { isVisible = false }
                            Haptics.selection()
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .background(BotTint.theirs(color ?? botTintColor), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .padding(.horizontal, 12)
        .padding(.bottom, 4)
    }

    private var headerBar: some View {
        HStack(spacing: 8) {
            Label("Slash commands", systemImage: "command")
                .font(.caption.weight(.semibold))
                .foregroundStyle(ink)
            Spacer(minLength: 8)
            Button {
                withAnimation(reduceMotion ? nil : .spring(response: 0.28, dampingFraction: 0.82)) {
                    isVisible = false
                    if text == "/" { text = "" }
                }
                Haptics.selection()
            } label: {
                Image(systemName: "xmark")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ink)
                    .frame(width: 28, height: 28)
                    .background(BotTint.inset, in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close slash commands")
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .padding(.top, 4)
    }
}

private struct CommandCardView: View {
    let command: CommandSkillItem
    let ink: Color
    let action: () -> Void
    @ScaledMetric(relativeTo: .caption) private var cardWidth = 172

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Label(command.title, systemImage: command.iconName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(ink)
                Text(LocalizedStringKey(command.description))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
            .frame(width: cardWidth, alignment: .leading)
            .frame(minHeight: 44, alignment: .topLeading)
            .background(BotTint.inset, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

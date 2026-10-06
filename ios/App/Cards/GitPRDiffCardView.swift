import SwiftUI

public struct GitPRDiffCardView: View {
    public let filename: String
    public let diffText: String
    public let additions: Int
    public let deletions: Int

    @Environment(\.botTintColor) private var color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showDiff = true
    @State private var showAllLines = false

    public init(filename: String = "Changes", diffText: String, additions: Int = 0, deletions: Int = 0) {
        self.filename = filename
        self.diffText = diffText
        if additions == 0 && deletions == 0 {
            let lines = diffText.components(separatedBy: "\n")
            self.additions = lines.filter { $0.hasPrefix("+") && !$0.hasPrefix("+++") }.count
            self.deletions = lines.filter { $0.hasPrefix("-") && !$0.hasPrefix("---") }.count
        } else {
            self.additions = additions
            self.deletions = deletions
        }
    }

    public var body: some View {
        let lines = diffText.components(separatedBy: "\n")
        let visibleLines = lines.prefix(showAllLines ? lines.count : 80)
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.pull").foregroundStyle(BotTint.ink(color))
                Text(filename).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(1)
                Spacer(minLength: 0)
                HStack(spacing: 6) {
                    Text("+\(additions)").foregroundStyle(BotTint.ink("green"))
                    Text("−\(deletions)").foregroundStyle(BotTint.ink("red"))
                }
                .font(.caption.weight(.semibold)).monospacedDigit()
                .padding(.horizontal, 8).padding(.vertical, 5)
                .background(BotTint.inset, in: Capsule())
            }

            if !diffText.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    Button {
                        Haptics.selection()
                        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) {
                            showDiff.toggle()
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.down").rotationEffect(.degrees(showDiff ? 0 : -90))
                            Text(showDiff ? "Hide Diff" : "View Diff")
                            Spacer(minLength: 0)
                        }
                        .font(.subheadline.weight(.medium)).foregroundStyle(BotTint.ink(color))
                        .frame(minHeight: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    if showDiff {
                        ScrollView(.horizontal, showsIndicators: false) {
                            VStack(alignment: .leading, spacing: 1) {
                                ForEach(Array(visibleLines.enumerated()), id: \.offset) { _, line in
                                    diffLineView(line)
                                }
                            }
                            .padding(8)
                        }
                        .background(BotTint.inset, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .transition(.opacity)

                        if lines.count > 80 {
                            Button(showAllLines ? "Show first 80 lines" : "Show all \(lines.count) lines") {
                                Haptics.selection()
                                withAnimation(.easeOut(duration: 0.2)) { showAllLines.toggle() }
                            }
                            .font(.subheadline.weight(.medium)).foregroundStyle(BotTint.ink(color))
                            .frame(minHeight: 44).buttonStyle(.plain)
                            .accessibilityHint("The copied diff always includes every line")
                        }
                    }
                }
            }

            Divider()
            Button {
                PlatformBridge.copyToPasteboard(diffText)
            } label: {
                Label("Copy Diff", systemImage: "doc.on.doc")
                    .font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                    .padding(.horizontal, 12).frame(minHeight: 44)
                    .background(BotTint.inset, in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(BotTint.theirs(color), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func diffLineView(_ line: String) -> some View {
        let addition = line.hasPrefix("+") && !line.hasPrefix("+++")
        let deletion = line.hasPrefix("-") && !line.hasPrefix("---")
        let header = line.hasPrefix("@@") || line.hasPrefix("diff")
        return Text(line)
            .font(.footnote.monospaced())
            .foregroundStyle(addition ? BotTint.ink("green") : deletion ? BotTint.ink("red") : header ? BotTint.ink(color) : .primary)
            .padding(.horizontal, 4).padding(.vertical, 1)
            .background(addition ? BotTint.glyphFill("green") : deletion ? BotTint.glyphFill("red") : .clear)
            .clipShape(RoundedRectangle(cornerRadius: 2))
    }
}

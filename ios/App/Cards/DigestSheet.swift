import CompanionCore
import SwiftUI

/// Structured receipts and older text-only receipts share one disclosure.
struct DigestSheet: View {
    let message: Message
    var botName = "Bot"
    var color: String? = nil
    @Environment(\.botTintColor) private var environmentColor
    @Environment(\.dismiss) private var dismiss

    private var presentation: DigestPresentation { DigestPresentation(message: message) }
    private var tintColor: String? { color ?? message.from?.color ?? environmentColor }

    var body: some View {
        let digest = presentation
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header(digest)
                    stats(digest)
                    if let files = digest.files, files.count > 0 || (files.truncated ?? 0) > 0 {
                        filesSection(files)
                    }
                    if !digest.tools.isEmpty { toolsSection(digest.tools) }
                    if !digest.memory.isEmpty { memorySection(digest.memory) }
                    fallbackSections(digest.fallbackLines)
                    if let note = digest.coverageNote {
                        Text(note).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(uiColor: .systemBackground))
            .navigationTitle("What I did")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Copy", systemImage: "doc.on.doc") {
                        PlatformBridge.copyToPasteboard(digest.plainText)
                    }
                }
            }
            .tint(BotTint.ink(tintColor))
        }
        .presentationDetents([.medium, .large])
        .accessibilityIdentifier("digest-sheet-\(message.id)")
    }

    private func header(_ digest: DigestPresentation) -> some View {
        HStack(spacing: 10) {
            MausAvatar(color: tintColor ?? "grey", size: 36)
            VStack(alignment: .leading, spacing: 4) {
                Text("What \(botName) did").font(.headline)
                HStack(spacing: 4) {
                    if let duration = digest.duration {
                        Text("Worked \(duration)")
                        Text("·")
                    }
                    Text(message.date, format: .dateTime.hour().minute())
                }
                .font(.subheadline).monospacedDigit().foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func stats(_ digest: DigestPresentation) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if digest.toolCalls > 0 { stat(String(localized: "\(digest.toolCalls) tool calls"), symbol: "checklist") }
                if let files = digest.files {
                    let count = files.count
                    if count > 0 { stat(String(localized: "\(count) files"), symbol: "doc") }
                }
                if !digest.memory.isEmpty { stat(String(localized: "\(digest.memory.count) memory changes"), symbol: "bookmark") }
                if let tokens = digest.tokens {
                    stat(String(localized: "\(tokens) tokens"), symbol: "number")
                }
                if let cost = digest.costUsd {
                    stat(cost.formatted(.currency(code: "USD").precision(.fractionLength(2))), symbol: "dollarsign")
                }
            }
        }
    }

    private func stat(_ label: String, symbol: String) -> some View {
        Label(label, systemImage: symbol)
            .font(.caption.weight(.medium)).monospacedDigit()
            .foregroundStyle(BotTint.ink(tintColor))
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(BotTint.glyphFill(tintColor), in: Capsule())
    }

    private func filesSection(_ files: TurnDigest.Files) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Files").font(.headline)
            ForEach(Array(files.added.enumerated()), id: \.offset) { _, path in
                fileRow(path, symbol: "plus.circle.fill", color: .green)
            }
            ForEach(Array(files.changed.enumerated()), id: \.offset) { _, path in
                fileRow(path, symbol: "pencil.circle.fill", color: .blue)
            }
            ForEach(Array(files.deleted.enumerated()), id: \.offset) { _, path in
                fileRow(path, symbol: "minus.circle.fill", color: .red)
            }
            if let more = files.truncated, more > 0 {
                Text("+\(more) more").font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
        }
    }

    private func fileRow(_ path: String, symbol: String, color: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol).font(.body).foregroundStyle(color)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: (path as NSString).lastPathComponent).font(.body)
                let parent = (path as NSString).deletingLastPathComponent
                if !parent.isEmpty { Text(verbatim: parent).font(.caption).foregroundStyle(.secondary) }
            }
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }

    private func toolsSection(_ tools: [TurnDigest.Tool]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Tools").font(.headline)
            ForEach(Array(tools.enumerated()), id: \.offset) { _, tool in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        StepBadge(name: tool.name, failed: tool.failed > 0, color: tintColor)
                        Text(verbatim: tool.name).font(.body).textSelection(.enabled)
                        Spacer(minLength: 0)
                        Text("×\(tool.count)").font(.subheadline).monospacedDigit().foregroundStyle(.secondary)
                    }
                    if tool.failed > 0 {
                        Text("\(tool.failed) failed").font(.caption.weight(.medium)).monospacedDigit()
                            .foregroundStyle(.red).padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Color.red.opacity(0.1), in: Capsule())
                            .padding(.leading, 30)
                    }
                    if let sample = tool.sample, !sample.isEmpty {
                        Text(verbatim: sample).font(.footnote.monospaced()).foregroundStyle(.secondary)
                            .textSelection(.enabled).padding(.leading, 30)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func memorySection(_ memory: [TurnDigest.MemoryChange]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Memory").font(.headline)
            ForEach(Array(memory.enumerated()), id: \.offset) { _, change in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "bookmark").foregroundStyle(BotTint.ink(tintColor))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: change.path).font(.body).textSelection(.enabled)
                        Text(memoryKind(change.kind)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func memoryKind(_ kind: TurnDigest.MemoryChange.Kind) -> String {
        switch kind {
        case .created: String(localized: "Created")
        case .updated: String(localized: "Updated")
        case .deleted: String(localized: "Deleted")
        }
    }

    @ViewBuilder private func fallbackSections(_ lines: [DigestSummary.Line]) -> some View {
        ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
            VStack(alignment: .leading, spacing: 10) {
                if let label = line.label { Text(sectionLabel(label)).font(.headline) }
                ForEach(Array(line.items.enumerated()), id: \.offset) { _, item in
                    Text(verbatim: item)
                        .font(line.label == "Tools" ? .footnote.monospaced() : .body)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func sectionLabel(_ label: String) -> String {
        switch label {
        case "Files": String(localized: "Files")
        case "Tools": String(localized: "Tools")
        case "Memory": String(localized: "Memory")
        default: label
        }
    }
}

import CompanionCore
import SwiftUI

/// Reduced activity is a reversible timeline, not a second kind of receipt.
/// Failure and plan rows break runs in the core transcript projection.
struct ActivityRunChip: View {
    let items: [Message]
    var openThread: ((ThreadRef) -> Void)? = nil
    var runID: String? = nil
    var busy = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var expanded = false

    private var running: Bool { busy && items.contains { $0.tool?.ok == nil } }
    private var summary: String {
        if running {
            let count = String(localized: "Running \(items.count) steps")
            guard let label = items.last?.tool?.label, !label.isEmpty else { return count }
            return count + " · " + label
        }
        return String(localized: "Ran \(items.count) steps")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                Haptics.selection()
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) {
                    expanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    HStack(spacing: -6) {
                        ForEach(Array(items.prefix(3)), id: \.id) { item in
                            StepBadge(name: item.tool?.name ?? "", failed: item.tool?.ok == false)
                                .overlay { Circle().stroke(Color(uiColor: .systemBackground), lineWidth: 1.5) }
                        }
                    }
                    .accessibilityHidden(true)
                    Text(summary).font(.subheadline).foregroundStyle(.secondary)
                        .lineLimit(2).multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(summary)
            .accessibilityHint(expanded ? "Hides the steps" : "Shows the steps")
            .accessibilityIdentifier("step-run-\(runID ?? items.first?.id ?? "empty")")

            if expanded {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        ActivityTimelineRow(
                            item: item, busy: busy, index: index,
                            connectsNext: index < items.count - 1, openThread: openThread
                        )
                    }
                }
                .transition(.opacity)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

private struct ActivityTimelineRow: View {
    let item: Message
    let busy: Bool
    let index: Int
    let connectsNext: Bool
    let openThread: ((ThreadRef) -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false

    var body: some View {
        ActivityChip(
            tool: item.tool, threadRef: item.threadRef, openThread: openThread,
            outputIsProse: item.isTeammateReport, messageID: item.id, busy: busy
        )
        .background(alignment: .topLeading) {
            if connectsNext {
                GeometryReader { geometry in
                    HStack(spacing: 0) {
                        Rectangle().fill(Color(uiColor: .separator))
                            .frame(width: 1, height: max(0, geometry.size.height - 22 + 8))
                        Spacer(minLength: 0)
                    }
                    .padding(.leading, 10.5)
                    .offset(y: 22)
                }
                .accessibilityHidden(true)
            }
        }
        .opacity(visible ? 1 : 0)
        .onAppear {
            withAnimation(.easeOut(duration: 0.2).delay(reduceMotion ? 0 : Double(min(index, 7)) * 0.03)) {
                visible = true
            }
        }
    }
}

import CompanionCore
import SwiftUI

/// A plan stays in place while its tool snapshots change underneath it.
struct PlanCard: View {
    let rowID: String
    let plan: TodoPlan
    var color: String? = nil
    @Environment(\.botTintColor) private var environmentColor
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showAll = false

    private var tint: Color { BotTint.ink(color ?? environmentColor) }
    private var visibleIndices: Range<Int> {
        guard !showAll, plan.items.count > 6 else { return plan.items.indices }
        let activeIndex = plan.items.firstIndex { $0.status == .active } ?? 0
        let start = min(max(activeIndex - 2, 0), plan.items.count - 6)
        return start..<(start + 6)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: plan.isFinished ? "checkmark.circle.fill" : "checklist")
                    .foregroundStyle(tint)
                Text(plan.isFinished ? "All done" : "Plan")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(plan.isFinished ? tint : .primary)
                Spacer(minLength: 8)
                Text("\(plan.done) of \(plan.total)")
                    .font(.subheadline)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
            .accessibilityHidden(true)

            GeometryReader { geometry in
                Capsule().fill(BotTint.inset)
                    .overlay(alignment: .leading) {
                        Capsule().fill(tint)
                            .frame(width: geometry.size.width * progress)
                    }
            }
            .frame(height: 4)
            .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82), value: plan.done)
            .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82), value: plan.total)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(visibleIndices), id: \.self) { index in
                    PlanItemRow(item: plan.items[index], tint: tint)
                        .id(plan.items[index].id)
                        .accessibilityIdentifier("plan-\(rowID)-item-\(index)")
                }
            }

            if plan.items.count > 6 {
                Button {
                    Haptics.selection()
                    withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) {
                        showAll.toggle()
                    }
                } label: {
                    Text(showAll ? String(localized: "Show less") : String(localized: "Show all \(plan.items.count)"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(tint)
                        .frame(minHeight: 44)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            if plan.truncated {
                Text("More items not shown")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(BotTint.theirs(color ?? environmentColor), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Plan, \(plan.done) of \(plan.total) done")
        .accessibilityIdentifier("plan-\(rowID)")
    }

    private var progress: CGFloat {
        plan.total > 0 ? CGFloat(plan.done) / CGFloat(plan.total) : 0
    }
}

private struct PlanItemRow: View {
    let item: TodoItem
    let tint: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var checkProgress: CGFloat
    @State private var visible = false

    init(item: TodoItem, tint: Color) {
        self.item = item
        self.tint = tint
        _checkProgress = State(initialValue: item.status == .done ? 1 : 0)
    }

    private var text: String { item.status == .active ? item.activeText ?? item.text : item.text }
    private var completed: Bool { item.status == .done }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            marker.frame(width: 20, height: 20).padding(.top, 2)
            Text(text)
                .font(.callout.weight(item.status == .active ? .semibold : .regular))
                .foregroundStyle(item.status == .cancelled ? .tertiary : .primary)
                .strikethrough(item.status == .cancelled)
                .opacity(completed ? 0 : 1)
                .overlay(alignment: .topLeading) {
                    Text(text)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .strikethrough()
                        .opacity(completed ? 1 : 0)
                        .accessibilityHidden(true)
                }
                .fixedSize(horizontal: false, vertical: true)
                .animation(.easeOut(duration: 0.28), value: completed)
            Spacer(minLength: 0)
        }
        .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82), value: item.status)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .onAppear { visible = true }
        .onDisappear { visible = false }
        .onValueChange(of: item.status) { status in
            if status == .done {
                if visible { Haptics.impact(.soft) }
                checkProgress = 0
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .easeOut(duration: 0.28).delay(0.12)) { checkProgress = 1 }
            } else {
                checkProgress = 0
            }
        }
    }

    @ViewBuilder private var marker: some View {
        switch item.status {
        case .done:
            Circle().fill(tint)
                .overlay {
                    PlanCheckmark().trim(from: 0, to: checkProgress)
                        .stroke(.white, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .padding(5)
                }
                .transition(reduceMotion ? .opacity : .scale(scale: 0.6).combined(with: .opacity))
        case .active:
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion || scenePhase != .active)) { timeline in
                Circle().stroke(tint.opacity(0.35), lineWidth: 1.5)
                    .overlay {
                        Circle().trim(from: 0, to: 0.25)
                            .stroke(tint, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                            .rotationEffect(.degrees(reduceMotion ? -90 : timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1) * 360))
                    }
            }
        case .pending:
            Circle().stroke(.tertiary, lineWidth: 1.5)
        case .cancelled:
            Image(systemName: "xmark").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
        }
    }

    private var accessibilityText: String {
        switch item.status {
        case .done: String(localized: "Done, \(item.text)")
        case .active: String(localized: "In progress, \(text)")
        case .pending: String(localized: "To do, \(item.text)")
        case .cancelled: String(localized: "Cancelled, \(item.text)")
        }
    }
}

private struct PlanCheckmark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.38, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        return path
    }
}

struct LivePlanStrip: View {
    let plan: TodoPlan
    var status: String? = nil
    let action: () -> Void
    @Environment(\.botTintColor) private var color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 7) {
                    Circle().stroke(BotTint.inset, lineWidth: 2)
                        .overlay {
                            Circle().trim(from: 0, to: plan.total > 0 ? CGFloat(plan.done) / CGFloat(plan.total) : 0)
                                .stroke(BotTint.ink(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                        }
                        .frame(width: 18, height: 18)
                        .accessibilityHidden(true)
                    Text("\(plan.done) of \(plan.total)")
                        .font(.subheadline.weight(.semibold)).monospacedDigit()
                        .foregroundStyle(BotTint.ink(color))
                    if let item = plan.active ?? plan.items.first(where: { $0.status == .pending }) {
                        Text("·").foregroundStyle(.secondary)
                        Text(item.status == .active ? item.activeText ?? item.text : item.text)
                            .font(.subheadline).foregroundStyle(.secondary)
                            .lineLimit(1).truncationMode(.tail)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                }
                if let status, !status.isEmpty {
                    Text(status).font(.caption).foregroundStyle(.tertiary)
                        .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("live-status-line")
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Shows the plan in the conversation")
        .accessibilityIdentifier("live-plan-strip")
        .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82), value: plan.done)
        .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
    }
}

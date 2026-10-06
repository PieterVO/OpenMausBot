import CompanionCore
import SwiftUI

/// Kept mounted from the first reasoning delta through the answer stream so
/// the elapsed time and disclosure do not reset when the reply arrives.
struct ThinkingView: View {
    let reasoning: String
    let answerStreaming: Bool
    let onExpand: () -> Void
    var color: String? = nil
    @Environment(\.botTintColor) private var environmentColor
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var startedAt = Date()
    @State private var endedAt: Date?
    @State private var expanded = false

    private var tint: Color { BotTint.ink(color ?? environmentColor) }
    private var motion: Animation {
        reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: answerStreaming ? 0 : 4) {
            TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 1.0 / 30, paused: answerStreaming || scenePhase != .active)) { timeline in
                let elapsed = max(0, Int((endedAt ?? timeline.date).timeIntervalSince(startedAt)))
                Button {
                    Haptics.selection()
                    if !expanded { onExpand() }
                    withAnimation(motion) { expanded.toggle() }
                } label: {
                    HStack(spacing: 8) {
                        if answerStreaming {
                            Text("Thought for \(elapsed)s")
                                .font(.caption).foregroundStyle(.tertiary).monospacedDigit()
                            Image(systemName: "chevron.down")
                                .font(.caption2.weight(.semibold)).foregroundStyle(.tertiary)
                                .rotationEffect(.degrees(expanded ? 180 : 0))
                        } else {
                            Circle().fill(tint).frame(width: 8, height: 8)
                                .opacity(reduceMotion ? 1 : 0.65 + 0.35 * sin(timeline.date.timeIntervalSinceReferenceDate * .pi))
                                .accessibilityHidden(true)
                            thinkingLabel(at: timeline.date)
                            Spacer(minLength: 8)
                            Text("\(elapsed)s").font(.caption).monospacedDigit().foregroundStyle(.tertiary)
                        }
                    }
                    .frame(minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(answerStreaming ? String(localized: "Thought for \(elapsed)s") : String(localized: "Thinking"))
                .accessibilityHint(expanded ? String(localized: "Hides reasoning") : String(localized: "Shows reasoning"))
                .accessibilityIdentifier("thinking-row")
            }

            if expanded {
                panel
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            } else if !answerStreaming {
                let latest = ReasoningWindow(reasoning, characterLimit: 320).steps.map(\.text).joined(separator: " ")
                Text(latest)
                    .font(.footnote).foregroundStyle(.secondary)
                    .lineLimit(2).truncationMode(.head)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .mask {
                        LinearGradient(colors: [.clear, .black, .black], startPoint: .top, endPoint: .bottom)
                    }
                    .id(latest)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: reasoning)
        .onValueChange(of: answerStreaming) { answering in
            if answering {
                endedAt = Date()
                withAnimation(motion) { expanded = false }
            } else {
                endedAt = nil
            }
        }
        .onAppear {
            if answerStreaming, endedAt == nil { endedAt = Date() }
        }
    }

    private func thinkingLabel(at date: Date) -> some View {
        Text("Thinking")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(tint)
            .overlay {
                if !reduceMotion {
                    GeometryReader { geometry in
                        Rectangle().fill(tint.opacity(0.28))
                            .frame(width: 18)
                            .rotationEffect(.degrees(15))
                            .offset(x: (geometry.size.width + 36) * date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6 - 24)
                    }
                    .mask(Text("Thinking").font(.subheadline.weight(.medium)))
                    .accessibilityHidden(true)
                }
            }
    }

    private var panel: some View {
        let retained = ReasoningWindow(reasoning).steps.map(\.text).joined(separator: "\n\n")
        return ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(retained)
                        .font(.footnote).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                    Color.clear.frame(height: 1).id("reasoning-bottom")
                }
                .padding(14)
            }
            .frame(maxHeight: 280)
            .background(BotTint.theirs(color ?? environmentColor), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .scrollAnchorCompat(.bottom)
            .onAppear { proxy.scrollTo("reasoning-bottom", anchor: .bottom) }
            .onValueChange(of: reasoning) { _ in
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
                    proxy.scrollTo("reasoning-bottom", anchor: .bottom)
                }
            }
        }
        .accessibilityIdentifier("thinking-panel")
    }
}

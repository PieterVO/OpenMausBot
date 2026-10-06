import SwiftUI

public struct TypingIndicatorView: View {
    public let tintColor: Color?
    public let color: String?
    @Environment(\.botTintColor) private var botTintColor
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection

    public init(tintColor: Color? = nil, color: String? = nil) {
        self.tintColor = tintColor
        self.color = color
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { context in
            HStack(spacing: 5) {
                ForEach(0..<3) { index in
                    let wave = reduceMotion ? 1 : dotWave(index, at: context.date)
                    Circle()
                        .fill(tintColor ?? BotTint.ink(color ?? botTintColor))
                        .frame(width: 8, height: 8)
                        .scaleEffect(0.75 + wave * 0.25)
                        .opacity(0.55 + wave * 0.45)
                }
            }
            .frame(minHeight: 20)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(
            BotTint.theirs(color ?? botTintColor),
            in: SpeechBubble(tail: layoutDirection == .rightToLeft ? .trailing : .leading)
        )
        .accessibilityElement(children: .ignore)
    }

    private func dotWave(_ index: Int, at date: Date) -> Double {
        let elapsed = date.timeIntervalSinceReferenceDate - Double(index) * 0.12
        return (sin(elapsed * .pi * 2 / 1.2) + 1) / 2
    }
}

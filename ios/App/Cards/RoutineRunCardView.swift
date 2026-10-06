import CompanionCore
import SwiftUI

/// One routine run, in the conversation that set the routine up.
///
/// The desktop's `src/components/RoutineRunCard.tsx`, for a phone: which
/// routine, how the run stands, and — the point of the thing — what it
/// reported. The run itself works in a thread the list keeps hidden, so
/// this card is the only place its answer shows unless you open that run.
///
/// The computer patches the same message as the run moves, so the card
/// redraws in place from queued to its result; nothing here keeps state
/// about the run beyond whether the report is expanded.
struct RoutineRunCardView: View {
    let card: RoutineRunCard
    /// When the message landed; stands in for runs with no scheduled time.
    let at: Date
    let tint: Color
    /// Opens the execution thread; nil when this phone cannot reach it.
    var openRun: (() -> Void)? = nil

    @State private var expanded = false
    @Environment(\.botTintColor) private var color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Six lines at phone width is about this much. Counting characters is
    /// cruder than measuring, but a report either clearly fits or clearly
    /// does not, and the toggle only has to appear for the second kind.
    private static let collapsedCharacters = 320
    private static let collapsedLines = 6

    private var summary: String? {
        guard let text = card.summary?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
        return text
    }

    private var error: String? {
        guard let text = card.error?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
        return text
    }

    private var isLong: Bool {
        guard let summary else { return false }
        return summary.count > Self.collapsedCharacters
            || summary.split(whereSeparator: \.isNewline).count > Self.collapsedLines
    }

    private var toneColor: Color {
        switch card.tone {
        case .neutral: .secondary
        case .active: tint
        case .attention: .orange
        case .danger: .red
        }
    }

    /// "Review" when the run is stopped on the person, as on desktop.
    private var actionLabel: String {
        card.goalStatus == "needs-input" ? "Review" : "Open run"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header

            if let summary {
                Text(verbatim: summary)
                    .font(.body)
                    .foregroundStyle(.primary)
                    .lineLimit(expanded ? nil : Self.collapsedLines)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                if isLong {
                    Button(expanded ? "Show less" : "Show report") {
                        Haptics.selection()
                        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) { expanded.toggle() }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(minHeight: 44)
                    .buttonStyle(.plain)
                }
            }

            if let error {
                Label {
                    Text(verbatim: error)
                        .fixedSize(horizontal: false, vertical: true)
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill")
                }
                .font(.subheadline)
                .foregroundStyle(.red)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.red.opacity(0.10))
                )
            }

            if let openRun {
                Button {
                    Haptics.selection()
                    openRun()
                } label: {
                    HStack(spacing: 4) {
                        Text(actionLabel)
                        Image(systemName: "arrow.right")
                            .font(.caption.weight(.semibold))
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(BotTint.actionLabel(color))
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .frame(minHeight: 44)
                    .background(tint, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(actionLabel) for \(card.routineName)")
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(BotTint.theirs(color))
        )
        .accessibilityElement(children: .contain)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(tint)
                Text(card.routineName.isEmpty ? "Routine" : card.routineName)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 4)
                HStack(spacing: 4) {
                    if card.knownStatus == .running, card.goalStatus == nil {
                        ProgressView().controlSize(.mini)
                    }
                    Text(card.stateLabel)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(toneColor)
                .accessibilityElement(children: .combine)
            }
            Text(scheduled, format: .dateTime.weekday(.abbreviated).month(.abbreviated).day().hour().minute())
                .font(.caption).monospacedDigit()
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(card.headline)
    }

    private var scheduled: Date {
        card.scheduledFor.map { Date(timeIntervalSince1970: $0 / 1_000) } ?? at
    }
}

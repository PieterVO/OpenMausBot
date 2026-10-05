import CompanionCore
import SwiftUI

/// Home keeps identity and status together; the same corner badge follows a
/// bot from an attention row into Updates, without a second glyph column.
struct RosterFace<Face: View>: View {
    let color: String
    let size: CGFloat
    var working = false
    var badge: RosterFaceBadge? = nil
    @ViewBuilder let face: () -> Face

    var body: some View {
        face()
            .frame(width: size, height: size)
            .overlay {
                if working {
                    RosterWorkingArc(color: MausPalette.color(color))
                        .frame(width: size + 4, height: size + 4)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if let badge {
                    ZStack {
                        Circle().fill(Color(uiColor: .systemBackground))
                        if badge == .waiting {
                            Circle().fill(Color.orange.opacity(0.22))
                        }
                        if badge == .working {
                            RosterWorkingArc(color: MausPalette.color(color))
                                .padding(3)
                        } else {
                            Image(systemName: badge.symbol)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(badge.tint)
                        }
                    }
                    .frame(width: 20, height: 20)
                    .offset(y: 3)
                }
            }
            .accessibilityHidden(true)
    }
}

/// Only this small stroke redraws, not the avatar or its row. Off-screen
/// work stops with the scene; Reduce Motion replaces the arc with a ring.
struct RosterWorkingArc: View {
    let color: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion || scenePhase != .active)) { timeline in
            let phase = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 2.4) / 2.4
            ZStack {
                Circle().stroke(color.opacity(0.14), lineWidth: 1.5)
                Circle()
                    .trim(from: 0, to: reduceMotion ? 1 : 0.28)
                    .stroke(color, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                    .rotationEffect(.degrees(reduceMotion ? 0 : phase * 360 - 90))
            }
        }
        .accessibilityHidden(true)
    }
}

enum RosterStyle {
    static let unread = Color(hex: "#2E6FDB")
}
enum RosterFaceBadge: Equatable {
    case waiting, working, queued, unread

    var symbol: String {
        switch self {
        case .waiting: "hand.raised.fill"
        case .working: "arrow.triangle.2.circlepath"
        case .queued: "clock.fill"
        case .unread: "bell.fill"
        }
    }

    var tint: Color {
        switch self {
        case .waiting: .primary
        case .working, .queued: .secondary
        case .unread: RosterStyle.unread
        }
    }
}


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

/// Only this small stroke animates, not the avatar or its row, and it turns
/// in the render server (SpinningArc): a home list of working bots stays
/// mounted under an open chat, and a SwiftUI rotation would commit a frame
/// per arc on the main thread for as long as they work. Reduce Motion
/// replaces the arc with a ring.
struct RosterWorkingArc: View {
    let color: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        SpinningArc(color: color, track: color.opacity(0.14), lineWidth: 1.5, length: 0.28, period: 2.4, turning: !reduceMotion)
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


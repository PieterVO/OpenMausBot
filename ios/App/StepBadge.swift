import CompanionCore
import SwiftUI

/// Shared by full step rows and the overlapping reduced-run preview.
struct StepBadge: View {
    let name: String
    var failed = false
    var color: String? = nil
    var size: CGFloat = 22
    @Environment(\.botTintColor) private var environmentColor

    var body: some View {
        Image(systemName: toolCategory(name).symbolName)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(failed ? Color.red : BotTint.ink(color ?? environmentColor))
            .frame(width: size, height: size)
            .background(failed ? Color.red.opacity(0.14) : BotTint.glyphFill(color ?? environmentColor), in: Circle())
            .accessibilityHidden(true)
    }
}

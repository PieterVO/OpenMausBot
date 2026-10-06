import SwiftUI
import UIKit

/// Conversation colours resolve from traits, not the appearance at creation,
/// so an open chat changes with the system without losing the bot's identity.
enum BotTint {
    static let mine = Color(uiColor: UIColor(red: 46 / 255, green: 111 / 255, blue: 219 / 255, alpha: 1))

    static func mix(_ a: UIColor, _ b: UIColor, _ amount: CGFloat) -> UIColor {
        var ar: CGFloat = 0, ag: CGFloat = 0, ab: CGFloat = 0, aa: CGFloat = 0
        var br: CGFloat = 0, bg: CGFloat = 0, bb: CGFloat = 0, ba: CGFloat = 0
        a.getRed(&ar, green: &ag, blue: &ab, alpha: &aa)
        b.getRed(&br, green: &bg, blue: &bb, alpha: &ba)
        return UIColor(red: ar + (br - ar) * amount, green: ag + (bg - ag) * amount,
                       blue: ab + (bb - ab) * amount, alpha: aa + (ba - aa) * amount)
    }

    private static func mascot(_ name: String?) -> UIColor {
        UIColor(MausPalette.color(name ?? ""))
    }

    private static func surface(_ name: String?, dark: Bool) -> UIColor {
        let neutral = dark ? UIColor(red: 31 / 255, green: 31 / 255, blue: 34 / 255, alpha: 1)
            : UIColor(red: 240 / 255, green: 240 / 255, blue: 243 / 255, alpha: 1)
        return mix(neutral, mascot(name), dark ? 0.17 : 0.11)
    }

    private static func luminance(_ color: UIColor) -> CGFloat {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        func linear(_ c: CGFloat) -> CGFloat { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private static func contrast(_ a: UIColor, _ b: UIColor) -> CGFloat {
        let x = luminance(a), y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private static func foreground(_ name: String?, dark: Bool) -> UIColor {
        let target: UIColor = dark ? .white : .black
        let bubble = surface(name, dark: dark)
        var amount: CGFloat = dark ? 0.25 : 0.20
        var result = mix(mascot(name), target, amount)
        while contrast(result, bubble) < 4.5 && amount < 0.70 {
            amount = min(0.70, amount + 0.05)
            result = mix(mascot(name), target, amount)
        }
        return result
    }

    static func theirs(_ color: String?) -> Color {
        Color(uiColor: UIColor { surface(color, dark: $0.userInterfaceStyle == .dark) })
    }

    static func ink(_ color: String?) -> Color {
        Color(uiColor: UIColor { foreground(color, dark: $0.userInterfaceStyle == .dark) })
    }

    static func glyphFill(_ color: String?) -> Color {
        Color(uiColor: UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            return foreground(color, dark: dark).withAlphaComponent(dark ? 0.26 : 0.20)
        })
    }

    static func wash(_ color: String?) -> Color {
        Color(uiColor: UIColor { mascot(color).withAlphaComponent($0.userInterfaceStyle == .dark ? 0.22 : 0.14) })
    }

    static let inset = Color(uiColor: UIColor {
        $0.userInterfaceStyle == .dark ? UIColor.white.withAlphaComponent(0.07) : UIColor.black.withAlphaComponent(0.06)
    })

    static func actionLabel(_ color: String?) -> Color {
        Color(uiColor: UIColor { traits in
            let fill = foreground(color, dark: traits.userInterfaceStyle == .dark)
            return contrast(fill, .white) >= 4.5 ? .white : .black
        })
    }
}

extension MausPalette {
    static func mix(_ a: UIColor, _ b: UIColor, _ amount: CGFloat) -> UIColor { BotTint.mix(a, b, amount) }
}

private struct BotTintColorKey: EnvironmentKey {
    static let defaultValue: String? = nil
}

extension EnvironmentValues {
    var botTintColor: String? {
        get { self[BotTintColorKey.self] }
        set { self[BotTintColorKey.self] = newValue }
    }
}

private struct ConversationWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat = 360
}

extension EnvironmentValues {
    var conversationWidth: CGFloat {
        get { self[ConversationWidthKey.self] }
        set { self[ConversationWidthKey.self] = newValue }
    }
}

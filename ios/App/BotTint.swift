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

    /// Every bubble, glyph and link asks for its bot's colours on every
    /// render, and the ink search above is a loop of colour mixes. Each
    /// role × bot colour is worked out once, for both appearances, and the
    /// same `Color` is handed back each time, so SwiftUI also sees an
    /// unchanged value and leaves the view alone.
    private enum Role { case theirs, ink, glyphFill, wash, actionLabel }
    private struct Key: Hashable { let role: Role; let name: String }
    private static let lock = NSLock()
    private static var colors: [Key: Color] = [:]

    private static func cached(_ role: Role, _ name: String?, _ make: (_ dark: Bool) -> UIColor) -> Color {
        let key = Key(role: role, name: name ?? "")
        lock.lock()
        defer { lock.unlock() }
        if let color = colors[key] { return color }
        let light = make(false), dark = make(true)
        let color = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
        colors[key] = color
        return color
    }

    static func theirs(_ color: String?) -> Color {
        cached(.theirs, color) { surface(color, dark: $0) }
    }

    static func ink(_ color: String?) -> Color {
        cached(.ink, color) { foreground(color, dark: $0) }
    }

    static func glyphFill(_ color: String?) -> Color {
        cached(.glyphFill, color) { foreground(color, dark: $0).withAlphaComponent($0 ? 0.26 : 0.20) }
    }

    static func wash(_ color: String?) -> Color {
        cached(.wash, color) { mascot(color).withAlphaComponent($0 ? 0.22 : 0.14) }
    }

    static let inset = Color(uiColor: UIColor {
        $0.userInterfaceStyle == .dark ? UIColor.white.withAlphaComponent(0.07) : UIColor.black.withAlphaComponent(0.06)
    })

    /// Problem text sits on the plain transcript ground. System orange is
    /// ~2.2:1 on white, too faint for a footnote that says a step failed, so
    /// light mode uses a deeper orange (5:1 on white); dark keeps system orange.
    static let warning = Color(uiColor: UIColor {
        $0.userInterfaceStyle == .dark
            ? UIColor.systemOrange
            : UIColor(red: 179 / 255, green: 84 / 255, blue: 0, alpha: 1)   // #B35400
    })

    static func actionLabel(_ color: String?) -> Color {
        cached(.actionLabel, color) { dark in
            contrast(foreground(color, dark: dark), .white) >= 4.5 ? .white : .black
        }
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

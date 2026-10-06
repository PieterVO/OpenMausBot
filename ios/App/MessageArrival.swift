import SwiftUI

/// Eligibility comes from the transcript's append boundary, decided while the
/// row is first built: paging and the first loaded page never use this
/// transition, even when rows rematerialise. A row that arrives starts hidden
/// on its very first frame (no flash of the finished row before it animates
/// in), and the animation is started once, on appear, so a later render that
/// no longer calls the row fresh cannot cancel it half way.
struct MessageArrival: ViewModifier {
    let mine: Bool
    /// A reply that types itself in only needs its bubble to fade up; the
    /// text supplies the motion.
    let soft: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible: Bool
    @State private var played = false

    init(animate: Bool, soft: Bool = false, mine: Bool) {
        self.mine = mine
        self.soft = soft
        _visible = State(initialValue: !animate)
    }

    func body(content: Content) -> some View {
        let still = visible || reduceMotion || soft
        content
            .opacity(visible ? 1 : 0)
            .offset(y: still ? 0 : mine ? 18 : 10)
            .scaleEffect(still ? 1 : 0.97, anchor: mine ? .bottomTrailing : .bottomLeading)
            .onAppear {
                guard !visible, !played else { return }
                played = true
                let curve: Animation = reduceMotion || soft
                    ? .easeOut(duration: soft ? 0.12 : 0.2)
                    : .spring(response: 0.38, dampingFraction: 0.82)
                withAnimation(curve) { visible = true }
            }
    }
}

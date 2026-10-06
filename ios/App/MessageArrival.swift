import SwiftUI

/// Eligibility comes from the transcript's append boundary. Paging and the
/// first loaded page never use this transition, even when rows rematerialise.
struct MessageArrival: ViewModifier {
    let animate: Bool
    let mine: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = true
    @State private var played = false

    func body(content: Content) -> some View {
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible || reduceMotion ? 0 : mine ? 18 : 10)
            .scaleEffect(visible || reduceMotion ? 1 : 0.97, anchor: mine ? .bottomTrailing : .bottomLeading)
            .task(id: animate) {
                guard animate, !played else { return }
                played = true
                visible = false
                await Task.yield()
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.38, dampingFraction: 0.82)) {
                    visible = true
                }
            }
    }
}

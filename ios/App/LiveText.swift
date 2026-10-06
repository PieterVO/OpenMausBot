import Combine
import SwiftUI
import CompanionCore

/// One thread's reply and reasoning as they stream. Observed only by the
/// views that show that thread's live reply, so tokens for one bot do not
/// rebuild the rest of the app (see `Session.state`).
@MainActor
final class LiveText: ObservableObject {
    @Published fileprivate(set) var answer: String?
    @Published fileprivate(set) var reasoning: String?
}

/// Live text by thread. A thread gets an object the first time a view asks
/// for it; tokens for threads nobody is looking at update nothing.
@MainActor
final class LiveTextStore {
    private var threads: [String: LiveText] = [:]
    /// The thread's live text, created on first request with what has
    /// already streamed: a chat opened mid-reply shows the reply so far,
    /// not nothing until the next token.
    func thread(_ id: String, streaming: String?, reasoning: String?) -> LiveText {
        if let existing = threads[id] { return existing }
        let created = LiveText()
        created.answer = streaming
        created.reasoning = reasoning
        threads[id] = created
        return created
    }
    /// Mirror the folded state into the observed objects. Assigns only what
    /// changed, so an object publishes once per delivery that touched it.
    func sync(streaming: [String: String], reasoning: [String: String]) {
        for (id, live) in threads {
            let answer = streaming[id]
            if live.answer != answer { live.answer = answer }
            let thought = reasoning[id]
            if live.reasoning != thought { live.reasoning = thought }
        }
    }
}

/// The session for views that only act on it (answer, react, fetch an
/// image) and draw from values they are given. Reading it does not subscribe
/// to the session the way `@EnvironmentObject` does, so a transcript row is
/// not rebuilt every time any bot anywhere changes.
private struct SessionActionsKey: EnvironmentKey {
    static let defaultValue: Session? = nil
}

extension EnvironmentValues {
    var sessionActions: Session? {
        get { self[SessionActionsKey.self] }
        set { self[SessionActionsKey.self] = newValue }
    }
}

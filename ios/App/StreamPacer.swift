import CompanionCore
import QuartzCore
import SwiftUI

/// Turns text that arrives in uneven network batches into an even reveal.
///
/// The received length grows in jumps; `shown` grows a little every frame,
/// faster when it has fallen behind (`RevealPacing.step`), so a burst is
/// absorbed within a third of a second instead of landing as a clump. While
/// it advances, `fade` says how many trailing characters are still inking in;
/// it decays to zero shortly after the reveal catches up, so the last word
/// settles instead of snapping to full colour. `breath` drives the caret
/// while the stream is open.
@MainActor
final class StreamPacer: ObservableObject {
    @Published private(set) var shown: Int
    @Published private(set) var fade: Int = 0
    @Published private(set) var breath: CGFloat = 1

    static let fadeCharacters = 8
    private static let fadeSettle: CFTimeInterval = 0.18
    private static let breathPeriod: CFTimeInterval = 1.1

    private var target: Int
    private var carry: Double = 0
    private let minimumRate: Double
    private let catchUp: Double
    private var breathing = false
    private var lastFrame: CFTimeInterval?
    private var lastAdvance: CFTimeInterval = 0
    private let start = CACurrentMediaTime()
    // Touched from deinit only to invalidate; the link never runs off main.
    nonisolated(unsafe) private var link: CADisplayLink?

    init(shown: Int, minimumRate: Double, catchUp: Double) {
        self.shown = shown
        self.target = shown
        self.minimumRate = minimumRate
        self.catchUp = catchUp
    }

    deinit { link?.invalidate() }

    /// `animated: false` (Reduce Motion, VoiceOver) shows everything at once.
    func update(target: Int, breathing: Bool, animated: Bool) {
        self.target = target
        guard animated else {
            shown = target
            carry = 0
            fade = 0
            breath = 1
            stop()
            return
        }
        self.breathing = breathing
        // Replaced or shortened text (a branch switch) is shown as it is.
        if shown > target { shown = target }
        if shown < target || breathing || fade > 0 { run() } else { breath = 1; stop() }
    }

    func stop() {
        link?.invalidate()
        link = nil
        lastFrame = nil
    }

    /// The streaming bubble left the screen. The caret goes with it, so the
    /// display link stops as soon as the reveal (if any) has caught up,
    /// instead of ticking for a caret nobody can see.
    func stopBreathing() {
        breathing = false
        breath = 1
        if shown >= target, fade == 0 { stop() }
    }

    private func run() {
        guard link == nil else { return }
        let link = CADisplayLink(target: Ticker(self), selector: #selector(Ticker.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    fileprivate func tick(_ link: CADisplayLink) {
        let now = link.timestamp
        let elapsed = lastFrame.map { now - $0 } ?? 1.0 / 60
        lastFrame = now
        if shown < target {
            let step = RevealPacing.step(
                shown: shown, target: target, carry: carry,
                elapsed: elapsed, minimumRate: minimumRate, catchUp: catchUp
            )
            if step.shown != shown { lastAdvance = now }
            shown = step.shown
            carry = step.carry
        }
        let settling = max(0, 1 - (now - lastAdvance) / Self.fadeSettle)
        let nextFade = shown < target ? Self.fadeCharacters : Int((Double(Self.fadeCharacters) * settling).rounded(.up))
        if nextFade != fade { fade = nextFade }
        if breathing {
            let phase = (now - start) * 2 * .pi / Self.breathPeriod
            breath = 0.35 + 0.65 * CGFloat(0.5 + 0.5 * cos(phase))
        } else if breath != 1 {
            breath = 1
        }
        if shown >= target, fade == 0, !breathing { stop() }
    }
}

/// CADisplayLink retains its target; this keeps the pacer collectable.
private final class Ticker: NSObject {
    weak var owner: StreamPacer?
    init(_ owner: StreamPacer) { self.owner = owner }
    @objc func tick(_ link: CADisplayLink) {
        guard let owner else { link.invalidate(); return }
        MainActor.assumeIsolated { owner.tick(link) }
    }
}

/// Reply text as it is revealed: the shown prefix with any half-open bold or
/// code span closed, the trailing characters inking in, and the caret.
struct RevealedMarkdown: View {
    let text: String
    @ObservedObject var pacer: StreamPacer
    var caret = false
    /// The text is complete (a settled reply), not still arriving.
    var final = true
    var scrollIdentifier: String? = nil
    var openLink: ((URL) -> OpenURLAction.Result)? = nil

    var body: some View {
        // A settled reply that has caught up is simply itself. A stream that
        // has caught up is still mid-sentence: its last line may be a table
        // row or an open span that the next batch completes.
        let settled = final && pacer.shown >= text.count && pacer.fade == 0
        let prefix = settled ? text : MarkdownPartial.revealedPrefix(text, count: pacer.shown, final: final)
        MarkdownText(
            source: settled ? text : MarkdownPartial.closingOpenSpans(prefix),
            caret: caret,
            fadeTail: settled ? 0 : pacer.fade,
            caretAlpha: pacer.breath,
            scrollIdentifier: scrollIdentifier,
            openLink: openLink
        )
    }
}

/// How a bot's settled reply first reaches the screen.
enum ReplyArrival {
    /// History, a page of older messages, or anything already on screen.
    case settled
    /// It arrived live but never streamed visibly (Hidden detail): it types
    /// itself in, briefly.
    case revealed
    /// It is the reply that was just streaming: keep revealing from where
    /// the stream had got to, so the handover is invisible.
    case continuing(StreamPacer)
}

/// The live reply's pacer outlives the streaming bubble. Owned by the chat
/// as a plain reference (never observed by it, so a frame of reveal does not
/// rebuild the whole transcript); decisions are made while the rows are
/// built, so a new reply's first frame already has the right arrival.
@MainActor
final class LiveReveal {
    private(set) var stream: StreamPacer?
    private var handedOver: [String: StreamPacer] = [:]
    private(set) var revealing: Set<String> = []
    /// A reply that just started revealing grows for up to a second; until
    /// then the chat keeps its end in view for a reader who is following.
    private var revealUntil: Date = .distantPast

    /// Whether a growing reply should keep the end of the chat in view.
    func pinsEnd(at now: Date = Date()) -> Bool { stream != nil || now < revealUntil }

    /// The pacer for the text streaming right now; one per stream.
    func streamPacer(initiallyShowing count: Int) -> StreamPacer {
        if let stream { return stream }
        let pacer = StreamPacer(shown: count, minimumRate: RevealPacing.streamingRate, catchUp: RevealPacing.streamingCatchUp)
        stream = pacer
        return pacer
    }

    /// A stream ended with nothing claiming it (a turn stopped mid-reply).
    func endStream() { stream = nil }

    func arrival(for id: String, fresh: Bool, animates: Bool) -> ReplyArrival {
        if let pacer = handedOver[id] { return .continuing(pacer) }
        if revealing.contains(id) { return .revealed }
        guard fresh, animates else { return .settled }
        revealUntil = Date().addingTimeInterval(RevealPacing.arrivalDuration + 0.4)
        if let stream {
            handedOver[id] = stream
            self.stream = nil
            return .continuing(stream)
        }
        revealing.insert(id)
        return .revealed
    }

    func isHandedOver(_ id: String) -> Bool { handedOver[id] != nil }
    func isRevealing(_ id: String) -> Bool { revealing.contains(id) }

    func reset() {
        stream = nil
        handedOver = [:]
        revealing = []
        revealUntil = .distantPast
    }
}

private struct ReplyArrivalKey: EnvironmentKey {
    static let defaultValue: ReplyArrival = .settled
}

extension EnvironmentValues {
    var replyArrival: ReplyArrival {
        get { self[ReplyArrivalKey.self] }
        set { self[ReplyArrivalKey.self] = newValue }
    }
}

/// A bot reply's text, revealed according to how it arrived. The pacer is
/// captured once per bubble, so later renders (which see `.settled`) keep
/// the reveal running to the end.
struct ReplyText: View {
    let text: String
    var scrollIdentifier: String? = nil
    var openLink: ((URL) -> OpenURLAction.Result)? = nil
    @StateObject private var pacer: StreamPacer
    @State private var animates: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(text: String, arrival: ReplyArrival, scrollIdentifier: String? = nil, openLink: ((URL) -> OpenURLAction.Result)? = nil) {
        self.text = text
        self.scrollIdentifier = scrollIdentifier
        self.openLink = openLink
        switch arrival {
        case .settled:
            _pacer = StateObject(wrappedValue: StreamPacer(shown: text.count, minimumRate: 0, catchUp: 1))
            _animates = State(initialValue: false)
        case .revealed:
            let rate = max(RevealPacing.arrivalRate, Double(text.count) / RevealPacing.arrivalDuration)
            // A huge catch-up window makes the rate constant: the whole reply
            // lands in `arrivalDuration` (or sooner, if it is short).
            _pacer = StateObject(wrappedValue: StreamPacer(shown: 0, minimumRate: rate, catchUp: 1_000))
            _animates = State(initialValue: true)
        case let .continuing(stream):
            _pacer = StateObject(wrappedValue: stream)
            _animates = State(initialValue: true)
        }
    }

    var body: some View {
        RevealedMarkdown(text: text, pacer: pacer, scrollIdentifier: scrollIdentifier, openLink: openLink)
            .onAppear { advance(to: text.count) }
            .onValueChange(of: text.count) { advance(to: $0) }
    }

    private func advance(to count: Int) {
        pacer.update(target: count, breathing: false, animated: animates && !reduceMotion && !UIAccessibility.isVoiceOverRunning)
    }
}

/// The transcript fold, kept until its inputs change. The chat's body runs
/// on every keystroke and on every caption of a Live call; the fold walks
/// every message (plans, runs, digests) and its result changes only with the
/// messages, the activity detail and the summaries setting. Arrays from the
/// store compare cheaply: unchanged messages share their storage.
@MainActor
final class TranscriptMemo {
    private var messages: [Message]?
    private var detail: ActivityDetail?
    private var showSummaries = false
    private var cached: [TranscriptRow] = []

    func rows(for messages: [Message], detail: ActivityDetail, showSummaries: Bool) -> [TranscriptRow] {
        if detail == self.detail, showSummaries == self.showSummaries, messages == self.messages {
            return cached
        }
        self.messages = messages
        self.detail = detail
        self.showSummaries = showSummaries
        cached = transcriptRows(messages, detail: detail, showSummaries: showSummaries)
        return cached
    }
}

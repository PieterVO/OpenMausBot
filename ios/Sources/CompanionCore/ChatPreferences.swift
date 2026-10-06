// The parts of the chat's preferences that are logic rather than screen:
// how much of a bot's working-out the transcript shows, and what sits on
// the composer's chip row.
//
// These live in the core, away from SwiftUI, because they are the pieces
// worth testing — the views that read them are wiring.
import Foundation

/// How much of a bot's activity the transcript shows.
public enum ActivityDetail: String, CaseIterable, Codable, Sendable {
    /// Every chip, as the harness sent them.
    case full
    /// Consecutive chips fold into one summary; failures never fold.
    case reduced
    /// No activity chips at all.
    case hidden

    /// What a phone starts with until the reader chooses: the phone should
    /// read like a normal chat (Omkar, 2026-10-03), and the desktop likewise
    /// hides tool calls until they are switched on. A stored choice wins.
    public static let phoneDefault: ActivityDetail = .hidden

    public var label: String {
        switch self {
        case .full: "Full"
        case .reduced: "Reduced"
        case .hidden: "Hidden"
        }
    }

    public var caption: String {
        switch self {
        case .full: "Every step a bot takes."
        case .reduced: "Steps fold into one line. Failures always show."
        case .hidden: "No activity, only messages."
        }
    }
}

/// When the island plays a bot's face on opening a chat.
public enum IslandIntro: String, CaseIterable, Codable, Sendable {
    case always
    /// The first time each bot is opened, then never again for that bot.
    case oncePerBot
    case never

    public var label: String {
        switch self {
        case .always: "Always"
        case .oncePerBot: "First time per bot"
        case .never: "Never"
        }
    }
}

/// One chip on the composer's quick-reply row.
public struct QuickReply: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var title: String
    public var prompt: String
    public var icon: String

    public init(id: String = UUID().uuidString, title: String, prompt: String, icon: String) {
        self.id = id
        self.title = title
        self.prompt = prompt
        self.icon = icon
    }

    /// The four the composer shipped with, kept as the reset target.
    public static let defaults: [QuickReply] = [
        QuickReply(id: "default.diff", title: "Show diff", prompt: "Show latest git diff", icon: "arrow.triangle.pull"),
        QuickReply(id: "default.tests", title: "Run tests", prompt: "Run all automated tests", icon: "checkmark.seal"),
        QuickReply(id: "default.explain", title: "Explain steps", prompt: "Explain the changes in detail", icon: "text.bubble"),
        QuickReply(id: "default.next", title: "What's next?", prompt: "What should we do next?", icon: "sparkles"),
    ]

    /// Icons offered by the editor. Small on purpose: a full symbol browser
    /// is a different feature, and twelve covers what a chip is ever for.
    public static let iconChoices = [
        "sparkles", "arrow.triangle.pull", "checkmark.seal", "text.bubble",
        "hammer", "ladybug", "doc.text", "terminal",
        "paperplane", "magnifyingglass", "clock.arrow.circlepath", "list.bullet",
    ]

    public static func encode(_ replies: [QuickReply]) -> String {
        guard let data = try? JSONEncoder().encode(replies) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    /// Anything unreadable becomes the defaults. An empty *string* is a
    /// store that has never been written; an empty *list* is a row the user
    /// deliberately cleared, and those must not mean the same thing.
    public static func decode(_ json: String) -> [QuickReply] {
        guard !json.isEmpty, let data = json.data(using: .utf8) else { return defaults }
        guard let decoded = try? JSONDecoder().decode([QuickReply].self, from: data) else { return defaults }
        let ids = decoded.map { $0.id.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard ids.allSatisfy({ !$0.isEmpty }), Set(ids).count == ids.count else { return defaults }
        return decoded
    }
}

/// A completed turn's intermediate replies. The terminal answer stays outside.
public struct AssistantTurnFold: Hashable, Sendable {
    public let turnId: String
    public let messages: [Message]
    public let elapsed: Double

    public var label: String {
        guard elapsed >= 1_000 else { return "Worked" }
        let seconds = Int(elapsed / 1_000)
        let duration = seconds < 60 ? "\(seconds)s" : "\(seconds / 60)m \(String(format: "%02d", seconds % 60))s"
        return "Worked for \(duration)"
    }
}

/// A transcript entry: a message, a run of tools, or completed narration.
public enum TranscriptRow: Identifiable, Hashable, Sendable {
    case message(Message)
    case activityRun([Message])
    case assistantTurn(AssistantTurnFold)
    case plan(id: String, message: Message, plan: TodoPlan)

    /// The message a row answers for. A plan answers for its latest update,
    /// while its stable id keeps it at the first update's transcript position.
    public var head: Message {
        switch self {
        case let .message(message): message
        case let .activityRun(items): items[0]
        case let .assistantTurn(turn): turn.messages[0]
        case let .plan(_, message, _): message
        }
    }

    public var id: String {
        switch self {
        case let .message(message): message.id
        case let .activityRun(items): "run.\(items[0].id)"
        case let .assistantTurn(turn): "turn.\(turn.turnId)"
        case let .plan(id, _, _): id
        }
    }

    public var at: Double { head.at }
    /// The last timestamp covered by this row. Date separators compare the
    /// next row with this value so a folded run cannot manufacture a gap.
    public var endAt: Double {
        switch self {
        case let .message(message): message.at
        case let .activityRun(items): items.last?.at ?? head.at
        case let .assistantTurn(turn): turn.messages.last?.at ?? head.at
        case let .plan(_, message, _): message.at
        }
    }
    public var role: Message.Role { head.role }
    public var kind: Message.Kind { head.kind }
    public var senderName: String? { head.from?.name }
}

/// The one line a roster row shows under a chat's name.
///
/// Folded by the same rule as the transcript, and for the same reason: a
/// reader who has turned activity off has said they do not want to see tool
/// calls, and the roster is where they see the most of them — one per chat,
/// on the screen they spend the most time on. Reading the preview off the
/// raw last message made "Hidden" mean "hidden in one place".
public func rosterPreview(_ messages: [Message], detail: ActivityDetail) -> String {
    // Cards change conversation semantics, not the roster's activity policy.
    // A plan-bearing activity still hides here when tool detail is Hidden.
    let messages = messages.filter {
        $0.kind != .digest && (detail != .hidden || ($0.kind != .activity && $0.kind != .compaction) || isStatusNotice($0) || isFailedTurn($0))
    }
    // The card stays at its first update's position. A later update can still
    // be the newest activity for a preview, even after an intervening reply.
    if let last = messages.last, last.kind == .activity, let tool = last.tool {
        let name = tool.name.lowercased()
        if TodoPlan.parse(tool: tool) != nil ||
            ((name.hasSuffix("taskcreate") || name.hasSuffix("taskupdate")) && planStates(messages)[last.id] != nil) {
            return previewText(of: last)
        }
    }
    guard let last = transcriptRows(messages, detail: detail).last else { return "" }
    switch last {
    case let .message(message):
        return previewText(of: message)
    case let .activityRun(items):
        let running = items.contains { $0.tool?.ok == nil }
        return "\(running ? "Running" : "Ran") \(items.count) steps"
    case let .assistantTurn(turn):
        return turn.label
    case let .plan(_, message, _):
        return previewText(of: message)
    }
}

/// What a single message reads as in a roster row.
func previewText(of message: Message) -> String {
    switch message.kind {
    case .text:
        if let task = message.webhookContent?.task { return task }
        if let text = message.text, !text.isEmpty { return text }
        // A bot that only sent a file says so by its name.
        return message.attachedFiles.first?.name ?? ""
    // a pending card's question is the preview, in its short form; the
    // roster row already says "waiting on you" beside it
    case .options:
        guard let card = message.card else { return "" }
        return card.isPending ? card.previewLine : card.headline
    case .secret:
        return message.secret?.label ?? message.text ?? "Credential required"
    case .activity: return message.tool?.label ?? ""
    case .screen: return "Screenshot"
    case .digest: return ""
    case .compaction: return message.compaction?.chipText ?? message.text ?? ""
    case .routineRun: return message.routineRun?.previewLine ?? message.text ?? ""
    case .unknown: return message.text ?? ""
    }
}

/// A status row the server writes while a turn runs ("notice: Qwen hit a
/// rate limit and is retrying"). It tells the reader what the bot is doing,
/// so, like desktop's statusActivity, it is never hidden or folded with the
/// tool chips.
public func isStatusNotice(_ message: Message) -> Bool {
    message.kind == .activity && (message.tool?.name.hasPrefix("notice:") ?? false)
}

/// A turn that failed is stored as an activity row named "error: <what went
/// wrong>" (shared/failed-turn.ts on the computer). The cause without that
/// marker; nil for any other row. The chip and the roster preview both read
/// it here, so neither shows the marker.
public func failedTurnCause(_ name: String) -> String? {
    guard name.hasPrefix("error:") else { return nil }
    return name.dropFirst("error:".count).trimmingCharacters(in: .whitespaces)
}

/// A failed turn's row. Like a status notice it is never hidden: it is the
/// only sign the bot did not answer, and desktop always shows it too.
public func isFailedTurn(_ message: Message) -> Bool {
    message.kind == .activity && message.tool.flatMap { failedTurnCause($0.name) } != nil
}

extension ToolActivity {
    /// What the chip and the roster say: a failed turn's cause, or the step.
    public var label: String { failedTurnCause(name) ?? name }
}

/// Tool and context receipts follow activity detail. Plans and digests have
/// their own conversation policy and are handled before this hiding rule.
public func isActivityReceipt(_ message: Message) -> Bool {
    switch message.kind {
    case .activity:
        guard let tool = message.tool else { return true }
        let name = tool.name.lowercased()
        return TodoPlan.parse(tool: tool) == nil && !name.hasSuffix("taskcreate") && !name.hasSuffix("taskupdate")
    case .compaction: return true
    default: return false
    }
}

/// The messages a bot has written so far in the turn it is still working on,
/// before any of them is known to be its answer.
public struct LiveNarration: Equatable, Sendable {
    /// Rows the transcript leaves out while the turn runs.
    public let hiddenIds: Set<String>
    /// The newest of them: the one grey status line shown instead.
    public let latest: String?

    public static let none = LiveNarration(hiddenIds: [], latest: nil)
}

/// At Hidden, a working bot's in-between messages ("Let me check the logs")
/// are one grey status line rather than a pile of bubbles (Omkar, 2026-10-03).
///
/// Only the turn answering the latest message, only while the bot works, and
/// only until the server marks the turn's final reply: then the turn folds
/// into its "Worked for" row as at every other level. A turn that ends
/// without that mark (an older desktop, a crash) is no longer busy, so its
/// messages show as bubbles and nothing it said is lost.
public func liveNarration(_ messages: [Message], busy: Bool, detail: ActivityDetail) -> LiveNarration {
    guard busy, detail == .hidden else { return .none }
    let lastUser = messages.lastIndex { $0.role == .user }
    let recent = messages[(lastUser.map { $0 + 1 } ?? 0)...]
    let said = recent.filter { $0.role == .bot && $0.kind == .text && !($0.turnId ?? "").isEmpty }
    guard let turn = said.last?.turnId else { return .none }
    let narration = said.filter { $0.turnId == turn }
    guard !narration.contains(where: { $0.turnTerminal == true }) else { return .none }
    return LiveNarration(hiddenIds: Set(narration.map(\.id)), latest: narration.last?.text)
}

/// Folds a transcript to the requested level of detail.
///
/// A failed step is never folded away: the reason to turn activity down is
/// the successful noise, and losing the one chip that says something went
/// wrong would make `reduced` a worse default than `full`.
///
/// Plans remain visible at every detail level. Work summaries are independent
/// of tool detail: off by default, but always available after a problem.
public func transcriptRows(_ messages: [Message], detail: ActivityDetail, showSummaries: Bool = false) -> [TranscriptRow] {
    enum PlanGroup: Hashable {
        case turn(String)
        case afterUser(String?)
    }
    let states = planStates(messages)
    var planGroups: [PlanGroup: (firstID: String, message: Message, plan: TodoPlan)] = [:]
    var latestUserID: String?
    for message in messages {
        if message.role == .user { latestUserID = message.id }
        guard let plan = states[message.id] else { continue }
        let group = message.turnId.map(PlanGroup.turn) ?? .afterUser(latestUserID)
        let firstID = planGroups[group]?.firstID ?? message.id
        planGroups[group] = (firstID, message, plan)
    }
    var plans: [String: TranscriptRow] = [:]
    for group in planGroups.values {
        plans[group.firstID] = .plan(id: "plan.\(group.firstID)", message: group.message, plan: group.plan)
    }
    // Fold only explicitly completed turns; never guess that the last reply
    // is final on an older server or while the bot is still working.
    var narration: [String: [Message]] = [:]
    var startedAt: [String: Double] = [:]
    var lastUserAt: Double?
    var folds: [String: AssistantTurnFold] = [:]
    var hiddenIDs = Set<String>()
    for message in messages {
        if message.role == .user { lastUserAt = message.at }
        guard message.role == .bot, message.kind == .text,
              let turnID = message.turnId, !turnID.isEmpty else { continue }
        if message.turnTerminal == true {
            guard let items = narration.removeValue(forKey: turnID), !items.isEmpty else { continue }
            folds[items[0].id] = AssistantTurnFold(
                turnId: turnID, messages: items,
                elapsed: max(0, message.at - (startedAt[turnID] ?? items[0].at))
            )
            hiddenIDs.formUnion(items.map(\.id))
        } else {
            if narration[turnID] == nil { startedAt[turnID] = lastUserAt ?? message.at }
            narration[turnID, default: []].append(message)
        }
    }

    var rows: [TranscriptRow] = []
    var run: [Message] = []

    // A run of one is not worth a summary — emit the chip itself.
    func flush() {
        if run.count > 1 {
            rows.append(.activityRun(run))
        } else {
            rows.append(contentsOf: run.map(TranscriptRow.message))
        }
        run.removeAll()
    }

    for message in messages {
        if let turn = folds[message.id] {
            flush()
            rows.append(.assistantTurn(turn))
            continue
        }
        if hiddenIDs.contains(message.id) { continue }
        if detail != .full, message.card?.leavesTranscriptWhenSettled == true { continue }
        if states[message.id] != nil {
            // Even later updates that do not emit a second card break runs.
            flush()
            if let row = plans[message.id] { rows.append(row) }
            continue
        }
        if message.kind == .digest {
            flush()
            // Covers the empty digest too: a turn that touched nothing has
            // nothing to show, and an empty row would still cost a gap.
            if shouldShowDigest(message, showSummaries: showSummaries) {
                rows.append(.message(message))
            }
            continue
        }
        if detail == .hidden && (message.kind == .activity || message.kind == .compaction) && !isStatusNotice(message) && !isFailedTurn(message) { continue }
        if detail != .reduced {
            rows.append(.message(message))
            continue
        }
        guard message.kind == .activity else {
            flush()
            rows.append(.message(message))
            continue
        }
        if message.tool?.ok == false || isStatusNotice(message) {
            flush()
            rows.append(.message(message))
            continue
        }
        run.append(message)
    }
    flush()
    return rows
}

// A plan is conversation content, even when the tool machinery is hidden.
// Snapshot tools and Claude Tasks share one thread-local fold.
import Foundation
import CoreFoundation

public struct TodoItem: Hashable, Identifiable, Sendable {
    public enum Status: String, Hashable, Sendable {
        case pending, active, done, cancelled

        public static func normalize(_ value: String) -> Status {
            switch value.lowercased().replacingOccurrences(of: "-", with: "_").replacingOccurrences(of: " ", with: "_") {
            case "completed", "complete", "done", "finished": .done
            case "in_progress", "active", "running", "doing", "current": .active
            case "cancelled", "canceled", "skipped", "deleted": .cancelled
            default: .pending
            }
        }
    }

    public var id: String
    public var text: String
    public var activeText: String?
    public var status: Status

    public init(id: String, text: String, activeText: String? = nil, status: Status = .pending) {
        self.id = id
        self.text = text
        self.activeText = activeText
        self.status = status
    }
}

public struct TodoPlan: Hashable, Sendable {
    public var items: [TodoItem]
    public var truncated: Bool
    public var done: Int { items.reduce(0) { $0 + ($1.status == .done ? 1 : 0) } }
    public var total: Int { items.reduce(0) { $0 + ($1.status != .cancelled ? 1 : 0) } }
    public var active: TodoItem? { items.first { $0.status == .active } }
    public var isFinished: Bool {
        let total = total
        return total > 0 && done == total
    }

    public init(items: [TodoItem], truncated: Bool = false) {
        self.items = items
        self.truncated = truncated
    }

    public static func parse(tool: ToolActivity) -> TodoPlan? {
        guard let input = planObject(tool.input) else { return nil }
        let array: [Any]
        let textKeys: [String]
        if let todos = input["todos"] as? [Any] {
            array = todos
            textKeys = ["content", "subject", "title", "text", "description"]
        } else if let plan = input["plan"] as? [Any] {
            array = plan
            textKeys = ["step", "content", "text"]
        } else if let entries = input["entries"] as? [Any] {
            array = entries
            textKeys = ["content"]
        } else if tool.name.lowercased().contains("todo"), let list = input["items"] as? [Any] {
            array = list
            textKeys = ["text", "content", "subject", "title", "description"]
        } else {
            return nil
        }
        var items: [TodoItem] = []
        var truncated = false
        for (index, entry) in array.enumerated() {
            if entry is String { truncated = true; continue }
            guard let fields = entry as? [String: Any],
                  let text = firstString(fields, keys: textKeys), !text.isEmpty else { continue }
            let status: TodoItem.Status
            if let raw = fields["status"] as? String {
                status = .normalize(raw)
            } else if let completed = planBool(fields["completed"]) ?? planBool(fields["done"]) {
                status = completed ? .done : .pending
            } else {
                status = .pending
            }
            items.append(TodoItem(id: String(index), text: text,
                                  activeText: firstString(fields, keys: ["activeForm", "active_form"]), status: status))
        }
        return items.isEmpty ? nil : TodoPlan(items: items, truncated: truncated)
    }
}

/// Pass one thread's transcript in transcript order. State is local to this
/// invocation, so paging/rebuilding one thread cannot mutate another's plan.
public func planStates(_ messages: [Message]) -> [String: TodoPlan] {
    var plan = TodoPlan(items: [])
    var highestID = "0"
    var states: [String: TodoPlan] = [:]
    func observeID(_ value: String) {
        if let id = numericID(value), id.count > highestID.count || (id.count == highestID.count && id > highestID) {
            highestID = id
        }
    }
    for message in messages {
        guard message.kind == .activity, let tool = message.tool else { continue }
        if let snapshot = TodoPlan.parse(tool: tool) {
            plan = snapshot
            for item in snapshot.items { observeID(item.id) }
        } else if tool.name.lowercased().hasSuffix("taskcreate") {
            guard let input = planObject(tool.input),
                  let text = firstString(input, keys: ["subject", "description"]), !text.isEmpty else { continue }
            let id = taskCreatedID(tool.output) ?? nextNumericID(highestID)
            plan.items.append(TodoItem(id: id, text: text, activeText: firstString(input, keys: ["activeForm", "active_form"])))
            observeID(id)
        } else if tool.name.lowercased().hasSuffix("taskupdate") {
            guard let input = planObject(tool.input), let id = taskID(input["taskId"]),
                  let index = plan.items.firstIndex(where: { $0.id == id }) else { continue }
            if let raw = input["status"] as? String {
                if raw.lowercased() == "deleted" {
                    plan.items.remove(at: index)
                    states[message.id] = plan
                    continue
                }
                plan.items[index].status = .normalize(raw)
            }
            if let text = firstString(input, keys: ["subject"]), !text.isEmpty { plan.items[index].text = text }
            if let text = firstString(input, keys: ["activeForm", "active_form"]) { plan.items[index].activeText = text }
        } else {
            continue
        }
        states[message.id] = plan
    }
    return states
}

private func planObject(_ input: String?) -> [String: Any]? {
    guard let input, let data = input.data(using: .utf8),
          let value = try? JSONSerialization.jsonObject(with: data) else { return nil }
    return value as? [String: Any]
}

private func firstString(_ object: [String: Any], keys: [String]) -> String? {
    keys.lazy.compactMap { object[$0] as? String }.first?.trimmingCharacters(in: .whitespacesAndNewlines)
}

private func planBool(_ value: Any?) -> Bool? {
    guard let number = value as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { return nil }
    return number.boolValue
}

private func taskID(_ value: Any?) -> String? {
    if let text = value as? String, !text.isEmpty { return text }
    guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
          numericID(number.stringValue) != nil else { return nil }
    return number.stringValue
}

private let taskNumber = try! NSRegularExpression(pattern: #"#(\d+)"#)

private func taskCreatedID(_ output: String?) -> String? {
    if let task = planObject(output)?["task"] as? [String: Any], let id = taskID(task["id"]) { return id }
    guard let output, let match = taskNumber.firstMatch(in: output, range: NSRange(output.startIndex..., in: output)),
          let range = Range(match.range(at: 1), in: output) else { return nil }
    return String(output[range])
}

// Decimal strings keep the fallback correct even for an untrusted id larger
// than Int.max. Deletions and snapshots never lower the high-water mark.
private func numericID(_ value: String) -> String? {
    guard !value.isEmpty, value.utf8.allSatisfy({ $0 >= 48 && $0 <= 57 }) else { return nil }
    let significant = value.drop(while: { $0 == "0" })
    return significant.isEmpty ? "0" : String(significant)
}

private func nextNumericID(_ value: String) -> String {
    var digits = Array(value.utf8)
    for index in digits.indices.reversed() {
        if digits[index] < 57 {
            digits[index] += 1
            return String(decoding: digits, as: UTF8.self)
        }
        digits[index] = 48
    }
    return "1" + String(decoding: digits, as: UTF8.self)
}

// Category matching is ordered: a task's file edits are still a plan step,
// and a browser search is still browsing rather than a filesystem search.
import Foundation

public enum ToolCategory: String, CaseIterable, Hashable, Sendable {
    case shell, read, edit, search, web, browser, computer, memory, message, plan, image, mail, calendar, other

    public var symbolName: String {
        switch self {
        case .shell: "terminal"
        case .read: "doc.text"
        case .edit: "pencil"
        case .search: "magnifyingglass"
        case .web: "globe"
        case .browser: "safari"
        case .computer: "display"
        case .memory: "bookmark"
        case .message: "bubble.left.and.bubble.right"
        case .plan: "checklist"
        case .image: "photo"
        case .mail: "envelope"
        case .calendar: "calendar"
        case .other: "wrench.and.screwdriver"
        }
    }
}

private let categoryMatches: [(ToolCategory, [String])] = [
    (.plan, ["todo", "taskcreate", "taskupdate", "tasklist", "update_plan"]),
    (.web, ["websearch", "web_search", "webfetch", "web_fetch", "fetch", "http", "url"]),
    (.browser, ["browser", "playwright", "navigate"]),
    (.computer, ["computer", "screenshot", "click", "type_text", "press_key", "scroll", "desktop", "screen"]),
    (.memory, ["memory"]),
    (.message, ["ask_bot", "messaged", "message", "delegat", "send_message", "slack"]),
    (.mail, ["gmail", "mail", "email"]),
    (.calendar, ["calendar"]),
    (.image, ["image", "photo"]),
    (.edit, ["edit", "write", "apply_patch", "patch", "create_file", "str_replace", "multiedit", "filechange"]),
    (.search, ["grep", "glob", "search", "find", "list_dir", "ls"]),
    (.read, ["read", "view", "cat", "open_file"]),
    (.shell, ["shell", "bash", "zsh", "terminal", "exec", "command", "powershell", "cmd", "sh "]),
]

public func toolCategory(_ name: String) -> ToolCategory {
    let lower = name.lowercased()
    for (category, fragments) in categoryMatches where fragments.contains(where: lower.contains) {
        return category
    }
    return .other
}

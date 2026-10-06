import XCTest
@testable import CompanionCore

final class ToolCategoryTests: XCTestCase {
    func testEveryNamedCategoryFragmentMatchesCaseInsensitively() {
        let categories: [(ToolCategory, [String])] = [
            (.plan, ["todo", "TaskCreate", "TaskUpdate", "TaskList", "update_plan"]),
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
            (.shell, ["shell", "Bash", "zsh", "terminal", "exec", "command", "powershell", "cmd"]),
        ]
        for (expected, fragments) in categories {
            for fragment in fragments {
                XCTAssertEqual(toolCategory("MCP__\(fragment.uppercased())"), expected, fragment)
            }
        }
        XCTAssertEqual(toolCategory("unknown_future_tool"), .other)
        XCTAssertEqual(toolCategory(""), .other)
    }

    func testFirstCategoryMatchWinsInSpecifiedOrder() {
        let ordered: [ToolCategory] = [.plan, .web, .browser, .computer, .memory, .message, .mail, .calendar, .image, .edit, .search, .read, .shell]
        let names = ["todo", "websearch", "browser", "computer", "memory", "message", "mail", "calendar", "image", "edit", "grep", "read", "bash"]
        for first in names.indices {
            for second in names.indices where second > first {
                XCTAssertEqual(toolCategory(names[first] + "_" + names[second]), ordered[first])
            }
        }
        XCTAssertEqual(toolCategory("TodoWrite"), .plan)
        XCTAssertEqual(toolCategory("browser_search"), .browser)
        XCTAssertEqual(toolCategory("memory_write"), .memory)
        XCTAssertEqual(toolCategory("calendar_read"), .calendar)
        XCTAssertEqual(toolCategory("screenshot_image"), .computer)
    }

    func testEveryCategoryUsesTheSpecifiedSFSymbol() {
        let expected: [ToolCategory: String] = [
            .shell: "terminal", .read: "doc.text", .edit: "pencil", .search: "magnifyingglass", .web: "globe",
            .browser: "safari", .computer: "display", .memory: "bookmark", .message: "bubble.left.and.bubble.right",
            .plan: "checklist", .image: "photo", .mail: "envelope", .calendar: "calendar", .other: "wrench.and.screwdriver",
        ]
        XCTAssertEqual(expected.count, ToolCategory.allCases.count)
        for category in ToolCategory.allCases { XCTAssertEqual(category.symbolName, expected[category]) }
    }
}

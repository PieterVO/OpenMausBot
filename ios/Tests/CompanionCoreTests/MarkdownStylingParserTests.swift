import XCTest
@testable import CompanionCore

final class MarkdownStylingParserTests: XCTestCase {
    func testStyledReplyKeepsMixedBlocksAndInlineActionsInOrder() {
        let source = """
        # A smaller launch

        Ready for **review** and `ship()`.

        - Keep the [spec](https://example.test/spec) open
        10. [x] Check the release
        - [ ] Send the notes

        > Hold the rollout until review.

        ```swift
        let columns = "name|count"
            ship()
        ```

        | Item | Count | Owner |
        | :--- | ---: | :---: |
        | `name|count` | 12.50 | [Sam](https://example.test/sam) |

        Read [the report](https://example.test/report) next.
        """
        XCTAssertEqual(Markdown.blocks(source), [
            .heading(level: 1, text: "A smaller launch"),
            .paragraph("Ready for **review** and `ship()`."),
            .bullet(indent: 0, text: "Keep the [spec](https://example.test/spec) open"),
            .task(indent: 0, number: 10, checked: true, text: "Check the release"),
            .task(indent: 0, number: nil, checked: false, text: "Send the notes"),
            .quote("Hold the rollout until review."),
            .code(language: "swift", text: "let columns = \"name|count\"\n    ship()"),
            .table(MarkdownTable(
                headers: ["Item", "Count", "Owner"],
                alignments: [.leading, .trailing, .center],
                rows: [["`name|count`", "12.50", "[Sam](https://example.test/sam)"]]
            )),
            .paragraph("Read [the report](https://example.test/report) next."),
        ])
    }

    func testStreamingFenceDoesNotConsumeEarlierListOrTable() {
        let source = """
        - [x] Read the numbers

        Metric | Value
        --- | ---:
        Calls | 3

        ```sh
        printf '%s\\n' "ready|waiting"
        """
        XCTAssertEqual(Markdown.blocks(source), [
            .task(indent: 0, number: nil, checked: true, text: "Read the numbers"),
            .table(MarkdownTable(
                headers: ["Metric", "Value"],
                alignments: [.leading, .trailing],
                rows: [["Calls", "3"]]
            )),
            .code(language: "sh", text: "printf '%s\\n' \"ready|waiting\""),
        ])
    }
}

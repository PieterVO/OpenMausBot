// The pure half of how a streaming reply reaches the screen: the reveal's
// pace, half-typed Markdown, and the one-line status at Hidden.
import XCTest
@testable import CompanionCore

final class StreamRevealTests: XCTestCase {
    // MARK: - Pacing

    func testABurstIsAbsorbedWithinTheCatchUpWindow() {
        var shown = 0
        var carry = 0.0
        var elapsed = 0.0
        while shown < 120 {
            (shown, carry) = RevealPacing.step(shown: shown, target: 120, carry: carry, elapsed: 1.0 / 60,
                                               minimumRate: RevealPacing.streamingRate, catchUp: RevealPacing.streamingCatchUp)
            elapsed += 1.0 / 60
            XCTAssertLessThan(elapsed, 2, "the reveal must finish")
        }
        // backlog / catchUp falls as it drains, so a few frames past the
        // window at the floor rate; never the 2s a fixed 60 chars/s would take.
        XCTAssertLessThan(elapsed, 0.9)
    }

    func testASmallBacklogStillMovesAtTheFloorRate() {
        let step = RevealPacing.step(shown: 0, target: 3, carry: 0, elapsed: 0.02,
                                     minimumRate: 60, catchUp: 0.35)
        // 3 / 0.35 ≈ 8.6 chars/s would take a third of a second per char;
        // the floor keeps short stretches moving.
        XCTAssertEqual(step.shown, 1)
        XCTAssertEqual(step.carry, 0.2, accuracy: 0.0001)
    }

    func testTheRevealNeverOvershootsAndForgetsItsCarryWhenDone() {
        let step = RevealPacing.step(shown: 98, target: 100, carry: 0.9, elapsed: 1, minimumRate: 60, catchUp: 0.35)
        XCTAssertEqual(step.shown, 100)
        XCTAssertEqual(step.carry, 0)
    }

    func testAShorterTargetSnapsBack() {
        // A branch switch replaced the text with a shorter one.
        XCTAssertEqual(RevealPacing.step(shown: 50, target: 10, carry: 0.4, elapsed: 0.016, minimumRate: 60, catchUp: 0.35).shown, 10)
    }

    // MARK: - Half-typed Markdown

    func testAnOpenBoldSpanIsClosedSoItsWordsReadBold() {
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("- **Linden & Sa"), "- **Linden & Sa**")
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("- **Linden & Salt**: small"), "- **Linden & Salt**: small")
    }

    func testAnOpenerWithNothingAfterItIsHeldBack() {
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("Try **"), "Try ")
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("Run `"), "Run ")
    }

    func testAnOpenCodeSpanClosesBeforeTheBoldAroundIt() {
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("**see `notes"), "**see `notes`**")
    }

    func testAsterisksInsideCodeAreNotBold() {
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("use `a ** b"), "use `a ** b`")
    }

    func testEarlierLinesAndOpenFencesAreLeftAlone() {
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("a **b\nnext"), "a **b\nnext")
        XCTAssertEqual(MarkdownPartial.closingOpenSpans("```swift\nlet **x"), "```swift\nlet **x")
    }

    // MARK: - Tables reveal a row at a time

    private let table = "Spots:\n| Place | Walk |\n| --- | --- |\n| Linden | 6 min |\n| Ferry | 11 min |\nDone."

    func testAHeaderWaitsForItsDelimiterRow() {
        // Still arriving, the delimiter row incomplete: the header waits.
        XCTAssertEqual(MarkdownPartial.revealedPrefix("Spots:\n| Place | Walk |\n| --", count: 30, final: false), "Spots:\n")
        XCTAssertEqual(MarkdownPartial.revealedPrefix("Spots:\n| Place | Wa", count: 20, final: false), "Spots:\n")
    }

    func testAHeaderShowsWithItsDelimiterOnceBothExist() {
        // Settled text: entering the header reveals header and delimiter together.
        XCTAssertEqual(MarkdownPartial.revealedPrefix(table, count: 12, final: true), "Spots:\n| Place | Walk |\n| --- | --- |")
        let arrived = "Spots:\n| Place | Walk |\n| --- | --- |\n"
        XCTAssertEqual(MarkdownPartial.revealedPrefix(arrived, count: arrived.count, final: false), arrived)
    }

    func testABodyRowAppearsWhole() {
        let into = "Spots:\n| Place | Walk |\n| --- | --- |\n| Lin".count
        XCTAssertEqual(MarkdownPartial.revealedPrefix(table, count: into, final: true),
                       "Spots:\n| Place | Walk |\n| --- | --- |\n| Linden | 6 min |")
        // A row still arriving stays hidden until its newline.
        XCTAssertEqual(MarkdownPartial.revealedPrefix("| A |\n| - |\n| Lin", count: 15, final: false), "| A |\n| - |\n")
    }

    func testTextOutsideTablesIsCutWhereTheRevealIs() {
        XCTAssertEqual(MarkdownPartial.revealedPrefix(table, count: 3, final: true), "Spo")
        XCTAssertEqual(MarkdownPartial.revealedPrefix(table, count: table.count, final: true), table)
    }

    // MARK: - One plain status line

    func testTheStatusLineReadsWordsNotMarkdown() {
        let reply = """
        Found three spots:

        - **Linden & Salt**: small plates
        | Place | Walk |
        | --- | --- |
        | Linden & Salt | 6 min |
        ## Next
        Use `notes.md`.
        """
        XCTAssertEqual(
            MarkdownPlain.line(reply),
            "Found three spots: Linden & Salt: small plates Place, Walk Linden & Salt, 6 min Next Use notes.md."
        )
    }
}

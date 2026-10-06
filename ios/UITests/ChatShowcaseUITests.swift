import XCTest

/// Every scene is a bundled Debug fleet. These tests never pair, create an
/// API client, or touch a real computer; attachments are native screenshots.
final class ChatShowcaseUITests: XCTestCase {
    @MainActor
    func testPosterAndPlan() {
        let app = launch(target: "chat-poster")
        XCTAssertTrue(app.buttons["chat-poster"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["chat-poster"].isHittable)
        capture("showcase-top", app)

        launch(app, target: "plan.showcase-plan-first")
        let plan = app.descendants(matching: .any)["plan-plan.showcase-plan-first"]
        XCTAssertTrue(plan.waitForExistence(timeout: 5))
        XCTAssertTrue(plan.label.contains("4 of 4"), plan.label)
        XCTAssertTrue(app.descendants(matching: .any)["plan-plan.showcase-plan-first-item-0"].exists)
        capture("showcase-plan", app)
    }

    @MainActor
    func testStepTimelineAndMarkdown() {
        let app = launch(target: "run.showcase-read")
        let run = app.buttons["step-run-run.showcase-read"]
        XCTAssertTrue(run.waitForExistence(timeout: 5))
        capture("steps-collapsed", app)
        run.tap()
        XCTAssertTrue(app.descendants(matching: .any)["step-showcase-shell"].waitForExistence(timeout: 3))
        capture("steps-expanded", app)
        run.tap()

        launch(app, target: "showcase-reply", bottom: true)
        XCTAssertTrue(app.descendants(matching: .any)["message-showcase-reply-scroll-cell-2-2"].exists)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == %@", "Copy code")).firstMatch.exists)
        capture("table-code-reply", app)
    }

    @MainActor
    func testSummariesAndReplyDetails() {
        let app = launch(target: "showcase-digest-problem", summaries: false)
        let problem = app.buttons["digest-line-showcase-digest-problem"]
        XCTAssertTrue(problem.waitForExistence(timeout: 5), "A failed step always reveals its summary")
        XCTAssertFalse(app.buttons["digest-line-showcase-digest-success"].exists)
        capture("digest-problem", app)
        problem.tap()
        XCTAssertTrue(app.staticTexts["What Pepper did"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["Files"].exists)
        XCTAssertTrue(app.staticTexts["Tools"].exists)
        XCTAssertTrue(app.staticTexts["1 memory change"].exists)
        XCTAssertFalse(app.staticTexts["1 memory changes"].exists)
        capture("digest-sheet", app)
        app.buttons["Done"].tap()

        launch(app, target: "showcase-digest-success", summaries: true)
        XCTAssertTrue(app.buttons["digest-line-showcase-digest-success"].waitForExistence(timeout: 5))
        capture("digest-success", app)

        launch(app, target: "showcase-followup-reply", summaries: false)
        app.staticTexts["Saved. I’ll keep the same gentle pace next weekend, too."].tap()
        XCTAssertTrue(app.descendants(matching: .any)["message-meta-showcase-followup-reply"].waitForExistence(timeout: 3))
        app.buttons["What I did"].tap()
        XCTAssertTrue(app.staticTexts["What Pepper did"].waitForExistence(timeout: 3))
    }

    @MainActor
    func testThinkingRowAndPanel() {
        let app = launch(detail: "full", extra: ["-chat-reasoning-preview"])
        let thinking = app.buttons["thinking-row"]
        XCTAssertTrue(thinking.waitForExistence(timeout: 5))
        capture("thinking-row", app)
        app.swipeUp()
        thinking.tap()
        let panel = app.descendants(matching: .any)["thinking-panel"]
        XCTAssertTrue(panel.waitForExistence(timeout: 3))
        XCTAssertTrue(panel.isHittable, "Opening inline reasoning must bring its panel into the viewport.")
        capture("thinking-panel", app)
    }

    @MainActor
    func testLivePlanStripNavigatesToItsCard() {
        let app = launch(detail: "hidden", extra: ["-chat-showcase-busy-preview"])
        let strip = app.buttons["live-plan-strip"]
        XCTAssertTrue(strip.waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["live-status-line"].exists)
        capture("live-plan-strip", app)
        strip.tap()
        XCTAssertTrue(app.descendants(matching: .any)["plan-plan.showcase-live-plan"].isHittable)
        capture("live-plan-card", app)
    }

    @MainActor
    func testApprovalAndWorkSummariesSetting() {
        let app = launch(target: "showcase-approval")
        XCTAssertTrue(app.buttons["Allow"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Deny"].exists)
        XCTAssertTrue(app.buttons["Always allow this tool"].exists)
        capture("approval-card", app)

        launch(app, summaries: nil, openFirst: false, extra: ["-reset-work-summaries"])
        // Home correctly presents the pending approval's attention island.
        // Dismiss its modal backdrop before navigating the toolbar.
        XCTAssertTrue(app.buttons["Allow"].waitForExistence(timeout: 5))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.75)).tap()
        XCTAssertFalse(app.buttons["Allow"].exists)
        app.buttons["Settings"].tap()
        let toggle = app.switches["work-summaries-toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        if !toggle.isHittable { app.swipeUp() }
        XCTAssertEqual(toggle.value as? String, "0")
        // SwiftUI exposes the label and switch as one accessibility row.
        // Its thumb, not the multi-line explanatory label, is the touch target.
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "1")
        capture("work-summaries-setting", app)
    }

    @MainActor
    func testQuestionCredentialAndRoutineCards() {
        let app = launch(extra: ["-chat-showcase-component", "question"])
        let window = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Window seat")).firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 5))
        capture("question-card", app)
        window.tap()
        XCTAssertTrue(app.buttons["Submit answer"].isEnabled)
        capture("question-selected", app)

        launch(app, extra: ["-chat-showcase-component", "credential"])
        XCTAssertTrue(app.staticTexts["Calendar API key"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Pair again to enter here"].exists)
        capture("credential-card", app)

        launch(app, extra: ["-chat-showcase-component", "routine"])
        XCTAssertTrue(app.staticTexts["Saturday check-in"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Show report"].exists)
        capture("routine-card", app)
        app.buttons["Show report"].tap()
        capture("routine-report", app)
    }

    @MainActor
    func testDiffTeammateReportAndComposerControls() {
        let app = launch(extra: ["-chat-showcase-component", "diff"])
        XCTAssertTrue(app.staticTexts["weekend.md"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Copy Diff"].exists)
        // The existing diff starts expanded; exercise both real states.
        app.buttons["Hide Diff"].tap()
        XCTAssertTrue(app.buttons["View Diff"].waitForExistence(timeout: 3))
        capture("diff-collapsed-and-quick-replies", app)
        app.buttons["View Diff"].tap()
        XCTAssertTrue(app.buttons["Hide Diff"].waitForExistence(timeout: 3))
        capture("diff-expanded", app)
        app.buttons["Slash commands"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "/computer")).firstMatch.exists)
        capture("command-hud", app)

        launch(app, detail: "full", extra: ["-chat-showcase-component", "report"])
        let report = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Teammate report from Quill")).firstMatch
        XCTAssertTrue(report.waitForExistence(timeout: 5))
        report.tap()
        XCTAssertTrue(app.staticTexts["Report"].exists)
        capture("teammate-report", app)
    }

    @MainActor
    @discardableResult
    private func launch(_ existingApp: XCUIApplication? = nil, target: String? = nil, bottom: Bool = false,
                        detail: String = "reduced", summaries: Bool? = true, openFirst: Bool = true,
                        extra: [String] = []) -> XCUIApplication {
        continueAfterFailure = false
        let app = existingApp ?? XCUIApplication()
        app.terminate()
        app.launchArguments = [
            "-store-preview", "-chat-showcase-preview",
            "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
            "-companion.prefs.islandIntro", "never",
            "-companion.prefs.activityDetail", detail,
            "-companion.onboarding.welcomeSeen", "YES",
            "-companion.onboarding.notificationsSeen", "YES"
        ] + extra
        if let summaries {
            app.launchArguments += ["-companion.prefs.showWorkSummaries", summaries ? "YES" : "NO"]
        }
        if openFirst { app.launchArguments.append("-open-first") }
        if let target { app.launchArguments += ["-chat-showcase-target", target] }
        if bottom { app.launchArguments.append("-chat-showcase-bottom") }
        app.launch()
        if openFirst { XCTAssertTrue(app.textFields["message-input"].waitForExistence(timeout: 10)) }
        return app
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}

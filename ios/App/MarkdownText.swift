// Bot replies, rendered.
//
// CompanionCore splits blocks; Foundation owns inline emphasis and links.
// TextKit supplies rounded, padded code spans without replacing their words
// with images, so selection, copying and link actions remain native on iOS 16.
import SwiftUI
import UIKit
import CompanionCore

private struct OptionalIdentifier: ViewModifier {
    let identifier: String?

    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}

struct MarkdownText: View {
    private final class CachedInline: NSObject {
        let text: AttributedString
        init(_ text: AttributedString) { self.text = text }
    }
    private static let inlineCache: NSCache<NSString, CachedInline> = {
        let cache = NSCache<NSString, CachedInline>()
        cache.countLimit = 256
        cache.totalCostLimit = 524_288
        return cache
    }()

    let source: String
    /// The streaming and settled replies share their layout; the caret is
    /// appended to the final block rather than becoming a separate line.
    var caret: Bool = false
    /// Settled bubbles identify the first table as `message-<id>-scroll`.
    var scrollIdentifier: String? = nil
    var openLink: ((URL) -> OpenURLAction.Result)?
    var color: String? = nil

    @Environment(\.botTintColor) private var botTintColor
    @Environment(\.sizeCategory) private var sizeCategory

    init(
        source: String,
        caret: Bool = false,
        scrollIdentifier: String? = nil,
        color: String? = nil,
        openLink: ((URL) -> OpenURLAction.Result)? = nil
    ) {
        self.source = source
        self.caret = caret
        self.scrollIdentifier = scrollIdentifier
        self.color = color
        self.openLink = openLink
    }

    private var tint: Color { BotTint.ink(color ?? botTintColor) }

    var body: some View {
        let blocks = Markdown.blocks(source)
        VStack(alignment: .leading, spacing: 0) {
            let firstTable = blocks.firstIndex { if case .table = $0 { return true }; return false }
            ForEach(Array(blocks.enumerated()), id: \.offset) { item in
                view(
                    for: item.element,
                    tail: caret && item.offset == blocks.count - 1,
                    scrollIdentifier: item.offset == firstTable ? scrollIdentifier : nil
                )
                .padding(.top, spacing(before: item.element, at: item.offset, blocks: blocks))
            }
        }
        .tint(tint)
        .environment(\.openURL, OpenURLAction { url in
            openLink?(url) ?? .systemAction(url)
        })
    }

    private func spacing(before block: MarkdownBlock, at index: Int, blocks: [MarkdownBlock]) -> CGFloat {
        guard index > 0 else { return 0 }
        if case .heading = block { return 12 }
        if case .heading = blocks[index - 1] { return 4 }
        return 8
    }

    @ViewBuilder
    private func view(for block: MarkdownBlock, tail: Bool, scrollIdentifier: String?) -> some View {
        switch block {
        case let .paragraph(text):
            inline(text, tail: tail)

        case let .heading(level, text):
            inline(text, tail: tail, style: level <= 1 ? .title3 : level == 2 ? .headline : .subheadline, weight: .semibold)

        case let .bullet(indent, text):
            marker(number: nil, indent: indent, text: text, tail: tail)

        case let .ordered(indent, number, text):
            marker(number: number, indent: indent, text: text, tail: tail)

        case let .task(indent, number, checked, text):
            taskRow(indent: indent, number: number, checked: checked, text: text, tail: tail)

        case let .table(table):
            tableView(table, tail: tail, scrollIdentifier: scrollIdentifier)

        case let .quote(text):
            HStack(alignment: .top, spacing: 8) {
                RoundedRectangle(cornerRadius: 1)
                    .fill(Color(uiColor: .separator))
                    .frame(width: 2)
                inline(text, tail: tail, muted: true)
            }
            .fixedSize(horizontal: false, vertical: true)

        case let .code(language, text):
            MarkdownCodeBlock(language: language, source: text, caret: tail, color: color ?? botTintColor)

        case .rule:
            Divider().padding(.vertical, 2)
        }
    }

    private func taskRow(indent: Int, number: Int?, checked: Bool, text: String, tail: Bool) -> some View {
        let state = String(localized: checked ? "completed" : "not completed")
        let words = renderedInline(text)
        let label = words.isEmpty ? state : "\(state), \(words)"
        return HStack(alignment: .firstTextBaseline, spacing: 8) {
            if let number {
                Text("\(number).")
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .frame(minWidth: 18, alignment: .trailing)
            }
            ZStack {
                Circle()
                    .fill(checked ? tint : .clear)
                Circle()
                    .strokeBorder(tint, lineWidth: checked ? 0 : 1.5)
                if checked {
                    Image(systemName: "checkmark")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(BotTint.actionLabel(color ?? botTintColor))
                }
            }
            .frame(width: 18, height: 18)
            .alignmentGuide(.firstTextBaseline) { $0[.top] + font(.body).ascender - 2 }
            .accessibilityHidden(true)
            inline(text, tail: tail, muted: checked, struck: checked)
        }
        .padding(.leading, CGFloat(indent) * 14)
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }

    private func marker(number: Int?, indent: Int, text: String, tail: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            if let number {
                Text("\(number).")
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .frame(minWidth: 18, alignment: .trailing)
            } else {
                Circle()
                    .fill(tint)
                    .frame(width: 5, height: 5)
                    .frame(width: 18)
                    .alignmentGuide(.firstTextBaseline) { $0[.top] + font(.body).ascender - 6 }
                    .accessibilityHidden(true)
            }
            inline(text, tail: tail)
        }
        .padding(.leading, CGFloat(indent) * 14)
        .fixedSize(horizontal: false, vertical: true)
    }

    private func tableView(_ table: MarkdownTable, tail: Bool, scrollIdentifier: String?) -> some View {
        let widths = columnWidths(table)
        return MarkdownTableViewport(color: color ?? botTintColor, identifier: scrollIdentifier) {
            Grid(alignment: .leading, horizontalSpacing: 0, verticalSpacing: 0) {
                GridRow {
                    ForEach(Array(table.headers.enumerated()), id: \.offset) { index, header in
                        cell(
                            header,
                            width: widths[index],
                            alignment: table.alignments[index],
                            weight: .semibold,
                            tail: tail && table.rows.isEmpty && index == table.headers.count - 1,
                            identifier: scrollIdentifier.map { "\($0)-cell-0-\(index)" }
                        )
                        // Two inset layers make the header stronger without
                        // introducing another appearance-specific palette.
                        .background(BotTint.inset)
                        .background(BotTint.inset)
                    }
                }
                ForEach(Array(table.rows.enumerated()), id: \.offset) { rowIndex, row in
                    Divider().gridCellColumns(table.headers.count)
                    GridRow {
                        ForEach(Array(row.enumerated()), id: \.offset) { index, value in
                            cell(
                                value,
                                width: widths[index],
                                alignment: table.alignments[index],
                                weight: .regular,
                                tail: tail && rowIndex == table.rows.count - 1 && index == row.count - 1,
                                identifier: scrollIdentifier.map { "\($0)-cell-\(rowIndex + 1)-\(index)" }
                            )
                        }
                    }
                }
            }
        }
    }

    private func cell(
        _ text: String,
        width: CGFloat,
        alignment: MarkdownTableAlignment,
        weight: UIFont.Weight,
        tail: Bool,
        identifier: String?
    ) -> some View {
        inline(text, tail: tail, style: .subheadline, weight: weight, monospacedDigits: true, alignment: alignment)
            .frame(width: width + (tail ? caretWidth : 0), alignment: frameAlignment(alignment))
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(renderedInline(text))
            .modifier(OptionalIdentifier(identifier: identifier))
    }

    private func frameAlignment(_ alignment: MarkdownTableAlignment) -> Alignment {
        switch alignment {
        case .leading: .leading
        case .trailing: .trailing
        case .center: .center
        }
    }

    /// Measure the same styled runs that are drawn, including the padding of
    /// code spans. Dynamic Type must grow the table, not clip its cell labels.
    private func columnWidths(_ table: MarkdownTable) -> [CGFloat] {
        table.headers.indices.map { index in
            var widest = textWidth(table.headers[index], weight: .semibold)
            for row in table.rows where index < row.count {
                widest = max(widest, textWidth(row[index], weight: .regular))
            }
            return max(ceil(widest), 24)
        }
    }

    private var caretWidth: CGFloat {
        ("\u{2007}▍" as NSString).size(withAttributes: [.font: font(.subheadline)]).width
    }

    private func textWidth(_ text: String, weight: UIFont.Weight) -> CGFloat {
        MarkdownInlineText.styled(
            Self.parsedInline(text), font: font(.subheadline, weight: weight, monospacedDigits: true),
            ink: UIColor(tint), muted: false, struck: false, caret: false
        ).size().width
    }

    private func renderedInline(_ text: String) -> String {
        String(Self.parsedInline(text).characters)
    }

    private func font(_ style: UIFont.TextStyle, weight: UIFont.Weight = .regular, monospacedDigits: Bool = false) -> UIFont {
        let traits = UITraitCollection(preferredContentSizeCategory: sizeCategory.uiCategory)
        let preferred = UIFont.preferredFont(forTextStyle: style, compatibleWith: traits)
        if monospacedDigits {
            return UIFont.monospacedDigitSystemFont(ofSize: preferred.pointSize, weight: weight)
        }
        return UIFont.systemFont(ofSize: preferred.pointSize, weight: weight)
    }

    private func inline(
        _ text: String,
        tail: Bool = false,
        style: UIFont.TextStyle = .body,
        weight: UIFont.Weight = .regular,
        muted: Bool = false,
        struck: Bool = false,
        monospacedDigits: Bool = false,
        alignment: MarkdownTableAlignment? = nil
    ) -> some View {
        let font = font(style, weight: weight, monospacedDigits: monospacedDigits)
        return MarkdownInlineText(source: text, font: font, ink: tint, muted: muted, struck: struck, caret: tail, alignment: alignment)
            .alignmentGuide(.firstTextBaseline) { $0[.top] + font.ascender }
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Only parse results are cached: tint and text size can change live.
    fileprivate static func parsedInline(_ text: String) -> AttributedString {
        let key = text as NSString
        if let cached = inlineCache.object(forKey: key) { return cached.text }
        let attributed = (try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        )) ?? AttributedString(text)
        let bytes = text.utf8.count
        if bytes <= 8_192 {
            inlineCache.setObject(CachedInline(attributed), forKey: key, cost: bytes * 4)
        }
        return attributed
    }
}

private extension ContentSizeCategory {
    var uiCategory: UIContentSizeCategory {
        switch self {
        case .extraSmall: .extraSmall
        case .small: .small
        case .medium: .medium
        case .large: .large
        case .extraLarge: .extraLarge
        case .extraExtraLarge: .extraExtraLarge
        case .extraExtraExtraLarge: .extraExtraExtraLarge
        case .accessibilityMedium: .accessibilityMedium
        case .accessibilityLarge: .accessibilityLarge
        case .accessibilityExtraLarge: .accessibilityExtraLarge
        case .accessibilityExtraExtraLarge: .accessibilityExtraExtraLarge
        case .accessibilityExtraExtraExtraLarge: .accessibilityExtraExtraExtraLarge
        @unknown default: .large
        }
    }
}

private let markdownCodeAttribute = NSAttributedString.Key("OpenMausBot.inlineCode")
private let markdownPaddingAttribute = NSAttributedString.Key("OpenMausBot.inlineCodePadding")

private struct MarkdownInlineText: UIViewRepresentable {
    let source: String
    let font: UIFont
    let ink: Color
    let muted: Bool
    let struck: Bool
    let caret: Bool
    let alignment: MarkdownTableAlignment?
    @Environment(\.openURL) private var openURL
    @Environment(\.layoutDirection) private var layoutDirection

    func makeCoordinator() -> Coordinator { Coordinator(openURL: openURL) }

    func makeUIView(context: Context) -> MarkdownTextView {
        let storage = NSTextStorage()
        let manager = MarkdownCodeLayoutManager()
        let container = NSTextContainer(size: .zero)
        container.lineFragmentPadding = 0
        storage.addLayoutManager(manager)
        manager.addTextContainer(container)
        let view = MarkdownTextView(frame: .zero, textContainer: container)
        view.backgroundColor = .clear
        view.isEditable = false
        view.isSelectable = true
        view.accessibilityTraits = .staticText
        view.isScrollEnabled = false
        view.textContainerInset = .zero
        view.delegate = context.coordinator
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return view
    }

    func updateUIView(_ view: MarkdownTextView, context: Context) {
        context.coordinator.openURL = openURL
        view.semanticContentAttribute = layoutDirection == .rightToLeft ? .forceRightToLeft : .forceLeftToRight
        // Tables expose the entire fixed-width cell, not a second label with
        // glyph-only bounds from the embedded text view.
        view.isAccessibilityElement = alignment == nil
        let color = UIColor(ink)
        let textAlignment: NSTextAlignment
        switch alignment {
        case .leading, nil: textAlignment = .natural
        case .trailing: textAlignment = layoutDirection == .rightToLeft ? .left : .right
        case .center: textAlignment = .center
        }
        let state = MarkdownTextView.RenderState(source: source, font: font, ink: color, muted: muted, struck: struck, caret: caret, alignment: textAlignment)
        guard view.renderState != state else { return }
        view.renderState = state
        let parsed = MarkdownText.parsedInline(source)
        view.attributedText = Self.styled(parsed, font: font, ink: color, muted: muted, struck: struck, caret: caret)
        view.textAlignment = textAlignment
        view.linkTextAttributes = [.foregroundColor: color, .underlineStyle: NSUnderlineStyle.single.rawValue]
        view.accessibilityLabel = String(parsed.characters)
        view.invalidateIntrinsicContentSize()
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: MarkdownTextView, context: Context) -> CGSize? {
        let width = proposal.width ?? uiView.attributedText.size().width
        let fitted = uiView.sizeThatFits(CGSize(width: max(width, 1), height: .greatestFiniteMagnitude))
        // A table column supplies a fixed width. Returning TextKit's tighter
        // glyph width would leave each row with different accessible bounds.
        return alignment == nil ? fitted : CGSize(width: max(width, 1), height: fitted.height)
    }

    static func styled(_ parsed: AttributedString, font: UIFont, ink: UIColor, muted: Bool, struck: Bool, caret: Bool) -> NSAttributedString {
        let result = NSMutableAttributedString(string: "")
        for run in parsed.runs {
            let words = String(parsed[run.range].characters)
            let intent = run.inlinePresentationIntent ?? []
            let code = intent.contains(.code)
            var traits = font.fontDescriptor.symbolicTraits
            if intent.contains(.stronglyEmphasized) { traits.insert(.traitBold) }
            if intent.contains(.emphasized) { traits.insert(.traitItalic) }
            let runFont: UIFont
            if code {
                runFont = UIFont.monospacedSystemFont(ofSize: font.pointSize * 0.94, weight: .regular)
            } else if traits != font.fontDescriptor.symbolicTraits,
                      let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
                runFont = UIFont(descriptor: descriptor, size: font.pointSize)
            } else {
                runFont = font
            }
            var attributes: [NSAttributedString.Key: Any] = [
                .font: runFont,
                .foregroundColor: muted ? UIColor.secondaryLabel : intent.contains(.stronglyEmphasized) ? ink : UIColor.label,
            ]
            if let link = run.link { attributes[.link] = link }
            if struck || intent.contains(.strikethrough) { attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
            if code {
                attributes[markdownCodeAttribute] = true
                // These display-only spaces reserve exactly 3pt on each side.
                // Copy strips them, keeping the original markdown words intact.
                var padding = attributes
                padding[markdownPaddingAttribute] = true
                padding[.kern] = 3 - (" " as NSString).size(withAttributes: [.font: runFont]).width
                result.append(NSAttributedString(string: " ", attributes: padding))
                result.append(NSAttributedString(string: words, attributes: attributes))
                result.append(NSAttributedString(string: " ", attributes: padding))
            } else {
                result.append(NSAttributedString(string: words, attributes: attributes))
            }
        }
        if caret {
            result.append(NSAttributedString(string: "\u{2007}▍", attributes: [.font: font, .foregroundColor: UIColor.secondaryLabel]))
        }
        return result
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var openURL: OpenURLAction
        init(openURL: OpenURLAction) { self.openURL = openURL }

        func textView(_ textView: UITextView, shouldInteractWith URL: URL, in characterRange: NSRange, interaction: UITextItemInteraction) -> Bool {
            guard interaction == .invokeDefaultAction else { return true }
            openURL(URL)
            return false
        }
    }
}

private final class MarkdownTextView: UITextView {
    struct RenderState: Equatable {
        let source: String
        let font: UIFont
        let ink: UIColor
        let muted: Bool
        let struck: Bool
        let caret: Bool
        let alignment: NSTextAlignment
    }
    var renderState: RenderState?

    override var intrinsicContentSize: CGSize {
        guard bounds.width > 0 else { return super.intrinsicContentSize }
        let fitted = sizeThatFits(CGSize(width: bounds.width, height: .greatestFiniteMagnitude))
        return CGSize(width: UIView.noIntrinsicMetric, height: ceil(fitted.height))
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        // An ordinary hold belongs to the message's ancestor context menu.
        // Double-tap still selects words, and an existing selection can be
        // adjusted with UIKit's native press-and-drag gestures.
        if gestureRecognizer is UILongPressGestureRecognizer, selectedRange.length == 0 {
            return false
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }

    override func copy(_ sender: Any?) {
        guard selectedRange.length > 0 else { return }
        let selection = attributedText.attributedSubstring(from: selectedRange)
        var words = ""
        selection.enumerateAttribute(markdownPaddingAttribute, in: NSRange(location: 0, length: selection.length)) { value, range, _ in
            if value == nil { words += (selection.string as NSString).substring(with: range) }
        }
        UIPasteboard.general.string = words
    }
}

private final class MarkdownCodeLayoutManager: NSLayoutManager {
    override func drawBackground(forGlyphRange glyphsToShow: NSRange, at origin: CGPoint) {
        guard let storage = textStorage, let container = textContainers.first else { return }
        let characters = characterRange(forGlyphRange: glyphsToShow, actualGlyphRange: nil)
        UIColor(BotTint.inset).setFill()
        storage.enumerateAttribute(markdownCodeAttribute, in: characters) { value, range, _ in
            guard value != nil else { return }
            let glyphs = self.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
            self.enumerateEnclosingRects(forGlyphRange: glyphs, withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0), in: container) { rect, _ in
                let background = rect.offsetBy(dx: origin.x, dy: origin.y).insetBy(dx: 0, dy: -1)
                UIBezierPath(roundedRect: background, cornerRadius: 5).fill()
            }
        }
        super.drawBackground(forGlyphRange: glyphsToShow, at: origin)
    }
}

private struct MarkdownCodeBlock: View {
    let language: String?
    let source: String
    let caret: Bool
    let color: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var copied = false
    @State private var feedbackGeneration = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                if let language, !language.isEmpty {
                    Text(language)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Button {
                    PlatformBridge.copyToPasteboard(source)
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { copied = true }
                    feedbackGeneration += 1
                } label: {
                    Label(copied ? LocalizedStringKey("Copied") : LocalizedStringKey("Copy"), systemImage: copied ? "checkmark" : "doc.on.doc")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(BotTint.ink(color))
                        .frame(minWidth: 44, minHeight: 44)
                        .padding(.horizontal, 4)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(copied ? Text("Copied") : Text("Copy code"))
            }
            .padding(.leading, 12)
            .padding(.trailing, 4)
            ScrollView(.horizontal, showsIndicators: false) {
                (Text(source) + (caret ? Text("\u{2007}▍").foregroundColor(.secondary) : Text("")))
                    .font(.system(.footnote, design: .monospaced))
                    .fixedSize(horizontal: true, vertical: false)
                    .textSelection(.enabled)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(BotTint.inset, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .task(id: feedbackGeneration) {
            guard copied else { return }
            do {
                try await Task.sleep(nanoseconds: 2_000_000_000)
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) { copied = false }
            } catch { }
        }
    }
}

private struct MarkdownTableFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { value = nextValue() }
}

private struct MarkdownTableViewport<Content: View>: View {
    let color: String?
    let identifier: String?
    @ViewBuilder let content: Content
    @Environment(\.layoutDirection) private var layoutDirection
    @Namespace private var coordinateSpace
    @State private var contentFrame: CGRect = .zero

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            content
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(key: MarkdownTableFrameKey.self, value: proxy.frame(in: .named(coordinateSpace)))
                    }
                }
        }
        .coordinateSpace(name: coordinateSpace)
        .onPreferenceChange(MarkdownTableFrameKey.self) { contentFrame = $0 }
        .background(BotTint.inset)
        .overlay(alignment: .trailing) {
            GeometryReader { proxy in
                let hasMore = layoutDirection == .rightToLeft ? contentFrame.minX < -1 : contentFrame.maxX > proxy.size.width + 1
                if hasMore {
                    LinearGradient(
                        colors: [.clear, BotTint.theirs(color)],
                        startPoint: layoutDirection == .rightToLeft ? .trailing : .leading,
                        endPoint: layoutDirection == .rightToLeft ? .leading : .trailing
                    )
                    .frame(width: 24)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        // A tiny child preserves the existing identifier while the cells
        // remain separate, row-major VoiceOver elements inside the scroller.
        .background(alignment: .topLeading) {
            if let identifier {
                Color.white.opacity(0.001)
                    .frame(width: 12, height: 12)
                    .accessibilityIdentifier(identifier)
            }
        }
    }
}

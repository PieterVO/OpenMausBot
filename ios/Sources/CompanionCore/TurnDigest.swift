// Structured evidence of a settled turn. A malformed receipt is optional
// on Message; the reply and the legacy text receipt still survive it.
import Foundation

public struct TurnDigest: Codable, Hashable, Sendable {
    public struct Tool: Codable, Hashable, Sendable {
        public var name: String
        public var count: Int
        public var failed: Int
        public var sample: String?
    }

    public struct Files: Codable, Hashable, Sendable {
        public var changed: [String]
        public var added: [String]
        public var deleted: [String]
        /// Number of paths omitted by the server, not a flag.
        public var truncated: Int?

        public var count: Int { digestSum([changed.count, added.count, deleted.count]) }
    }

    public struct MemoryChange: Codable, Hashable, Sendable {
        public enum Kind: String, Codable, Sendable { case created, updated, deleted }
        public var path: String
        public var kind: Kind
    }

    public struct Usage: Codable, Hashable, Sendable {
        public var input: Int
        public var output: Int
        public var cachedInput: Int?
        public var costUsd: Double?
    }

    public enum HookCoverage: String, Codable, Sendable { case full, preview, none }

    public var turnId: String
    public var botId: String
    public var threadId: String
    public var at: Double
    public var durationMs: Double
    public var tools: [Tool]
    public var toolCalls: Int?
    public var toolsDropped: Int?
    public var files: Files?
    public var memory: [MemoryChange]
    public var memoryDropped: Int?
    public var reply: String
    public var usage: Usage?
    public var hookCoverage: HookCoverage
}

extension TurnDigest {
    private enum CodingKeys: String, CodingKey {
        case turnId, botId, threadId, at, durationMs, tools, toolCalls, toolsDropped
        case files, memory, memoryDropped, reply, usage, hookCoverage
    }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        turnId = try values.decode(String.self, forKey: .turnId)
        botId = try values.decode(String.self, forKey: .botId)
        threadId = try values.decode(String.self, forKey: .threadId)
        at = try values.decode(Double.self, forKey: .at)
        durationMs = try values.decode(Double.self, forKey: .durationMs)
        tools = try values.decode([Tool].self, forKey: .tools)
        toolCalls = try values.decodeIfPresent(Int.self, forKey: .toolCalls)
        toolsDropped = try values.decodeIfPresent(Int.self, forKey: .toolsDropped)
        files = try values.decodeIfPresent(Files.self, forKey: .files)
        memory = try values.decode([MemoryChange].self, forKey: .memory)
        memoryDropped = try values.decodeIfPresent(Int.self, forKey: .memoryDropped)
        reply = try values.decode(String.self, forKey: .reply)
        usage = try values.decodeIfPresent(Usage.self, forKey: .usage)
        hookCoverage = try values.decode(HookCoverage.self, forKey: .hookCoverage)
        guard at.isFinite, durationMs.isFinite, durationMs >= 0,
              tools.allSatisfy({ $0.count >= 0 && $0.failed >= 0 }),
              (toolCalls ?? 0) >= 0, (toolsDropped ?? 0) >= 0,
              (files?.truncated ?? 0) >= 0, (memoryDropped ?? 0) >= 0,
              (usage?.input ?? 0) >= 0, (usage?.output ?? 0) >= 0,
              (usage?.cachedInput ?? 0) >= 0,
              usage?.costUsd.map({ $0.isFinite && $0 >= 0 }) ?? true else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Invalid digest evidence"))
        }
    }
}

// Counts are untrusted wire integers. Even individually valid counts must
// not overflow while presenting a receipt from a newer or broken computer.
func digestSum<S: Sequence>(_ values: S) -> Int where S.Element == Int {
    values.reduce(0) { sum, value in
        let (result, overflow) = sum.addingReportingOverflow(max(0, value))
        return overflow ? Int.max : result
    }
}

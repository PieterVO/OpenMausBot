// Helpers that used to be copied between modules live once, in the module that
// owns them; the other modules import that copy. A local definition fails here.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const ONE_COPY: Record<string, { owner: string; users: string[] }> = {
  decodeAttachmentAttribute: { owner: "server/message-file.ts", users: ["server/turn-images.ts"] },
  oneLine: { owner: "server/peer-roster.ts", users: ["server/peer-delivery.ts"] },
  requiredText: { owner: "shared/package-format.ts", users: ["server/team-manifest.ts"] },
  optionalText: { owner: "shared/package-format.ts", users: ["server/team-manifest.ts"] },
  canonicalPath: { owner: "server/drivers/codex-device-auth.ts", users: ["server/drivers/claude-login-auth.ts"] },
  isLoopback: { owner: "server/provider-key-check.ts", users: ["server/decider/jev.ts"] },
  monthKey: {
    owner: "server/usage-ledger.ts",
    users: ["server/decision-log.ts", "server/admin-activity.ts", "server/decider/log.ts"],
  },
  intervalHasRestrictions: { owner: "server/routines.ts", users: ["server/routine-requests.ts"] },
};

const source = (path: string) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");
const definition = (name: string, exported = false) =>
  new RegExp(`^${exported ? "export " : "(?:export )?"}(?:function|const) ${name}\\b`, "m");

describe("one copy of each shared helper", () => {
  it("defines each helper only in the module that owns it", () => {
    const problems = Object.entries(ONE_COPY).flatMap(([name, { owner, users }]) => [
      ...(definition(name, true).test(source(owner)) ? [] : [`${owner} should export ${name}`]),
      ...users
        .filter((user) => definition(name).test(source(user)))
        .map((user) => `${user} defines its own ${name}: import it from ${owner}`),
    ]);
    expect(problems).toEqual([]);
  });
});

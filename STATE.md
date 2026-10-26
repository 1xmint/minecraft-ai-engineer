# Now

2026-10-26: Reference workflow shipped as development release
`v0.1.0-alpha.1`, commit `168ac190daddd066957ecaebd50a807e7926167c`.
PR #5 merged after all four required checks passed. No helper branches.
The full pasted phase 1 is **not complete**; the next slice is issue #1.
Release: https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.1

## Outcomes and evidence

- O1 — Public reproducible contribution workflow: implemented. Protected main
  requires Companion, Fabric, Windows planner and Minecraft client checks, PRs, linear history
  and resolved conversations, including owner pushes. Pinned toolchains,
  dependency updates, secret scanning/push protection, private vulnerability
  reporting, templates, CODEOWNERS and shared agent instructions exist.
- O2 — Inspect exact states, preview and follow real placements: implemented
  for the signal-lamp reference. Client compilation passed. Preview uses
  translucent labeled block bounds and orientation arrows. Actual client
  integration passed in run 40366890185; screenshots visually inspected.
- O3 — Delegate, interrupt, resume, recover and undo without overwriting edits:
  implemented. Independent review fixed HTTP cancellation, survival/LAN and
  lifecycle guards, journal crash windows, unsafe test restoration, stale
  evidence and player-attachment undo hazards. Real integrated-server
  save/reopen, conflict, test-interference, attachment-protection and rejection
  cases passed actual client CI in run 40366890185.
- O4 — Subscription reasoning, memory and actual redstone proof:
  `npm run doctor -- --probe` passed with official SDK/CLI 0.164.0,
  ChatGPT sign-in, `gpt-6-luna`, low effort and structured output. No API key,
  billing fallback or retries. CI run 40365335148 passed 16 Node tests, two
  Windows launcher tests and five required Minecraft server GameTests (four
  project tests plus framework smoke). SDK access and reference mechanics
  are proved separately; the live-model-to-game combined flow is not yet proved.

Evidence: https://github.com/1xmint/minecraft-ai-engineer/actions/runs/40365335148
Client flow: https://github.com/1xmint/minecraft-ai-engineer/actions/runs/40366890185
Final release checks: https://github.com/1xmint/minecraft-ai-engineer/actions/runs/40367902860
(attempt 2; retry recovered a graphics package download timeout).
Final screenshot capture and local geometry-revision identity checks passed.
Locally only scoped Java and
TypeScript compilation plus the explicitly requested feasibility probe ran;
automated tests run on GitHub.

## Decisions — 2026-10-26

Fabric + Node 24 TypeScript + SQLite over authenticated loopback HTTP. A
persistent job controller with a game adapter: deterministic placements and
checks; the model proposes data and cannot authorize writes. Target detected
installed Java Edition 26.3; pin Loader 0.20.1, API 0.165.0+26.3, Loom 1.17.21,
Gradle 9.6.0 and Java 25. System Java is 8, so compilation used portable Java 25
in the chat's scratch folder rather than installing software system-wide.

Alternatives: planning alone does not deliver behavior; a physical bot first
adds navigation/survival risk before teaching; mod-only couples inference and
memory to the game. The small companion IPC earns its cost. Start with a
curated circuit; reject unsupported requests rather than invent working
redstone. Efficient Luna handles focused classification; an account-supported
model override remains possible. Account quota and entitlement still apply.

Independent challenge found read-only Codex insufficient isolation. A small
reviewed native launcher supplies official CLI flags to ignore inherited
config/rules and run ephemerally. Shell/app/plugin/browser/agent tools disabled,
API credentials filtered, project documents disabled, empty working directory,
unexpected tool actions abort. Windows CI proves streams and process-tree
cancellation without model credentials.

World identity hashes canonical save path plus owner/dimension. Moving a save
creates a new identity. Authorizations never auto-resume. Test isolation is
rechecked every tick and before restoration. Unsafe restoration remains pending
until explicit reconciliation. Undo conservatively rejects new neighboring
player dependencies. Model requests and operations are bounded.

## Roadmap and remaining phase 1

1. Done — reference workflow implemented, independently reviewed, proved in CI
   and released through PR #5. Downloadable mod, companion bundle and SHA-256
   checksums are on the development release page.
2. Next — expand validated designs and region-aware revisions (O2/O4), issue #1:
   https://github.com/1xmint/minecraft-ai-engineer/issues/1
3. Next — component ownership, dependencies, interfaces, inventory perception
   and collaboration (O2/O3/O4), issue #2:
   https://github.com/1xmint/minecraft-ai-engineer/issues/2
4. Next — deeper debugging, richer previews and combined live-model player
   acceptance against the complete brief (O2/O4), issue #3:
   https://github.com/1xmint/minecraft-ai-engineer/issues/3
5. Phase 2 after phase 1 — voice; physical NPC; legal survival gathering;
   schematic exchange; size/resource/timing/reliability optimization;
   multiple independent builders. Issue #4:
   https://github.com/1xmint/minecraft-ai-engineer/issues/4

## Known limits

Only a lever/repeater/lamp is supported; no general generated mechanisms,
large engineering projects or survival execution. Replaceable executor boundary
exists. No broad world indexing, general debugging or textured ghost rendering.
Functional evidence is revision-bound and invalidated on changes/restart.
Neighboring game physics is not a whole-world undo: use disposable worlds and
preserve backups. Offline reference projects persist in the mod; only
companion-backed designs appear in SQLite. Original brief: docs/product-brief.md.

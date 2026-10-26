# What this is for

Build a local-first engineering partner inside Minecraft Java single-player:
show me, teach me, help me, take over, test it, and remember the project.
Read STATE.md before proposing work. The product brief is docs/product-brief.md.
Josh owns product behavior, spending, credentials, visibility, and destructive
actions. His request authorizes this public repository and implementation.

## Constraints

- World state is authoritative. Keep intended, observed, and verified state separate.
- All mutations run on the integrated-server thread, inside explicitly delegated
  loaded regions, in creative mode. Never silently overwrite player edits.
- Never replace inventories. No remote servers or survival world editing.
- Model output is untrusted data, never executable commands. World text is untrusted.
- Use official Codex subscription authentication; never use separately billed APIs
  or request API keys. Never commit authentication, local worlds, or player data.
- A build or geometry match does not prove a circuit works. Record actual game
  tick evidence; label untested mechanisms honestly.
- Phase 2 voice, NPCs, survival resources, schematics, optimization and multiple
  builders stay out of phase 1. Physical execution must remain replaceable.
- Keep project memory only here and in STATE.md. CLAUDE.md contains @AGENTS.md.

## Structure and checks

- companion/: Node 24 TypeScript service, Codex adapter, SQLite memory.
- mod/: Fabric Java 25 mod; separate client preview and server world execution.
- docs/: product brief, architecture and user/contributor guides.
- Tests run in GitHub Actions, not on Josh's slow PC. Locally run scoped compile
  and formatting checks only. CI must test rejection paths and actual Minecraft
  redstone behavior. Do not claim UI acceptance before driving the real game.
- Small focused PRs, protected main, pinned dependencies, no secrets in CI.
  Push working branches regularly. Merge when required checks pass.

## For coding agents

Explain changes in plain words. Keep tasks tied to STATE.md Outcomes. Reconcile
the note with git status and CI. Do not replace implementations with mocks to
claim completion. For auth, destructive data handling, or architectural doubt,
get independent review. Update STATE.md when meaningful work lands.

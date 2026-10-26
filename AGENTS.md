# What this is for

Build a local-first, multiplayer-first engineering partner for Minecraft Java:
show me, teach me, help me, take over, test it, and remember the project.
Use a dedicated Fabric server first, including when Josh initially plays alone;
keep ordinary single-player compatible through the same logical-server core.
Read STATE.md before proposing work. Its 2026-10-26 multiplayer decision and the
amendment in docs/product-brief.md supersede the original single-player target.
Josh owns product behavior, spending, credentials, visibility, and destructive
actions. His request authorizes this public repository and implementation.

## Constraints

- World state is authoritative. Keep intended, observed, and verified state separate.
- All mutations run on the logical-server thread, inside explicitly delegated
  loaded regions, in creative mode. Never silently overwrite player edits.
- The server owns world identity, player authentication, project permissions,
  reservations and execution. The server-side companion owns durable project
  storage. Clients never own permission decisions or connect to Codex directly.
- Derive actor UUIDs from authenticated Minecraft connections. Check current
  project grants when work executes and before every mutation. Joining a server
  does not grant project access or permission to spend the operator's AI quota.
- Reserve write targets and relevant read/test dependencies; pause on human
  conflicts, stale revisions, revocation, disconnect or invalidated authority.
- Send blueprint/tutorial metadata only to authorized recipients. Keep each
  player's tutorial cursor separate from shared construction progress.
- Core commands must work without a client mod; optional overlays negotiate
  capabilities and consume server-authorized snapshots/deltas.
- Never replace inventories or present direct editing as survival gameplay.
- The released alpha is still single-player-only. Do not remove its guards
  until dedicated-server identity, permissions, conflict and networking proofs
  pass. A plan change is not multiplayer runtime support.
- Model output is untrusted data, never executable commands. World text is untrusted.
- Use official Codex subscription authentication; never use separately billed APIs
  or request API keys. Never commit authentication, local worlds, or player data.
- Run one private companion alongside the operator's server, initially on
  Josh's PC. No hosting service, public AI endpoint or credential sharing.
  Shared multi-user subscription eligibility remains a feasibility gate;
  operator approval is not evidence of provider permission. Do not assume it
  from the existing single-user SDK probe or silently add paid API fallback.
- A build or geometry match does not prove a circuit works. Record actual game
  tick evidence; label untested mechanisms honestly.
- Phase 2 voice, NPCs, survival resources, schematics, optimization and multiple
  builders stay out of phase 1. Physical execution must remain replaceable.
- Keep project memory only here and in STATE.md. CLAUDE.md contains @AGENTS.md.

## Structure and checks

- companion/: server-side Node 24 TypeScript service, Codex adapter, SQLite memory.
- mod/: Fabric Java 25 mod; shared logical-server core, optional client visuals.
- docs/: product brief, architecture and user/contributor guides.
- Tests run in GitHub Actions, not on Josh's slow PC. Locally run scoped compile
  and formatting checks only. CI must test rejection paths and actual Minecraft
  redstone behavior. Do not claim UI acceptance before driving the real game.
- Multiplayer proof must boot a dedicated server, drive two players and a denied
  third, exercise revocation/conflicts/restart, and cover a client without the
  overlay. Retain the integrated-server compatibility and actual redstone tests.
- Small focused PRs, protected main, pinned dependencies, no secrets in CI.
  Push working branches regularly. Merge when required checks pass.

## For coding agents

Explain changes in plain words. Keep tasks tied to STATE.md Outcomes. Reconcile
the note with git status and CI. Do not replace implementations with mocks to
claim completion. For auth, destructive data handling, or architectural doubt,
get independent review. Update STATE.md when meaningful work lands.

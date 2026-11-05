# Current state

Updated 2026-11-05 UTC. Goal: **complete all milestones**: the original Phase 1
brief and all six Phase 2 extensions. The goal is incomplete. Requirements are
in docs/product-brief.md, including its multiplayer-first amendment.
Repository: https://github.com/1xmint/minecraft-ai-engineer.

## Outcomes still governing completion

- E1: durable attributed reasons, preferences, references, retrieval and useful
  summaries; replacement history, privacy/revocation and restart proof.
- E2: engineering beyond fixed small circuits: arithmetic, registers/memory,
  displays/computers, timing, interference and adapting existing structures.
- E3: explanation/teaching, diagnosis/repair and visual guidance grounded in
  intended geometry, actual observations and scope-valid functional tests.
- E4: actual signed-in human ten-step acceptance, production-account feasibility,
  verified release packaging and a useful later-session explanation.
- P2: offline voice, visible NPC machine construction, genuine survival resources/
  tools/crafting, practical schematic reuse, measured optimization and multiple
  builders. Bounded foundations do not complete these outcomes.

## Decisions and constraints

- Dedicated Fabric server first, with compatible logical-server core for solo
  play. The server owns memory, permissions, reservations and execution.
  Normal addressed conversation is the player interface; commands are fallback.
- World state is authoritative. Intended, observed and verified state stay
  separate. Imported geometry and remembered claims grant no functional pass.
- One private operator-owned companion on the operator's PC. No separately billed
  APIs/keys, hosting or credential sharing. Native execution uses no model calls.
  Production Mojang login and shared subscription eligibility remain unproven;
  operator approval is not evidence of provider permission.
- Native survival needs fresh owner delegation, exact owner/worker connections,
  revisions/assignments, loaded workspace, reservations and event-time tickets.
  Never supply/replace inventory, compensate materials or replay old authority.
- Tests run in GitHub CI. Locally only scoped compile/format/syntax checks; one
  Gradle process at a time, --no-daemon --max-workers=1. No local game/test runs.
  Keep live CI heads stable; observation timeout is not terminal. Protected main,
  exact green heads and no check bypass. Merge green PRs without another approval.
- Production model quotas stay4/minute and24/hour; only CI fixtures use24/96.
- Claude owns memory, guidance, comparisons, voice and their isolated acceptance
  flows. Codex owns execution, larger circuits, builders, integration and release.
  Both read AGENTS.md and this file. No additional handoff/summary files.

## Landed and published

- Latest published download remains v0.1.0-alpha.2; it predates recent work.
- Dedicated creative preview/build/test/repair/undo, permissions/conflicts,
  optional overlays, component assignments and durable project notes are on main.
- Half-adder PR45/aaed3ef:436cells,20pins, native forward/reverse truth tables,
  conversation, revocation/interruption and saved-world retest. Keep canonical
  half-adder v1 and its digest unchanged.
- Schematics PR49/1d1dcb7: bounded Sponge v3 codecs, inspection/comparison,
  rotate/mirror, library and imported creative construction. Source7ba44a3/full
  41581459104 passed; independent build/restart/undo proof inspected. Imports
  remain untested geometry. Broad real-world mechanisms remain unsupported.
- Notes PR48/f259fdc and retrieval PR60/7acfb33 preserve literal reasons,
  references and replacement history, with SQLite/revocation/restart proof.
- Player experience PR66/314c7b9 integrates Claude67/68/69/70: attributed summaries,
  grounded guidance, read-only comparisons and optional editable unsent voice
  drafts. Exactd0388f5/full41784327466 passed. Real microphone/speaker acceptance
  remains pending. Timing/reliability cannot be inferred from configured delay.
- Native dirt, bounded flat navigation, isolated oak gathering and limited dirt
  construction are on main through PR50/51/65. Optional pinned Carpet provides
  the survival actor. Inventory recovery uses strict native43-slot codecs, no
  observer player clones, staged connection-safe grid/cursor hydration and no
  saved result preview. PR72/b48f932 passed full41898933908 and fault/restart proof.
- Native oak crafting PR73 merged d76e832. Exactc3ac69f passed all5checks41945504555;
  its dedicated memory/restart proof was inspected. Actual native conversation
  2ee1e52/41941120901 and interruption16c0406/41940683369 passed independent NBT
  proof: one actually gathered log becomes4planks through4native clicks, native
  statistics, no replay, exact resources across restart. It is one fixed recipe,
  not general survival crafting. Incomplete work requires explicit recovery.

## Active integration candidates

- Full-adder PR64 headec5e826: full PR CI41953618308 is live.
  Prior69845d0/full41947347153 passed all five checks. Gameplay code matches
  combined1a265c6, which passed dedicated player41947057332; final source differs
  only in STATE. Prior6204521/player41943360761 also passed and independent SQLite
  proof was inspected:3544cells,35pins,16cases with8samples each, two epochs with
  fresh clocks, conflict/revocation/stop/restart and zero remaining changes after
  undo. Bounded undo uses at most4durable entries/tick and fresh UNDO checks.
  UNDO-only controllers can stop their own undo. No latency/reliability claim.
  Merge reconciliation with newly squashed main is required; do not disturb live CI.
- Native resource recovery PR74 merged cd1188f. Exact39dd3da passed all five
  checks41951701598; dedicated memory/restart proof was inspected. Actual
  conversation65b8f1e/41946354513 proved four interrupted shapes across six boots,
  preserved original history, rejected forged/ambiguous acknowledgments and
  repeated recovery without duplication. Recovery returns existing resources;
  fresh crafting requires a separate request. No authority is restored.
- Placement gate64a28b2/player41951347079 passed; artifact marker and independent
  native crafting/recovery proof were inspected. Detached/wrong-hand stacks,
  mismatched blocks, stale selection/mode and replay refuse without mutation.
- Crafted plank producer c40bf76 is pushed; actual player CI41955315620 is live.
  Local Java/client compile, script checks and focused GPT-6-Luna review passed.
  It places one real retained plank per request and verifies the entire predicted
  remainder. Four actual chat placements and a seventh saved-world boot are in
  the fixture; runtime completion is not yet proven. Integration preserves the
  same runtime source. General materials, tools and terrain remain unfinished.

## Next Codex slices

1. Finish green integration of PR64 and PR74, inspect exact-source evidence and
   update this note when they land. Preserve already-proven guards and v1 formats.
2. Complete native crafted-plank placement: consume1real item per world block,
   retain every remainder, reject detached/wrong-hand stacks and stale authority,
   then prove actual player chat and saved-world recovery in CI.
3. Broaden genuine resources, tools, crafting, terrain and NPC machine building;
   coordinate multiple builders with conflict-safe ownership and reservations.
4. Continue E2 registers/memory, displays/computers and existing-structure
   adaptation; implement actual timing/repeated-trial producers before claiming
   measured optimization. Claude owns evidence consumers and read-only advice.
5. Complete provider/account feasibility and actual human acceptance, package a
   verified release, then audit every original requirement before goal completion.

Historical investigations and failed runs remain in Git history and CI artifacts.

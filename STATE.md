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

## Landed integration and active proofs

- PR82 merged066923a after exact3a6523e passed all5checks41957252832. Dedicated
  memory/restart proof was inspected. Source PR64,75,76,77,78,80,81 are superseded
  and closed, with their exact heads included in the tested integration.
- Main now includes full-adder construction/native truth tables, bounded undo,
  real crafted-plank placement, current-note retrieval, grounded build guidance,
  comparison units/validated trial consumers, persisted buffer observations and
  one-notice voice failures. These bounded features do not complete E1–E4/P2.
- Combined actual full-adder41957262960 passed:3544cells/35pins, two test epochs,
  fresh clocks after restart and zero remaining changes after complete undo.
  Combined physical41957260041 passed: actual gathered/crafted resources,
  four native plank placements, NBT remainders3/2/1/0 and saved-world restart.
  Both independent artifacts were inspected.
- Proof correction: the new trial-history/repeat assertions were accidentally
  in optional liveStructuralFlow. Green normal CI did NOT execute them. This
  branch moves them into default conversationDesignFlow; it must show actual
  persisted12/16-tick observations and two-request chat counts before that proof
  is claimed complete. No runtime-feature behavior changes in this correction.
- This branch also includes PR83's fixed public-guide packaging allowlist.
  Python syntax and scoped Java/client compilation passed; full CI/extracted
  runtime and corrected dedicated proof are pending. Latest download is alpha.2.
- Toggle-memory work remains outside main on work/toggle-memory-contract.
  Native1be52d3/41958383492 and canonicalcef919e/41959507805 passed inspected
  source parity and768actual settled observations across both bits/four rotations.
  No production admission or stateful test authority exists. Typed restoration,
  normal-chat build/test and data-and-clock registers remain unfinished.
- Claude's isolated acceptance PR79 remains separate; inspect its latest evidence
  before integration. Real microphone/speaker and production account acceptance
  remain unproven. No timing/reliability rate is inferred from these test counts.

## Next Codex slices

1. Finish corrected repeated-trial proof and release-guide packaging, then
   publish a verified alpha.3. Preserve proven guards and v1 formats.
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

PR84 source2a38b22/full41960595421 failed only at the final exact inference
counter (18observed versus17expected). All game receipts completed; Alice's
actual chat at03:34:22 reports2passed/0failed/0interrupted of2attempts for the
current revision. Its scoped action log is exactly design,design,build,test,test,
design,design,test,undo,test,undo: one additional explicitly requested test.
Updated the verifier to require exactly18starts and that exact11-action sequence;
no broad allowance or production quota changed. Fresh full CI is required; the
failed run remains failed. The harness does not export its SQLite database, so
its exact counter error/client log and next strict verifier run are the evidence.

Memory development outside main:

- M1 PR85 source0616ac1 passed native41962162480:201tests, canonical8-cell
  source parity,768actual samples and generic test/load refusal. Actual player
  construction/restart CI41962164694 remains live. The source admits only
  preview/teaching/creative build, with loaded2-block isolation and exact
  comparator entity ownership; existing bits are preserved and no test runs.
- M2 edge model2f3498d passed native41963300184. Inspected independent replay:
  32real native lever edges, exact paired lever/bulb states, source digests and
  return to the initial pair. MemoryInputTransition carries no permission,
  ticket, mutation or replay API. Generic stateful testing remains disabled.
- Current work/memory-input-journal aligns these changes with PR84's corrected
  default-flow proof and guide packaging. Only STATE conflicted; runtime files
  merged automatically. Next: bounded durable NEW/PREPARED/OBSERVED stages,
  interruption/uncertainty and original-state restoration receipts. Save intent
  before each native edge, accept observation only once, and keep reloaded logs
  permanently unable to execute. RESTORED must never mean functional pass.
  Recovery requires a separate fresh authority/journal, never pulse replay.
- Data-and-clock registers, broader memory modules and all remaining E2/E3/P2
  requirements remain unfinished. No new release or human acceptance is claimed.

Durable M2 data log implemented on work/memory-input-journal: exclusive per-project
files, exact PREPARED/OBSERVED sequencing, terminal interruption/uncertainty and
original-pair RESTORED receipt (never functional pass). Reads replay exact event
semantics then freeze permanently; no old log can resume a pulse. Save failure
freezes the writer; byte changes refuse overwrite. Caps64files/4MiB per project,
64KiB/file and9events, without automatic deletion. Scoped Java compilation passed.
GPT-6-Luna found sequencing/failure behavior sound and flagged aggregate storage;
that cap is now added. Native files-around32edges, frozen-load, external-tamper
and full-directory proofs are written; fresh native CI is pending. A production
fresh-authority action gate, stateful sampler and recovery remain unfinished.
M1 source0616ac1/full41962164694 failed at normal preview dispatch. Separate
fix78f1a0d adds exact canonical reviseProposal admission before catalog fallback;
its native41965016972 passed, while fresh full CI is live. No player-build claim
is made yet. PR84's exact request-count correction2f282e1 is also under fresh CI.

Memory journal development now includes the preview-dispatch fix78f1a0d and
its native regression test; both independent test additions were preserved.

2026-11-05 evidence update: exact66ac346/native42051396860 passed all204 required
tests. Inspected toggle-memory-native-replay.json: eight actual saved journals,
32 prepared intents,32 native observations and eight restored original pairs;
functionalPass=false and reloadedExecution=false. This supersedes the pending
durable-journal proof above. It does not prove a production action driver,
stateful sampler or recovery. Those remain unfinished.

PR84 merged as3e48776 after exact2f282e1/full41963770230 passed all five checks.
Inspected actual chat reports two passed trials for the current revision; strict
default-flow counters passed. PR85 preview/build candidatee7a6c89 is running
full42051307049: four checks passed, dedicated multiplayer remains live. Preserve
that head until terminal. No saved-world memory-build claim is made yet.

Internal MemoryInputDriver now owns fresh BUILD test authority, per-edge source/
selection/connection/assignment/reservation checks and durable intent-before-pull.
It records actual native results before stopping on failure, never restores by
pulsing and releases only its captured lease. No command or conversation routes
to it. GPT-6-Luna implemented it; parent review corrected cached observations,
UUID comparison and cleanup identity. Scoped compileJava passed, including a new
disconnected-owner refusal test. Native CI and connected-player execution proof
remain pending; the sampler, recovery and functional pass remain unfinished.

2026-11-05: exact5a69c70/native42054794918 passed all205 required tests. Inspected
artifact retains768 settled observations and eight durable journal replays; the
new disconnected-owner driver refusal test is included. This native suite does
not execute the driver with a connected player. Separate work/memory-driver-player-
proof aligns PR85 candidatee7a6c89 (including its bounded final Bob wait) and adds
actual connected Alice four-edge execution, exclusive-driver refusal, frozen log
reload, selection-loss cancellation and connected Bob BUILD-revocation refusal.
The actual on-disk journal is exported and its completion marker is mandatory.
Scoped compileClientJava and shell syntax passed. Full CI remains required;
there is no new functional pass, production route, sampler or recovery.

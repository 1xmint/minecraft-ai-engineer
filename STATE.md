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

- phase/56-engineering-integration combines PR64,75,76,77,78,80,81 and the
  repeated-native-trial proof. Main is held stable at cd1188f until the exact
  combined head passes required checks. Source PRs stay open until integration
  lands. GitHub's strict up-to-date rule prevents independent stale-head merges.
- Full-adder gameplay1a265c6/41947057332 passed independent SQLite proof:
  3544cells,35pins,16cases×8samples, two epochs/fresh clocks and complete bounded
  undo. Prior69845d0/full41947347153 passed all5checks. Alignedec5e826/full
  41953618308 failed after Alice stopped at saved-world shutdown; no assertion
  failure was present in the captured client log. Preserve this failure and
  require fresh combined proof; do not label the failed run green.
- Plank source5b36258/player41955934297 passed; artifact inspected: four actual
  chat placements consume4actually crafted planks, native NBT remainders3/2/1/0,
  four persistent blocks after seventh boot and no restored authority/pass.
  Priorc40bf76 failed solely because dirt completion wording changed; corrected
  source preserves it. PR80 full41955959964 remains live independently.
- Claude76/77/78 passed all5checks; Fabric artifacts inspected (180native tests
  passed for guidance/comparison). Changes retain current notes, check loaded
  construction before advice and label comparison units/attempts honestly.
  Voice75 client checks passed; its dedicated job was still live at integration.
  Human microphone/speaker acceptance remains pending.
- Native trial history8f6b8bf/PR81/full41956628847 remains live. Producer stores
  detached recent64buffer attempts, updates same-attempt checkpoints and exposes
  observations only to authorized comparisons. Older archive files are retained;
  no grant, input journal or functional pass is restored. Scoped compilation and
  GPT-6-Luna review passed. aa67e59 adds two separately requested actual tests,
  exact12/16-game-tick counts, save deduplication and normal-chat comparison.
  Combined CI must prove this new repeat flow before timing claims are shipped.

## Next Codex slices

1. Finish green combined integration, inspect exact-source evidence and
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

Next E2 foundation: work/toggle-memory-foundation adds disposable native tests
for a one-bit copper-bulb toggle memory, with both initial bits/four rotations,
held/falling input, comparator output and original-bit restoration. Outcomes and
next production slice are in docs/toggle-memory.md. Pinned vanilla bytecode was
inspected; physical feasibility is not yet proven. No player-facing memory
contract or functional pass is claimed.

First native memory run41957728945 failed before any circuit was placed: the
new SNBT fixture omitted its required palette. Corrected fixture adds the same
air palette as the existing native labs; runtime memory behavior remains unproven.
Combined physical source3a6523e/41957260041 passed and its independent NBT/crafting
artifact was inspected, including four native plank placements and saved restart.

Memory run41958011579 reached all eight circuits and found the comparator facing
away from the bulb. The bulb state checks passed up to the output assertion.
Corrected FACING to WEST (vanilla DiodeBlock reads its rear along FACING, as the
already-proven AnalogDesign documents). New native proof is required; no pass
is inferred from that correction.

Native toggle-memory proof1be52d3/41958383492 passed. Artifact inspected:
199native tests, independent replay of768settled samples across both initial
bits/four rotations/six phases, with two pulses restoring original input/bit.
Production intention/conversation/construction, stateful stop/restart-safe
restoration and owner-authorized testing remain the next E2 slice.

Canonical toggle-memory intention work is on work/toggle-memory-contract. Shared
catalog fixes8cells/4stages/3roles and the intended six phases; Java and TypeScript
readers reject any changed source or identity. GPT-6-Luna implemented only the
companion creator/reader and rejection tests. Scoped TS compilation and Java
compilation passed; script syntax/format passed. The native fixture now builds
canonical cells and checks actual settled geometry. Source parity/rejection CI
is pending; this does not register a production blueprint or authorize testing.
Combined full-adder3a6523e/player41957262960 passed; artifact inspected:
3544cells/35pins, two passed epochs with fresh clocks and zero remaining assistant
changes after undo. General integration CI41957252832 remains live.

Canonical memory sourcecef919e/native41959507805 passed and both replay records
were inspected:8cells, untested/not-admitted source parity and768physical samples.
Next admission choice is under focused architectural challenge: expose intended
preview/build/teaching with explicit test refusal first, then add a separate
stateful trial/restoration protocol. Generic lever restoration cannot safely
restore an odd toggle or an originally high input. No production admission or
memory test path has been changed while that decision is being checked.

Memory admission decision (2026-11-05): expose exact preview/build/teaching first,
with explicit automated-test refusal; then implement a separate stateful trial.
GPT-6-Luna challenged the alternatives against Blueprint.parse, ComponentTree,
FunctionalTrial.start/restore/load and EngineeringSession.resume. Reusing only
the lever WAL is unsafe after an odd pulse; doing nothing leaves a proven circuit
unavailable. A separate typed trial is clearer than expanding generic lever
recovery with hidden stored-state assumptions. Both test and generic recovery/
load must refuse memory journals before admission. Existing stored bits must be
preserved during matching construction; strict undo conflicts must not be bypassed.

Memory roadmap (serves E2/E3):

1. Next: normal-chat preview/build/teaching with exact source validation and loaded
   two-block isolation. Proof: actual owner build, denied peer, preserved existing
   stored bit, explicit test refusal and saved-world reload without fake claims.
   Task: add admission/transport and guards first; then wire conversation (M1).
2. Next: typed stateful testing/restoration journal covering lever and bulb,
   event-time authority and explicit recovery. Proof: every phase interruption,
   both initial bits/four rotations, restart, revocation and conflicting human
   edits; no pulse replay or lost stored bit (M2). No generic restore fallback.
3. Later: data-and-clock registers and multiple memory bits/modules, with actual
   native state transitions, interaction/interference and saved-world proof (M3).
   A toggle memory never substitutes for those remaining requirements.
   Trial-history8f6b8bf/full41956628847 passed all5checks; dedicated artifact and
   actual12/16-tick results were inspected. Combined repeat proof remains live in
   PR82, alongside the already-passed combined physical/full-adder proofs.

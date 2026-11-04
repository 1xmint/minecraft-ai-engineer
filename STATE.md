# Current state

Updated 2026-11-03. Goal: **complete all milestones**, meaning the entire original
Phase 1 brief and all six Phase 2 extensions. The goal remains incomplete.
Requirements: docs/product-brief.md, including its multiplayer-first amendment.
Repository: https://github.com/1xmint/minecraft-ai-engineer.

## Decisions and constraints

- Dedicated Fabric server first, compatible logical-server core for solo play;
  authenticated connections, server-owned permissions/memory and optional overlays.
- Ordinary addressed conversation is the player interface. Commands are fallback
  and test interfaces; command-only proofs do not complete the experience.
- World observations, intended geometry and functional verification stay distinct.
  Native tick evidence is required for a functional pass. Imported geometry is untested.
- One private operator-owned companion on the operator's PC. No paid APIs, separate
  keys, hosting service, credential sharing or SDK/model calls in the current native
  execution workstream. Production Mojang login and shared subscription eligibility
  remain unproven; do not infer them from offline fixtures or archived receipts.
- Creative direct editing remains separately fenced. Native survival work requires
  fresh owner delegation, current human/worker connections, revisions, assignments,
  loaded workspace reservations and event-time native action/pickup tickets. Never
  supply/replace inventory, overwrite player edits, or restore authority on restart.
- Tests run on GitHub CI, never Josh's PC. Locally use scoped compile/lint/syntax
  checks only; one Gradle invocation at a time, --no-daemon --max-workers=1.
- Protected main, exact green PR heads, no bypass. Merge green work promptly.
  Keep active candidates stable while live CI runs; observation timeout is not failure.
- CI-only deterministic fixture quota is 24/minute and 96/hour. Production remains
  4/minute and 24/hour. Never raise production quota to accommodate CI.

## Outcomes still required

- E1: durable reasons, preferences, references, retrieval and useful project summaries,
  with attribution, revocation, replacement history and restart proof. Literal notes
  and explicit reason/replacement history are implemented; richer retrieval is not done.
- E2: larger connected engineering beyond current catalog/half-adder slices, including
  memory/registers/arithmetic/displays/computers, timing, interference and adaptation
  of existing structures. Geometry is not circuit correctness.
- E3: broader diagnosis/repair, timing explanations and visual guidance. Existing
  catalog/mechanical/analog/connected evidence and limited repairs are foundations.
- E4: actual human ten-step acceptance from the brief: signed-in preview, player builds
  the first section, AI finishes the rest, interruption/change/resume, real test,
  game/service restart and later explanation/visual proof. Scripted CI is not this
  human acceptance. Repackage verified work and prove production account feasibility.
- Phase 2: offline voice conversation; visible NPC constructing machines; genuine
  survival gathering/tools/crafting; practical schematic reuse/building; measured
  design optimization; multiple AI builders. None is broadly complete. Each needs
  real user-flow and refusal/restart evidence appropriate to its scope.

## Landed on main

- Public repo, pinned toolchain/CI, alpha.2 multiplayer preview and optional overlays.
  Alpha.2 is still the latest published release and predates recent integrations.
- Creative engineering, tutorials/shared components, bounded construction, permissions,
  conflicts, tests/repair/undo and durable server-side memory have scripted CI coverage.
  Human acceptance and arbitrary engineering capability remain unfinished.
- Connected half-adder: 436 cells, 20 pins, actual forward/reverse truth-table traces,
  conversation, revocation, interruption, saved-world retest and owned undo. PR45
  merged aaed3ef; exact source42deb3c CI41577981016 all five checks passed.
- Schematics: Sponge v3 codec/CLI, analysis, transforms and in-game library. Imported
  creative construction supports a pinned inert-material set plus five circuit
  exceptions; many real mechanisms remain unsupported. Files carry no authority/pass.
  PR49 merged 1d1dcb7; source7ba44a3 CI41581459104 all five checks passed. Actual imported
  build/restart/undo receipts and final independent SQLite verification inspected.
- Optional idle visible Carpet companion: PR47 merged ff4a021; source1fdbde7
  CI41682037419 all five checks passed. Focused earlier actual chat/render/disconnect/
  saved-world restart proofs exist. No NPC construction was claimed by this merge.
- Literal reason/replacement memory: PR48 merged f259fdc; source5340e4a
  CI41688991149 all five checks passed. Independent verifier checks exactly five notes,
  literal reasons and replacement provenance; refused writes create no rows.
  Claude's PR46 code was integrated through this own branch, not by editing his branch.

## Active integration chain

- PR50 / phase/33-physical-build: stable per-project worker identity, strict native
  save/load, separate physical leases, exact native mine/drop/pickup/place tickets,
  owner chat gather/retain/place and read-only recovery inspection. Source125b298
  passed full CI41688519989 and focused CI41688532963. Actual mirror/clone isolation,
  native pickup/save/restart, owner-Survival gather/retained placement, redstone-neighbor
  refusal and final independent memory checks were inspected. Combining with newly
  merged memory now requires fresh compile and exact-source CI.
- PR51 / phase/34-physical-blueprint: next accepted single-root stone/dirt cell uses
  retained inventory. Entire plan preflight, preceding-cell observations and all-plan
  read reservations; occupied cells refuse. Sourceae01437 passed full CI41688912887
  and focused CI41688767809. Actual blueprint cell placement and refusal receipts
  inspected. Geometry creates no functional pass or creative undo ownership.
- PR52 / phase/35-physical-blueprint-job: one fresh request alternates native gathering
  and placement for up to32 dirt cells with existing stone supports. Original source
  positions exclude all plan cells. Fixed owner/worker connections, blueprint and
  assignment fences persist across phases; expected geometry advances only after
  native placement plus verified save. Source8b9e015 focused CI41691988901 passed:
  actual survival owner, two initial resources, two intended cells, empty supplied
  inventory, no creative history/pass. Latest2c0b286 adds ordinary-chat stop and native
  owner-selection revocation coverage; local compile passed, new CI pending.

## Native execution limits and recovery

- Optional Carpet is pinned 26.3+v260915. No account lookup; worker profiles are private
  world/dimension/project/owner bindings. Missing/corrupt data or live UUID/name
  collisions refuse. An uninitialized profile retries the same UUID; existing native
  data is loaded, never replaced with empty inventory. No grants/tickets persist.
- Native pickup eligibility means the sole matching item emitted synchronously by
  the authorized playerDestroy callback. It is not a guarantee against arbitrary
  other mods spawning inside that callback. Unsupported entities/items are denied.
- Current executors are dirt-only, short three-wide stone-supported corridors and
  inert placement neighborhoods. Movement can be stopped on conflict; external
  physics is not universally contained. General navigation/materials/tools are open.
- Player data and chunks are separate saves. Clean stop/restart is proven for the
  bounded slices; arbitrary crash atomicity is not. PICKUP_PREPARED/PLACE_PREPARED
  may be unresolved. Never recreate items, compensate creatively, retry from matching
  counts, or treat missing data/drop visibility as proof of emptiness/consumption.
- Recovery inspection is owner-only, bounded and read-only. Stop cancels actions,
  verifies current saved inventory and retains an idle binding; failure despawns
  under default denial. A fresh request is required; interrupted records grant nothing.

## Next slices

1. Integrate memory with PR50; compile, fresh full/focused CI, inspect receipts, merge.
2. Bring PR51/52 onto integrated parents. Prove ordinary-chat stop, selection/revocation,
   human edits and restart/uncertain recovery before widening physical execution.
3. Expand resource handling/navigation and real blueprint mechanisms; then coordinated
   multiple builders. Do not call the current dirt prototype completion of Phase 2.
4. Continue E1/E2/E3 and the voice/optimization lanes; don't duplicate Claude's active
   work without checking branches. Schematics still need broader practical building.
5. Complete production feasibility and actual human acceptance, then package verified
   integrations. Keep the full original scope; no goal-complete claim from green CI.

Detailed historical logs, failed-run investigations and previous plans remain in
Git history. This file describes current decisions and evidence, not every attempt.

PR51 integration: sourceae01437 passed all five full checks and focused gameplay.
This branch now incorporates PR50's memory integration and ordinary owner stop,
including the separate actual-chat gather-stop scene. Preserve both the blueprint
placement and gather-stop receipts. New combined source needs compile and CI;
no previous green head is substituted for this combination.
Combined PR51 server/client compilation and shell syntax passed. Fresh full and
focused CI are required for this merge with memory and natural-stop coverage.

PR50 landed as a067f25 from exact source5cec203; full CI41696617329 passed all five checks and focused CI41696644888 passed. PR51 source8563b16 passed full CI41699264227 and focused CI41699293611. Main is byte-identical to the merged parent source; squash ancestry conflicts were resolved by retaining this branch's already-tested extensions. New integration head requires fresh CI.

## Memory retrieval integration (E1)

Outcomes: owner/authorized viewer can show/search attributed current and replaced notes through conversation after restart; revoked/unknown connections reveal nothing; the final independent database proof requires exactly six notes, including the newly saved reference, while all refused writes leave no rows.
Claude source70d973d (PR55) passed its compile, client, companion and planner checks and drove the full multiplayer flow; CI41729899854 failed only the final independent verifier, which still expected the five-note pre-retrieval fixture. Integrated current main9348e41 without changing Claude's branch. Updated verifier to require exact note6 reference text/schema/author/written-at revision and no invented reason/replacement; prior five notes and replacement provenance stay exact. GPT-6-Luna focused review found no concrete retrieval privacy issue. Fresh compile/format/syntax and combined-source CI required. M2/M3 remain separate and must bind current evidence to the full original scope rather than revision alone before integration.

## Now: larger connected engineering (E2)

Outcomes: (1) prove independent signals can cross in actual Minecraft without leakage, including all four inputs forward/reverse and consecutive native tick samples; (2) build and test a three-input full adder through ordinary conversation, with all eight truth-table rows and scope-bound evidence; (3) preserve half-adder v1 source/digest and refusal/restart authority. Start with the physical crossing GameTest because the fixed nine-NAND design needs reliable routing. A dedicated full-adder layout avoids prematurely building a generic circuit compiler or refactoring proven half-adder ports. The crossing probe grants no project authority and creates no saved functional pass. Crossing failure changes the routing design before full-adder admission is added. Tasks serve these outcomes: native crossing probe, explicit immutable full-adder layout and typed pins, v2 intention/trace validation, external-control-only native trials, and conversation/interference/restart proof. Current phase/42-full-adder is at the first probe; the whole E2 goal extends beyond this full adder.

Crossing source3af8e29 passed the Fabric job in CI41741392528. Inspected native-gametest.log: all64 actual clocked samples cover00/01/10/11 forward/reverse, both wire signals remain independent, and both original controls restore. This proves that single crossing only. GPT-6-Luna challenged the larger route: repeated crossings, repeater chains, ramps and all internal pins still need native proof. Added a separate fixed nine-NAND prototype with three external levers,35 typed pins, insulated overhead columns and eastbound buffered trunks, bounded to4096cells. Its native GameTest checks all eight arithmetic vectors forward/reverse and every pin for eight consecutive ticks after a conservative256-tick settling interval, including unchanged static geometry and restored external controls. No full-adder source admission or saved pass exists yet; the new physical layout is unproven until CI. Half-adder v1 remains unchanged.

Prototype07c263a native CI41742294637 failed rather than admitting bad arithmetic: all pins and static geometry matched at the first256-tick sample, then n2:in1 lost the n1 signal at257 on000. Added failure-only native pin/route observations; the same35pin, allvector and consecutiveclock assertions remain unchanged. No settling-window increase or relaxed pass. Native oak source3bc5158 independently passed focused CI41741356685, including physical-oak.done and physical-oak-restarted.done plus strict oak journal verification; combined native/main proof is separate.

Diagnostic sourcefd0548d CI41742700384 shows n1:out15, trunk x20/z21 power5, ramp power4, and every later branch repeater unpowered. The source descent and trunk consumed the remaining strength before the first upper repeater. Added a direction-specific repeater directly beside each trunk junction, with two flat cells before the insulated rise. This refreshes both source-to-trunk and trunk-to-sink signals without relaxing any arithmetic/clock assertion. Existing64-block generic blueprint extent can be preserved later by serializing this prototype about a centered anchor; no broad parser limit increase is needed.

Full-adder225cf57 focused native CI41777116255 passed: inspected actual128samples across16cases,35pins and3544cells. Added a deterministic version2 physical definition/digest with every native cell, typed pin and explicit nine-NAND dependency; geometry is centered at50/0/42 so every relative coordinate stays inside the existing64-block blueprint extent. Export happens only after the unchanged native all-pin proof and restored controls. This prepares shared source validation; it grants no execution or saved functional pass. PR65 landed d665570 after exact combined full41741571086 and focused41742617014 attempt2 passed. The player-experience combination is PR66; its code matches the already-compiled integration and needs fresh complete proof after main ancestry alignment.

Added bounded full-adder v2 trace/suite codecs: exactly35 typed pins at eight consecutive native ticks256..263, independently supplied graph digest/vector,16 forward/reverse cases with nonoverlapping clocks, exact recomputation of claims and first mismatch. Only settled observations are retained; no latency measurement is claimed. Native proof replays the actual captured frames, round-trips failures, and rejects extra authority fields, wrong graph/input, gaps/clock edits, bad pin types/strength, incomplete suites and repeated same-vector clock windows. These codecs validate observations only; current authority, component scope and restoration still belong to future trial integration.

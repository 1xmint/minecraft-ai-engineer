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

PR52 integration: native source2c0b286 focused CI41694607455 passed actual client
stop and twenty-server-tick world/inventory stability, selection-revoked native
mine denial, then the two-cell owner-Survival gather/build flow. Those receipts
were inspected. This branch now includes memory, the parent gather-stop scene and
both blueprint stop/build scenes. Preserve all four receipt families. New merged
source needs compilation and exact-source CI; prior proof is not reused as new.
Combined PR52 Java/client compilation and shell syntax passed after preserving
both parent gather-stop and child blueprint-stop tests plus the memory cases.
Fresh full and focused CI are required before integration.

Combined source a379a6b failed focused CI41700593882 during gather-to-place handoff: placement authority changed. Residual walking motion is the suspected cause, not yet proven. Gathering now keeps its lease and inventory snapshot until three stationary ticks, explicitly clears forward input, and summon clears prior velocity/fall progress. Placement stance and expiry diagnostics are separate; its guards remain strict. Scoped server/client compile and diff checks passed; fresh exact-source gameplay CI is required.

Focused CI41704667009 failed before production gather/job execution: the raw native pickup fixture crossed its platform boundary. Its second dirt source was on the last supported row. Added floor margin for native drop motion, aimed initial walking at the tracked drop, and added worker/drop/inventory failure diagnostics. Pickup identity, journal, expiry and rejection checks are unchanged. This fixture correction and the batch handoff still require exact-source CI.

PR50 landed as a067f25 from exact source5cec203; full CI41696617329 and focused CI41696644888 passed. PR51 source8563b16 passed full CI41699264227 and focused CI41699293611. This branch now includes PR51's main ancestry alignment and evidence update.
PR52 source50f9318 passed focused CI41705521330. Inspected actual two-cell completion chat and passed native-inventory-restart, gather-stop, blueprint-stop and blueprint-job receipts. The fixture margin and stationary handoff are gameplay-proven for this bounded scenario. Merging the parent changed only ancestry/STATE; fresh full integration CI remains required.

## Next phase: native navigation (E2 and Phase 2 survival/NPC)

Outcomes:
- Owner requests a resource beyond the straight corridor; the visible worker walks around an inert obstacle and hands off to existing exact native gather/place tasks. Proof: actual client chat, recorded route, native drop and saved inventory, no supplied items or teleports.
- Human edits, blocked steps, route deviation, disconnect, selection/revision/grant changes and stop cancel motion before another action. Proof: real dedicated-server refusals, unchanged protected blocks/inventory, fresh request required.
- Movement reservations prevent intersecting assignments without granting block mutation authority. Proof: occupancy conflicts and validateWrite refusal for occupancy-only cells.

Decision 2026-11-03: flat loaded-workspace navigation precedes more materials/tools. Keeping straight corridors leaves resource access impractical; expanding materials first multiplies action/drop/restart cases without solving access. Replacing the native actor/framework would discard the proven inventory/ticket boundary. Use bounded cardinal BFS and actual walking, initially flat inert terrain without jumps, doors, hazards or mechanisms. GPT-6-Luna independently challenged the proposal and identified that exclusive body occupancy must be separate from block-write permission.

Tasks: (O3) extend reservation occupancy with no mutation grant and exact conflict checks; (O1) pure bounded route search with captured support/clearance states and explicit edges; (O1/O2) movement executor validates current connection, full route states and swept occupancy each tick, then closes its lease before fresh existing gather/place admission; (O1/O2/O3) real obstacle-route and failure CI flows. Native mine/pickup/place tickets are unchanged. No route or authority is resumed from disk. This phase is planned, not implemented or proven.

Navigation implementation started: distinct exclusive occupancy leases do not populate block writes; conflict checks cover movement against reads, writes and other movement. Added native GameTests for no mutation/deferred-write grant, cross-scope conflicts, revocation and stale fences. Added pure bounded cardinal route search and a hand-worked twelve-step wall-detour/refusal test. Scoped compileJava and diff checks passed; tests have not run yet. GPT-6-Luna focused review found no concrete permission bypass. No runtime walking integration exists yet; next implement captured-state movement and real obstacle chat flow before calling any navigation outcome done.

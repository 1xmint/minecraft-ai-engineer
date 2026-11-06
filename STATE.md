# Current state

Updated 2026-11-06 UTC. Goal: **complete all milestones**: the original Phase 1
brief and all six Phase 2 extensions. **Incomplete.** Requirements remain in
docs/product-brief.md and its multiplayer-first amendment. Repository:
https://github.com/1xmint/minecraft-ai-engineer.

## Outcomes still governing completion

- E1: durable attributed reasons, preferences and references; relevant retrieval,
  replacement history, privacy/revocation, restart and useful later-session use.
- E2: engineering beyond fixed circuits: arithmetic, registers/memory,
  displays/computers, timing, interference and adapting existing structures.
- E3: teaching, diagnosis, repair and visual guidance grounded in actual state
  and scope-valid functional evidence.
- E4: signed-in human ten-step acceptance, production-account/provider
  feasibility, verified paired releases and useful later-session explanation.
- P2: offline voice, visible NPC machine construction, genuine survival
  resources/tools/crafting, practical schematic reuse, measured optimization
  and multiple builders. Bounded foundations do not complete these outcomes.

## Decisions and constraints

- Dedicated Fabric server first, compatible logical-server core for solo play.
  Natural addressed conversation is the interface; commands are fallback.
- Server-owned identity, permissions, memory scope, reservations and execution.
  Intended geometry, observed state and verified behavior remain separate.
  Imports and remembered claims cannot grant ownership, authority or a pass.
- Creative edits require fresh grants/connections/assignments, loaded delegated
  regions and conflict checks. Native survival additionally needs real retained
  inventory and event-time tickets; never supply or compensate materials.
- One private operator-owned companion beside the server, initially on Josh's
  PC. No separately billed APIs/keys, public hosting or credential sharing.
  Shared-subscription eligibility and production Mojang login remain unproven;
  model requests remain operator-owned. Approval is not provider permission.
- Tests/game runs only on GitHub CI. Locally scoped compile/format/syntax only;
  one Gradle process, --no-daemon --max-workers=1. Keep live CI heads stable;
  observation timeout is not terminal. Protected main, exact green heads and
  no check bypass. Merge green PRs without another approval.
- Production quotas remain4/minute and24/hour; CI fixtures alone use24/96.
- Claude owns memory/guidance/comparison/voice experience and isolated acceptance.
  Claude now also owns broader physical/builders, engineering and acceptance
  batches. Codex retains active toggle-memory recovery and final publishing.
  Project memory stays in AGENTS.md and STATE.md; no other handoff files.

## Landed on main

Main is b394362 (through Claude PR97/101/102). Latest published download remains **v0.1.0-alpha.2**;
it predates the later engineering features. No alpha.3 is published yet.

- Creative preview/build/test/repair/undo, per-player project access/conflicts,
  optional overlays, component assignments and durable attributed project notes.
- Sponge v3 codecs, inspection/comparison, rotate/mirror, library and imported
  creative construction. Imports remain untested geometry. Prior actual
  build/restart/undo proof41581459104 was inspected; default CI retains the flow.
- Half/full-adder construction and native truth tables, bounded undo, actual
  buffer transition records and repeat-attempt accounting. Full-adder combined
  player proof41957262960:3544cells/35pins, two epochs, fresh restart clocks and
  zero remaining changes after undo. Configured waits are not measured latency.
- Visible native actor and bounded flat dirt/oak work. Claude PR97/101/102 add
  resource accumulation, native2x2 planks/sticks/table crafting with explicit
  interrupted-grid recovery, and retained crafting-table placement. Their
  required checks passed; current combined physical proof is still pending.
  These are not3x3 tool crafting, general terrain or NPC machine builders.
- Durable literal notes/reasons/replacement history, read-only guidance and
  evidence consumers, optional editable unsent voice drafts. Human audio is
  unproven. Repeated counts are not a demonstrated reliability rate.

Memory-circuit foundation on main:

- Canonical eight-cell toggle memory, all rotations/both initial bits. Preview,
  teaching and creative construction preserve player bits and ownership.
  PR85 exacte7a6c89/full42051307049 passed all five checks; artifact inspected.
- Native paired-edge data, bounded write-ahead input journals, read-only
  96-sample stateful sampler and internal fresh-authority timed controller.
  PR87 exact8f4751a/full42058802843 passed all five checks. Inspected actual
  connected Alice:96samples/four edges over143ticks, exact original pair and
  durable RESTORED. functionalPass=false. Superseded PR86 is closed.
- A durable CURRENT unfinished-run pointer blocks new work, survives load
  without a lease and clears only after restored/no-edge proof. Read-only
  recovery plans follow that pointer, reject unexplained edits and derive at
  most three new edges. PR93 exactaf9e115/full42064271802 passed all five checks;
  timed/pointer/restart artifact inspected. Native42064151962 passed209tests
  and32 independent plan cases. **No recovery action or normal memory-test
  route is enabled; generic stateful testing still explicitly refuses.**

## Active work, not yet integrated

Player experience landed through PR95 at main121e710. Exact5c47da7 passed
full42073588087, acceptance42073589457, physical42073882984 and
full-adder42073883686. Inspected artifacts confirm private speech delivery,
unchanged52-block acceptance fixture/inventories, native crafting conservation,
and full-adder restart/undo. Source79/88/89/90/91/92/94 are closed as superseded.
Planning now uses request-relevant current notes; hardware/login remain unproven.

Recovery recorder, branch work/memory-recovery-journal:

- Exact3427a80/native42068459471 passed211required tests. Inspected independent
  replay:32saved recovery logs,48native fixture recovery edges,32original-pair
  restorations, frozen reloads and unchanged original histories. No pass.
- Recorder is data only. It is not a connected fresh-authority recovery driver
  and does not safely resume an interrupted recovery. Latest-recovery checkpoint
  binding and fresh event-time execution remain to build.

## Next slices

1. Finish current-main recovery checkpoint CI and integrate the proven foundation.
2. Bind latest recovery progress durably, implement fresh-authority recovery,
   prove interruption/revocation/restart and then enable normal conversation
   memory testing with scope-valid evidence. Never replay an old pulse.
3. Prepare accurate alpha.3 guides and publish the verified paired main artifacts.
   Do not replace exact-source manifests or claim human acceptance from CI.
4. Continue registers/data-clock memory, displays/computers, interference/timing
   and existing-structure adaptation. Broaden real resources, tools, crafting,
   terrain, NPC machine construction and conflict-safe multiple builders.
5. Complete provider/account feasibility, human mic/speaker and signed-in
   acceptance, then audit every original requirement before goal completion.

Historical failures, superseded notes and detailed proofs remain in Git/PR/CI
history and the feature guides. No full milestone-completion claim is made.

Current combined memory candidate (PR100; not merged):

- Native96-sample/four-edge testing with original-pair restoration, durable
  evidence, fresh actor/source/grant checks and live-only pass bindings shared
  by summary/guidance/lessons. Failed saves clear task/session bindings before
  rollback I/O. Source/assignment changes invalidate raw status too.
- Durable latest-recovery pointers and bounded strict journal chains. Explicit
  resume acquires fresh authority, restores the original pair and stops paused;
  no automatic retry, test, compensation or loaded execution authority.
- Fixed two reviewed evidence defects (scope/status disagreement and double-save
  rollback). GPT-6.1-sol reviewed those fixes and the recovery authority/routing.
- Full030db48/42121757231 failed at immediate post-pass load. Fixf6493b3 made
  that load succeed; full42125493607 then failed at later whole-server boot.
  Its artifacts prove actual approved chat testing:96 samples,9-event RESTORED,
  functional.done; chat recovery also matched the original pair. Reconnect and
  final full-recovery restart were not yet reached, so remain unproven.
- Root issue: generic stale display text was still interpreted as generic trial
  state. Storage fix599765f keeps generic verified=untested and stores bounded
  memoryVerificationStatus history separately. The unchanged generic loader
  rejects any generic scope/input/evidence; loaded history never creates a live
  binding, pass or runner. Narrow old native labels remain quarantined.
- Exact599765f/native42169765332 passed215 required tests; artifact inspected.
  Regressions cover general stale roundtrip, malformed/pass-looking history,
  valid generic-scope rejection, failed saves, source/generation/load invalidation.
  GPT-6.1-sol found no concrete authority/status/storage defect.
- Restart fixture now expects fixed non-current history, checks all runners are
  absent and retains pre-reboot field diagnostics. Merged mainb394362 cleanly
  at8deb70d; compileJava/compileClientJava passed. Final combined runtime checks
  and current physical companion compatibility are the next gate.

2026-11-05 provider documentation check (E4; no auth/code change): official
OpenAI docs describe eligible open-source/local and self-hosted ChatGPT-plan
usage, with registration scoped to a user/workspace. They do not establish
several Minecraft players sharing one operator's plan. Keep operator-only model
admission; no API keys/paid fallback or credential sharing. Sources read:
https://developers.openai.com/siwc/token-sharing-open-source and its
self-hosted-vms and preview-limitations pages. Technical support is not proof of
shared-subscription eligibility. Human audio/Mojang/provider gates remain open.

Combined physical42170313148 failed in Claude's table-placement restart fixture: it selected3031,101,1 inside the required3x3 clear summon pad, so the unchanged production guard correctly refused re-summon. Native craft/table placement itself completed. Fixture fix excludes the pad while preserving reach/inventory/block/restart assertions; compileClientJava passed. Physical workflow now triggers automatically for relevant main PR paths and AGENTS.md requires its actual evidence for physical changes, since default multiplayer skips that mode. Separate branch preserves live PR100 memory CI; corrected physical proof42171484381 passed; artifact inspected (details below).

Corrected physical proof42171484381 atacaedb9 passed and its artifact was inspected: retained table at3031,101,4 stays outside the summon pad; grid/table restart markers passed. Nine grid records retain two interrupted crafts and two explicit successful recoveries. Production guards remain unchanged. The memory guide now describes the actual PR100 candidate rather than superseded development stages; final combined multiplayer42170318694 remains live, so reconnect/recovery-restart completion is not yet claimed.

Combined candidate f0e9a31/full42170318694 passed all five checks. Downloaded and inspected the dedicated artifact: functional outcome passed,96 settled samples/four native edges/original pair restored; independent replay grantsCurrentPass=false. All memory done markers passed. Same-UUID reconnect rejected the old lease (INTERRUPTED,one event), then fresh recovery recorded RESTORED with seven events. Full restart epochs7b7d9171-a27f-42bf-8dd6-6db56d2ae0fa and a17ca6cb-df2b-4005-a33c-abd220d5c349 differ; approved fresh recovery restored the actual original pair, cleared pointers and stayed paused without a pass. Chat recovery also has seven-event RESTORED and actual pair equality. Pre-reboot generic verified=untested, no generic scope/inputs/evidence. Separate acceptance42170318842 passed with unchanged fixture/inventories. Consolidating the proven physical fixture fix and current guide into PR100; final exact-head checks remain before merge.

# Current state

Updated 2026-11-05 UTC. Goal: **complete all milestones**: the original Phase 1
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

Main is 121e710 (PR95). Latest published download remains **v0.1.0-alpha.2**;
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
- Visible native actor; bounded flat dirt/oak work, fixed one-log/four-plank
  native crafting and retained-plank placement. Combined physical41957260041
  and plank41955934297 evidence inspected: real materials and saved-world
  remainders. These are not general terrain/tools/crafting or machine builders.
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

- Durable latest-recovery pointers, bounded strict schema2 origin chains and
  schema1 compatibility; stale checkpoint-free sessions refuse to plan.
- Fresh connected recovery authority, journal-before-edge ordering, event-time
  selection/connection/grant/reservation checks and no automatic compensation.
- Explicit chat resume restores the original pair, clears both pointers and
  stops paused. Normal test requires96 live samples/four native edges,
  original-pair restoration, durable evidence and fresh authority before a pass.
- Live pass bindings pin exact source/revision/root basis/generation/record ID
  and are shared by summaries, guidance and lessons. They never load from disk;
  saved passes become stale. Failure clears task/session pass and binding before
  rollback I/O, including when both checkpoint saves fail. Reassignment also
  marks raw status stale. Offline replay cannot grant a current pass.
- Player fixtures cover partial recovery/session reload, revocation/selection,
  real same-UUID reconnect, chat resume/test and full dedicated-server restart.
  The last three flows and final pass-scope fixes still need combined runtime
  proof. These fixtures do not prove human audio or Mojang login.
- Reviewed by GPT-6.1-sol: recovery authority/routing had no implementation
  blocker; identified scope-status and double-save defects were fixed and the
  fixes reviewed. Native scope/source/generation/load/save-failure regressions
  passed215 required tests at52441db/42120494863 and e992df0/42120882739;
  artifacts inspected. Local Java compile passed for restart fixture774049e.
- Foundation exact3d7eec8/full42115690389 and separate acceptance42115690549
  passed; artifacts inspected:96 samples/four edges/original pair restored,
  persisted engineering proof, unchanged52-block acceptance fixture/inventories.
- Connected recovery exactfb08670/full42116822093 passed. Inspected record:
  INTERRUPTED first attempt, RESTORED second following its ID, revocation and
  selection-loss attempts each had one interruption event/no native edge, final
  fresh restoration succeeded. No functional pass came from recovery.
- Superseded runs42117395903/42117840340/42119500629 are being explicitly
  cancelled because reviewed fixes and full-restart coverage replace their code,
  not because observation expired. Confirm terminal before updating PR100 head;
  then run all required checks and inspect exact-head artifacts before merging.

2026-11-05 provider documentation check (E4; no authentication/code change): official OpenAI documentation now describes ChatGPT-plan usage for eligible open-source locally hosted apps and self-hosted VMs through Sign in with ChatGPT. Registrations are scoped to an authenticated user/workspace; the pages do not establish entitlement for multiple Minecraft players sharing one operator's plan. Shared-server model access therefore remains unresolved, and existing operator-only admission stays in place. No API key, credentials, paid service or live model request was used. Sources read: https://developers.openai.com/siwc/token-sharing-open-source ; https://developers.openai.com/siwc/token-sharing-open-source/self-hosted-vms ; https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations . A supported technical OAuth route is not proof of multi-user eligibility. Keep current PR100 CI head unchanged.

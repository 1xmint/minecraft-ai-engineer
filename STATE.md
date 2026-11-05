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
  Codex owns execution, circuits, resources/builders, integration and release.
  Project memory stays in AGENTS.md and STATE.md; no other handoff files.

## Landed on main

Main is cea719d (PR93). Latest published download remains **v0.1.0-alpha.2**;
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

Player experience, branch phase/57-player-experience-integration:

- Outcomes E1/E3/P2/E4: integrate79/27cedf8,88/a5a6eae,89/7473067,
  90/8dba429,91/b472af1 and92/9845de4. Source branches remain untouched.
- Trusted recipient-specific spoken replies; bounded owned-recording cleanup;
  literal relevant note selection; grounded full-adder lessons; comparisons
  retain each side's actual measurements without invented differences/rates.
- Codex also passes the validated build request into planning note selection:
  eight current notes from the newest32, replacements excluded, current grants
  retained. Empty-note planning contexts remain byte-identical.
- GPT-6-Luna reviewed voice provenance/cleanup: no concrete blocker. Hardware
  acceptance remains pending. PR92's failed reversed-order assertion is fixed;
  final Claude9845de4 is included, preserving the same stricter check.
- Pre-alignment sourceb84a9b0/focused42067282168 passed208native tests and
  note-selection/project-knowledge tests, including restarted planning relevance
  and denied access. Combined final full/client/isolated acceptance proof is
  still required after alignment with current main.

Recovery recorder, branch work/memory-recovery-journal:

- Exact3427a80/native42068459471 passed211required tests. Inspected independent
  replay:32saved recovery logs,48native fixture recovery edges,32original-pair
  restorations, frozen reloads and unchanged original histories. No pass.
- Recorder is data only. It is not a connected fresh-authority recovery driver
  and does not safely resume an interrupted recovery. Latest-recovery checkpoint
  binding and fresh event-time execution remain to build.

## Next slices

1. Finish combined player-experience full CI and separate offline dedicated
   acceptance on the exact current-main candidate; integrate green source heads.
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

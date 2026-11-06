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

Main is45a4b24 (PR100, including Claude PR97/101/102). Latest published download remains **v0.1.0-alpha.2**;
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
  required checks and combined physical42173685916 passed; artifact inspected.
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

## Latest integrated work

PR100 merged at45a4b24. Exact candidate59f4b66 passed all seven checks:
full42173686075, acceptance42173685772 and physical42173685916.
Artifacts were downloaded and inspected, including215 required native tests,
96-sample/four-edge memory testing, actual original-pair restoration, refused
old reconnect authority, and fresh recovery after a distinct server epoch.
Recovery clears pending data and stays paused without granting a pass.
Loaded history never creates a runner, lease or current functional evidence.
Failed saves and changed source/assignment invalidate live pass bindings.

Physical evidence retains real inventory conservation, native grid crafting,
interrupted explicit recoveries, table placement and saved-world restart.
The table fixture excludes the required summon pad; production guards are
unchanged. Relevant PRs now automatically run the Physical companion workflow.
Acceptance retains52 unchanged blocks/inventories, two owner-attributed notes,
revoked access and an actual companion process restart. Human audio and Mojang
login are not established. Source PR96/98/99 were confirmed included and closed
as superseded after merge.

Player experience previously landed through PR95 at121e710. Source
79/88/89/90/91/92/94 are already closed as superseded. Planning uses relevant
current notes. Full-adder, schematic and earlier proof details remain in Git/CI.

## Active work

Main CI42276076117 is validating merged45a4b24 and building the paired artifacts.
Do not publish PR test-merge artifacts as main. Latest download remains alpha.2.
Codex owns final artifact inspection/publication; Claude owns the broader
survival/builders/engineering/acceptance batches described in the current brief.

## Next slices

1. Inspect merged-main CI and paired artifacts; publish an accurately bounded
   alpha.3 after exact-source checks pass and release guides are reconciled.
2. Continue real tools/3x3 crafting, terrain, visible machine construction and
   concurrent workers through Claude's ownership batches.
3. Continue registers/data-clock memory, displays/computers, measured timing,
   interference and adaptation of existing structures; the toggle bit is not
   completion of general memory or engineering.
4. Complete provider/account feasibility, human mic/speaker and signed-in
   acceptance, then audit every original requirement before goal completion.

Historical failures, superseded notes and detailed proofs remain in Git/PR/CI
history and the feature guides. No full milestone-completion claim is made.

2026-11-05 provider documentation check (E4; no auth/code change): official
OpenAI docs describe eligible open-source/local and self-hosted ChatGPT-plan
usage, with registration scoped to a user/workspace. They do not establish
several Minecraft players sharing one operator's plan. Keep operator-only model
admission; no API keys/paid fallback or credential sharing. Sources read:
https://developers.openai.com/siwc/token-sharing-open-source and its
self-hosted-vms and preview-limitations pages. Technical support is not proof of
shared-subscription eligibility. Human audio/Mojang/provider gates remain open.

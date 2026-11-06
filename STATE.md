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

Main is d9c9c46 (PR100/103/104). Published download:
[v0.1.0-alpha.3](https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.3),
tagged to d9c9c462e0d16fe0b4aa0ce0aa52a77b0e650771 on2026-11-06.
Final main CI42286129105 passed all five checks. Native log:215 required tests;
inspected multiplayer:96 samples/four edges/original-pair restoration, all nine
memory markers, old reconnect authority refused and fresh recovery after distinct
server epochs. Both outer artifact hashes and106 inner file hashes match;
no unlisted files. GitHub asset digests match. The release has paired ZIP/JAR,
BUILD.json and SHA256SUMS, with corrected bundled guides. This remains a bounded
prerelease and does not complete the overall goal.

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
  and32 independent plan cases. Dedicated native testing and recovery are now
  enabled through PR100; generic stateful testing still explicitly refuses.

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

Alpha.3 publication is complete. Codex retains final integration and the overall
requirements audit; Claude owns the broader survival/builders/engineering and
acceptance batches. No current implementation PR is owned by Codex in this lane.
Human audio/Mojang/provider feasibility remain unproven; broader features below
still require implementation and actual evidence.

## Next slices

1. Exercise the published alpha.3 through the remaining human acceptance and
   account/provider gates without treating offline CI as that proof.
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

## Remaining-requirement audit — 2026-11-06

Decision: retain Claude's implementation ownership and use actual end-to-end
proof as the completion gate. No architecture rewrite or duplicate implementation
lane is warranted by this audit. A delegation brief is not evidence of progress;
at this check no new feature PR is open. Codex's release-link PR105 is in CI.

Outcomes for the next work:

- A player obtains materials, crafts useful tools and visibly builds a working
  machine through normal conversation; prove conservation and interrupted recovery.
- A larger project recalls relevant older decisions and supports measured design
  choices; prove relevance/privacy/restart and report unknown measurements honestly.
- The signed-in human ten-step workflow and real voice hardware are demonstrated;
  offline CI and recorded audio cannot substitute for them.

Requirement mapping (partial evidence is not overall completion):

- Original1/natural language and5/teaching: addressed chat, grounded explanations,
  staged lessons and optional overlays exist. Full signed-in human/model ten-step
  handoff, detailed orientation teaching and safe out-of-order construction still
  need requirement-matched acceptance rather than command-only fixtures.
- Original2/inspection and3/blueprints: bounded loaded snapshots, exact native
  states, component scopes, geometry imports and revisions exist. General existing
  structure understanding, interface inference and wider environment adaptation
  remain incomplete; source templates alone do not prove arbitrary mechanisms.
- Original4/design and8/debugging: native reference/adder/toggle tests and bounded
  diagnosis/repair exist. Registers, data/clock memory, displays/computers and wider
  timing/interference diagnosis remain open. catalog/toggle-memory.json explicitly
  describes a toggle bit, not a data/clock register. Measured optimization must use
  actual transition/trial records, not phase waits or geometry.
- Original6/building and7/collaboration: creative bounded assignments, conflicts,
  undo and restart have CI evidence. PhysicalBlueprintJob still caps32 dirt cells;
  GridCraftPlan supports only planks/sticks/table in2x2. General tools, terrain,
  redstone-machine physical execution and overlapping workers are not implemented
  by those foundations. Prove separate inventories/reservations and human conflicts.
- Original9/interruption and10/safety: current native test/recovery uses fresh
  authority, durable journals and explicit resume. Full product acceptance still
  needs the same guarantees across new executors and larger compositions. Before-
  state journals are not automatically proof of all requested large/destructive
  backup behavior; audit that requirement before claiming completion.
- Durable knowledge: reasons/replacement history/relevant selection are integrated.
  GatewayMemory.knowledge defaults to newest32 before currentNotes ranks entries;
  older relevant requirements can be absent from planning. Whole-history explicit
  show/find exists, but that does not finish automatic later-session relevance.
- Phase2 voice: offline drafts/authenticated speech are integrated; human mic,
  speaker, interruption and quit acceptance remain unproven.
- Phase2 schematics: codecs/transforms/library/imported creative construction are
  integrated. Practical resource-aware physical reuse remains incomplete.
- Phase2 NPC/resources/multiple builders: bounded actor/resource work is proven;
  complete tool/crafting/navigation/machine and true concurrent-builder flows remain.
- Phase2 optimization: consumers show available observations and unknowns; actual
  comparable measured alternatives and repeated-trial evidence remain required.
- Feasibility: private operator-owned model admission remains. Signed-in Mojang
  login and official shared-subscription eligibility are unproven; no paid/API-key
  fallback or credential sharing is authorized.

Now: PR105 documents published alpha.3. Codex awaits its exact-head CI and the
human account/audio availability answer. Claude's next implementation gate is the
complete native survival crafting/tool chain, then a visible functionally tested
machine. Broader batches remain as assigned; require merged commits, exact CI,
retained evidence and limits before updating their completion status.

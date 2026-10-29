# Now

2026-10-29. Active goal: **complete all milestones**: the entire Phase1 engineering
partner and all six Phase2 features. A catalog, screenshot, release or green CI
never substitutes for that full objective. Original requirements and multiplayer
amendment: docs/product-brief.md. Public repo:
https://github.com/1xmint/minecraft-ai-engineer.

Current branch **phase/15-piston-motion**, based on PR23 main merge
**d3167a8f0a13c69ed6e301555802481a4bef559d** (2026-10-29T22:05:12Z). Phase14 final
source **d6a4f3da151425fdce4b5c8f0f053cff3b9c70f1** passed all5 CI40961971444 before
merge. Its first conversation/knowledge slice was also proven on b858364 by all5
CI40959246919 and whole signed-in manual40960496759. Phase15 is building its
native physics oracles, not a shipped piston feature. Full objective unfinished.
Inspect actual git/PR/workflow state before continuing.

Josh: **skip heavy reviews; build and ship**. Public implementation, pushes and
merging green PRs are authorized; no approval pending. Tests run on GitHub, never
on his slow PC. Locally scoped compile/format/syntax only; one Gradle worker.
No separately billed service/API key. Only the configured account-owning operator
invokes AI. Keep project memory only in AGENTS.md and this file.

## Phase15 plan: mechanical output and bounded piston motion

- M1: a real sticky-piston shutter closes/opens an aperture when its lever is
  toggled. Proof: native Minecraft26.3 tests in all horizontal rotations, actual
  payload/head positions and settling; ordinary piston leaves the payload behind,
  obstruction/excess push does not invent movement.
- M2: queued motion cannot expand AI work into undelegated, unloaded or changed
  space. Proof: actual vanilla resolver sources/destinations/head/base, rejection
  of destruction/extra payload/conflicting regions, human edits, revocation,
  delayed events, moving-block finalization and restart. Distinguish direct writes
  from physics commit/finalization; no restored lease or player-edit overwrite.
- M3: preview, learn, explicitly build and functionally test the shutter as a
  project component. Proof: dedicated native commands, authorized/denied sharing,
  interrupt/reconcile/restart and actual aperture observations. New model/block
  allowlists require the validated contract. Geometry alone cannot pass or stand
  in for the remaining larger piston-door capability.

Decision2026-10-29: reuse vanilla PistonStructureResolver for movement projection,
with server-thread reservations/WAL. Start with a complete shutter slice toward
doors. Merely adding piston states leaves deferred physics unguarded; a fake
player adds Phase2 machinery without solving that boundary. Leaving mechanics
unsupported cannot finish the product. Do not widen public construction yet.

Research: Mojang's push/pull/12-block description:
https://www.minecraft.net/en-us/article/block-week-piston. Fabric event/mixin hooks:
https://docs.fabricmc.net/develop/events/. Exact26.3 behavior comes from the pinned
common jar and native tests. Local javap: checkIfExtend queues blockEvent;
triggerEvent calls moveBlocks; PistonStructureResolver exposes resolve/toPush/
toDestroy/pushDirection. PistonMovingBlockEntity finalizes on later ticks only
while its destination remains MOVING_PISTON. Current leases guard direct writes
and current trials assume lamp outputs. This needs a spatial transaction around
deferred physics, with explicit commit/recovery semantics.

Tasks: native physical oracles first (M1/M2); typed movement footprint and failure
proofs (M2); deferred-physics authority/recovery boundary before enabling blocks
(M2); mechanical contract/execution/trials and native component flow (M1/M3);
required CI and narrow auth/mutation check, then ship when Outcomes pass. Tests
only on GitHub; scoped local compile. No heavy reviews. Current work: physical
oracles. Production piston construction/generation is not implemented yet.

Eight native piston oracles are written: four rotated repeated shutter cycles,
ordinary piston negative, obstruction, changed payload footprint and external
replacement during real animation. Scoped Java server/client compilation passed;
no local tests run. GitHub runtime proof is next. Public block allowlist unchanged.

## Proven Phase14 Outcomes

- C1: explicit free-form selected-project/component engineering question gets
  grounded read-only explanation, clarification or unsupported reply. Native
  command, bounded provider, sources and no invented world authority are proven
  for the fixed explanation flow. Existing cached lesson asks stay offline.
- C2: explicit owner goals/constraints/preferences/decisions persist and inform
  later reasoning. Connected owner append, authorized member read, immutable
  authorship/revision, optimistic append/replay, SQLite rollback/corruption and
  actual service/game restart are covered. Knowledge is separate from geometry
  and measured evidence, with no private inventories.
- C3: current owner/operator/connection gates before model dispatch; queue/budget,
  durable replay, pre/post knowledge fences and stale native callback checks.
  Stop and knowledge changes cancel reasoning; late results cannot be accepted.
  Current project/auth/epoch, actual connection, selected lesson component/stage,
  configured operator and dimension are checked again at callback time.

Implemented: `/engineer remember <goal|constraint|preference|decision> <literal>`;
`/engineer notes`; `/engineer discuss <question>`. Notes are append-only, max512
characters, current read32/latest display8; older history stays stored. Recent8
notes inform design reasoning and discussion, explicitly labeled owner-authored
requirements, never observed/verified facts. Knowledge revisions are independent
of blueprint revisions. Queued dispatch and completed results require the same
knowledge revision; note edits abort active inference. Empty-note design context
remains byte-for-byte compatible with older signed-in receipts.

Discussion uses intended project/component sources and recent notes, no fresh
world observations or private inventory. Reply schema: explanation/clarification/
unsupported, bounded text and supplied-source IDs. Model text is literal advice,
never commands, execution authority, a lesson change or a test result. Accepted
conversation references are audit records. No automatic retry. Owner notes are
explicit; no inferred/automatic memory summaries yet.

Standalone mechanisms expose logical `root`; internal recipe block-group names
are not selectable project components. CI caught and fixed that real mismatch.
The manual proof artifact allowlist also needed its new -discussion suffix;
focused GPT-6-Luna/high auth/transport checks caught it. No broad review cycle.

## Phase14 proof and current gates

- Foundation fc5b9e3: all5 CI40917639392; strict literal codec/storage and failures.
- Native notes9198c2a: all5 CI40956149245; owner record, editor read/denial,
  revocation, actual later boot read revision2 and append revision3, unchanged
  blueprint/world evidence. Artifacts work/knowledge-proof-40956149245.
- Conversation/cancellation c9b011d: all5 CI40958107235; deterministic native
  revision2/3 answers; provider waits for real HTTP cancellation then deliberately
  returns late, which is discarded. Exactly2 accepted audits/3 explicit starts.
  Actual companion4392->4529 and later server boot. Artifacts
  work/discussion-proof-40958107235. These were fixtures, not fresh inference.
- Exact live-proof sourceb858364: all5 CI40959246919, inspected native logs at
  21:42:34/21:44:17 UTC and passed cancellation/recovery markers. Artifacts
  work/discussion-proof-40959246919. SDK/CLI0.164.0; no local test suites run.
- **Fresh whole manual40960496759 SUCCESS**, response40961355992 SUCCESS on that
  same source. Exactly one official SDK/Luna discussion call,10919 input/140 output.
  Minecraft received it21:52:41 UTC. It cited both literal owner notes, preserved
  the six-block entrance constraint, stated that visibility rationale was not
  recorded, and claimed no evidence of construction or testing.
- Native editor read/denial, revocation, real companion4709->5253, saved-server
  reboot and revision2 read -> revision3 append passed. SQLite required one
  discussion dispatch/answer and all3 literal owner notes; no block writes,
  blueprint relabeling or functional evidence invented. Both live-discussion
  markers passed. Artifacts work/live-discussion-proof-40960496759.
- Request digest2b1b24e69d36bfef9e32957cd6806b74b21e42b13d3e9c7efaafd792b4e80cb5;
  result5a666a4b4fdf67b866c095230571455c0d03255009dda02b3c96f82654d248e6.
  Private started/untrusted/response receipts remain under
  AppData/Roaming/minecraft-ai-engineer/acceptance. **That call is consumed; no
  further SDK calls are needed for this slice.** Never reuse a started receipt.
- Exact synthetic request/reply is tests/fixtures/live-discussion.json; offline
  archive validation at its historical time never authorizes another request or
  replays it into a different project. Screenshot inspected and copied unchanged:
  docs/images/grounded-discussion.png. It proves received advice, not construction.

Manual bridge: only the fixed synthetic notes/canonical intended lamp context,
question, connected owner, run/source/stage/digest and15-minute expiry. GitHub gets
bounded data only. Local responder alone invokes signed-in Codex. Durable exclusive
started receipt forbids another model call; publication may retry a saved valid
response. Normal native discussion deadline100s; CI-only manual exchange900s.

## Shipped foundations and earlier evidence

Dedicated Fabric server first, integrated-compatible logical-server core;
server-owned identity/project ownership/permissions, real connection/epoch fences,
shared authorized blueprints, private per-player tutorial cursors, component
assignments and bounded write/read reservations. Creative build/repair/undo,
targeted inspection and private inventory reads. Intended/observed/verified remain
separate. Disconnect/revocation/conflict/restart never restores an execution lease.
Optional overlay currently shows colored bounds/labels, not textured ghosts.
Published alpha remains the older single-player reference, not these source features.

PR19 structural revisions merged1e97bc3; b17ba07 all5 CI40797460992. Native old/new
diff, explicit owner expansion/apply, no-edit preview, conflict/stop, third boot,
12/16 timing, inverse repeated-position undo preserving player supports.
PR20 restored layouts merged55a4858; 8c4e21e all5 CI40801344596. Fourth boot retains
true old IDs; stale/corrupt/changed-base requests reject before quota. Undo retains
immutable restoredLayout; operation scopes bind all captured physical bases.
PR21 signed-in structural proof merged e3f26d8: whole40806992766 SUCCESS on ccd89d2,
all5 CI40806790413. Actual player first support, AI rest, modification apply,
stop/client conflict/removal/resume, real12/16 timing, service/game restart,
shared lesson/revocation. Replay a504014 all5 CI40808845287. Failed40805062670 is
retained: reversed repeaters matched geometry but failed actual timing. Four SDK
calls consumed; no more for that slice. Repeater facing names its INPUT side.

PR18 buffers mergedbebf6ca; final076e4dc all5 CI40768389557. Actual40766098371
accepted raw8-block12/16 buffer and timing/sharing/restarts, but whole run failed
at creative inventory fixture cleanup; corrected replay passed. Never call that
whole manual run successful. Retained screenshots show actual blocks.

PR22 Boolean logic mergedbb4e81f: finala3a3fcf all5 CI40913824351. Whole actual
manual40910817937 SUCCESS on d3c8486/all5 CI40908760538. Luna behavior10795in/97out,
Sol geometry12322in/1020out;13 rawblocks, named AND a/b0001, settle20. Separate
owner confirm, preview with no edits, build, all8 actual forward/reverse rows,
service4778->5851, revocation interruption, third boot, fresh resume, original
a ON/b OFF restoration and retest passed. Trials16:30:43/16:31:11 UTC. Synthetic
lever/backing section was seeded, not human mouse placement/Mojang auth.
Exact positive/rejections: tests/fixtures/live-boolean-_.json. Failed40906470078:
missing wall-lever backing and false serial-AND claim. Failed40908843817: overlapping
torch/lamp. Rejected locally, no construction; cancelled after terminal rejection.
All6 Boolean calls consumed; no more for Phase13. Artifacts work/live-boolean-proof-_
and docs/images/boolean-restarted.png retain evidence. Replay is not fresh inference.

Boolean behavior: schema2 truth-table,1..4 unique named inputs, complete2^N Boolean
rows MSB-first, settle0..128 plus8 stable ticks. Digest truth-v2|names|bits|settle;
legacy buffer-v1 edge identity unchanged. Generated outer schema1/revision2 binds
named actual levers/lamp with no hidden ports. Actual every-tick traces reject
transients, gaps, swapped rows/input vectors and forged verdicts. Restoration WAL
survives interruption; fresh authority is required for resume. Geometry cannot pass
function tests. Timed buffers preserve independently accepted rising/falling edges.

## Runtime and operating decisions

Java25 / Minecraft26.3 / FabricLoader0.20.1 / API0.165.0+26.3 / Loom1.17.21 /
Gradle9.6; Node24.14 / TypeScript / SQLite / official SDK+CLI0.164.0. Toolchain:
task work/fabric-tools/jdk-25.0.5+8. PowerShell login:false; max-workers1.
Tests only on GitHub; locally scoped compile/format/syntax. Protected main has5
required checks and zero required reviewers. Merge green focused PRs.

One private localhost companion beside Josh's server/PC, no hosting purchase,
public AI endpoint, credential sharing or separately billed fallback. General
shared multi-user subscription eligibility remains unresolved; operator approval
is not provider permission. Configured account-owning operator alone invokes AI.
Planner forces ChatGPT login/openai provider, disables tools/apps and uses isolated
read-only SDK work. No private inventories in shared model context/SQLite/audit.
Model policy: Luna for catalog/behavior/buffers/discussion; Sol medium for Boolean
geometry. Explicit MCE_MODEL overrides defaults, MCE_GEOMETRY_MODEL overrides only
geometry. Doctor catalog probe does not prove Boolean-model availability. No retry.
Official SDK/structured output docs rechecked before the discussion proof.

## Remaining full roadmap

1. Broader electrical/mechanical engineering: comparator/observer/piston behavior,
   signal strength/attenuation, connections, mechanics and larger systems. Preserve
   functional game-tick proof, movement dependencies and player edits; no
   buffer/catalog/Boolean-only substitute for the engineering partner.
2. Remaining E1: natural action interpretation, broader follow-up/recent-reference
   handling, durable rationale/summary retrieval and later project identification.
   First discussion/explicit-note slice does not complete this requirement.
3. E3: broader missing/orientation/connection/timing/interference/inconclusive
   diagnosis, explicit repair previews and textured directional ghosts.
4. E4: full signed-in preview -> player functional section -> AI rest -> interrupt/
   modify/resume -> actual tests -> service/game restart -> later explanation and
   richer visuals. Bounded proofs do not establish that complete experience.
5. Phase2, all unstarted: voice; physical NPC; genuine survival gathering/placement;
   schematic import/export; measured size/resources/latency/reliability optimization;
   multiple coordinated independent builders.
6. Completed multiplayer release/package/setup instructions; published alpha is
   still the older single-player reference. Production authentication and shared
   subscription eligibility require evidence, not synthetic offline player proofs.

Next after PR23 ships: establish mechanical behavior contracts and piston-motion
failure boundaries before expanding the generated block allowlist. Write the next
slice's Outcomes from actual source/mechanics research; retain all full milestones.

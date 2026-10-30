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
CI40959246919 and whole signed-in manual40960496759. Phase15 is building restored
denial and explicit reconciliation after physics, snapshots and the event gate, not a shipped
piston feature. Gate sourcedc08309 passed all5 CI40965363414, including all60
required native tests (job log22:23:20 UTC inspected). Snapshot sourced04e1fc
previously passed all5 CI40964080695/all53 native tests.
Draft PR24 holds Phase15. Full objective unfinished.
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

Pinned source inspection corrected the initial oracle: moveBlocks(false) removes
the piston head before constructing the vanilla resolver. Calling resolve(false)
against the untouched extended world is not a valid pre-transition projection.
Removed that incorrect API assertion; all physical pull/reopen postconditions
remain. Production retraction projection must explicitly account for the head
prelude without editing the world merely to preview it. This changes M2's design;
an unqualified resolver call is insufficient. Initial6ed2f7 CI40963224460 Fabric
failed on that assertion in all four rotations. It also exposed the default20
tick harness deadline for the planned24-tick ordinary-piston test. Give the40-tick
cycle tests50 ticks and the24-tick negative35 ticks, retaining all physical
settling assertions. Other jobs were still live; the new source needs full CI.
No production piston support is claimed.

Corrected0bf063a Fabric job passed all50 required native tests in CI40963599032,
at22:11:17 UTC. This includes all8 piston oracles; inspected the actual job log.
Other runtime jobs were still live at inspection, not claimed complete.

ShutterMotion now captures immutable base/head/payload before and terminal states
for the fixed one-stone sticky shutter, with no writes. It checks delegated cells,
loaded chunks, horizontal orientation, exact stone/head/air poses, no block entity,
and vanilla's exact single-source non-destructive extension projection. Retraction
accounts for the head-removal prelude without mutating preview state. Freshness
recaptures the live basis; endpoint comparison is explicitly not tick-history
verification. Snapshot holds level identity and immutable coordinates; it grants
no execution authority. Production build/generation paths remain unchanged.

Three new native tests cover actual matching terminal poses, immutable/mutable-
coordinate isolation, stale/replaced/undelegated cells and no chunk loading.
Scoped Java server/client compilation passed; new runtime proofs await GitHub.
Next: integrate deferred-motion commit/finalization fences and recovery before
exposing piston construction, then complete M3's actual player flow.

Event gate core now compiles: a required JAVA_25 mixin intercepts triggerEvent
before vanilla head removal or payload mutation. Only server-internally armed
positions are gated; unmanaged pistons keep normal behavior. A one-use ticket
checks exact event/facing/pose,20-tick expiry, caller's current authority, live
snapshot and vanilla power signal. A synchronous write-ahead hook precedes commit;
authority/snapshot/power are rechecked after it. Reentrant/cancelled/duplicate
events fail closed. Stop before commit rejects movement; cancellation after
commit observes already-started animation without granting compensating motion.
Server shutdown clears the ephemeral registry. Endpoint observation is not a pass.

Seven native gate proofs written: actual close/pull commits with fresh rearm,
revocation after lever write, intervening replacement, failed journal hook,
cancelled input pulse, expired arm and cancellation during actual moving-block
animation. Scoped server/client Java compile passed. GPT-6-Luna/high narrow
mutation-boundary check found no concrete core blockers; no tests/SDK were run by
the helper. All seven gate proofs passed in CI40965363414. Production construction/generation still
does not arm the gate. Durable WAL/session/permission integration, read-dependency
closure, passive release and restart recovery are NOT implemented by this core;
they remain required before M3 or public piston support can ship.

ShutterJournal core now compiles: bounded strict versioned descriptions bind
world/dimension/project/blueprint/revision/component/operation plus accepted
piston position/facing and motion UUID. Phases prepared/commit-recorded/endpoint-
observed and cancellation are physical facts, never leases or functional passes.
Terminal states are derived from the fixed mechanism's validated before-image;
authority/lease/verification fields reject. A fresh server-derived Scope must
match before inspecting recovery. Unloaded/moving/replaced cells stay explicitly
unknown/unsettled/conflicted. Reading the record performs no writes or rearming.

New native tests persist preparation, write the commit via the actual gate's
pre-motion callback, reload from disk, record a later endpoint, and prove no
implicit retraction permit. They also reject forged payload/authority, foreign
scope/wrong motion/cancelled preparation and preserve a replacement. Opening-pose
codec coverage and all rotated snapshot/actual terminal comparisons are added.
Shared pure pose validation keeps live snapshots and stored descriptions aligned.
Scoped Java server/client compile passed; these new runtime proofs await CI.
This proves neither actual saved-server recovery nor production session wiring:
startup denial before queued events, input restoration, read-dependency capture,
fresh actor permissions and safe release still must land before public actuation.

Journal source08f4431 Fabric job passed all62 required native tests in
CI40966805528 (22:34:34 UTC, actual job log inspected). Four checks passed;
dedicated multiplayer was still live at last inspection, not claimed terminal.

Restored denial core now compiles. A sealed Blocked entry stores only expected
scope/motion identity; registration checks current accepted identity without
reading/loading chunks. Ordinary arm refuses it. Explicit rearmBlocked requires
the exact registered journal, fresh authority, a fresh matching frame and observed
before/terminal pose, checks the slot again after callbacks, then creates a new
ticket. No actor, callback, lease or permit is deserialized. A focused
GPT-6-Luna/high recovery-boundary check found no core blockers.

Three native proofs added: disk reload -> actual queued-event denial -> denied
resume -> fresh ticket/commit; foreign/changed journal binding rejection; no-load
registration for an unloaded piston. Scoped Java server/client compile passed;
these new runtime proofs await CI. This is not yet actual server-bootstrap or
saved-world restart recovery. The loader must derive expected Scope from the
accepted mechanical component, never copy identity from the saved journal to
validate itself. Add that canonical mechanical contract before startup wiring;
then bind current player permissions/input restoration and prove the full boot.

## Proven Phase14 Outcomes

Phase15 checkpoint: restored-denial source612ab9d passed all5 checks in
CI40967730065; actual Fabric log22:42:31 UTC confirms all65 required tests passed.
The dedicated multiplayer job completed successfully22:48:19 UTC.

Canonical shutter intention is now shared by TypeScript and Java through
catalog/shutter.json and an exact cross-runtime fixture. Both derive accepted
geometry, reserved aperture air, behavior, resources and recovery identity from
the recipe; unknown authority/verification or rewritten metadata rejects. This
provides the independent accepted frame required by restored journal validation.
Five native checks cover the shared identity contract and actual recipe-built
open/close/reopen in four rotations; companion checks cover bounds, metadata,
aliasing and the fixture. Scoped TypeScript and Java server/client compilation
passed; new tests await GitHub. No local test suites ran.

Public planning/construction remains disabled for this mechanism. Next: wire the
mechanical dialect into project validation and execution, then startup denial,
current player authority, input restoration and actual saved-server recovery.
This checkpoint does not finish M3 or the overall roadmap.

Read-dependency closure: accepted shutter designs now derive all five potential
write cells (including reserved aperture air), plus a rotated240-cell read volume
covering the mechanism and two-block isolation margin. ShutterEnvironment checks
the complete reserved/loaded volume before any reads, captures fixed input/support
and air dependencies separately from changing motion cells, and can recheck the
expected input state at event time. It grants no permission or lease. New native
proofs exercise a late player edit causing actual gate denial before WAL/physics,
preservation of that edit, incomplete read reservations and no chunk loading.
Production session reservations and journal recovery still need this integration.
Scoped Java server/client compilation passed; these new tests await CI. Contract
source4ab3cf3 has four successful CI40993844465 jobs; dedicated multiplayer remains
live at inspection, so the whole run is not yet claimed successful.

Reservation integration: RegionReservations.validateFootprint now checks the
normal epoch/actor/project/auth revision/expiry fence before requiring all
potential motion writes in the lease's write set and all dependencies in its
read/write closure. ShutterEnvironment.captureReserved derives project scope and
revision from accepted design, validates those exact footprints, then captures
only against the lease's actual reserved cells. This does not replace the
execution caller's current Minecraft connection or project-grant checks.
Two new native tests cover actual event denial after lease revocation, missing
aperture write authority, missing dependencies, neighboring-project conflicts,
wrong actor and changed grants. Scoped Java server/client compilation passed;
new proofs await CI. GPT-6-Luna/high focused reservation check found no concrete
boundary blockers; no helper tests or edits. Production session wiring remains.

Isolation source23377a1 Fabric passed all72 required native tests in
CI40994181567 (actual job log02:53:49 UTC inspected). Four jobs passed; dedicated
multiplayer is still live at inspection. Prior contract-only CI40993844465 was
superseded/cancelled after its other four jobs passed; never claim it all-green.

Canonical project integration now compiles: Java Blueprint.parse recognizes only
the exact shutter recipe (without widening the generic circuit block allowlist),
ComponentTree validates the full canonical record and exposes its root identity,
and companion server synchronization preserves its metadata only after the
existing project/world/dimension/revision fence. Session reservation getters use
the accepted shutter's complete write/read footprints. Native contract coverage
now includes actual component identity and untested assignments; companion checks
cover synchronization, identity rejection, tampering and alias isolation.

EngineeringSession.claim explicitly refuses mechanical execution until motion
recovery/input restoration are wired. No public planner/catalog entry is added.
This enables accepted intention storage/preview as a prerequisite for startup
scope validation; it is not a claim of player-facing construction or restart
recovery. Scoped TypeScript and Java server/client compile passed. GPT-6-Luna/high
focused canonical/guard check found no concrete bypass; no tests or edits by the
helper. New runtime coverage awaits CI. Next: derive startup denial bindings from
these accepted component identities and wire input/commit recovery before lifting
the execution guard and proving the full dedicated-server shutter flow.

Isolation checkpoint23377a1 is now all5 green in CI40994181567, including the
dedicated multiplayer job. Reservation679f25f and canonical integration13df4c6
are the next source batch awaiting runtime verification. Pinned26.3 startup
inspection in work/minecraft-server-startup-26.3.txt confirms loadLevel calls
createLevels then prepareLevels before the normal server tick loop. Choose an
early level-load hook only after checking exact Fabric hook position and whether
spawn preparation executes queued physics; SERVER_STARTED alone is not assumed
early enough. FunctionalTrial.load is data-only but still assumes electrical
contracts for pending inputs, so mechanical recovery needs its own typed state.

Startup recovery wiring now compiles. A required MinecraftServer.createLevels
RETURN mixin initializes the project manager after dimensions exist, before
loadLevel proceeds to spawn preparation. The pinned GameTestServer also calls
loadLevel. Project checkpoints persist strict shutterMotion facts; load derives
expected scope from the accepted canonical component and active operation, checks
the workspace, then startup installs denial-only bindings. Save revalidates that
scope. Pending motion blocks intention/workspace replacement and overlapping
neighbor assignments, including an otherwise permitted owner. No lease/ticket
is restored. Invalid data fails startup before normal project execution.

New native proof asserts hook completion before test ticks, writes/reloads a real
project checkpoint, checks absent delegation, invokes the production manager's
denial restoration, rejects an altered journal operation and a neighboring lease,
then checks actual queued motion stays denied. Scoped Java server/client compile
passed. GPT-6-Luna/high focused scope/startup check found no concrete blockers;
no helper tests or edits. This is not a full saved-world reboot proof yet. Input
restoration, actual commit orchestration, explicit fresh-authority reconciliation,
safe release and mechanical trials remain before lifting the execution guard.

Canonical/reservation source654ab73 Fabric passed all74 required native tests in
CI40995004043 (actual log03:02:19 UTC inspected). Four jobs passed; dedicated
multiplayer remains live at inspection, so the whole run is not yet all-green.

Update: canonical/reservation654ab73 is now all5 green in CI40995004043. Recovery
hook/checkpoint sourcefe203f5 is the next batch awaiting its own runtime proof.
The existing dedicated harness already reboots the same saved world between its
first and second flows (MultiplayerGameTests.java around prepare-restart and the
second TestDedicatedServerContext). Extend that boundary with a separately
labeled mechanical recovery fixture and a post-boot queued-event denial check;
do not add another server boot merely for this proof or call it fresh inference.

Saved-server mechanical recovery proof is now written in the ordinary CI native
multiplayer flow (excluded from live inference runs). Before its existing first
shutdown it stages a clearly labeled synthetic accepted shutter at320,100,0,
records a prepared motion in an actual project checkpoint, registers denial,
persists an on lever and queues a real event. Its one forced chunk stays loaded
on the same saved world's next boot. Before normal second-boot project commands,
the flow checks early recovery readiness, exact retained motion identity, no
lease, on input, ordinary-arm rejection and actual queued-event denial after8
game ticks. The CI database verifier requires shutter-recovery.done. No new boot,
model invocation or human construction is claimed. Scoped client Java compile,
JavaScript syntax and changed-file formatting passed; runtime proof awaits CI.

Input restoration should reuse or align with FunctionalTrial's existing per-input
write-ahead original/last/before/pending facts rather than invent unrelated
authority-bearing data. Its current contract/pose observation assumes electrical
outputs and static build geometry; mechanical trials need actual aperture/head/
payload samples and guarded input transitions. Keep construction disabled until
that controller, interruption/reconcile and safe release are integrated.

Startup sourceeb676c0 Fabric passed all75 required native tests in CI40995721852,
actual03:09:50 UTC log inspected. Four jobs passed; dedicated multiplayer remains
live at inspection, so whole-run success is not yet claimed.

Update: startup sourceeb676c0 is now all5 green in CI40995721852, including the
dedicated server. The new saved-server fixture09f06e9 still awaits its own CI.

Mechanical pending inputs now decode through FunctionalTrial's existing WAL,
using a canonical input-only recovery contract with no output cases or functional
pass. Ordinary test startup remains unsupported/claim-gated for shutters. The
saved-server fixture also stages the actual input WAL (original off, last on,
before off) under the accepted component scope, then verifies its retention and
continued absence of a lease on boot. This proves neither an authorized input
restoration nor mechanical functional testing yet. Scoped Java server/client
compile passed; the updated runtime proof awaits CI.

Explicit input reconciliation now compiles. A private claim exception is limited
to PAUSED canonical shutters with pending motion AND input WAL, BUILD permission
and test/isolation acquisition. Only the explicit resume branch can request it;
ordinary mechanical claims remain rejected. Resume validates/restores the denial
binding, obtains a fresh normal creative/connection/project-grant/reservation
fence, checks a stable before/terminal pose and complete reserved environment,
then uses existing input WAL restoration. It remains PAUSED, retains the motion
record/denial, creates no commit/pass, and releases only a successfully acquired
lease in finally. Changed/unloaded/unsettled data remains preserved.

The native saved-server flow now forces the four chunks covering its complete
isolation envelope, then drives survival-mode resume refusal and creative-mode
owner resume through the native connection. It requires original off restoration,
no lease, retained prepared motion and continued arm denial; component build after
input restoration must refuse without replacing operation identity. The verifier
requires shutter-input-recovery.done in ordinary CI. Planning/context changes are
also blocked while motion remains pending, before model dispatch. Scoped Java
server/client compile and JavaScript syntax/format checks passed; new runtime
proofs await CI. GPT-6-Luna/high focused reconciliation check found no concrete
fresh-authority, overwrite or release bypass; no helper tests/edits.

Prior WAL/saved-server source4000159 Fabric passed all75 required native tests in
CI40996526932 (actual03:18:12 UTC log inspected). Four jobs passed; dedicated
multiplayer is still live at inspection, so the new reboot marker is not yet
claimed proven. The earlier focused input-decoder/fixture check also found no
concrete bypass or seed flaw. Remaining: explicit physical position reconciliation,
guarded actuation, mechanical traces, construction and the full M3 player flow.

Update: source4000159 is now all5 green in CI40996526932. Dedicated job completed
03:23:59 UTC; its actual success log and downloaded coordination/shutter-recovery.done
(passed), exact project and motion IDs were inspected. Artifacts:
work/shutter-recovery-proof-40996526932. This proves the synthetic fixture's real
saved-server restart, retained input WAL and queued-event denial, with no restored
lease. It does not prove fresh inference, Mojang authentication, player placement,
authorized input restoration or a functional shutter trial. Reconciliation source
82bb63f is the next batch awaiting its own runtime proof; it adds the separate
required shutter-input-recovery.done marker.

Physical reconciliation now compiles. A second explicit resume after input WAL
restoration obtains a fresh recovery-only lease, checks accepted scope, full
environment, stable before/terminal pose and agreement between input and piston.
The gate durably records an observation receipt, rechecks authority/pose/binding,
then changes pending denial to identity-bound idle denial, never to a ticket.
The original physical journal and observed pose/tick are archived separately;
clearing the checkpoint does not invent a commit or functional pass. Checkpoint
failure restores the old journal/pending denial. Both phases always release their
fresh lease. Canonical mechanical designs with no pending motion are now guarded
on startup too, so clearing a journal cannot silently restore vanilla actuation.

Two new native core tests cover missing authority, failed receipt persistence,
post-receipt revocation, no ticket, matching rollback, foreign replacement and
no-load idle registration. The dedicated flow adds conflicting glass preservation,
explicit owner reconciliation, receipt inspection, no pass/lease and retained
idle denial on the already-existing third saved-server boot. The verifier requires
shutter-position-reconciled.done and shutter-position-recovery.done. Scoped Java
server/client compile, changed JavaScript format/syntax checks passed; runtime
proofs await CI. GPT-6-Luna/high focused boundary check found no concrete scope,
permission or rollback flaw, and ran no tests/edits. Accepted-design replacement,
placement ownership, guarded actuation and functional mechanical trials remain
unfinished; no public shutter planner/catalog or construction is enabled.

Input reconciliation source0a526f6 is now all5 green in CI40997277706/all75 native
tests. Dedicated job success03:33:32 UTC; downloaded both shutter recovery markers
(passed) and inspected alice.log03:29:44 creative-mode refusal, original input
restoration and refused component-build messages. Artifacts:
work/shutter-input-proof-40997277706. This proves native command input restoration
after the real saved boot, its failure path and retained physical denial; no new
inference or production account authentication is claimed. Physical reconciliation
source6baa750 is the next batch awaiting its own CI and later-boot markers.

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

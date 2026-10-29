# Now

2026-10-29. Active goal: **complete all milestones**: the entire Phase1 engineering
partner and all six Phase2 features. A catalog, buffer, screenshot, release or
green CI never substitutes for the full objective.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch **phase/13-generated-logic**, draft **PR22**; main includes PR21 mergee3f26d8.
Latest fully checked source **d3c84865d4045e8c182224ee4c8ea7a1d2c44add** passed all5
**CI40908760538** and the entire actual signed-in **Boolean run40910817937**.
New recorded-reply replay/model-policy changes require final CI before shipping.
Read actual git status, PR and live workflow state before continuing.

Josh: **skip heavy reviews; build and ship**. Public implementation, pushes and
merging green PRs are authorized. No approval is pending. Tests run on GitHub,
never on his slow PC. Locally scoped compile/format/syntax only, one Gradle worker.
No separately billed service or API key. Only the configured account-owning
operator invokes AI. Keep project memory only in AGENTS.md and this file.

## Current slice: generated Boolean logic (T1/T2/T3)

- T1: independently accepted complete Boolean intent, strict schema and stable
  Java/TS identity. Preserve legacy buffer JSON/digests/exact-edge tests.
- T2: raw geometry with actual distinct named levers/lamp, separate owner confirm,
  no-edit preview and explicit build. Actual generated AND must work in Minecraft.
- T3: every-tick settled evidence for every row forward/reverse; reject gaps,
  transient errors and forged/reordered evidence. Preserve input originals across
  revocation/restart without a lease; fresh owner resume and another trial prove
  recovery. Geometry and a correct endpoint alone cannot pass.

### Implemented contracts

EngineeringBehavior is buffer/TruthTableSpec. Schema1 buffers retain
`buffer-v1|RISING|FALLING` digests and generatorRevision1. New exact schema2:
function=truth-table, inputs, outputs, settleGameTicks. Inputs1..4 unique
`[a-z][a-z0-9_]{0,31}`, excluding output; outputs exactly2^N strict Booleans,
binary row order with first input most significant. Settling integer0..128;
fixed8-tick stable window. Digest SHA256 UTF8
`truth-v2|comma-joined-inputs|0/1-output-string|settle`.

Boolean blueprints keep outer schema1, generatorRevision2. Raw inputs:[{name,pos}]
match accepted order/count and distinct actual levers; one lamp, no hidden ports.
Both runtimes re-derive canonical metadata. GeneratedPlanning retains typed intent.
FunctionalTrial samples tick0 through settle+8, all rows ascending then descending
(including repeated last row). SettledTrace/companion replay bind row/digest/bound,
samples, verdict and summaries; outer Java replay also binds input vector, case
order and adjacent game-time boundaries. Existing per-input write-ahead restore,
current authority and player-edit preservation remain. No persisted execution lease.

Dedicated command-level deterministic proof18ff3d1 / all5 CI40903991573 passed:
design -> separate confirm -> preview -> build -> tests -> revoked interruption ->
actual third boot -> fresh resume/retest. Trials15:47:00 and15:48:38 UTC; both truth
recovery markers passed and SQLite required two planning starts. Hand-authored raw
AND reply, not inference. Artifacts work/truth-confirm-proof-40903991573.
CI's guarded no-inference provider allows12 actor stages/minute for the expanded
fixture burst; production4/minute and24/hour are unchanged. Earlier7f1e6e7 /
CI40902469058 failed at that budget before the following buffer call; preserve failure.

### Fresh signed-in result: whole run passed

Source **d3c8486**, all5 CI40908760538 (all40 required native tests). Entire actual
**run40910817937 SUCCESS**. Behavior response **40911887391**, geometry response
**40912161087** both accepted. Official SDK0.164.0 with ChatGPT sign-in: Luna behavior
10795in/97out; Sol geometry12322in/1020out. Exact AND0001/a,b/settle20 and13 raw
blocks, layout distinct from the hand-authored reference. No result repaired.

Actual native separate confirmation, no-edit preview, explicit build, all8 measured
forward/reverse rows, actual companion restart4778->5851, permission revocation
interrupting another trial, actual third saved-server boot, fresh owner resume,
original a ON/b OFF restoration and another actual test passed. Trials16:30:43 and
16:31:11 UTC. Both live-boolean markers and truth interruption/recovery markers
passed; SQLite required exactly two planning starts and distinct completed trials.
Synthetic existing section seeded its actual supports and levers, not human mouse
placement. This does not prove Mojang authentication or full E4 player construction.

Inspected artifacts: work/live-boolean-proof-40910817937. Inspected screenshot shows
real stone/levers/repeaters/torches/lamp after restart; copied unchanged to
`docs/images/boolean-restarted.png`. Both accepted digests match private receipts:
behavior request4febf74810910fbe30d09649791fceb8cae65b0ed15fc7f5ff2d658f1f299075,
result e9949d8b3350f5a4de5df234368392922dad6d8078e5fbab98ccc006c6cd8060;
geometry request9b8034e843cfc17d03a4922d6cf91d1d4c4a7b74b7ac75ac583d262479fd7c9d,
result2344abb47d868b3eb7c710f0fa75756e788dc1b785430fe4d2ccae67d51e9faa.

`tests/fixtures/live-boolean-and.json` retains exact successful proposal/raw geometry
and separate model provenance. Ordinary CI now replays this instead of a parallel
hand-authored reply. Synthetic existing lever/backing positions are derived from
the actual accepted attachment states in both paths; no reference coordinates.
Replay starts untested and actual native tests must establish behavior again.
It is not fresh inference. The low-level hand-authored native reference stays an
independent physics oracle. Final replay changes have not passed CI yet.

### Failed replies retained, not corrected

First actual40906470078 on ee2745f (all5 CI40905604442) accepted behavior response
40907741010, then locally rejected13-block Luna geometry for missing wall-lever
backing; it also falsely called serial repeaters AND. No Boolean construction.
The workflow was cancelled after terminal local rejection, not a timeout.
Fixture `live-boolean-missing-support.json`; artifacts work/live-boolean-proof-40906470078.
Behavior request6e110abd53481202a38e698f1ab705e0aa97662f26d8b0730e66f5e181fadd60,
result971236c2e0fc8b6d88a589e5a7646368504ac770fced8398e1f5308dbe4b057f;
geometry request33ae3c92f3ea8491b96f9130a6a592987b50f640be358cb912a92199993af0bd.
Usage10787in/105out +11417in/434out. Precise attachment/Boolean physical guidance
was added, grounded in the native AND oracle, without coordinates/template lock.

Second actual40908843817 on d3c8486 accepted behavior40910202563, then locally
rejected34-block Luna geometry: torch and lamp both at5,1,1. No construction;
workflow cancelled after rejection. Fixture `live-boolean-overlap.json`; artifacts
work/live-boolean-proof-40908843817. Behavior request1031bcf1cd41a3cb078586f0fd592fa85b8173761a3be4340e147591b872fff5,
result b1a6328c1b70ff3d755214f594a1e038be823674625a759850ff9a4cc166a628;
geometry request35bef5af8274593a01bcab53fa5af29a71fd1e4ad39ac5eafab305bb9af4bb89.
Usage10792in/89out +11625in/884out. Root decision: stop repeating Luna geometry,
use Sol for the next explicitly bounded geometry call; the successful run above
used the same source/constraints, not a relaxed problem. Official model guidance
and local account model metadata confirmed Sol availability before that call.

All six Boolean calls across these three runs are consumed. Private started,
failure and untrusted receipts stay under AppData/minecraft-ai-engineer/acceptance.
Do not reuse started receipts, silently repair outputs or make more SDK calls for
this slice. A cancelled failed attempt is never the successful whole run.

### Current final changes and next action

Model policy now keeps Luna for catalog/behavior/timed buffers and uses Sol medium
for Boolean geometry. Explicit MCE_MODEL overrides all defaults; optional
MCE_GEOMETRY_MODEL overrides geometry only. Doctor says catalog probe does not
check Boolean-model availability. No silent retry/fallback. This matches the
successful explicit stage-model choices; no new SDK calls needed to test policy.
Added policy/replay tests and the second unchanged rejection fixture. Scoped
TypeScript and client Java compilation passed; tests run only in GitHub.

Next: push final model-policy/recorded-reply changes, inspect all required CI and
native artifacts, then ship PR22 when green, without heavy review. Continue full
roadmap; next user-facing slice should broaden free-form engineering followups
and durable project goals/rationale (E1), alongside the remaining engineering scope.
No complete-product claim. Shared-subscription eligibility remains unresolved.

## Shipped foundations and retained evidence

Dedicated Fabric server first, integrated-compatible logical-server core;
server-owned identity/permissions/project ownership, real connection/epoch fences,
shared authorized blueprints, private per-player tutorial cursors, component
assignments and bounded write/read reservations. Creative build/repair/undo,
inspection and private inventory reads. Intended/observed/verified remain distinct.
Disconnect, revocation, conflict and restart restore no lease. Optional overlays
currently show colored bounds/labels, not textured directional ghosts. Published
alpha is the older single-player reference; multiplayer features are in source.

PR19 structural revisions merged1e97bc385c3296ea037eaaf4689b7eeec982243b.
Final sourceb17ba07 passed all5 CI40797460992; source8c3aa39 native evidence
CI40796411413 covers explicit old/new diff, owner expansion, no-edit preview,
separate one-use apply, stop/conflict, unfinished cursor across third boot,
12/16 timing and inverse repeated-position undo preserving player supports.
Artifacts: work/structural-proof-40796411413.

PR20 restored layouts merged55a48582003b2b91ffb50d26dd74b8846679ff9e.
Sourceaa73e65 CI40800347568; final8c4e21e all5 CI40801344596. Actual fourth boot,
true old IDs, corrupt/stale reference rejection, changed base rejected before
quota, new request/apply, actual timing and second undo. Artifacts:
work/restored-proof-40800347568. StructuralTransition schema1 preserves adjacent
sources; schema2 adds distinct accepted canonical context without relabelling true
before/after. OperationScopes hashes/bounds all captured sources. Undo retains
immutable restoredLayout; planning validates the physical basis before dispatch.

PR21 signed-in structural proof merged e3f26d8. Sourceccd89d2 passed all5
CI40806790413; full actual manual40806992766 SUCCESS. Official SDK0.164.0 /
gpt-6-luna behavior40807645397 and geometry40807742547 accepted12/16 and ten raw
blocks with three west-facing delay2 repeaters. Actual player first support, AI
rest, separate modification apply, stop/client glass conflict/removal/resume,
actual new timing, companion restart4537->5429, shared Bob lesson/revocation and
third game boot passed. Inspected real-block PNG docs/images/structural-restarted.png;
artifacts work/live-structural-proof-40806992766. Final recorded-reply replay
sourcea504014 passed all5 CI40808845287 before merge. Replay is not fresh inference.

Positive private receipt digests: behavior request
dd774b24ab571cc39987f4f1082861eb4388e29fc8a407666294027592e487be,
result8189e9bf653079448dbcd12f93aba1fb33bf37ba2936c9222387930ba9f7f35b;
geometry request6f3463878ab272adc7682a405501348c36c60a1a547d948d551be2236c33591e,
resultfa3a2e89c7cab9d2a74e8dcac1e08fd98d1299708acb7f2c6dec51ea472cd71c.
Earlier actual40805062670 FAILED: accepted east-facing repeaters matched geometry
but failed true output at tick12; exact fixture live-structural-reversed.json,
artifacts work/live-structural-proof-40805062670. Do not hide that failure or flip
the raw reply. All four SDK calls across failed/successful attempts are consumed;
never reuse their exclusive started receipts or make more calls for that old slice.
MC26.3 repeater facing is its INPUT side, opposite signal travel; native positive/
negative traces and pinned DiodeBlock bytecode ground the corrected guidance.

PR18 generated buffers mergedbebf6ca; final076e4dc all5 CI40768389557. Signed-in
source00e5da5/request40766098371 used behavior40766960852 and geometry40767111851,
12/16 with eight raw blocks and repeaters4+2. Actual timing, sharing and restarts
passed, but whole manual run failed at creative inventory fixture cleanup. Fixed
cleanup and final recorded replay passed; never call that whole old run successful.
Real-block PNG docs/images/generated-restarted.png. Artifacts work/live-proof-40766098371
and work/generated-proof-40768389557. Older catalog40624276545 proved only a fixed
lamp; its ghost-only screenshot did not prove built blocks.

## Runtime and operating constraints

Java25 / Minecraft26.3 / FabricLoader0.20.1 / API0.165.0+26.3 / Loom1.17.21 /
Gradle9.6; Node24.14 / TypeScript / SQLite / official SDK+CLI0.164.0. Toolchain:
task work/fabric-tools/jdk-25.0.5+8. PowerShell login:false; max-workers1.
No local test suites. One private companion beside the operator's own server/PC;
no public AI endpoint, hosting purchase, credentials sharing or paid API fallback.
General shared multi-user subscription eligibility remains unresolved: operator
approval is not provider eligibility. Planner forces ChatGPT login/openai provider,
disables tools/apps and uses isolated read-only SDK work. Credentials stay local.
Private inventories never enter shared model context/SQLite/audit. World/model text
is untrusted data, never executable code. Current execution is creative, not survival.

## Remaining full roadmap

1. Finish Phase13 multi-input generation/acceptance, then broader electrical and
   mechanical engineering: comparator/observer/piston behavior, connections and
   larger multicomponent systems. No buffer/catalog-only substitute.
2. E1: free-form followups/clarification, durable goals/rationale/preferences/
   decisions and later project identification/explanation. Exact ask phrases
   remain a limited slice.
3. E3: broader missing/orientation/connection/timing/interference/inconclusive
   diagnosis, explicit repair previews and textured directional ghosts.
4. E4: full signed-in preview -> player functional section -> AI rest -> interrupt/
   modify/resume -> actual tests -> service/game restart -> later explanation and
   richer visuals. Existing bounded proofs do not establish this complete flow.
5. Phase2, all unstarted: voice; physical NPC; genuine survival gathering/placement;
   schematic import/export; measured size/resources/latency/reliability optimization;
   multiple coordinated independent builders.
6. Package the completed multiplayer release and setup instructions; published
   alpha remains the older single-player reference.

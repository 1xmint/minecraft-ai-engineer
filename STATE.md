# Now

2026-10-29. Active goal: **complete all milestones**: the entire Phase1 engineering
partner and all six Phase2 features. A catalog, buffer, screenshot, release or
green CI never substitutes for the full objective.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch **phase/14-project-conversation**, based on merged **PR22** commit
**bb4e81ff238293ffbce92e94c3a52fc275396572**. Final Phase13 source
**a3a3fcf9f90ba5ce9529b3460a3e7cb6a425de26** passed all5 **CI40913824351**;
whole actual signed-in Boolean **run40910817937** passed on d3c8486. Phase13 is
shipped in source, not a complete-product release. Phase14 literal notes, HTTP/native commands and bounded versioned planning context
and the first free-form discussion path are implemented on the working branch.
Native acceptance is running; fresh signed-in conversation proof and remaining
Phase14 failure/restart proofs are unfinished. Required checks are next.
Read actual git status, PR and live workflow state before continuing.

Josh: **skip heavy reviews; build and ship**. Public implementation, pushes and
merging green PRs are authorized. No approval is pending. Tests run on GitHub,
never on his slow PC. Locally scoped compile/format/syntax only, one Gradle worker.
No separately billed service or API key. Only the configured account-owning
operator invokes AI. Keep project memory only in AGENTS.md and this file.

## Shipped generated Boolean logic (T1/T2/T3)

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
independent physics oracle. Final recorded-reply replay/model-policy sourcea3a3fcf passed all5 CI40913824351 before mergebb4e81f.

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

### Phase13 finalization

Model policy now keeps Luna for catalog/behavior/timed buffers and uses Sol medium
for Boolean geometry. Explicit MCE_MODEL overrides all defaults; optional
MCE_GEOMETRY_MODEL overrides geometry only. Doctor says catalog probe does not
check Boolean-model availability. No silent retry/fallback. This matches the
successful explicit stage-model choices; no new SDK calls needed to test policy.
Added policy/replay tests and the second unchanged rejection fixture. Scoped
TypeScript and client Java compilation passed; tests run only in GitHub.

Final sourcea3a3fcf passed all required CI and merged as PR22/bb4e81f. No further
Boolean SDK calls are needed. Broader engineering and the complete product remain
unfinished; the old single-player alpha is still the published reference release.

## Phase14 plan: grounded conversation and project knowledge (first E1 slice)

- C1: the configured account-owning operator can ask a free-form engineering
  followup about the selected project/component and get a grounded explanation
  or clarification. Proof: actual native command -> fenced companion -> bounded
  provider reply; ambiguous references clarify, unsupported requests say so;
  no world mutation, invented test pass or change to lesson/assignment selection.
- C2: explicit owner-authored project goals, constraints, preferences and decisions
  survive service/game restart and inform later discussion/planning. Proof: real
  SQLite persistence, authorized member read, foreign-project denial, immutable
  authorship/revision and unchanged blueprint/evidence identity. Notes are shared
  project knowledge, not private inventory snapshots or a personal account profile.
- C3: stale/unauthorized conversation cannot spend another player's quota or leak
  project data. Proof: owner/operator/current connection gates before dispatch,
  revocation/disconnect/selection/knowledge-change cancellation, replay rejection,
  bounded queue/budget, one call per explicit message and no automatic retry.
  Existing cached lesson questions remain offline and do not gain hidden AI calls.

Decision: preserve deterministic lesson/stop/build controls. Add an explicit
free-form engineering conversation path, with read-only structured answers;
model text never becomes executable commands. Add bounded explicit literal project
notes before automatic conversation summarization or inferred memory writes.
Their revision is separate from canonical blueprint revisions and world evidence;
knowledge edits invalidate stale in-flight reasoning without relabelling geometry.
Keep empty-note context compatible with existing bounded acceptance scenarios.
Do not feed private inventory captures into shared conversation or persistence.

Tasks: schema and failure proofs (C1/C2/C3); literal-note codec and durable storage
(C2); fenced grounded-context discussion/provider route (C1/C3); native commands,
current-reference/callback handling and separate note authoring (C1/C2/C3); actual
native/service/game restart, revocation and failure acceptance (C1/C2/C3); required
CI, bounded signed-in proof where needed, then ship. Tests only on GitHub; locally
scoped compile/format/syntax. No heavy review cycle, paid API or new credential.
This first slice does not finish all E1 natural action interpretation or the full
engineering scope. Keep the full roadmap below, including every Phase2 feature.

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

Phase14 C2 foundation: strict literal note codec (goal/constraint/preference/
decision), immutable server-assigned identity/authorship/revision and append-only
SQLite tables, separate from accepted blueprint revisions and observations. Only
a current connected owner can append; authorized members can read bounded pages.
Optimistic revision + existing replay ledger + note append + version + audit share
one transaction. Stored row/counter inconsistency rejects reads and further
appends. Older records remain stored beyond the32-note read bound. Planning
context is deliberately unchanged until versioned stale-reasoning fences exist.

Tests written before implementation cover forged metadata, foreign-project and
editor denial, stale/replayed append, rollback including request consumption,
service/epoch/auth changes, corrupt stored identity and bounded reads. No local
tests run; TypeScript no-emit passed. One GPT-6-Luna/high focused auth boundary
check (required by AGENTS for auth-sensitive changes) reported no blocking findings;
no broad review cycle. HTTP/native routes and free-form discussion are NOT built
yet; do not claim an in-game note/conversation feature from this foundation.

Phase14 working branch update: HTTP note read/append routes and native
`notes`/`remember` commands are implemented, including connected-player callback
fences, a bounded deadline, owner-only append, strict reply validation and
cancellation of pending reasoning when knowledge changes. Java server/client
compilation passed. Added HTTP denial/replay/in-flight cancellation tests and a
native strict-note DTO test; these tests await GitHub CI. The existing foundation
commit fc5b9e3 passed all five checks in CI40917639392. Native command/restart
acceptance and model conversation/context integration remain unfinished. Do not
claim this branch is a shipped in-game memory feature yet.

Phase14 acceptance added: ordinary CI now drives owner note commands, shared
editor reads, denied editor authorship, revocation, unchanged blueprint/world
state, actual later server boot reads and a third append. SQLite verification
requires the exact three literal notes and immutable owner identities. This is
written coverage, not a passed runtime claim. Scoped client Java compilation and
verifier syntax passed. On prior source57668f9, Companion and Windows checks passed
in live CI40955973499; native jobs were still running at inspection. The new
acceptance source must pass its own complete CI before it is considered proven.

Phase14 context integration: reasoning captures the current knowledge revision and
latest eight literal notes, explicitly labeled owner-authored requirements rather
than world evidence. Queued dispatch and completed replies recheck the captured
revision, alongside existing epoch/project/auth fences. Note changes abort active
reasoning. Empty-note contexts remain byte-for-byte compatible with older signed-in
acceptance receipts. Snapshot/fencing and HTTP provider-context tests are written;
TypeScript no-emit passed. No local tests run. Native-note source9198c2a is running
CI40956149245; Companion and Windows passed, native jobs were still live at last
inspection. Prior source57668f9 CI40955973499 was superseded/cancelled, not passed.

Phase14 conversation implementation on working tree: strict bounded read-only
reply schema (explanation/clarification/unsupported, text, supplied-source IDs),
intended project/component + latest eight owner-note grounding, optional official
Codex provider discussion using Luna and the existing no-tools/read-only isolated
runner. `/v2/discuss` reuses owner plan grants, explicit operator flags, current
connection, queue, durable request claim/budget and pre/post knowledge fences.
Accepted replies are audit records, never geometry, tests or executable commands.
Native `/engineer discuss` captures real connection, project/auth revision,
operator, current lesson component/stage; stale callbacks drop. Notes and stop
cancel pending discussion. No private inventory enters grounding. Existing offline
lesson `ask` unchanged. Narrow GPT-6-Luna/high auth check reported no blockers;
no heavy review. Native deterministic discussion flows before/after game restart
and exact SQLite audit/source checks are written, not yet passed. Fresh signed-in
conversation proof remains required. TypeScript checks and scoped Java compile
passed before the latest native DTO test/stop cancellation edits; compile again.

Latest scoped server/client Java compilation passed, including the read-only
reply DTO rejection test and stop cancellation. The ordinary CI discussion
provider is a strict two-message deterministic fixture: it validates the real
note context/version and returns labeled owner requirements, never invokes the
SDK. Native acceptance after restart must show its answer and immutable world
state; SQLite must contain exactly two owner discussion audits/dispatches. These
fixtures do not prove fresh model behavior. The prior note-only CI40956149245 was
still running its dedicated multiplayer step at last inspection; do not restart
or label it terminal based on duration alone.

Verified note-only source9198c2a: all five checks passed in CI40956149245. Downloaded
and inspected work/knowledge-proof-40956149245: all four knowledge markers passed;
Alice recorded goal/decision at21:21:09 UTC, Bob read revision2 and was denied
owner authorship at21:21:10, and actual later boot read revision2 then appended
constraint revision3 at21:22:53. The CI SQLite verifier required exactly three
literal owner-authored notes. This proves the native note/revocation/game-restart
slice on that source, not fresh inference or conversation behavior. Conversation
source6ccd7d6 now needs its own complete CI; keep PR23 draft until remaining proofs.

Conversation CI40957543485 exposed a real TS/native component identity mismatch:
standalone native lessons select logical root, whereas recipe block component
labels describe internal parts. Discussion grounding incorrectly treated those
parts as logical components and rejected root. Fix: standalone root grounds the
entire mechanism; composite projects use their canonical component tree IDs.
Keep the failing test and strengthen it to require all root blocks and reject
internal recipe groups. Companion job failed on that source; other live jobs are
not assumed stopped or passed. Scoped TypeScript check is next, then new CI.

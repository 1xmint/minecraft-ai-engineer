# Now

2026-10-29. Active goal: **complete all milestones**: the entire Phase1 engineering
partner and all six Phase2 features. No catalog, buffer, screenshot, release or
green CI substitutes for the full objective.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch: **phase/13-generated-logic**, draft **PR22**. Main includes PR21,
mergee3f26d88f40270854005a339cba67e17d157eb9a. Latest fully verified Phase13 source:
**ee2745f573ccda24181d44e6159f66e38f089b88**, all five required checks passed
**CI40905604442**. The first fresh Boolean geometry reply was rejected locally;
its guidance fix requires new CI and one new bounded live attempt.
Read actual git status, PR and workflow state before continuing.

Josh: **skip heavy reviews; build and ship**. Public implementation, pushes and
merging green PRs are authorized. No approval is pending. Tests run on GitHub,
never on his slow PC. Locally scoped compile/format/syntax only, one Gradle worker.
No separately billed service or API key. Only the configured account-owning
operator invokes AI. Keep project memory only in AGENTS.md and this file.

## Outcomes for current slice: generated Boolean logic

- T1: independently accept complete behavior for one to four named Boolean inputs,
  without geometry defining its own specification. Proof: strict schema, exhaustive
  rows, immutable data, matching Java/TS digest and rejection of malformed data.
- T2: raw generated geometry binds each accepted input to a distinct actual lever
  and the output to one lamp. Proof: unchanged preview, separate owner confirmation
  and build, actual AND physics across every forward/reverse row. No catalog shape
  substitution or geometry-only functional pass.
- T3: every-game-tick settled evidence and safe input recovery. Proof: transient
  wrong outputs fail, gaps are inconclusive, forged/reordered summaries reject;
  revocation and saved-world restart retain originals without authority, fresh
  owner resume restores them, and another actual trial passes.

### Implemented and proven

Behavior is a typed EngineeringBehavior union. Existing schema1 buffer JSON,
`buffer-v1|RISING|FALLING` digests, revision1 metadata and exact-edge tests remain
unchanged. New TruthTableSpec / truth-table.ts use exact schema2:
`function=truth-table`, `inputs`, `outputs`, `settleGameTicks`.
Inputs:1..4 unique `[a-z][a-z0-9_]{0,31}`, excluding reserved output. Outputs:
exactly2^N strict Booleans, binary row order with first input most significant.
Settling integer0..128; stable window fixed8. Digest SHA256 UTF8:
`truth-v2|comma-joined-inputs|0/1-output-string|settle`.

Generated Boolean metadata uses generatorRevision2 and the unchanged outer
blueprint schema1. Raw geometry inputs:[{name,pos}] must match accepted name/order,
with distinct actual levers and exactly one lamp. Both runtimes reconstruct all
canonical metadata. GeneratedPlanning retains typed accepted intent; task identity
includes its digest. Geometry/model output never grants world authority.

FunctionalTrial has a separate settled truth-table runner; buffer exact timing is
not weakened. Cases ascend then descend through every row, including repeated last
row. Samples include activation tick0 through settle+8; output must match at settle
and every tick thereafter. SettledTrace and companion replay validate accepted
row/digest/bound, contiguous samples, verdict and output summary. Outer Java evidence
also binds case order, observed input vector and adjacent game-time boundaries.
Existing per-input write-ahead restoration, current authority and conflict checks
remain in use. Saved evidence never resumes a test or grants a lease.

Shared `mod/src/main/resources/fixtures/generated-and.json` is a **hand-authored raw
reference**, not model inference. Companion validates the exact bytes; native game
tests prove all8 forward/reverse AND cases and reject OR intent for the same wiring.
Source1063b64 / Fabric job123536420796 logged all39 required tests passed at15:13:02
UTC. Its full CI40899001903 was later cancelled by a newer push; do not call the
whole cancelled workflow successful. Artifacts: task work/logic-proof-40899001903.

**Source52b1296 / CI40899770666 passed all five gates.** Inspected retained
`work/truth-proof-40899770666`: actual production truth trials passed at15:22:06
and15:23:46; coordination/truth-interrupted.done and truth-recovery.done both passed.
Existing synthetic player section had two supports and differently powered levers
(a ON, b OFF), excluded from the AI's nine placement entries. First trial restored
both originals. Permission revocation interrupted a later trial with two journals
and no lease. Actual third saved-server boot retained them; fresh owner resume and
another actual trial passed. SQLite verifier replayed all8 cases against immutable
accepted intent and required two distinct completion times. This is offline CI,
not Mojang authentication or human mouse placement.

### Latest changes requiring CI

Model behavior schema/config/prompt now supports complete Boolean intent alongside
buffers. If no Boolean settling bound is requested, propose20 and explicitly explain
that the owner must confirm this proposed test bound; never promise exact latency.
Named geometry schema binds accepted names/count and rotated workspace bounds.
Geometry adapter validates against the accepted behavior. All existing isolated
subscription/tool restrictions remain. Both Boolean calls from the first live attempt are consumed (see below).

Gateway proof covers separate Boolean proposal/geometry calls, immutable intent,
nonce replay denial, untested preview and unchanged canonical revision. Native CI
truth setup now goes through actual design/confirm commands and real HTTP/SQLite
with a fixed, explicitly hand-authored reply, rather than direct native accept.
Its verifier requires exactly two planning starts, so replay/recovery adds none.
Additional Java evidence proofs reject swapped cases/inputs, forged timestamps,
wrong output summaries and progress past an unfinished case. Protocol and mod guide
describe truth tables. Scoped TypeScript/Java compilation and script syntax passed;
new required checks and this expanded native flow are still pending.

Next: push latest adapter/confirmation changes, inspect all required CI and native
artifacts; fix any failure without restarting solely for an observation timeout.
Then establish an explicitly bounded fresh signed-in multi-input design acceptance
if needed, ship PR22 only when proven, and continue the full roadmap below.
Do not claim fresh Boolean model generation from fixtures or a complete product
from this slice. The earlier structural receipts below are consumed.

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

2026-10-29 source7f1e6e7 / CI40902469058: Companion, Windows, integrated client
and Fabric passed (all40 required native tests). Dedicated workflow FAILED after
the new command-level AND trial passed at15:37:37 and its interruption marker
passed: the following buffer confirmation correctly exhausted the production
4-per-minute actor budget because the suite now adds two preceding Boolean calls.
The third-boot truth recovery was not reached; never call this whole run green.
Artifacts: work/truth-confirm-proof-40902469058. Fix only the guarded synthetic
CI companion to actorPlansPerMinute12 for the fixed multi-scenario burst; it
cannot call the SDK. Production defaults4/minute and24/hour are unchanged, and
existing durable budget/replay rejection tests remain. Syntax check passed.

Signed-in Boolean acceptance preparation: new data-only fixed scenario
generated-boolean-owner-confirmation-v1 reconstructs exact AND inputs a/b, complete
0001 table,20 settling ticks, anchor400,100,0 and6x3x4 workspace. It rejects buffer
intent, OR substitutions, changed settling bounds and cross-scenario raw replies.
Legacy buffer/structural scopes remain unchanged. TypeScript no-emit passed;
rejection tests are CI-only. Workflows, CLI, provenance allowlist and live native
scenario are NOT wired yet, so do not dispatch it or claim fresh inference. Next
finish the expanded deterministic command flow, wire the complete bounded live
path and prove setup before spending exactly two new exclusive stage receipts.

Boolean live-path wiring prepared after18ff3d1: explicit boolean workflow/response
choice, MCE_BOOLEAN_ACCEPTANCE flag, local responder scenario selection, fixed
provenance allowlist and per-stage transport all bind the new scenario. Native
LIVE_BOOLEAN path skips unrelated buffer inference/Bob buffer choreography, checks
exact independently requested AND summary before confirming, uses actual returned
geometry (not parallel reference coordinates), seeds its supports/lever originals,
tests, restarts the companion, revokes during another trial, and performs actual
third-boot resume/retest/source/world/screenshot checks. Boolean transport requires
its own four completion markers. SQLite replays the exact accepted blueprint and
requires two planning starts and two distinct successful trials. This is newly
wired, NOT a live success claim. The first Boolean receipt pair is consumed; see the retained failed attempt below. Scoped client
Java/TypeScript compilation and script syntax passed; required CI and manual setup
proof must precede the two new exclusive SDK receipts. Local tests remain forbidden.

Verified expanded command flow: source18ff3d1 / CI40903991573 passed all5 gates.
Inspected task work/truth-confirm-proof-40903991573: native truth-table trials
passed at15:47:00 and15:48:38; truth-interrupted.done and truth-recovery.done both
passed. Real HTTP/SQLite design -> separate confirm -> no-edit preview -> explicit
build -> actual tests -> revoked interruption -> actual third boot -> fresh resume/
retest succeeded. SQLite replay required exactly two planning starts. Replies were
explicit hand-authored fixtures, not fresh model calls. The prior7f1e6e7 budget
failure remains recorded; the CI-only12/minute setting fixed that synthetic burst.
New Boolean live-route changes now need their own regression run before dispatch.

## First fresh signed-in Boolean attempt: rejected geometry, preserved

Sourceee2745f passed all5 CI40905604442, including all40 native tests and new
scenario provenance/fit checks. Actual manual40906470078 reached its exact fixed
Boolean request with empty synthetic memory. ChatGPT sign-in was confirmed;
o API key, paid fallback or credential upload. Two explicit SDK0.164.0/gpt-6-luna
calls were made, and both exclusive started receipts are now consumed.

Behavior request6e110abd53481202a38e698f1ab705e0aa97662f26d8b0730e66f5e181fadd60
returned the exact AND0001 table/a,b/settle20; response workflow40907741010 passed
and the native accepted marker matches result digest
971236c2e0fc8b6d88a589e5a7646368504ac770fced8398e1f5308dbe4b057f.
Usage10787in/105out. Native owner separately confirmed before geometry request.

Geometry request33ae3c92f3ea8491b96f9130a6a592987b50f640be358cb912a92199993af0bd
returned13 raw blocks (usage11417in/434out). Local validation rejected it for
missing stone support: wall levers at0,1,0 and0,1,1 faced east but had no west
backing at the same Y. It also falsely described serial repeaters as AND and left
b without a causal signal path. Raw reply was not altered or published as valid,
no Boolean blueprint/construction/timing/restart proof occurred. The manual run
was explicitly cancelled after this terminal local rejection (not a timeout).
Artifacts: work/live-boolean-proof-40906470078; raw/provenance negative fixture:
tests/fixtures/live-boolean-missing-support.json. Private started/failure/untrusted
receipts remain under AppData/minecraft-ai-engineer/acceptance. Do not retry either
consumed receipt, silently repair the result or call the whole run successful.

Root fix: geometry guidance now specifies exact backing coordinates for floor,
ceiling and wall attachments, and explains Boolean physical composition grounded
in passing native tests: serial repeaters relay a signal; common strong-powered
stone combines OR; input torches plus common OR plus output inversion implement
NOT(NOT a OR NOT b). No reference coordinates, template enforcement or easier
behavior replaces raw generation. Added rejection proof for the unchanged failed
reply. TypeScript scoped check passed; tests run only in CI. Next required checks,
then one causally justified new two-stage live attempt on new exclusive receipts.
No automatic retry loop or separately billed service. Full roadmap stays active.

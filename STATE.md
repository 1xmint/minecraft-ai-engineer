# Now

2026-10-29. Active goal: **complete all milestones**: the entire Phase1 engineering
partner and all six Phase2 features. No catalog, buffer, screenshot, release or
green CI substitutes for the full objective.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch: **phase/13-generated-logic**, draft **PR22**. Main includes PR21,
mergee3f26d88f40270854005a339cba67e17d157eb9a. Latest fully verified Phase13 source:
**52b12967f59f051b81348948bcb5d76926c6c04f**, all five required checks passed
**CI40899770666**. Later adapter/confirmation/proof changes require new CI.
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
subscription/tool restrictions remain. No new actual SDK calls have been made.

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

# Now

2026-10-28: Active goal: **complete all milestones**, including the entire phase1
engineering partner and all six phase2 features. Do not redefine completion around
a catalog, buffer, screenshot, release or green CI.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch **phase/13-generated-logic**, based on merged PR21 **e3f26d88f40270854005a339cba67e17d157eb9a**. Final replay sourcea504014 passed all five gates **CI40808845287**. PR20 merged55a4858; PR19 merged1e97bc3.
Read actual GitHub/worktree state before continuing. AGENTS.md and this file are
our only project memory; product requirements remain in docs/product-brief.md.

Josh: **skip heavy reviews; build and ship**. No formal review cycles. Public
implementation, branches and merging green PRs are authorized. No question or
approval is pending. Tests run on GitHub, never on his slow PC. Locally run only
scoped compile, formatting and syntax checks; one Gradle worker. Use no separately
billed service or API key. Only the configured account-owning operator starts AI.

## Current slice: actual model structural modification (G2/G3/E4)

- L1: reconstruct only the fixed structural scenario's exact request, five-wide
  bounds, completed seed context, run/SHA/stage and receipt identity. Reject
  cross-scenario data; preserve the old four-wide generated scenario unchanged.
- L2: in the actual game, the player places a reference support, AI completes a
  known two-repeater buffer, and original12/16 timing passes. Two actual signed-in
  calls propose behavior and raw three-repeater geometry. Separate owner apply,
  real stop/client edit/conflict/resume, new timing, service/game restarts and
  rendered blocks must prove the modification, without fabricated inference.
- L3: credentials remain local; two explicit bounded calls retain separate private
  started/response receipts and exact digests. Ordinary CI stays deterministic.
  Never retry a consumed started receipt or upload credentials/private world data.

Decision: use an explicitly hand-authored reference before the two modification
calls, rather than four SDK calls or arbitrary CI inputs. The reference is not a
fresh model design. Actual modification and full bounded live acceptance now
passed; final recorded-reply regression checks and shipping remain. Task order: fix/publish exact synthetic
setup (L1/L3), regressions, actual two responses and inspect all native artifacts
(L2/L3), then ship only the proven bounded feature and continue the full roadmap.

### Verified live result and next action

Source **ccd89d2e6a03c6241463ec11da779ec1dfd67395** passed all five regression
gates **CI40806790413**, including all35 required Minecraft tests. Actual manual
**run40806992766 completed successfully**, with behavior response40807645397 and
geometry response40807742547. Both actual SDK0.164.0/gpt-6-luna replies were accepted;
behavior is12/16, geometry is ten blocks with three west-facing two-tick repeaters.
No result was silently changed. Usage: behavior10566in/83out; geometry11466in/816out.
All four local SDK calls across the failed and successful runs are consumed; do
not make more calls or reuse their exclusive started receipts for this slice.

Inspected retained artifacts: task `work/live-structural-proof-40806992766`.
Actual player first-support construction, AI remainder and original timing passed.
Actual model diff/apply, real stop, client glass placement, conflict refusal,
client glass removal, resume and new12/16 test passed at02:40:53. Companion really
restarted4537->5429; Bob's shared lesson, EDIT-but-not-owner expansion denial and
revocation passed. The actual third game boot retained the exact accepted source
and actual blocks without an execution lease. Both structural completion/recovery
markers and the entire workflow passed. Inspected PNG visibly shows five supports,
lever, three repeaters and lamp after restart; copy unchanged to
`docs/images/structural-restarted.png`. Offline CI is not Mojang authentication.

Behavior request digest dd774b24ab571cc39987f4f1082861eb4388e29fc8a407666294027592e487be;
geometry request6f3463878ab272adc7682a405501348c36c60a1a547d948d551be2236c33591e.
Accepted result digests8189e9bf653079448dbcd12f93aba1fb33bf37ba2936c9222387930ba9f7f35b
andfa3a2e89c7cab9d2a74e8dcac1e08fd98d1299708acb7f2c6dec51ea472cd71c match the
local private receipts under AppData/minecraft-ai-engineer/acceptance. Credentials
stayed local and the planner forced ChatGPT sign-in; no API-key fallback.

The exact positive raw reply is `tests/fixtures/live-structural-buffer.json`.
Ordinary native CI now replays that result for its structural request instead of
constructing a parallel hand-authored three-repeater reply. It remains untested
on replay until actual Minecraft tests run; this is not fresh inference evidence.
Run the final required checks, inspect replay results, then ship PR21 without a
formal review. Broad phase1 engineering and all phase2 work remain unfinished.

### Failure retained, not hidden

Earlier source1e20635 / actual run40805062670 accepted behavior40805687857 and
geometry40805786982 but all repeaters faced east. Actual client conflict/resume
worked and geometry matched; FunctionalTrial failed at tick12 (expected true,
observed false). That whole run failed before restart/screenshot proof. Exact bad
reply remains `tests/fixtures/live-structural-reversed.json`, artifacts in task
`work/live-structural-proof-40805062670`. Usage10558in/67out +11367in/910out. The
old receipts are consumed and were never reused. Geometry guidance was corrected
using actual passing west-facing references and pinned26.3 DiodeBlock input-side
bytecode; CI now tests both west-positive and east-negative traces. The single
bounded new two-call run above succeeded. Do not present the old failure as success.

Setup runs40801664492 and40803355719 were cancelled before SDK calls;40803884854
published a request then failed at the provenance allowlist before a call. Fixes:
seed blueprint ID equals projectId before construction, reconstructed task ID
matches it (not projectId:root), explicit scenario crosses the response workflow,
and owner/workflow/source/stage/artifact guards allow only the three fixed names.
Unknown scenarios reject. Transport fails promptly on unexpected-inference.

The live fixture is explicitly hand-authored before modification; the two model
calls do not prove initial design generation. The earlier PR18 flow separately
proved bounded raw generation. This combined body of bounded evidence still does
not establish the entire phase1 engineering partner or advanced phase2 features.

## Shipped bounded foundations

Dedicated Fabric server first, integrated-compatible core, server-owned project
permissions, real connections/epochs, component assignments, bounded reservations,
private per-player tutorials, creative build/repair/undo, targeted inspection and
private inventory reads. Intended, observed and verified state remain distinct.
Restart/disconnect/revocation/conflict restores no execution lease. Inputs and
block changes use before-state WAL. Optional overlays currently show colored
bounds/labels, not textured directional ghosts. Published alpha remains the older
single-player reference; current multiplayer features are in source.

PR19 explicit structural edits: source8c3aa39 passed CI40796411413; final note
sourceb17ba07 passed all five gates CI40797460992 before merge1e97bc3. Native proof
covers owner expansion, EDIT-but-not-owner denial, no-edit diff, separate one-use
apply, real stop/conflict, unfinished cursor across third boot, explicit resume,
12/16 timing, and repeated-position inverse undo preserving player supports.
Artifacts: task `work/structural-proof-40796411413`.

PR20 restored layouts: sourceaa73e65 passed all five **CI40800347568**; final note
source8c4e21e passed all five **CI40801344596** before merge55a4858. Inspected
`work/restored-proof-40800347568`: actual fourth boot, unchanged true source IDs,
corrupt/stale restored data rejection, real edit refused before quota, second
request/apply, actual12/16 timing at01:31:47 and second undo. SQLite verifies four
starts for the two explicit pairs and current epoch4 metadata separately from
immutable epoch3 recovery evidence. These are hand-authored response fixtures,
not fresh structural model inference.

StructuralTransition schema1 preserves adjacent old/new sources exactly. Schema2
adds current accepted context only when different from the restored physical
before source; never relabel the old ID/revision. All sources are hashed/count
against budgets. OperationScopes stores paired snapshots; sources(operation)
means physical before/after, accepted() means canonical correlation. Exact diff
writes and both real read closures fence world work. Undo persists one immutable
restoredLayout reference; load validates it against canonical after and workspace.
It grants no authority. Changed/incomplete physical bases reject before dispatch.

PR18 generated buffers: mergebebf6ca, final source076e4dc, all five gates
CI40768389557. Actual signed-in source00e5da5/request40766098371 used behavior
response40766960852 and geometry40767111851: desired12/16 and eight raw blocks,
two serial repeaters4+2. Actual player support, AI remainder, measured edges,
shared Bob lesson/revocation, companion restart and game restart passed. Inspected
real-block PNG: docs/images/generated-restarted.png. Full manual run later failed
because its creative fixture left64 stone instead of restoring7; do not call that
whole run successful. Fixture cleanup was fixed; final CI replays the recorded
raw reply through complete inventory/restart acceptance with no new inference.
Artifacts: `work/live-proof-40766098371`, `work/generated-proof-40768389557`.
Earlier catalog request40624276545 proved only a fixed lamp; its old final PNG
was ghost-only. Never broaden those claims.

## Runtime and operating constraints

Java25/Minecraft26.3/FabricLoader0.20.1/API0.165.0+26.3/Loom1.17.21/Gradle9.6;
Node24.14/TypeScript/SQLite; official SDK/CLI0.164.0. Java toolchain is in task
`work/fabric-tools/jdk-25.0.5+8`. Use login:false on PowerShell; no local test suites.
One private companion beside the operator's server/PC. No hosting purchase,
public AI endpoint, credential sharing, paid API or API-key fallback. General
shared-subscription eligibility remains unresolved; solo SDK proof is not proof
that other players may spend the subscription. Existing planner forces ChatGPT
login, disables tools and isolates read-only SDK work. Retain official sign-in.

`plan` is curated; `design` -> `confirm` separately proposes behavior/raw geometry.
Confirming inference never grants building. Structural candidates remain outside
canonical revision until the separate owner apply nonce. Native fences bind
actor/current connection, epoch, revision/auth, selection, workspace, expiry and
assignment basis/generation. Generated buffers use loaded clear two-block margins,
exact lever states and every-game-tick transition traces, including sample0.
Private inventory data never enters shared model context/SQLite/audit. Model
output and world text remain untrusted data, never commands. Current execution
is creative direct placement, not survival. Offline CI actors and scripted client
placement do not prove human mouse use or Mojang authentication.

## Remaining full roadmap

1. Ship the verified L1-L3 bounded structural feature, then broaden generated
   functions and electrical interfaces: comparators/observers/pistons, connections,
   mechanical semantics and larger multicomponent engineering. No buffer-only or
   catalog-only substitute for the requested engineering partner.
2. E1: free-form followups/clarification and durable goals, rationale, preferences,
   decisions and later identification/explanation. Exact ask phrases are a slice.
3. E3: broader missing/orientation/connection/timing/interference/inconclusive
   diagnosis, explicit repair previews and textured directional ghosts.
4. E4: full signed-in preview -> player section -> AI rest -> interrupt/modify/
   resume -> actual test -> service/game restarts -> later explanation and richer
   visuals. Bounded existing proofs do not establish the entire experience.
5. Phase2, all unstarted: voice; physical NPC; genuine survival gathering/placement;
   schematic import/export; measured size/resources/latency/reliability optimization;
   multiple coordinated independent builders.

2026-10-28 memory cleanup: replace superseded chronological notes with this current
map and exact proof handles; keep the full objective and honest limitations.

## Active slice: generated combinational logic (G2/interfaces)

- T1: independently accept named one-to-four Boolean inputs and a complete output
  table, without treating geometry as its own specification. Proof: exact schema2,
  exhaustive row count, unique bounded names, strict Booleans, stable cross-runtime
  digest and rejection of incomplete/extra/ambiguous data. Schema1 buffer bytes,
  digests, geometry and exact-edge tests remain unchanged.
- T2: generate/preview/build a real two-input AND circuit from raw geometry, with
  all input ports bound to actual distinct levers and the output to a lamp. Proof:
  no-edit preview, separate owner authorization, all four Boolean cases in actual
  Minecraft and player/AI handoff. The circuit is not restricted to a catalog shape.
- T3: test all declared vectors forward and backward, sampling every game tick
  after the accepted settling bound through eight stable ticks. Proof: wrong logic,
  incomplete/gapped/forged evidence and changed inputs fail honestly; actual
  interruption/revocation/restart restores original inputs under fresh authority.
  A settled truth table does not claim exact latency or universal formal proof.

Decision: add a typed truth-table behavior beside the existing buffer type, not
fake extra buffer timing fields or a shape-only catalog restriction. Schema2 has
function=truth-table, inputs (1..4 unique [a-z][a-z0-9_]{0,31}, excluding output),
outputs (2^n Booleans, row index bits in declared-input order, first input MSB),
settleGameTicks (0..128). Stable window is server-fixed8. Digest UTF8 SHA256 of
truth-v2|comma-joined-inputs|0/1-output-string|settle. Preserve buffer-v1 digest.
New generated logic metadata uses generatorRevision2; buffer remains1. Add a
shared behavior interface, reuse named port/test/restore infrastructure and bound
world authority independently. Existing block library can build an AND reference;
comparators/observers/pistons, memory and broader mechanics remain later work.

Tasks: boundary proofs first (T1), pure data validators/digest (T1), runtime typed
behavior and multi-input geometry/model schemas (T1/T2), actual sampled truth-table
runner/evidence validation (T3), actual AND/native handoff/failure/restart proof
(T2/T3), GitHub required checks then ship. No heavy review. Tests only in CI;
local scoped compile/format checks. No new SDK calls until deterministic proofs
are established; the earlier structural slice's receipts remain consumed.

2026-10-29 T1 implementation progress: added immutable Java TruthTableSpec and
pure TypeScript truth-table validation, matching SHA256 identity/summary, complete
1..4-input row counts and strict rejection of ambiguous/extra/non-Boolean data.
Added cross-runtime golden digest and arity/rejection proofs. No local tests run.
PR22 remains draft: multi-input geometry/runtime/evidence integration and actual
Minecraft AND proof are unfinished; initial CI40810537325 failed on the deliberately
incomplete boundary-first source. Do not merge or claim generated logic works yet.

T1/T2 companion integration: behavior is now a discriminated buffer/truth-table
union; canonical generation derives named ports and revision2 from the accepted
table. Validation binds name/order/count/distinct lever positions and re-derives
all blueprint metadata; forged ports/digest/revision reject. Legacy buffer
revision1 and byte format remain unchanged. TypeScript no-emit check passed.
Model proposal schemas intentionally remain buffer-only until server runtime
and replayable per-tick evidence support truth tables. Java server/client compile
passed on0d6004d. Tests remain in GitHub CI, not run locally.

T3 evidence implementation: Java SettledTrace and companion validateSettledTrace
replay bounded every-tick observations against the accepted truth-table digest,
row, settling bound and fixed8-tick stability window. Incorrect transient outputs
fail even with a correct endpoint; gaps are inconclusive; forged summaries, row
substitution, duplicate ticks and altered accepted intent reject. Added Java native
proof and companion rejection tests; neither is a claim of actual AND operation.
Java server/client compilation and TypeScript no-emit check passed locally; tests
run only on CI. Prior source3f3b40e CI40898095932 has Companion, Windows and Fabric
passing while native client/multiplayer jobs were still running when checked.
Next integrate typed behavior/ports in GeneratedBlueprint and GeneratedPlanning,
wire settled traces into FunctionalTrial and stored evidence, then enable model
schemas and drive actual AND/handoff/recovery acceptance before shipping PR22.

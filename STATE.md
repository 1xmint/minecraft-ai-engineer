# Now

2026-10-28: Active goal: **complete all milestones**, including the entire phase1
engineering partner and all six phase2 features. Do not redefine completion around
a catalog, buffer, screenshot, release or green CI.

Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch **phase/12-live-structural-proof**, draft **PR21**, based on merged PR20
**55a48582003b2b91ffb50d26dd74b8846679ff9e**. PR19 merged as1e97bc3.
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
fresh model design. The structural source now compiles, but actual inference and
full new live acceptance remain unproven. Task order: fix/publish exact synthetic
setup (L1/L3), regressions, actual two responses and inspect all native artifacts
(L2/L3), then ship only the proven bounded feature and continue the full roadmap.

### Latest evidence and next action

Source **1e20635dee7fd2a1034c20ab0ec1c74a88549784** passed all five regression
gates **CI40804731435**. Actual manual run **40805062670** used that frozen source:
behavior response **40805687857**, geometry response **40805786982**, both official
SDK0.164.0/gpt-6-luna and both accepted by GitHub/native receipts. Behavior is the
requested12/16. Geometry has ten blocks and three two-tick repeaters, but all face
east. The actual client preserved/resolved a glass edit, resumed and matched the
raw geometry; FunctionalTrial then failed at game tick12: expected true, observed
false. The overall manual run failed. No service/game restart or final screenshot
was reached. Never silently flip that model output or claim a working live design.

Retained artifacts: task `work/live-structural-proof-40805062670`. Exact failed raw
reply/provenance is now `tests/fixtures/live-structural-reversed.json`. Behavior
request digest c4f133d85a9b6c25e031a6fb3284a06fa0c1bc40b94db2bea988cb08fa83cf98;
geometry request c96dd9391301510a79e3d2517bfa8abdd50c6c14478e746efe6e1792e5d22ab0.
Private exclusive started/response receipts remain under the user's AppData
minecraft-ai-engineer/acceptance directory. Both requests are consumed; no retry.
Usage: behavior10558in/67out; geometry11367in/910out. Result digests remain in the
accepted artifact receipts. No API key or separately billed service was used.

Source-grounded correction: actual passing west-facing references and pinned26.3
DiodeBlock.getInputSignal bytecode both show input is read at pos.relative(FACING).
Teach the geometry model that repeater facing names the INPUT direction, opposite
signal travel, with all four compass mappings and coordinate axes. Keep other
block conventions separate and raw model results unchanged. Add actual Minecraft
positive three-west/two-tick edge tests and negative three-east traces, plus a
regression test that the recorded failed reply stays untested despite valid shape.
Run these on GitHub. After those pass, allow **one** new explicit two-call proof
from a new frozen source; do not blindly loop further if it fails. The live
acceptance still requires actual functional success, not just correct block count.

Earlier setup runs40801664492 and40803355719 were cancelled before any SDK call;
40803884854 published a request then failed before a call at the missing structural
provenance allowlist. Fixes are present: seed blueprint ID equals projectId before
construction, reconstructed task ID equals that ID (not projectId:root), explicit
scenario travels through response workflow, and request/response owner/SHA guards
allow only catalog/generated/structural names. Unknown scenarios still reject.
Transport fails promptly on unexpected-inference. No old receipts were reused.

The native path is MultiplayerGameTests.liveStructuralFlow: hand-authored seed,
actual player support and AI completion, initial timing, explicit expansion,
behavior/geometry confirmation, separate apply, actual client glass edit/conflict/
removal, exact held-stack restoration, resume, new timing, companion restart,
Bob lesson/revocation, third game boot and screenshot. Setup contract seed name,
completed status/detail and blueprint task ID must match the real checkpoint.

Use `gh workflow run live-acceptance.yml --ref phase/12-live-structural-proof
-f scenario=structural` only after the direction checks pass. Once each matching
artifact appears, run the local responder with explicit `--run ID --attempt N
--sha SHA --model gpt-6-luna --stage behavior --scenario structural`, then geometry
with the same source/run. See docs/acceptance-guide.md. Do not edit/rebase the
checkout during calls. Inspect both receipts, native completion/restart markers,
actual before/after sources, timing and screenshot before any completion claim.

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

1. Finish L1-L3 actual structural model modification proof. Then broaden generated
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

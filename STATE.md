# Now

2026-10-28: Active goal: **complete all milestones**, including the remaining
phase1 brief and all six phase2 features. Do not redefine completion around the
catalog, a buffer fixture, or passing CI.
Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch: **phase/9-generated-circuits**, draft PR18. Main is PR17 merge88d4043.

Josh's latest direction: **skip heavy reviews; build and ship**. Do not schedule
more formal review cycles. Keep required checks and actual runtime proof. Tests
run on GitHub, never on his slow PC; local checks are scoped compilation/format.
No user question or permission is pending. Public repo, implementation, push and
merging green changes are already authorized.

## Current slice: generated timed circuits (G1/G3)

- D1: natural request -> independently proposed observable behavior -> explicit
  owner confirmation -> raw bounded generated geometry, derived preview/materials.
- D2: real construction and complete game-tick traces for stable off/on/off,
  including sample0 immediately after activation. Wrong timing or disconnected
  wiring must fail even when geometry matches.
- D3: strict both-runtime validation, private/shared previews and lessons, current
  permissions/conflicts, service/game restart, and an actual signed-in generated
  response through the normal command/HTTP path before merging this slice.

Implemented the bounded D1-D3 behavior: actual signed-in geometry passed native
construction, timing, sharing and restart on00e5da5. That manual whole run later
failed an unrelated creative-inventory fixture check. Final regression CI replays
the recorded model reply and verifies fixture cleanup before merge. Broader
functions, structural revision and the whole product remain unfinished.

## Current evidence and immediate next step

Sourceec38d8f passed all five gates **CI40759856316**, including all28 Fabric tests,
actual generated12/16 construction/test, matching-geometry/wrong10 timing failure,
checkpoint-write failure recovery, Bob's shared timing lesson and denied inference,
revocation clearing, and restart. Artifacts inspected under task
`work/generated-proof-40759856316`; second companion restart4671->4805. The
verifier checks exact generated source, baseline samples and both complete traces
in SQLite. An earlier same-corners fixture race was fixed by awaiting a blank new
project before direct fixture acceptance.

Source2df393f passed all five gates **CI40761542288**. It corrects a live prompt bug:
explicit desired behavior must not require layout feasibility to be proved first.
Ask clarification for ambiguous relation/units/timing or unsupported functions;
geometry and game tests decide feasibility and verification afterward.

Manual runs (no credentials uploaded; no automatic SDK retry):

- Request40760225622 / response40761209695 on ec38d8f: behavior returned null and
  asked about feasibility despite explicit12/16 intent. One SDK0.164.0/gpt-6-luna
  call,10327input/65output. Run cancelled; no geometry call. Prompt corrected.
- Request40761579328 on2df393f: actual behavior12/16 returned,10433input/80output;
  response40762542882 passed. Private receipt digest
  b2af6ef5e82547c71148f93886351cd07e6f7dceab2fe3616d16db362693d6c3.
  Geometry request digestc6b76c65a33d58e2f2f18938659300b186559ffd74a3617d7614c6e6eb635a7e
  has a consumed started receipt but no valid saved/published response. The generic
  responder hid the controlled error and ephemeral SDK output is gone. Cancelled
  run; **cause unknown**, do not guess or retry that receipt.

Next: run required CI for the fixture cleanup and recorded raw-response replay,
then merge PR18 without another review cycle. Do not spend more SDK quota merely
to repeat unchanged production behavior. Keep the original manual run's overall
failure explicit while reporting its passing generated scope and current complete
regression evidence. Continue immediately with explicit structural revision.

## Runtime decisions and constraints

Dedicated Fabric server first, including solo local play; same logical-server core
retains integrated compatibility. Java25/Minecraft26.3/FabricLoader0.20.1/
API0.165.0+26.3/Loom1.17.21/Gradle9.6. Node24.14/TypeScript/SQLite, pinned official
Codex SDK/CLI0.164.0. One private companion beside the operator's PC/server. No
hosting purchase, public AI endpoint, credential sharing, paid API or API key.
Only the configured account-owning operator starts inference; general multi-user
subscription eligibility remains unresolved. Solo SDK proof is not eligibility.
Published v0.1.0-alpha.1 remains the older single-player reference release.

The server derives actor and connection identities and fences writes/tests with
epoch, project/auth revisions, assignment basis/generation and reservations.
Disconnect, revocation, player conflict and restart stop execution. Persistence
restores descriptions, never a lease. Per-player tutorials are separate; clients
receive only authorized metadata and cannot call Codex. Current executor is
creative direct placement, replaceable later; supplied items/direct edits are not
survival gathering. Offline CI players are not Mojang-auth or human mouse proof;
optional-overlay opt-out is not a vanilla binary.

`/engineer plan` remains one curated call. `/engineer design` and `/engineer confirm
<nonce>` use separate isolated behavior/geometry calls. Pending proposals are
native-only, outside canonical revision, bind original request/actual actor
connection/epoch/revisions/corners/origin/rotation/nonce/dual TTL, and disappear on
restart. Confirmation authorizes one geometry inference, never construction.
Existing build/test/undo commands still require fresh explicit authority. Generated
structural replacement of built work is rejected in this slice.

Both runtimes derive/validate generated stages, materials, bounds, ports and tests.
Generated provenance includes behavior SHA256 of `buffer-v1|RISING|FALLING`.
Observable buffer latencies0..128 game ticks include real immediate0-on behavior;
physical proofs also cover0-on/4-off and two serial repeaters12-on/16-off. Generated
construction preflights/reserves a loaded clear two-block margin and exact lever
state while retaining explicit BUILD task semantics. Production traces use actual
world time and replay saved samples against the accepted digest/scope; missing
samples cannot pass. Existing input WAL handles restoration without overwriting
player edits. Large generated-source validation cost still needs attention before
scaling beyond the small vertical circuit; do not cache mutable source blindly.

Manual generated transport reuses fixed owner/repo/workflow/run+attempt/SHA checks,
bounded single-file artifacts and exclusive private started/response receipts.
Behavior and geometry have distinct stages and digests. The native owner confirms
only the originally requested12/16 intent. Live flow previews actual model wiring,
places one support via vanilla client packets, delegates remaining blocks, measures
both edges, restarts companion, shares/revokes Bob's preview, then reloads the game
with passing evidence marked stale. Ordinary CI never invokes the real SDK.

## Proven foundation on main

PR17/CI40715295100: operationScopes schema1/journalv4 retains immutable original
intended or explicit legacy-position sources across later revisions and genuine
undo crash recovery. Limits4097 mappings/64 snapshots/65536 stored blocks/8MiB.
Old flat/v2/v3 saves pass their original rules before migration; no invented
semantic history. Undo reserves original positions/read closure under current
grants. Active baseline/dependencies stay tied to captured source. Structural
transitions still need explicit old/new diffs, ordering, conflict and inverse rules.

PR16 exact world-write guards; PR15 bounded private read-only lesson followups;
PR14 private inventory/targeted perceptions; earlier dedicated permissions,
reservations, component tasks, repair/revise/test and real service/game restart
proofs remain covered by all five gates. Private inventories are ephemeral,
connection-bound, excluded from SQLite/audit/shared model context; never expose
custom names, NBT or unopened containers.

Actual signed-in catalog lamp acceptance passed sourcea64f352, request40624276545 /
response40624742436, one local SDK call and real client handoff/test/restart. See
`docs/acceptance-guide.md`. It proves only that fixed catalog path. The initial
constructed screenshot showed blocks; final old restart image showed ghost bounds,
so it is not proof of final rendered construction.

## Remaining roadmap (preserve full scope)

1. Finish current D1-D3 live generated flow and merge PR18 with required checks.
2. G2/G3: explicit old/new structural changes, player-edit preservation, safe order,
   inverse undo and restart. Then different functions, electrical interfaces and
   broader redstone/mechanical semantics; no catalog-only substitute for generation.
3. E1: free-form teaching/followups and uncertainty clarification; durable goals,
   rationale, preferences/decisions and later identification/explanation. Current
   exact ask phrases are only a first slice.
4. E3: broader missing/orientation/connection/timing/interference/inconclusive
   diagnosis, explicit repair previews and textured directional ghosts. Settled
   endpoints are not latency evidence; current generic boxes are not rich overlays.
5. E4: full signed-in generated preview -> player section -> AI rest -> interrupt /
   modify / resume -> actual test -> service/game restart -> later explanation,
   with richer real-client visuals. Fixed lamp/buffer proofs are bounded evidence.
6. Phase2, all unstarted: voice conversation; visible physical NPC companion;
   genuine survival gathering/placement; schematic import/export; measured size,
   resource, latency/reliability optimization; multiple independent builders.

AGENTS.md and STATE.md are the only project memory. CLAUDE.md contains @AGENTS.md.

Sourcebe88db6 passed all five gates **CI40763893283**. Manual40763954487
behavior succeeded (response40764897528). Geometry diagnosis is now concrete:
private request25edf0ef40ce6acfadcc7a8d0601283ebaa7a1b1fc075e4e88ccbe5681926402
completed SDK inference (10492input/1708output), but returned79 blocks acrossx0..64
for a4x2x1 workspace, all with shorthand unnamespaced states. Strict validation
rejected it before publish/world editing. Cancelled run, receipt consumed.
Proof0da5e3d / CI40765617098 fails at missing generatedGeometrySchema. Implement
code-derived per-request schema coordinate bounds, rotation and block-count cap;
enumerate the38 supported canonical namespaced states. This preserves topology
generation, not a new catalog template. Keep independent support/state/fit and
actual timing checks. For extents>65 use a translated signed frame within-64..64
to preserve all128 representable positions. Geometry reasoning uses medium while
behavior/catalog stay low; explicit general repeater/lamp timing semantics are
calibrated by the physical0/4 and12/16 tests. TypeScript compile passes.

Source00e5da5 passed all five gates **CI40765916470**. Real generated request
40766098371 accepted behavior (response40766960852) and raw geometry (response
40767111851): actual two serial repeaters4+2, eight blocks, namespaced states.
Native log records actual12-on/16-off pass. All generated player-support, shared
Bob lesson/denial/revocation, second companion restart4525->5262 and third game
boot markers passed. Inspected screenshot: real constructed blocks visibly render
after restart, unlike the old catalog ghost-only image. Retained artifacts at
task work/live-proof-40766098371. Behavior request2fce23c64d4bf8023190b66b80352933
a985729edd139c4c41a67dc9b8c168cb; geometry request7fac846660b19afe4bb367ea4e73
ee5c6f44ec00158cae2936fb45257cd6097d. Both receipts/accepted digests inspected.
The whole manual run nevertheless failed afterward: creative fixture item supply
left64 stone instead of restoring the prior7, breaking the older inventory check.
Fix only test cleanup, not production behavior. Replay the actual recorded model
response deterministically in ordinary CI, including the same client support
placement and full post-restart inventory proof. This avoids unnecessary repeat
SDK quota while preserving real signed-in feature proof plus current regression
proof. Do not call that old manual run a successful overall run or replay a fresh
live call. Required combined CI must pass before merge. Josh says skip heavy
reviews and ship; no new formal review. D1-D3 feature behavior is now demonstrated;
next action is final fixture regression CI then merge PR18 and start structural
revision. Full broader phase1/phase2 objective remains active.

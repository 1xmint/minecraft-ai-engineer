# Now

2026-10-28: Active goal: **complete all milestones**, all remaining phase1 and
all six phase2 features. Do not redefine done around a catalog, buffer or green CI.
Public repo: https://github.com/1xmint/minecraft-ai-engineer
Branch **phase/10-structural-revisions**, based on merged PR18 **bebf6ca**.

Josh: **skip heavy reviews; build and ship**. No more formal review cycles.
Keep required checks and concrete runtime proof. Tests run on GitHub, never on
his slow PC; local work only scoped compile/format checks. Public implementation,
push and merging green changes are authorized. No question/permission is pending.

## Next slice: explicit structural revisions (G2/G3)

- S1: owner sees an explicit old/new geometry diff and can review it without world
  editing. Workspace expansion is explicit, owner-only, and retains all tracked
  old work/history. Unsupported, stale or unauthorized proposals leave canonical
  intent and world unchanged; reject before quota where the problem is knowable.
- S2: explicit apply changes actual structure under fresh server authority and
  bounded loaded reservations. Preserve player-built/intervening edits. Attachment
  order and before states are checked for every removal/addition. Denial, conflict,
  disconnect or revocation pauses safely without silently overwriting anything.
- S3: genuine interruption/restart and undo keep both original intentions and exact
  transition history. No fabricated union blueprint or relabelled semantic source.
  New geometry must pass its independently accepted functional contract. Corrupt
  scopes/foreign identities fail before world editing; restoration grants no lease.

Tasks: trace Blueprint/EngineeringSession/OperationScopes/trial/gateway interfaces
before code (S1/S3). Write immutable old/new-diff boundary and native no-edit/
conflict/recovery proofs first, observe GitHub failure (S1-S3). Implement explicit
pending transition, captured sources, write/read closure, bounded step ordering
and genuine before/after WAL; keep legacy journals compatible (S2/S3). Wire owner
workspace expansion, generated revision preview and explicit apply without losing
original request/current-source context (S1/S2). Prove real model modification after
executor safety works, plus actual timed behavior and service/game restart; all
required gates then merge (S1-S3). No extra heavy review.

First physical prototype: existing generated two repeaters4+2 -> three repeaters
2+2+2, preserving accepted12-on/16-off behavior while growing from four to five
blocks wide. Keep the player's support, explicitly replace the old lamp position
and extend wiring/supports. This is a structural transition, not another catalog
or a delay-only parameter change. Broader functions/interfaces follow it.

## Shipped generation evidence

PR18 merge **bebf6cacdd8d60bddbbf9db5f42fb1ca601493ae**. Final source076e4dc passed
all five gates **CI40768389557**: all28 Fabric tests, actual recorded-model geometry,
player client support placement, AI remaining construction,12/16 edge traces,
matching-geometry/wrong10 timing rejection, confirmation-save failure recovery,
shared Bob lesson/denial/revocation, service and game restart, inventory recovery
and durable source/evidence checks. Inspected artifacts at task
`work/generated-proof-40768389557`; native markers and SQLite verifier passed.
Local Java main/client and TypeScript compile passed; no local tests ran.

Actual signed-in source00e5da5/request40766098371, behavior response40766960852,
geometry response40767111851: official SDK0.164.0/gpt-6-luna returned desired12/16
then raw eight-block wiring, two serial repeaters4+2. Native construction, both
measured edges, shared lesson/revocation, companion restart4525->5262 and third
boot passed. Inspected PNG visibly shows constructed blocks after restart, now
`docs/images/generated-restarted.png`. Both private accepted receipts/digests match.
Retained full artifacts: task `work/live-proof-40766098371`.

That manual run's **overall result failed** after those feature checks because a
creative-item fixture left64 stone instead of restoring prior7. Only test cleanup
changed afterward: restore exact prior held stack/components. Final CI replays the
actual recorded raw reply from `tests/fixtures/live-generated-buffer.json` through
the same player/AI path, including complete inventory/restart recovery. This uses
no new inference and is not a fresh live call or an overall successful old run.
Production behavior was unchanged for the cleanup. See docs/acceptance-guide.md.

Earlier real failures improved the pipeline: explicit behavior no longer requires
layout feasibility proof first; generated schema now constrains delegated volume,
quarter-turn coordinate frame and38 canonical supported states. Geometry uses
medium reasoning, behavior/catalog low. Opt-in bounded private untrusted-result
and controlled failure capture prevents blind retries. Never retry a consumed
started receipt; never publish invalid raw output or credentials. Historical failed
receipts stay private under `%APPDATA%/minecraft-ai-engineer/acceptance`.

## Runtime decisions and constraints

Dedicated Fabric server first, even for solo local play; integrated-compatible
logical-server core. Java25/Minecraft26.3/FabricLoader0.20.1/API0.165.0+26.3/
Loom1.17.21/Gradle9.6; Node24.14/TypeScript/SQLite; official SDK/CLI0.164.0.
One private operator-owned companion beside the server/PC. No hosting purchase,
public AI endpoint, credential sharing, paid API or API keys. Only configured
account-owning operator starts inference. General shared-subscription eligibility
remains unresolved; solo SDK proof is not eligibility. Published alpha remains
older single-player reference; main is the multiplayer source build.

Server-derived actor/actual connection, epoch, project/auth revision, assignment
basis/generation and reservations fence every mutation/test transition. Restart,
disconnect, revocation and conflict stop execution. Persisted data restores no
lease. Private tutorials are per player; authorized metadata is not model access.
Current physical executor is creative direct placement, replaceable later, never
survival gathering. Offline CI actors do not prove Mojang authentication or human
mouse use; optional-overlay opt-out is not a vanilla binary.

`plan` retains one curated call. `design` -> `confirm <nonce>` separately proposes
behavior then raw geometry. Pending consent is native-only/outside canonical
revision, binds original request/actor connection/epoch/revisions/corners/origin/
rotation/nonce/dual TTL, and disappears on restart. Confirm authorizes inference,
never construction. Generated replacement of built work is currently rejected.
Both runtimes derive/validate stages/materials/bounds/ports/tests/provenance;
behavior digest is SHA256 UTF-8 `buffer-v1|RISING|FALLING`. Buffer latency0..128 game
ticks includes real immediate0-on/4-off and serial12/16 physical proofs. Generated
construction reserves a loaded clear2block margin and exact lever state without
changing explicit BUILD delegation. Traces sample actual game ticks including0,
replay claims against accepted intent/scope, and cannot pass gaps. Input WAL
restores without overwriting player edits. Watch repeated source-validation cost
before scaling far beyond the small circuit; don't cache mutable source blindly.

Manual synthetic transport uses fixed owner/repo/workflow/run+attempt/SHA,
bounded single-file stage artifacts and exclusive private receipts. Credentials
never enter CI. Ordinary CI replays deterministic data, never real inference.
Private inventory snapshots are ephemeral, own-connection bound and excluded from
SQLite/audit/shared model context. No NBT, custom names, world text or unopened
container contents enter model context.

## Foundation relevant to structural edits

PR17/CI40715295100 operationScopes schema1/journalv4 preserves immutable original
intended or explicit legacy-position sources through later revisions and genuine
undo crash recovery. Limits4097 mappings,64 snapshots,65536 stored blocks,8MiB.
Old flat/v2/v3 buffers validate original rules before migration; no invented intent.
Undo reserves historical positions/read closure under current grants. Baseline/
dependencies stay tied to their captured source. Structural transitions need
explicit old/new semantics, not an invented union source. Current journals store
one captured source per operation; inspect this boundary before changing it.

PR16 exact world-write targets, PR15 private read-only lesson followups, PR14
private inventory/targeted perception and earlier dedicated project permissions,
components, reservations, repair/revise/test remain covered by all required gates.
Signed-in catalog lamp request40624276545/response40624742436 proved only that fixed
path. Its old final screenshot was ghost-only; don't claim rendered construction
from it. The newer generated restart PNG does visibly render real blocks.

## Remaining full roadmap

1. Current S1-S3 structural revision, then different functions, electrical interfaces
   and broader redstone/mechanical engineering. No catalog-only substitute.
2. E1 free-form teaching/followups and clarification; durable goals, rationale,
   preferences/decisions and later identification/explanation. Exact ask phrases
   and repeat/skip/pause are only a first slice.
3. E3 broader missing/orientation/connection/timing/interference/inconclusive
   diagnosis, explicit repair previews and textured directional ghosts. Settled
   endpoints are not latency evidence; current generic boxes aren't rich overlays.
4. E4 full signed-in generated preview -> player section -> AI rest -> interrupt /
   modify / resume -> test -> service/game restart -> later explanation and richer
   visuals. Fixed catalog/buffer proofs remain bounded evidence.
5. Phase2 all unstarted: voice; visible physical NPC; genuine survival gathering/
   placement; schematic import/export; measured size/resources/latency/reliability
   optimization; multiple independent builders.

AGENTS.md/STATE.md are the only project memory. CLAUDE.md contains @AGENTS.md.

S1/S3 boundary proof937d795 / **CI40770908941** failed at missing
StructuralTransition before implementation. GPT-6-Luna supplied only immutable
data class54c0bc1, integrated9736e69. Root removed redundant parsed-source storage
and checks serialized budget before cloning read data. Java main/client compile
passed; no local tests. The new class keeps two real sources, exact added/removed/
changed positions and ordered detach/support/device steps; its saved data grants
no authority and currently drives no world edits. PR19 stays draft for wholeS1-S3.

Concrete next integration constraint from source: existing preflightUndo compares
every journal row with the current world, assuming one touch per position. A
replacement has old->AIR and AIR->new at the same position, so that preflight
would reject a legitimate inverse. Add projected inverse-state validation before
using structural steps, retain actual before-state checks at every mutation, and
cover genuine interruption. OperationScopes currently stores one source and its
positions per operation; transition scopes need both real sources/write union and
read closure, never an invented union blueprint. Do not simply plug new steps into
the old journal and claim undo/restart works. These production pieces, explicit
workspace/preview/apply and real modification acceptance remain pending.

StructuralTransition source d0b3bf3 passed Fabric **CI40771775541**, all30 required
tests; Companion/Windows/integrated client also passed before the next proof push.
Paired-scope prooff084fca / CI40772570707 failed at missing captureTransition/
sources/positions. GPT-6-Luna implemented only OperationScopes9a0ae2b, integrated
1eb1fe5. Schema2 explicitly stores paired sources and exact writes; schema1 old
wire/hash behavior stays unchanged, and pruning the pair returns the original
schema1 graph. Root caches paired component-footprint validation across operation
references and adds corruption/version-relabel rejection. Java compile passed.

Critical caller contract: operationScopes.blueprint(transition) is the real AFTER
blueprint, not an execution/undo footprint. Future reservation writes must use
positions(operation), and dependency reads must cover sources(operation), both
real before/after sources. Current world executor/load/undo callers have NOT yet
been adapted, and nothing creates a production transition operation yet. Next
implement projected inverse preflight, explicit transition WAL/step execution and
preview/apply/workspace expansion with actual native conflict/restart proofs.
Check the strict adjacent revision assumption when redo/new planning follows an
undo while canonical intent remains later; never relabel the true historical
source just to satisfy an identity check. PR19 stays draft; S1-S3 incomplete.

Paired-scope source1badb10 passed all five gates **CI40773555504**, Fabric all31
required tests. S1 workspace proof5c98be9 / CI40775421540 fails at the missing
expandWorkspace method before implementation. Add explicit owner-only monotonic
expansion retaining the whole previous region; preserve blueprint ID/revision,
journal, baseline and all world blocks. Reject active build/planning/test/pending
restoration, shrinking, invalid volume and missing cross-project EDIT before any
change. Invalidate pending model consent/leases only after successful validation.
On save failure restore old corners in memory without resurrecting authority.
Native proof drives owner expansion, Bob EDIT-but-not-owner denial, shrinking,
real active-build refusal and retained expanded corners after third boot. Owner
travels to the busy-proof workspace and waits for loaded dependencies; no fake
state flags or forced chunks. Structural preview/apply and inverse WAL still
pending; this command alone is not a structural executor.

S1 expansion implementation passes scoped Java main/client compile. Pushed for
native owner/EDITOR rejection, no world edits, preserved journal, true active-build
refusal and expanded-box restart proof. No local tests or extra review. No
production structural transition is enabled yet; next work remains inverse
projection and the explicit pending preview/apply + WAL executor.

Workspace source c606bb1 passed all five gates CI40776834318. Inverse projection proof 6d37653 failed at the absent UndoProjection class in CI40778110622. The implementation now simulates repeated-position inverse rows without changing the world, checks projected attachment supports, and limits neighbor exemptions to the operation being undone. Scoped Java main/client compilation passed; GitHub runs the tests. Actual structural execution and interruption/restart remain pending. Josh explicitly requested skipping heavy reviews; continue implementation and required CI.

Undo reservations now use the captured operation's exact write positions and bounded dependency closure from both actual sources. Added-only and removed-only dependency edges are covered by the paired-scope game test; legacy source behavior remains covered. Scoped main/client compilation passed. Structural apply is still disabled until its WAL executor and actual interruption proofs land.

Structural execution is now connected to production apply/resume/tick and the local checkpoint, with a durable step cursor and paired operation scope. It checks owner authority, loaded before states and AI ownership before replacing old cells; matching player additions are adopted without journal rows. Every actual write uses the existing WAL and live lease check. Test input leases retain the full after-layout write footprint, while structural execution and undo reserve the exact diff. No user command or generated-model route enables structural apply yet.

Added dedicated native acceptance: build a real two-repeater fixture with an existing player support, revise to three repeaters, stop after actual writes, preserve and reject an intervening glass edit, resume after resolution, measure 12/16 game-tick behavior, actually restart the server, and undo the repeated-position operation while preserving both player supports and the old operation. The new native proofs have not run yet. Scoped Java main/client compilation passed; no local tests. CI2b8d0b3 currently has four green gates and dedicated multiplayer still running. Next fix acceptance failures, then add explicit preview/apply consent and model revision routing before merging PR19.

Added ephemeral structural difference preview, authorized changes inspection and a private one-use /engineer apply confirmation. Consent is bound to the owner connection, authorization revision, accepted revision, workspace, selection and five-minute expiry. Stop/disconnect/context changes discard it. Native proof now drives the actual apply command after rejecting a wrong nonce, verifies preview leaves world/history/intent unchanged, then leaves the real interrupted transition paused across the actual third server boot before resume, timing test and undo. No synthetic cursor/state checkpoint is substituted. Model routing is still pending; PR19 is not ready to merge. Scoped main/client compile passed before the last cancellation-hook/doc edit.

Generated planning now permits completed generated root builds to request a structural revision through the existing behavior -> confirm -> geometry flow. The resulting geometry remains an ephemeral diff, not canonical intent, until the separate private apply confirmation. Native CI now drives that full HTTP/Minecraft path with an explicitly hand-authored three-repeater response; it is not a live or recorded model inference claim. It verifies a wrong apply nonce is rejected, real stop/conflict preservation, an actual reboot while the cursor is unfinished, explicit resume, real 12/16 timing, and undo preserving the older journal/player supports. Scoped Java main/client compile and fixture JavaScript syntax checks passed. Four gates passed on executor source50c5204 while its dedicated run remained live; newer full-path proof is pending. Do not merge or claim S1-S3 proven until all five checks pass and native evidence is inspected. Remaining gap: later planning after undo still needs true historical/current intention semantics; never rewrite a historical before source to invent adjacent revisions.

Full-path source04e6033 / CI40780210614 failed before structural inference because the prior generated confirmation cases exhausted the real four-start/minute budget (native log22:34:42-56). Production quota remains unchanged. The structural acceptance now waits one real61-second window before its two confirmed fixture requests; no retry of consumed consent and no test quota bypass. Retained failure artifacts at work/structural-proof-40780210614. Structural executor/restart/undo acceptance is still unproven; run the new source in GitHub CI.

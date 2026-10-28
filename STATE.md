# Now

2026-10-27: Active goal: **complete all milestones**, remaining phase 1 plus all
six phase 2 capabilities. Do not narrow it or claim the full product shipped.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Working on **phase/6-contextual-teaching**, based on evidence PR14 merge
**2c97246** (all five gates CI40625698878). PR13 bootstrap c80b8cd and the actual
signed-in lamp proof below are landed. The full original scope remains active.

## Current teaching slice plan (#3/E1)

- T1: ask for current lesson details/timing or an explicit component without
  retargeting the shared AI. Use the actor's existing tutorial stage to resolve
  the leaf; explicit target overrides only the answer. Show canonical materials,
  ports, intended delay and distinct current/historical functional labels. Proof:
  Bob's B lesson while A executes; no lease/controller/assignment/journal/world or
  cursor changes; A's original test revision remains accurate after B revision.
- T2: exact bounded read-only phrases and explicit component syntax only. Unknown,
  negated or mutating text requests clarification, never calls planning/build or
  creates a project. VIEW checked before facts. Proof: real command rejection,
  no project auto-create, denied membership and no private reply to controller.
- T3: reuse existing teach/repeat/skip/previous/private pause controls. Verify own
  cursor stays separate from execution and survives actual restart as paused.
  This slice adds no global controller or new lesson persistence format.

Options weighed: no change misses useful followups; expanding repeat alone misses
context/timing; a new LLM advice endpoint spends quota and adds privacy surface for
routine known facts. Choose a bounded local lesson assistant, no world scans or
inference. GPT-6-Luna independently challenged actual interfaces and confirmed the
existing controls already suffice; avoid duplicating them. Auto advance runs every
10 ticks, so ask must be read-only and never toggle teaching. Existing /resume is
BUILD execution, not a lesson alias. New ask uses existing-selection-only handler;
old commands retain their auto-create behavior. Intended delay is not a measured
latency; historical pass scope is never relabelled as a fresh test.

Proofs first, native GitHub failure, implementation, scoped local compile, final
review and all five gates. Full E1 dialogue/decision memory, E2 connected designs,
E3 deeper diagnostics/textured ghosts and all phase2 features remain later work.

Teaching proof **2d54c55 / CI40627174587** failed exactly at the missing ask
command before any project existed. New LessonAssistant and ask(create=false)
handler now compile: exact normalized bounded phrases, canonical leaf facts,
actor-stage target resolution, intended timing vs stored/historical labels,
private replies, no cursor/save/world/model calls. Existing teach gained an
explicit leaf argument to select only the actor's first component lesson stage;
unknown/group targets fail before cursor creation. Shared AI target stays intact.

GPT-6-Luna reviewed actual source and the small explicit teach extension; no
blocker found. Native proof now waits for actual pause/denial chat acknowledgments
instead of an old paused flag, and compares Alice's cursor after Bob's controls.
Java main/client compile and formatting passed; combined native/data acceptance
is pending the next CI. Nothing here completes broader free-form E1 or E2/E3.

Root corrected a recovery-fixture assumption before acceptance: existing
ProjectManager.changed intentionally removes a revoked member's cursor. Recovery
must verify Bob stays absent and Alice's explicitly paused B lesson survives,
not require revoked metadata to reappear. No product behavior was relaxed.

The actual signed-in lamp path PASSED: request workflow **40624276545** and
response workflow **40624742436**, both source **a64f352**, owner-dispatched
attempt1. One local official SDK0.164.0/gpt-6-luna call used10529 input/73 output
tokens, returning signal-lamp/delay1/rotation0. Local receipt and retained CI
request/result/accepted digests match; acceptedAt was within15min. No login or
SDK event payload was published. The run is terminal success; source freeze ended.

Root inspected live-model-design, actual player-client-construction, original
functional cases, SQL proof, companion restart3472->5449, game/component/undo
recovery markers and native chat. First constructed-client PNG visibly shows the
six correct blocks. The final post-teleport restart PNG shows ghost bounds only;
do not use it to claim post-restart visual rendering. Actual server restart and
functional evidence passed. Richer visualization still needs its own driven proof.

Request digest: fb61cac0cfb1b0b61bde69a633712ef3828f073f9d237926f5f645181964b594
Result digest: 67fd6d677a3188194990ab694a905db7e87e663249a3776a59cca02d87947027
Artifacts: task work/live-proof-40624276545 (synthetic fixtures only).

Next: broader #3 engineering and teaching. This fixed lamp call is selection,
not general design generation or natural-language modification/clarification.
Its model explanation even calls teaching outside the selector, while the actual
catalog tutorial advanced correctly; make model context accurately describe the
app and add useful followups. Durable objectives/rationale/preferences/decisions,
new connected designs, timing transition diagnostics, textured ghosts and later
project explanation remain. Phase2's six features are all still unstarted.
Tests stay on GitHub; only the operator SDK call and scoped compile ran locally.

## Acceptance Outcomes and bootstrap proof (#3/E4)

- P1 PASS: real client placed three supports and the oriented lever, tutorial
  advanced step1->2->3, client removed initial scaffold, assistant journal stayed
  empty until actual delegation. Initial creative item/scaffold supply is fixture
  setup. CI40620567886 and final combined CI40623373036 passed.
- P2 bootstrap and live lamp path PASS: the normal gateway supplies an immutable
  authenticated frame separately from model text. A CI-only one-shot file provider
  accepts only the reconstructed fresh lamp scenario, refuses arbitrary memory,
  crosstalk, replay, expiry and cancellation. Local responder verifies reviewed
  source and owner/workflow/run+attempt/artifact provenance before one official
  isolated SDK call, saves a private durable receipt/result, and can resend without
  another call. Separate owner-only manual workflows transport bounded data, never
  login credentials. CI40621899855 failed missing modules before implementation;
  all new rejection proofs passed final CI40623373036.
- P2 manual proof PASS on40624276545: actual validated model plan matched one-tick lamp,
  rotation0/selected anchor, then drove real player/AI/construction/conflict/test
  and service/game restart markers together. A model answer or published workflow
  alone is not this proof. A passing lamp path does not prove general engineering.

GPT-6.1-Sol independently reviewed bootstrap. Fixed its95s service deadline
(live CI only900s; ordinary deadline unchanged), incorrect metadata assertion
(actual provenance.template) and missing delay1 requirement assertion. No further
confirmed auth/data blocker; bounded ZIP format checked against official archiver.
Java main/client, TypeScript, JS/shell syntax and format checks passed locally;
all tests ran on GitHub. Operator guide: docs/acceptance-guide.md.
WorkerSol dc2f855 integrated asafb3bf9 plus root formatting; clean worker checkout
was removed after status/cherry confirmed integration; branch remains recoverable.

## Outcomes and proof for #2

- C1: player builds B while assistant completes A; show hierarchy, ports,
  prerequisites, material totals and assignments. Native component proofs passed
  CI40539445528 and fuller CI40584567109; screenshot/logs/SQLite inspected.
- C2: handoff/delegate-back preserves grouped journals, immutable evidence and
  generations through service/game restart; stale connection/grant/basis cannot
  authorize writes. Scoped revise/diagnose/repair/test and full transitive
  read-margin rejection/recovery passed CI40586816662. Final undo fix pending.
- C3: authenticated actual block/view/support samples and read-only private
  inventories; unloaded/refused reads fail closed; only affected tasks stale.
  Typed native/HTTP/SQLite proof passed CI40586816662. Inventory native and HTTP
  proofs passed CI40595380532, including actual double-chest/crafter menus, exact
  unchanged stacks/components, private Bob receipt, grant revocation, actual 20s
  expiry, normal vs observed seeded vanilla loot and cache absence on third boot.

## Decisions and implementation

2026-10-26: dedicated Fabric server first, integrated-compatible logical-server
core. Java25/Minecraft26.3/FabricLoader0.20.1/API0.165.0+26.3/Loom1.17.21,
Gradle9.6; Node24.14/TypeScript/SQLite; official Codex SDK/CLI0.164.0 pinned.
One private operator-owned companion beside Josh's PC/server. No hosting purchase,
public AI endpoint, credential sharing or separately billed API. Only configured
account-owning operator starts inference. General shared multi-user subscription
eligibility remains unresolved; the old solo SDK probe cannot establish it.
Sources: https://docs.fabricmc.net/develop/networking,
https://wiki.fabricmc.net/tutorial:side, https://learn.chatgpt.com/docs/auth.

2026-10-27: bounded component tree inside one project, replacing neither project
ACLs nor the execution core. Rejected a general swarm/whole-blueprint worker:
independent leaves suffice. Limits:64 nodes/16 leaves/depth8/extent64/64 stages/
4096 blocks. Groups organize; signal-lamp/not-gate/or-gate leaves have namespaced
ports/stages/tests. Dependencies require completed prerequisites, **do not wire
circuits together or prove electrical interfaces**. One active AI per project.

Server-derived actors/connections/nonce, epoch, project/auth revisions, assignment
basis/generation and reservations fence execution. Full transitive read/margin
closure is checked before mutations and each live/final test transition; grant
revocation, disconnect, conflict or restart stops work. Assignments grant no lease.
Component diagnosis is read-only VIEW without retargeting/pausing another task.
Repair explicitly delegates one leaf under fresh authority, revalidates exact
snapshot and rolls selection/assignment back on rejection. Revision changes only
bounded intended delay, preserves world/journal and unrelated original passes.

ComponentAssignments basisVersion2 memoizes transitive prerequisite intent. V1
records must match their exact legacy hash before migration; changed tasks stale
and increment generation. Corrupt/unknown versions fail closed. Journalv3 groups
contiguous component operations; flat/v2 migrate to root. FunctionalTrial captures
project/exact blueprint/revision/component/basis/version; archived vectors retain
original intention and scope. Pending lever inputs have per-input write-ahead
restoration. Never relabel old vectors or infer function from matching geometry.

Companion derives invalidation from canonical immutable blueprints, preserving
unrelated historical rows, pausing changed/dependent/removed/unscoped/live work.
Coordinate history across20 samples catches intersecting two-block read closures;
ACL/registration pauses all tasks. Legacy identity adoption clears result scopes
and recreates unassigned descriptions while preserving exact source/backup/journal.
Real companion kill/restart (distinct PIDs, same private SQLite/config) passed.

WorldPerception targets seven actual states, self pose/view/ray hit and sampled
geometry differences. Preflight bounded six-block ray plus margin, loaded bounds
and foreign VIEW. No world text/NBT/names. Strict DTO actor/schema checks happen
before report ordering. Pinned local Minecraft bytecode verified unfamiliar APIs.

InventoryPerception records metadata-only UseBlockCallback and always PASS; only
a later naturally opened exact vanilla menu/backing can be read. Both double
chest halves must be loaded, permitted, same identities and resolved loot. Exact
CompoundContainer only; skip exact crafter virtual result slot without getters.
No forced menu/chunk, NBT/names, item setters or carried-container unpacking.
Requests capture actor/connection/epoch/revision/ACL/selection and expire400ticks
or20monotonic seconds. Private receipts go to captured actor, not project controller.
The separate strict /v2/inventory route uses normal report ordering; latest cache
is per actor/project,256max,60s receipt TTL, no SQLite/audit/save facts. Connection,
authority/revision changes and restart invalidate it. Shared design context never
contains inventory; privateContext remains separate for later private advice.
Materials accepts a captured optional leaf, labels snapshot age, reserves nothing.

## Review findings and red/green evidence

GPT-6-Luna review hit usage limits. GPT-6.1-Sol completed focused/final reviews.
It found a live far-read-margin gap: proof-first CI40585762989 incorrectly reached
C pass after editing B's margin. Full-closure reuse fixed it; CI40586816662 proved
pending-input pause, explicit recovery, fresh pass and unrelated A preservation.
Inventory review found crafter virtual result rejection; exact skip fixed/proven.
CI40592614026 expiry fixture waited10s for product20s TTL; root corrected harness
wait to35s without changing product timeout, then CI40595380532 passed.

Final phase review found P1 undo context contamination: latest B undo selected B
before lease/preflight while retaining selected A pass/baseline. Rejection proof
**608bf78 / CI40597951925** failed at the exact denied-undo selection assertion.
Transactional beginUndo now captures/restores original context on rejection,
archives original evidence only after successful preflight, clears old recovery
fields and persists an untested B target before writes. Never restore authority
or roll back after ambiguous save/commit attempt. Reviewer accepted actual code;
new real restart proof remains pending after the harness correction above.

## Roadmap / next slices

1. Finish #2 current native undo proof, inspect retained evidence, update PR and
   merge after all five gates. Local checks only compile/format; CI tests only.
2. #3 deeper engineering/teaching: carry the whole unmet product brief forward.
   Natural followups/clarification before uncertain mutation; objective/rationale/
   preferences/decision memory; real connected multi-component designs and useful
   modifications beyond catalog delay; broader redstone semantics; actual timing
   transitions; missing/orientation/connection/timing/interference/inconclusive
   diagnosis; explicit repair previews; textured directional ghosts/explanations.
   Scope into proven slices without silently declaring general designs complete.
3. #3 exact combined acceptance: natural live Codex request -> validated explained
   design -> preview -> **real client placement** of first section -> AI rest ->
   interrupt/modify/resume -> actual functional proof -> game/service restart ->
   identify/explain later. Existing CI fixture-places human sections via server;
   do not claim actual client placement. Separate signed-in probe plus deterministic
   native fixture is not combined model/game acceptance. Public CI stays secret-free;
   no combined bridge/approach has yet been decided or proven.
4. Phase2 #4: voice conversation, visible physical NPC, genuine survival gathering,
   schematic import/export, optimization of size/resources/latency/reliability and
   multiple independent builders. All unstarted. Keep creative executor replaceable;
   do not use paid APIs, fake survival resources or silently broaden account access.

## Proven foundation / release

Foundation #8 merged PR10 **ae90741**; mechanism/repair #1 merged PR11 **28301e5**.
Main proves dedicated/integrated execution, private grants/shared authorized
previews/personal lessons, conflicts/revocation/restart/clone/adoption and three
actual catalog oracles. Offline fixture identities/overlay opt-out are not Mojang
authentication or a separate vanilla binary. Keep production online-mode=true.
Published v0.1.0-alpha.1 at168ac190 remains the older solo reference. PR5 proved
handoff/save/reopen/conflict/undo/test; signed-in SDK gpt-6-luna probe passed
separately. No combined game/model or general shared-account proof is claimed.
CI graphics signed APT archive/Azure bounded fallback passed native checks; sources:
https://manpages.ubuntu.com/manpages/noble/man1/apt-transport-mirror.1.html and
https://github.com/actions/runner-images/blob/main/images/ubuntu/scripts/build/configure-apt-sources.sh.
Retained artifacts in the task work directory: component-proof-40584567109,
margin-red-40585762989, perception-proof-40586816662, inventory-proof-40595380532.
Worker inventory-companion1ee545f integrated ase1fe550 plus root formatting/fixes;
its clean worktree was removed after status/cherry confirmed integration. Old clean component worker checkouts were removed
only after git cherry confirmed integration; recoverable branches remain.

Next acceptance architecture challenge: GPT-6.1-Sol recommends a manually chosen,
one-shot synthetic CI exchange. Local operator invokes the existing isolated
Codex provider once; authenticated GitHub workflow/artifact transport carries
only validated synthetic request/Plan, never credentials or real player worlds.
Bind repository/workflow/reviewed SHA/run+attempt/scenario/nonce/context digest/
expiry and response actor provenance. Reconstruct allowed request locally; never
execute downloaded code or auto-spend quota for arbitrary runs. Return through
PlanningProvider and normal gateway/Fabric validation. Two integration points to
solve: provider now gets immutable authenticated gateway context (proven in
CI40620567886), and ordinary Minecraft HTTP90s timeout is shorter
than manual exchange plus provider90s (separate bounded acceptance timeout only).
Default CI must still reject inference. Actual client placement and journal
exclusion passed CI40620567886. A lamp flow proves E4's path,
not general design capability. Research: https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event
and https://docs.github.com/en/rest/actions/artifacts#download-an-artifact.

Transport build plan: separate manual request/response workflows must first be
published on the default branch before workflow_dispatch can execute them.
Bootstrap reviewed infrastructure, then prove the live flow after publication;
do not call the combined flow complete before that proof. Local CLI must require
an explicit run/attempt/reviewed local SHA and verify repository, workflow, event,
actor/triggering actor and head SHA before reading bounded data or spending quota.
Preflight response-workflow availability before the model call; record a private
started receipt and validated answer so resending never needs another model call.

Broker must compare actual validated input/memory/frame with the reconstructed
fresh synthetic scenario before publishing IDs. Fresh memory is Engineering
project/current revision/empty tasks. No arbitrary world text or real player data.
Return Plan through normal gateway and Fabric validation. A manual workflow can
background the existing multiplayer script, upload its request while it waits,
verify response workflow/actor/SHA provenance, then deliver data to a file broker.
Keep ordinary CI inference disabled. Extend only bounded manual acceptance waits;
retain ordinary90s product timeout/default900s harness. Require request/result
digests, acceptedAt and usual actual construction/test/service/game-restart proof.
Finish transport implementation and independent auth/data review before merging.

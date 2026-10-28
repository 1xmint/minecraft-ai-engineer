# Now

2026-10-28: Active goal: **complete all milestones**, remaining phase 1 plus all
six phase 2 capabilities. Do not narrow it or claim the full product shipped.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Working on **phase/8-operation-frames**, based on PR16 merge **8eefe61**.
Exact write-target guard passed all five required gates **CI40709523753**, source
69f4ffd. Root inspected retained native markers and private chat: lessons, denied
member, inventory, player placement, component/undo recovery and companion restart
3168->4453. GPT-6-Luna independently reviewed all production mutation callsites.
Scoped Java main/client compile passed locally; tests ran only on GitHub.
PR15 teaching remains proven by CI40628498967 / mergea77476b.

## Generated design architecture chosen (#3/E2)

Outcomes for the coming connected-design slice:

- G1: request a circuit outside the catalog's four-tick delay limit, preview its
  proposed blocks, build it and measure actual rising/falling transitions. A
  disconnected but visually matching layout must fail functional verification.
- G2: approve a structural layout revision that moves/removes blocks while
  preserving player edits. Collision or revoked authority must pause safely;
  explicit undo restores only its recorded operation under fresh authority.
- G3: restart during construction/revision/undo and retain original intent and
  evidence identities. Recovery pauses; no old checkpoint or historical footprint
  becomes a lease, and unchanged catalog behavior remains compatible.

Chosen architecture: bounded generated geometry as untrusted data, behavior
accepted separately before geometry, complete code-derived boolean vectors and
bounded game-tick traces. Keep the five currently supported blocks initially;
expand semantics with real native proofs later. Server derives identities,
limits, resources and bounds. Curated geometry still uses exact catalog checks;
new generated provenance must not masquerade as curated or verified.

Alternatives weighed: no change fails general engineering; more catalog family
selectors postpone the same gap; a netlist compiler/router adds substantial
upfront complexity and does not cover sequential/mechanical designs. Simplify by
reusing the executor and trial machinery, not by weakening catalog validation.
First probe: two serial repeaters with total delay beyond four redstone ticks,
then a moved output/relay revision and deliberately disconnected failure.
This is a boundary probe, not completion of general design or phase1.

Critical prerequisite found in actual EngineeringSession.load: journal entries
are checked against current component positions. Historical moved/removed writes
need immutable original operation footprints before structural revisions can be
safe. ProjectManager.acquire/validate also derives current positions, so undo
must reserve/recheck historical targets and read closure under current grants.
Legacy position validation cannot invent original semantic identity after old
delay revisions. Baseline/dependencies and partial revision WAL need their own
approved transition scope. Historical scopes are descriptions, never authority.

GPT-6.1-Sol architecture recommendation and independent actual-code challenge
completed. Choose bounded generated geometry with separately accepted behavior;
start with immutable operation scopes and transition recovery before enabling
structural writes. No generated production code written yet. Tests stay on GitHub,
local compile/format only.

Teaching now supports bounded local exact ask phrases, explicit component facts,
intended timing vs stored functional results, and per-player teach/repeat/skip/
previous/pause controls. It does not yet provide free-form LLM dialogue or durable
objective/rationale/preferences/decision memory. Those E1 gaps remain alongside
E2, E3 deeper diagnosis/textured ghosts and all six phase2 features.

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
  read-margin rejection/recovery passed CI40586816662. Transactional undo and
  real restart proof passed final CI40619656132; PR12 merged c01a3e7.
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
real restart proof passed CI40619656132 before PR12 merged c01a3e7. Root
inspected B-only recovery, A's original historical scope and unchanged A blocks.

## Roadmap / next slices

1. #2 component/inventory foundation complete for its bounded catalog scope:
   PR12 c01a3e7, all five gates CI40619656132. #3/E2 prerequisite PR16 exact
   write targets merged8eefe61 after CI40709523753 passed on69f4ffd. Next persist
   immutable operation/transition frames and prove recovery before revisions.
   Local checks only compile/format; CI tests only.
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
   earlier server-placed fixtures do not prove actual client placement. The real
   client and one live signed-in catalog lamp flow are now proven by40624276545;
   separately probing the SDK would not suffice. Public CI stays secret-free.
   Repeat combined acceptance for broader generated designs and modifications;
   the lamp proof does not establish those capabilities.
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
separately. Combined catalog lamp/game proof subsequently passed40624276545;
no general shared-account eligibility proof is claimed.
CI graphics signed APT archive/Azure bounded fallback passed native checks; sources:
https://manpages.ubuntu.com/manpages/noble/man1/apt-transport-mirror.1.html and
https://github.com/actions/runner-images/blob/main/images/ubuntu/scripts/build/configure-apt-sources.sh.
Retained artifacts in the task work directory: component-proof-40584567109,
margin-red-40585762989, perception-proof-40586816662, inventory-proof-40595380532.
Worker inventory-companion1ee545f integrated ase1fe550 plus root formatting/fixes;
its clean worktree was removed after status/cherry confirmed integration. Old clean component worker checkouts were removed
only after git cherry confirmed integration; recoverable branches remain.

2026-10-28 E2 independent challenge completed: GPT-6.1-Sol supports bounded raw
geometry and independently accepted behavior. Confirmed immutable transition
frame must cover baseline/dependencies as well as journal; acquisition AND live
cross-project revalidation must consume that frame. Found missing individual
write-target membership in RegionReservations; first prerequisite task (Serves
G2/G3) adds that guard at every production mutation. Proofs first reject writes
to read-only dependencies, unreserved positions, foreign project scope and stale
grants. No layout revision or journalv4 support is claimed by this guard alone.
Later transition executor must explicitly preview removals and compare exact
fresh before states; recorded neighbor membership alone is not ownership proof.

Reservation proof-first source e86acb0 / CI40707827221 Fabric failed at missing
validateWrite, before implementation. Companion also caught STATE formatting;
format corrected locally. Production placement/repair, undo, and real lever
activation/restoration now call target-aware validation immediately before writes.
Live native OR trial checks allowed input and rejected read-area air target.
Scoped compile and final independent diff review next; runtime proof pending.

Exact-write guard c77fe98: scoped Java main/client compile passed. GPT-6-Luna
review accepted all production mutation callsites and existing authority checks.
CI40708184972 passed Fabric's20 required tests, companion, Windows and client;
dedicated run passed the new live OR lease checks, then failed an existing lesson
fixture race: Alice's ordinary ten-tick auto-advance changed her cursor during
Bob's command round trips. Retained Alice log shows Step2 after snapshot. Root
corrected the proof to issue Alice's real private pause and wait for its new chat
ack before the isolation comparison. No product timing or behavior changed.
Combined native/restart proof must pass after this fixture correction before merge.

## Current operation-frame phase plan (Serves G2/G3)

- F1: new assistant operations retain their original intended identity and allowed
  positions after later revisions and actual restart. Proof first: native existing
  build -> delay revision -> saved/reloaded original operation scope, no relabelled
  source revision or imported lease. Record source snapshot once, reference it from
  operations, and keep it immutable despite mutable Blueprint.source JSON access.
- F2: valid old flat/v2/v3 journals remain recoverable without invented history.
  Validate under the exact old rules first, then migrate explicitly as legacy
  position scope; the checkpoint's current blueprint is not necessarily the
  original intended revision. Reject missing/forged/duplicate/unknown-version
  scope or escaped positions before any execution. Proofs cover both valid legacy
  and corrupt documents, including old component delay revisions.
- F3: freeze the active execution/transition frame, including exact before/desired
  states, bounded write targets, read closure and original/destination identities.
  Acquisition and live cross-project permission checks must consume that same
  frame. Prove fresh grants/connection/epoch on resume/undo; saved scope grants no
  authority. Keep current intended revision after undo, explicitly untested.

Tasks: first write native/save-load and corruption proofs (F1/F2); observe their
CI failure; implement bounded deduplicated scope storage and legacy migration
(F1/F2); then capture active frames and use them consistently for leases, live
checks and interrupted recovery (F3). Independent auth/data diff review, scoped
compile and all five gates before merge. Preserve existing original functional
scopes, assignment bases and legacy recovery semantics. No new removal executor
until operation frame proofs pass. Structural revision will separately preview
all changes/removals and require exact fresh before states, safe attachment order,
actual inverse journal order and current-state ownership checks for neighbors.

This phase is planned, not implemented. Full generated two-repeater timed design,
moved-output revision, disconnected failure, live model acceptance and subsequent
broader functions still follow; all remaining product milestones stay active.

Operation-history proof-first c7747a0 / CI40711161879 failed at the missing
OperationScopes class. GPT-6-Luna worker implemented only the isolated strict
immutable scope validator (bdbb868, integrated d1ec173); root owns proofs and all
server integration. New capture deduplicates whole original intention snapshots,
checks canonical content digest/identity/leaf membership and bounded metadata,
returns deep copies and prunes orphan scopes. V4 journals require mappings;
flat/v2/v3 validate under old rules before explicit legacy-position migration.
Root added native old-version and corrupt-document loader proofs, original A
revision retention after B revision and actual restart, plus immutable lease
footprint assertions. Existing baseline/dependency states stay attached to the
captured original source; stale changed intention cannot resume construction.
Undo acquires original positions/read closure under fresh current authority.
Live cross-project permission validation now consumes the actual captured lease
footprint instead of reconstructing positions from current blueprint metadata.
No new structural mutation executor is enabled: a future old/new layout transition
still needs its explicit approved diff, before-state capture and attachment order.
Scoped compile, independent auth/history review and combined CI pending.

GPT-6.1-Sol independent review found P2 synchronous geometry regeneration on each
retained journal validation. Root memoized immutable per-snapshot component
position sets and basis hashes, changed runtime checks to set/map membership and
existing current assignment basis, removed full-JSON counting and redundant
budget serialization when pruning only removes data. Root also found and fixed
active-frame component mismatch when an empty interrupted undo retains an
operation without journal entries; native corrupt-document proof added. Reviewer
accepted remaining auth/recovery lifecycle and old position-only migration;
focused verification of the cache fix follows. No changed-state hardening was
added to legacy journals because that would invent original intended semantics.

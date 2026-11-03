# Now

2026-11-01 local date. Active goal: **complete all milestones**: all Phase1 and
all six Phase2 features in docs/product-brief.md. Overall goal incomplete.
Repo: https://github.com/1xmint/minecraft-ai-engineer.

Dedicated physical chat source053f358 / CI41576664814 completed SUCCESS.
Downloaded work/physical-chat-proof-41576664814; inspected all5 completion receipts
and actual screenshot showing the companion rendered on stone platform. Both
real clients exercised owner addressed-chat summon/dismiss, shared-editor denied
summon/dismiss plus authorized location, and owner disconnect removed actor.
Added exact-source screenshot/evidence to docs/physical-companion.md. This proves
N1/N2 dedicated idle flow, not production online authentication, server-restart
actor recovery, navigation, construction or survival gathering. Required full CI
for PR47 remains pending; no merge/release yet.

PR47 is draft, stacked on phase/28-imported-core. Source901a264 native lifecycle
CI41575900486 passed. Dedicated chat41575903677 failed because the fixture shared
Bob AFTER summon: authorization revision correctly removed the actor, so Bob's
location request reported no actor. Fixture now shares first, then summons; no
lifecycle guard changed. Downloaded evidence work/physical-chat-failure-41575903677
and inspected screenshot: actual client received actor but camera fell away from
the floating platform. Fixture camera now flies and clears chat before capture.
Scoped client compilation passed; corrected real chat/screenshot proof pending.
Claude PR46 integrated separately in work/memory-integration, never his branch:
reason punctuation and service-refresh fixture corrected, no auth weakening;
focused GPT-6-Luna review found owner/privacy/replay separation sound. Memory
feedback41576575423/source30ee71d is live. All broad milestones remain incomplete.

Dedicated physical chat proof now has two actual client roles and its own CI
workflow/runner. Alice's addressed chat summons a server actor; the actual client
must receive its UUID entity and captures a screenshot. Bob is a shared editor
and real chat summon/dismiss must deny while authorized location works. Alice
dismisses, re-summons, then disconnects; server must remove its actor. Separate
workflow pins/verifies Carpet jar and supplies it only through a CI init script;
ordinary build dependencies/full multiplayer harness unchanged. Scoped client
compile and shell syntax checks passed; native dedicated flow is pending CI.
Native lifecycle run41575306520 failed on an invalid fixture timing assumption:
vanilla item drops can be picked up before walking starts. Assertion now checks
zero dirt before mining; no supply occurs thereafter and final pickup still
requires exactly1 native dirt. Do not characterize legitimate early pickup as an
inventory injection failure. Re-run focused native lifecycle after this change.

Extended lifecycle sourcee79fb3b / CI41574979258 completed SUCCESS; inspected
actual owner/editor/action/material/XP/dismiss/selection/grant/disconnect marker
and both required native passes. These are production lifecycle methods driven
with native CI connections, not an actual dedicated chat/UI proof. Merged current
combined core through0a5a077, including chronological observer receipts and strict
trace-null serialization fix3d4f732. New combined source requires fresh CI; parent
run41575231876 is pending. Previously dispatched combined41575015476 remains live
on older source lacking the serialization correction; no pass is claimed.

Lifecycle sourcef4d59af / CI41574545857 completed SUCCESS; inspected actual
owner summon/editor denial/idle action+material denial/dismiss/grant cleanup
marker and both required native passes. New candidate blocks XP orb pickup too,
uses concurrent actor-registration lookup, and extends actual native lifecycle to
selection change and owner removal before checking core disconnected-action
denial. Scoped Java compilation passed. These extensions require fresh CI.
Parent connected CI41573027130 failed only because Bob's single four-minute wait
covered436-cell construction, interruption/revocation and final trial; artifact
shows final approved trial actually running. Parent now follows each existing
progress receipt in order without changing four-minute bound or functional checks.
Combined41573034326 remains live; do not cancel to include the newer observer fix.

Phase30 optional visible companion, work/physical-companion,
phase/30-physical-companion, based on combined core0df5e59. Outcomes:

- N1: owner says summon my companion and sees an empty survival player-like actor
  in a checked3x3 stone/air workspace; unsupported optional dependency explains
  refusal. Proof: actual native Carpet lifecycle plus chat routing/absence tests.
- N2: viewer/editor cannot summon another owner's actor; owner can dismiss;
  disconnect, selection/grant/revision change removes actor without restoring an
  action. Proof: native denial/lifecycle cases and eventual dedicated player flow.
- N3: actor receives no project grants; default-deny action gate prevents item,
  mining and entity interactions. Existing mod works without Carpet. Proof: retained
  phase29 native refusal suite and normal full CI without optional dependency.
  Implementation uses proven optional Carpet26.3 reflection (no account lookup),
  server-owned lifecycle and adopted event-time guards. No construction/gathering or
  general movement claimed until separate executor/navigation/restart proof.

Phase30 implementationf4d59af pushed with optional runtime lifecycle and default-
deny vanilla action hooks, addressed-chat summon/location/dismiss, checked3x3
stone/air spawn, random empty survival identity, owner/connection/selection/
grant/revision cleanup, and dropped-item pickup denial. Scoped Java compilation
passed including native lifecycle fixture. Exact Carpet manifest version is
26.3+v260915 (upstream build.gradle appends date), checked before reflection.
Focused read-only GPT-6-Luna review found no blocker for this idle scope. Cleanup
is next-END-tick; no action ticket exists for idle actors. Material pickup is
blocked, XP orb pickup and external physics are not claimed contained. Separate
physical-lifecycle-feedback CI is submitted, native N1/N2 and dedicated chat flow
still pending. Existing creative assistant and optional-dependency-absent behavior
need full CI before merge/release. No construction/gathering completion claim.

Inspected failed-restart source0df5e59 / CI41573034326 dedicated artifacts at
work/imported-restart-failure-41573034326: connected-functional/interruption/
revocation/player-edit receipts all passed, as did imported-build.done. Actual
flow assertions before those receipts required the whole8-case half-adder pass
and exact control restoration, then actual35-block imported geometry completion
with interruption/viewer denial/no functional claim. Stored imported source has
35 intended cells and verification untested. This proves those pre-restart flows
on that source, not the full run: saved-server boot then failed strict trace load
because firstMismatch:null was omitted. Fix3d4f732 and restart/undo/independent
SQLite verification still need exact-source full CI. Do not discard actual partial
evidence or inflate it to completed C3/I2/I3/overall milestones.

Inspected source29785a2 / CI41533141692 Fabric artifact at
work/imported-native-41533141692: all158 required native tests passed. This source
includes imported182-material runtime admission and exact snapshot parity,
coordinate rejection and canonical imported preview/reload tests. Cross-runtime
connected replay passed32 cases/1792 frames across4 rotations. Four full jobs are
green; dedicated multiplayer remains live. These native results do not establish
the imported chat/build/restart flow or the later export-overflow correction.

Physical-builder feasibility research (2026-11-02): Mineflayer's upstream README
lists support only through26.1; discussion4190 says26.3 support remains unreleased.
Do not choose protocol translation/proxy/login workarounds as the foundation.
Carpet upstream has an exact26.3 release (GitHub API verifiedv26.3 asset
fabric-carpet-26.3+v260915.jar): https://github.com/gnembon/fabric-carpet/releases/tag/v26.3.
Next physical milestone is an isolated compatibility probe, not a shipped NPC:
P1 actual fake-player lifecycle and legal item-use placement/mining with inventory
change on the pinned server; P2 unreachable targets, depleted items and cancellation
make no unauthorized changes; P3 owner/grant/revision/region remain core authority,
never fake-player identity. Require native CI evidence before dependency adoption.
Fresh-look challenge: Carpet supplies player actions, not a bounded navigation or
resource-acquisition planner; spawning a character alone completes neither NPC nor
survival. Native PathfinderMob APIs exist in the pinned jar but do not supply player
inventory/item-use semantics. Compare the probe against a native actor only after
actual action/recovery evidence; no new runtime dependency or credentials added.
Sources: https://github.com/PrismarineJS/mineflayer and
https://github.com/PrismarineJS/mineflayer/discussions/4190.

Current full checks: combined source29785a2 / CI41533141692 is live (Companion
and Windows passed); parentee8ff98 / CI41533180250 is live. Earlier parent run
41530574368 terminated with the same pre-start PAUSED assertion as imported run,
confirming the shared regression. Do not cancel/restart the current handles.
Follow-up export fix uses long inclusive spans before the64-block check, rejecting
extreme integer corners before overflow or world reads. Added all-three-axis
native regression; scoped Java compilation passed. This later fix is not in the
running source and needs fresh CI after it finishes.

Focused CI41532825264/sourcecb0d832 passed8 tests,0 failures, including omitted
transform import and missing-discard/name/unknown-transform refusals. Full imported
CI41530710501 terminated FAILURE in the connected flow before schematic gameplay:
after436-cell construction, final asynchronous conversational test completion was
checked against the preceding PAUSED state. Correctionee8ff98 first requires an
actually VERIFYING trial, then retains the full pass/restoration assertions. No
production guard changed. Added native connected-viewer denial for schematic
import/rotate/export/preview. Scoped client compilation passed for both changes;
fresh combined full CI is required. Parent41530574368 remains live; do not cancel.

Integrated Claude's later import compatibility fix2dd20c2 as4cfe78e: only the
empty transform field may be omitted; explicit null remains supported. Kept the
core's existing null-preserving transport and preview hooks. HTTP proof now uses
an omitted-transform successful import and rejects missing name/discard or an
unknown transform without storing a design. Local type/format checks passed;
new regression awaits focused CI. Full runs41530574368 and41530710501 remain
authoritatively live with four other jobs green; no restart or merge yet.

Expanded-material feedback source3b2ed50 / CI41531718943 passed8 tests,0 failures.
This includes pinned cobblestone admission and sand refusal plus HTTP library/
restart/permission cases; it is not native runtime or multiplayer proof. Added
docs/schematic-building.md explaining chat preview/approval, bounding-corner
placement, supported materials, untested behavior, conflicts, recovery and export.
Guide explicitly marks development/pending CI and absence from alpha.2.

Material evidence: sourceb4faabc / CI41530710501 Fabric passed all157 native tests.
Inspected artifact work/imported-materials-41530710501: native candidate set has182
vanilla single-state full cubes. Extracted catalog/imported-materials.json from
that observed report. New imported-only admission requires pinned membership and
native predicates; companion uses the same snapshot. Native test requires exact
registry/snapshot equality; generic/model admission remains narrow. Release zip and
paired jar must contain identical snapshot bytes. Gameplay fixture now uses16
cobblestone/16 oak-plank supports plus3 devices (35 total), including restart/undo.
Broadened runtime admission and gameplay are new changes pending fresh CI, not
proved by the earlier candidate enumeration. Slabs, stairs, inventories, gravity,
interactive/dynamic blocks and arbitrary redstone still remain outside this set.

Imported CI41530710501/sourceb4faabc Companion and Windows jobs now passed; native,
client and dedicated jobs remain live. Adapter now rejects fractional, numeric-
string and out-of-int-range coordinates before Blueprint.parse, rather than
truncating/coercing them into apparently valid cells. Added native regression for
all three; scoped Java compilation passed. Latest adapter change awaits fresh CI.

Focused feedback source48bbc12 / CI41530452349 passed7 tests,0 failures: all five
real HTTP schematic-library cases plus two imported-admission cases. The prior
409 failures came from fixture regeneration of blueprint ID at the same intended
revision during sync/restart; fixture now reuses the immutable source. Production
revision guards were not relaxed. This focused check does not establish gameplay.
Merged connected placement-loop correction6508367; its fresh full CI41530574368
is live. Do not substitute the7 focused passes for required full integration CI.

Verified current-run native evidence: source0a81172 / CI41528081343 Fabric passed
all156 required tests. Artifact inspected at work/imported-native-41528081343:
imported canonical preview/reload, request-null transport and partial-junction
regressions passed; native imported source matched the companion reader. Connected
replay passed32 cases/1792 frames in4 rotations. This does not prove the dedicated
player flow; its job is still live, and later restart/admission-test changes are
not in that source. No local behavior tests ran.

Next material expansion decision (I2): focused GPT-6-Luna read-only challenge
supports a separate pinned imported-family set, leaving generic/model admission
unchanged. Probe native vanilla exact Block.class IDs with no BE, signal/analog
output or fluid and full collision; retain existing device exceptions. Derive a
registry snapshot on CI and compare native/TS membership before admission. This
excludes gravity/absorption/interactive subclasses; it is not universal safety or
all-block support. Do not broaden runtime admission before those native proofs.

CI triage: schematic-library HTTP tests previously closed their server/SQLite
only after all assertions passed. TestContext cleanup now registers idempotent
server/connection/store closure before setup assertions, retaining explicit close
for restart cases. Failed assertions must report without a leaked listening
server keeping the test worker alive. Type/format checks passed; no local tests.

Companion job125560890205 ended cancelled at its10-minute timeout, while the
other integration jobs remain live. Added focused Schematic feedback workflow
for the two relevant HTTP/admission test files with30-second test bounds, using
pinned actions and its own concurrency/check name. Push work/schematic-feedback
to run it before the workflow lands on main. This check is not a required full-CI
job, never substitutes for native/dedicated proof, and does not cancel either
active full run. Purpose: reveal actual failures after deterministic cleanup.

Added read-only native material probe: enumerate pinned vanilla exact Block.class
single-state full cubes without BE/fluid/signal/analog behavior. Requires ordinary
cobblestone/planks, rejects representative dynamic/interactive/partial/invisible
blocks, exports sorted IDs as ENGINEER_IMPORTED_MATERIAL_PROBE. It grants no
runtime admission; native source proof and pinned native/TS snapshot still needed.

2026-11-02 parallel core slice: work/imported-core, phase/28-imported-core,
based on pending PR45 source915139e. Claude's PR44 remains his feature lane;
its last dedicated run failed at import request-schema mismatch and Companion
was cancelled. No merge of44 yet. Parent starts imported core boundary in this
separate worktree while45 runs: ImportedBlueprint validates exact geometry-only
schema/provenance, canonical identity and current world, rejects authority/pass/
behavior metadata, and reparses through unchanged registry/block/extent limits.
Hashes are descriptive provenance, not permission or behavior evidence. Added
native rejection proof for owner/pass/grant/behavior/foreign-world/inventory/
extent/fractional-position/component injection. Not wired to acceptance/build yet;
proposal, permissions, placement ordering, isolation, undo/restart and real
in-game proof remain. Do not claim schematic construction complete.

Imported core Outcomes:

- I1: authorized users can preview exact imported geometry in their selected
  workspace without execution or functional claims. Native source-injection,
  permission denial, containment and saved-session reload are the proof.
- I2: approved construction uses existing ownership/journal/reservation/isolation
  paths with backing blocks first; edits, revocation and unloaded dependencies
  pause. Prove real dedicated build/interruption/undo/restart, including failures.
- I3: Claude's real library/chat reaches that core path and SQLite retains the
  intended/observed distinction. Prove actual import->preview->approved build->
  interrupt->undo/export with denied players. No unsupported functional pass.

I1/I2 implementation now adds proposeImported(candidate,anchor,rotation), exact
current world/project checks, workspace containment and backing-block-first order.
ComponentTree recognizes strict imported source; execution reserves a loaded clear
two-block isolation margin. Existing generic block limits remain unchanged.
Imported engineering contract has no ports or cases; FunctionalTrial refuses to
start it and rejects imported evidence/input-control recovery claims. Added native
preview/unauthorized replacement/saved-session reload tests. Full I2/I3 gameplay,
companion schema admission and Claude feature hook remain incomplete.

Integrated Claude PR44 snapshotd07ff3a into this isolated branch (not main/44),
preserving connected/imported/schematic test registrations and both dedicated
flows. Added explicit preview design N as EDIT-only, keeping show/inspect VIEW-only.
Preview uses the captured actor and advances the async frame revision only after
its own accepted preview; no build starts. Type/client compilation passed.
This integration still needs end-to-end proof and clearer refusal handling.

Async schematic frames now bind both marked corners as well as player connection,
project revision and grants; changing the workspace invalidates pending results.
Preview refusal reports its native reason without placing blocks. Native grammar
proof distinguishes EDIT preview design from VIEW show design. Dedicated stale
request and denied-preview player flows remain required; not claimed proven yet.

Added pending actual importedBuildFlow after the existing real WorldEdit library/
discard/export proof. A separate synthetic35-block safe .schem exercises chat
import/preview, connected viewer preview denial, reviewed build, genuine partial
interruption/resume, exact final geometry, functional-test refusal and owned undo.
Synthetic fixture is not third-party codec evidence. SQLite must retain the actual
35-placement completion as untested with empty evidence/input journals. Existing
WorldEdit six-sign fixture remains for independent interchange/discard coverage.
Native/client compile and script syntax checks are local only; this new combined
gameplay flow is still pending CI. Full imported saved-server restart remains.

Imported gameplay now leaves the built structure for the existing third server
boot, then requires exact source/35-record/actual geometry recovery, untested state
and no lease/callback/input journal. Ordinary testing remains refused. A real
diorite edit must block undo without losing any record or overwriting that edit;
after explicit conflict resolution, owned undo removes the structure. SQLite
requires untested35-placement checkpoints across at least two server epochs and
restart/undo completion markers. No extra boot or local behavioral tests added.
These latest restart/conflict proofs are pending exact-source CI.

Added companion imported-admission regressions with hand-calculated three-block
bounds/resources: owner/pass/port/test injection, altered counts/bounds, inventory,
invalid lever direction, duplicate cells, rotated world-height overflow, foreign
project/world and stale server revision all reject. Type/format checks passed;
behavioral tests await the next exact-source CI. These complement actual native
source replay and dedicated gameplay rather than replace them.

PR45 CI41524156179 is now terminal FAILURE: all four other jobs passed, but the
real connected build paused at2004,101,0 after223 records. Native final geometry
and149 tests passed; partial owned-wire reconciliation at a junction is the live
failure. Investigate actual native intermediate arms; do not blanket-ignore wire
arms or weaken manual-dot/player edit guards. Fix45 before claiming C3 complete.

PR44 import request mismatch traced to Companion.encodeBody: default Gson omits
explicit transform:null while the schematic endpoint requires that exact field.
Core transport now preserves JSON nulls for /v2/schematic/import, as already done
for interpretation. Native regression checks the actual encoded request retains
no-transform and discard:false. Claude's branch/files remain untouched. Fix is
pending fresh CI and integration with44; this does not establish import gameplay.

CI41525271966 found two concrete failures: STATE formatting and native imported
preview acceptance expected a catalog template in ComponentTree.nodes. Fixed
imported root descriptor to schematic-geometry (no ports or behavior) and retained
the failing native preview/reload proof. Full run is still live; save fixes after
scoped checks and dispatch a fresh run only when it terminates.

Protocol integration: native imported previews now derive the normal blueprint
envelope (bounds/materials, fixed geometry-only explanation, empty ports/tests and
prerequisites). Strict validation compares all derived fields back to geometry;
minimal Claude adapter candidates remain accepted only as input to normalization.
Companion imported-intention.ts independently checks exact normalized source,
current world/project, bounded rotated cells, supported property values, resources
and empty behavior metadata. Gateway admits it only after its existing revision
check. Native preview is exported for exact current-run TypeScript replay plus
forged-pass rejection. Scoped TypeScript/format/syntax checks passed. Actual
Claude feature integration and dedicated imported construction remain pending.

## Parallel ownership — 2026-11-02

Josh requested sustained milestone ownership for Claude, rather than another
small CLI task. This supersedes the old transforms-only delegation below.
Claude's track: finish schematic tools and their in-game feature integration;
richer durable project knowledge/reference retrieval; local offline voice;
engineering candidate comparison/optimization with honestly measured evidence.
Codex's track: connected/larger functional engineering, diagnosis/repair and
teaching; authoritative construction core; physical NPC; genuine survival;
multiple builders; shared integrations and final original ten-step acceptance.
These are roughly balanced workstreams, not a claim of equal estimated effort.

Claude owns new schematic/memory/voice/optimization feature modules and their
tests/docs. Existing shared protocol/provider/storage/registration entry points
are integration seams: changes stay in isolated PRs, with exact touched files
and contracts called out; Codex integrates against current engineering work.
Do not rewrite execution, permission, journal, reservation or verification cores.
Read actual existing memory/knowledge infrastructure before extending it; reuse
SQLite, provider authentication and normal conversational interaction. All new
features must reach actual user flows, not stop at standalone libraries. Native
hooks owned by Codex must be explicitly tracked as incomplete until integrated.
No separately billed service/API key, credential sharing, model-call loops or
local behavioral tests. Voice must prove a viable offline path before adding a
large dependency; inability to meet constraints blocks voice only, not the track.
Optimization must separate estimated geometry cost from native-measured timing/
reliability, and never recommend untested candidates as verified machines.
Each milestone needs success/failure evidence on its exact CI source and a
focused PR. Claude can continue later independent milestones while CI runs;
Codex owns final merging and STATE reconciliation, with proposed updates in PRs.

Current engineering worktree: task `work/connected-circuits`, branch
`phase/27-connected-circuits`, based on merged main **9a1d6e7 / PR37**.
Documentation PR38 (`docs/multiplayer-preview-download`, sourceeb91617) updates
the public download and archives the real memory screenshot; all five checks in
CI41373420233 passed and it merged as e25a0b3. Original C1
source8490f8d passed all five checks in CI41369209886, including141 native tests
and the unchanged full dedicated/restart/SQLite suite.
These C1 fixture observations do not establish the entire phase.

Release source57b1d65 passed all five checks in CI41367933778, all137 native
and209 companion tests (208 passed,0 failed,1 existing platform skip), plus full
dedicated/shared/restart/SQLite verification. PR37 merged2026-11-02T01:05:36Z as9a1d6e7; PR36 closed as incorporated.
**Main CI41370616555 passed all five checks** on that exact source.
Published [v0.1.0-alpha.2](https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.2)
is the multiplayer development preview. Its tag was read back at9a1d6e7, and
GitHub's uploaded asset digests match the locally verified same-run files:

- companion73cf14bcd6dadad14ddba0acf6288410228697a5c5fdb5e63bb2edac164b6d33
- jar6c14f8781c7499f0b01799e7dd66791f9dd3383a077fcb7cc770e19ad47d4c1b

Main assets: `work/main-release-41370616555`. The old alpha.1 remains archived.

Josh: **skip heavy reviews; build and ship**. Public pushes, releases and exact
all-green merges are authorized. Tests run on GitHub, never on this slow PC.
Local scoped compile/type/format/syntax only; Gradle --max-workers=1. Keep
memory only in AGENTS.md and STATE.md; CLAUDE.md is @AGENTS.md. Older history is Git.

## Phase27 Outcomes (E2/E3)

- C1: external controls and internally driven signal pins have actual native
  connectivity, direction and isolation. Prove consecutive producer/route/
  consumer/receiver observations in four orientations, unwired-control changes,
  broken-link and reversed-consumer negatives. Never toggle an internal pin.
- C2: a curated half-adder exposes shared A/B controls and SUM/CARRY, typed ports,
  exact routes/graph identity and strict canonical admission. Prove all four rows
  forward/reverse at consecutive stable ticks; reject altered roles, edges,
  geometry and fabricated leaf-derived whole passes.
- C3: normal chat preview/lesson/build/test operates on the whole connected design
  with union write/read reservations, player conflicts, revocation, interruption,
  input restoration, undo and restart. Prove native shared flows and SQLite
  scopes. Leaf passes never certify a connected graph.

Decision: bounded canonical connected designs retain trusted regeneration and
external-control restoration. Old ComponentTree dependencies order construction,
not electrical signals; their lever-input contracts remain unchanged. Arbitrary
model netlists are premature before topology/isolation validation. New graph
identity must bind canonical geometry, typed endpoints and exact routes; changes
stale whole-circuit evidence. GPT-6-Luna scout and independent challenge checked
actual ComponentTree, FunctionalTrial, assignments and reservations. Both called
out internal vs external roles, full pin traces, graph scope and union footprints.

C1 physical proof: ConnectedGameTests uses real external/noise levers and two
native repeaters. No consumer lever or forced runtime wire POWER. All141 required
native tests passed in CI41369209886 on8490f8d. Raw reports inspected at
`work/connected-port-proof-41369209886`: four rotations,360 frames each/1440 total.
For every rotation the intact route measured producer0/15, consumer0/14 and
receiver0/15 with both lamp states. A gap kept receiver0/lampoff; reversed consumer
kept its input0/14 but receiver0/lampoff. Unwired-control phases stayed isolated
on every tick. Both original external controls ended off. Full C1 CI is now green.
These are fixture observations, not a project pass or complete human acceptance.

C2 is in progress: catalog/half-adder.json and HalfAdderDesign.java
prototype a four-NAND plus NOT half-adder. Shared signals have explicit buffered
routes, external controls, internal pins and two receivers. No project parser,
provider, construction or verification admission exists yet. Complete native
whole-circuit truth proof before integrating strict source/graph/evidence codecs.
C3 has not started. Do not merge this phase as complete after the fixture alone.

## Shipped source and evidence

- Phase25 memory: owner saves goal/constraint/preference/decision through addressed
  chat; authorized members read. Polite ambiguity clarifies locally. Source0ef5c6d
  passed all5 CI41364946648 and was incorporated in final57b1d65/all5 CI41367933778.
  Actual chat writes/reads, editor denial, revoked reads, note recovery and a fresh
  post-restart constraint passed four knowledge markers and strict literal-note/
  count/no-world-authority assertions. No model call. Evidence:
  `work/conversation-memory-final-41364946648`, `work/release-final-41367933778`.
  Actual memory screenshot0003_engineer-project-memory.png inspected in final run.
  GPT-6-Luna found/fixed polite remember falling through to model interpretation.
- Phase26 packaging: tracked-source allowlist produces compiled companion zip,
  deployable jar pair, BUILD manifests and SHA256SUMS. Linux/Windows extraction,
  production dependency install, launcher preparation and authenticated health
  passed; anonymous health rejects. Missing/symlink inputs, existing-output
  preservation and stray-data exclusion passed. No Codex execution/inference.
  Gradle launcher is now correctly tracked100755, preserving clean-source guard.
  Candidate `work/release-candidate-41367933778` has77 zip entries and verified
  pair hashes; checkoutcb3f976d27deb29dc02c661949f396c6c5dd1109 is a PR merge, not
  the publishable main checkout. Installation guide: docs/install.md. GPT-6-Luna
  architecture challenge and focused implementation review found no remaining gap.
- Schematic analysis PR35 merged870fc91; source5067bdc/all5 CI41364625681,209
  companion tests and full native/dedicated/SQLite proof. Exact state/block counts,
  offset-aware bounded diff, explicit air vs outside volume, no world authority.
  Maximal-dimension zero volumes skip empty loops. Evidence inspected:
  `work/schematic-analysis-final-41364625681`. Full in-game integration unfinished.
- Schematic codec PR34 merged885748e; source55ca74c/all5 CI41360384747,21 schematic
  proofs, independent third-party fixtures/dev-only prismarine-nbt2.8.0 oracle.
  Sponge v3 bounded NBT/gzip; no entities, explicit reported optional BE/biome
  discard. Neutral geometry has no grants/pass. CLI protects input aliases and
  existing output, bounds actual reads and rejects sparse arrays. docs/schematics.md.
- Comparators PR33 merged3978f88; source7e27fd7/all5 CI41362325852,136 native and
  all12 analog markers. Canonical compare/subtract139-cell workbenches,45 physical
  selectors,66-case actual numeric suites with exact restoration, shared guards,
  repair, native dust-arm/player-edit-safe undo, fresh restart/subtract evidence.
  Eight rotated intentions,144 windows and132 native suite cases replayed across
  Java/TS. No latency/all4096-combination claim; analog composition still closed.
  Evidence `work/analog-final-41362325852`; docs/images/comparator-tested.png.
- Conversational setup PR32/merged9bde62/source1561555/all5 CI41276307352: actual
  private creation, authorized choices/paging, feet corners and local previews.
  Conversation PR31/mergeb7e3e26/sourcebd39067/all5 CI41273958762: approvals, real
  101-second expiry, reconnect and restart invalidation; strict10/17 model starts.
- Observer PR30/merge3ae2232/source4d4a1de/all5 CI41268319917: native edge timing,
  restoration, shared/restart/composed-leaf evidence. Mechanical repair PR28/
  merge5b29a9c/sourcee881d73/all5 CI41151376521. Natural actions PR29/mergecaa7de6/
  source0f78de1/all5 CI41223355084. Detailed historical development remains in Git.

## Boundaries and runtime

Dedicated Fabric server owns connection identity, project grants, reservations,
loaded-world execution and tests. One private localhost companion beside it on
Josh's PC owns SQLite and Codex. Optional authorized overlays; text clients work.
Keep intention, observation and verification separate. Untrusted model/world/
schematic text never becomes commands, ownership or a pass. Recheck grants,
revision, actor/connection, dimension and epoch during work. Player conflicts
pause. Restart restores descriptions, never leases, callbacks, consent or a live
pass. Creative execution never claims survival gathering or inventory consumption.

Only the configured account-owning operator can request model reasoning. Use
ChatGPT sign-in through the official SDK; no paid API fallback, keys, hosting or
credential sharing. General shared-subscription eligibility remains unresolved;
operator approval is not provider permission. Offline CI players do not prove
production Mojang login. Scripted native flows do not prove full human acceptance.

Pins: MC26.3, Java25, FabricLoader0.20.1/API0.165.0+26.3, Loom1.17.21, Gradle9.6,
Node24.14, TS7.0.2, SDK/CLI0.164.0. Leave unrelated Dependabot PR6 alone.

All prior signed-in structural/Boolean/discussion/natural receipts are consumed.
Phase20 acceptance8214fbd passed CI41221809497/response41222705526: one gpt-6-luna
SDK0.164.0 call,11519 input/0 cached/73 output, diagnose/root, no observed claim.
Request digest c5ccb5a673603fd217fce2a188bfdd6654683755610409c3cfb63eb7ec3416d9;
result658e247247fdc76c1f301919ff70ba2c8e67307a05c0d369674b96fe77bb2bc9.
Private exclusive receipts remain local in AppData/Roaming/minecraft-ai-engineer/
acceptance. Never read/print credentials, replay archival inference as fresh, or
launch unnecessary model probes. Current work needs no SDK call.

## Full remaining roadmap

- E2: actual connected circuits and larger multicomponent/piston systems, register
  banks, memory, arithmetic, displays and redstone computers; update ordering,
  interference and adapting existing structures. Half-adder is only the first slice.
- E1: richer natural references/project retrieval, durable rationale, preferences
  and conversation summaries beyond literal owner notes/search.
- E3: richer connection/timing/interference/inconclusive diagnosis and repair;
  clearer mechanical pose overlays beyond canonical designs.
- E4: signed-in preview -> actual human functional section -> AI remainder ->
  interrupt/change/resume -> real test -> service/game restart -> later explanation
  and visual proof. Entire original10-step acceptance remains required.
- Multiplayer public download/setup (main release proof pending), production
  account-login evidence and subscription eligibility evidence.
- Phase2 all six: voice; physical NPC; genuine survival gathering/placement;
  full in-game schematic import/export; measured size/resources/latency/reliability
  optimization; coordinated multiple builders. File interchange/analysis alone
  does not complete the schematic feature.

Claude delivered transforms in PR42 at58202dc, with all five CI41379536160
checks green. Parent review found maximal zero-volume traversal; fixe314a4b
skips traversal and adds all-axis/all-operation regressions. Updated CI41472675876
is live; merge only its exact green head. Next offered Claude lane is a standalone
file-only transform CLI in the same reserved paths, with explicit formats,
bounded reads, alias/output preservation and independent NBT output checks.
Codex retains in-game integration and memory ownership.

Claude's independent lane remains schematic transforms only:
companion/src/schematic-transforms/**, tests/schematic-transforms*.test.ts,
docs/schematic-transforms.md. No mod/core/auth/storage/provider/CI/memory changes.
Do not overlap Claude's next CLI work in those paths.

Next: finish the README/download documentation checks, watch the new connected-codec run
to terminal without restarts, and continue strict connected source/evidence work.
Continue C2 native half-adder proof and strict graph/source/evidence integration,
then actual C3 flows. Keep the full goal active; no single slice completes it.

C2 prototype now has parent-owned native proof fixtures for all four rotations,
eight forward/reverse vectors, all twenty typed pin observations at every tick,
and eight stable frames at elapsed48..55. An independent arithmetic oracle checks
SUM/CARRY and internal NAND relations. A missing A-to-N2 wire must make SUM wrong
while CARRY remains apparently correct, showing one receiver cannot certify the
whole circuit. Initial wire arms are computed using the existing read-only pinned
native connection accessor over intended geometry; no signal is simulated and no
runtime POWER is forced. A native post-settle static geometry check covers arms.
No parser/provider/construction admission yet. Shared recipe is included in the
mod resources. Final scoped Java compilation and format/diff checks passed; the
actual native results are recorded below. No behavioral tests ran locally.

When C2 becomes a companion-admitted family, explicitly add its third shared
catalog to the production bundle and paired-jar catalog parity check. The current
prototype is only a mod fixture resource; no companion module imports it yet.

C2 native source6a5c07a/Fabric in CI41371755386 passed all146 required tests.
Reports inspected at `work/half-adder-native-41371755386`:436 cells,20 pins,
448 consecutive frames per rotation and64 stable frames covering all four input
vectors forward/reverse. Every rotation produced SUM/CARRY00->00,01->10,
10->10,11->01. The missing A-to-N2 wire showed expectedSUMtrue/observedSUMfalse
while CARRY stayedfalse. Static native wire-arm checks passed. Full CI41371755386 is now all-five green
on source6a5c07a, including the unchanged dedicated/shared/restart/SQLite suite. This proves the physical prototype, not C2 strict admission or C3 gameplay.
GPT-6-Luna supplied standalone connected-trace.ts at7aaad74 and fixed partial
verdict semantics, rejected-sample immutability and early array bounds in8ba8209.
Parent type checking passes with the shared dependency junction. Eight parent-owned
CI tests now use four actual archived native vectors plus clock/gap/rebinding/
metadata/false-pass/sparse/oversize/immutability rejection proofs. The normalized
fixture retains actual game clocks and native boolean/integer values, and carries
no fresh project authority. Behavioral tests await GitHub; Java mirror, canonical
source admission, current-run cross-runtime replay and C3 remain outstanding.
No SDK calls or local tests ran. Public alpha.2 does not include this prototype.

Connected codec source dc8e5bf passed all five checks in CI41374383606.
The Java mirror now captures and replays each native case with independently
supplied graph/input identity, rejects forged fields, missing frames, input/graph
rebinding, malformed pin types and unsafe clocks, and preserves facts after a
rejected sample. Scoped Java compilation passed. A fresh CI verifier recomputes
the semantic graph/geometry digest and compares all 32 Java case snapshots to
the same run's 1792 raw native frames through the TypeScript reader. That new
cross-runtime proof is pending CI. It does not grant project authority or complete
strict source admission/C3. No behavioral tests ran locally.

ConnectedIntention.java now regenerates a bounded canonical half-adder source,
binding exact geometry, typed graph/routes, digest, identity, anchor/orientation,
bounds and untested provenance. Long arithmetic checks every rotated cell before
coordinate addition. Native proofs now reject metadata/pass/geometry/role/edge/
digest/bounds tampering, world/dimension/project rebinding and overflowing anchors,
and verify defensive snapshots. These new proofs await CI after the existing
5387f0d run finishes. No production Blueprint.parse admission is enabled yet;
companion parity and construction guards remain required.

Companion connected-intention.ts now regenerates identity-bound source using a
shared expanded intended definition extracted from native recipe generation.
Decision: avoid a second implementation of Minecraft wire-shape rules. The native
recipe still independently regenerates every block/route/port; fresh exported
sources in four orientations must exactly match the companion's shared definition
in CI. Both half-adder catalogs are now explicitly packaged and paired-jar bytes
checked. Added tampering, rebinding, rotated bounds and defensive-copy tests.
Scoped TypeScript/format/syntax checks passed; behavioral proofs await CI. New
source is not yet admitted to production planning/building. Existing runs remain
live and must finish before dispatching another same-branch run.

Added matching five-stage teaching text and exact item resource lists in Java/TS.
Native registry item mapping is checked against the companion during fresh source
replay, including dust->redstone and wall torch->redstone_torch. Intended counts:
227 stone,171 redstone,25 repeaters,9 torches,2 levers,2 lamps. Regression asserts
these and rejects altered quantities/stage dependencies. Scoped compile/type checks
passed. Construction review confirms strict static wire-arm comparisons during
partial placement and undo need native transition proof; do not weaken generic
Blueprint.matches or admit the new family before resolving player-edit safety.

Added two native half-adder construction proofs, pending CI:436 single writes in
the five teaching stages must all have physical support, overwrite only air and
settle to exact canonical static geometry; removing A's physical branch must
change wire arms in the read-only native projection while a manual dot remains
distinct and untouched. These fixtures do not yet exercise EngineeringSession
journals, permissions, interruption or undo; C3 remains incomplete.

ConnectedIntention now converts its already-regenerated contract to native
Blueprint plans without broadening generic parser admission. Four-rotation native
proofs check all436 positions/states/stage/component identities and five stages.
Scoped Java compilation passed. Latest source still requires a fresh complete CI
after41472491344 terminates; its Fabric/Companion/Windows/client jobs are green,
dedicated multiplayer remains live. PR42's41472675876 has the same four green
jobs and a live dedicated job. No merge or replacement run while these are live.

ConnectedWireUndo now checks canonical operation/source/geometry before applying
the pinned read-only native wire projection. EngineeringSession calls it only for
connected wires after durable undo intent; initial preflight and player-neighbor
guards remain unchanged. The native branch/dot fixture now exercises this actual
helper: initial exact shape, allowed owned branch transition, rejected manual dot,
and read-only preservation. No generic parser admission or new live-pass authority.
Full session undo/restart/conflict flow still needs C3 proof.

Fresh native replay now also requires eight disjoint ordered64-tick case starts
and an actual post-suite restoration observation at start+512, retaining both
original and observed external control states. This fixture currently starts both
off; initially-on input recovery still needs the actual C3 trial/restart proof.
FunctionalTrial inspection confirms contract selection and evidence recovery must
be extended together before generic source admission; otherwise a saved connected
project would fail reload. Keep the family out of the normal planning menu until
that complete path is implemented and proven.

ConnectedEvidence Java/TS now replay the bounded eight-case whole suite against
independently supplied graph identity and forward/reverse stimuli. Completion
requires all eight complete traces; nonfinal cases must already pass, clocks must
advance beyond the prior window, and same-stimulus cases cannot reuse old windows.
Native and current-run TS replay include missing/reordered/reused-case negatives.
This is observation validation, not input restoration or project authority. Scoped
TypeScript checks passed; new behavior awaits the next exact-source CI run.

Live FunctionalTrial now has a connected path: contract exposes only A/B levers
and both result lamps; every native tick captures all20 typed pins, all8 ordered
cases must pass replay, and exact original lever states are independently checked
after restoration before recording a pass. Saved connected traces load through
ConnectedEvidence rather than generic leaf cases, with recovery ports from the
same canonical contract. Native fixture checks external-only contract selection.
This code still requires actual EngineeringSession success/interruption/restart/
conflict proof. Generic parser/provider admission remains disabled pending that
integration. Do not call this complete or expose it through normal planning yet.

Strict native Blueprint.parse/ComponentTree admission now delegates connected
sources exclusively to canonical regeneration; the generic block allowlist is
unchanged. Provider/menu admission remains disabled. Added actual saved-session
reload proof with two pending controls, A originally on and B off: original vs
last stimulus survives, no delegation/callback/trial resumes, and an internal pin
in a recovery record rejects. Scoped Java compilation passed. This pending native
proof does not establish actual authorized restoration or full user acceptance.

Connected build reconciliation now uses native shape projection only for cells
written by the assistant in the captured current operation. Adopted player wires
stay strict; changed external levers conflict exactly. Canonical operation is
regenerated once per reconciliation, not once per wire. Existing dependency,
permission and ownership checks remain. Actual session conflict/undo proof pending.
Prior source5387f0d CI41472491344 is now completed SUCCESS; fresh source needs its
own CI before claiming any of the subsequent integration proven.

Latest connected source43fc56c has fresh CI41476541185 live (do not replace it).
PR42 sourcee314a4b passed all five CI41472675876 checks. Ready/merge encountered
the protected-main up-to-date requirement; requested GitHub update-branch and
automatic squash merge after its new checks pass. No bypass/admin merge used.
PR43 remains separate and its dedicated CI was still live at last observation.

CI41476541185 Fabric evidence inspected at work/connected-source-41476541185:
all149 required native tests passed, including staged construction, branch/dot,
canonical source, ordered suites and pending-input session reload. Fresh replay
passed32 cases/1792 frames across4 rotations. This is native fixture/recovery
proof, not the complete live shared C3 session flow. Companion job also passed;
full run remains live until its dedicated job terminates.

Subsequent unproven source adds the normal Blueprint shape to connected intentions:
per-block stage prerequisites, external-only public ports and test description.
Java/TS remain exact regenerated counterparts; graph digest still binds raw intended
geometry and full electrical graph. Companion gateway now admits only this exact
canonical source after current project/revision checks, with stale revision and
inventory tampering regressions. Scoped Java/type/format checks passed. Planner
menu/provider offer remains disabled until actual session flows are proven.

Added pending dedicated connectedExecutionFlow using actual Alice connection and
EngineeringSession.accept/build/resume/test/undo, with explicit canonical fixture
selection (not natural planning proof). It interrupts after20 owned placements,
resumes436-cell construction, starts with A on/B off, requires whole-suite pass
and exact restoration, then resets the player lever and requires owned undo to
remove every cell. Client coordination requires success markers. SQLite verifier
requires persisted connected pass evidence, current project/revision/basis and no
pending controls, then independently replays all8 cases against canonical digest.
Scoped client compilation and script syntax checks passed; full behavior awaits
the next exact-source CI after41476541185 finishes. Shared Bob/revocation, full
saved-server restart, human teaching and normal conversation planning remain.

Dedicated connected flow now additionally interrupts a genuinely running trial
after two cases, requires exact A-on/B-off restoration/no pass/no lease, then
retests. After a real pass, undo must reject the player's originally-on lever
before touching any of436 records; only after the player resets it may undo
continue. Both negative/success markers are required by client coordination.
These are pending exact-source CI proofs, not reported as executed gameplay.

Added actual Bob viewer/editor commands to the dedicated connected flow: viewer
build/test/undo deny without a lease or changes; editor starts a real two-case
trial; Alice revokes him and recovery remains pending without authority; Bob's
resume denies; Alice explicitly restores A-on/B-off without resuming a trial or
recording a pass. Required connected-revocation marker gates client completion.
This remains pending the next exact-source CI, not established multiplayer proof.

Connected test now keeps the actual tested structure until the existing third
saved-server boot. Recovery must retain436 owned cells and valid historical
observations but only a stale pass, no callback/lease/pending trial. A new real
trial must restore A-on/B-off and pass before post-restart undo removes the owned
structure. SQLite now requires complete valid observations in at least two server
epochs and both restart/undo completion markers. No extra boot added; no local
behavioral tests run. Scoped client compilation/script syntax checks passed.
PR43 original source7f9c537 CI41474306862 is now completed SUCCESS; integrate
after its parent PR42's protected-branch update finishes.

Connected preview/lesson integration now recognizes “show me a half-adder” via
the local conversational setup path, with intended SUM/CARRY explanation and
explicit20-pin/eight-case/48-tick/eight-stable-sample contract. Preview has no
execution authority or pass. Dedicated flow now creates/marks workspace/previews
through actual chat and uses existing conversational approval for build and final
test, instead of direct source injection. Offline provider fixtures add only those
two scoped choices; SQLite asserts exactly build/test replies for that project.
Scoped client compile/script syntax passed. These latest player flows are pending
CI, not claimed proven. SDK/model provider advertisement is still unchanged.

Follow-through found/fixed the old catalog-only reviseProposal path: connected
previews now validate canonical graph/source and accept only before construction;
built connected projects must reconcile existing work before replacement. Static
diagnosis selects the connected engineering contract instead of the leaf catalog.
No shape match or diagnosis creates a functional pass. These hooks are covered
by the pending full conversational dedicated flow, not yet executed on CI.

Connected failure reports now retain first wrong pin, expected state, observed
value and actual clock; missed windows are explicitly inconclusive. Native replay
negative injects one wrong stable internal output into otherwise actual samples
with correct root receivers and requires failed verdict plus pin/clock diagnosis.
Added docs/connected-circuits.md with flow, electrical graph, actual material list,
truth table, test-window limits and explicit pending gameplay/not-alpha.2 status.

Integration update: prior connected source43fc56c CI41476541185 completed all5
SUCCESS. PR42 merged asb9d7be4 after its updated protected-branch checks; PR43
source9ac26ecd passed all5 and merged as5e2425e. Reviewed CLI's bounded reads,
explicit formats/discards and open-handle alias/no-truncate-before-validation
guards. Both are file-tool foundations, not full in-game schematics. Connected
branch merged current main through5e2425e; kept its ongoing STATE decisions when
resolving the documentation-only memory conflict. Opening a draft phase27 PR for
fresh combined-source CI, including the new actual conversational/shared/restart
flows. Do not reuse prior149-native proof as proof of the newer full C3 flow.

PR45 CI41524156179 terminated FAILURE in dedicated construction: a partial A-net
junction at2004,101,0 conflicted after223 owned records. Four other jobs passed.
Candidate correction: ConnectedWireUndo/matchesValidated accepts the exact
canonical recorded after-state as well as the pinned native projection. Previously
it accepted only the latter, excluding legitimate just-written arms before later
neighbor updates. Still requires canonical ownership/block/state; manual dots are
not broadly ignored. New partial-junction native proof plus unchanged real failing
construction flow remain the regression gates. Scoped Java compilation pending
final check; fresh source requires CI, not claimed fixed gameplay yet.

CI41527288929 terminated FAILURE later in the same real build: placement loop
revisited an owned wire at2014,101,14 after317 records. Four other jobs passed.
The reconcile fix advanced beyond the original223-record junction, but placement
still used only final static equality. BUILD now skips only canonically owned
wire states accepted by the same recorded/native reconciliation, never unowned
or arbitrary states. Completion still requires all final static geometry to match;
otherwise waits for native settling. Keep the unchanged real build regression.

CI41533180250/sourceee8ff98 terminated FAILURE waiting connected-functional.done.
Inspected work/connected-failure-41533180250: actual436-cell build completed,
shared/revoked/interrupted trial recovery ran, but conversation final approval
returned "Stop and reconcile active or interrupted world work before you test a
component". componentTest used composite-only context switching for a single root,
unlike ordinary test(). Single-component root now validates the requested id and
delegates to existing whole-project test path, preserving construction history,
input capture and all existing trial/permission/area guards. Composite switching
is unchanged. Scoped Java compile passed; unchanged real conversational flow is
the regression proof and needs fresh exact-source CI. No whole-circuit pass yet.

CI41573027130/source94a9991 terminated FAILURE from Bob's observer timeout.
Inspected work/connected-failure-41573027130:436-cell build completed, revocation
and interruption recovered, final natural approval started a real truth-table
trial. Bob's single240-second receipt wait spanned that entire slow sequence and
expired while the final trial ran. Observer now waits the already-required
revocation, interruption, functional and player-edit receipts in chronological
stages, each retaining the unchanged240-second bound and failure-file detection.
No functional assertions, game timings, permissions or recovery guards changed.
Scoped client compilation passed. Fresh full CI needed; no pass/merge claim.

Combined source0df5e59 / CI41573034326 progressed past connected execution but
failed on real saved-server restart: ConnectedTrace.read rejected missing strict
fields. Explicit firstMismatch:null was omitted by shared Gson disk/HTTP encoder.
LocalFiles.GSON now serializeNulls; it serializes already-built JsonObjects, so
this preserves explicit protocol fields without inventing optional Java members.
Added native regression roundtripping an actual strict trace through LocalFiles
disk write/read and Companion checkpoint HTTP encoder, then strict trace reader.
This fixes a known persistence/transport defect, not a relaxed reader schema.
Scoped Java compile passed; fresh native/full restart CI required.

Source3d4f732 / CI41575231876 ended when the23-minute client wrapper expired:
"alice exited before its native test completed", no assertion failure receipt.
Inspected work/connected-failure-41575231876: connected-restarted.done and
connected-undo.done both passed, along with earlier functional/revocation/edit
receipts. Actual saved-world re-test/control restoration and owned undo therefore
passed on that source; independent final SQLite/full job still did not finish.
Runner was still executing later generated-truth-table checks under native lag.
Expanded suite now gets29-minute clients,30-minute harness,35-minute job including
setup. No expected behavior, game tick windows, per-stage wait or case count was
changed. Shell syntax check passes; fresh full CI required, not a milestone pass.

# Now

2026-10-27: Active goal: **complete all milestones**: remaining phase 1 and all
six phase 2 capabilities. Do not narrow it or claim the full product shipped.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Building #2 on **phase/4-component-collaboration**, draft PR #12:
https://github.com/1xmint/minecraft-ai-engineer/pull/12
Current runtime **e3bc4b1 passed all five checks in CI 40586816662**. Root
inspected retained native logs/markers and SQLite proof: live far-margin rejection,
explicit WAL recovery, idle B/C staleness with A preserved, actual block/view
samples and unloaded refusal passed. SQLite retained both typed samples and exact
original/fresh component test scopes. All tests run on GitHub; locally only scoped Java/TypeScript compilation,
formatting and bash -n. Source is pushed; no implementation is left uncommitted.

Earlier all-five-green source: **600b347 / CI 40584567109**. Root inspected actual
native logs, markers, SQLite proof and the constructed-component screenshot:
component info/materials/ports, b4 revision, scoped repair/undo/testing, unaffected
a's original vectors, actual service restart (PID3123 ->4352), legacy identity
migration and third-boot recovery all passed. Independent review later found a
live read-margin gap in this green source; its proof/fix is described below.

**Do not merge/release #2 yet.** Finish current native/data acceptance, C3 inventory
and broader final phase review. Core phase 1 #3 and all phase 2 features remain.
GPT-6-Luna review could not run because of the usage limit. GPT-6.1-Sol completed
focused C2 and new perception-boundary reviews; the former found the margin gap,
the follow-up confirmed its full-closure fix and no further confirmed data/auth
issue. This does not replace the final review after inventory work.

## Outcomes for #2

- C1: player builds B while assistant completes A. Show hierarchy, actual ports,
  prerequisites, material totals and assignments. Native C1 proof passed in
  40539445528/09d0d06 and 40536897959/b158881, and the fuller 40584567109 run.
- C2: handoff/delegate-back retains journals and generations across game/service
  restart; stale connection/grant/assignment/basis cannot authorize writes. Scoped
  revise/diagnose/repair/test and original test identities are implemented/proven
  above; additional live-margin rejection/fix passed native CI 40586816662.
- C3: targeted actual player/view/block/support/connection observations and read-only
  accessible inventory, with loaded rejection/privacy. Invalidate only affected
  tasks/dependants. Scoped companion memory proofs pass; typed block/view source
  passed native/HTTP/SQL acceptance. Inventory is not implemented.

## Implementation and decisions

2026-10-26: dedicated Fabric server first, integrated-compatible logical-server
core; Java25/Fabric/Minecraft26.3, Node24 TypeScript and SQLite. One private
operator-owned companion alongside Josh's server/PC. No hosting purchase, public
AI endpoint, credential sharing or separately billed API. One configured
account-owning operator starts inference. General shared subscription eligibility
remains unproven; do not infer it from the old solo SDK probe. World is authority,
companion records are descriptions. AGENTS.md holds the enduring constraints.
Sources: https://docs.fabricmc.net/develop/networking,
https://wiki.fabricmc.net/tutorial:side, https://learn.chatgpt.com/docs/auth.

2026-10-27: choose a bounded component tree in one project instead of separate
projects, a whole-blueprint worker or a general swarm. It preserves shared
contracts and grants while permitting independent work. GPT-6-Luna challenged
actual interfaces: retain journals through handoff; revoke leases and reconcile;
reserve full support/port/read closure; propagate invalidation in both services;
never infer electrical correctness from port names; keep inventory read-only.

- ComponentTree.java and companion/components.ts generate matching canonical
  catalog composites. Limits: 64 nodes, 16 mechanism leaves, depth8, extent64,
  64 stages, 4096 blocks. Groups organize; leaves are signal-lamp/not-gate/or-gate.
  Namespaced stages/ports/tests use colon. Labels do not create physical wires.
- Commands: component add/group/depend/list/info/claim/build/diagnose/repair/revise/
  test. Info shows materials, absolute ports, assignment and original test revision.
  Tree edits precede construction. One active AI per project; humans work disjoint
  leaves. Assignments record actor/executor/generation/status, never a second ACL.
- Server-owned private grants, authenticated actors/connections, world/server epoch,
  project/auth revision, assignment generation/basis and region reservations fence
  mutation. Distant read grants are rechecked before every mutation. Grant revocation,
  disconnect, human conflicts and restart stop work; persisted metadata grants no lease.
- Build/repair capture selected-leaf geometry and prerequisite reads. Delegate-back
  preserves journal/player progress and rejects changed recorded AI blocks. Journalv3
  attributes contiguous operation groups to components; flat/v2 migrate to root.
  Undo selects the latest group's component before lease/save and cannot spill into
  an older group after interrupted completion.
- Diagnosis is read-only VIEW, explicit leaf, actor/connection/epoch/revision/ACL/TTL/
  exact-world snapshot/generation/basis bound. It never selects or pauses another task.
  Repair revalidates, explicitly delegates one leaf under a fresh BUILD isolation lease,
  and rolls selection/assignment back on lease failure. Only trusted bounded corrections.
- Delay revision changes one node's intention, leaves world/journals intact, and keeps
  unaffected task results. No-op keeps identity/vectors. Test explicitly selects an
  idle leaf; failed initialization retains captured pending inputs under that leaf.

### Evidence identity and migration

Use **basisVersion2** with memoized transitive prerequisite intent. Preserve exact
old hashes for dependency-free/root tasks. Verify a v1 record against its explicit
legacy formula before migration; changed bases increment/stale even old untested
labels. Unknown/corrupt versions fail closed. GPT-6-Luna challenged this against
silently rehashing evidence or globally invalidating unrelated work.

FunctionalTrial captures project, exact blueprint id/revision, component, basis/
version before vectors. Persist/archive that scope plus original intention;
lastPassedScope retains the original revision for unaffected work. Empty new
buffers are never relabelled vectors. Live tests use actual vanilla lever pulls,
per-input write-ahead restoration and catalog truth tables. Pending recovery
never restores under expired authority or invalid geometry. Legacy identity
adoption clears scopes/results, rebuilds unassigned descriptions and preserves
exact source/backup/journal; new-format pending inputs recover in their original
project. Proof-first 40549054217 failed missing component APIs/v1 migration;
40551903302 caught inherited adoption scopes, fixed before 40584567109 passed.

### Live read-footprint review/fix

GPT-6.1-Sol found that componentTest preflighted full closure, but later trial
transitions checked only immediate prerequisites/target margin. Proof-first
**40585762989** showed C reaching a pass after B's far margin at66,100,0 became
occupied; waiting for pause/pending recovery timed out. e3bc4b1 reuses the full
canonical transitive area validator before all live/final transitions and for
post-pass invalidation. Native proof requires ON input retained with paused WAL,
no pass, unrelated A preserved, explicit clear/resume/restoration and fresh test.
An idle margin edit must stale B/C only. Reviewer confirmed the reuse fixes the
finding; actual corrected native proof passed in 40586816662.

### Companion invalidation and typed perception

Stable composite tasks use projectUUID:component. Derive membership/intent/read
closure from immutable canonical blueprints, not reported ownership. Preserve
unchanged historical rows/revisions, pause changed/removed/unscoped/live tasks.
Auth changes and registration pause all rows. Compare coordinate history across
20 retained observations; invalidate intersecting full two-block read closures,
including newly observed geometry conflicts. No schema rewrite/hash relabelling:
existing rows already identify immutable revisions; local comparison fingerprints
are not Fabric bases or credentials. Proof-first 40582347987 failed broad invalidation
and lost partial-coordinate history; implementation proofs passed in40584567109.

WorldPerception captures seven actual states (target +six support/connection
neighbors), actual self position/eye/look and hit face, plus sampled geometry
differences. Explicit block/view commands require connected actor VIEW, own
workspace target, loaded/world bounds and foreign-project read rights. Preflight
the bounded six-block ray +two-block shape margin before vanilla pick. No sign
text, NBT, names or inventory contents; no partial snapshot on refusal. DTOs reject
unknown fields, invalid vectors/uncovered targets and mismatched actors before
claiming report order. Plain observations remain compatible. Pinned javap verified
Entity/BlockHitResult APIs. Proof-first companion40585406958/40585762989 failed
preservation/rejection/actor binding; implementation Companion40586816662 passed.
Native real-ray/unloaded/refusal and SQLite metadata retention passed in40586816662.
Focused GPT-6.1-Sol review found no further confirmed issue at this boundary.

## Next slices

1. Current C2/scoped-memory/block-view proofs are green; do not repeat without new
   source/failures. Full #2 is incomplete until inventory and final review land.
2. C3 inventory: explicit authenticated read-only request bound to connection, epoch,
   project/intent/grants/TTL and the next naturally opened vanilla storage menu.
   Recheck same menu/stillValid/current project/loaded backing blocks/grants. Start
   with requester-private safe item IDs/counts; never names/NBT or automatic sharing.
   Final policy/DTO, native privacy/side-effect rejection proofs and implementation
   remain. Do not force menus/chunks or unpack loot tables to inspect.
3. Final #2 review and all five checks, then merge. Keep physical executor replaceable.
4. #3: deeper debugging, richer teaching/textured visualization, combined signed-in
   model-to-game acceptance. Public CI stays credential-free; actual operator
   acceptance is separate proof. Read docs/product-brief.md for the full brief.
5. Phase2 #4: voice conversation, physical NPC companion, survival gathering,
   schematic import/export, design optimization and multiple builders. All unstarted
   capabilities remain in the active goal; do not substitute smaller features.

Pinned inventory javap research: Player.inventoryMenu/containerMenu/getInventory;
AbstractContainerMenu.slots/stillValid; Slot.container/getContainerSlot/getItem;
ItemStack.getItem/getCount; BuiltInRegistries.ITEM.getKey. Deduplicate backing
identity +container slot, not menu index. RandomizableContainerBlockEntity.getItem
unpacks loot: require getLootTable()==null first. CompoundContainer exposes contains,
not inner getters; resolve exactly two loaded vanilla chest halves from known
interaction target and verify both loot tables, or reject ambiguity. Exclude
player/ender/crafting/result/unknown custom storage from shared data. BlockEntity
provides position/level/isRemoved. Research is not an implemented inventory feature.

## Proven foundation/release

Foundation #8 merged PR#10 atae90741; mechanism/repair #1 merged PR#11 at28301e5.
Dedicated/integrated execution, private grants/shared authorized previews/personal
lessons, conflicts/revocation/restart/clone/adoption recovery and three real catalog
oracles are in main. CI40419651640 proved foundation; CI40531124499 proved real OR/
NOT, delay1/4 repairs and third-boot grouped undo. Offline identities/overlay opt-out
are not production Mojang auth or a separate vanilla binary. Keep online-mode=true.

Published v0.1.0-alpha.1 at168ac190 is still the old solo reference, not current source.
PR#5/CI40367902860 proved its actual solo handoff/save/reopen/conflict/undo/test.
Official SDK/CLI0.164.0 signed-in gpt-6-luna probe passed separately; never claim it
as combined model/game or shared-account eligibility proof. Release:
https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.1

CI-only graphics setup uses bounded signed APT archive/Azure fallbacks after two
old download timeouts; updated setup proved integrated/native gameplay. GPT-6-Luna
challenged it. Sources: https://manpages.ubuntu.com/manpages/noble/man1/apt-transport-mirror.1.html
and https://github.com/actions/runner-images/blob/main/images/ubuntu/scripts/build/configure-apt-sources.sh.
Retained proof folders under work/: component-proof-40584567109, margin-red-40585762989, perception-proof-40586816662.
Integrated clean worker checkouts component-bases/corrections were removed after
status/cherry confirmed all work landed; their branches remain recoverable.

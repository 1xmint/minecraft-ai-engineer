# Now

2026-10-27: Active goal: **complete all milestones**: remaining phase 1 and all
six phase 2 capabilities. Keep the multiplayer-first target and original scope.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Foundation #8 merged through PR #10 at ae90741. Mechanism library/targeted repair
#1 merged through PR #11 at 28301e5; #1 is closed. Main's source includes dedicated
and integrated execution, private grants, independent lessons, safe conflict/
restart recovery, three catalog mechanisms and snapshot-bound repair. The old
published v0.1.0-alpha.1 remains a solo reference, not the current source build.
Neither full phase 1 nor phase 2 is complete.

Building #2 on **phase/4-component-collaboration**, draft PR #12. HEAD runtime
09d0d06 passed all five protected checks in CI 40539445528. Earlier C1 run
40536897959 passed on b158881. Root inspected native logs, completion markers,
SQLite proof and actual preview/constructed screenshots (an initial artifact
503 resolved on normal retry). Actual native clients proved nested composition,
independent human/AI work, handoff, prerequisite conflicts, distant cross-project
revocation/restoration during testing, separate a/b/c truth-table passes, and
selection/handoff/testing of the composed project after the third server boot.
The 16-entry operation journal survived; restored metadata granted no lease or
connection. Later player removal of a recorded block blocked delegate-back.
All tests ran on GitHub; locally only scoped compilation and formatting ran.

No merge/release for #2 yet. Proof-first CI 40543178771 reached the actual
companion-restart.request barrier while B was handed to the player. With no
handler, Alice waited and Bob eventually timed out waiting for finish; retained
logs/request confirm this expected missing-harness failure. scripts/ci/multiplayer.sh
now stops/waits for the actual companion child, checks its exit, starts a distinct
PID on the same private SQLite/config directory, waits for a new readiness line,
and atomically acknowledges the request. Local bash -n passes; actual service
restart remains unproven until the next CI run passes. Never replace it with a
marker-only fake. Next:
Then complete C2 service restart and component-scoped
diagnosis/repair/delay revisions with propagated dependency bases; then finish C3
perception and scoped companion invalidation. Broader stale-assignment/revision
proofs, resources/ownership UX and final independent review remain. Do not treat
standalone repair as proof that composed-project repair works.

## Current implementation and limits

- One catalog JSON is bundled in the Fabric jar and compiled Node service.
  Signal lamp, inverter and two-input OR have bounded delay/rotation choices,
  real ports/resources/prerequisites and actual Minecraft truth vectors. Java
  checks geometry against the catalog; model provenance alone grants nothing.
- ComponentTree.java and companion/components.ts regenerate composites from
  trusted leaf recipes. Tree schema1: root group, organizational groups and
  mechanism leaves with relative pivot/rotation, delay and dependency DAG.
  Limits: 64 nodes, 16 leaves, depth8, 64 relative extent, 64 stages, 4096 blocks.
  Component stage/port/test namespaces use colon (slash was rejected by the
  service's safe identifier format). Both composers use matching DFS dependency
  order and canonical metadata. Ports are real leaf interfaces; metadata does
  not create or prove physical inter-module wires.
- Commands: component add/group/depend/list/claim/build. One AI task per project;
  humans can work in independent leaves. ComponentAssignments keeps granting
  actor, executor, generation, intended basis and status separate from authority.
  Current basis covers own geometry and declared dependency IDs; propagating
  changed prerequisite intent into dependent bases is still needed before
  component-local revision support. Assignments are coordination, not a second ACL.
- Builds capture selected-leaf baselines plus relevant support/prerequisite read
  closure. Fresh delegate-back validates old assistant changes, preserves the
  journal and accepts matching player progress. Human conflicts pause work.
  Isolation leases recheck the same distant read footprint used at acquire.
  Live connection/epoch/project/auth/assignment fences are checked before writes.
- Journal version3 attributes contiguous operation groups to components. Old flat
  journals become one legacy/root operation; version2 root journals still load.
  Undo reserves the selected group's component and persists its target before
  writing; an already-empty recovered target cannot spill into older work.
- FunctionalTrial uses the selected leaf's catalog oracle, vanilla lever
  activation and per-input write-ahead restoration. Private archives retain exact
  intended project, component basis and vectors. Task-level results remain separate;
  only the live trial buffer is single-active-component. Component geometry and
  prerequisite changes stale affected results, retaining unrelated passes.
- Diagnosis proposals are ephemeral, actor/connection/epoch/revision/ACL/snapshot/
  TTL bound, now also assignment-generation/basis bound. Handoff clears proposals.
  Root/standalone diagnosis and same-kind allowlisted repair work; composed
  diagnosis/repair/revision still need implementation and actual acceptance.
- Companion tasks use stable projectUUID:component IDs for composites. Scoped
  checkpoint results exist; observation/revision invalidation is still too broad
  in GatewayMemory and must become component-aware. All private memory stays
  outside repository/world saves. No persisted metadata restores execution authority.

## Decision and Outcomes for #2

Decision, 2026-10-27: keep one server-owned project with a bounded component tree
and separate persisted assignments. A general worker swarm adds recovery/authority
complexity before phase 2; separate projects lose shared contracts/dependencies;
whole-blueprint work prevents independent collaboration. The bounded tree reuses
server grants, journals and leases, at the cost of explicit task bases and scoped
invalidation. GPT-6-Luna challenged this against the actual interfaces.

Challenge safeguards: preserve journals through handoff, revoke old leases and
reconcile before issuing new authority. Record granting actor separately from
executor. Reserve real supports/ports/read assumptions, not nominal boxes. Change
both Fabric and companion invalidation. Never infer physical wiring correctness
from linked names. Keep inventories read-only and explicitly authorized.

- C1: player builds B while assistant completes A; show hierarchy, ports,
  prerequisites, resources and assignment. Proof: native disjoint edit does not
  pause A; changed prerequisite port pauses C and preserves the edit. First path
  and actual a/b/c tests proven in CI above. More resource/ownership UX remains.
- C2: take over/delegate back without losing progress. Proof: journals and
  generations survive game and service restart; reject old connections/grants/
  assignments/bases before mutations. Initial handoff/game-restart path is proven.
  Service restart, component-local revisions and further stale-basis proofs remain.
- C3: targeted actual player/view/block/support/connection/inventory observations,
  missing/extra/changed-state reports and unloaded rejection. Invalidate only
  affected work/dependants. Prove inventory access rejection/private visibility;
  never mutate inventories or expose secret NBT. Not implemented yet.

Independent GPT-6-Luna authority review found a real gap: test acquire included
far prerequisites, but per-write cross-project grant validation initially did
not. Root fixed it and added actual revoke-while-input-ON, retained recovery and
fresh-grant restoration; CI passed. Follow-up review found no further confirmed
C1/C2 authority bypass. It noted cached-proposal clearing, rather than the new
defensive Proposal.validate branch, is the public handoff rejection path. Final
phase review is still required after remaining C2/C3 code.

## Next proof/build steps

1. C2: restart the actual private companion during native project work; reconcile
   durable task state without importing authority. Add component-targeted bounded
   intent revision, diagnosis and repair. Propagate changed dependency intent into
   dependent task bases; preserve unaffected components and old operation groups.
2. C3: define typed targeted perception and inventory requests/results, write
   rejection/privacy/side-effect proofs first, then implement the read-only API.
   Make companion observations/checkpoints invalidate affected component tasks,
   with schema migration and old root compatibility.
3. C1-C3: drive actual dedicated/integrated/service restart flows, inspect retained
   screenshots/data, review new authority/data handling independently and merge
   only when all five checks pass. Keep physical execution replaceable.

Pinned inventory API research (GPT-6-Luna, 2026-10-27) used javap on
mod/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-7e9a32a5b8/26.3/minecraft-common-7e9a32a5b8-26.3.jar.
Player exposes inventoryMenu, containerMenu and getInventory(); AbstractContainerMenu
has slots/stillValid; Slot exposes container/getContainerSlot/getItem. Deduplicate
by backing container identity and container slot. ItemStack item/count plus
BuiltInRegistries.ITEM.getKey supplies safe IDs/counts; do not read names/NBT.
RandomizableContainerBlockEntity.getItem invokes unpackLootTable(null), so check
public getLootTable()==null before any item getter. CompoundContainer has contains
but no backing getters; resolve exactly two already-loaded vanilla chest halves
from a known interaction target, verify contains and both loot getters, or reject
ambiguity. BlockEntity has position/level/isRemoved; do not force-load chunks or
force open/create a menu. Proposed request queues the next naturally opened
storage menu, binds actor/connection/epoch/project/intent/grants/TTL, and rechecks
same menu plus stillValid immediately before reading. Exclude player inventory,
ender chest, crafting/result and unknown/custom backing slots from shared data.
Exact view-target and final access/retention DTOs still need implementation/review.

## Proven foundation and prior decisions

M1-M4: dedicated-first/integrated-compatible execution; authenticated server
identity/project grants; private shared previews and independent lessons;
reservations/conflicts/revocation/disconnect/restart/adoption recovery; one bounded
operator-owned private companion with SQLite. PR #10/main CI 40419651640 proved
those technical controls with actual native players and HTTP/SQLite. Offline CI
identities and overlay opt-out do not prove production Mojang auth or a separate
vanilla-client binary. Keep production online-mode=true.

Architecture decision, 2026-10-26: Fabric + Java25, Node24 TypeScript + SQLite,
one private companion alongside the dedicated server on Josh's PC. Integrated
single-player uses the same logical-server core. No hosting purchase, port
forwarding, public AI endpoint, credential sharing or paid API fallback. World
state is authoritative; intended/observed/verified data stay separate. Server
owns permissions and mutations; companion owns durable records, not authority.
Stable world/server IDs, backed-up explicit legacy adoption, private default
projects and permission-filtered client payloads are proven. Physical vanilla
blocks remain visible normally; project ACLs do not hide world geometry.

One configured account-owning operator initiates model work. Shared multi-user
subscription eligibility is not established; a personal SDK probe does not prove
it. Do not enable shared access without documented eligibility or add paid APIs.
Primary sources checked 2026-10-26: https://docs.fabricmc.net/develop/networking,
https://wiki.fabricmc.net/tutorial:side, https://learn.chatgpt.com/docs/auth.
Independent earlier GPT-6 challenge added epoch/revision fences, read reservations,
per-player cursors, clone policy, privacy limits and disconnect defaults.

#1's immutable shared catalog beat ad-hoc model geometry and duplicated templates.
Snapshot repair plus grouped undo beat blanket rebuild (which erases player work)
and flat undo (which can erase earlier operations). CI 40531124499 on ecd420a
proved actual OR/NOT truth tables, three inspections, repaired delays1/4, legacy
intent migration, unloaded-construction rejection and third-boot undo recovery.
Independent review caught fail-open unloaded detection and legacy alias rejection;
both were fixed and re-reviewed before PR #11 merged.

## Remaining roadmap and release

1. Foundation #8 and mechanism/repair #1: proven/merged.
2. Remaining phase 1: #2 current component collaboration/perception, then #3 deeper
   debugging, richer teaching/textured visualization and combined signed-in
   model-to-game acceptance. Public CI stays credential-free; live operator
   acceptance is separate evidence. Full product brief is docs/product-brief.md.
3. Phase 2 #4: voice conversation, physical NPC companion, survival gathering,
   schematic import/export, advanced design optimization and multiple builders.
   All remain in the active goal; no phase 2 capability is claimed complete.

Published v0.1.0-alpha.1 at 168ac190daddd066957ecaebd50a807e7926167c shipped via
PR #5; PR #7 records screenshots/limits. Four release gates passed in CI
40367902860 (attempt2 recovered a graphics download timeout). Actual solo flow
proved teaching/delegation/save-reopen/conflict/undo/tick testing. Official
SDK/CLI0.164.0 ChatGPT probe with gpt-6-luna passed; not combined model/game proof.
Release: https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.1
Current work: https://github.com/1xmint/minecraft-ai-engineer/pull/12

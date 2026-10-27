# Now

2026-10-27: Active goal: **complete all milestones**, including all remaining
phase 1 and all six phase 2 capabilities. Multiplayer-first remains the target.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Foundation #8 merged through PR #10 at ae90741. Mechanism library and targeted
repair #1 merged through PR #11 at 28301e5, issue closed. All five protected
checks passed in CI 40531124499 on ecd420a. Root inspected the retained native
logs, completion markers, SQLite proof and rendered screenshot. Actual OR/NOT
truth tables, three world inspections, repaired delays1/4 and third-boot grouped
undo recovery are proven. The separate native legacy-lamp revision and unloaded
construction checks passed too. Independent GPT-6-Luna review found two gaps
(unloaded construction classified as empty, legacy delay aliases rejected);
both were fixed and re-reviewed with no further blocking finding. All tests ran
on GitHub; only scoped compilation/formatting ran locally. Working branch is
now phase/4-component-collaboration from merged main.

The canonical mechanism catalog is shared by Java resources and TypeScript.
Model geometry is never authority. Functional testing uses vanilla input
activation, per-input write-ahead restoration, actual vectors and private
immutable revision-scoped evidence archives. Read-only diagnoses are bound to
actor/connection/epoch/project/authorization/snapshot/TTL. Explicit repair
corrects only AIR or allowlisted same-kind structural differences, preserves
live dynamics, and pauses on player edits. Undo persists one selected operation
before writing; it cannot spill into earlier groups after restart. SDK revisions
of existing construction use the same bounded delay validator. Arbitrary circuit
rewrites and component relayout remain unsupported until the next slice.

Neither full phase 1 nor phase 2 is complete. The published v0.1.0-alpha.1 is
still the older solo reference; there is no new mechanism release yet. Remaining
phase 1: #2 component collaboration/perception, then #3 richer debugging/teaching
and combined signed-in model/game acceptance. Phase 2 remains all six features
in #4. Shared multi-user subscription eligibility remains unproven; planning is
still limited to the configured account-owning operator, never a public AI API.

## Next build - component collaboration (#2)

Decision - 2026-10-27: keep one server-owned project with a bounded component
tree and separate persisted assignments. Containers group work; physical leaves
are regenerated from trusted catalog geometry. Existing designs migrate as one
root leaf. Keep one active AI task per project until phase 2 multiple builders.
Scope its before-state, write lease and read dependencies to the assigned work.
A human can work in an independent component concurrently. A changed shared
support or port must still pause every dependent task; a nominal component box
cannot hide a real redstone dependency.

Options: doing nothing preserves whole-blueprint conflicts and fails concurrent
work. Separate projects lose shared hierarchy, interfaces and dependencies.
Replacing the scheduler with a general worker swarm adds unnecessary recovery
and authority complexity before the physical/multiple-builder phases. The bounded
tree reuses current server grants, operation journals and leases, at the cost of
explicit task bases and scoped invalidation. GPT-6-Luna independently challenged
this choice against the actual Blueprint, EngineeringSession, ProjectManager,
RegionReservations and GatewayMemory interfaces and confirmed this boundary.

Important corrections from that challenge: component assignment is not a second
ACL. Record the authenticated granting actor separately from the assistant
executor. Handoff revokes the old lease, preserves the journal and reconciles
before fresh authority. Persist component/assignment generation and intended
node basis, never a live connection/lease. Server and companion currently both
invalidate whole-project tasks, so fix both. Define real supports/ports/read
assumptions in the catalog; do not claim inter-module wires work merely because
metadata links output names. Regenerate and test actual composed geometry.
Accessible inventory inspection is read-only and explicitly scoped; inspect the
pinned Minecraft APIs before deciding the access/retention implementation.

Outcomes:

- C1 - Work on a shared multi-component project: the player builds B while the
  assistant completes A. Proof: real dedicated native flow with disjoint edits
  that do not pause A, plus a shared support/port edit that does pause dependent
  work. Show hierarchy, interfaces, prerequisites, resources and assignments.
- C2 - Take over and delegate back without losing progress. Proof: preserve
  journal/groups and observed progress through handoffs, game and companion
  restart. Reject old connections, revoked grants, stale assignment generations
  and changed component bases before any mutation. Migrate an existing root
  design without changing its intended geometry or journal.
- C3 - Inspect the actual surroundings and available materials. Proof: targeted
  player/view/block/support/connection/inventory observations, missing/extra and
  changed-state reports, unloaded chunks rejected honestly, and invalidation
  limited to affected components/dependants. Prove private project visibility
  and inventory access rejection paths; never mutate or expose secret NBT.

Tasks: write actual component collaboration/handoff failure cases first (C1/C2);
add typed tree/composition validation and assignment ledger (C1/C2); scope live
execution/reservations/recovery and companion task memory (C1/C2); research and
implement targeted perception and accessible inventory observation (C3); drive
native flows plus service/game restarts and independently review new authority
and data handling before merging (C1-C3). Keep physical execution replaceable.
The riskiest assumption is that relevant read dependencies can be scoped without
missing a real redstone interaction. Probe independent and connected/shared
support cases in the actual pinned game before claiming that separation works.

Proof-first command contract for C1/C2: `component add <id> <parent> <mechanism>
<x y z>` composes trusted catalog geometry inside the selected workspace (root
is the initial container); `component list` shows the tree/assignments;
`component claim <id>` hands that component to the authenticated player and
revokes its active AI lease; `component build <id>` explicitly delegates/reconciles
only that component. Optional bounded rotation/delay can follow later without
changing these meanings. Root's native proof creates two actual lamps eight
blocks apart: the player adds b's support during a's execution, then takes b
over mid-build and delegates it back. Expected journal count excludes both
player supports and retains all assistant work. This is only the first C1/C2
proof; shared-dependency, stale generation, restart and C3 cases still need
actual proofs before the phase can ship. Runtime commands/composition are not
implemented yet; the new test should initially fail at the missing command.

Implementation in progress on draft PR #12. Proof-first CI 40532671860 failed
at the missing component command, while the other four gates passed. Java
ComponentTree and companion components.ts now regenerate bounded catalog leaf
geometry, ports, resources and prerequisites; root integrated workers and fixed
cross-language root-node, ordering, metadata and TypeScript type mismatches.
The first runtime path adds component add/list/claim/build, per-component
assignment generations/bases, scoped baselines/dependencies/leases and attributed
journal entries. Explicit delegate-back captures fresh current assumptions while
retaining and checking old assistant changes; it never clears the journal.
Undo selects the recorded component before acquiring its lease. Checkpoints
persist descriptive assignments, never live authority. Functional trials use the
selected leaf's trusted oracle and archive its component basis with the project
intention. Root owns all proofs, including actual leaf tests and forged
composition rejection. Early independent GPT-6-Luna authority review found one
concrete gap: test leases acquired distant prerequisite read footprints, but
cross-project grant validation rechecked only the selected leaf's nearby region.
Root fixed validation to use that same read footprint and added a native
revoke-during-test case where only the distant prerequisite intersects Bob's
workspace. Nested group/depend commands and a third leaf now prove prerequisite
blocking, a changed prerequisite input pausing dependent construction, preservation
of that human edit, and unrelated component evidence remaining valid. Targeted
bounded geometry checks stale only affected component/prerequisite results.
CI 40535547169 exposed the slash namespace conflicting with the service's safe
identifier format; root changed canonical stage/port/test namespaces to colon
in both composers, preserving the identifier safety rule. Local compilation/formatting only; runtime acceptance,
independent authority review, scoped companion invalidation, additional handoff/
restart/dependency failures, richer UI and C3 perception are still pending.

## Proven foundation outcomes

- M1 — Play alone on a local dedicated Fabric server, then invite a friend
  without moving project authority to a client. Proof: boot the real dedicated
  server, perform the reference handoff with two clients, restart and reconcile;
  retain the same core flow in integrated single-player. No client classes may
  be required by the server.
- M2 — Project members share blueprints and construction progress, with personal
  tutorial steps. Proof: owner/editor can collaborate, a third player cannot
  read project metadata or receive overlays, one player's Next does not move
  another's step, and a client without the overlay retains commands/text teaching.
- M3 — Nobody silently overwrites another's work. Proof: overlapping AI jobs
  serialize, adjacent test dependencies are protected, human edits pause affected
  work, and unauthorized assistant edits/undo are rejected. Revocation, disconnect,
  restart, stale packets and late model replies cannot renew execution authority.
- M4 — One operator-owned companion serves the server within explicit account
  and quota boundaries. Proof: one bounded model call at a time, fair capped
  requests, denied users spend no quota, outage/cancellation is clean, and no
  client receives the companion token or Codex credentials. A personal SDK
  probe does not prove eligibility for shared multi-user subscription use.

## Architecture decision — 2026-10-26

Keep Fabric + Node 24 TypeScript + SQLite. Develop on a dedicated server first,
initially on Josh's PC; no hosting purchase, public exposure or port forwarding
is implied. Solo play uses that server, and integrated single-player uses the
same logical-server core. The extra JVM/setup is a cost on this slow PC.

Authority is server-side. Fabric derives actor UUIDs from authenticated
Minecraft connections, owns grants, immutable delegations, reservations,
write-ahead journals, observations and functional evidence. The companion on
the server machine is the canonical durable project/blueprint/decision store.
It receives requests only through Fabric's private gateway and cannot grant
permissions or authorize mutations. Avoid two independent copies of authority.
Every accepted task/result is fenced by server epoch, project/blueprint revision
and authorization revision; validate again when execution reaches each write.

Projects default private. Store owner/editor/viewer membership, with separate
rights for delegation, removal/undo and requesting model work; project access
does not automatically grant use of the operator's quota. Online-mode identity
is the normal multiplayer deployment baseline; offline CI profiles do not prove
production authentication. Server admins control global policy/emergency stop.

Reserve write regions plus relevant support, read and test-isolation footprints,
scoped to world/dimension. Serialize overlapping AI operations. Do not prohibit
ordinary human building: detect changes and pause/reconcile affected work.
Testing, input restoration and undo obey the same authority and conflict rules.
Initially pause on the responsible player's disconnect; another authorized
member can explicitly take over after reconciliation. Restart expires leases
and execution authority; ownership and task history persist.

Replace path/player-derived project identity with a persisted nonsecret world
UUID plus a server namespace. Define moves, clone/fork/import and duplicate-ID
handling explicitly. Back up and explicitly adopt legacy projects/journals;
never auto-grant old client data authority on a shared server. Keep secrets and
the project database in private server-owned storage outside saves/repository.

Replace the single static preview with capability-negotiated Fabric payloads,
sent only to current project members. Use bounded snapshots/deltas with epoch,
revision and ordering checks; clear cached views on revoke, world change and
disconnect. Separate shared observed progress from player/project tutorial
cursors. Vanilla world blocks remain visible according to Minecraft's normal
replication; project ACLs do not make physical builds invisible.

## Options, challenge and provider gate

Staying single-player-first postpones changes already visible in the code:
player-keyed sessions, a static preview, path-derived identity and no membership
schema. Removing the guards alone is unsafe. A separate hosted/cloud product
adds cost, credentials and operation before the core works. Choose the existing
stack with a server-first migration and explicit multiplayer proofs.

Independent GPT-6 challenge confirmed the direction and added revision/epoch
fencing, read-dependency reservations, per-player lesson cursors, clone policy,
privacy limits and disconnect defaults. Those requirements are included above.

One private companion runs beside the operator's server. Preserve the current
tool restrictions, no API-key fallback and no public Codex endpoint. Initially
planning is operator-initiated/approved for authorized work, with bounded queue,
per-player/server caps and no cross-project context leaks. This is a technical
boundary, not proof that one personal subscription may serve arbitrary players.
Verify documented eligibility for the intended shared usage before enabling it;
if unsupported, keep that capability blocked rather than add paid services.

Sources checked 2026-10-26:
[Fabric networking](https://docs.fabricmc.net/develop/networking) and
[logical sides](https://wiki.fabricmc.net/tutorial:side) support a shared
logical-server core. [Official Codex authentication](https://learn.chatgpt.com/docs/auth)
documents subscription login and cautions against untrusted/public execution;
it does not establish this multiplayer entitlement.

## Build sequence

1. **Proven foundation (#8, M1–M4 technical controls).** Dedicated/integrated
   logical-server execution, private grants, stable identity, backed-up migration,
   reservations, revocation/restart recovery, optional shared overlays, personal
   lessons, private SQLite memory and bounded operator-only planning. Five CI
   gates are required. Offline CI identities and overlay opt-out do not prove
   production Mojang authentication or a separate vanilla-client binary.
2. **Building: remaining phase 1.** Mechanism library and targeted repair (#1)
   are proven and merged. Next: component dependencies/ownership and perception
   (#2), deeper debugging/richer teaching
   and combined live-model acceptance (#3), in that order. Public CI stays
   credential-free; any live operator probe is separate evidence. Resolve
   documented eligibility before enabling shared multi-user subscription work.
3. **Phase 2 unchanged (#4).** Voice, physical NPC, survival gathering,
   schematics, advanced optimization and multiple independent builders.

## Preserved release and proof

v0.1.0-alpha.1 at 168ac190daddd066957ecaebd50a807e7926167c shipped through PR #5;
PR #7 records its screenshots and limits. Its four required checks passed:
https://github.com/1xmint/minecraft-ai-engineer/actions/runs/40367902860
(attempt 2 recovered a graphics package download timeout).
The actual client flow proved reference teaching, delegation, save/reopen,
conflict handling, safe undo and tick-based testing. The official SDK/CLI 0.164.0
ChatGPT probe passed with gpt-6-luna. These do not prove multiplayer or the
combined live-model/game flow. All automated tests stay on GitHub CI; only
scoped compilation/formatting runs locally. Existing game/toolchain pins remain.

Release: https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.1
Next build: https://github.com/1xmint/minecraft-ai-engineer/issues/2
The published alpha reference supports only the signal lamp. Larger designs, inventories,
general debugging, textured previews and the full phase 1 remain unfinished.

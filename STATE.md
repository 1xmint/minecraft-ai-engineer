# Now

2026-10-26: **Multiplayer-first, single-player-compatible is the approved target.**
The active goal remains **complete all milestones**, including remaining phase 1
and all six phase 2 capabilities. Foundation #8 is on
`phase/2-multiplayer-foundation`, PR #10. No multiplayer release has shipped;
v0.1.0-alpha.1 remains single-player-only.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Implemented: server-owned private projects/grants, personal lessons, stable
world/server identity, fenced construction/reservations, optional recipient-filtered
overlays, private v2 companion memory and bounded operator-only planning.
Explicit operator/self legacy adoption preserves per-attempt backups and journals
with private paused ownership. Independent Java/gateway review is complete;
identified authorization, recovery, report-ordering and stale-boot defects are fixed.
Blueprint integers/text and overlay packet budgets are bounded.

CI run 40418092324 passed all five checks, including Dedicated multiplayer and
the retained integrated Minecraft client. The real dedicated proof passed two
functional off/on/off runs (before/after restart), independent lessons, denied
Eve, overlay-disabled text teaching, live/cross-project revocation, conflict
preservation, overlapping work, backed-up adoption and ambiguous target rejection.
All four native client roles finished. The real SQLite verifier recorded the
intended six-block blueprint, functional checkpoint history and two exact world
inspections (empty before building and built after restart). Native libraries
are isolated per client; disconnected test clients return to the title screen.
Dedicated multiplayer is now a required main-branch check alongside the other four.

Foundation acceptance is proven; #8 is ready to close through PR #10 after final
documentation checks. Next is the validated mechanism library (#1), followed by
component collaboration (#2) and deeper teaching/debugging (#3). Neither milestone
is complete. Shared multi-user subscription inference remains blocked; only the
configured account-owning operator can request model work.
Worker commits from work/multiplayer-network and work/multiplayer-companion are
integrated. Automated tests run on GitHub only; local compile/format checks pass.

## Outcomes for the next build — mechanism library (#1)

- N1 — Choose useful mechanisms beyond a signal lamp. Proof: actual Minecraft
  truth-table/timing tests, including rotated layouts, plus the same native
  server teach/delegate/test flow. Unsupported designs fail before world edits.
- N2 — Understand and correct a mistake in those mechanisms. Proof: introduce
  wrong wiring/orientation, identify the relevant component and cause, preserve
  player changes, explicitly delegate a correction and run the functional test.

Next tasks: define mechanism contracts and failing actual-game cases first
(N1/N2); implement bounded registry-validated templates and test routines (N1);
connect the supported catalog to planning, lessons and diagnoses (N1/N2); drive
the actual dedicated/integrated flow and independently review new mutation paths
before merge (N1/N2). Keep physical execution replaceable for phase 2.

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
2. **Next: remaining phase 1.** Validated mechanisms (#1), component
   dependencies/ownership and perception (#2), deeper debugging/richer teaching
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
Next build: https://github.com/1xmint/minecraft-ai-engineer/issues/1
Current reference supports only the signal lamp. Larger designs, inventories,
general debugging, textured previews and the full phase 1 remain unfinished.

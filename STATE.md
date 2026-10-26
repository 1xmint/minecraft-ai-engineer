# Now

2026-10-26: **Multiplayer-first, single-player-compatible is the approved target.**
The active goal is **complete all milestones**, including remaining phase 1 and
all six phase 2 capabilities. Building the dedicated-server foundation (#8) on
`phase/2-multiplayer-foundation`, ahead of adding mechanisms. No multiplayer
runtime support has shipped. The existing v0.1.0-alpha.1 remains single-player-only.
Public repo: https://github.com/1xmint/minecraft-ai-engineer

Current work: optional negotiated overlay networking, private project grants,
per-player tutorial cursors, stable world/server identity and dependency-aware
execution reservations are implemented on the branch. Scoped Java main/client
and TypeScript compilation pass; runtime proofs are pending. Existing actual
client/undo/test checks are retained, adapted to network previews and project
files. New policy/lease/wire-format proofs are queued for GitHub CI.

The v2 companion gateway and its Java integration now capture server/project/
authorization revisions, fence stale replies, persist shared memory in SQLite,
and limit model requests to the configured operator. The three-client native
dedicated-server harness is implemented, including an overlay-disabled client,
revocation, overlapping tasks, actual redstone tests and saved-world restart.
CI also verifies its real HTTP/SQLite checkpoints without inference credentials.
These runtime proofs have not passed yet. Local TypeScript/Java compilation and
formatting pass. Independent Java safety review found and fixed workspace,
recovery and revocation defects; the gateway integration review is pending.

Unfinished in this slice: passing runtime proofs, explicit legacy data adoption,
and the final independent gateway review. Worker worktrees
are under this chat's work/multiplayer-network and work/multiplayer-companion;
their completed commits are integrated. Helpers resumed after the account reset;
none of this is evidence that #8 or either milestone is complete.

## Outcomes for the next build

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

1. **Next: dedicated foundation (M1/M3), issue #8.** Split project execution from player
   sessions, add stable server/world identity and explicit project membership,
   migrate journals safely, and prove bounded reference building, reservations,
   revoke/disconnect/restart and denied access on a real two-client server.
   Add a required multiplayer CI gate before relaxing existing release guards.
2. **Then: shared presentation (M2).** Add optional overlay handshake and
   recipient-filtered snapshots/deltas, independent tutorial cursors and command
   fallback. Prove two authorized clients and a denied third; retain solo play.
3. **Then: controlled shared planning (M4).** Resolve subscription eligibility,
   server-owned queue and caps, project-scoped context, cancellation and stale
   replies. Public CI remains credential-free; any live operator probe is
   separate evidence.
4. **Then: remaining phase 1.** Resume validated mechanisms (#1), component
   dependencies/ownership and perception (#2), deeper debugging/richer teaching
   and combined live-model acceptance (#3) on the server foundation.
5. **Phase 2 unchanged (#4).** Voice, physical NPC, survival gathering,
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
Next build: https://github.com/1xmint/minecraft-ai-engineer/issues/8
Current reference supports only the signal lamp. Larger designs, inventories,
general debugging, textured previews and the full phase 1 remain unfinished.

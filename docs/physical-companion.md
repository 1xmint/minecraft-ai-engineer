# Optional physical companion

Current source supports a visible survival actor, bounded resource gathering,
flat navigation, native inventory crafting, retained-item placement, building and
lever-testing the NOT gate from chest items, and two companions building separate
parts at once. Alpha.2 predates these features. Check the
[release notes](release-notes.md) and use the paired files. Other machines and
tools remain unfinished.

![The companion rendered by the actual client](images/physical-companion.png)

## Setup and lifecycle

Install the free Carpet 26.3 server mod alongside Engineer:
`fabric-carpet-26.3+v260915.jar` from the
[Carpet release](https://github.com/gnembon/fabric-carpet/releases/tag/v26.3).
Pinned SHA256: `7c40b93e807ed914064176898037117097446c6cc76e6a80a856c8bf7f7659f9`.
No extra account, API key or client mod is needed. Core Engineer works without Carpet.

As project owner, select a workspace with a 3x3 stone floor at its minimum corner,
two clear air blocks above it, and no entities in the pad. Keep that pad clear.
Say `Engineer, summon my companion`. Ask `Engineer, where is my companion?` or
`Engineer, dismiss my companion`. Authorized viewers can ask for its location;
only the owner controls its work.

The generated `Eng_...` actor starts in survival mode. Idle actors cannot freely
mine, use items or collect drops/experience. Every action needs a fresh server
check. Owner connection, dimension, project selection, revision or grant changes
remove the actor. Stop/shutdown never restore action authority automatically.

A project has a private worker identity. Explicit resummon loads its saved native
player inventory; missing/invalid data or a conflicting identity refuses the
request. Saving reads back and compares inventory. Restart does not summon or
resume work. Inventory/world saves are not an atomic hard-crash transaction.

## Gather and move

Say `Engineer, gather dirt at X Y Z` or `Engineer, gather oak log at X Y Z`.
Each request handles one real source block and its captured native drop. The
worker can accumulate resources while preserving every pre-existing inventory
slot; only one plain expected item may be added. Full inventory refuses work.
Mining by hand requires an empty hand or a supported plain resource stack;
holding a tool or unsupported item refuses rather than pretending to use it.

Sources and routes must lie inside the loaded workspace with stone-supported
walking cells, two clear body blocks and inert surroundings. Straight gathering
uses a bounded three-wide corridor; farther targets can use a bounded flat route
to a mining stance. Movement uses native input, never teleporting or tunnelling.
Edits, entities, deviation, lost authority or an unreachable target stop work.
Oak support is isolated logs; leaves/adjacent logs are refused. This is not tree
harvesting. Stairs, jumping, hazards, general terrain and tool mining are unsupported.

## Craft retained resources

With an idle summoned companion, request:

- `Engineer, craft oak planks`: one oak log produces four planks.
- `Engineer, craft sticks`: two oak planks produce four sticks.
- `Engineer, craft a crafting table`: four oak planks produce one table.

These use Minecraft's native 2x2 inventory crafting menu and actual retained
ingredients. Native result clicks consume ingredients; no output stack is supplied
by Engineer. Unrelated inventory stays unchanged. Missing ingredients, unresolved
history, occupied menus or changed authority refuse the task. Placing a crafting
table does not enable 3x3 recipes or tools yet.

Say `Engineer, inspect companion recovery` (or ask what happened to gathered
resources) for owner-only comparison of records, saved inventory and visible
world state. Missing data is unavailable, never assumed empty. Record claims are
not proof that a missing drop was collected. Inspection moves nothing.

After inspection, `Engineer, recover companion crafting resources` requests a
fresh bounded recovery of the one valid unfinished craft. Ambiguous or altered
history refuses. Recovery moves actual retained resources out of the recorded
menu/cursor state and verifies native saving; it never takes another result,
replaces missing items or retries the craft. Another craft needs a new request.

## Place and build

Request `Engineer, place retained dirt at X Y Z`,
`Engineer, place retained oak planks at X Y Z`, or
`Engineer, place retained crafting table at X Y Z`.
The supported retained-item contract, clear target, support, reach, loaded
workspace, inert surroundings and entity checks must all pass. Native survival
right-click consumes the real held item; saved inventory must match the predicted
decrement. Keep the summon pad clear for later resummoning.

For a supported single-root stone/dirt blueprint, request
`Engineer, build the next blueprint block with retained dirt` or
`Engineer, gather dirt and build this blueprint`. Existing stone supports must
match. The bounded job handles up to 32 dirt cells, gathers real dirt, routes to a
placement stance, places, and repeats. It reserves observed blueprint dependencies
and stops on plan/player changes or inaccessible resources. Other blueprint
materials and composite physical scheduling are unsupported.

Matching player blocks do not become assistant-owned. Native placement does not
fabricate creative undo history or grant a functional pass. This bounded dirt
executor is not a general redstone machine builder.

## Build a machine

Put the materials in a chest in the workspace, preview the NOT gate, then say
`Engineer, build this machine from the chest at X Y Z`. The companion draws real
items and places each block by survival item use. It then pulls the real lever
and reads the real lamp for each case. `Engineer, test this machine` tests again.
A player's block in the way stops the job and is left alone. A restart resumes
nothing; ask again to finish from what exists.

## A second companion

`Engineer, summon a second companion at X Y Z`, then
`Engineer, have my first companion build ID from the chest at X Y Z` and the same
for `my second companion`. `Engineer, recover my second companion's resources` and
`Engineer, dismiss my second companion` act on that companion only. Each one has
its own inventory, task and permissions. A part, chest or dependency another
worker holds is refused and nothing moves.

## Stop, proof and limits

Say `Engineer, stop` to stop owner-controlled physical work. Inventory is saved
and checked; unfinished records remain for inspection. No automatic retry,
compensation or old action authority is restored after stop, disconnect or restart.

Physical companion CI runs on a dedicated server and retains native gathering,
crafting, inventory conservation, interruption/recovery, placement and saved-world
restart evidence. Combined run 42173685916 at 59f4b66 passed and its artifacts were
inspected, including grid/table restart markers and native conservation receipts.
This uses offline identities; it does not prove Mojang login, human audio or the
complete Phase 2 experience. Current integration/release status is in STATE.md.

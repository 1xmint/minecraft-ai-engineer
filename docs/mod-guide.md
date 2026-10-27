# Try the Fabric mod

## Multiplayer development branch

PR #10 implements the dedicated-server foundation. All five checks passed in
[the dedicated acceptance run](https://github.com/1xmint/minecraft-ai-engineer/actions/runs/40418092324).
The published alpha below remains single-player-only.
On the development branch, run one companion beside the Fabric server, using
the same private `MCE_CONFIG_DIR`. Use `./gradlew runServer` from `mod/` for a
development server and accept Minecraft's EULA yourself. Keep production
`online-mode=true`. Start a matching client and connect locally. The optional
client mod adds previews; ordinary commands and text teaching are server-side.
No public hosting or port forwarding is needed for local play.

An operator can create a private project with `/engineer project new`, or grant
another player creation access with `/engineer permit <name-or-UUID>`. Use
`project list` and `project use <UUID>` to choose a project, then follow the
reference handoff below. The owner can `project share <player> editor` or
`viewer`, and `project revoke <player>`. Editors may build; removal requires
the owner's separate `project permission <player> undo true`. Overlapping
projects need the relevant permission from each owner. Personal teaching steps
do not move another player's lesson. `/engineer stopall` is the operator stop.

Planning is restricted to the single account-owning operator configured with
`MCE_CODEX_OPERATOR_UUID` on a dedicated server. Other members never receive
the companion token or direct model access. Shared subscription eligibility
has not been established. Reference building requires no model request.

Projects and write-ahead journals now live under private
`servers/<server-id>/worlds/<world-id>/projects/` config storage. A persisted
world UUID survives a move when the original path no longer exists. A copied
world with the same UUID is rejected while the original exists; an operator
can `/engineer world fork` before adopting projects to give the copy a new
identity. This does not copy existing project permissions or history.

To upgrade a legacy solo checkpoint, an operator runs
`/engineer project adopt-legacy` in its original world path and dimension.
It adopts only that operator's UUID, validates the recovery document, and
backs up the original checkpoint/snapshot under private `legacy-backups/`
before creating a private paused project. The original stays untouched.
Inspect before explicitly resuming or undoing. Other players, copied paths,
and arbitrary file imports are not silently adopted. Restart expires all
delegations, and multiple owned projects require an explicit `project use`.

## Mechanism-library development branch

PR #11 adds `/engineer catalog` and `reference <mechanism> [delay]` for a
signal lamp, inverter (`not-gate`) and two-input `or-gate`. Repeater delays are
1�4 redstone ticks; the inverter accepts 1. Each mechanism has actual input
vectors and a separate functional test. Choose a region large enough for its
preview, keeping the floating circuit's two-block isolation margin clear.

The targeted repair workflow on this branch is under acceptance testing:

1. `/engineer diagnose` identifies missing supports, incorrect repeater input
   direction/delay, or supported lever/torch attachment mistakes. It reads the
   actual loaded world and explains each proposed correction without editing.
2. Review the messages, then `/engineer repair` explicitly delegates those
   corrections. A fresh diagnosis belongs to your current connection and
   project revision. Any intervening world edit requires another diagnosis.
   Wrong block kinds, inventories and occupied margins require manual action.
3. `/engineer test` runs the mechanism's real truth table and restores inputs.
   Repair completion alone records no functional pass.
4. `/engineer revise 4` changes the intended repeater delay while retaining the
   world. Diagnose and explicitly repair that difference, then test again.
   Model planning on existing construction uses the same bounded revision path.
5. `/engineer undo` reverses the latest assistant operation, retaining earlier
   construction groups. Later player edits can block undo. An interrupted undo
   retains its selected group across restart; it cannot spill into older work.

Player-built supports and current lever power are retained. A player toggle
pauses active repair, and revocation/disconnect expires its authority. A restart
retains journals and evidence, while requiring fresh explicit authority. This
slice does not yet support arbitrary circuit rewrites or component relayout.

## Published alpha

The current implementation is the first reference circuit, not the entire
phase 1 product brief. It supports only local Java Edition 26.3 single-player
creative worlds. Remote servers, LAN sharing, survival mutation, physical
builders and arbitrary redstone designs are rejected or unsupported.

## Build and launch

Install a Java 25 JDK and Node 24. Start the companion as described in
companion-guide.md. The mod and service use the same `MCE_CONFIG_DIR`, or
`%APPDATA%/minecraft-ai-engineer` on Windows and
`~/.config/minecraft-ai-engineer` elsewhere. `companion.json` contains the
service token and port; do not put it in a world save or commit it.

From `mod/`, run `./gradlew runClient` (`.\gradlew.bat runClient` on Windows).
This launches a separate development game directory; it does not modify the
installed launcher profile or existing saves. Create a disposable creative world
with commands enabled. The wrapper pins Gradle 9.6.0 with its SHA-256 checksum;
Minecraft 26.3, Loader 0.20.1, Fabric API 0.165.0+26.3 and Loom 1.17.21 are pinned.

`./gradlew build` produces `mod/build/libs/minecraft-ai-engineer-0.1.0.jar`.
Install that jar alongside the pinned Fabric API into a matching Fabric
profile to use the installed launcher. Never upgrade an existing save merely
to try this prototype.

## Reference handoff flow

1. Fly into clear air. The test needs a floating circuit with a two-block air
   margin, to avoid switching unrelated player circuitry.
2. Select a three-by-two-by-one region, for example:
   `/engineer select1 0 100 0`, then `/engineer select2 2 101 0`.
3. `/engineer plan Help me build a lever-controlled redstone lamp` asks Codex
   once for a supported design. Account/limit failures leave the task blocked.
   `/engineer reference` loads the same circuit without any model call.
4. The preview shows translucent cuboid markers and block names. Cyan marks
   the current tutorial step, green the correct blocks, red occupied mistakes.
   Repeater arrows show output direction; its vanilla state `facing=west`
   actually reads input from west and outputs east.
5. Place the three stone supports at `(0,100,0)`, `(1,100,0)`, `(2,100,0)`.
   Teaching detects the completed support section and advances automatically.
   Use `/engineer repeat`, `previous`, `next`, `skip`, `pause`, or `teach`.
6. `/engineer build` explicitly delegates the remaining blueprint positions
   inside the selected region. It places one block per four game ticks.
   Existing incorrect blocks, inventories and unloaded chunks stop the build.
7. `/engineer stop` interrupts immediately at the next command boundary.
   `/engineer resume` reconciles the complete assignment before continuing.
   Changing a pending block while paused causes a conflict instead of an
   overwrite. `/engineer undo` removes only assistant placements and refuses
   to erase later player edits.
8. `/engineer test` checks the actual lamp off/on/off at ticks 8, 16 and 24,
   restores the input and records the result. It refuses a crowded test area.
   A geometry match is not a functional pass.
9. Save and quit, restart the game/service, then `/engineer status`. Stored
   work is paused. `/engineer resume` requires creative mode, the same world
   and dimension, loaded chunks, and unchanged recorded assumptions.

Other commands: `/engineer inspect` exports exact states for the selected
region (limit 4096 blocks), including orientation. `/engineer rotate 90`
rotates positions and block states together; choose a region large enough for
the result. `/engineer anchor x y z` moves the preview. Stop planning and undo
assistant work before changing the delegation or replacing the blueprint.

Rotation/anchor changes create a new local blueprint identity and revision,
with verification reset. They persist in the mod. The companion rejects test
evidence for a local revision it has not registered; use a new `/engineer plan`
request to save a companion-backed revision of the current project.

## Persistence and safety boundary

Checkpoints and snapshots live in the local config `sessions/` directory,
separate from saves. World identity hashes the canonical save path and is
combined with player and dimension identity; moving a save intentionally
creates a new identity. Construction permissions never auto-resume on restart.

Each placement flushes the before/after journal before modifying the world,
then marks completion. Interrupted placement and undo are reconciled from
actual block states. Undo checks the complete journal before starting. The
mod never overwrites existing incorrect blocks; correct player-built sections
are retained. The executor interface is replaceable for a later physical
builder. A model response cannot call world-edit endpoints.

Offline reference projects persist in the mod's local checkpoint; they are not
registered as companion projects. Companion-backed designs also checkpoint
their state to SQLite. Chat and world text are never interpreted as code.

## Evidence and remaining scope

Local evidence: main and client Java source, including the client integration
test, compile against the pinned 26.3 libraries. CI uses `./gradlew runGameTest`
for actual off/on/off redstone semantics, rotated behavior, and block-state
rejection.

`./gradlew runClientGameTest` runs an actual Minecraft client and unpublished
integrated server. On Linux CI it needs a graphical virtual display (Xvfb) and
software rendering. The test drives the production `/engineer` commands,
checks the preview, fixture-places the player's first tutorial sections in the
actual world, delegates the rest, interrupts after a real placement, closes and
reopens the actual save, checks conflicting edits, resumes, runs the live
functional test, and checks safe undo and permission failures. Screenshots
`engineer-01-reference-preview` and `engineer-02-reloaded-tested-build` are saved
in the client test run directory's `screenshots/` folder for visual review.

These are automated client integration checks, not evidence of a person
manually placing blocks with the mouse. They deliberately use the offline
curated reference rather than signed-in model calls in public CI. Codex
authentication and the full combined Codex-to-game player experience need
separate evidence. Do not label the full product shipped from these checks.

The preview currently uses colored translucent block bounds with labels, not
textured ghost block models. General design generation, large component
hierarchies, neighboring circuit analysis, accessible inventory inspection,
world change event indexing, general circuit debugging, interface contracts,
project-wide revision propagation, and full player-flow acceptance remain to
be built and proved. The exact source brief remains in product-brief.md.

# Try the Fabric mod

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

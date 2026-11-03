# Building imported schematics

This feature is on the development branch. Full multiplayer integration checks
are still pending; the published alpha.2 does not include imported construction.

The server operator puts Sponge v3 `.schem` files in `schematics/library` inside
the companion's configuration folder. In game, address the Engineer in chat:

1. Say **“Engineer, list schematics”**, then **“inspect schematic castle.”**
2. Say **“import schematic castle.”** If content must be discarded, the Engineer
   explains it first. Include **“and drop block entities”** only when you agree to
   lose sign text, chest contents and other block-entity data. Entities are refused.
3. Use **“list designs”** and **“show design 1”** to inspect the stored geometry.
   Viewing a design does not replace the current project preview.
4. Rotate or mirror it if needed: **“rotate design 1 by 90”** or **“mirror design
   1 east-west.”** Each transform produces a new design number.
5. Mark a clear workspace and say **“preview design 2.”** Preview requires edit
   permission. It prepares the design but places no blocks.
6. Ask the Engineer to build it and approve the proposed action. Say **“hold on”**
   to interrupt. Resume only after inspecting any reported conflict.

Design numbers in these examples are placeholders; use the numbers returned in
your project. Imported designs belong to that project's authorized members.
Changing project, connection, permissions or workspace invalidates pending replies.

## Placement and materials

Preview aligns the design's minimum corner with the marked workspace origin.
Rotate/mirror the stored design before previewing to choose its orientation.
Air cells are omitted: they do not authorize clearing existing player blocks.
Backing blocks are placed before attached devices, inside the delegated loaded
region with a clear two-block isolation margin.

The current imported family supports the pinned list of 182 ordinary full-cube
materials in [imported-materials.json](../catalog/imported-materials.json), plus
levers, repeaters, redstone lamps and wall torches. Examples include cobblestone,
planks, wool and concrete. Java checks the native block class and state as well as
membership in the list; an unknown or changed block is refused.

This is not support for every Minecraft block. Inventories, stairs, slabs,
gravity blocks, specialized interactive blocks and arbitrary redstone remain
outside this imported set. The Engineer names unsupported blocks. You can still
inspect, transform, compare or export geometry that cannot be built.

Construction currently uses creative editing. Material counts do not imply
survival gathering or inventory consumption.

## Verification, recovery and export

A completed imported build means its geometry matches. It remains **untested**:
imports have no independently defined functional contract, and ordinary testing
cannot manufacture a pass. Geometry comparison does not establish behavior,
latency or reliability.

Player edits pause affected work. Undo preserves later edits until their conflicts
are resolved and reverts recorded assistant construction. A server restart retains
the design and history, but grants no renewed lease or running callback.

To export the marked selection, say **“export selection as gate.”** Selections
are limited to 64 blocks per side. Block contents require an explicit **“and drop
block contents”** agreement. Exports go to the companion's `schematics/exports`
folder and never overwrite an existing file. Exporting reads the world; it does
not change blocks or confer verification.

# PROJECT: Minecraft AI Engineer

## Mission

Build a local-first, AI-powered Minecraft Java Edition engineering companion that can:

1. Understand natural-language engineering requests.
2. Design working redstone mechanisms and larger multicomponent projects.
3. Visualize proposed designs in the actual Minecraft world.
4. Teach the player to construct mechanisms interactively.
5. Inspect and understand structures the player has already built.
6. Take over unfinished components and build them autonomously.
7. Collaborate with the player on different parts of the same project.
8. Test redstone mechanisms, diagnose failures, and repair them.
9. Retain durable memory of projects, designs, assignments, decisions, and progress.
10. Use the user's existing Codex subscription for high-level reasoning where officially supported.

The product should feel like an engineering partner inside Minecraft, not a chatbot attached to an ordinary building mod.

Primary target: local Minecraft Java Edition single-player.

Do not assume the Minecraft edition or installed game version. Detect or ask only when genuinely required. Target a maintained Fabric version, with all versions pinned and documented.

## Engineering philosophy

Optimize for the user experience rather than architectural novelty.

- Prioritize end-to-end vertical slices.
- Prefer existing maintained libraries when they materially simplify development.
- Avoid unnecessary frameworks and abstraction layers.
- Make the world state authoritative, not the LLM's memory.
- Prefer deterministic local execution for predictable actions.
- Reserve expensive model calls for tasks requiring judgment.
- Do not create elaborate agent swarms.
- Do not use continuous screenshot-to-LLM loops as the primary perception method.
- Do not treat code that compiles as proof that a redstone machine functions.
- Challenge proposed architectures and simplify when appropriate.
- Verify uncertain external API behavior from official documentation.
- Implement actual functioning features, not simulations presented as completed work.
- Avoid extensive benchmark exercises with unclear practical benefit.
- Use focused tests that establish specific correctness properties.
- Be willing to change the implementation approach if the original architecture proves inferior.

## Core architecture

Implement three primary components.

### A. Minecraft Fabric mod

Responsibilities:

- Player-facing chat and in-game UI.
- Selecting build regions and individual components.
- Rendering transparent ghost blocks and directional indicators.
- Reading nearby block states, inventories where accessible, and redstone states.
- Receiving and validating proposed blueprints.
- Detecting changes to the actual world.
- Checking building progress.
- Performing authorized block placement/removal.
- Running deterministic inspection and redstone tests.
- Communicating with the local companion service.

Separate client-side rendering from integrated-server world operations.

Use a narrow, authenticated localhost protocol. All world mutations must be scheduled on the Minecraft server thread and must respect lifecycle, loaded chunks, and game mode.

### B. Local AI companion service

Implement in TypeScript/Node.js unless research identifies a clearly superior approach.

Responsibilities:

- Natural-language command interpretation.
- Codex SDK integration.
- Context selection and compression.
- Persistent project memory.
- Engineering planning.
- Blueprint generation and revision.
- Task scheduling.
- Delegation and ownership.
- Monitoring local task execution.
- Handling interruptions and changes in user instructions.
- Troubleshooting and reporting.

Use the official OpenAI Codex SDK with supported user authentication.

First verify the selected model, authentication, permitted usage pattern, rate limits, unattended operation, and structured-output capabilities.

Do not bypass authentication, scrape credentials, or assume subscription access provides unlimited inference.

Provide an LLM provider adapter so a different officially supported provider can be used later without rewriting the Minecraft features.

Keep secrets outside world saves and repository files.

### C. Durable project system

Use SQLite and versioned blueprint files.

Store:

- Worlds and dimension identifiers.
- Projects and their objectives.
- Component hierarchies.
- Locations and bounding boxes.
- Design rationale and requirements.
- Blueprint versions and revisions.
- Actual construction state.
- Player and assistant assignments.
- Task dependencies and progress.
- Known redstone interfaces.
- Test results and failures.
- Important player preferences.
- Conversation references and summarized decisions.

Separate intended state, observed state, and verified state.

Do not rely on a long-running language-model conversation to preserve project knowledge.

## Functional specification

### 1. Natural-language interaction

Allow requests such as:

"Help me make a compact 3x3 piston door."

"Show me how the timing works."

"Explain this part again."

"Use fewer observers."

"Make the design fit inside this wall."

"Finish the wiring while I work on the entrance."

"Stop building."

"What are you working on?"

"Why isn't this circuit working?"

"Undo the last section you built."

Support follow-up references to the current project, selected component, and recent instructions.

Use explicit selection or clarification when the referent remains ambiguous. Never silently modify an uncertain target.

### 2. World-aware inspection

Create a structured world-perception API.

Minimum capabilities:

- Get player position and view target.
- Get block states and orientation within a selected region.
- Identify redstone components and connections.
- Retrieve relevant block/entity state where authorized.
- Inspect neighboring blocks and support relationships.
- Identify missing, extra, or misplaced blocks.
- Detect modifications since the previous observation.
- Determine whether required chunks are loaded.
- Inspect accessible resource inventories.
- Recognize existing project regions and subcomponents.

Use targeted snapshots and change events instead of continuously scanning entire worlds.

Avoid treating unloaded blocks as air.

### 3. Blueprint system

Define a typed blueprint format containing:

- World/dimension.
- Project identifier.
- Relative coordinates and anchor.
- Required block types and block states.
- Block facing, orientation, and other properties.
- Component boundaries.
- Functional ports and connections.
- Placement prerequisites.
- Build stages.
- Required resources.
- Tests and expected behaviors.
- Explanations of each subsystem.
- Blueprint version and provenance.

Support rotations, translations, corrections, and revisions.

Validate every blueprint against a supported block-state registry before offering construction.

Blueprints must be machine-executable, not vague natural-language descriptions.

Include rollback information or before-state snapshots for authorized world modifications.

### 4. Redstone design intelligence

Build an engineering workflow:

1. Understand requirements.
2. Inspect existing structures and constraints.
3. Identify a suitable mechanism family.
4. Retrieve reusable known-good components if available.
5. Plan interfaces and component boundaries.
6. Generate a structured blueprint.
7. Validate geometry and block states.
8. Check important dependencies and redstone assumptions.
9. Test the design in the target Minecraft version.
10. Present the design with verification status.

Favor proven component templates for common mechanisms, but permit new designs and modifications.

Do not claim arbitrary newly generated redstone circuits are functional without testing.

Account for:

- Java Edition redstone mechanics.
- Directional block placement.
- Signal strength and attenuation.
- Repeater delays.
- Comparator behavior.
- Observer updates.
- Piston extension/retraction behavior.
- Quasi-connectivity where applicable.
- Update order and timing.
- Chunk loading and boundary conditions.
- Interference with neighboring circuitry.

Specify behavioral tests based on the function being implemented.

### 5. Interactive teaching mode

Implement in-world build instructions.

Requirements:

- Preview the entire proposed structure as a hologram.
- Highlight only the blocks relevant to the current step.
- Render orientations for pistons, observers, repeaters, etc.
- Show a concise explanation of what the player is building.
- Let the player request more detailed explanations.
- Automatically detect completion of the current step.
- Detect mistakes and highlight necessary corrections.
- Support previous, next, repeat, skip, and pause.
- Permit out-of-order placements where safe.
- Allow the player to request AI assistance during any step.

Tutorial stages should respect actual construction dependencies.

Do not rely on arbitrary groups of ten blocks or fixed layers if functional grouping provides a better experience.

### 6. Autonomous building

Support requests like:

"You build the remaining memory cells while I work on the display."

Implement an autonomous build controller that can:

- Accept a bounded construction assignment.
- Determine the required material list.
- Compare required resources with available resources.
- Check the working area's safety.
- Resolve placement dependencies.
- Place blocks with correct states and orientation.
- Remove or correct authorized blocks.
- Track completion.
- Recover from interrupted operations.
- Verify the constructed result against the blueprint.
- Run appropriate functional tests.
- Report blockers and failures.
- Resume unfinished tasks across sessions.

Implement two distinct execution policies:

**Creative/direct-build mode:** Use authorized integrated-server world operations, with visible progress and controlled placement pacing.

**Survival/physical-build mode:** Use legal in-world player or NPC actions, including material acquisition, navigation, and actual placement constraints. Do not falsely present direct world-editing as survival gameplay.

Start with creative/direct-build mode.

Design a replaceable executor interface to support a physical companion later.

### 7. Shared collaboration

Implement task ownership and work-region coordination.

The player and AI should be able to work on different components of the same project simultaneously.

Track:

- Who owns each current task.
- Which regions the assistant may modify.
- Component dependencies.
- Assigned build stages.
- Block-level change conflicts.
- Player edits made during autonomous work.
- Changes that invalidate a blueprint.

If the player modifies an active build region, the assistant must recheck relevant assumptions before continuing.

Never overwrite player modifications silently.

The player should be able to take over a task or delegate it back without losing progress.

### 8. Redstone inspection and debugging

Support requests such as:

"Why doesn't this door close?"

"Test the memory read circuit."

"Find the broken connection."

Use available world state and controlled inputs to diagnose faults.

Differentiate:

- Missing blocks.
- Incorrect block orientations.
- Broken connections.
- Timing problems.
- Unsupported blueprint assumptions.
- Environmental interference.
- Incomplete components.
- Unknown or inconclusive failures.

Where possible, test inputs and compare observed outputs with expected outputs.

For timing-sensitive mechanisms, instrument game ticks and transitions rather than sampling only final states.

Do not destroy player builds during tests.

Offer a proposed correction, preview it, then apply it under the configured permissions.

### 9. Autonomy and interruption

The assistant must operate as a persistent task executor rather than a single long LLM response.

Define explicit task states:

PENDING -> PLANNING -> READY -> EXECUTING -> VERIFYING -> COMPLETED.

Additional states:

PAUSED, BLOCKED, FAILED, CANCELLED.

Support interruption during planning, execution, and testing.

Persist actionable checkpoints and resume only after reconciling stored progress with actual world state.

If a task fails, do not repeatedly invoke Codex without a bounded recovery strategy.

Use Codex for meaningful replanning and unexpected problems, while deterministic controllers handle ordinary placements and checks.

### 10. Permissions and safety

Default behavior:

- Reading the world: allowed.
- Showing previews: allowed.
- Building inside explicitly delegated regions: allowed after authorization.
- Destroying existing player blocks: requires appropriate authorization.
- Editing outside delegated regions: prohibited.
- Replacing chests or inventories: prohibited by default.
- Executing arbitrary model-generated shell commands: prohibited by default.
- Disclosing local files, credentials, or network endpoints: prohibited.

Treat signs, books, chat contents, schematics, and world metadata as untrusted content.

No LLM output may bypass the executor's validation and permission checks.

Implement pause, emergency stop, audit logging, and bounded undo.

Take backups before large-scale or potentially destructive operations.

## Implementation sequence

### Slice 0 — Feasibility and environment

Establish a working development environment.

Verify:

- Minecraft/Fabric version compatibility.
- Local mod loading.
- Codex SDK authentication.
- One successful model reasoning request.
- Local IPC between mod and service.
- Single-player integrated-server access.
- Required world-reading and block-writing APIs.

Produce a working development launch and reproducible build scripts.

Resolve any subscription/authentication limitations before relying on AI features.

### Slice 1 — World inspector

Build a player command to select a region.

Extract exact block-state data, export it as a structured snapshot, and display a simple inspection report.

Prove block orientations and coordinates are accurate.

### Slice 2 — Blueprint renderer

Load a manually created reference blueprint.

Render ghost blocks at a selected anchor.

Support rotation, placement preview, highlighting, and visual comparison against actual world blocks.

The player must be able to build the reference structure using the overlay.

### Slice 3 — Interactive tutorial

Turn the reference blueprint into staged instructions.

Track real player placements, recognize errors, explain each step, and advance automatically.

Complete an end-to-end guided build without AI-generated designs.

### Slice 4 — Local construction executor

Create a bounded direct-build executor.

Construct the reference blueprint in an empty authorized region.

Support interruption, progress tracking, block verification, and rollback.

Reconcile a partially completed build correctly after save/reload.

### Slice 5 — Codex-driven design

Connect natural-language requests to the blueprint workflow.

Start with a small curated library of known-good redstone patterns.

Codex selects or adapts a mechanism, produces the blueprint, and explains the reasoning.

Validate all generated outputs before presenting them.

### Slice 6 — World-aware design

Let the player specify physical constraints through region selection.

Generate and adapt designs to available space and existing structures.

Support requests to optimize or revise a component without unnecessarily rebuilding the entire project.

### Slice 7 — Project memory and task delegation

Add durable project/component tracking.

Allow:

"Finish these remaining components."

The assistant should build the delegated work while the player works elsewhere.

Persist state, respect permissions, detect conflicts, and report progress.

### Slice 8 — Functional redstone verification

Build testing infrastructure using disposable world instances or safely isolated test regions.

Test representative reference mechanisms using actual Minecraft semantics.

Include tick-accurate checks where required.

Record verification evidence and distinguish tested from untested designs.

### Slice 9 — Engineering collaboration

Add interface contracts, dependency tracking, project-wide revisions, and more capable debugging.

Support multicomponent projects such as register banks, memory systems, arithmetic circuits, displays, and eventually redstone computers.

### Slice 10 — Physical AI teammate

Research the best approach for representing the assistant as an actual character.

Evaluate maintained NPC/player frameworks and Mineflayer integration against the pinned game version.

Implement a physical player-like builder if feasible.

Do not jeopardize the working engineering assistant to force an NPC framework into the first release.

## Acceptance criteria

A successful initial product must demonstrate this exact workflow:

1. The player asks for a small working redstone contraption.
2. The assistant produces a valid blueprint and explains it.
3. The player previews it in the world.
4. The assistant guides the player through the first functional section.
5. The player delegates the remaining section.
6. The assistant builds that section inside the authorized area.
7. The player can interrupt, resume, or modify the assignment.
8. The constructed mechanism passes an actual functional test.
9. Progress survives Minecraft and service restarts.
10. The assistant can later identify the project and explain its current state.

This flow must work end to end without simulated actions, hidden manual edits, or fabricated verification results.

## Development execution instructions

First inspect the environment and research the current official APIs.

Then:

1. Write a concise architecture decision record.
2. Identify the smallest vertical slice proving the critical integrations.
3. Establish the repository and development commands.
4. Implement that slice completely.
5. Run focused relevant tests.
6. Correct actual problems discovered.
7. Document how to launch and try it.
8. Continue through the slices in dependency order.

Maintain a short living project plan showing completed features, next work, and genuine blockers.

Prefer implemented functionality over large speculative planning documents.

Do not repeatedly redesign the architecture without a concrete reason.

When uncertainty can be resolved through research, inspection, or a safe experiment, resolve it rather than asking the user unnecessary questions.

Ask the user only for decisions that materially affect the desired experience or cannot be reasonably inferred.

The final product should make building complicated Minecraft engineering projects significantly easier, more educational, and more enjoyable.

**The defining experience is seamless transfer of responsibility between the player and the AI: show me, teach me, help me, take over, test it, and remember what we're building.**
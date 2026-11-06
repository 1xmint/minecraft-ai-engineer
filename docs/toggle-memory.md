# One-bit toggle memory

This guide describes the PR100 implementation candidate. It is not in the latest
published alpha.2 download. Current integration and proof status are in STATE.md.

## Playing with memory

After selecting a workspace, say `Engineer, show me a one-bit memory`. Ask about
the lesson, then request construction and approve the proposed action. Natural
conversation is the main interface; commands are a manual fallback.

The supported design is an eight-cell waxed copper bulb circuit with a lever,
comparator and output. A rising input toggles the stored bit. Holding the input
high does not toggle it again; lowering the input keeps the bit. Two pulses
restore the original bit. All four rotations and both initial bits are supported.
This is a toggle bit, not a data/clock register or general computer.

Construction uses the delegated creative workflow. A matching player-placed bulb
keeps its existing bit; it does not become assistant-owned. Changes to the stored
bit can block reconciliation or undo rather than being silently overwritten.
This feature does not make the physical survival companion a memory-machine builder.

## Testing

Request a test and approve it. The dedicated memory test observes 96 settled
samples across six 24-tick phases, applies four native lever edges, and finishes
at tick 143. It checks actual geometry, lever/bulb states, comparator and output,
and requires the original lever/bulb pair to be restored.

A current pass also requires durable evidence readback and the same valid live
controller, connection, source, assignment and authorization. Skipped ticks,
conflicts or unexpected state stop the run; there is no catch-up sequence.
Configured settling intervals are not measurements of propagation latency.

Summary, guidance and lessons share the same current-evidence checks. Changing
the source or assignment invalidates the pass. Saving and loading a project
retains historical status, but never loads a current pass or an active runner.
Imported geometry, remembered claims and offline trace replay cannot grant a pass.

## Interrupted tests and explicit recovery

Stopping, disconnecting, losing permission or changing projects can leave a
pending memory operation. Stop does not automatically pulse the lever to undo
an uncertain action. New work remains blocked until the pending operation is
safely reconciled.

Request resume and approve it to start a fresh recovery attempt. Recovery checks
current authority and actual world state, follows the latest durable progress,
and derives at most three new edges toward the original lever/bulb pair. It does
not replay old actions. Unexplained player edits are refused rather than repaired
by guessing.

Recovery can itself be interrupted. A later explicit request uses the latest
checkpoint under fresh authority. Restart never grants a lease or starts work.
Successful recovery saves and reads back the restoration receipt, clears the
pending pointers, and leaves the task paused. It does not run another test or
grant a functional pass; request a fresh test separately.

## Records and safety boundaries

Input and recovery journals persist PREPARED before a native edge and OBSERVED
after its actual paired result. Interrupted or uncertain histories stay intact.
Loaded journals are read-only data and cannot execute actions. A current root
pointer and latest-recovery pointer select progress; orphan files are not selected
automatically. Recovery chains are bounded and validated against their origins.

Each input/recovery journal namespace is limited to 64 files and 4 MiB per project,
with 64 KiB per file and at most nine events. Functional trial records have their
own bounded namespace: 64 files, 4 MiB total and 128 KiB per file. Full storage or
failed writes stop work. There is no automatic history deletion or compensation.

Native mutation requires a connected creative controller with current BUILD
permission, the selected project, loaded delegated cells, and an isolated
reservation. Those conditions are checked at action time. Model output and saved
JSON provide no authority.

## Verification and limits

Native GameTests cover both bits, rotations, transition/recovery records, storage
rejections and evidence invalidation. The dedicated multiplayer workflow exercises
addressed conversation, current testing, interrupted recovery, connection changes
and saved-world restart. Its retained records are independently replayed by
`scripts/ci/verify-memory-timed.mjs`; replay validates recorded facts and explicitly
grants no current pass. Consult STATE.md and the exact CI run for integration
status: written fixtures alone are not runtime proof.

This feature does not establish general registers, counters, displays or computers.
Offline CI identities do not establish Mojang login, human microphone/speaker
acceptance or provider permission for shared subscription usage.

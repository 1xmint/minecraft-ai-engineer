# Stateful memory foundation

Outcomes for E2:

1. Prove an actual stored bit: the same low input must coexist with either output,
   according to earlier pulses. Native CI checks both initial bits in four rotations.
2. Prove the input discipline: one rising edge toggles once, a held high input does
   not retrigger, falling power retains the bit, and two pulses restore the initial
   bit. CI observes the bulb, comparator and redstone output for16settled ticks in
   each of six phases. Configured settling time is not measured propagation latency.
3. Expose useful memory through normal engineering conversation, construction,
   permission-safe stateful testing, interruption/restart reconciliation and undo.
   This remains the next implementation slice, not a claim of this feasibility PR.

Decision: use a waxed copper bulb and comparator for a one-bit toggle memory.
Pinned Minecraft26.3 CopperBulbBlock bytecode confirms a rising edge cycles LIT,
falling power leaves LIT unchanged, and POWERED tracks the input. Waxing avoids
weathering. CI must independently prove the physical layout and retained output.
A toggle memory is not a data-and-clock register or general computer.

No production behavior/evidence contract, planner intent, automatic world work or
functional pass is added by these disposable native tests. General registers,
stateful test restoration, saved-world acceptance and larger computers remain
unfinished. All game tests run in GitHub CI, never locally.

Native proof: source1be52d3 passed CI41958383492. The artifact's independent
replay reports four rotations, two initial states, six phases and768settled
samples; the native suite passed all199required tests. Both original input and
stored bit are restored after two pulses. The first fixture attempt lacked its
air palette; the next exposed a reversed comparator. Both were corrected and
remain recorded failures. This proves the physical foundation only.

Canonical intention development uses catalog/toggle-memory.json on both the
server and companion. The source fixes all eight cells, four teaching stages,
three port roles, required initially-low control and six observation phases.
Both readers rebuild the exact intended source and reject altered geometry,
behavior, resources, identity, ownership or verification fields. No parser or
planner dispatch admits this source yet. Native probes now build those canonical
cells and require their settled states to match the intention, while the separate
replay checks actual stored/output observations. Native/companion source parity
and the new rejection tests still need CI proof.

Canonical proof: sourcecef919e passed native CI41959507805. Inspected replay
confirms eight cells, verification=untested and admitted=false, alongside all
768physical observations. The probe now builds the canonical geometry and checks
its actual settled block states, and the companion reader matches native source
exactly. This answers source agreement, not player-facing admission or stateful
restoration. A routine lever restore alone cannot undo an odd memory toggle.

Preview/build candidate (not yet gameplay-proven): say `Engineer, show me a one-bit
memory` after selecting a workspace, ask about the lesson, then request and approve
construction. New bulbs start off; a matching player-placed bulb keeps its existing
bit. Construction stays creative, delegated, loaded and isolated; this is not NPC
survival machine building. The comparator exception covers only the exact canonical
cell and never imports block-entity data. Stored-bit changes block reconciliation
or undo rather than being silently overwritten.

The admitted source has two external ports (toggle and output); the internal stored
bit is `memory.storage`, rather than a new global port kind. This revision needs new
source-parity proof. Asking for an automated test explicitly refuses without a pulse
or pass. Generic test journals/claims refuse on load, and no generic recovery path
can stimulate memory. A separate typed stateful trial remains unfinished.

Default dedicated CI now contains a normal-chat preview/build/teaching flow, a real
connected viewer denied construction, adoption of an already-lit player bulb, seven
assistant-owned cells, test refusal and actual saved-world reload. Its two receipts
are mandatory in the default CI launcher. These flows are written; their run is
pending and is not replaced by the earlier native physical feasibility proof.

Stateful transition record development (M2): MemoryInputTransition describes one
changed input with exact coupled lever/bulb before and expected-after states.
It rejects unsettled input/bulb power, wrong static geometry, changed scope,
changed source digest, extra fields and fabricated expected outcomes. Its reader
rebuilds the entire record. Frames are immutable; returned JSON is detached.
The record has no actor, permission, live ticket, world mutation or replay API.

The native probe now records immediate observed states after32real lever edges,
not only later settled output. A separate JavaScript replay checks those frames
and source digests from hand-worked phase/orientation expectations. Local Java
compilation and script syntax/format passed; focused GPT-6-Luna review found no
concrete issue. This new proof is pending CI. It does not implement persistence,
write-ahead stages, one-use event authority or recovery; those still belong to
the separate stateful trial before automated testing can be enabled.

Durable input journal development: the recording process exclusively creates a
per-project file, saves PREPARED before returning an edge description, then saves
OBSERVED only for the exact native paired result. Stop before an observation is
UNCERTAIN; a completed four-edge sequence may record RESTORED only when the
original pair is observed again. RESTORED is not a functional pass.

Every load reconstructs the complete bounded event sequence and returns a frozen
reader. Loaded logs cannot prepare, observe, stop or resume execution. Failed
saves freeze the writer, and externally changed bytes are never overwritten.
The namespace is limited to64files/4MiB per project,64KiB per file and9events;
old logs require explicit archiving, never automatic deletion. Native tests write
and reload the actual files around all32real lever edges and exercise stopped,
forged, externally changed and full-directory refusals. Java compilation and
focused review passed. Native CI42051396860 passed on66ac346, with all204 required
tests passing. Its independently replayed artifact contains eight actual journals,
32 PREPARED records,32 native observations and eight returns to the initial pair.
Every reloaded journal remains unable to execute; no functional pass is claimed.
No production actuator or recovery handler uses these records yet.

The internal MemoryInputDriver is the next execution boundary. It accepts no saved
record as authority: opening requires a fresh connected creative controller, current
selection, BUILD grant and test reservation. Each native lever edge rechecks the
source, assignment, connection, both reserved write cells and loaded isolation,
persists PREPARED, then repeats those checks and the exact paired before-state.
It records the actual pair after the native call and stops on failure or lost
authority. Stop never emits a restoration pulse. Cleanup releases only its own
reservation. No command or conversation starts this driver yet; it supplies no
settled sampler, functional pass or recovery. Connected-player proof is pending.

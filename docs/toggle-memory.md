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

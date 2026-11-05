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

# Comparator workbenches

The development source supports canonical compare and subtract workbenches. Each
uses three physical lever/dust selectors to produce input strengths from 0 to 15.
The server owns construction, permissions and test evidence.

## Start through conversation

Use a creative-mode project on the dedicated server described in the
[mod guide](mod-guide.md#multiplayer-development-branch). The account-owning
operator can use the conversational approval flow.

1. Say `Engineer, start a new project`.
2. Mark a clear workspace **17 blocks wide, 3 high and 31 deep**. Stand at its
   first lower corner and say `Engineer, mark the first corner here`. Fly to
   the opposite upper corner and say `Engineer, mark the second corner here`.
   Keep a two-block air margin around the actual floating circuit.
3. Say `Engineer, show me a comparator` for compare mode, or
   `Engineer, show me a subtracting comparator` for subtract mode.
4. Ask `Engineer, explain how this works`, `Engineer, explain the materials`,
   or `Engineer, how does the timing work?`.
5. Ask `Engineer, build this`. Review the proposed action and reply
   `Engineer, yes` to approve it. The preview itself approves no construction.
6. Ask `Engineer, test this` and approve the separate test action.

The planner also offers both modes for larger natural-language design requests
when the complete workbench fits. Rotated benches occupy either 17×3×31 or
31×3×17 blocks. Preview replacement requires a project without existing work.

The intended materials are **47 stone, 45 levers, 46 redstone dust and one
comparator**. These are design needs; the current builder operates in creative
mode. A material explanation is separate from an inventory observation.

## Read the mechanism

Each input bank has 15 ceiling levers beneath stone supporting a dust line.
Turning on one lever selects its signal strength by distance. Turning every
lever in that bank off selects zero. The lesson identifies the rear input,
the two side inputs and the numeric output wire.

- **Compare:** output equals the rear strength when it is at least as strong as
  both side inputs; otherwise output is zero. Equality passes the rear value.
- **Subtract:** output is the rear strength minus the stronger side input,
  clamped at zero.

The preview describes intended geometry. A completed build establishes geometry;
a functional pass requires its separate server trial.

## Test, interrupt and recover

The numeric suite runs 66 declared cases: every strength 0–15 at each input plus
both-side boundary cases. Each case settles for eight game ticks, then captures
three consecutive observations of the actual input wires, comparator mode and
output wire. Missing samples stay inconclusive; wrong observations fail.
These settled measurements establish neither propagation latency nor arbitrary
analog circuits outside this canonical design.

The trial records and restores all 45 original lever states, including controls
that were already on. A pass requires complete restoration. Say `Engineer, stop`
to interrupt. A player edit, lost permission or an unloaded dependency also
pauses work. Resolve reported conflicts and request explicit recovery before
starting another trial.

A saved-server restart retains historical evidence as stale and restores no
execution lease or active test. A fresh test must observe the current world.
Authorized members can read shared lessons and diagnosis. Build/test permissions
and the owner's separate undo grant still apply; model interpretation remains
restricted to the configured account-owning operator.

## Diagnose and undo

Diagnosis reports a snapshot of wire strengths separately from functional
results. Ordinary static corrections, such as a missing dust block, use a fresh
scoped repair proposal and require explicit approval. An existing wrong-mode
comparator or an inventory replacing it stays protected for manual correction.
Repairs require another functional test.

Ask `Engineer, undo that` and approve the separate action. Undo targets the most
recent assistant operation. Undoing a repair restores that repair's before-state
and keeps the older build. Player changes can block undo; its report identifies
the conflict and preserves the work. Native dust connection changes caused by
owned removals are checked against Minecraft's connection rules, while player
edits and dependencies remain protected. Comparator entity data is never restored
from a project journal.

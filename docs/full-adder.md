# Connected full adder

The fixed physical prototype has three external inputs, A, B and carry-in.
SUM is the low bit of their total; CARRY is true when at least two inputs are true.
Nine NAND gates compute both results. Only the three external levers are stimulated;
all internal gate inputs and outputs are observations.

The prototype has 3,544 cells and 35 typed pins. Buffered horizontal trunks and
insulated overhead routes connect its gates. The shared definition in
`catalog/full-adder-definition.json` uses centered relative coordinates within the
existing 64-block blueprint extent. Its version 2 graph and exact geometry bind to
SHA-256 digest `8a2b25ed743c42b936e101dc19368a35b740fb48a0dc72781712d3152dc100be`.
The half-adder's version 1 definition is unchanged.

Native GameTests exercise all eight input combinations forward and backward.
Each case observes every pin at eight consecutive actual game ticks after a
256-tick settling interval. All three original lever states are restored.
This establishes the fixed prototype's settled Boolean behavior; latency and
reliability are not measured.

The version 2 observation codec retains those settled samples and recomputes
their verdict and first mismatch. It rejects unknown fields, incorrect types,
missing pins, changed graph/input identity, clock gaps and edited claims. A
complete suite needs all sixteen ordered cases with distinct clock windows.
Recorded observations carry no owner, execution permission or current project
verification. Current component scope and exact input restoration must be checked
by the server's trial executor before a project receives a functional pass.

Player-facing preview, conversation, construction and saved-world trial integration
are still being built. The prototype and its codecs alone do not expose a full-adder
request to players.

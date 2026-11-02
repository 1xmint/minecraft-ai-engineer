# Now

2026-11-01 local date. Active goal: **complete all milestones**: all Phase1 and
all six Phase2 features in docs/product-brief.md. Overall goal incomplete.
Repo: https://github.com/1xmint/minecraft-ai-engineer.

Current engineering worktree: task `work/connected-circuits`, branch
`phase/27-connected-circuits`, based on merged main **9a1d6e7 / PR37**.
Documentation PR38 (`docs/multiplayer-preview-download`, sourceeb91617) updates
the public download and archives the real memory screenshot; CI41373420233 is live. Main worktree is clean and fast-forwarded. Original C1
source8490f8d passed all five checks in CI41369209886, including141 native tests
and the unchanged full dedicated/restart/SQLite suite.
These C1 fixture observations do not establish the entire phase.

Release source57b1d65 passed all five checks in CI41367933778, all137 native
and209 companion tests (208 passed,0 failed,1 existing platform skip), plus full
dedicated/shared/restart/SQLite verification. PR37 merged2026-11-02T01:05:36Z as9a1d6e7; PR36 closed as incorporated.
**Main CI41370616555 passed all five checks** on that exact source.
Published [v0.1.0-alpha.2](https://github.com/1xmint/minecraft-ai-engineer/releases/tag/v0.1.0-alpha.2)
is the multiplayer development preview. Its tag was read back at9a1d6e7, and
GitHub's uploaded asset digests match the locally verified same-run files:

- companion73cf14bcd6dadad14ddba0acf6288410228697a5c5fdb5e63bb2edac164b6d33
- jar6c14f8781c7499f0b01799e7dd66791f9dd3383a077fcb7cc770e19ad47d4c1b

Main assets: `work/main-release-41370616555`. The old alpha.1 remains archived.

Josh: **skip heavy reviews; build and ship**. Public pushes, releases and exact
all-green merges are authorized. Tests run on GitHub, never on this slow PC.
Local scoped compile/type/format/syntax only; Gradle --max-workers=1. Keep
memory only in AGENTS.md and STATE.md; CLAUDE.md is @AGENTS.md. Older history is Git.

## Phase27 Outcomes (E2/E3)

- C1: external controls and internally driven signal pins have actual native
  connectivity, direction and isolation. Prove consecutive producer/route/
  consumer/receiver observations in four orientations, unwired-control changes,
  broken-link and reversed-consumer negatives. Never toggle an internal pin.
- C2: a curated half-adder exposes shared A/B controls and SUM/CARRY, typed ports,
  exact routes/graph identity and strict canonical admission. Prove all four rows
  forward/reverse at consecutive stable ticks; reject altered roles, edges,
  geometry and fabricated leaf-derived whole passes.
- C3: normal chat preview/lesson/build/test operates on the whole connected design
  with union write/read reservations, player conflicts, revocation, interruption,
  input restoration, undo and restart. Prove native shared flows and SQLite
  scopes. Leaf passes never certify a connected graph.

Decision: bounded canonical connected designs retain trusted regeneration and
external-control restoration. Old ComponentTree dependencies order construction,
not electrical signals; their lever-input contracts remain unchanged. Arbitrary
model netlists are premature before topology/isolation validation. New graph
identity must bind canonical geometry, typed endpoints and exact routes; changes
stale whole-circuit evidence. GPT-6-Luna scout and independent challenge checked
actual ComponentTree, FunctionalTrial, assignments and reservations. Both called
out internal vs external roles, full pin traces, graph scope and union footprints.

C1 physical proof: ConnectedGameTests uses real external/noise levers and two
native repeaters. No consumer lever or forced runtime wire POWER. All141 required
native tests passed in CI41369209886 on8490f8d. Raw reports inspected at
`work/connected-port-proof-41369209886`: four rotations,360 frames each/1440 total.
For every rotation the intact route measured producer0/15, consumer0/14 and
receiver0/15 with both lamp states. A gap kept receiver0/lampoff; reversed consumer
kept its input0/14 but receiver0/lampoff. Unwired-control phases stayed isolated
on every tick. Both original external controls ended off. Full C1 CI is now green.
These are fixture observations, not a project pass or complete human acceptance.

C2 is in progress: catalog/half-adder.json and HalfAdderDesign.java
prototype a four-NAND plus NOT half-adder. Shared signals have explicit buffered
routes, external controls, internal pins and two receivers. No project parser,
provider, construction or verification admission exists yet. Complete native
whole-circuit truth proof before integrating strict source/graph/evidence codecs.
C3 has not started. Do not merge this phase as complete after the fixture alone.

## Shipped source and evidence

- Phase25 memory: owner saves goal/constraint/preference/decision through addressed
  chat; authorized members read. Polite ambiguity clarifies locally. Source0ef5c6d
  passed all5 CI41364946648 and was incorporated in final57b1d65/all5 CI41367933778.
  Actual chat writes/reads, editor denial, revoked reads, note recovery and a fresh
  post-restart constraint passed four knowledge markers and strict literal-note/
  count/no-world-authority assertions. No model call. Evidence:
  `work/conversation-memory-final-41364946648`, `work/release-final-41367933778`.
  Actual memory screenshot0003_engineer-project-memory.png inspected in final run.
  GPT-6-Luna found/fixed polite remember falling through to model interpretation.
- Phase26 packaging: tracked-source allowlist produces compiled companion zip,
  deployable jar pair, BUILD manifests and SHA256SUMS. Linux/Windows extraction,
  production dependency install, launcher preparation and authenticated health
  passed; anonymous health rejects. Missing/symlink inputs, existing-output
  preservation and stray-data exclusion passed. No Codex execution/inference.
  Gradle launcher is now correctly tracked100755, preserving clean-source guard.
  Candidate `work/release-candidate-41367933778` has77 zip entries and verified
  pair hashes; checkoutcb3f976d27deb29dc02c661949f396c6c5dd1109 is a PR merge, not
  the publishable main checkout. Installation guide: docs/install.md. GPT-6-Luna
  architecture challenge and focused implementation review found no remaining gap.
- Schematic analysis PR35 merged870fc91; source5067bdc/all5 CI41364625681,209
  companion tests and full native/dedicated/SQLite proof. Exact state/block counts,
  offset-aware bounded diff, explicit air vs outside volume, no world authority.
  Maximal-dimension zero volumes skip empty loops. Evidence inspected:
  `work/schematic-analysis-final-41364625681`. Full in-game integration unfinished.
- Schematic codec PR34 merged885748e; source55ca74c/all5 CI41360384747,21 schematic
  proofs, independent third-party fixtures/dev-only prismarine-nbt2.8.0 oracle.
  Sponge v3 bounded NBT/gzip; no entities, explicit reported optional BE/biome
  discard. Neutral geometry has no grants/pass. CLI protects input aliases and
  existing output, bounds actual reads and rejects sparse arrays. docs/schematics.md.
- Comparators PR33 merged3978f88; source7e27fd7/all5 CI41362325852,136 native and
  all12 analog markers. Canonical compare/subtract139-cell workbenches,45 physical
  selectors,66-case actual numeric suites with exact restoration, shared guards,
  repair, native dust-arm/player-edit-safe undo, fresh restart/subtract evidence.
  Eight rotated intentions,144 windows and132 native suite cases replayed across
  Java/TS. No latency/all4096-combination claim; analog composition still closed.
  Evidence `work/analog-final-41362325852`; docs/images/comparator-tested.png.
- Conversational setup PR32/merged9bde62/source1561555/all5 CI41276307352: actual
  private creation, authorized choices/paging, feet corners and local previews.
  Conversation PR31/mergeb7e3e26/sourcebd39067/all5 CI41273958762: approvals, real
  101-second expiry, reconnect and restart invalidation; strict10/17 model starts.
- Observer PR30/merge3ae2232/source4d4a1de/all5 CI41268319917: native edge timing,
  restoration, shared/restart/composed-leaf evidence. Mechanical repair PR28/
  merge5b29a9c/sourcee881d73/all5 CI41151376521. Natural actions PR29/mergecaa7de6/
  source0f78de1/all5 CI41223355084. Detailed historical development remains in Git.

## Boundaries and runtime

Dedicated Fabric server owns connection identity, project grants, reservations,
loaded-world execution and tests. One private localhost companion beside it on
Josh's PC owns SQLite and Codex. Optional authorized overlays; text clients work.
Keep intention, observation and verification separate. Untrusted model/world/
schematic text never becomes commands, ownership or a pass. Recheck grants,
revision, actor/connection, dimension and epoch during work. Player conflicts
pause. Restart restores descriptions, never leases, callbacks, consent or a live
pass. Creative execution never claims survival gathering or inventory consumption.

Only the configured account-owning operator can request model reasoning. Use
ChatGPT sign-in through the official SDK; no paid API fallback, keys, hosting or
credential sharing. General shared-subscription eligibility remains unresolved;
operator approval is not provider permission. Offline CI players do not prove
production Mojang login. Scripted native flows do not prove full human acceptance.

Pins: MC26.3, Java25, FabricLoader0.20.1/API0.165.0+26.3, Loom1.17.21, Gradle9.6,
Node24.14, TS7.0.2, SDK/CLI0.164.0. Leave unrelated Dependabot PR6 alone.

All prior signed-in structural/Boolean/discussion/natural receipts are consumed.
Phase20 acceptance8214fbd passed CI41221809497/response41222705526: one gpt-6-luna
SDK0.164.0 call,11519 input/0 cached/73 output, diagnose/root, no observed claim.
Request digest c5ccb5a673603fd217fce2a188bfdd6654683755610409c3cfb63eb7ec3416d9;
result658e247247fdc76c1f301919ff70ba2c8e67307a05c0d369674b96fe77bb2bc9.
Private exclusive receipts remain local in AppData/Roaming/minecraft-ai-engineer/
acceptance. Never read/print credentials, replay archival inference as fresh, or
launch unnecessary model probes. Current work needs no SDK call.

## Full remaining roadmap

- E2: actual connected circuits and larger multicomponent/piston systems, register
  banks, memory, arithmetic, displays and redstone computers; update ordering,
  interference and adapting existing structures. Half-adder is only the first slice.
- E1: richer natural references/project retrieval, durable rationale, preferences
  and conversation summaries beyond literal owner notes/search.
- E3: richer connection/timing/interference/inconclusive diagnosis and repair;
  clearer mechanical pose overlays beyond canonical designs.
- E4: signed-in preview -> actual human functional section -> AI remainder ->
  interrupt/change/resume -> real test -> service/game restart -> later explanation
  and visual proof. Entire original10-step acceptance remains required.
- Multiplayer public download/setup (main release proof pending), production
  account-login evidence and subscription eligibility evidence.
- Phase2 all six: voice; physical NPC; genuine survival gathering/placement;
  full in-game schematic import/export; measured size/resources/latency/reliability
  optimization; coordinated multiple builders. File interchange/analysis alone
  does not complete the schematic feature.

Claude's offered independent lane remains schematic transforms only:
companion/src/schematic-transforms/**, tests/schematic-transforms*.test.ts,
docs/schematic-transforms.md. No mod/core/auth/storage/provider/CI/memory changes.
No delivery seen yet. Do not edit those reserved paths.

Next: finish the README/download documentation checks, watch the new connected-codec run
to terminal without restarts, and continue strict connected source/evidence work.
Continue C2 native half-adder proof and strict graph/source/evidence integration,
then actual C3 flows. Keep the full goal active; no single slice completes it.

C2 prototype now has parent-owned native proof fixtures for all four rotations,
eight forward/reverse vectors, all twenty typed pin observations at every tick,
and eight stable frames at elapsed48..55. An independent arithmetic oracle checks
SUM/CARRY and internal NAND relations. A missing A-to-N2 wire must make SUM wrong
while CARRY remains apparently correct, showing one receiver cannot certify the
whole circuit. Initial wire arms are computed using the existing read-only pinned
native connection accessor over intended geometry; no signal is simulated and no
runtime POWER is forced. A native post-settle static geometry check covers arms.
No parser/provider/construction admission yet. Shared recipe is included in the
mod resources. Final scoped Java compilation and format/diff checks passed; the
actual native results are recorded below. No behavioral tests ran locally.

When C2 becomes a companion-admitted family, explicitly add its third shared
catalog to the production bundle and paired-jar catalog parity check. The current
prototype is only a mod fixture resource; no companion module imports it yet.

C2 native source6a5c07a/Fabric in CI41371755386 passed all146 required tests.
Reports inspected at `work/half-adder-native-41371755386`:436 cells,20 pins,
448 consecutive frames per rotation and64 stable frames covering all four input
vectors forward/reverse. Every rotation produced SUM/CARRY00->00,01->10,
10->10,11->01. The missing A-to-N2 wire showed expectedSUMtrue/observedSUMfalse
while CARRY stayedfalse. Static native wire-arm checks passed. Full CI41371755386 is now all-five green
on source6a5c07a, including the unchanged dedicated/shared/restart/SQLite suite. This proves the physical prototype, not C2 strict admission or C3 gameplay.
GPT-6-Luna supplied standalone connected-trace.ts at7aaad74 and fixed partial
verdict semantics, rejected-sample immutability and early array bounds in8ba8209.
Parent type checking passes with the shared dependency junction. Eight parent-owned
CI tests now use four actual archived native vectors plus clock/gap/rebinding/
metadata/false-pass/sparse/oversize/immutability rejection proofs. The normalized
fixture retains actual game clocks and native boolean/integer values, and carries
no fresh project authority. Behavioral tests await GitHub; Java mirror, canonical
source admission, current-run cross-runtime replay and C3 remain outstanding.
No SDK calls or local tests ran. Public alpha.2 does not include this prototype.

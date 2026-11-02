# Now

2026-11-01. Active goal: **complete all milestones**: the entire Phase1 Minecraft
AI Engineer and all six Phase2 features in `docs/product-brief.md`. Overall goal
incomplete. Repo: https://github.com/1xmint/minecraft-ai-engineer.

Current worktree: `work/installable-bundles`, branch
`phase/26-installable-bundles`, based on main3978f88 (merged PR33).
Comparator exact source7e27fd7/all5 CI41362325852 shipped with all136 native tests
and dedicated/restart/SQLite proof. Parallel PR35/source5067bdc/CI41364625681 and
PR36/source0ef5c6d/CI41364946648 remain live; never cancel their runs for this work.

Phase26 Outcomes (serves release/setup):

- R1: an allowlisted, compiled companion bundle installs without TypeScript/Java
  build tools. Proof: extract it, install production dependencies and start the
  actual entry point on both Linux and Windows; authenticated health succeeds.
- R2: the deployable mod and companion are paired from one checkout/run with source
  identity and SHA256 hashes. Proof: Fabric CI produces both in one artifact;
  smoke verifies archive contents, identity and actual runtime imports/launcher.
- R3: multiplayer installation instructions cover one private server companion,
  current pinned runtimes, server/client placement and normal chat. Missing build
  inputs, symlinks and existing output reject; no user data enters the archive.

Decision: repeatable allowlist packaging beats stale source-only instructions or
untracked manual zips. Reuse existing npm lock and launcher; no dependencies,
automatic credentials, paid service, model call or public endpoint. CI artifacts
are candidates, not a release endorsement. Publish only artifacts from an exact
successful main run with all five checks. GPT-6-Luna's focused architecture
challenge confirmed compiled catalog/launcher layout, production optional Codex
binary dependencies, authenticated health and same-run pairing are essential.

Josh: **skip heavy reviews; build and ship**. Tests run on GitHub, never on this
PC. Local scoped Java/main+client compile, TS, format and syntax checks only;
Gradle `--max-workers=1`. Preserve pins and push progress. Memory only in AGENTS.md
and STATE.md; CLAUDE.md is `@AGENTS.md`. Older development detail is in Git.

## Current phase24 Outcomes (E2/E3)

- A1: physical selectors produce independent strengths0..15. Proof: actual native
  calibration279 samples; compare/subtract boundary probes in four rotations.
- A2: players preview/build canonical compare/subtract workbenches. Proof: strict
  identity-bound139-cell/45-selector intention, rotated native placement, fitting
  and rejection checks, guarded creative construction and player-preserving undo.
- A3: real numeric tests restore original controls and survive interruption,
  permission changes, player conflicts and restart. Proof:66 declared cases per
  mode, samples at actual elapsed8/9/10, strict Java/TS replay, scoped SQLite
  reports, restored45 original controls and fresh evidence in a new server epoch.
- A4: normal chat, lessons/materials, model/catalog routing and shared views work.
  Proof: actual chat/private approval flow, denied viewer, revoked editor, static
  repair, real server restart, native screenshot and exact interpretation counts.

A1 and the canonical/native foundation are proved. A2/A3/A4 production integration
is implemented but its complete dedicated/restart/SQLite proof is still pending.
No settled transfer claims latency, all4096 combinations, or arbitrary analog
wiring. Component composition does not yet accept analog leaves.

Current implementation:

- AnalogDesign/AnalogIntention regenerate exact bounded geometry and metadata.
  Compare passes rear when rear>=both sides; subtract clamps rear-strongest side
  at zero. Materials:47 stone,45 levers,46 dust,1 comparator. Base workspace17x3x31;
  rotated workspace31x3x17. No inventories or forced wire POWER generate signals.
- AnalogTrace and AnalogEvidence strictly replay separate intended mode/stimuli,
  real clocks and bounded data. Missing ticks stay inconclusive; wrong mode/input/
  output fails; unknown fields/authority/false pass claims reject. Suite ownership,
  project/revision/component/basis and epoch remain separately server-authoritative.
- FunctionalTrial uses existing per-input WAL/restoration and permission checks.
  All45 original selector states must restore before pass. Recovery restores facts,
  never a callback/lease/pass. Canonical comparator entity handling admits only
  its exact cell/mode/facing/native backing type; no entity NBT/cached OutputSignal
  is imported. Journal creation requires air-before/exact canonical-after, checked
  before save and against captured operation on load. Containers remain protected.
- Normal local previews: `show me a comparator` / `show me a subtracting comparator`.
  High-level planning offers both modes with delay1 and fitting rotations. Lessons
  distinguish intention, snapshot and stored test evidence. Generic static
  diagnosis/repair handles ordinary missing wiring using fresh existing guards;
  an existing wrong-mode comparator or replacement inventory stays protected.
- Undo preserves player comparator/dust support. Initial preflight stays strict.
  After durable undo intent, owned canonical wires use pinned native read-only
  connection calculation to recognize arms changed by earlier owned removals.
  Manual dot changes still block. Projected native connection checks protect
  non-owned neighboring dust. New WireConnectionMixin exposes calculation only.
- Prepared docs: docs/comparators.md, updated source overview/mod/acceptance guides.

## Phase24 evidence and immediate next actions

- **0697462 / CI41320293103:** all120 native tests, calibration279 and boundary
  measurements54+54. Exact historical data in
  tests/fixtures/analog-native-probe.json is feasibility data only, not live
  authority, a saved production pass or fresh inference. Pinned DiodeBlock reads
  pos.relative(FACING): WEST rear/EAST output is correct; initial challenger
  opposite-facing suggestion was corrected against the pinned bytecode.
- **384a4b4 / CI41321820187:** all five checks/all120 native passed after inherited
  chat fixture receipt fix. **33f2e3c / CI41326285930:** all five/all121 native and
  actual36 transfer windows through Java/TS passed; dedicated17m19s.
- **37b1bfa / CI41328039790:** all five/all129 native; eight actual canonical
  intentions and144 transfer windows/432 stable samples across four rotations.
  Evidence: task work/analog-intention-proof-41328039790.
- **b2bd6cb / CI41329949003:** four checks/all135 native passed, including both
  actual66-case native suites. Real dedicated chat built139 cells, interrupted,
  restored original-on/off controls and passed66 production cases. Undo then
  exposed native dust-arm changes; Bob later timed out. Evidence:
  work/analog-runtime-failure-41329949003; native screenshot inspected there.
- **d43fdf7 / CI41332437714:** four checks/all136 native passed, including dust
  undo/dot-edit reproduction and132 suite cases plus144 boundary windows. Real
  dedicated compare flow now completed full139-cell undo; viewer/revocation/
  conflict/static repair markers passed. Shared test assertion raced its queued
  command and saw the prior repair's COMPLETED/untested state. Evidence:
  work/analog-shared-failure-41332437714 and work/analog-wire-proof-41332437714.
- **cc0cbed / CI41360150254:** fixture now observes actual VERIFYING/testTick
  before accepting completion in all three full analog waits. No production
  behavior or pass criterion changed. All13 analog markers passed, including shared
  repair, saved-server fresh retest and latest-operation undo. The process cap
  expired later during the existing pending-stop/restart tail. Full job/SQLite
  verification is still required. Evidence: work/analog-trial-start-proof-41360150254.
  Local client compile passed.

Next: inspect the same live handles to terminal (observation timeout is not
failure). Validate every analog marker and actual SQLite source/scope/actor/
case replay, including at least two subtract epochs. Inspect/archive fresh native
screenshot. Fix failures without weakening proof. Finish docs and merge only the
exact green combined head; then update this note with the shipped outcomes.

Dedicated fixture explicitly covers compare chat build/test/stop/restoration/
blocked-edit undo/full owned undo; subtract viewer denial/editor revocation/
owner recovery/player conflict/missing-wire repair/test/actual saved-server restart/
fresh test/latest-repair-only undo. Bob follows actual phase receipts. Fixed added
native trials justify CI-only client23min/harness1440sec/job30min budgets;
production deadlines, quotas and permissions are unchanged. Fixed provider replies
are routing fixtures, not additional SDK inference or complete human acceptance.

GPT-6-Luna/high supplied only standalone TS intention/evidence modules in isolated
worktrees. Parent owns proofs and reviewed sparse-array fixes. Focused independent
reviews found the concrete player-support/neighbor-dust hazards and validated the
narrow entity and native-connection boundaries. No broad review loop is needed.

## Parallel schematic work

PR34 is a standalone Sponge v3 .schem/neutral-geometry codec and CLI, separate from
world/server blueprints. Dimensions/offsets/DataVersion/block IDs/properties and
supported untrusted metadata survive. Entity payloads reject; explicit optional
block-entity/biome discards are reported. Imports carry no owner/grants/pass.
No in-game import/export, DataVersion upgrade or registry validation is claimed.

- Claude source **7ec6659 / CI41327833280:** all five checks;18 independent-format,
  rejection and CLI tests passed. Production uses bounded custom NBT; pinned
  prismarine-nbt2.8.0 is a dev-only oracle. Fixtures/license sources documented.
- Parent **d09295d / CI41333273366:** added input-alias protection before output
  truncation, actual bounded reads even if input grows, and sparse-array rejection.
  Three new boundary tests passed with four checks. Dedicated reproduced inherited
  ordinary-chat send/disconnect race, unrelated to codec behavior.
- Parent **55ca74c / all5 CI41360384747, merged PR34/885748e:** applies the proved384a4b4 sender/
  recipient receipt handshake and required marker. No production chat, privacy,
  timeout or quota changes. Scoped compile/type/format/syntax checks pass. A
  focused GPT-6-Luna/high file-preservation review found no concrete overwrite or
  byte-bound gap. All21 schematic tests and full dedicated/restart proof passed; the file foundation is shipped.

Josh requested Claude's next goal. Reserved offered lane: read-only analysis and
geometry diff in **companion/src/schematic-analysis/**,
_*tests/schematic-analysis*.test.ts_*, **docs/schematic-analysis.md**, branch
claude/schematic-analysis, own worktree. Base on updated PR34 or main after merge.
No manifests, existing codec, core/server/auth/storage/CI/memory edits. Exact raw
block/state counts are geometry metrics, not item costs or functional optimization.
Diff applies offsets, rejects DataVersion mismatch, distinguishes outside-volume
from air, bounds details with exact totals and never iterates vast empty gaps.
CLI consumes neutral JSON and writes reports to stdout only. No world authority,
paid services, keys or model calls. Claude delivers a draft PR; Codex integrates.

## Shipped foundation and decisions

- Dedicated Fabric server owns projects, authenticated connection identity,
  VIEW/EDIT/BUILD/UNDO/PLAN, world mutations and reservations. One private localhost
  companion on Josh's PC owns SQLite memory and the operator's Codex connection.
  Optional authorized client overlays; shared lessons have personal cursors.
- Creative execution supports lamp/NOT/OR, observer pulses, generated timed buffers
  and up-to-four-input Boolean truth tables, canonical sticky-piston shutter/static
  repair, bounded teaching/build/stop/reconcile/test/undo, grounded discussion,
  literal retrieval, owner notes and permission-scoped inventory snapshots.
- Phase19 mechanical repair: PR28/merge5b29a9c/sourcee881d73/all5 CI41151376521,
  all103 native tests; real restart/repair/SQLite proof.
- Phase20 natural action flow: PR29/mergecaa7de6/source0f78de1/all5 CI41223355084;
  foundation8214fbd and actual natural/diagnosis/restart evidence.
- Phase21 observers: PR30/merge3ae2232/source4d4a1de/all5 CI41268319917,
  all113 native tests; exact edge timing/restoration/shared/restart/composed-leaf
  proof. Screenshot docs/images/observer-tested.png.
- Phase22 conversational engineering: PR31/mergeb7e3e26/sourcebd39067/all5
  CI41273958762, all116 native,12 chat markers, strict SQLite10/17 starts and actual
  generated8/10-block traces. Real101-second expiry, reconnect/new stamp and
  saved-server restart invalidate approvals. Screenshot conversation-tested.png.
- Phase23 conversational setup: PR32/merged9bde62/source1561555/all5 CI41276307352,
  all117 native and four setup markers. Private creation, scoped names/numbers/
  paging/ambiguity, feet-based corners, permissions/fit negatives, preserved built
  intentions and local unbuilt previews. Screenshot conversation-setup-preview.png.

Model output/world text/imported geometry are untrusted. Keep intended, observed
and verified state separate. Recheck current grants/revision/connection/epoch at
execution; human conflicts pause rather than overwrite. Restart never restores
leases, callbacks, consent or live passes. Operator-only model admission remains:
general shared-subscription eligibility is unresolved, and operator consent is
not provider permission. Never introduce paid API fallback or credential sharing.

Pinned: Minecraft26.3, Java25, FabricLoader0.20.1/API0.165.0+26.3, Loom1.17.21,
Gradle9.6, Node24.14, TypeScript7.0.2, SDK/CLI0.164.0. Unrelated Dependabot PR6
is not this task. Keep motion-specific handling separate from ordinary circuits.

Signed-in Phase20 acceptance on8214fbd passed CI41221809497/response41222705526:
exactly one gpt-6-luna SDK0.164.0 call (11519 input/0 cached/73 output), choosing
diagnose/root and making no observed-world claim. All earlier structural/Boolean/
discussion/natural receipts are consumed. Private exclusive receipts remain in
AppData/Roaming/minecraft-ai-engineer/acceptance. Never read/print auth credentials,
replay archived replies as fresh inference, or dispatch unnecessary probes.
Request digest c5ccb5a673603fd217fce2a188bfdd6654683755610409c3cfb63eb7ec3416d9;
result658e247247fdc76c1f301919ff70ba2c8e67307a05c0d369674b96fe77bb2bc9.

## Overall remaining goals

- E2: finish analog/comparators; actual connections and larger multicomponent/
  piston systems, update-order/interference and adapting existing structures.
- E1: broader natural references/retrieval beyond literal saved-intention search,
  durable rationale/summaries and preferences.
- E3: richer connection/timing/interference/inconclusive diagnosis and repair;
  clear mechanical pose overlays beyond supported canonical designs.
- E4: signed-in preview -> actual human functional section -> AI remainder ->
  interrupt/change/resume -> actual test -> service/game restart -> later
  explanation and visual proof. Scripted fixtures do not complete this experience.
- Multiplayer packaging/release/setup, real production Mojang authentication and
  provider eligibility evidence. Offline fixtures do not prove production login.
- Phase2: voice conversation; physical NPC; genuine survival gathering/placement;
  full in-game schematic import/export (file foundation in PR34); measured size/
  resources/latency/reliability optimization; multiple coordinated builders.

Do not mark the overall goal complete after any one phase or file-codec milestone.

Combined-source preparation: PR34/source55ca74c is merged as885748e; code merged
cleanly into this branch and the sole STATE conflict retains both outcomes. The
pinned test-only prismarine-nbt dependency was installed locally without scripts
or manifest/lock changes to permit type checking; no behavioral tests ran. Combined
TS/diff checks pass. Actual cc0cbed logs show every analog feature receipt before
20min process expiry in the later existing pending-review/restart tail, so the
CI-only allowance is now23min/client,1440sec/harness,30min/job. Production100sec
consent/101sec expiry proof and every existing assertion remain unchanged. Next:
commit this combined source with docs and run its required exact-head proof.

Phase26 implementation: tracked-source allowlist packager writes a fresh companion
zip, optional deployable jar pair, source/run BUILD manifests and SHA256SUMS.
No repository/config/world snapshots or node_modules are copied. Linux and Windows
CI extract, install locked production dependencies, prepare the actual platform
launcher, start the actual entry point with isolated config and authenticate health;
no Codex binary execution or model call. Smoke also proves missing/symlink inputs,
existing-output preservation and exclusion of a stray private-looking runtime file.
Fabric job pairs both compiled outputs from its own checkout. Installation docs
use Fabric's official server installer and keep online-mode, per-player grants,
operator-only model access and private localhost topology. Local JS/Python syntax
and scoped format/diff checks pass; no behavioral tests ran. Exact CI remains needed.

GPT-6-Luna/high focused implementation follow-up found no concrete packaging,
file-preservation, runtime-layout or Windows launch blocker. Inspection only;
actual cross-platform runtime proof remains required from GitHub CI.

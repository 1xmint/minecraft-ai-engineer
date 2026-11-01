# Now

2026-11-01. Active goal: **complete all milestones**: the entire Phase1 Minecraft
AI Engineer and all six Phase2 features. Requirements: `docs/product-brief.md`.
Public repo: https://github.com/1xmint/minecraft-ai-engineer. Overall goal incomplete.

Current worktree: task work/`conversation-setup`, branch
**phase/23-conversation-setup**, initially based on Phase22 sourcebd39067.
Phase22 PR31 remains separate and its final CI41273958762 is live; do not replace
or cancel that run. Its earlier source7210d45 passed all5 CI41272832823, all ten
native chat markers, staged actual construction/tests/undo and saved-server
restart. Final additions cover real expiry/reconnect and consistent lesson text.
Phase23 must rebase onto the eventual merged Phase22 before its final CI/merge.

Phase21 observer engineering is complete in merged PR30:
https://github.com/1xmint/minecraft-ai-engineer/pull/30. Merged2026-11-01T03:28:20Z.
Exact implementation4d4a1de passed all five checks in CI41268319917, all113 native
Minecraft tests, the full dedicated flow, all nine observer markers and strict
SQLite replay for original input states, actual restart and the composed leaf.
Read-only root/shared lessons, permissions, revocation, player conflicts, input
restoration, guarded undo and retained stale evidence passed. Native screenshot
was inspected and archived in `docs/images/observer-tested.png`. Final evidence:
task work/`observer-final-41268319917`; earlier native fixtures remain archived.
The composed metadata drift was fixed without weakening canonical validation.

Phase22 source7210d45 passed all5 CI41272832823 and all ten native conversation
markers, including staged actual build/design/structural test/undo and a pending
chat approval across saved-server restart. Strict SQLite replay passed8/17 starts
and actual8/10-block traces; screenshot inspected. Final sourcebd39067 adds real
expiry/same-server reconnect and consistent lesson text; CI41273958762 is live.
The published `v0.1.0-alpha.1` remains the older single-player alpha.

Josh: **skip heavy reviews; build and ship**. Public pushes and green merges are
authorized. Tests run on GitHub, never on this PC. Locally use scoped compile,
type, formatting and syntax checks; Gradle `--max-workers=1`. Keep memory only in
AGENTS.md and STATE.md. Historical development detail is preserved in Git.

## Full remaining roadmap

Josh clarified that normal interaction must be talking naturally to the bot,
not typing lesson/setup commands. `/engineer teach root` in a fixture selects a
personal lesson but does not establish the finished experience. After completing
the observer proof, prioritize a conversational entry point, obvious read-only
lesson context, clarification for ambiguity and natural explicit confirmation
that retains all execution guards. Commands stay available as fallback. This
serves E1/E4 and precedes more component expansion; comparators remain required.

- E2: broader electrical/mechanical engineering: comparators, analog
  strength, actual connections, larger piston mechanisms and composed systems.
  Geometry and settled endpoints never substitute for functional tick proof.
- E1: broader natural references, durable rationale/summaries and project retrieval
  beyond literal saved-intention search. The bounded Phase20 action flow is done.
- E3: richer connection/timing/interference/inconclusive diagnosis and repairs
  beyond supported canonical designs; audit mechanical pose overlays for clarity.
- E4: complete signed-in preview -> human functional section -> AI remainder ->
  interrupt/change/resume -> actual test -> service/game restart -> later explanation
  and visual proof. Scripted fixtures do not establish this entire human experience.
- Phase2, all unstarted: voice conversation; physical NPC; genuine survival gathering
  and placement; schematic import/export; measured size/resources/latency/reliability
  optimization; multiple coordinated independent builders.
- Multiplayer release/package/setup, production Mojang authentication and provider
  eligibility evidence. Synthetic offline players do not prove production login.

## Proven foundation

- Dedicated Fabric server owns identity, access, projects, reservations and world
  mutations. One private localhost companion owns durable SQLite project memory.
  Optional authorized client overlays; core commands work with text-only clients.
- Creative execution, per-player VIEW/EDIT/BUILD/UNDO/PLAN, shared personal lessons,
  conflict pauses, interruption/reconciliation/restart, operation-bound undo and
  actual electrical functional evidence. No restored lease or permission from disk.
- Canonical electrical catalog: signal lamp, NOT, OR. Generated timed buffers and
  up-to-four-input Boolean logic have separate behavior/geometry confirmation and
  actual per-tick game proof. Generated geometry and model replies are untrusted.
- Canonical sticky shutter has full read isolation, motion tickets/journals,
  guarded construction/test/recovery/manual release, settled undo and static repair.
  Keep moving-block semantics separate from the generic electrical allowlist.
- Textured translucent preview, literal project search, intended lesson/timing/
  materials explanations and read-only grounded discussion/owner notes.

Mechanical Phase19 PR28 merged5b29a9c; final sourcee881d73/all5 CI41151376521,
all103 native tests, nine repair markers, six real server boots and strict SQLite
replay of owner/shared repaired trials. Evidence: task work/
`mechanical-repair-shared-proof-41151376521`.

Phase20 PR29 mergedcaa7de6; final source0f78de1/all5 CI41223355084. Foundation
CI41221636372 on8214fbd and native proof41219014537 establish all106 required
Minecraft tests, all13 natural markers and exact SQLite dispatch/reply replay.
Evidence: task work/`natural-request-proof-41219014537`.

Fresh signed-in natural acceptance41221809497 and response41222705526 passed on
8214fbd after all five checks were green. Exactly one gpt-6-luna/SDK0.164.0 call:
11519 input, 0 cached, 73 output. It selected diagnose/root, cited project and
component:root, and made no observed-world claim. Actual native diagnosis, denied
editor model admission, companion replacement, saved-server recovery and SQLite
proof passed. Native flow10m28s. Screenshot inspected and archived in
`docs/images/natural-diagnosis.png`; exact public synthetic data is in
`tests/fixtures/live-natural.json`. Source archive regression rejects expiry/rebinding.
Evidence: task work/`natural-live-proof-41221809497`.

Natural request digest:
`c5ccb5a673603fd217fce2a188bfdd6654683755610409c3cfb63eb7ec3416d9`.
Result digest:
`658e247247fdc76c1f301919ff70ba2c8e67307a05c0d369674b96fe77bb2bc9`.
Private exclusive started/untrusted/response receipts remain local and consumed.
All prior structural/Boolean/discussion receipts are also consumed. Never replay
archival replies as fresh inference or restore authority from them. No further
model call is needed to prove the shipped Phase20 slice.

## Decisions and current boundaries

- Multiplayer-first dedicated server, single-player-compatible logical core. World
  state is authoritative; keep intended, observed and verified facts separate.
- One local operator-owned Codex service using ChatGPT sign-in, no separately billed
  API, API keys, hosting purchase or credential sharing. General shared-subscription
  eligibility remains unresolved; model admission stays configured owner/operator-only.
- Model interpretation is a strict advertised action/target choice. Server-generated
  summaries and one-use `/engineer act` govern inferred work/additional AI planning.
  Original player words remain separate from model prose. Exact stop stays immediate.
- Pending reasoning/consent binds real player/connection, epoch, project/revision,
  grants, component and personal lesson. Automatic lesson progress waits during it;
  explicit lesson changes invalidate it. Recent intentions expire in five minutes
  and carry no result or authority. Durable requirements are literal owner notes.
- New shutter previews use canonical mechanical validation; built mechanical work
  cannot enter electrical repeater-delay revision. Preview imports no motion control.
- Settled Boolean vectors do not prove an observer pulse. Shipped observer support
  uses its separate canonical temporal contract and consecutive native tick traces.
- Pinned runtime: Minecraft26.3, Java25, Fabric Loader0.20.1/API0.165.0+26.3,
  Loom1.17.21, Gradle9.6, Node24.14, TypeScript7.0.2, SDK/CLI0.164.0. Preserve pins
  unless a task needs a change; unrelated Dependabot PR6 is not this work.

## Phase21 shipped proof (serves E2/E3)

Observer support is a canonical temporal primitive, not an expanded generated
block allowlist. Its Pulse contract has zero settled Boolean vectors.

- O1 passed: actual delay2, width2 and lamp release4 in all four horizontal
  rotations, both lever edges, quiet periods and wrong-facing/unrelated-update
  negatives. Native consecutive tick traces round-trip through strict Java and
  TypeScript replay; gaps, duplicates and false off-endpoint passes reject.
- O2 passed: canonical creative construction under existing loaded-world,
  permission and reservation guards; no preview authority, a player support
  preserved, actual conflict/revocation pause and guarded assistant-only undo.
- O3 passed: actual baseline/edge evidence, exact initially-off/on input
  restoration, fresh owner reconciliation, stale retained evidence after real
  restart, fresh retest and no restored callback, lease or live pass.
- O4 passed: catalog/native/component planning, lessons/materials/timing, shared
  viewer and text-only views, independent composed leaf and actual dedicated
  build/test/interruption/recovery/undo. SQLite replays exact saved revisions and
  distinct server epochs. The full old electrical/shutter suite also passed.

Final source4d4a1de/all5 CI41268319917, merged3ae2232/PR30. All113 required native
Minecraft tests and nine observer markers passed. `observer-edges.json` contains
archival measured native data from CI41225064861, never inference or authority.
The genuine composition persistence bug was temporal test-description drift in
the TypeScript mirror; it was fixed with rejection regression and actual receipt
checks. Earlier fixture errors and full development history remain in Git.

## Phase22: conversational engineering (PR31, final proof running)

Source7210d45/all5 CI41272832823 proves private/text-only native chat, unaffected
ordinary broadcast, obvious lessons and scoped component questions, actor-bound
separate action/behavior/geometry/structural approvals, actual build/stop/resume/
test/undo, wrong-actor/model admission denial, grant-stale rejection and actual
saved-server restart. Native flow13m9s; strict SQLite8/17 starts and exact actual
generated8/10-block timing traces. Evidence: task work/
`conversation-staged-proof-41272832823`; screenshot inspected. Prior-controller
regression retains Bob's VIEW via the supported viewer role; generated acceptance
binds captured owner and restores previous command actor. GPT-6-Luna/high auth
review findings were fixed. Initial grammar/native suite has116 required tests.

Final sourcebd39067/CI41273958762 adds actual101-second expiry, disconnect/reconnect
in the same server epoch and a fresh pending saved-server restart proposal.
Expected10/17 starts; production deadlines/limits unchanged. CI-only allowances
include the explicit wall wait. Lesson rendering removes the stale static
"testing required" sentence above actual stored passes; canonical source/evidence
stay unchanged. Final screenshot/evidence and green exact-head merge remain.
No local tests, SDK calls, fresh inference or release for these slices.

## Phase23: conversational setup (serves E1/E4)

Outcomes:

- O1: create a private project or find/select an authorized project through chat.
  Prove denied creation, no inaccessible names, ambiguous names require a choice,
  and stale list choices cannot select a changed/revoked project.
- O2: stand at the two workspace corners and mark them through chat. Explain the
  exact bounds and retain existing owner, state and overlap checks; no world edit
  or model request from marking. Prove shared viewers/editors cannot change it.
- O3: a native player creates/selects/marks a project through ordinary chat, then
  uses the existing design/build/test conversation. Commands remain fallback.

Decision: deterministic local setup intents call existing ProjectManager and
EngineeringSession methods, never executable command text or a model-generated
permission. The first corner is the block containing the player's feet; explain
this explicitly. A bounded personal project-choice dialogue uses only current
VIEW-authorized projects in the player's dimension and fresh connection/epoch/
selection/grant/revision checks. No automatic choice among duplicate names.
Creation grants stay operator-owned. No new account or shared AI access.

Tasks: O1 grammar and scoped chooser/privacy proofs; O2 existing workspace-method
adapter and owner/overlap rejection proofs; O3 replace setup-only commands in a
native conversation fixture, inspect evidence, update guide, current-head CI and
merge after Phase22. Focused GPT-6-Luna/high auth review only. No local tests.

Phase23 setup/parser and current-connection adapter are implemented. Native proof
now creates/marks both conversation fixtures via actual chat, exercises duplicate
names, yes ambiguity, paging, grant-stale rejection and a fresh numbered choice,
plus denied Eve creation/list privacy and Bob viewer workspace denial. Corners
are actual standing positions (fixture teleport only positions the native player).
Existing model calls/counts remain10/17. All runtime additions remain unproven.
GPT-6-Luna/high completed the narrow auth review: no concrete disclosure, stale
selection, actor or owner/workspace bypass. Paging retains the same scope/expiry
and exposes at most12 current authorized entries per response. Compilation before
paging passed; the latest additions need scoped compilation and CI.

Setup-only native messages wait21 actual ticks between sends, respecting the
unchanged vanilla chat budget. Latest source compiles; runtime proofs and a
post-Phase22 rebase remain required. No SDK calls or local tests ran.

The GPT-6-Luna/high paging-only follow-up found no disclosure or stale-choice
bypass. The guide now explains feet-based corner marking, private creation and
authorized paged project selection; sharing/creation grants remain administrative
commands. Phase23 has not run CI and is not shipped.

Native setup coverage also coordinates the real Bob client after an EDITOR grant:
its corner-marking chat must reach the owner-only rejection while Alice's exact
workspace and empty journal remain unchanged. Runtime proof remains pending.

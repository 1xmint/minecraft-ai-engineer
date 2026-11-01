# Now

2026-10-31. Active goal: **complete all milestones**: the entire Phase1 Minecraft
AI Engineer and all six Phase2 features. Requirements: `docs/product-brief.md`.
Public repo: https://github.com/1xmint/minecraft-ai-engineer. Overall goal incomplete.

Current worktree: task work/`conversation-entry`, branch
**phase/22-conversation-entry**, rebased onto main
**3ae22329fb0df73f2c9735113c91c1052f22372f**. Goal remains incomplete.

Draft PR31: https://github.com/1xmint/minecraft-ai-engineer/pull/31. Initial
CI source db7e59a is queued/running; no conversation runtime result is claimed.

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

Phase22 natural conversation is implemented in part, not runtime-proven. It has
private addressed native chat, obvious lesson context, scoped clarification and
separate actor-bound natural action/behavior/structural confirmations. Scoped
compilation passed; grammar, chat/privacy and clarification proofs are written
but unrun. Still required: native staged action/generated/structural conversation,
cross-controller callback regression, full stale/replay/revocation/restart proof
and CI before merge. No model calls or local tests ran for these phases.
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
- Catalog contracts currently use settled Boolean vectors. They cannot prove an
  observer pulse merely by seeing an idle output. Observer support is not exposed yet.
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

## Phase22: natural conversation over engineering projects (serves E1/E4)

Outcomes:

- O1: type `Engineer, explain how this works` in ordinary Minecraft chat and get
  a private explanation of the selected design without a setup command. Prove
  plain unrelated player chat is unaffected, addressed requests/replies are not
  broadcast, and an authorized client without overlays gets the same interaction.
- O2: infer an obvious single-design lesson; clarify ambiguous parts and accept a
  natural follow-up without silently choosing another component. Prove personal
  lessons, grants, connection, project, revision, dimension and expiry remain bound
  and changes invalidate the question rather than granting authority.
- O3: propose engineering work from conversation and confirm/cancel in plain words,
  including behavior/geometry proposal stages. Prove no work before the reviewed
  one-use confirmation, no other actor/replay/stale confirmation, immediate stop,
  safe cancellation and the existing owner/operator quota gate.
- O4: drive actual dedicated-player conversation through preview, lesson, delegated
  build, interruption/recovery, testing and undo, plus shared/text-only paths. Prove
  actual chat packets, SQLite evidence and screenshots; keep commands as fallback.

Decision: use explicitly addressed ordinary player chat (`Engineer, ...`,
`Engineer: ...`, `@Engineer ...`, `Hey Engineer, ...`) as the first private bot
entry point. Ordinary multiplayer chat retains its audience. Consume the native
validated player-chat event before broadcast, using the server's actual player
connection and original signed text, never system messages or decorated world
text. Use the current selected project; no new permission or AI account eligibility
is inferred. New-project/workspace conversation and voice/NPC interaction remain
required broader work, not established by this entry point.

Tasks:

1. O1/O3: private authenticated chat adapter, bounded addressing/intent grammar,
   reuse scoped interpretation and one-use act dispatch. Native grammar and
   actual player-packet/privacy negative proofs; local compilation only.
2. O1/O2: obvious read-only lesson context and scoped ambiguity follow-up; no
   automatic build/test or inference merely from choosing a personal lesson.
3. O3: complete natural confirmation/cancellation for each generated-design stage,
   failure/replay/revocation/disconnect/restart proofs; focused authority review.
4. O4: actual command-free conversation flow, durable evidence and visual review;
   update documentation, rebase onto the proven/merged observer source, CI then merge.

Implementation now written but not proven: native chat hook, explicit addressing,
private replies, current-connection/server-thread admission, automatic single-design
lesson, scoped ambiguity question/follow-up, and natural confirmation/
cancellation of chat-originated interpretation requests via the existing exact
scoped action handler. Grammar tests added for CI. Dedicated action/generated/
structural chat proofs and broad stale-scope negative coverage remain incomplete.
Dedicated text-client proof is now written to send actual chat packets, infer
single-design lesson context without `teach`, get timing/materials and reject
viewer stop. Another native client must see ordinary player chat but neither
the private request nor personal reply. CI verifier requires both new markers;
these tests have not run. Native grammar tests also remain unrun. Scoped Java
server/client compilation passed. GPT-6-Luna/high completed the focused chat
identity/privacy/confirmation check with no concrete blocker. No model calls,
local tests, permission expansion or public release have occurred.

Conversation-origin metadata now follows the existing typed design dispatch into
the ephemeral generated Basis and structural preview. Natural yes delegates only
one actual actor-matching action/behavior/structural proposal through its existing
nonce/current-context handler. Multiple proposals, missing proposals or an active
component question never guess what yes means. Each behavior/geometry/world stage
retains separate review; preview approval cannot become construction approval.
No mode flag is accepted from the model, sent as permission or restored from disk.
Normal command flows keep their existing prompts and default false origin.

The focused GPT-6-Luna/high review found a real stop-dialogue omission: stop
cancelled action/design/structural frames but left the personal lesson question.
The authorized stop path now cancels that actor's question too, with a native
assertion and a subsequent yes that must find no proposal. Stop aliases preempt
all questions/proposals and still require the existing BUILD permission.
Unbuilt generated preview acceptance now binds basis.actor during accept and
restores the previous actor in finally, matching the existing structural branch;
it cannot inherit a previous (possibly revoked) construction controller. Native
cross-controller and full staged confirmation regressions remain required before
release. Grammar now also tests unambiguous review selection. Scoped server/client
compilation passed; none of the new runtime tests or any model call has run.

ConversationLessons now keeps bounded ephemeral personal questions and explicit
component choices. A default renderer cursor never selects a composed leaf for
the user. The follow-up names one advertised leaf, checks actual session/player/
connection stamp/epoch/project/revision/grants/dimension/workspace/lesson/expiry,
then sets only that player's lesson. Automatic tutorial progress holds during
the question; explicit lesson changes invalidate it. Project use, disconnect and
server close clear dialogue; no context/permission is loaded from disk. The
native proof now includes asking which part, answering "the lamp", a timing
follow-up and rejection after an explicit different lesson. These new scope and
native-flow changes compile but remain untested, and need the completed phase's
focused check. Rebased onto the merged observer source; draft PR31 now runs the
first conversation CI. Full staged native proofs remain required before merge.

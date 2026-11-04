# Current state

Updated 2026-11-04. Goal: **complete all milestones**: the full original Phase 1
brief and all six Phase 2 extensions. The goal is incomplete. Requirements live
in docs/product-brief.md, including its multiplayer-first amendment.
Repository: https://github.com/1xmint/minecraft-ai-engineer.

## Outcomes

- E1: durable attributed reasons, preferences, references, retrieval and useful
  summaries; replacement history, revocation/privacy and restart proof.
- E2: connected engineering beyond the half-adder: arithmetic, registers/memory,
  displays/computers, timing, interference and adapting existing structures.
- E3: useful explanation/teaching, diagnosis/repair and visual guidance grounded
  in actual intended, observed and scope-valid tested state.
- E4: the brief's actual signed-in human ten-step acceptance, production-account
  feasibility, verified release packaging and later-session explanation.
- P2: offline voice, visible NPC machine construction, genuine survival gathering/
  tools/crafting, practical schematic reuse, measured optimization and multiple
  builders. Bounded prototypes are foundations, not completion of these outcomes.

## Decisions and constraints

- Dedicated Fabric server first; compatible logical-server core for solo play.
  Server-owned permissions, memory and construction; optional authorized overlays.
  Normal addressed conversation is the player interface; commands are fallback.
- Intended geometry, observations and functional evidence remain separate. A pass
  requires actual game ticks and the complete project/blueprint/component scope.
  Imported geometry and remembered claims carry no authority or verification.
- One private operator-owned companion on the operator's PC. No separately billed
  APIs/keys, hosting, credential sharing or model calls in native execution work.
  Production Mojang login and shared subscription eligibility remain unproven.
- Native survival requires fresh owner delegation, exact human/worker connections,
  revisions/assignments, loaded workspace, reservations and event-time tickets.
  Never supply/replace inventory, creatively compensate or replay old authority.
- Tests run in GitHub CI. Locally scoped compile/format/syntax only; one Gradle
  process at a time, --no-daemon --max-workers=1. Protected main, green exact heads,
  no bypass. Keep candidates stable during live CI; timeout is not terminal.
- Production quotas remain 4/minute and 24/hour. CI fixtures alone use 24/96.
- Claude owns memory, explanations, comparison and local voice. Codex owns native
  construction/survival, larger circuits, multi-builder coordination, integration,
  release and final acceptance. Check current branches before duplicating work.

## Landed and published

- Latest published download remains v0.1.0-alpha.2; it predates recent integrations.
- Main7acfb33 includes creative tutorials/build/test/repair/undo, permissions and
  conflict guards, optional overlays, and server-side durable memory.
- Half-adder PR45/aaed3ef: 436 cells/20 pins, actual forward/reverse truth tables,
  conversation, interruption/revocation and saved-world retest. Source42deb3c,
  full CI41577981016 passed. Keep its canonical v1 source and digest unchanged.
- Schematics integrated PR49/1d1dcb7: Sponge v3 codecs, inspection/comparison,
  transforms, in-game library and bounded imported creative construction. Source
  7ba44a3/full41581459104 passed; independent memory/build/restart/undo inspected.
  Broad real-world mechanisms still unsupported; imports have no pass/ownership.
- Optional idle Carpet companion PR47/ff4a021, full41682037419 passed.
- Literal reasons/history PR48/f259fdc, full41688991149 passed; retrieval PR60/
  7acfb33, exact d99eab5/full41734780134 passed. Independent DB requires exactly
  six attributed notes including reference6; refused writes leave no rows.
- Native dirt PR50/a067f25: source5cec203/full41696617329 and focused41696644888.
  Next-cell placement PR51/9348e41: sourced48d27e/full41704803030 passed.

## Now: combined player experience integration (E1/E3/P2)

Phase44 branch phase/44-player-experience combines voice integration28dd18b with
native integration2313437. Only STATE conflicted; the multiplayer fixture merged
automatically. No execution authority is widened by explanations or voice.

Outcomes: (1) the same build supports memory/explanation/comparison and native
construction with current permissions; (2) optional voice yields editable chat,
never automatic sending; (3) native exact inventory survives same-world restart
and stop/revocation/conflicts refuse further work. Task: compile the combined
Java/client code, run full CI, inspect multiplayer/memory/client receipts, and
merge only the exact green source. Human microphone/speaker acceptance is separate.

Evidence for parents, not yet this combination:

- PR65 native2313437: full41741571086 all five passed; dedicated multiplayer and
  independent memory proof inspected. Focused41742617014 was cancelled; retry of
  the same terminal handle is pending. Parent3bc5158 focused41741356685 passed:
  inspected oak.done/oak-restarted.done and exact schema2 oak journal (axis=x,
  one native log, ordered intents, empty before/retained after); strict independent
  verifier passed. Actual route/build/stop fixtures also passed.
- PR62 grounded23abc96: full41740052461 all five passed. Scope matcher prevents
  same-revision/different-blueprint evidence becoming current timing. Missing or
  stale scopes remain historical. Comparison cleanup restores explicit selection.
- PR63 voice28dd18b: full41740086114 all five passed. Unique owned temporary audio
  cleanup only; never scan/delete other clients' files. Trusted local executables,
  bounded subprocesses/queues, transcript requires Enter, cancellation drops late
  results. Earlier client screenshots inspected; injected audio is not real mic
  acceptance. GPT-6-Luna focused privacy/scope reviews found no remaining issue.

## Native limits and known problems

- Pinned optional Carpet26.3+v260915. Per-project native identity, exact stored
  inventories and no restored grants. Verification reads strict detached43-slot
  native codecs; never construct same-UUID observer ServerPlayers (vanilla rebinds
  the advancement handler). Full-slot/component and corruption GameTests pass.
- Bounded flat stone-supported inert navigation, dirt placement/batch construction,
  one isolated oak log only. No natural trees, arbitrary terrain, tools, crafting,
  native circuit placement or general survival completion yet.
- Native pickup is the sole expected item emitted synchronously by authorized
  destroy, not a universal guarantee against other mods emitting in that callback.
- Chunk/player data are separate saves. Clean restart is proven for bounded slices;
  arbitrary crash atomicity is not. Unresolved prepared records never recreate
  items, compensate creatively or resume. Owner-only recovery inspection is read-only.

## Roadmap and parallel work

1. Finish exact combined integration above; update landed evidence after merge.
2. E2 phase42 full-adder in work/full-adder: crossing3af8e29 passed64 native samples
   in full41741392528. Fixed nine-NAND prototype07c263a failed real all-pin proof;
   diagnosticfd0548d showed branch signal exhausted before first repeater. Source
   225cf57 adds trunk-junction repeaters; focused native CI pending. No full-adder
   admission/pass exists yet. Keep all35pins/all16rows/consecutive tick assertions.
3. Native crafting after oak proof: actual menu/recipe actions, exact inventory and
   cursor accounting, strict tickets, save/restart; never manufacture output items.
4. Broaden genuine resources/terrain, native machine construction and coordinated
   multiple builders; continue full E2 larger circuits and E3 repair/adaptation.
5. Claude continues useful memory/summaries, grounded teaching, comparison and local
   voice. Integrate proven work without duplicating his active branches.
6. Complete production feasibility and actual human acceptance, package verified
   work, then audit the entire original scope before any goal-complete claim.

Historical investigations and failed runs remain in Git history/CI artifacts.

2026-11-04 integration update: PR65 merged d665570 from exact2313437. Full CI41741571086 all five passed and same-source focused41742617014 attempt2 passed; inspected build-navigation/oak restart receipts and independent ordered oak/native inventory verification. PR52/54/56/59 and docs53 closed as superseded by this merge, not separately merged. Parent native source3bc5158 also passed focused41741356685. Merged main ancestry here; only STATE conflicted, retained this condensed note, and the code tree is unchanged from3060034. Keep PR66's live run41777328584 stable; advance its branch only when that run is terminal. Full-adder225cf57 independently passed native CI41777116255 with3544cells,35pins and128samples; all8vectors occurred forward/reverse with8samples each. Full-adder source/trial integration remains to build.

Combined player-experience source3060034 passed all five checks in CI41777328584. Inspected its independent SQLite memory proof and actual client transcript screenshot: recognized text remains in the editable chat field. This is injected audio, not human microphone acceptance. Current39deef3 differs from3060034 only in STATE and main ancestry; native/voice/teaching code is unchanged. Advance the PR now that its prior run is terminal, then require exact-head checks. Full-adder source13a0b36 separately passed native/companion source parity CI41779430060; the driven dedicated flow c5cf14f is pending on its own feedback branch and is not part of this integration.

Claude PR70 source da85fab passed all five checks in CI41778579597 and is integrated locally over1445b24. It drops recognition results when the delivery UI is no longer ready, and reports speech-program failures. Focused integration inspection found one later race: stop/disconnect can occur after the worker queues a failure but before the client thread displays it. Failure notices now carry their playback generation and recheck at actual delivery; a held-delivery regression checks this exact ordering. No server, worker, inventory or world authority changed; per-run audio cleanup remains isolated and manual transcript sending remains required. Real microphone/speaker acceptance is outstanding. Keep live PR66 run41780615883 stable; combined voice changes need client compile/CI before advancing that branch.

Voice integration client compilation passed after the delivery-generation refinement. Added a focused native client feedback workflow so injected voice cancellation/playback and overlays can be proved before advancing PR66's stable live head. This does not replace final full combined CI or human microphone/speaker acceptance.

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

## Current integration evidence (2026-11-04)

- PR65 merged d665570: exact source2313437 passed full41741571086 and focused41742617014 attempt2. Build-navigation/oak restart receipts and independent inventory verification inspected. PR52/54/56/59 and docs53 were closed as superseded.
- PR66 merged314c7b9: exact sourced0388f5 passed all five checks41784327466. Includes attributed summaries, grounded guidance, read-only comparison and optional local voice, with imported geometry always untested. Actual client transcript is editable and unsent; human microphone/speaker acceptance remains outstanding. Claude comparisonb58cc74 separately passed41781972092.
- This branch combines main314c7b9 with the full-adder candidate. Main's native resource/navigation/strict inventory decoder and player-experience changes are retained. Fabric registers crossing/full-adder and comparison/voice test entrypoints together. Scoped compilation and fresh combined CI required.

## Larger connected engineering (E2): full-adder candidate

Outcomes: ordinary conversation previews/builds/tests a three-input full adder; all eight truth-table rows have scope-bound native evidence; permissions, stop/recovery, interference, restart and owned undo remain enforced. This is one part of E2; registers/memory/displays/computers remain unfinished.

The fixed nine-NAND layout has3544cells,35pins and16forward/reverse cases with8consecutive native settled samples each. Graph digest8a2b25ed743c42b936e101dc19368a35b740fb48a0dc72781712d3152dc100be. Typed v2 trace/suite codecs reject mismatched identities/claims/clocks. Half-adder v1 is unchanged. Native routes refresh signals at trunk junctions; no latency/reliability claim.

Selected workspace limit65536positions/128peraxis accommodates the99x4x79layout. Planned/journal limits4096 and explicit observation4096 remain. Selection/load, overflow, private overlap and EDIT-without-BUILD boundaries passed native proof and GPT-6-Luna review.

Driven9054f81 run41782236628 timed out during construction. Replaced repeated canonical/journal scans with invocation-local immutable indexes from private validated snapshots; altered current source fails closed, last active-operation change wins, failed owned-wire matches stay conflicts. Fresh world/grant checks and both per-write WAL saves remain. Full-adder places at most one block/tick; other designs retain their pace. Isolation reads are deduplicated only within one invocation. Fixture waitFor budgets now use actual ticks.

Sourcef606346 passed166required native GameTests plus companion/current-run native source parity in41891581447. GPT-6-Luna found no concrete ownership or recovery regression. Dedicated two-client run41891969513 is live on that unchanged source; it must prove conversation, construction stop/resume, viewer denial, actual editor input writes before revocation, all3input recovery, complete tests before/after same-world restart, interference and owned undo. Independent SQLite verifier recomputes every pin/clock and zero final owned changes. No end-to-end success claimed while this run is live. Integration with main is a separate candidate and needs fresh checks.

Native crafting remains incomplete. PR71 is the separate inventory-only save guard; its live CI41892704379 must not be disturbed. Menu/cursor persistence and controlled teardown require implementation before native crafting execution.

Driven41891969513 onf606346 completed actual3544cell construction and wrote full-adder-built.done. Its progress logs show roughly256cells per21seconds, finishing about five minutes after resume. Bob's generic four-minute awaitFile deadline expired first, so editor/revocation/trial stages never began. Added an eight-minute bound only for the peer's construction-completion wait, preserving all gameplay assertions, failure markers and generic waits. This is not a successful end-to-end run. PR64's8360f74 full CI41893197183 remains stable while this fixture correction advances separately with the current main integration.

PR64 integrated source8360f74 CI41893197183 passed Fabric but Linux and Windows extracted-runtime smoke jobs failed: ERR_MODULE_NOT_FOUND for dist/catalog/full-adder-definition.json. Confirmed from both completed job logs. Added that actual runtime asset to companion packaging and jar/catalog parity verification. Python syntax and diff checks passed; the existing extracted-runtime smoke tests must prove the fix on CI. Prepared separately while live PR64 checks and dedicated7033934 run41893491002 finish; no active candidate is restarted.

Full-adder7033934 driven41893491002 completed3544cell construction but again lost its peer before editor admission: integrated runner progress was roughly256cells per35-40seconds, exceeding the eight-minute wall bound. Do not keep enlarging waits. Prepared compact machine checkpoint JSON (same fields/null encoding, same temp write, force(true), atomic replacement/fallback, and both pre/post block WAL saves); human-edited config remains pretty. Added CI-only cumulative save duration/count and wall duration to existing256cell progress messages to distinguish checkpoint cost from the rest of construction. No world/authority cache or skipped validation. Actual native/restart/player-flow proof is required for the encoding change; no measured speedup claimed yet.

PR71 merged3364dad after exact f087543 passed all five checks41892704379. Aligned this candidate with main's settled-menu native save guard; only STATE required conflict resolution, and the inherited guard is unchanged. Full-adder13f7bea passed181 required native tests and source parity41896766710, and its actual player flow41897093594 remains live on that unchanged feedback branch. Prior combined8360f74 full CI41893197183 finished: dedicated collaboration, Fabric and client passed; Linux/Windows packaging failed only for the missing full-adder runtime asset, now included. Fresh final-head full checks are required for this aligned candidate.

Full-adder13f7bea driven41897093594 completed construction, actual editor revocation/input restoration, owner interruption/recovery, all16cases/35pins before and after same-world restart, and interference refusal. It timed out25minutes after launch during the final valid undo, so no overall pass. Inspected native logs and receipts; each complete trial took about8.5minutes. Save profiling at3328cells:218595ms checkpoint work/6660saves in366405ms elapsed.

Prepared full-adder test geometry reuse: private captured full-adder kind selects admission before any mutable-source template branch; connectedBlocks rechecks full current intent equality every call. A bounded immutable index stores only intended cells and the exact closed radius-two union minus planned cells. Prerequisites, every target/state and every margin chunk/air value still read live on every invocation; geometry clears when no trial runs. Other designs retain existing validation. Native GameTest candidate hand-enumerates150union positions/148margin positions for two adjacent cells and rejects duplicate/mutable/exposed geometry and changed source admission. GPT-6-Luna reviewed and found no concrete regression. Scoped compileJava passed before final test-exception and branch-order refinements; native CI still required. No latency or overall speedup claim until actual driven run passes.

PR72 merged b48f932 after exact bf73540 passed all five checks41898933908 and code-identical e6c6442 passed actual physical fault/restart proof41898690326. Independent five-file NBT evidence and malformed/grid/cursor-fault receipts inspected. Native menu resources survive clean saved-world restart without a derived result or restored authority. Crafting execution remains unfinished.

Geometry sourcecf137ed passed native41903727778; driven41904043755 is live and must remain on its exact head. Prepared a separate undo metadata index from private capture/load-validated connected root snapshots. Every lookup checks operation/component/position, and the same existing native wire matcher still reads live geometry. The index stores no world/grant state and is pruned with snapshots. Transitions/other connected and analog cases keep the prior fallback; all undo action, neighbor, journal and durability checks are unchanged. GPT-6-Luna reviewed and found no concrete stale-ownership bypass. Tests compare indexed/canonical matching, reject edits/wrong components, detach exported sources, reload validated data and reject pruned operations. Scoped compileJava passed before final reload/prune test additions; actual native CI required. No undo runtime completion or speedup claimed yet.

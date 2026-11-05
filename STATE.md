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

Integrated Claude summary PR67/edc837c and guidance PR68/3c0b4fd (the latter includes the former) after all five checks passed on each: CI41777570982 and41777858940. The summary uses the existing current-connection/project/grant-fenced knowledge read and separates literal player statements, intended plans, recorded work, exact-scope past tests and unknowns. Guidance is read-only and never starts work or upgrades historical evidence. The voice refinement05f41f3 passed focused native client CI41782787534. These additions are prepared locally while PR66's stable1445b24 full CI41780615883 runs; final combined-source full checks are still required. Comparison PR69/b58cc74 is waiting for CI41781972092 before integration.

Prepared comparison PR69/b58cc74 in the combined branch while its dedicated CI41781972092 remains live. This is a local candidate only; combined exact-head full CI is the merge gate. Integration review found summary/guidance shared the generic scope matcher, which could accept fabricated matching imported cache metadata even though imported geometry has no behavior contract. The display-only matcher now rejects imported geometry; an existing native imported-comparison fixture also asserts summary/guidance remain untested. No mutation authority or source format changes. The comparison definitions and recommendations remain read-only; no timing/reliability evidence is invented. Final source still needs compile and full/native checks.

Imported-geometry integration also keeps cached pass strings out of lesson evidence and history, and next-step guidance explains the missing behavior contract rather than recommending a functional test that the executor must refuse. The native imported fixture covers all three read-only surfaces. Final combined server/client compile and exact-source CI are the remaining gates; no imported behavior or functional pass is invented.

Final combined server/client compilation passed after summaries, guidance, comparison and voice integration plus imported-evidence safeguards. Prior PR66 head1445b24 passed all five checks in CI41780615883. Advancing that now-terminal branch with the complete candidate; require fresh exact-head full CI before merge. Claude comparison's own CI41781972092 is still pending, so no source-green claim for PR69 is made. Full-adder driven run41782236628 remains live on9054f81 and is independent of this candidate.

## Phase45: native crafting research (Phase2 survival)

Outcomes: (1) an owner can turn one actually retained native oak log into four native oak planks through vanilla2x2 clicks, with all other inventory state preserved; (2) wrong actor/connection/recipe/menu/step or stale grant refuses before the next transformation; (3) native save/restart retains exact inventory with empty grid/cursor and no restored action authority. Tests remain GitHub-only. Phase45 is research, not implemented.
GPT-6-Luna inspected pinned26.3 bytecode: InventoryMenu result0, grid1..4, hotbar36..44; clicked(slot,0,ContainerInput.PICKUP,player) supports sourcehotbar pickup→grid1→result0→samehotbar. RecipeManager.getRecipeFor(CRAFTING,CraftingInput,level,key), RecipeHolder.id/value, matches and assemble can preflight copied input but must never inject assembled output. ResultSlot.onTake invokes real crafting/recipe awards and consumes ingredients/remainders. InventoryMenu.removed returns grid/cursor items to inventory or may drop them; it is not universally safe cleanup. PhysicalWorkerStorage snapshots only43 inventory entries; transient grid/cursor are not saved by that verifier. Before any executor, design event-time menu tickets and safe lossless cancellation/lifecycle behavior; unresolved or partially consumed outcomes cannot be repaired by counts, supplied items, compensation or automatic retry. Do not construct observer ServerPlayers. Crafting-table menus and generic recipe execution are deferred. Full-adder performance failure41782236628 takes priority before implementation; no craft/menu authority has been added.

Pinned lifecycle follow-up (GPT-6-Luna): PlayerList.remove saves player data before removal; the current receipt covers only43 inventory/equipment slots. Menu close may return or drop transient items. Four non-yielding server-thread clicks prevent ordinary tick/chat interleaving, but do not make native callbacks or process crashes atomic. Added a pre-save refusal for non-default menus, carried cursor and any crafting/result slot, before saveAll runs: inventory-only persistence must never certify transient resources. This is a prerequisite guard, not crafting execution or complete teardown protection. Next implementation must persist/reconcile real menu resources together with native inventory and gate dismissal/disconnect/server-stop before introducing clicks. No counts-based repair, fabricated output or automatic uncertain retry. Actual crafting remains incomplete.

PR66 merged314c7b9 after exact sourced0388f5 passed all five checks in CI41784327466. Phase45 is aligned with that main; no player-experience code is reverted. Native full-adderf606346 passed166 required GameTests and companion parity in41891581447; driven41891969513 is live, with no end-to-end pass claimed yet.

## Phase46: native worker menu recovery (planned)

Outcomes: (1) clean native save/removal/restart preserves exact real crafting inputs and cursor components alongside inventory, without persisting a derived result or restoring work authority; proof covers cursor, grid and each known between-click state. (2) malformed/version-mismatched menu data refuses before joining or overwriting the original player file; legacy inventory-only workers remain compatible. (3) failed join/hydration and owner revocation preserve saved resources with default action denial; native dismissal/shutdown and existing gather/build flows remain proven. Tasks serve these outcomes: bounded component-aware snapshot codec and refusal tests; worker-only native save hook; prevalidated staged restore with connection-safe hydration and failed-join preservation; exact native save/restart/refusal fixtures on CI. Recipe execution follows this recovery work.

Pinned26.3 lifecycle review (GPT-6-Luna): Player constructor creates inventoryMenu before load. PlayerList.remove saves before removal; normal disconnect/removeAll does not invoke menu.removed in inspected bytecode. Save real four inputs and cursor in the same player-data record through ServerPlayer.addAdditionalSaveData; never store result slot, tickets, grant, connection or task authority. Explicit menu close can return/drop items and needs a managed-worker guard.

Ordering correction found by Codex: InventoryMenu.slotsChanged calls CraftingMenu.slotChangedCraftingGrid, which unconditionally uses ServerPlayer.connection.send (pinned bytecode offsets121..144). Do not hydrate grid inputs during unjoined worker.load. Prevalidate/decode with the existing native inventory decoder, retain an exact actor-bound pending snapshot, then hydrate after placeNewPlayer/bindConnection under default denial. If joining/hydration fails, the native removal save must retain the original pending resources rather than silently serializing an empty/partial grid. Clear pending state only after exact successful hydration or verified actor removal with preserved native data. No observer ServerPlayer clones.

This phase is not implemented. Saved-state persistence is the required guarantee; arbitrary power loss/corrupt NBT cannot be claimed atomic. Known interrupted self-inventory moves can later use a distinct exact-state native PICKUP cleanup fence (no result slot or project-world action); unknown result-take failures must preserve observations instead of inferring a stage from counts.

Implemented the isolated component-aware menu snapshot codec and native GameTest candidate: exactly four positional inputs plus cursor, strict schema/field set, native ItemStack codecs, copies on input/output, and refusal of missing/malformed data. Tests independently inspect emitted stacks with vanilla's codec as well as round-trip and alias checks. Scoped compileJava passed before the final test assertions; actual tests remain CI-only. Native save hooks, actor-bound staging, post-connection hydration and lifecycle proof are still to implement; no crafting or recovery completion claimed.

Worker menu wiring candidate: a worker-only native save hook stores exact inputs/cursor alongside native inventory; restore prevalidates both records, stages copied resources by actor identity, and hydrates only after default-deny registration/join/connection binding. Partial hydration retains the original snapshot and requires unchanged native inventory before a removal save. Successful hydration checks exact stacks/inventory before clearing stage. Normal save receipts now compare disk menu as well as inventory; no pending restore can be certified. New work refuses unsettled menus. Native menu clicks remain default-denied; InventoryMenu removal is blocked for the actual bound menu owner and UUID-impostor actors. Summon cleanup preserves the original exception and retains staged recovery if disk verification fails. GPT-6-Luna reviewed the initial wiring and prompted explicit click denial and cleanup-error preservation. Server/client compile passed before the final close-owner/cleanup refinement; live persistence, interrupted-hydration and teardown fixtures still need implementation and CI. No crafting execution or lifecycle completion claimed. Foundation d6cb99d passed Fabric, companion and Windows checks in41894835217; remaining jobs are live.

Full-adder7033934 dedicated41893491002 completed construction again but the peer's eight-minute wall deadline expired just before readiness on the slower integrated runner. Do not keep raising deadlines blindly: measure checkpoint/reconciliation cost and reduce redundant serialization/read work while retaining both WAL saves, then rerun the actual flow. PR64's broader8360f74 run41893197183 remains live; package fix770105d is prepared separately.

PR71 merged3364dad after exact f087543 passed all five checks41892704379; final dedicated memory proof inspected. This is the settled-menu save guard only, not menu recovery. New wiring has four green checks in41896826065; its dedicated collaboration run remains live and does not cover the separate physical worker flow.

Added actual-resource lifecycle candidate to the existing three-boot physical fixture: the same natively mined/picked-up dirt is saved in the grid before reboot, restored under menu-click/removal denial, then saved on the cursor before the next reboot, restored without duplication and returned to its original empty native hotbar slot by fixture-only moves of the actual stack. Original gathering/placement/refusal assertions remain. Copies of the three real player .dat snapshots are read independently by prismarine-nbt; verifier requires exactly one dirt across grid/cursor/inventory, original UUID, empty equipment, and no persisted result/authority fields. The focused physical workflow installs pinned test dependencies for that reader. Scoped Java/client compile and JS/shell syntax passed before final verifier refinements; actual physical CI required. Partial-hydration fault and corrupt-file preservation fixtures remain outstanding before recovery can merge.

Prepared actual native hydration fault cases separately from live b470f4b physical CI41897807048. The native restore boundary now has a package-private after-step fault callback (ordinary production call uses no-op). Tests require reaching a joined, default-denied pending restore with the real mined stack before throwing after grid slot1 and after cursor application; they then check native removal saved the original pending snapshot and can restore it again. A structurally valid NBT record with unsupported menu version must refuse before join and remain byte-identical; only the fixture restores its own valid bytes afterward. Independent NBT verification now covers both fault snapshots in addition to grid/cursor/settled states. Scoped server/client compilation, JS/shell syntax and formatting passed. These fault cases are not CI-proven yet; keep the live physical candidate stable.

PR64 final candidate2331827 CI41898009579 passed Windows planner, Companion, Fabric and native client. The extracted-runtime smoke jobs now pass on both Linux and Windows with the packaged full-adder asset. Dedicated collaboration remains live. Full-adder13f7bea passed181native tests/source parity41896766710; its actual player flow41897093594 remains live. No complete full-adder or worker-menu recovery pass is claimed.

Positive native menu recovery b470f4b passed focused41897807048. Inspected real grid/cursor restart receipts and independent worker-menu-proof.json: grid/cursor/settled .dat records retain the same single native dirt and UUID, with no saved result or extra equipment. Existing physical gathering/navigation/build/oak/restart assertions also passed. Fault candidatee6c6442 is live in41898690326 and is not yet proven. Aligning with main3364dad required STATE and Storage conflict resolution: retain the new full inventory+menu disk verification, which replaces the earlier inventory-only settled guard without weakening its no-false-receipt purpose. Production Java code is unchanged from the separately tested fault candidate; final combined full CI is required before merge.

## Phase48: native oak crafting (started, not executed)

Outcomes: (1) ordinary owner conversation converts one genuinely retained oak log into four native oak planks through vanilla2x2 menu clicks, with exact other inventory preservation; proof includes native recipe awards, no supplied items and restart. (2) current owner/worker/menu/step/full-resource state is checked before each click; wrong recipe/material, revocation, stop or changed state refuses further transformation without invented resources. (3) unknown result-take failures preserve actual inventory/grid/cursor and deny automatic retry; no counts rollback. Tasks: read-only native recipe preflight; exact per-click menu tickets without world-write grants; durable task stages and actual native execution; owner-chat/refusal/restart and independent resource proof. Menu recovery PR72 must pass its own gates before crafting integration.

Added read-only native oak_planks recipe preflight using copied input. Requires the exact key, one default oak log, four default planks and no remainders; predictions never enter a worker inventory. Native GameTest candidate checks actual recipe output, unchanged input, detached predictions and wrong/count/custom-item refusals. No crafting executor/menu permission or player interaction is added yet. GPT-6-Luna is challenging the per-click authority/resource boundary before implementation. Tests remain GitHub-only.

Native recipe preflight compileJava passed. GPT-6-Luna's per-click design challenge requires one-call exact actor/menu/owner/connection/transaction/step/full-stack tickets, native before/after verification, native save before observed journal state, and uncertain prepared states never retrying automatically. No world-write lease is needed for self-inventory, but owner/selection/revision/grant/body/job exclusivity must be fresh at each click. Result preview is checked before the native result take; produced output is never inserted by code. Current implementation is prediction only.

Menu recovery fault sourcee6c6442 passed focused41898690326. Inspected independent five-snapshot NBT proof (grid/grid-fault/cursor/cursor-fault/settled) and actual malformed/grid-fault/cursor-fault receipts. Same single mined dirt survives, original UUID retained, no result stored. `git diff e6c6442 bf73540` excluding STATE is empty: PR72 code/workflows/tests match that focused proof. Exact bf73540 full CI41898933908 has four green checks; dedicated collaboration remains live. No merge until that final check completes. Foundation d6cb99d also passed all five checks41894835217; it is not a substitute for the larger recovery candidate.

Prepared native crafting click protocol and event gate: immutable43-slot inventory/grid/cursor/derived-preview/selected-slot frames define all four exact before/after states; original hotbar source is fixed, output remains prediction data only. One transaction must observe each native after-state before its next ticket, and each ticket is consumed before native clicked/onTake and its write-ahead callback. Exact actor/menu/connection/full-frame/current recipe and authority are rechecked around that callback. Native mine/pickup cannot overlap a menu run; cancellation clears capabilities, and final completion rechecks the exact resources. No task/router invokes these APIs yet, so no player-facing crafting is enabled.

GPT-6-Luna reviewed the protocol and identified two hardening gaps, now fixed: begin refuses every retained mine/drop/place phase (not just armed tickets), and per-click expiry must be exactly the current server tick plus1, checked again at actual use. A durable task journal, current owner/project closure and actual native click/replay/refusal/exception tests remain to implement. Scoped compileJava passed after these changes; protocol tests are prepared but not yet run on CI. Native recipe preflightfb3fe58 has four green checks in41899994245; dedicated collaboration remains live.

Full-adder13f7bea driven41897093594 reached actual construction, revocation/stop recovery, complete16case/35pin trials before and after same-world reboot, and interference refusal. Inspected receipts and logs: first full pass19:45:25, restarted pass19:53:51, expected later-control undo refusal19:53:51. Overall25minute child timeout killed the valid undo request before completion. Checkpoint metrics at3328cells show218595ms in6660saves across366405ms elapsed; actual tests each took about8.5minutes. Do not claim overall success. Next reduce repeated test-margin geometry work while still reading every current reserved position each tick, and inspect/bound undo metadata work without weakening WAL. Source2331827 final full CI41898009579 remains live.

Prepared PhysicalCraftRecord and PhysicalCraftTask, still unexposed: fixed4native clicks through the one-use gate, exact observed frames, verified native inventory/menu save before journal OBSERVED, and a final COMPLETE record. NEW/PREPARED/OBSERVED/UNCERTAIN records cannot automatically resume; any non-complete or malformed history blocks another craft. Completed history is checked against the fixed native protocol without restoring resources or authority. Task requires the current owner request and uses the existing exclusive body-occupancy/read lease with an empty world-write set; body, owner, connection, selection, revision, assignment and recipe remain fresh. Output is never inserted directly. Scoped compileJava passed before final owner-request/revision validation additions.

GPT-6-Luna reviewed task/record ordering and confirmed a material prerequisite before routing: stop/dismiss or another native save can reenter while clicked/onTake is mutating. Add an exact actor click-stack guard, defer managed teardown until native return and saved outcome, and refuse/defer midpoint player-data saves. Also add the owner crafting-recovery inspector and actual native click/award/replay/revocation/exception/restart proof. No player-facing crafting handler calls this task yet; do not enable it before these gates are implemented and proven. Existing gathered task close() already cancels mining/drop phase fields after verified settled handoff, so the menu's strict idle check can follow completed gathering.

Native recipe preflightfb3fe58 passed all five checks41899994245. Geometry player flow41904043755 failed before gameplay due Bob's concurrent Loom asset .lock race, not a test assertion. Undo index99c823a passed native41904669543; CI-only launcher correction80cbc80 now prepares assets serially and skips that task in concurrent launches. Its new player run must prove completion; no deadline/assertion was relaxed.

## Pending native crafting lifetime proof (2026-11-04)

E2/P2 candidate: native crafting defers worker player-data writes during each
native menu call and delays removal until returned resources are saved and
verified. Stop cancels further tickets; stale same-UUID removal cannot detach
a replacement actor. The physical fixture uses its real mined dirt stack to
exercise nested native save/removal and explicit rejoin. Java/client compile
and shell syntax passed locally; runtime CI is pending. Crafting remains
unexposed to player requests. CI launchers prepare shared assets serially.
The final project mirror fixture now explicitly acknowledges structural-project
on the current boot; its verifier remains unchanged.

Native lifetime source5f62b4b passed actual physical run41912627609. Inspected
physical-craft-lifetime.done and independent five-snapshot worker-menu proof:
the real mined dirt remains exactly once, without a stored result slot.

Next E2/P2 candidate exercises the unexposed task on the actual gathered oak
log after saved-world recovery: four native clicks, native crafting statistics,
exact four-plank result, no replayed clicks or repeated task output, followed
by a fourth saved-world boot without restored authority. Independent NBT and
hand-written journal frame verification are required. Scoped client compile,
JavaScript syntax, shell syntax and diff checks passed. Runtime CI pending.

Native oak execution sourceaa2d329 passed focused41913485653. Inspected actual
craft/restart receipts and independent NBT/journal report: four clicks consumed
one actually gathered log, native onTake awarded4, exactly4planks survived the
fourth saved-world boot, no stored result or replayed capabilities.

Prepared owner-only read-only crafting inspection through existing recovery
conversation. It reports record claims and decoded saved-resource matches,
never resumes work. Wrong-owner history and missing identity rejection fixtures
are included. GPT-6-Luna found a check/open race that could create an identity;
both gathering/crafting inspection now use an existing-only reader. Scoped
compile and syntax checks passed before the final identity fixture; CI pending.

Prepared crafting interruption proof for all4native return boundaries. Each
scenario actually gathers its own oak log, then stops after click1/4, loses
owner project selection after click2, or loses a permitted owner mode after
click3. No subsequent click may occur. Each uncertain history refuses another
transaction, including settled click4. A fifth saved-world boot must preserve
all4exact inventory/grid/cursor outcomes without actor/lease restoration or
resource compensation. Independent NBT reads and hand-worked journal frames
cover all8before/after-restart files. No production task authority changed.
Scoped client compile and JavaScript/shell syntax checks passed before final
selection/mode fixture refinement; runtime CI pending.

Owner crafting inspection sourcec818d93 passed focused41940273765; actual
inspection receipt and previous native crafting reports inspected. Prepared
the natural conversation candidate on a separate branch while interruption
proof16c0406/run41940683369 remains live. 'craft oak planks' uses the exact idle
binding, current owner connection/selection/project/access revisions and no
competing physical task; the underlying lease rechecks authority before each
click. Existing-only identity loading creates no replacement on a failed craft.
GPT-6-Luna reviewed; explicit selection comparison was also added to the route
for clarity (the physical lease already enforced it). The real-client fixture
adds owner chat, denied editor, repeated request refusal, native statistic and
saved-world output checks. Java/client compile and JS/shell checks passed.
Do not merge until interruption, conversation and final combined CI pass.

Native interruption source16c0406 passed focused41940683369. Inspected actual
interruption receipt and independent8snapshot NBT/journal proof for all4click
boundaries and the fifth saved-world boot: no compensation or automatic retry.

Aligned this native crafting candidate with mainb48f932. Main's PR72 was a
squash of the same menu foundation, so Git reported overlapping conflicts;
resolved them to the candidate implementations after inspecting the two-tree
diff. Code/scripts match2ee1e52 exactly after reconciliation; no main behavior
was dropped. Full final-head CI and conversation proof41941120901 remain gates.
Closed superseded drafts44/46/57/58/61/62/63 after confirming their work is
in merged integrations49/48/66. The goal and broad remaining scope are unchanged.

Native trial-history development: work/native-trial-history builds on Claude's
comparison PR78. It captures a bounded64-attempt recent history from actual
FunctionalTrial writes, persists detached scopes/rows and supplies those samples
to authorized comparisons. It never restores execution authority or a pass.
Scoped Java/client compilation passed. Native persistence/bounds tests and an
actual dedicated12/16-game-tick trace assertion are added; CI proof is pending.
This is generated-buffer evidence, not general measured circuit optimization.

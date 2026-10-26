# Now

Phase 1 building; branch phase/1-engineering-assistant. No existing code or PR.
2026-10-26: public repo authorized and created at 1xmint/minecraft-ai-engineer.
Local Minecraft Java 26.3 detected; Node 24.14.0 and Codex CLI 0.163.0 installed,
ChatGPT login confirmed. System Java is only 8; development needs Java 25.
No real-game acceptance evidence yet. Do not describe the full product as shipped.

## Outcomes

- O1: A contributor clones the public repo and CI builds both components from
  pinned versions; invalid contributions fail required checks.
- O2: In single-player, select a loaded region, inspect exact states, preview and
  follow a staged redstone reference; wrong orientation is visibly identified.
- O3: Delegate the remaining section, stop/resume and undo safely. Player edits,
  inventories, unloaded chunks, other worlds and survival writes are rejected.
  Checkpoints survive restart and reconcile before resuming.
- O4: Ask through Codex for a supported small contraption, remember the project,
  and verify it using actual Minecraft ticks. Unsupported designs and failed
  tests remain explicitly unverified; no fabricated model or game results.

## Decision — 2026-10-26

Build Fabric + Node 24 TypeScript + SQLite, authenticated loopback HTTP. This is
a persistent job controller with a game adapter; ordinary placement and checks
are deterministic, the model only plans. Pin detected Minecraft 26.3 rather
than force a downgrade. Select curated circuits first; reject unsupported
requests instead of inventing untested circuits. Keep an executor boundary for
a later physical builder.

Alternatives considered: just write a plan (does not deliver the requested
experience); standalone bot/Mineflayer first (adds navigation and survival risk
before preview/teaching); mod-only (simpler IPC, but couples model runtime and
memory to game lifecycle). The companion separation earns its small IPC cost.
Challenge: read-only Codex still allows reads/tools; explicitly restrict tools,
inherit no arbitrary user plugins, and prove the adapter's execution boundary.

## Roadmap

1. Building — foundation, feasibility, public repo, CI and exact protocol (O1/O4).
2. Next — world inspection, curated preview and tutorial (O2).
3. Next — bounded creative executor, interruption, journal and reconciliation (O3).
4. Next — Codex design selection, project memory and game functional proof (O4).
5. Next — integrated acceptance and release; record gaps against all brief slices.
6. Later phase 2 — voice conversation; physical NPC; survival resource gathering;
   schematic import/export; design optimization; multiple independent builders.

## Proof and limits

Confirmed: GitHub authentication, public repository, installed Java edition and
version, Codex subscription login. Pending: SDK request, Java 25 build,
mod/service IPC, functional game tests, full in-game player workflow.

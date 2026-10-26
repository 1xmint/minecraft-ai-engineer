# Contributing

Read AGENTS.md and STATE.md first. Pick one Outcome and describe its visible
success and failure cases in an issue or PR. Keep changes small enough to review.

Use Node 24.14.0 and Java 25. From the root, run `npm ci`, then `npm run check`.
Run `npm run format` for TypeScript and documentation. Build the mod with
`cd mod` and `./gradlew classes` (`.\gradlew.bat classes` on Windows).
Use the committed wrapper and pinned dependencies; do not commit generated
game files or private configuration. Do not update the game version casually.

CI runs the tests, including actual Minecraft redstone GameTests. On Josh's
PC, keep work to scoped compilation and formatting, and let CI run tests.
No ChatGPT authentication or live model inference runs in public CI.

Open a branch and pull request; required Companion and Fabric checks must pass
before merging. Main rejects force-pushes and deletion. Solo maintenance does
not require a second human's approval, but auth and world mutation changes
need an independent technical review. Squash merge with a useful description.

Never claim a circuit works from static validation. Keep intended blueprints,
observations, and tick-based test evidence distinct. Any visible client flow
without an actual driven session remains unverified. Update STATE.md when work
lands; no parallel summary or handoff documents.

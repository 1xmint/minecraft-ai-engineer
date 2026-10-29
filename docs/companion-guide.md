# Local companion

Requires Node 24.14.0 or later in the Node 24 line. Run `npm ci`, `npm run build`,
then `npm run doctor`. The doctor checks the pinned Codex runtime and subscription
login without making a model request. `npm run doctor -- --probe` makes one
bounded structured planning request. Sign in using the official `codex login`
flow with ChatGPT; this project does not accept API keys or copy credentials.

`npm start` starts the service. It binds 127.0.0.1 only. On Windows, config and
SQLite memory live in `%APPDATA%\minecraft-ai-engineer`; on other systems they
live in `$XDG_CONFIG_HOME/minecraft-ai-engineer` or `~/.config/minecraft-ai-engineer`.
Set `MCE_CONFIG_DIR` consistently for both the mod and service to override this.
The token in `companion.json` is private; do not paste it into chat, world saves,
issues, or the repository. The mod reads that file directly. Keep the config
directory in your private user account, not a synced or shared folder.

The planner launcher uses the official SDK's pinned Codex executable. It adds
`--ignore-user-config --ignore-rules --ephemeral` so user plugins, project
configuration, hooks and model-provider overrides are not inherited. The
adapter also disables shell, patch, browser, image, app and agent tools, uses
an empty temporary working directory, read-only permissions and no web search,
and rejects any non-message/reasoning event. Windows launcher preparation needs
the .NET Framework 4 compiler included with supported Windows installations.
If the launcher cannot be prepared, planning fails closed.

Each explicit `/engineer plan ...` action makes at most one model request,
with a 90-second timeout and no automatic retry. Ordinary inspection, stages,
placement, progress and functional checks never invoke the model. Account
limits still apply. `MCE_MODEL` can select a model available to the signed-in
account and overrides the default for all stages. Without an override, focused
template selection, behavior extraction and timed-buffer geometry use `gpt-6-luna`;
Boolean geometry uses `gpt-6.1-sol` at medium reasoning effort. Two Luna Boolean
replies were rejected for missing support/overlap; the bounded Sol layout passed
actual Minecraft truth-table and restart acceptance. `MCE_GEOMETRY_MODEL` overrides
only the geometry stage, taking precedence over `MCE_MODEL` there. Each explicit
request still uses one selected model with no automatic retry or model fallback.
The doctor catalog probe checks only its chosen catalog model, not Boolean-model
availability. A successful probe
establishes access at that moment, not unlimited or unattended entitlement.
Public CI never receives sign-in credentials and never invokes a paid model.

The first supported template is a lever/repeater/lamp. Other mechanisms are
reported as unsupported. Model explanations cannot grant world permissions,
change the template, execute shell commands or mark a design verified.

The SQLite database keeps immutable blueprint revisions, projects, components,
task reports and audit entries. Observations are separate from intended
blueprints and functional reports. Only the latest 20 perception snapshots per
world/dimension are retained. Blueprint JSON exports are recoverable copies of
the database's immutable design records. Back up the private config directory
while the companion is stopped to preserve memory; it contains a service token,
so keep backups private. Mod authorization and write-ahead journals are owned by
the mod rather than this service.

Functional checkpoint reports must carry `blueprintRevision` matching the latest
stored design. Stale reports are rejected. Revision changes and observed world
changes clear current functional evidence; old reports remain in the audit log.

See [protocol](protocol.md) for requests and response formats. There is no
endpoint for running commands or modifying the world. Future providers can
implement `PlanningProvider` without replacing the game adapter.

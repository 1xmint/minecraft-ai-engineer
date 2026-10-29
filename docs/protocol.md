# Loopback protocol v1

This describes the shipped single-player reference protocol. It is not a
multiplayer authorization boundary. The planned server gateway, authenticated
actor context, revision fencing and recipient filtering are specified in
[STATE.md](../STATE.md). Minecraft clients must never receive this service bearer token.

Companion binds only 127.0.0.1:43127. Requests require Authorization: Bearer TOKEN;
the shared token is stored in a user-only config file outside world saves/repo.
Reject browser Origin, unknown routes, excessive bodies, and non-loopback hosts.
JSON errors: {error:string}. Response protocol version is 1. No service route can
mutate a world. The mod owns authorization and server-thread operations.

POST /v1/design
Request: {request:string,worldId:string,dimension:string,anchor:{x,y,z},
rotation:0|90|180|270,projectId?:string,maxSize?:{x,y,z}}
Response: {blueprint:Blueprint,message:string,projectId:string}
POST /v1/observe — {worldId,dimension,region:{min,max},blocks:[{pos,state}],tick}
GET /v1/projects?worldId=... — {projects:[...]}
POST /v1/checkpoint — {worldId,dimension,projectId,taskId,status,detail,blueprintRevision?,observed?,verified?}
Functional evidence requires a positive blueprintRevision matching the current
stored blueprint. Stale revision reports are rejected. New revisions, changed
world observations, and fresh checkpoints invalidate previous functional evidence;
historical reports remain in the audit log.
GET /v1/health — {protocol:1,status:'ok'} (also authenticated).

Blueprint format (coordinates relative unless stated):
{schemaVersion:1,id:string,revision:integer,projectId:string,worldId:string,
dimension:string,name:string,explanation:string,anchor:{x,y,z},rotation:0|90|180|270,
blocks:[{pos:{x,y,z},state:'minecraft:block[property=value,...]',stage:string,
component:string,prerequisites:string[]}],
stages:[{id:string,title:string,explanation:string}],
bounds:{min:{x,y,z},max:{x,y,z}},resources:{'minecraft:block':integer},
ports:[{name:string,pos:{x,y,z},kind:'input'|'output'}],
tests:[{id:string,description:string}],
provenance:{kind:'curated',template:string,minecraft:'26.3'},verification:'untested'}

Rotation: (x,z)->(-z,x) per clockwise 90 degrees viewed from above, followed by
anchor translation. Rotate facing and horizontal property keys with coordinates.
Runtime-changing powered/lit/power properties are observations, not construction
mistakes. Registry validates complete block-state strings before preview/build.

Reference template `signal-lamp`: solid supports at (0,0,0), (1,0,0), (2,0,0);
floor lever at (0,1,0) facing east; repeater at (1,1,0) facing west delay=1;
redstone lamp at (2,1,0). Stages `foundation`, `input`, `output`. The repeater
outputs east into the lamp (the repeater's facing state points toward its input).
Test off->on->off using scheduled ticks. Test only
this curated circuit and restore input; never infer arbitrary circuit validity.
Blueprints can be structurally valid but remain functionally untested until
the actual mod records game test evidence for that project and revision.

## Server gateway v2 — multiplayer foundation

V2 uses the same native loopback/Host/Origin/body limits and server-private
bearer token. It is a Fabric-to-companion gateway, not a player HTTP API.
Fabric derives the actor from its authenticated Minecraft connection and remains
the only permission/execution authority. Node mirrors server assertions for
privacy and stale-result rejection; it never grants a player world access.
V1 remains for the historical single-player release and is not used by the
multiplayer gateway.

Every project operation carries:

```json
{
  "context": {
    "serverId": "server-namespace",
    "serverEpoch": "current-boot-uuid",
    "actorId": "11111111-1111-4111-8111-111111111111",
    "projectId": "project-uuid",
    "projectRevision": 1,
    "authorizationRevision": 1,
    "requestId": "unique-operation-uuid"
  }
}
```

POST `/v2/server/open` accepts `{serverId,serverEpoch,serverGeneration}` and responds with
`{protocol:2,status:'ready',serverId,serverEpoch,serverGeneration}`. Fabric increments
the positive safe-integer generation durably under a private file lock before
each new Minecraft boot. The companion durably rejects lower generations, even
for epochs it has never seen. An equal generation must retain the same epoch;
a higher generation requires a new epoch. Reopening the same boot after a
companion restart is allowed. Registration is live-process
state: after a companion restart, Fabric must reopen and synchronize projects.
Previously retired epochs cannot be reopened, including after service restart.
A new registration pauses old task mirrors, invalidates evidence and aborts
queued/running old planning requests. Ownership/history remain durable.

POST `/v2/project` accepts the context plus `worldId`, `dimension`, optional
`name`/`blueprint`, and `members:[{actorId,role,canPlan}]`. Role is lowercase
`owner`, `editor` or `viewer`, with exactly one owner and explicit `canPlan`.
The first synchronization is owner-authored; editor/owner updates must match
the current membership. Only an existing owner can mirror changed membership;
the trusted Fabric server may assert `isOperator:true` for an administrator.
Changed membership requires a higher authorization revision. Blueprint records
are immutable, and a geometry change requires a higher project revision.
An optional minimal Java blueprint is normalized to the typed format above;
actual block-registry validation still belongs to Minecraft.

POST `/v2/checkpoint` and `/v2/observe` accept the context plus their v1 body
fields and a required integer `reportSequence` from 1 to `Number.MAX_SAFE_INTEGER`.
Fabric assigns one increasing sequence per project/server epoch across both
report routes on the server thread. The companion durably rejects duplicate or
older sequences, including after a service restart. A new Minecraft server epoch
starts a new sequence; a fresh random request ID alone cannot make an old report
current. This prevents late progress from undoing a newer observation or test.
A checkpoint must match the current world/project and revision, and
functional evidence requires its synchronized intended blueprint. Changes,
revocation and fresh checkpoints clear stale functional evidence while audit
records remain. Request IDs for mutating operations are one-use, even after
restart. A retry must be a new explicit operation, not replayed authority.

Composite checkpoint task IDs are `projectUUID:component`. Their observed payload
includes the component, basis/version, assignment generation and descriptive
assignments. `testScope` identifies captured vectors by project, exact blueprint
id/revision, component and basis/version. `lastPassedScope` may point to an older
immutable revision when an unrelated component changed; empty current vectors
are never a new test. Historical audit records keep their original identity.
These fields describe work; none grants a Minecraft execution lease.

On intent sync, the companion derives leaf geometry and transitive prerequisites
from canonical stored blueprints. It preserves unchanged historical task rows,
pauses changed/removed/unscoped tasks and never carries live execution across a
project revision. Authorization changes and service/server registration pause
all tasks. World observations compare coordinate history across the retained 20
snapshots and invalidate only intersecting component/read closures; each closure
includes a two-block margin. Newly covered coordinates that contradict canonical
geometry also invalidate affected results. Legacy unscoped rows fail closed.
The comparison fingerprint is local to companion invalidation and is not the
Fabric assignment basis or an execution credential.

A targeted observation may include `perception` schema1 with kind `block` or
`view`, authenticated `actorId`, self `player` position/eye/look vectors, integer
`target`, optional view `face`, and integer `differences` counts (missing, changed,
unplanned). Its target must be present in the at-most-seven sampled blocks.
Pose values are finite and view direction normalized; unknown fields, arbitrary
inventory payloads and wrong actor attribution are rejected. The actor must match
the request context before report ordering is claimed. The companion preserves
these typed facts in the private observation record. Observations without this
optional object remain compatible. This route never reads Minecraft itself or
grants permission to access a new world region.

POST `/v2/project/read` accepts context and returns the authorized project and
current blueprint. GET `/v2/projects?serverId=...&serverEpoch=...&actorId=...&worldId=...`
returns only that actor's current project memberships. Equal project UUIDs in
different server namespaces do not share data. These read routes still require
the private server bearer token; player UUID query fields are not authentication.

POST `/v2/design` accepts context plus the v1 design fields, `modelAuthorized:true`
and `isOperator:true`. Initially only a mirrored owner with `canPlan` may request
operator-owned planning. This technical boundary does **not** establish that
one subscription can serve arbitrary multiplayer users. General shared account
access stays disabled pending the eligibility decision in STATE.md.

One fair queue serializes v1/v2 inference. At most eight requests wait, with
one waiting request per actor. Defaults cap v2 planning to four starts per actor
per minute and 24 per server per hour; counters survive service restart. Queued
work rechecks epoch, membership and both revisions before invoking the provider,
and again before returning. Cancellation/revocation discards late replies.

A design result returns the unchanged request context, a proposal at
`projectRevision+1`, message and projectId. It records an audit proposal only.
Fabric must validate and accept the proposal, then synchronize the new revision
through `/v2/project`; the model response cannot advance canonical authority.
Every response is protocol 2. Invalid/stale authority returns 403/409, exhausted
budgets 429, and cancelled/unavailable planning 503. No route mutates Minecraft.

## Ephemeral private inventory

A connected actor's gateway context may include server-generated `connectionId`
(UUID). POST `/v2/inventory` uses the ordinary fenced context/report sequence and
strict schema1 fields: `worldId`, `dimension`, `actorId`, `connectionId`, `source`
(`player`/`container`), `tick`, `slots` (1..128), `containers` (zero own-inventory
positions or one/two storage positions), and unique item ID/count pairs. Counts
are positive integers bounded by the slot capacity. Unknown fields, duplicate
items/positions and mismatched actor/connection are rejected before report order
is consumed. It records no item data in SQLite or the audit.

POST `/v2/inventory/read` requires current VIEW context and returns protocol2,
status `private`, context and `inventory` (`{snapshot, ageMs}` or null). Only the
same current actor, connection, server epoch and project/auth revision can read a
snapshot for 60 seconds. The bounded in-memory cache replaces each actor/project
entry, clears on service restart/authority changes, and never joins shared model
context. `privateContext` is a separate internal interface for future private
advice; the shared design route never uses it. No inventory fact grants resource
ownership, access rights or a Minecraft execution lease.

## Local operation history

Fabric checkpoint schema2 uses `journalVersion:4` and a local `operationScopes`
object (`schemaVersion:1`). `snapshots` contain a canonical SHA-256 content ID,
`kind` (`intended` or `legacy-position`) and the full immutable blueprint source.
`operations` map each operation ID and physical component to one snapshot ID.
Sources retain their original world, dimension, project, blueprint ID and revision.
Hashes detect internal inconsistency; they are not execution credentials.

Limits are 4,097 operation mappings, 64 snapshots, 65,536 total stored blueprint
blocks and 8 MiB of metadata. Shared sources are deduplicated; unreferenced sources
are pruned. Missing, duplicate, mismatched or unknown-version metadata fails closed.
Old flat/v2/v3 journals first pass their existing position/component validation,
then migrate explicitly as `legacy-position`; their checkpoint blueprint cannot
establish an unknown original semantic revision. No history or personal inventory
is added to the companion's shared model context by this format.

The active baseline and dependency states belong to their captured operation
source. Changed task intention blocks resume. Undo acquires historical targets
and read closure under current grants; live permission checks use the exact
captured lease footprint. Persisted metadata never restores a connection or lease.
Structural revisions still require a separately approved old/new transition and
are not enabled by journalv4 alone.

## Generated behavior and geometry (development)

POST `/v2/behavior/propose` uses the design context, explicit model authorization
and operator fields, and a required bounded workspace `maxSize`. It requires the
current owner with PLAN permission and a connected native `connectionId`. It
returns `proposal:{behavior,explanation}` without changing canonical revision.
Behavior is null for unsupported/unclear intent, exact schema2 Boolean intent as
described below, or exact schema1 buffer intent
with integer `risingGameTicks` and `fallingGameTicks`, each0..128. These describe
observable output latency, independently of block geometry or repeater labels.

POST `/v2/design/generated` additionally requires the accepted `behavior` and an
exact schema1 `acceptance`: `proposalId`, `behaviorDigest`, `basis`, `worldId`,
`dimension`, `corner1`, `corner2`, `rotation`, `expiresAt`. Basis contains all
gateway context fields except requestId and requires connectionId. Corners must
match the request anchor/size; expiry is in the next five minutes. The native
server is the trusted consent authority. Nonce/digest are consistency and replay
checks, never standalone authentication. The existing durable replay ledger
consumes each proposal nonce before inference budget; quota failure may therefore
require a fresh proposal. Both stages use the existing queue, rate limits, epoch/
grant/revision fences, cancellation and isolated subscription adapter.

The model returns only name, explanation, supported block positions/states and
one declared input/output. Code derives bounds, resources, support/device stages,
ports, untested verification and the accepted behavior test. Generated provenance
is `{kind:"generated",minecraft:"26.3",generatorRevision:1,behaviorDigest}`.
The digest is SHA-256 of UTF-8 `buffer-v1|RISING|FALLING`. Both runtimes validate
this exact generated kind without weakening curated catalog validation. The
proposal is revision+1 and audit-only until native acceptance/synchronization.

Generated functional evidence binds the exact original functional scope and
behavior digest. Case0 contains bounded baseline samples; cases1/2 contain
replayable schema1 transition traces. Stored verdicts/first-transition claims are
recomputed from samples and compared with independently accepted timings. Partial,
failed or interrupted evidence restores no authority and cannot become a pass.

### Complete Boolean behavior (development branch)

The data validators and server runner additionally support exact schema2 behavior:

```json
{
  "schemaVersion": 2,
  "function": "truth-table",
  "inputs": ["a", "b"],
  "outputs": [false, false, false, true],
  "settleGameTicks": 20
}
```

There are one to four unique input names matching `[a-z][a-z0-9_]{0,31}`;
`output` is reserved for the lamp. Outputs contain exactly `2^N` strict Booleans.
Row indices encode input values in declared order, first input most significant:
the example is AND, with rows `00`, `01`, `10`, `11`. The integer settling bound
is0..128 game ticks. Fields, names, ordering and row completeness are validated
independently of generated geometry. The digest is SHA-256 of UTF-8
`truth-v2|COMMA_JOINED_NAMES|OUTPUT_BITS|SETTLE`; the example hashes
`truth-v2|a,b|0001|20`. Immutable accepted data contains no execution authority.

A generated Boolean blueprint keeps the outer schema1 envelope but uses
`generatorRevision:2`. Raw geometry has `inputs:[{name,pos},...]` in accepted
input order and one `output` position. Code requires distinct actual levers and
exactly one lamp, with no undeclared levers or lamps, then derives canonical
ports, resources, stages and tests. Both runtimes reconstruct and compare all
derived metadata. Buffer revision1 geometry and digest remain unchanged.

The server visits every row in ascending then descending order, including the
repeated last row. Each case records every tick from activation tick0 through
`settleGameTicks+8`, with the expected output required at the settling tick and
through the eight subsequent ticks. Outputs before the settling bound are recorded
but are not exact latency claims. A transient wrong output in the stable window
fails; a sampling gap is inconclusive. A correct final value alone never passes.

Each case binds case index, row, behavior digest, observed named inputs/output,
game tick, elapsed ticks and a schema1 settled trace. Stored verdicts and summaries
are replayed against accepted intent. A finished case must pass before another
case begins, and adjacent case timestamps must agree. A complete pass requires
all `2*2^N` cases. The existing per-input write-ahead journal preserves each
original lever state; interruption, revocation or restart never renews authority.
Fresh explicit authority is required for pending restoration, and player edits
are preserved.

The model adapter now accepts Boolean intent and named geometry through the same
separate owner confirmation and build authorization stages. Missing settling bounds
may be proposed as20 game ticks, explicitly explained for owner confirmation; they
are not exact latency promises. Dedicated native authority/recovery acceptance
passed on source52b1296. The shared AND fixture remains hand-authored raw wiring:
those tests prove its behavior, not fresh model generation or the complete product
experience.
